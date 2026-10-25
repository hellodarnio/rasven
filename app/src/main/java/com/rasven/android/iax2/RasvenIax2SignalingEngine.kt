package com.rasven.android.iax2

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class RasvenIax2Account(
    val server: String,
    val port: Int,
    val username: String,
    val password: String,
) {
    override fun toString(): String = "RasvenIax2Account([redacted], port=$port, [redacted], [redacted])"
}

internal data class RasvenIax2MediaOffer(
    val format: Int,
    val capability: Int,
    val codecPreferences: String = "",
)

internal sealed interface RasvenIax2RegistrationEvent {
    data object Registered : RasvenIax2RegistrationEvent
    data object Failed : RasvenIax2RegistrationEvent
}

internal sealed interface RasvenIax2OutgoingEvent {
    data object Ringing : RasvenIax2OutgoingEvent
    data object Busy : RasvenIax2OutgoingEvent
    data object Rejected : RasvenIax2OutgoingEvent
    data object NoAnswer : RasvenIax2OutgoingEvent
    data object NetworkFailure : RasvenIax2OutgoingEvent
    data object RemoteHangup : RasvenIax2OutgoingEvent
    data object OtherFailure : RasvenIax2OutgoingEvent
    data class Connected(val sessionId: Long) : RasvenIax2OutgoingEvent
    data class PeerHoldChanged(val held: Boolean) : RasvenIax2OutgoingEvent
}

internal sealed interface RasvenIax2IncomingEvent {
    data class Ringing(
        val sessionId: Long,
        val callingNumber: String,
        val callingName: String?,
        val calledNumber: String?,
    ) : RasvenIax2IncomingEvent
    data class RemoteCancel(val sessionId: Long, val answered: Boolean) : RasvenIax2IncomingEvent
    data class PeerHoldChanged(val sessionId: Long, val held: Boolean) : RasvenIax2IncomingEvent
}

internal enum class RasvenIax2StartResult {
    Accepted,
    TransportUnavailable,
    NotRegistered,
    MediaCapabilityUnavailable,
}

internal fun interface RasvenIax2Cancellation { fun cancel() }
internal fun interface RasvenIax2Clock { fun nowMillis(): Long }
internal interface RasvenIax2Scheduler : AutoCloseable {
    fun schedule(delayMs: Long, task: () -> Unit): RasvenIax2Cancellation
}

internal class RasvenIax2ExecutorScheduler : RasvenIax2Scheduler {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "rasven-iax2-scheduler").apply { isDaemon = true }
    }

    override fun schedule(delayMs: Long, task: () -> Unit): RasvenIax2Cancellation {
        val future = executor.schedule(task, delayMs, TimeUnit.MILLISECONDS)
        return RasvenIax2Cancellation { future.cancel(false) }
    }

    override fun close() { executor.shutdownNow() }
}

internal class RasvenIax2CallNumberAllocator {
    private val next = AtomicInteger(1)
    private val allocated = LinkedHashSet<Int>()

    @Synchronized fun allocate(): Int {
        repeat(RasvenIax2.MAX_CALL_NUMBER) {
            val candidate = next.getAndUpdate { current -> if (current >= RasvenIax2.MAX_CALL_NUMBER) 1 else current + 1 }
            if (allocated.add(candidate)) return candidate
        }
        throw IllegalStateException("No IAX2 call number available")
    }

    @Synchronized fun release(callNumber: Int) { allocated.remove(callNumber) }
    @Synchronized fun isAllocated(callNumber: Int): Boolean = allocated.contains(callNumber)
}

/**
 * RFC 5456 signaling-only session owner. The UDP transport is real; tests inject a packet transport.
 * No media capability is invented: callers must supply a truthful media offer or the call fails closed.
 */
