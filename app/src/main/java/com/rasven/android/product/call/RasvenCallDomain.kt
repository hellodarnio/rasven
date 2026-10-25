package com.rasven.android.product.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telecom.DisconnectCause
import com.rasven.android.diagnostics.RasvenDiagnostics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rasven.android.app.BootstrapActivity
import com.rasven.android.iax2.RasvenIax2IncomingEvent
import com.rasven.android.iax2.RasvenIax2Runtime
import com.rasven.android.product.history.RasvenHistoryCallClassification
import com.rasven.android.product.history.RasvenHistoryEntry
import com.rasven.android.product.history.RasvenHistoryRepositoryProvider
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal sealed interface RasvenIncomingCallState {
    data object Idle : RasvenIncomingCallState
    data class Ringing(val call: RasvenIncomingCall) : RasvenIncomingCallState
    data class AwaitingMicrophone(val call: RasvenIncomingCall) : RasvenIncomingCallState
    data class Answering(val call: RasvenIncomingCall) : RasvenIncomingCallState
    data class Active(val call: RasvenIncomingCall) : RasvenIncomingCallState
    data class Missed(val entry: RasvenHistoryEntry) : RasvenIncomingCallState
    data class Ended(val summary: RasvenCallEndSummary) : RasvenIncomingCallState
}

/** Presentation leftovers must not be confused with a live call owner. */
internal fun RasvenIncomingCallState.blocksNewIncomingCall(): Boolean =
    when (this) {
        is RasvenIncomingCallState.Idle, is RasvenIncomingCallState.Missed,
        is RasvenIncomingCallState.Ended -> false
        is RasvenIncomingCallState.Ringing, is RasvenIncomingCallState.AwaitingMicrophone,
        is RasvenIncomingCallState.Answering, is RasvenIncomingCallState.Active -> true
    }

internal data class RasvenIncomingCall(
    val sessionId: Long,
    val historyId: String,
    val number: String,
    val name: String?,
    val calledNumber: String?,
    val arrivedEpochMillis: Long,
    val connectedAtElapsedMillis: Long? = null,
    val peerHeld: Boolean = false,
)

/** Process-alive Product truth for incoming calls. Platform and protocol actions converge here. */
internal object RasvenCallDomain : RasvenCoreTelecomGateway.Actions {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var context: Context? = null
    private var telecom: RasvenCoreTelecomGateway? = null
    private var notifications: RasvenCallNotification? = null
    private var historyChanged: (() -> Unit)? = null
    private var holdChanged: ((Long, Boolean) -> Unit)? = null
    private val heldBySession = ConcurrentHashMap<Long, Boolean>()
    private val holdTransitionBySession = ConcurrentHashMap<Long, Boolean>()
    private var admission = RasvenIncomingAdmissionPolicy()
    private data class OutgoingPlatformCall(
        val attemptId: Long,
        val telecomId: Long,
        val destination: String,
        val displayIdentity: String?,
        val startedAtEpochMillis: Long,
        val onReady: (Boolean) -> Unit,
        val onCancelled: () -> Unit,
        val historyFinalizer: RasvenOneShotAction = RasvenOneShotAction(),
        val historyId: String = UUID.randomUUID().toString(),
    ) {
        @Volatile var connectedAtElapsedMillis: Long? = null
        @Volatile var sessionId: Long? = null
    }
    private val outgoingCalls = ConcurrentHashMap<Long, OutgoingPlatformCall>()

    var incomingState: RasvenIncomingCallState by mutableStateOf(RasvenIncomingCallState.Idle)
        private set

    fun initialize(appContext: Context) {
        synchronized(lock) {
            if (context != null) return
            context = appContext.applicationContext
            admission = RasvenIncomingAdmissionPolicy(
                appContext.getSharedPreferences("rasven_call_policy_v1", Context.MODE_PRIVATE)
                    .getBoolean("dnd", false))
            RasvenHistoryRepositoryProvider.initialize(appContext)
            RasvenIax2Runtime.initialize(appContext)
            com.rasven.android.product.setup.RasvenIax2RegistrationGateway.initialize(appContext)
            telecom = RasvenCoreTelecomGateway(appContext).also { it.register() }
            notifications = RasvenCallNotification(appContext)
            RasvenIax2Runtime.setIncomingListener(::onSignalingEvent)
        }
    }

