package com.rasven.android.iax2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.rasven.android.diagnostics.RasvenDiagnostics
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Process-scoped owner for real signaling plus the first real two-way audio media foundation. */
internal object RasvenIax2Runtime {
    private val lock = Any()
    private val mediaExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "rasven-media-start").apply { isDaemon = true } }
    private val outgoingRingback = RasvenOutgoingRingback()

    private val productionMediaOffer = RasvenIax2MediaOffer(
        format = RasvenIax2.MediaFormat.PCMU,
        capability = RasvenIax2.MediaFormat.IMPLEMENTED_CAPABILITY,
        // RFC 5456 permits CODEC PREFS to be absent; CAPABILITY + FORMAT are sufficient.
        codecPreferences = "",
    )

    private var appContext: Context? = null
    private var engine: RasvenIax2SignalingEngine? = null
    private var mediaEngine: RasvenCallMediaEngine? = null
    private var mediaAttemptId: Long? = null
    private var currentOutgoingAttemptId: Long? = null
    private var currentIncomingSessionId: Long? = null
    private var incomingListener: ((RasvenIax2IncomingEvent) -> Unit)? = null

    fun initialize(context: Context) {
        synchronized(lock) { appContext = context.applicationContext }
    }

    fun setIncomingListener(listener: ((RasvenIax2IncomingEvent) -> Unit)?) {
        val current = synchronized(lock) {
            incomingListener = listener
            engine
        }
        current?.setIncomingListener(::onIncomingEvent)
    }

    private fun onIncomingEvent(event: RasvenIax2IncomingEvent) {
        if (event is RasvenIax2IncomingEvent.RemoteCancel) {
            val snapshot = synchronized(lock) {
                val media = if (mediaEngine != null && currentIncomingSessionId == event.sessionId) detachMediaLocked() else null
                if (currentIncomingSessionId == event.sessionId) currentIncomingSessionId = null
                media to appContext
            }
            closeMedia(snapshot.first)
            if (snapshot.first != null) snapshot.second?.let(::stopMediaServiceIfIdle)
        }
        val callback = synchronized(lock) { incomingListener }
        callback?.invoke(event)
    }

    fun register(
        account: RasvenIax2Account,
        onEvent: (RasvenIax2RegistrationEvent) -> Unit,
    ) {
        val context = synchronized(lock) { appContext }
        if (context == null || !RasvenRegistrationForegroundService.start(context)) {
            onEvent(RasvenIax2RegistrationEvent.Failed)
            return
        }
        val replacement = RasvenIax2SignalingEngine(
            account = account,
            transport = RasvenIax2UdpTransport(account.server, account.port, diagnostic = ::logProtocolDiagnostic),
            scheduler = RasvenIax2ExecutorScheduler(),
            diagnostic = ::logProtocolDiagnostic,
        )
        replacement.setIncomingListener(::onIncomingEvent)
        val detached = synchronized(lock) {
            val oldEngine = engine
            val oldMedia = detachMediaLocked()
            currentOutgoingAttemptId = null
            currentIncomingSessionId = null
            engine = replacement
            Triple(oldEngine, oldMedia, appContext)
        }
        // Never close a media/signaling owner while holding the runtime monitor: their cleanup re-enters
        // signaling locks and callbacks can arrive in the opposite order.
        closeMedia(detached.second)
        outgoingRingback.stopAll()
        detached.first?.close()
        detached.third?.let(RasvenCallMediaForegroundService::stop)
        replacement.register { event ->
            if (synchronized(lock) { engine !== replacement }) return@register
            logProtocolDiagnostic(if (event == RasvenIax2RegistrationEvent.Registered) "REGISTRATION_RESULT=registered" else "REGISTRATION_RESULT=failed")
            if (event == RasvenIax2RegistrationEvent.Registered) RasvenRegistrationForegroundService.start(context, ready = true)
            else if (replacement.lastRegistrationFailureRetryable) RasvenRegistrationForegroundService.start(context, ready = false)
            else RasvenRegistrationForegroundService.stop(context)
            onEvent(event)
        }
    }

    fun isRegistrationAlive(): Boolean = synchronized(lock) { engine != null }
    fun registrationFailureRetryable(): Boolean = synchronized(lock) { engine?.lastRegistrationFailureRetryable == true }

    /** Release the registrar binding before closing the local transport on explicit user disconnect. */
    fun releaseRegistrationAndInvalidate(onComplete: (Boolean) -> Unit) {
        val current = synchronized(lock) { engine }
        if (current == null) {
            invalidate()
            onComplete(true)
            return
        }
        current.releaseRegistration { released ->
            if (synchronized(lock) { engine === current }) invalidate()
            onComplete(released)
        }
    }

    fun startOutgoing(
        attemptId: Long,
        destination: String,
        preparePlatformAudio: (Long, (Boolean) -> Unit) -> Unit,
        onEvent: (RasvenIax2OutgoingEvent) -> Unit,
    ): RasvenIax2StartResult {
        val snapshot = synchronized(lock) { appContext to engine }
        val context = snapshot.first ?: return RasvenIax2StartResult.TransportUnavailable
        val current = snapshot.second ?: return RasvenIax2StartResult.NotRegistered
        val reserved = synchronized(lock) {
            if (engine !== current || currentOutgoingAttemptId != null || currentIncomingSessionId != null || mediaEngine != null) false
            else { currentOutgoingAttemptId = attemptId; true }
        }
        if (!reserved) return RasvenIax2StartResult.TransportUnavailable
        // This call is reached only from the user-owned B6 call action after JIT RECORD_AUDIO permission.
        // Android 14+ enforces the visible/while-in-use microphone FGS precondition at this boundary.
        if (!RasvenCallMediaForegroundService.start(context)) {
            synchronized(lock) { if (currentOutgoingAttemptId == attemptId) currentOutgoingAttemptId = null }
            stopMediaServiceIfIdle(context)
            return RasvenIax2StartResult.TransportUnavailable
        }
        val result = current.startOutgoing(
            attemptId = attemptId,
            destination = destination,
            mediaOffer = productionMediaOffer,
        ) { event ->
            when (event) {
                RasvenIax2OutgoingEvent.Ringing -> {
                    if (synchronized(lock) { currentOutgoingAttemptId == attemptId && engine === current }) {
                        outgoingRingback.start(attemptId)
                        onEvent(event)
                    }
                }
                is RasvenIax2OutgoingEvent.Connected -> {
                    outgoingRingback.stop(attemptId)
                    preparePlatformAudio(event.sessionId) { ready ->
                    if (ready) beginConnectedMedia(
                    context = context,
                    signaling = current,
                    attemptId = attemptId,
                    event = event,
                    onEvent = onEvent,
                    ) else {
                        cancelOutgoing(attemptId)
                        onEvent(RasvenIax2OutgoingEvent.OtherFailure)
                    }
                    }
                }
                is RasvenIax2OutgoingEvent.PeerHoldChanged -> onEvent(event)
                RasvenIax2OutgoingEvent.Busy,
                RasvenIax2OutgoingEvent.Rejected,
                RasvenIax2OutgoingEvent.NoAnswer,
                RasvenIax2OutgoingEvent.NetworkFailure,
                RasvenIax2OutgoingEvent.RemoteHangup,
                RasvenIax2OutgoingEvent.OtherFailure -> {
                    outgoingRingback.stop(attemptId)
                    val detachedMedia = synchronized(lock) {
                        if (currentOutgoingAttemptId == attemptId) currentOutgoingAttemptId = null
                        if (mediaAttemptId == attemptId) detachMediaLocked() else null
                    }
                    closeMedia(detachedMedia)
                    stopMediaServiceIfIdle(context)
                    onEvent(event)
                }
            }
        }
        if (result != RasvenIax2StartResult.Accepted) {
            outgoingRingback.stop(attemptId)
            synchronized(lock) { if (currentOutgoingAttemptId == attemptId) currentOutgoingAttemptId = null }
            stopMediaServiceIfIdle(context)
        }
        return result
    }

    private fun beginConnectedMedia(
        context: Context,
        signaling: RasvenIax2SignalingEngine,
        attemptId: Long,
        event: RasvenIax2OutgoingEvent.Connected,
        onEvent: (RasvenIax2OutgoingEvent) -> Unit,
    ) {
        mediaExecutor.execute {
            val stillCurrent = synchronized(lock) { engine === signaling && currentOutgoingAttemptId == attemptId }
            if (!stillCurrent) {
                signaling.cancelOutgoing(attemptId)
                stopMediaServiceIfIdle(context)
                return@execute
            }
            val channel = signaling.openMediaChannel(event.sessionId)
            if (channel == null) {
                synchronized(lock) { if (currentOutgoingAttemptId == attemptId) currentOutgoingAttemptId = null }
                signaling.cancelOutgoing(attemptId)
                stopMediaServiceIfIdle(context)
                onEvent(RasvenIax2OutgoingEvent.OtherFailure)
                return@execute
            }

            val media = RasvenCallMediaEngine(RasvenAndroidAudioIoFactory(context, telecomOwnsAudio = true))
            val format = RasvenAudioMediaFormat(channel.codec)
            val mediaFailed = AtomicBoolean(false)
            val start = media.start(event.sessionId, channel, format) { reason ->
                mediaFailed.set(true)
                synchronized(lock) {
                    if (mediaEngine === media) {
                        mediaEngine = null
                        mediaAttemptId = null
                    }
                    if (currentOutgoingAttemptId == attemptId) currentOutgoingAttemptId = null
                }
                // The failing media owner may invoke this callback from its own capture/playout worker.
                // close() is idempotent and shutdownNow() is safe without waiting on that worker.
                media.close()
                signaling.cancelOutgoing(attemptId)
                stopMediaServiceIfIdle(context)
                onEvent(
                    if (reason == RasvenMediaFailure.MediaTransportFailure) RasvenIax2OutgoingEvent.NetworkFailure
                    else RasvenIax2OutgoingEvent.OtherFailure,
                )
            }

            when (start) {
                RasvenMediaStartResult.Started -> {
                    var previousMedia: RasvenCallMediaEngine? = null
                    val publish = synchronized(lock) {
                        if (mediaFailed.get() || engine !== signaling || currentOutgoingAttemptId != attemptId) {
                            false
                        } else {
                            previousMedia = detachMediaLocked()
                            mediaEngine = media
                            mediaAttemptId = attemptId
                            true
                        }
                    }
                    closeMedia(previousMedia)
                    if (publish) {
                        // Product Connected is emitted only after negotiated codec + focus + real AudioRecord/AudioTrack startup.
                        onEvent(event)
                    } else {
                        media.close()
                        signaling.cancelOutgoing(attemptId)
                        stopMediaServiceIfIdle(context)
                    }
                }
                is RasvenMediaStartResult.Failed -> {
                    synchronized(lock) { if (currentOutgoingAttemptId == attemptId) currentOutgoingAttemptId = null }
                    media.close()
                    signaling.cancelOutgoing(attemptId)
                    stopMediaServiceIfIdle(context)
                    onEvent(RasvenIax2OutgoingEvent.OtherFailure)
                }
            }
        }
    }

    fun cancelOutgoing(attemptId: Long): Boolean {
        outgoingRingback.stop(attemptId)
        val snapshot = synchronized(lock) {
            if (currentOutgoingAttemptId == attemptId) currentOutgoingAttemptId = null
            val detachedMedia = if (mediaAttemptId == attemptId) detachMediaLocked() else null
            Triple(appContext, engine, detachedMedia)
        }
        closeMedia(snapshot.third)
        snapshot.first?.let(::stopMediaServiceIfIdle)
        return snapshot.second?.cancelOutgoing(attemptId) ?: false
    }

    /** The permission check is repeated at the media boundary after the Product permission gate. */
    fun answerIncoming(sessionId: Long, onReady: (Boolean) -> Unit): Boolean {
        val snapshot = synchronized(lock) { Triple(appContext, engine, mediaEngine) }
        val context = snapshot.first ?: return false
        val signaling = snapshot.second ?: return false
        if (snapshot.third != null || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val reserved = synchronized(lock) {
            if (engine !== signaling || mediaEngine != null || currentOutgoingAttemptId != null || currentIncomingSessionId != null) false
            else { currentIncomingSessionId = sessionId; true }
        }
        if (!reserved) return false
        if (!RasvenCallMediaForegroundService.start(context)) {
            synchronized(lock) { if (currentIncomingSessionId == sessionId) currentIncomingSessionId = null }
            stopMediaServiceIfIdle(context)
            return false
        }
        val accepted = signaling.answerIncoming(sessionId)
        if (!accepted) {
            synchronized(lock) { if (currentIncomingSessionId == sessionId) currentIncomingSessionId = null }
            stopMediaServiceIfIdle(context)
            return false
        }
        mediaExecutor.execute {
            val current = synchronized(lock) { engine === signaling && currentIncomingSessionId == sessionId }
            if (!current) {
                stopMediaServiceIfIdle(context)
                onReady(false)
                return@execute
            }
            val channel = signaling.openMediaChannel(sessionId)
            if (channel == null) {
                signaling.rejectIncoming(sessionId)
                synchronized(lock) { if (currentIncomingSessionId == sessionId) currentIncomingSessionId = null }
                stopMediaServiceIfIdle(context)
                onReady(false)
                return@execute
            }
            val media = RasvenCallMediaEngine(RasvenAndroidAudioIoFactory(context, telecomOwnsAudio = true))
            val started = media.start(sessionId, channel, RasvenAudioMediaFormat(channel.codec)) {
                synchronized(lock) {
                    if (mediaEngine === media) detachMediaLocked()
                    if (currentIncomingSessionId == sessionId) currentIncomingSessionId = null
                }
                media.close()
                signaling.rejectIncoming(sessionId)
                stopMediaServiceIfIdle(context)
                onReady(false)
            }
            if (started == RasvenMediaStartResult.Started) {
                val publish = synchronized(lock) {
                    if (engine !== signaling || currentIncomingSessionId != sessionId || mediaEngine != null) false
                    else {
                        mediaEngine = media
                        true
                    }
                }
                if (publish) onReady(true)
                else {
                    media.close()
                    signaling.rejectIncoming(sessionId)
                    synchronized(lock) { if (currentIncomingSessionId == sessionId) currentIncomingSessionId = null }
                    stopMediaServiceIfIdle(context)
                    onReady(false)
                }
            } else {
                media.close()
                signaling.rejectIncoming(sessionId)
                synchronized(lock) { if (currentIncomingSessionId == sessionId) currentIncomingSessionId = null }
                stopMediaServiceIfIdle(context)
                onReady(false)
            }
        }
        return true
    }

    fun rejectIncoming(sessionId: Long, busy: Boolean = false): Boolean {
        val snapshot = synchronized(lock) {
            val activeIncoming = currentIncomingSessionId == sessionId
            val media = if (activeIncoming) detachMediaLocked() else null
            if (activeIncoming) currentIncomingSessionId = null
            Triple(engine, media, appContext to activeIncoming)
        }
        val rejected = snapshot.first?.rejectIncoming(sessionId, busy) ?: false
        closeMedia(snapshot.second)
        if (snapshot.third.second) snapshot.third.first?.let(::stopMediaServiceIfIdle)
        return rejected
    }

    /** Media owns the applied uplink state; stale session commands are rejected. */
    fun setMuted(sessionId: Long, enabled: Boolean): Boolean {
        val current = synchronized(lock) { mediaEngine } ?: return false
        return current.setMuted(sessionId, enabled)
    }

    fun isMuted(sessionId: Long): Boolean {
        val current = synchronized(lock) { mediaEngine } ?: return false
        return current.isMuted(sessionId)
    }

    fun setHeld(sessionId: Long, enabled: Boolean): Boolean {
        val snapshot = synchronized(lock) { engine to mediaEngine }
        val signaling = snapshot.first ?: return false
        val media = snapshot.second ?: return false
        if (!signaling.setHeld(sessionId, enabled)) return false
        if (media.setHeld(sessionId, enabled)) return true
        signaling.setHeld(sessionId, !enabled)
        return false
    }

    /** Direct in-call digits never enter the Contacts prefix or outgoing-call path. */
    fun sendDtmf(sessionId: Long, digit: Char): Boolean {
        val current = synchronized(lock) { engine } ?: return false
        return current.sendDtmf(sessionId, digit)
    }

    fun invalidate(keepStandby: Boolean = false) {
        val detached = synchronized(lock) {
            val oldMedia = detachMediaLocked()
            currentOutgoingAttemptId = null
            currentIncomingSessionId = null
            val oldEngine = engine
            engine = null
            Triple(oldEngine, oldMedia, appContext)
        }
        closeMedia(detached.second)
        outgoingRingback.stopAll()
        detached.first?.close()
        detached.third?.let(RasvenCallMediaForegroundService::stop)
        if (!keepStandby) detached.third?.let(RasvenRegistrationForegroundService::stop)
    }

    /** Detach under the runtime monitor; close the returned owner only after releasing that monitor. */
    private fun detachMediaLocked(): RasvenCallMediaEngine? {
        val value = mediaEngine
        mediaEngine = null
        mediaAttemptId = null
        return value
    }

    private fun closeMedia(media: RasvenCallMediaEngine?) {
        try { media?.close() } catch (_: Exception) { }
    }

    /** Old asynchronous call cleanup must not stop the foreground service owned by a newer call. */
    private fun stopMediaServiceIfIdle(context: Context) = synchronized(lock) {
        if (shouldStopCallMediaService(
                mediaEnginePresent = mediaEngine != null,
                mediaAttemptId = mediaAttemptId,
                outgoingAttemptId = currentOutgoingAttemptId,
                incomingSessionId = currentIncomingSessionId,
            )) {
            RasvenCallMediaForegroundService.stop(context)
        }
    }

    /** Safe protocol diagnostics only: packet metadata/status, never credentials, numbers, or media. */
    private fun logProtocolDiagnostic(message: String) {
        RasvenDiagnostics.debug("RasvenIax2", message)
    }
}

internal fun shouldStopCallMediaService(
    mediaEnginePresent: Boolean,
    mediaAttemptId: Long?,
    outgoingAttemptId: Long?,
    incomingSessionId: Long?,
): Boolean = !mediaEnginePresent && mediaAttemptId == null && outgoingAttemptId == null && incomingSessionId == null