internal class RasvenIax2SignalingEngine(
    private val account: RasvenIax2Account,
    private val transport: RasvenIax2PacketTransport,
    private val scheduler: RasvenIax2Scheduler,
    private val clock: RasvenIax2Clock = RasvenIax2Clock(System::currentTimeMillis),
    private val callTimeoutMs: Long = 45_000L,
    private val diagnostic: (String) -> Unit = {},
) : AutoCloseable {
    companion object {
        // Reserve a process-random low namespace, distinct from Product's outgoing platform IDs.
        // A fresh process also must not accept an obsolete notification's session identity.
        private val processSessionIds = AtomicLong(java.security.SecureRandom().nextLong() and ((1L shl 59) - 1))
        private const val INITIAL_RETRANSMIT_MS = 500L
        private const val MAX_RETRANSMIT_MS = 10_000L
        private const val MAX_RETRANSMISSIONS = 4
        // Covers DNS/socket preparation as well as a partially completed registration exchange.
        private const val REGISTRATION_DEADLINE_MS = 30_000L
        private const val REGISTRATION_RELEASE_DEADLINE_MS = 10_000L
        private const val DEFAULT_REFRESH_SECONDS = 60
        private const val LOCAL_CANCEL_RETIRE_MS = 16_000L
    }

    private sealed class Session(
        val localCallNumber: Int,
        val sessionId: Long,
        val startMillis: Long,
    ) {
        var remoteCallNumber: Int? = null
        var nextOutgoingSequence: Int = 0
        var nextIncomingSequence: Int = 0
        var lastIncomingTimestamp: Long? = null
        var terminal = false
        val pending = LinkedHashMap<Int, Pending>()
    }

    private class RegistrationSession(
        localCallNumber: Int,
        sessionId: Long,
        startMillis: Long,
        val callback: (RasvenIax2RegistrationEvent) -> Unit,
        val releasing: Boolean = false,
        val releaseCallback: ((Boolean) -> Unit)? = null,
    ) : Session(localCallNumber, sessionId, startMillis)

    private class MonitorSession(local: Int, id: Long, start: Long, val pokeTimestamp: Long) : Session(local, id, start)

    private sealed class MediaSession(
        localCallNumber: Int,
        sessionId: Long,
        startMillis: Long,
    ) : Session(localCallNumber, sessionId, startMillis) {
        var connected = false
        var held = false
        var selectedCodec: RasvenAudioCodec? = null
        var incomingVoiceCodec: RasvenAudioCodec? = null
        var mediaReceiver: ((RasvenIax2InboundVoice) -> Unit)? = null
        var lagReceiver: ((() -> Unit) -> Unit)? = null
        val miniTimestampExtender = RasvenIax2MiniTimestampExtender()
    }

    private class OutgoingSession(
        localCallNumber: Int,
        sessionId: Long,
        startMillis: Long,
        val attemptId: Long,
        val destination: String,
        val mediaOffer: RasvenIax2MediaOffer,
        val callback: (RasvenIax2OutgoingEvent) -> Unit,
    ) : MediaSession(localCallNumber, sessionId, startMillis) {
        var cancelling = false
        var ringingDelivered = false
        var productTerminalDelivered = false
        var terminationSequence: Int? = null
        var timeout: RasvenIax2Cancellation? = null
        var retirement: RasvenIax2Cancellation? = null
    }

    private class IncomingSession(
        localCallNumber: Int,
        sessionId: Long,
        startMillis: Long,
        codec: RasvenAudioCodec,
        val callingNumber: String,
        val callingName: String?,
        val calledNumber: String?,
        val newTimestamp: Long,
    ) : MediaSession(localCallNumber, sessionId, startMillis) {
        init { selectedCodec = codec }
        var localTermination = false
    }

    private data class Pending(
        val frame: RasvenIax2FullFrame,
        var retransmissions: Int = 0,
        var timer: RasvenIax2Cancellation? = null,
    )

    private val lock = Any()
    private val allocator = RasvenIax2CallNumberAllocator()
    // Product/Telecom/admission owners outlive a replaced signaling engine. Engine-local IDs
    // would classify a genuine post-reconnect call as a duplicate or accept an old action.
    private val sessionIds = processSessionIds
    private val sessions = LinkedHashMap<Int, Session>()
    private val outgoingByAttempt = LinkedHashMap<Long, OutgoingSession>()
    private val incomingByRemoteCall = LinkedHashMap<Int, IncomingSession>()
    private val locallyRejectedNew = LinkedHashMap<Pair<Int, Long>, Long>()
    private var incomingCallback: ((RasvenIax2IncomingEvent) -> Unit)? = null
    private var registrationRefresh: RasvenIax2Cancellation? = null
    private var registrationDeadline: RasvenIax2Cancellation? = null
    private var registrationReleaseDeadline: RasvenIax2Cancellation? = null
    private var registrationCallback: ((RasvenIax2RegistrationEvent) -> Unit)? = null
    private var transportFailed = false
    private var closed = false

    @Volatile var isRegistered: Boolean = false
        private set
    @Volatile var lastRegistrationFailureRetryable: Boolean = false
        private set

    init {
        transport.start(
            onReady = { synchronized(lock) { if (!transportFailed) registrationCallback?.let(::beginRegistrationLocked) } },
            onPacket = { bytes -> onPacket(bytes) },
            onFailure = { onTransportFailure() },
        )
    }

    fun register(callback: (RasvenIax2RegistrationEvent) -> Unit) {
        synchronized(lock) {
            if (closed) {
                callback(RasvenIax2RegistrationEvent.Failed)
                return
            }
            if (transportFailed) {
                lastRegistrationFailureRetryable = true
                callback(RasvenIax2RegistrationEvent.Failed)
                return
            }
            if (sessions.values.any { it is RegistrationSession && it.releasing && !it.terminal }) {
                callback(RasvenIax2RegistrationEvent.Failed)
                return
            }
            registrationCallback = callback
            isRegistered = false
            scheduleRegistrationDeadlineLocked(callback)
            if (transport.isReady) beginRegistrationLocked(callback)
        }
    }

    /** RFC 5456 §6.1.6: explicitly release a prior registrar binding, including REGAUTH challenge. */
    fun releaseRegistration(onComplete: (Boolean) -> Unit) {
        synchronized(lock) {
            if (closed || !transport.isReady) {
                onComplete(false)
                return
            }
            if (!isRegistered) {
                onComplete(true)
                return
            }
            if (sessions.values.any { it is RegistrationSession && it.releasing && !it.terminal }) {
                onComplete(false)
                return
            }
            registrationRefresh?.cancel(); registrationRefresh = null
            registrationDeadline?.cancel(); registrationDeadline = null
            // Once teardown starts, reject new call setup locally as well as at the registrar.
            isRegistered = false
            sessions.values.filterIsInstance<RegistrationSession>().filter { !it.terminal }.forEach(::cleanupRegistrationLocked)
            val local = allocator.allocate()
            val session = RegistrationSession(
                localCallNumber = local,
                sessionId = sessionIds.incrementAndGet(),
                startMillis = clock.nowMillis(),
                callback = {},
                releasing = true,
                releaseCallback = onComplete,
            )
            sessions[local] = session
            registrationReleaseDeadline = scheduler.schedule(REGISTRATION_RELEASE_DEADLINE_MS) {
                synchronized(lock) {
                    if (sessions[session.localCallNumber] === session && !session.terminal) {
                        completeRegistrationReleaseLocked(session, released = false)
                    }
                }
            }
            val request = newReliableFrameLocked(
                session,
                RasvenIax2.FrameType.IAX,
                RasvenIax2.IaxSubclass.REGREL,
                listOf(RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.USERNAME, account.username)),
            )
            if (!sendReliableLocked(session, request)) completeRegistrationReleaseLocked(session, released = false)
        }
    }

    fun setIncomingListener(listener: ((RasvenIax2IncomingEvent) -> Unit)?) = synchronized(lock) {
        incomingCallback = listener
    }

    fun answerIncoming(sessionId: Long): Boolean = synchronized(lock) {
        val session = sessions.values.asSequence().filterIsInstance<IncomingSession>()
            .firstOrNull { it.sessionId == sessionId && !it.terminal && !it.localTermination && !it.connected }
            ?: return false
        val frame = newReliableFrameLocked(session, RasvenIax2.FrameType.CONTROL,
            RasvenIax2.ControlSubclass.ANSWER, emptyList())
        if (!sendReliableLocked(session, frame)) return false
        session.connected = true
        true
    }

    fun rejectIncoming(sessionId: Long, busy: Boolean = false): Boolean = synchronized(lock) {
        val session = sessions.values.asSequence().filterIsInstance<IncomingSession>()
            .firstOrNull { it.sessionId == sessionId && !it.terminal && !it.localTermination }
            ?: return false
        session.localTermination = true
        // Retain the remote leg until termination settles so a retransmitted NEW cannot
        // create a second Product event while a DND rejection is in flight.
        clearPendingLocked(session)
        val frame = newReliableFrameLocked(session, RasvenIax2.FrameType.IAX,
            RasvenIax2.IaxSubclass.HANGUP,
            listOf(RasvenIax2FrameCodec.u8Ie(RasvenIax2.IeType.CAUSECODE,
                if (session.connected) RasvenIax2.CauseCode.NORMAL_CLEARING else if (busy)
                    RasvenIax2.CauseCode.USER_BUSY else RasvenIax2.CauseCode.CALL_REJECTED)))
        if (!sendReliableLocked(session, frame)) cleanupIncomingLocked(session)
        true
    }

    fun startOutgoing(
        attemptId: Long,
        destination: String,
        mediaOffer: RasvenIax2MediaOffer?,
        callback: (RasvenIax2OutgoingEvent) -> Unit,
    ): RasvenIax2StartResult = synchronized(lock) {
        if (closed || !transport.isReady) return RasvenIax2StartResult.TransportUnavailable
        if (!isRegistered) return RasvenIax2StartResult.NotRegistered
        val offer = mediaOffer ?: return RasvenIax2StartResult.MediaCapabilityUnavailable
        if (destination.isBlank() || outgoingByAttempt.containsKey(attemptId)) return RasvenIax2StartResult.TransportUnavailable

        val local = allocator.allocate()
        val session = OutgoingSession(
            localCallNumber = local,
            sessionId = sessionIds.incrementAndGet(),
            startMillis = clock.nowMillis(),
            attemptId = attemptId,
            destination = destination,
            mediaOffer = offer,
            callback = callback,
        )
        sessions[local] = session
        outgoingByAttempt[attemptId] = session
        val newFrame = newReliableFrameLocked(
            session = session,
            frameType = RasvenIax2.FrameType.IAX,
            subclass = RasvenIax2.IaxSubclass.NEW,
            ies = buildList {
                // RFC 5456: VERSION MUST be the first IE in NEW.
                add(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.VERSION, RasvenIax2.VERSION))
                add(RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.CALLED_NUMBER, destination))
                add(RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.USERNAME, account.username))
                add(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.CAPABILITY, offer.capability.toLong()))
                add(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, offer.format.toLong()))
                if (offer.codecPreferences.isNotBlank()) {
                    add(RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.CODEC_PREFS, offer.codecPreferences))
                }
            },
        )
        if (!sendReliableLocked(session, newFrame)) {
            cleanupOutgoingLocked(session)
            return RasvenIax2StartResult.TransportUnavailable
        }
        session.timeout = scheduler.schedule(callTimeoutMs) {
            synchronized(lock) {
                val current = outgoingByAttempt[attemptId]
                if (current === session && !session.terminal && !session.connected) {
                    beginLocalTerminationLocked(session, RasvenIax2OutgoingEvent.NoAnswer)
                }
            }
        }
        RasvenIax2StartResult.Accepted
    }

    fun cancelOutgoing(attemptId: Long): Boolean = synchronized(lock) {
        val session = outgoingByAttempt[attemptId] ?: return false
        if (session.terminal || session.cancelling) return false
        session.cancelling = true
        session.timeout?.cancel()
        session.timeout = null
        outgoingByAttempt.remove(attemptId)
        clearPendingLocked(session)
        if (session.remoteCallNumber == null) {
            scheduleLocalRetirementLocked(session)
            return true
        }
        sendLocalHangupLocked(session)
        true
    }

    /** RFC 5456 §8.2.1: one DTMF digit is the subclass of one Full Frame. */
    fun sendDtmf(sessionId: Long, digit: Char): Boolean = synchronized(lock) {
        if (digit !in "0123456789*#") return false
        val session = sessions.values.asSequence().filterIsInstance<MediaSession>()
            .firstOrNull { it.sessionId == sessionId && it.connected && !it.terminal &&
                (it !is OutgoingSession || !it.cancelling) && (it !is IncomingSession || !it.localTermination) }
            ?: return false
        if (session.remoteCallNumber == null) return false
        val frame = newReliableFrameLocked(session, RasvenIax2.FrameType.DTMF, digit.code, emptyList())
        sendReliableLocked(session, frame)
    }

    fun setHeld(sessionId: Long, enabled: Boolean): Boolean = synchronized(lock) {
        val session = sessions.values.asSequence().filterIsInstance<MediaSession>()
            .firstOrNull { it.sessionId == sessionId && it.connected && !it.terminal &&
                (it !is OutgoingSession || !it.cancelling) && (it !is IncomingSession || !it.localTermination) }
            ?: return false
        if (session.held == enabled) return true
        val frame = newReliableFrameLocked(session, RasvenIax2.FrameType.CONTROL,
            if (enabled) RasvenIax2.ControlSubclass.HOLD else RasvenIax2.ControlSubclass.UNHOLD, emptyList())
        if (!sendReliableLocked(session, frame)) return false
        session.held = enabled
        true
    }

    internal fun activeSessionCountForTest(): Int = synchronized(lock) { outgoingByAttempt.size }
    internal fun protocolSessionCountForTest(): Int = synchronized(lock) { sessions.values.count { it is OutgoingSession } }

    private fun beginRegistrationLocked(callback: (RasvenIax2RegistrationEvent) -> Unit) {
        if (sessions.values.any { it is RegistrationSession && !it.terminal }) return
        if (registrationDeadline == null) scheduleRegistrationDeadlineLocked(callback)
        val local = allocator.allocate()
        val session = RegistrationSession(local, sessionIds.incrementAndGet(), clock.nowMillis(), callback)
        sessions[local] = session
        val request = newReliableFrameLocked(
            session,
            RasvenIax2.FrameType.IAX,
            RasvenIax2.IaxSubclass.REGREQ,
            listOf(
                RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.USERNAME, account.username),
                RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH, DEFAULT_REFRESH_SECONDS),
            ),
        )
        if (!sendReliableLocked(session, request)) failRegistrationLocked(session)
    }

    private fun scheduleRegistrationDeadlineLocked(callback: (RasvenIax2RegistrationEvent) -> Unit) {
        registrationDeadline?.cancel()
        registrationDeadline = scheduler.schedule(REGISTRATION_DEADLINE_MS) {
            synchronized(lock) {
                if (closed || registrationCallback !== callback) return@synchronized
                val session = sessions.values.filterIsInstance<RegistrationSession>().firstOrNull { !it.terminal }
                if (session != null) failRegistrationLocked(session)
                else {
                    lastRegistrationFailureRetryable = true
                    registrationDeadline = null
                    registrationCallback = null
                    callback(RasvenIax2RegistrationEvent.Failed)
                }
            }
        }
    }

    private fun onPacket(packet: ByteArray) {
        val frame = RasvenIax2FrameCodec.decode(packet)
        if (frame == null) {
            RasvenIax2MiniFrameCodec.decode(packet)?.let(::onMiniFrame)
            return
        }
        synchronized(lock) {
            if (closed) return
            if (frame.destinationCallNumber == 0 && frame.frameType == RasvenIax2.FrameType.IAX &&
                frame.subclass == RasvenIax2.IaxSubclass.POKE) {
                onPokeLocked(frame)
                return
            }
            if (frame.destinationCallNumber == 0 && frame.frameType == RasvenIax2.FrameType.IAX &&
                frame.subclass == RasvenIax2.IaxSubclass.NEW) {
                onIncomingNewLocked(frame)
                return
            }
            val session = sessions[frame.destinationCallNumber] ?: return
            val remote = session.remoteCallNumber
            if (remote == null) session.remoteCallNumber = frame.sourceCallNumber
            else if (remote != frame.sourceCallNumber) return

            processAcknowledgments(session, frame.iSeqNo)
            if (frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass == RasvenIax2.IaxSubclass.VNAK) {
                retransmitOutstandingLocked(session, frame.iSeqNo)
                return
            }
            if (frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass == RasvenIax2.IaxSubclass.ACK) {
                if (session is MonitorSession && session.pending.isEmpty()) cleanupMonitorLocked(session)
                if (session is OutgoingSession && session.cancelling) {
                    val terminationSequence = session.terminationSequence
                    if (terminationSequence != null && !session.pending.containsKey(terminationSequence)) {
                        cleanupOutgoingLocked(session)
                    }
                }
                if (session is IncomingSession && session.localTermination && session.pending.isEmpty()) {
                    cleanupIncomingLocked(session)
                }
                return
            }
            // RFC 5456: INVAL, like ACK/VNAK, does not advance the sequence counters.
            if (frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass == RasvenIax2.IaxSubclass.INVAL) {
                when (session) {
                    is MonitorSession -> cleanupMonitorLocked(session)
                    is RegistrationSession -> failRegistrationLocked(session)
                    is OutgoingSession -> if (session.connected) {
                        session.callback(RasvenIax2OutgoingEvent.RemoteHangup)
                        cleanupOutgoingLocked(session)
                    } else {
                        emitOutgoingTerminalLocked(session, RasvenIax2OutgoingEvent.OtherFailure)
                    }
                    is IncomingSession -> terminalIncomingLocked(session)
                }
                return
            }
            if (!acceptIncomingSequence(session, frame)) return
            when (session) {
                is MonitorSession -> Unit
                is RegistrationSession -> handleRegistrationLocked(session, frame)
                is OutgoingSession -> handleOutgoingLocked(session, frame)
                is IncomingSession -> handleIncomingLocked(session, frame)
            }
        }
    }

    private fun onIncomingNewLocked(frame: RasvenIax2FullFrame) {
        if (!isRegistered) {
            diagnostic("DROP_INCOMING_NEW reason=not_registered")
            return
        }
        if (frame.sourceCallNumber == 0 || frame.oSeqNo != 0) {
            diagnostic("DROP_INCOMING_NEW reason=invalid_call_header")
            return
        }
        if (frame.ieU16(RasvenIax2.IeType.VERSION) != RasvenIax2.VERSION) {
            diagnostic("DROP_INCOMING_NEW reason=unsupported_protocol_version")
            return
        }
        val newKey = frame.sourceCallNumber to frame.timestamp
        val now = clock.nowMillis()
        locallyRejectedNew.entries.removeAll { now - it.value > 30_000L }
        if (locallyRejectedNew.containsKey(newKey)) return
        incomingByRemoteCall[frame.sourceCallNumber]?.let { existing ->
            if (!existing.terminal) sendExplicitAckLocked(existing, frame)
            return
        }
        // V1 accepts one incoming setup at a time; a second peer leg is explicitly rejected.
        if (incomingByRemoteCall.isNotEmpty()) {
            diagnostic("DROP_INCOMING_NEW reason=another_incoming_session_active")
            return
        }
        val capability = frame.ieU32(RasvenIax2.IeType.CAPABILITY)?.toInt() ?: 0
        val format = frame.ieU32(RasvenIax2.IeType.FORMAT)?.toInt() ?: 0
        val supported = (capability or format) and RasvenIax2.MediaFormat.IMPLEMENTED_CAPABILITY
        val selected = when {
            format and RasvenIax2.MediaFormat.PCMU != 0 && supported and RasvenIax2.MediaFormat.PCMU != 0 -> RasvenAudioCodec.PCMU
            format and RasvenIax2.MediaFormat.PCMA != 0 && supported and RasvenIax2.MediaFormat.PCMA != 0 -> RasvenAudioCodec.PCMA
            supported and RasvenIax2.MediaFormat.PCMU != 0 -> RasvenAudioCodec.PCMU
            supported and RasvenIax2.MediaFormat.PCMA != 0 -> RasvenAudioCodec.PCMA
            else -> null
        } ?: run {
            diagnostic("DROP_INCOMING_NEW reason=unsupported_media capability=$capability format=$format")
            return
        }
        val local = allocator.allocate()
        val session = IncomingSession(local, sessionIds.incrementAndGet(), clock.nowMillis(),
            selected, frame.ieString(RasvenIax2.IeType.CALLING_NUMBER).orEmpty(),
            frame.ieString(RasvenIax2.IeType.CALLING_NAME),
            frame.ieString(RasvenIax2.IeType.CALLED_NUMBER), frame.timestamp)
        session.remoteCallNumber = frame.sourceCallNumber
        session.nextIncomingSequence = 1
        session.lastIncomingTimestamp = frame.timestamp
        sessions[local] = session
        incomingByRemoteCall[frame.sourceCallNumber] = session
        diagnostic("INCOMING_NEW_ACCEPTED codec=${selected.name}")
        sendExplicitAckLocked(session, frame)
        val accept = newReliableFrameLocked(session, RasvenIax2.FrameType.IAX,
            RasvenIax2.IaxSubclass.ACCEPT,
            listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, selected.iaxFormat.toLong())))
        if (!sendReliableLocked(session, accept)) {
            cleanupIncomingLocked(session)
            return
        }
        val ringing = newReliableFrameLocked(session, RasvenIax2.FrameType.CONTROL,
            RasvenIax2.ControlSubclass.RINGING, emptyList())
        if (!sendReliableLocked(session, ringing)) {
            cleanupIncomingLocked(session)
            return
        }
        incomingCallback?.invoke(RasvenIax2IncomingEvent.Ringing(session.sessionId,
            session.callingNumber, session.callingName, session.calledNumber))
    }

    private fun onPokeLocked(frame: RasvenIax2FullFrame) {
        if (frame.sourceCallNumber == 0 || frame.oSeqNo != 0) return
        val monitors = sessions.values.filterIsInstance<MonitorSession>()
        val existing = monitors.firstOrNull { it.remoteCallNumber == frame.sourceCallNumber && it.pokeTimestamp == frame.timestamp }
        if (existing != null) {
            existing.pending.values.firstOrNull()?.let { transport.send(RasvenIax2FrameCodec.encode(it.frame.copy(retransmission = true))) }
            return
        }
        // Bounded monitoring transactions never consume a Product call/media owner.
        if (monitors.size >= 8) return
        val session = MonitorSession(allocator.allocate(), sessionIds.incrementAndGet(), clock.nowMillis(), frame.timestamp)
        session.remoteCallNumber = frame.sourceCallNumber
        session.nextIncomingSequence = 1
        session.lastIncomingTimestamp = frame.timestamp
        sessions[session.localCallNumber] = session
        val pong = newReliableFrameLocked(session, RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.PONG, emptyList()).copy(timestamp = frame.timestamp)
        if (!sendReliableLocked(session, pong)) cleanupMonitorLocked(session)
    }

    private fun cleanupMonitorLocked(session: MonitorSession) {
        clearPendingLocked(session)
        session.terminal = true
        sessions.remove(session.localCallNumber)
        allocator.release(session.localCallNumber)
    }

    private fun handleIncomingLocked(session: IncomingSession, frame: RasvenIax2FullFrame) {
        if (session.localTermination) {
            sendExplicitAckWhenRequired(session, frame)
            if (frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass == RasvenIax2.IaxSubclass.HANGUP) {
                cleanupIncomingLocked(session)
            }
            return
        }
        when (frame.frameType) {
            RasvenIax2.FrameType.IAX -> when (frame.subclass) {
                RasvenIax2.IaxSubclass.HANGUP, RasvenIax2.IaxSubclass.REJECT -> {
                    sendExplicitAckLocked(session, frame)
                    terminalIncomingLocked(session)
                }
                RasvenIax2.IaxSubclass.PING -> sendPongLocked(session, frame)
                RasvenIax2.IaxSubclass.LAGRQ -> handleLagRequestLocked(session, frame)
            }
            RasvenIax2.FrameType.CONTROL -> when (frame.subclass) {
                RasvenIax2.ControlSubclass.HANGUP -> {
                    sendExplicitAckLocked(session, frame)
                    terminalIncomingLocked(session)
                }
                RasvenIax2.ControlSubclass.HOLD -> {
                    sendExplicitAckLocked(session, frame)
                    if (session.connected) incomingCallback?.invoke(RasvenIax2IncomingEvent.PeerHoldChanged(session.sessionId, true))
                }
                RasvenIax2.ControlSubclass.UNHOLD -> {
                    sendExplicitAckLocked(session, frame)
                    if (session.connected) incomingCallback?.invoke(RasvenIax2IncomingEvent.PeerHoldChanged(session.sessionId, false))
                }
                else -> Unit
            }
            RasvenIax2.FrameType.VOICE -> {
                sendExplicitAckLocked(session, frame)
                val codec = RasvenAudioCodec.fromIaxFormat(frame.subclass) ?: return
                if (codec != session.selectedCodec || !session.connected) return
                session.incomingVoiceCodec = codec
                session.miniTimestampExtender.syncFullTimestamp(frame.timestamp)
                if (frame.payload.isNotEmpty()) session.mediaReceiver?.invoke(
                    RasvenIax2InboundVoice(session.sessionId, codec, frame.timestamp, frame.payload.copyOf(), true))
            }
        }
    }

    private fun terminalIncomingLocked(session: IncomingSession) {
        if (session.terminal) return
        incomingCallback?.invoke(RasvenIax2IncomingEvent.RemoteCancel(session.sessionId, session.connected))
        cleanupIncomingLocked(session)
    }

    private fun cleanupIncomingLocked(session: IncomingSession) {
        if (session.localTermination) {
            locallyRejectedNew[session.remoteCallNumber!! to session.newTimestamp] = clock.nowMillis()
            if (locallyRejectedNew.size > 64) locallyRejectedNew.remove(locallyRejectedNew.keys.first())
        }
        clearPendingLocked(session)
        session.mediaReceiver = null
        session.terminal = true
        sessions.remove(session.localCallNumber)
        incomingByRemoteCall.remove(session.remoteCallNumber)
        allocator.release(session.localCallNumber)
    }

    private fun acceptIncomingSequence(session: Session, frame: RasvenIax2FullFrame): Boolean {
        val expected = session.nextIncomingSequence
        if (frame.oSeqNo == expected) {
            val previousTimestamp = session.lastIncomingTimestamp
            if (previousTimestamp != null && !timestampAtOrAfter(frame.timestamp, previousTimestamp)) {
                sendVnak(session, frame.timestamp)
                return false
            }
            session.nextIncomingSequence = (expected + 1) and 0xff
            session.lastIncomingTimestamp = frame.timestamp
            return true
        }
        val previous = (expected - 1) and 0xff
        if (frame.retransmission || frame.oSeqNo == previous) {
            sendExplicitAckWhenRequired(session, frame)
            return false
        }
        sendVnak(session, frame.timestamp)
        return false
    }

    private fun timestampAtOrAfter(candidate: Long, previous: Long): Boolean {
        val distance = (candidate - previous) and 0xffff_ffffL
        return distance == 0L || distance < 0x8000_0000L
    }

    private fun handleRegistrationLocked(session: RegistrationSession, frame: RasvenIax2FullFrame) {
        if (frame.frameType != RasvenIax2.FrameType.IAX) return
        when (frame.subclass) {
            RasvenIax2.IaxSubclass.REGAUTH -> {
                clearPendingLocked(session)
                val methods = frame.ieU16(RasvenIax2.IeType.AUTHMETHODS) ?: 0
                val challenge = frame.ieString(RasvenIax2.IeType.CHALLENGE)
                if (methods and RasvenIax2.AuthMethod.MD5 == 0 || challenge.isNullOrEmpty()) {
                    failRegistrationLocked(session, retryable = false)
                    return
                }
                val digest = MessageDigest.getInstance("MD5")
                    .digest((challenge + account.password).toByteArray(StandardCharsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val authenticated = newReliableFrameLocked(
                    session,
                    RasvenIax2.FrameType.IAX,
                    if (session.releasing) RasvenIax2.IaxSubclass.REGREL else RasvenIax2.IaxSubclass.REGREQ,
                    buildList {
                        add(RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.USERNAME, account.username))
                        add(RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.MD5_RESULT, digest))
                        if (!session.releasing) add(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH, DEFAULT_REFRESH_SECONDS))
                    },
                )
                if (!sendReliableLocked(session, authenticated)) {
                    if (session.releasing) completeRegistrationReleaseLocked(session, released = false)
                    else failRegistrationLocked(session)
                }
            }
            RasvenIax2.IaxSubclass.REGACK -> {
                clearPendingLocked(session)
                sendExplicitAckLocked(session, frame)
                if (session.releasing) {
                    completeRegistrationReleaseLocked(session, released = true)
                    return
                }
                val refresh = frame.ieU16(RasvenIax2.IeType.REFRESH) ?: DEFAULT_REFRESH_SECONDS
                if (refresh == 0) {
                    failRegistrationLocked(session, retryable = false)
                    return
                }
                isRegistered = true
                val callback = session.callback
                cleanupRegistrationLocked(session)
                registrationDeadline?.cancel(); registrationDeadline = null
                callback(RasvenIax2RegistrationEvent.Registered)
                // A synchronous terminal observer may invalidate/close this engine.
                if (!closed && isRegistered && registrationCallback === callback) scheduleRefreshLocked(refresh)
            }
            RasvenIax2.IaxSubclass.REGREJ -> {
                sendExplicitAckWhenRequired(session, frame)
                if (session.releasing) completeRegistrationReleaseLocked(session, released = false)
                else failRegistrationLocked(session, retryable = false)
            }
            RasvenIax2.IaxSubclass.PING -> sendPongLocked(session, frame)
            RasvenIax2.IaxSubclass.LAGRQ -> handleLagRequestLocked(session, frame)
        }
    }

    private fun handleOutgoingLocked(session: OutgoingSession, frame: RasvenIax2FullFrame) {
        if (session.productTerminalDelivered) {
            if (frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass == RasvenIax2.IaxSubclass.HANGUP) {
                sendExplicitAckLocked(session, frame)
                cleanupOutgoingLocked(session)
            } else {
                sendExplicitAckWhenRequired(session, frame)
            }
            return
        }
        if (session.cancelling) {
            val remoteTerminal =
                frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass in setOf(
                    RasvenIax2.IaxSubclass.REJECT,
                    RasvenIax2.IaxSubclass.HANGUP,
                    RasvenIax2.IaxSubclass.INVAL,
                ) || frame.frameType == RasvenIax2.FrameType.CONTROL && frame.subclass in setOf(
                    RasvenIax2.ControlSubclass.BUSY,
                    RasvenIax2.ControlSubclass.CONGESTION,
                )
            sendExplicitAckWhenRequired(session, frame)
            if (remoteTerminal) {
                cleanupOutgoingLocked(session)
            } else if (session.terminationSequence == null) {
                session.retirement?.cancel(); session.retirement = null
                sendLocalHangupLocked(session)
            }
            return
        }
        when (frame.frameType) {
            RasvenIax2.FrameType.IAX -> handleOutgoingIaxLocked(session, frame)
            RasvenIax2.FrameType.CONTROL -> handleOutgoingControlLocked(session, frame)
            RasvenIax2.FrameType.VOICE -> handleIncomingFullVoiceLocked(session, frame)
        }
    }

    private fun handleOutgoingIaxLocked(session: OutgoingSession, frame: RasvenIax2FullFrame) {
        when (frame.subclass) {
            RasvenIax2.IaxSubclass.AUTHREQ -> {
                clearPendingLocked(session)
                val methods = frame.ieU16(RasvenIax2.IeType.AUTHMETHODS) ?: 0
                val challenge = frame.ieString(RasvenIax2.IeType.CHALLENGE)
                if (methods and RasvenIax2.AuthMethod.MD5 == 0 || challenge.isNullOrEmpty()) {
                    emitOutgoingTerminalLocked(session, RasvenIax2OutgoingEvent.OtherFailure)
                    return
                }
                val digest = MessageDigest.getInstance("MD5")
                    .digest((challenge + account.password).toByteArray(StandardCharsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val authRep = newReliableFrameLocked(
                    session,
                    RasvenIax2.FrameType.IAX,
                    RasvenIax2.IaxSubclass.AUTHREP,
                    listOf(RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.MD5_RESULT, digest)),
                )
                if (!sendReliableLocked(session, authRep)) emitOutgoingTerminalLocked(session, RasvenIax2OutgoingEvent.NetworkFailure)
            }
            RasvenIax2.IaxSubclass.ACCEPT -> {
                clearPendingLocked(session)
                sendExplicitAckLocked(session, frame)
                val selected = frame.ieU32(RasvenIax2.IeType.FORMAT)?.toInt()
                val codec = selected?.let(RasvenAudioCodec::fromIaxFormat)
                if (selected == null || codec == null || session.mediaOffer.capability and selected == 0) {
                    emitOutgoingTerminalLocked(session, RasvenIax2OutgoingEvent.OtherFailure)
                } else {
                    session.selectedCodec = codec
                }
            }
            RasvenIax2.IaxSubclass.REJECT -> {
                clearPendingLocked(session)
                sendExplicitAckLocked(session, frame)
                emitOutgoingTerminalLocked(session, mapCause(frame.ieU8(RasvenIax2.IeType.CAUSECODE)))
            }
            RasvenIax2.IaxSubclass.HANGUP -> {
                clearPendingLocked(session)
                sendExplicitAckLocked(session, frame)
                if (session.connected) {
                    session.callback(RasvenIax2OutgoingEvent.RemoteHangup)
                    cleanupOutgoingLocked(session)
                } else {
                    emitOutgoingTerminalLocked(session, mapCause(frame.ieU8(RasvenIax2.IeType.CAUSECODE)))
                }
            }
            RasvenIax2.IaxSubclass.PING -> sendPongLocked(session, frame)
            RasvenIax2.IaxSubclass.LAGRQ -> handleLagRequestLocked(session, frame)
        }
    }

    private fun handleOutgoingControlLocked(session: OutgoingSession, frame: RasvenIax2FullFrame) {
        sendExplicitAckLocked(session, frame)
        when (frame.subclass) {
            RasvenIax2.ControlSubclass.RINGING -> if (!session.connected && !session.ringingDelivered) {
                session.ringingDelivered = true
                session.callback(RasvenIax2OutgoingEvent.Ringing)
            }
            RasvenIax2.ControlSubclass.PROGRESS,
            RasvenIax2.ControlSubclass.PROCEEDING -> Unit
            RasvenIax2.ControlSubclass.BUSY -> emitOutgoingTerminalLocked(session, RasvenIax2OutgoingEvent.Busy)
            RasvenIax2.ControlSubclass.CONGESTION -> emitOutgoingTerminalLocked(session, RasvenIax2OutgoingEvent.NetworkFailure)
            RasvenIax2.ControlSubclass.ANSWER -> if (!session.connected) {
                if (session.selectedCodec == null) {
                    emitOutgoingTerminalLocked(session, RasvenIax2OutgoingEvent.OtherFailure)
                } else {
                    session.connected = true
                    session.timeout?.cancel(); session.timeout = null
                    clearPendingLocked(session)
                    session.callback(RasvenIax2OutgoingEvent.Connected(session.sessionId))
                }
            }
            RasvenIax2.ControlSubclass.HOLD -> if (session.connected) session.callback(RasvenIax2OutgoingEvent.PeerHoldChanged(true))
            RasvenIax2.ControlSubclass.UNHOLD -> if (session.connected) session.callback(RasvenIax2OutgoingEvent.PeerHoldChanged(false))
            RasvenIax2.ControlSubclass.HANGUP -> if (session.connected) {
                session.callback(RasvenIax2OutgoingEvent.RemoteHangup)
                cleanupOutgoingLocked(session)
            } else emitOutgoingTerminalLocked(session, RasvenIax2OutgoingEvent.OtherFailure)
        }
    }

    private fun handleIncomingFullVoiceLocked(session: OutgoingSession, frame: RasvenIax2FullFrame) {
        sendExplicitAckLocked(session, frame)
        val codec = RasvenAudioCodec.fromIaxFormat(frame.subclass) ?: return
        if (session.mediaOffer.capability and codec.iaxFormat == 0) return
        session.incomingVoiceCodec = codec
        session.miniTimestampExtender.syncFullTimestamp(frame.timestamp)
        if (frame.payload.isNotEmpty()) {
            session.mediaReceiver?.invoke(
                RasvenIax2InboundVoice(session.sessionId, codec, frame.timestamp, frame.payload.copyOf(), true),
            )
        }
    }

    private fun onMiniFrame(frame: RasvenIax2MiniFrame) {
        synchronized(lock) {
            if (closed) return
            val session = sessions.values.asSequence()
                .filterIsInstance<MediaSession>()
                .firstOrNull { !it.terminal && it.remoteCallNumber == frame.sourceCallNumber } ?: return
            val codec = session.incomingVoiceCodec ?: return
            val timestamp = session.miniTimestampExtender.extend(frame.timestamp16)
            session.mediaReceiver?.invoke(
                RasvenIax2InboundVoice(session.sessionId, codec, timestamp, frame.payload.copyOf(), false),
            )
        }
    }

    fun openMediaChannel(sessionId: Long): RasvenIax2MediaChannel? = synchronized(lock) {
        val session = sessions.values.asSequence().filterIsInstance<MediaSession>()
            .firstOrNull { it.sessionId == sessionId && it.connected && !it.terminal } ?: return null
        val codec = session.selectedCodec ?: return null
        object : RasvenIax2MediaChannel {
            override val sessionId: Long = session.sessionId
            override val codec: RasvenAudioCodec = codec
            override val initialTimestampMs: Long = elapsedTimestamp(session)

            override fun setLagReceiver(receiver: ((() -> Unit) -> Unit)?) = synchronized(lock) {
                if (sessions[session.localCallNumber] === session && !session.terminal) session.lagReceiver = receiver
            }

            override fun sendFullVoice(timestampMs: Long, payload: ByteArray): Boolean = synchronized(lock) sendFull@ {
                val current = sessions[session.localCallNumber] as? MediaSession ?: return@sendFull false
                if (current !== session || current.terminal || !current.connected || current.selectedCodec != codec) return@sendFull false
                val remote = current.remoteCallNumber ?: return@sendFull false
                val sequence = current.nextOutgoingSequence
                current.nextOutgoingSequence = (sequence + 1) and 0xff
                val voice = RasvenIax2FullFrame(
                    sourceCallNumber = current.localCallNumber,
                    destinationCallNumber = remote,
                    retransmission = false,
                    timestamp = timestampMs and 0xffff_ffffL,
                    oSeqNo = sequence,
                    iSeqNo = current.nextIncomingSequence,
                    frameType = RasvenIax2.FrameType.VOICE,
                    subclass = codec.iaxFormat,
                    compressedSubclass = true,
                    payload = payload.copyOf(),
                )
                sendReliableLocked(current, voice)
            }

            override fun sendMiniVoice(timestampMs: Long, payload: ByteArray): Boolean = synchronized(lock) sendMini@ {
                val current = sessions[session.localCallNumber] as? MediaSession ?: return@sendMini false
                if (current !== session || current.terminal || !current.connected || current.selectedCodec != codec) return@sendMini false
                transport.send(
                    RasvenIax2MiniFrameCodec.encode(
                        RasvenIax2MiniFrame(current.localCallNumber, (timestampMs and 0xffff).toInt(), payload.copyOf()),
                    ),
                )
            }

            override fun setReceiver(receiver: ((RasvenIax2InboundVoice) -> Unit)?) {
                synchronized(lock) {
                    val current = sessions[session.localCallNumber] as? MediaSession
                    if (current === session && !current.terminal) current.mediaReceiver = receiver
                }
            }
        }
    }

    private fun mapCause(cause: Int?): RasvenIax2OutgoingEvent = when (cause) {
        RasvenIax2.CauseCode.USER_BUSY -> RasvenIax2OutgoingEvent.Busy
        RasvenIax2.CauseCode.NO_USER_RESPONSE,
        RasvenIax2.CauseCode.NO_ANSWER -> RasvenIax2OutgoingEvent.NoAnswer
        RasvenIax2.CauseCode.CALL_REJECTED -> RasvenIax2OutgoingEvent.Rejected
        RasvenIax2.CauseCode.NETWORK_OUT_OF_ORDER,
        RasvenIax2.CauseCode.TEMPORARY_FAILURE,
        RasvenIax2.CauseCode.SWITCH_CONGESTION -> RasvenIax2OutgoingEvent.NetworkFailure
        else -> RasvenIax2OutgoingEvent.OtherFailure
    }

    private fun newReliableFrameLocked(
        session: Session,
        frameType: Int,
        subclass: Int,
        ies: List<RasvenIax2InformationElement>,
    ): RasvenIax2FullFrame {
        val sequence = session.nextOutgoingSequence
        session.nextOutgoingSequence = (sequence + 1) and 0xff
        return RasvenIax2FullFrame(
            sourceCallNumber = session.localCallNumber,
            destinationCallNumber = session.remoteCallNumber ?: 0,
            retransmission = false,
            timestamp = elapsedTimestamp(session),
            oSeqNo = sequence,
            iSeqNo = session.nextIncomingSequence,
            frameType = frameType,
            subclass = subclass,
            informationElements = ies,
        )
    }

    private fun sendReliableLocked(session: Session, frame: RasvenIax2FullFrame): Boolean {
        if (!transport.send(RasvenIax2FrameCodec.encode(frame))) return false
        val pending = Pending(frame)
        session.pending[frame.oSeqNo] = pending
        scheduleRetransmissionLocked(session, pending, INITIAL_RETRANSMIT_MS)
        return true
    }

    private fun scheduleRetransmissionLocked(session: Session, pending: Pending, delay: Long) {
        pending.timer = scheduler.schedule(delay) {
            synchronized(lock) {
                if (closed || session.terminal || session.pending[pending.frame.oSeqNo] !== pending) return@synchronized
                if (pending.retransmissions >= MAX_RETRANSMISSIONS) {
                    session.pending.remove(pending.frame.oSeqNo)
                    when (session) {
                        is MonitorSession -> cleanupMonitorLocked(session)
                        is RegistrationSession -> failRegistrationLocked(session)
                        is OutgoingSession -> if (session.cancelling || session.productTerminalDelivered) {
                            cleanupOutgoingLocked(session)
                        } else {
                            emitOutgoingTerminalLocked(session, RasvenIax2OutgoingEvent.NetworkFailure)
                        }
                        is IncomingSession -> if (session.localTermination) cleanupIncomingLocked(session)
                            else terminalIncomingLocked(session)
                    }
                    return@synchronized
                }
                val retry = pending.frame.copy(retransmission = true, iSeqNo = session.nextIncomingSequence)
                if (!transport.send(RasvenIax2FrameCodec.encode(retry))) {
                    when (session) {
                        is MonitorSession -> cleanupMonitorLocked(session)
                        is RegistrationSession -> failRegistrationLocked(session)
                        is OutgoingSession -> if (session.cancelling || session.productTerminalDelivered) {
                            cleanupOutgoingLocked(session)
                        } else {
                            emitOutgoingTerminalLocked(session, RasvenIax2OutgoingEvent.NetworkFailure)
                        }
                        is IncomingSession -> if (session.localTermination) cleanupIncomingLocked(session)
                            else terminalIncomingLocked(session)
                    }
                    return@synchronized
                }
                pending.retransmissions += 1
                val next = (delay * 2).coerceAtMost(MAX_RETRANSMIT_MS)
                scheduleRetransmissionLocked(session, pending, next)
            }
        }
    }

    private fun retransmitOutstandingLocked(session: Session, fromSequence: Int) {
        session.pending.values.filter { sequenceAfter(it.frame.oSeqNo, fromSequence) }.forEach { pending ->
            transport.send(RasvenIax2FrameCodec.encode(pending.frame.copy(retransmission = true, iSeqNo = session.nextIncomingSequence)))
        }
    }

    private fun processAcknowledgments(session: Session, peerISeqNo: Int) {
        val acknowledged = session.pending.keys.filter { sequenceBefore(it, peerISeqNo) }
        acknowledged.forEach { sequence -> session.pending.remove(sequence)?.timer?.cancel() }
    }

    private fun sequenceBefore(sequence: Int, nextExpected: Int): Boolean {
        val distance = (nextExpected - sequence + 256) and 0xff
        return distance in 1..127
    }

    private fun sequenceAfter(sequence: Int, floor: Int): Boolean {
        val distance = (sequence - floor + 256) and 0xff
        return distance in 1..127
    }

    private fun sendExplicitAckWhenRequired(session: Session, frame: RasvenIax2FullFrame) {
        if (frame.frameType == RasvenIax2.FrameType.VOICE || frame.frameType == RasvenIax2.FrameType.CONTROL || frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass in setOf(
                RasvenIax2.IaxSubclass.ACCEPT,
                RasvenIax2.IaxSubclass.REJECT,
                RasvenIax2.IaxSubclass.HANGUP,
                RasvenIax2.IaxSubclass.REGACK,
                RasvenIax2.IaxSubclass.REGREJ,
                RasvenIax2.IaxSubclass.PONG,
            )
        ) sendExplicitAckLocked(session, frame)
    }

    private fun sendExplicitAckLocked(session: Session, frame: RasvenIax2FullFrame) {
        val remote = session.remoteCallNumber ?: frame.sourceCallNumber
        transport.send(
            RasvenIax2FrameCodec.encode(
                RasvenIax2FullFrame(
                    sourceCallNumber = session.localCallNumber,
                    destinationCallNumber = remote,
                    retransmission = false,
                    timestamp = frame.timestamp,
                    oSeqNo = session.nextOutgoingSequence,
                    iSeqNo = session.nextIncomingSequence,
                    frameType = RasvenIax2.FrameType.IAX,
                    subclass = RasvenIax2.IaxSubclass.ACK,
                ),
            ),
        )
    }

    private fun sendVnak(session: Session, timestamp: Long) {
        val remote = session.remoteCallNumber ?: return
        transport.send(
            RasvenIax2FrameCodec.encode(
                RasvenIax2FullFrame(
                    sourceCallNumber = session.localCallNumber,
                    destinationCallNumber = remote,
                    retransmission = false,
                    timestamp = timestamp,
                    oSeqNo = session.nextOutgoingSequence,
                    iSeqNo = session.nextIncomingSequence,
                    frameType = RasvenIax2.FrameType.IAX,
                    subclass = RasvenIax2.IaxSubclass.VNAK,
                ),
            ),
        )
    }

    private fun sendPongLocked(session: Session, ping: RasvenIax2FullFrame) {
        val frame = newReliableFrameLocked(session, RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.PONG, emptyList())
            .copy(timestamp = ping.timestamp)
        sendReliableLocked(session, frame)
    }

    private fun handleLagRequestLocked(session: Session, request: RasvenIax2FullFrame) {
        sendExplicitAckLocked(session, request)
        val reply: () -> Unit = {
            synchronized(lock) {
                if (sessions[session.localCallNumber] === session && !session.terminal &&
                    (session !is OutgoingSession || !session.cancelling) &&
                    (session !is IncomingSession || !session.localTermination)) {
                    val frame = newReliableFrameLocked(session, RasvenIax2.FrameType.IAX,
                        RasvenIax2.IaxSubclass.LAGRP, emptyList()).copy(timestamp = request.timestamp)
                    sendReliableLocked(session, frame)
                }
            }
        }
        val receiver = (session as? MediaSession)?.lagReceiver
        if (receiver == null) reply() else receiver(reply)
    }

    private fun scheduleRefreshLocked(refreshSeconds: Int) {
        registrationRefresh?.cancel()
        // Honor even a short positive lease; clamping to four seconds can refresh after expiry.
        val delay = refreshSeconds * 800L
        registrationRefresh = scheduler.schedule(delay) {
            synchronized(lock) {
                registrationRefresh = null
                if (!closed && transport.isReady) {
                    val callback = registrationCallback ?: return@synchronized
                    beginRegistrationLocked(callback)
                }
            }
        }
    }

    private fun failRegistrationLocked(session: RegistrationSession, retryable: Boolean = true) {
        if (session.terminal) return
        if (session.releasing) {
            completeRegistrationReleaseLocked(session, released = false)
            return
        }
        session.terminal = true
        lastRegistrationFailureRetryable = retryable
        isRegistered = false
        registrationRefresh?.cancel(); registrationRefresh = null
        clearPendingLocked(session)
        sessions.remove(session.localCallNumber)
        allocator.release(session.localCallNumber)
        registrationDeadline?.cancel(); registrationDeadline = null
        registrationCallback = null
        session.callback(RasvenIax2RegistrationEvent.Failed)
    }

    private fun cleanupRegistrationLocked(session: RegistrationSession) {
        clearPendingLocked(session)
        session.terminal = true
        sessions.remove(session.localCallNumber)
        allocator.release(session.localCallNumber)
    }

    private fun completeRegistrationReleaseLocked(session: RegistrationSession, released: Boolean) {
        if (session.terminal) return
        session.terminal = true
        clearPendingLocked(session)
        sessions.remove(session.localCallNumber)
        allocator.release(session.localCallNumber)
        registrationReleaseDeadline?.cancel(); registrationReleaseDeadline = null
        if (released) {
            isRegistered = false
            registrationCallback = null
        }
        session.releaseCallback?.invoke(released)
    }

    private fun beginLocalTerminationLocked(session: OutgoingSession, event: RasvenIax2OutgoingEvent) {
        if (session.terminal || session.productTerminalDelivered) return
        session.productTerminalDelivered = true
        session.timeout?.cancel(); session.timeout = null
        outgoingByAttempt.remove(session.attemptId)
        session.callback(event)
        clearPendingLocked(session)
        if (session.remoteCallNumber == null) {
            cleanupOutgoingLocked(session)
            return
        }
        session.cancelling = true
        sendLocalHangupLocked(session)
    }

    private fun sendLocalHangupLocked(session: OutgoingSession) {
        if (session.terminal || session.terminationSequence != null) return
        val hangup = newReliableFrameLocked(
            session,
            RasvenIax2.FrameType.IAX,
            RasvenIax2.IaxSubclass.HANGUP,
            listOf(RasvenIax2FrameCodec.u8Ie(RasvenIax2.IeType.CAUSECODE, RasvenIax2.CauseCode.NORMAL_CLEARING)),
        )
        session.terminationSequence = hangup.oSeqNo
        if (!sendReliableLocked(session, hangup)) cleanupOutgoingLocked(session)
    }

    private fun scheduleLocalRetirementLocked(session: OutgoingSession) {
        session.retirement?.cancel()
        session.retirement = scheduler.schedule(LOCAL_CANCEL_RETIRE_MS) {
            synchronized(lock) {
                if (!closed && !session.terminal && session.cancelling && session.terminationSequence == null) {
                    cleanupOutgoingLocked(session)
                }
            }
        }
    }

    private fun emitOutgoingTerminalLocked(session: OutgoingSession, event: RasvenIax2OutgoingEvent) {
        if (session.terminal) return
        session.terminal = true
        session.callback(event)
        cleanupOutgoingLocked(session)
    }

    private fun cleanupOutgoingLocked(session: OutgoingSession) {
        clearPendingLocked(session)
        session.timeout?.cancel(); session.timeout = null
        session.retirement?.cancel(); session.retirement = null
        session.mediaReceiver = null
        session.terminal = true
        sessions.remove(session.localCallNumber)
        outgoingByAttempt.remove(session.attemptId)
        allocator.release(session.localCallNumber)
    }

    private fun clearPendingLocked(session: Session) {
        session.pending.values.forEach { it.timer?.cancel() }
        session.pending.clear()
    }

    private fun onTransportFailure() {
        synchronized(lock) {
            if (closed) return
            transportFailed = true
            lastRegistrationFailureRetryable = true
            isRegistered = false
            registrationRefresh?.cancel(); registrationRefresh = null
            registrationReleaseDeadline?.cancel(); registrationReleaseDeadline = null
            val current = sessions.values.toList()
            current.forEach { session ->
                when (session) {
                    is MonitorSession -> cleanupMonitorLocked(session)
                    is RegistrationSession -> failRegistrationLocked(session)
                    is OutgoingSession -> when {
                        session.cancelling || session.productTerminalDelivered -> cleanupOutgoingLocked(session)
                        session.connected -> {
                            session.callback(RasvenIax2OutgoingEvent.RemoteHangup)
                            cleanupOutgoingLocked(session)
                        }
                        else -> emitOutgoingTerminalLocked(session, RasvenIax2OutgoingEvent.NetworkFailure)
                    }
                    is IncomingSession -> if (session.localTermination) cleanupIncomingLocked(session)
                        else terminalIncomingLocked(session)
                }
            }
            // DNS or socket setup can fail before a protocol session exists.
            registrationCallback?.let { callback ->
                registrationDeadline?.cancel(); registrationDeadline = null
                registrationCallback = null
                callback(RasvenIax2RegistrationEvent.Failed)
            }
        }
    }

    private fun elapsedTimestamp(session: Session): Long =
        (clock.nowMillis() - session.startMillis).coerceAtLeast(0L) and 0xffff_ffffL

    override fun close() {
        val terminalCallbacks = synchronized(lock) {
            if (closed) return
            // Runtime invalidation (e.g. rejected registration refresh) must retire Product
            // and Telecom too. Invoke callbacks after releasing the engine monitor.
            val callbacks = mutableListOf<() -> Unit>()
            sessions.values.forEach { session ->
                when (session) {
                    is OutgoingSession -> if (!session.terminal && !session.productTerminalDelivered && !session.cancelling) {
                        callbacks += { session.callback(RasvenIax2OutgoingEvent.NetworkFailure) }
                    }
                    is IncomingSession -> if (!session.terminal && !session.localTermination) {
                        val callback = incomingCallback
                        val event = RasvenIax2IncomingEvent.RemoteCancel(session.sessionId, session.connected)
                        if (callback != null) callbacks += { callback(event) }
                    }
                    else -> Unit
                }
            }
            closed = true
            isRegistered = false
            registrationRefresh?.cancel(); registrationRefresh = null
            registrationDeadline?.cancel(); registrationDeadline = null
            registrationCallback = null
            sessions.values.toList().forEach { session ->
                clearPendingLocked(session)
                if (session is OutgoingSession) {
                    session.timeout?.cancel()
                    session.retirement?.cancel()
                }
                allocator.release(session.localCallNumber)
            }
            sessions.clear()
            outgoingByAttempt.clear()
            incomingByRemoteCall.clear()
            incomingCallback = null
            transport.close()
            scheduler.close()
            callbacks
        }
        terminalCallbacks.forEach { it() }
    }
}