    fun setHistoryChangedListener(listener: (() -> Unit)?) {
        synchronized(lock) { historyChanged = listener }
    }

    fun setHoldChangedListener(listener: ((Long, Boolean) -> Unit)?) {
        synchronized(lock) { holdChanged = listener }
    }

    fun isHeld(sessionId: Long): Boolean = heldBySession[sessionId] == true

    /** In-app Hold commits only after protocol/media and Telecom agree. */
    fun requestHeld(sessionId: Long, enabled: Boolean, onResult: (Boolean) -> Unit): Boolean {
        val callId = platformIdForSession(sessionId) ?: return false
        val previous = isHeld(sessionId)
        if (previous == enabled) {
            onResult(true)
            return true
        }
        if (holdTransitionBySession.putIfAbsent(sessionId, enabled) != null) return false
        if (!RasvenIax2Runtime.setHeld(sessionId, enabled)) {
            holdTransitionBySession.remove(sessionId)
            return false
        }
        val platform = telecom
        if (platform == null) {
            RasvenIax2Runtime.setHeld(sessionId, previous)
            holdTransitionBySession.remove(sessionId)
            return false
        }
        platform.setHeld(callId, enabled) { success ->
            holdTransitionBySession.remove(sessionId)
            if (success && platformIdForSession(sessionId) == callId) {
                publishHeld(sessionId, enabled)
                onResult(true)
            } else {
                RasvenIax2Runtime.setHeld(sessionId, previous)
                onResult(false)
            }
        }
        return true
    }

    private fun platformIdForSession(sessionId: Long): Long? {
        val incoming = incomingState as? RasvenIncomingCallState.Active
        if (incoming?.call?.sessionId == sessionId) return sessionId
        return outgoingCalls.values.firstOrNull { it.sessionId == sessionId }?.telecomId
    }

    fun observeSpeaker(sessionId: Long, observer: (Boolean) -> Unit): AutoCloseable =
        platformIdForSession(sessionId)?.let { telecom?.observeSpeaker(it, observer) } ?: AutoCloseable {}

    fun requestSpeaker(sessionId: Long, enabled: Boolean): Boolean =
        platformIdForSession(sessionId)?.let { telecom?.requestSpeaker(it, enabled) } ?: false

    private fun applyPlatformHeld(callId: Long, enabled: Boolean): Boolean {
        val sessionId = (incomingState as? RasvenIncomingCallState.Active)?.call?.sessionId
            ?.takeIf { it == callId }
            ?: outgoingCalls[callId]?.sessionId
            ?: return false
        if (isHeld(sessionId) == enabled) return true
        if (!RasvenIax2Runtime.setHeld(sessionId, enabled)) return false
        publishHeld(sessionId, enabled)
        return true
    }

    private fun publishHeld(sessionId: Long, enabled: Boolean) {
        if (enabled) heldBySession[sessionId] = true else heldBySession.remove(sessionId)
        main.post { synchronized(lock) { holdChanged }?.invoke(sessionId, enabled) }
    }

    private fun clearHeld(sessionId: Long?) {
        if (sessionId != null) holdTransitionBySession.remove(sessionId)
        if (sessionId != null && heldBySession.remove(sessionId) != null) {
            main.post { synchronized(lock) { holdChanged }?.invoke(sessionId, false) }
        }
    }

    fun isDoNotDisturbEnabled(): Boolean = admission.isDoNotDisturbEnabled()

    /** Main-thread policy change and incoming admission run in one serialized Product owner. */
    fun setDoNotDisturb(enabled: Boolean) {
        check(Looper.myLooper() == Looper.getMainLooper())
        admission.setDoNotDisturb(enabled)
        context?.getSharedPreferences("rasven_call_policy_v1", Context.MODE_PRIVATE)
            ?.edit()?.putBoolean("dnd", enabled)?.apply()
        context?.let(com.rasven.android.iax2.RasvenRegistrationForegroundService::refreshPolicy)
    }

