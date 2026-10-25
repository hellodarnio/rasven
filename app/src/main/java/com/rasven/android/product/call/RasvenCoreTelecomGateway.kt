package com.rasven.android.product.call

import com.rasven.android.designsystem.foundation.rasvenString
import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import com.rasven.android.R
import com.rasven.android.diagnostics.RasvenDiagnostics
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Platform representation only. Rasven's call domain remains the owner of call transitions. */
internal class RasvenCoreTelecomGateway(context: Context) : AutoCloseable {
    internal interface Actions {
        suspend fun answer(callId: Long): Boolean
        suspend fun disconnect(callId: Long): Boolean
        suspend fun setActive(callId: Long): Boolean
        suspend fun setInactive(callId: Long): Boolean
        fun added(callId: Long, success: Boolean)
        fun ended(callId: Long)
    }

    private class Entry(val id: Long, val actions: Actions) {
        @Volatile var scope: CallControlScope? = null
        @Volatile var job: Job? = null
        val added = AtomicBoolean(false)
        @Volatile var endpoints: List<CallEndpointCompat> = emptyList()
        @Volatile var currentEndpoint: CallEndpointCompat? = null
        val routeObservers = java.util.concurrent.CopyOnWriteArraySet<(Boolean) -> Unit>()
    }

    private val app = context.applicationContext
    private val manager = CallsManager(app)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val entries = ConcurrentHashMap<Long, Entry>()
    private val registered = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    fun register(): Boolean {
        if (closed.get()) return false
        if (registered.compareAndSet(false, true)) {
            try {
                manager.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE)
            } catch (failure: Exception) {
                registered.set(false)
                return false
            }
        }
        return true
    }

    fun add(
        callId: Long,
        incoming: Boolean,
        displayName: String,
        address: String,
        actions: Actions,
    ): Boolean {
        if (!register()) return false
        val entry = Entry(callId, actions)
        if (entries.putIfAbsent(callId, entry) != null) return false
        val attributes = CallAttributesCompat(
            displayName = displayName.ifBlank { app.rasvenString(R.string.incoming_call_unknown) },
            address = Uri.fromParts("sip", address.ifBlank { "unknown" }, null),
            direction = if (incoming) CallAttributesCompat.DIRECTION_INCOMING else CallAttributesCompat.DIRECTION_OUTGOING,
            callType = CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
        )
        entry.job = coroutineScope.launch {
            try {
                manager.addCall(
                    attributes,
                    onAnswer = {
                        RasvenDiagnostics.debug("RasvenTelecom", "REMOTE_ANSWER_REQUEST callId=$callId")
                        val answerAccepted = coordinateIncomingCallAnswer(
                            answerInRasven = { actions.answer(callId) },
                            disconnectOnFailure = { actions.disconnect(callId) },
                        )
                        RasvenDiagnostics.debug("RasvenTelecom", "REMOTE_ANSWER_COMPLETED callId=$callId accepted=$answerAccepted")
                        if (!answerAccepted) throw IllegalStateException("Call is no longer answerable")
                    },
                    onDisconnect = { if (!actions.disconnect(callId)) throw IllegalStateException("Call is no longer active") },
                    onSetActive = { if (!actions.setActive(callId)) throw IllegalStateException("Cannot activate call") },
                    onSetInactive = { if (!actions.setInactive(callId)) throw IllegalStateException("Hold is unavailable") },
                ) {
                    entry.scope = this
                    launch { availableEndpoints.collect { entry.endpoints = it } }
                    launch {
                        currentCallEndpoint.collect { endpoint ->
                            if (entries[callId] === entry) {
                                entry.currentEndpoint = endpoint
                                entry.routeObservers.forEach { it(endpoint.type == CallEndpointCompat.TYPE_SPEAKER) }
                            }
                        }
                    }
                    if (entry.added.compareAndSet(false, true)) actions.added(callId, true)
                }
            } catch (failure: Exception) {
                RasvenDiagnostics.error("RasvenTelecom", "CALL_SESSION_FAILURE callId=$callId type=${failure.javaClass.simpleName}")
                if (entry.added.compareAndSet(false, true)) actions.added(callId, false)
            } finally {
                RasvenDiagnostics.debug("RasvenTelecom", "CALL_SESSION_ENDED callId=$callId")
                entries.remove(callId, entry)
                entry.routeObservers.clear()
                actions.ended(callId)
            }
        }
        return true
    }

    fun isAdded(callId: Long): Boolean = entries[callId]?.scope != null

    fun observeSpeaker(callId: Long, observer: (Boolean) -> Unit): AutoCloseable {
        val entry = entries[callId] ?: return AutoCloseable {}
        entry.routeObservers.add(observer)
        entry.currentEndpoint?.let { observer(it.type == CallEndpointCompat.TYPE_SPEAKER) }
        return AutoCloseable { entry.routeObservers.remove(observer) }
    }

    fun requestSpeaker(callId: Long, enabled: Boolean): Boolean {
        val entry = entries[callId] ?: return false
        val target = if (enabled) entry.endpoints.firstOrNull { it.type == CallEndpointCompat.TYPE_SPEAKER }
        else entry.endpoints.firstOrNull { it.type == CallEndpointCompat.TYPE_EARPIECE }
            ?: entry.endpoints.firstOrNull { it.type == CallEndpointCompat.TYPE_WIRED_HEADSET || it.type == CallEndpointCompat.TYPE_BLUETOOTH }
        if (target == null) return false
        perform(callId, {}) { it.requestEndpointChange(target) }
        // Selection is published exclusively by currentCallEndpoint, never by this request.
        return true
    }

    fun answer(callId: Long, onResult: (Boolean) -> Unit) = perform(callId, onResult) { scope ->
        scope.answer(CallAttributesCompat.CALL_TYPE_AUDIO_CALL)
    }

    fun activate(callId: Long, onResult: (Boolean) -> Unit) = perform(callId, onResult) { it.setActive() }

    fun setHeld(callId: Long, enabled: Boolean, onResult: (Boolean) -> Unit) =
        perform(callId, onResult) { if (enabled) it.setInactive() else it.setActive() }

    fun disconnect(callId: Long, cause: Int, onResult: (Boolean) -> Unit = {}) =
        perform(callId, onResult) { it.disconnect(DisconnectCause(cause)) }

    private fun perform(
        callId: Long,
        onResult: (Boolean) -> Unit,
        operation: suspend (CallControlScope) -> CallControlResult,
    ) {
        val scope = entries[callId]?.scope
        if (scope == null) {
            onResult(false)
            return
        }
        coroutineScope.launch {
            val success = try { operation(scope) is CallControlResult.Success } catch (_: Exception) { false }
            onResult(success)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        entries.values.forEach { it.job?.cancel() }
        entries.clear()
        coroutineScope.cancel()
    }
}

/**
 * A Telecom-originated answer is completed by returning from the callback. Core-Telecom then
 * acknowledges the platform transaction. Calling CallControlScope.answer() here would submit
 * a second platform transaction while the first still awaits this callback. Only app-originated
 * answers use CallControlScope.answer(); this path accepts the Product request and retires the
 * signaling leg if Product cannot accept it.
 */
internal suspend fun coordinateIncomingCallAnswer(
    answerInRasven: suspend () -> Boolean,
    disconnectOnFailure: suspend () -> Boolean,
): Boolean {
    suspend fun cleanup() {
        try {
            withContext(NonCancellable) { disconnectOnFailure() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The failed CallControl transaction itself also retires the platform call.
        }
    }

    val productAccepted = try {
        answerInRasven()
    } catch (cancelled: CancellationException) {
        cleanup()
        throw cancelled
    } catch (_: Exception) {
        false
    }
    if (productAccepted) return true

    cleanup()
    return false
}