    fun addOutgoingPlatformCall(
        attemptId: Long,
        destination: String,
        displayIdentity: String? = null,
        onReady: (Boolean) -> Unit,
        onCancelled: () -> Unit,
    ): Boolean {
        val telecomId = OUTGOING_TELECOM_ID_BASE + attemptId
        val call = OutgoingPlatformCall(attemptId, telecomId, destination, displayIdentity, System.currentTimeMillis(), onReady, onCancelled)
        if (outgoingCalls.putIfAbsent(telecomId, call) != null) return false
        val accepted = telecom?.add(telecomId, false, destination, destination, outgoingActions) ?: false
        if (!accepted) outgoingCalls.remove(telecomId, call)
        return accepted
    }

    fun cancelOutgoingPlatformCall(attemptId: Long, disconnectPlatform: Boolean = true): Boolean {
        val call = outgoingCalls.values.firstOrNull { it.attemptId == attemptId } ?: return false
        if (!outgoingCalls.remove(call.telecomId, call)) return false
        clearHeld(call.sessionId)
        val stopped = RasvenIax2Runtime.cancelOutgoing(attemptId)
        recordOutgoingOnce(call)
        notifications?.clear(call.telecomId)
        if (disconnectPlatform) telecom?.disconnect(call.telecomId, DisconnectCause.LOCAL)
        call.onCancelled()
        return stopped
    }

    fun activateOutgoingPlatformCall(attemptId: Long, sessionId: Long) {
        val call = outgoingCalls.values.firstOrNull { it.attemptId == attemptId } ?: return
        call.sessionId = sessionId
        if (call.connectedAtElapsedMillis == null) call.connectedAtElapsedMillis = System.nanoTime() / 1_000_000L
        notifications?.showOutgoing(call.telecomId, call.destination)
    }

    fun prepareOutgoingPlatformAudio(attemptId: Long, sessionId: Long, onResult: (Boolean) -> Unit) {
        val call = outgoingCalls.values.firstOrNull { it.attemptId == attemptId }
        val platform = telecom
        if (call == null || platform == null) { onResult(false); return }
        call.sessionId = sessionId
        platform.activate(call.telecomId) { success ->
            onResult(success && outgoingCalls[call.telecomId] === call)
        }
    }

    fun finishOutgoingPlatformCall(attemptId: Long, cause: Int = DisconnectCause.REMOTE) {
        val call = outgoingCalls.values.firstOrNull { it.attemptId == attemptId } ?: return
        if (!outgoingCalls.remove(call.telecomId, call)) return
        clearHeld(call.sessionId)
        recordOutgoingOnce(call)
        notifications?.clear(call.telecomId)
        telecom?.disconnect(call.telecomId, cause)
    }

    private fun recordOutgoingOnce(call: OutgoingPlatformCall) {
        call.historyFinalizer.run {
            val endedAtElapsedMillis = System.nanoTime() / 1_000_000L
            RasvenHistoryRepositoryProvider.current().record(
                RasvenHistoryEntry(call.historyId, RasvenHistoryCallClassification.Outgoing,
                    call.displayIdentity ?: call.destination, call.destination, call.startedAtEpochMillis,
                    call.connectedAtElapsedMillis?.let { ((endedAtElapsedMillis - it).coerceAtLeast(0L)) / 1_000L }))
            synchronized(lock) { historyChanged }?.invoke()
        }
    }

    private val outgoingActions = object : RasvenCoreTelecomGateway.Actions {
        override suspend fun answer(callId: Long) = false
        override suspend fun disconnect(callId: Long): Boolean =
            outgoingCalls[callId]?.let { cancelOutgoingPlatformCall(it.attemptId, disconnectPlatform = false) } ?: false
        override suspend fun setActive(callId: Long): Boolean =
            outgoingCalls.containsKey(callId) &&
                (outgoingCalls[callId]?.sessionId?.let { if (isHeld(it)) applyPlatformHeld(callId, false) else true } ?: true)
        override suspend fun setInactive(callId: Long): Boolean = applyPlatformHeld(callId, true)
        override fun added(callId: Long, success: Boolean) {
            main.post {
                val call = outgoingCalls[callId] ?: return@post
                val shown = success && notifications?.showOutgoing(callId, call.destination) == true
                if (!shown) {
                    call.onReady(false)
                    telecom?.disconnect(callId, DisconnectCause.ERROR)
                } else call.onReady(true)
            }
        }
        override fun ended(callId: Long) {
            main.post {
                val call = outgoingCalls.remove(callId) ?: return@post
                clearHeld(call.sessionId)
                notifications?.clear(callId)
                RasvenIax2Runtime.cancelOutgoing(call.attemptId)
                recordOutgoingOnce(call)
                call.onCancelled()
            }
        }
    }

    private fun onSignalingEvent(event: RasvenIax2IncomingEvent) {
        main.post {
            when (event) {
                is RasvenIax2IncomingEvent.PeerHoldChanged -> {
                    val active = incomingState as? RasvenIncomingCallState.Active ?: return@post
                    if (active.call.sessionId != event.sessionId) return@post
                    incomingState = active.copy(call = active.call.copy(peerHeld = event.held))
                }
                is RasvenIax2IncomingEvent.Ringing -> {
                    val decision = admission.decide(event.sessionId)
                    RasvenDiagnostics.debug("RasvenCall", "INCOMING_ADMISSION callId=${event.sessionId} outcome=$decision dnd=${admission.isDoNotDisturbEnabled()} state=${incomingState.javaClass.simpleName}")
                    when (decision) {
                        RasvenIncomingAdmissionPolicy.Outcome.Duplicate -> return@post
                        RasvenIncomingAdmissionPolicy.Outcome.Suppress -> {
                        val suppressed = RasvenIncomingCall(
                            event.sessionId, UUID.randomUUID().toString(), event.callingNumber,
                            event.callingName, event.calledNumber, System.currentTimeMillis())
                        RasvenIax2Runtime.rejectIncoming(event.sessionId, busy = true)
                        RasvenHistoryRepositoryProvider.current().record(
                            historyEntry(suppressed, RasvenHistoryCallClassification.Missed))
                        synchronized(lock) { historyChanged }?.invoke()
                        return@post
                        }
                        RasvenIncomingAdmissionPolicy.Outcome.Present -> Unit
                    }
                    if (incomingState.blocksNewIncomingCall()) {
                        RasvenDiagnostics.debug("RasvenCall", "INCOMING_REJECT_LIVE_OWNER callId=${event.sessionId}")
                        RasvenIax2Runtime.rejectIncoming(event.sessionId)
                        return@post
                    }
                    val call = RasvenIncomingCall(
                        event.sessionId, UUID.randomUUID().toString(), event.callingNumber,
                        event.callingName, event.calledNumber, System.currentTimeMillis())
                    incomingState = RasvenIncomingCallState.Ringing(call)
                    val accepted = telecom?.add(call.sessionId, true,
                        call.name ?: call.number, call.number, this) ?: false
                    if (!accepted) requestReject(call.sessionId)
                }
                is RasvenIax2IncomingEvent.RemoteCancel -> {
                    val call = currentCall() ?: return@post
                    if (call.sessionId != event.sessionId) return@post
                    clearHeld(call.sessionId)
                    val wasActive = incomingState is RasvenIncomingCallState.Active || event.answered
                    notifications?.clear(call.sessionId)
                    telecom?.disconnect(call.sessionId,
                        if (wasActive) DisconnectCause.REMOTE else DisconnectCause.MISSED)
                    if (!wasActive) {
                        val entry = historyEntry(call, RasvenHistoryCallClassification.Missed)
                        RasvenHistoryRepositoryProvider.current().record(entry)
                        synchronized(lock) { historyChanged }?.invoke()
                        incomingState = RasvenIncomingCallState.Missed(entry)
                    } else {
                        RasvenHistoryRepositoryProvider.current().record(
                            historyEntry(call, RasvenHistoryCallClassification.Incoming, System.nanoTime() / 1_000_000L))
                        synchronized(lock) { historyChanged }?.invoke()
                        incomingState = RasvenIncomingCallState.Ended(
                            createCallEndSummary(
                                displayIdentity = call.name,
                                callableNumber = call.number,
                                connectedAtElapsedMillis = call.connectedAtElapsedMillis,
                                endedAtElapsedMillis = System.nanoTime() / 1_000_000L,
                                endedAtEpochMillis = System.currentTimeMillis(),
                                remoteTermination = true,
                                callSessionId = call.sessionId,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun requestAnswer(callId: Long, platformAlreadyAnswering: Boolean = false): Boolean {
        val call = currentCall() ?: return false
        if (call.sessionId != callId) return false
        if (incomingState is RasvenIncomingCallState.Active) return true
        if (incomingState is RasvenIncomingCallState.Answering) return true
        val app = context ?: return false
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            incomingState = RasvenIncomingCallState.AwaitingMicrophone(call)
            return false
        }
        incomingState = RasvenIncomingCallState.Answering(call)
        notifications?.stopRinging(callId)
        fun startMedia() {
            if ((incomingState as? RasvenIncomingCallState.Answering)?.call !== call) return
            notifications?.showOngoing(callId, call.name ?: call.number, incoming = true)
            if (!RasvenIax2Runtime.answerIncoming(callId) { ready ->
                main.post {
                    val current = currentCall()
                    if (current?.sessionId != callId) {
                        return@post
                    }
                    if (incomingState is RasvenIncomingCallState.Active && !ready) {
                        requestEnd(callId)
                        return@post
                    }
                    if (incomingState !is RasvenIncomingCallState.Answering) return@post
                    if (ready) {
                        incomingState = RasvenIncomingCallState.Active(
                            call.copy(connectedAtElapsedMillis = System.nanoTime() / 1_000_000L))
                    } else {
                        requestReject(callId)
                    }
                }
            }) {
            requestReject(callId)
            }
        }
        if (platformAlreadyAnswering) {
            // Complete the platform-originated answer callback before starting capture.
            main.post { startMedia() }
        } else {
            val platform = telecom
            if (platform == null) { requestReject(callId); return false }
            platform.answer(callId) { success ->
                if ((incomingState as? RasvenIncomingCallState.Answering)?.call !== call) return@answer
                if (success) startMedia() else requestReject(callId)
            }
        }
        return true
    }

    fun prepareAnswerFromForeground(callId: Long): Boolean {
        val call = currentCall() ?: return false
        if (call.sessionId != callId) return false
        return requestAnswer(callId)
    }

    fun onMicrophonePermissionResult(callId: Long, granted: Boolean): Boolean {
        val waiting = incomingState as? RasvenIncomingCallState.AwaitingMicrophone ?: return false
        if (waiting.call.sessionId != callId || !granted) return false
        return requestAnswer(callId)
    }

    fun requestReject(callId: Long, disconnectPlatform: Boolean = true): Boolean {
        val call = currentCall() ?: return false
        if (call.sessionId != callId || incomingState is RasvenIncomingCallState.Active) return false
        clearHeld(callId)
        incomingState = RasvenIncomingCallState.Idle
        val rejected = RasvenIax2Runtime.rejectIncoming(callId)
        notifications?.clear(callId)
        if (disconnectPlatform) telecom?.disconnect(callId, DisconnectCause.REJECTED)
        RasvenHistoryRepositoryProvider.current().record(historyEntry(call, RasvenHistoryCallClassification.Missed))
        synchronized(lock) { historyChanged }?.invoke()
        return rejected
    }

    fun requestEnd(callId: Long, disconnectPlatform: Boolean = true): Boolean {
        outgoingCalls[callId]?.let { return cancelOutgoingPlatformCall(it.attemptId, disconnectPlatform) }
        val active = incomingState as? RasvenIncomingCallState.Active ?: return false
        if (active.call.sessionId != callId) return false
        clearHeld(callId)
        val endedAtElapsedMillis = System.nanoTime() / 1_000_000L
        incomingState = RasvenIncomingCallState.Ended(
            createCallEndSummary(
                displayIdentity = active.call.name,
                callableNumber = active.call.number,
                connectedAtElapsedMillis = active.call.connectedAtElapsedMillis,
                endedAtElapsedMillis = endedAtElapsedMillis,
                endedAtEpochMillis = System.currentTimeMillis(),
                callSessionId = active.call.sessionId,
            ),
        )
        val ended = RasvenIax2Runtime.rejectIncoming(callId)
        notifications?.clear(callId)
        if (disconnectPlatform) telecom?.disconnect(callId, DisconnectCause.LOCAL)
        RasvenHistoryRepositoryProvider.current().record(
            historyEntry(active.call, RasvenHistoryCallClassification.Incoming, endedAtElapsedMillis))
        synchronized(lock) { historyChanged }?.invoke()
        return ended
    }

    fun closeMissedSurface() {
        if (incomingState is RasvenIncomingCallState.Missed) incomingState = RasvenIncomingCallState.Idle
    }

    fun closeCallEndSummary() {
        if (incomingState is RasvenIncomingCallState.Ended) incomingState = RasvenIncomingCallState.Idle
    }

    private fun currentCall(): RasvenIncomingCall? = when (val state = incomingState) {
        is RasvenIncomingCallState.Ringing -> state.call
        is RasvenIncomingCallState.AwaitingMicrophone -> state.call
        is RasvenIncomingCallState.Answering -> state.call
        is RasvenIncomingCallState.Active -> state.call
        else -> null
    }

    private fun historyEntry(
        call: RasvenIncomingCall,
        type: RasvenHistoryCallClassification,
        endedAtElapsedMillis: Long? = null,
    ) = RasvenHistoryEntry(
        call.historyId, type, call.name, call.number, call.arrivedEpochMillis,
        if (type == RasvenHistoryCallClassification.Incoming) {
            call.connectedAtElapsedMillis?.let { started ->
                endedAtElapsedMillis?.let { ended -> ((ended - started).coerceAtLeast(0L)) / 1_000L }
            }
        } else null,
    )

    override suspend fun answer(callId: Long): Boolean {
        // Telecom callbacks (including lock-screen and remote surfaces) have a short transaction
        // deadline. Accept the request and queue IAX/media startup; publish Active only when real
        // audio is ready. A later startup failure tears down the same call through requestReject.
        return requestAnswer(callId, platformAlreadyAnswering = true)
    }

    override suspend fun disconnect(callId: Long): Boolean =
        requestReject(callId, disconnectPlatform = false) || requestEnd(callId, disconnectPlatform = false)

    override suspend fun setActive(callId: Long): Boolean =
        (incomingState as? RasvenIncomingCallState.Active)?.call?.sessionId == callId &&
            (!isHeld(callId) || applyPlatformHeld(callId, false))

    override suspend fun setInactive(callId: Long): Boolean = applyPlatformHeld(callId, true)

    override fun added(callId: Long, success: Boolean) {
        main.post {
            val call = currentCall()
            if (call?.sessionId != callId) return@post
            if (!success) {
                RasvenDiagnostics.debug("RasvenCall", "INCOMING_PLATFORM_FAILED callId=$callId")
                requestReject(callId)
            } else {
                // CallStyle is a presentation enhancement; a notification permission denial must
                // never convert an admitted live PBX call into a reject. Telecom remains owner.
                val posted = notifications?.showIncoming(callId, call.name ?: call.number) == true
                RasvenDiagnostics.debug("RasvenCall", "INCOMING_PRESENTATION callId=$callId posted=$posted")
            }
        }
    }

    override fun ended(callId: Long) {
        main.post {
            if (currentCall()?.sessionId == callId) {
                if (incomingState is RasvenIncomingCallState.Active) requestEnd(callId, disconnectPlatform = false)
                else requestReject(callId, disconnectPlatform = false)
            }
            notifications?.clear(callId)
        }
    }

    private const val OUTGOING_TELECOM_ID_BASE = 1L shl 60
}
