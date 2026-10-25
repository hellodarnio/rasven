package com.rasven.android.product.call

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.rasven.android.product.setup.RasvenRegistrationState

/** Stage5B6 source ledger: Product 145:2557; Final 884:5704 + 847:5490/5511/5550/5585/5620. */
internal enum class RasvenOutgoingCallOrigin {
    HomeDirectDial,
    Contacts,
    HistoryCallback,
    RetryOfPreviousAttempt,
}

internal data class RasvenOutgoingCallAttempt(
    val id: Long,
    val destination: String,
    val origin: RasvenOutgoingCallOrigin,
    val displayIdentity: String? = null,
)

/** Opaque handoff owned by the transport. No account or protocol secret is exposed to Product UI. */
internal data class RasvenOutgoingConnectedSession(
    val opaqueId: Long,
    val connectedAtElapsedMillis: Long = System.nanoTime() / 1_000_000L,
)

internal sealed interface RasvenOutgoingCallState {
    data object Idle : RasvenOutgoingCallState

    data class AwaitingMicrophonePermission(
        val destination: String,
        val origin: RasvenOutgoingCallOrigin,
        val displayIdentity: String? = null,
    ) : RasvenOutgoingCallState

    data class PreparingAttempt(val attempt: RasvenOutgoingCallAttempt) : RasvenOutgoingCallState
    data class Dialing(val attempt: RasvenOutgoingCallAttempt) : RasvenOutgoingCallState
    data class Ringing(val attempt: RasvenOutgoingCallAttempt) : RasvenOutgoingCallState
    data class Busy(val attempt: RasvenOutgoingCallAttempt) : RasvenOutgoingCallState
    data class Rejected(val attempt: RasvenOutgoingCallAttempt) : RasvenOutgoingCallState
    data class NoAnswer(val attempt: RasvenOutgoingCallAttempt) : RasvenOutgoingCallState
    data class NetworkError(val attempt: RasvenOutgoingCallAttempt) : RasvenOutgoingCallState
    data class ConnectedBoundary(
        val attempt: RasvenOutgoingCallAttempt,
        val session: RasvenOutgoingConnectedSession,
        val peerHeld: Boolean = false,
    ) : RasvenOutgoingCallState
    data class Cancelled(val attempt: RasvenOutgoingCallAttempt) : RasvenOutgoingCallState
    data class Ended(val summary: RasvenCallEndSummary) : RasvenOutgoingCallState
}

internal sealed interface RasvenOutgoingTransportEvent {
    data object Ringing : RasvenOutgoingTransportEvent
    data object Busy : RasvenOutgoingTransportEvent
    data object Rejected : RasvenOutgoingTransportEvent
    data object NoAnswer : RasvenOutgoingTransportEvent
    data object NetworkFailure : RasvenOutgoingTransportEvent
    data class Connected(val session: RasvenOutgoingConnectedSession) : RasvenOutgoingTransportEvent
    data object RemoteHangup : RasvenOutgoingTransportEvent
    data object PeerHeld : RasvenOutgoingTransportEvent
    data object PeerResumed : RasvenOutgoingTransportEvent
    data object OtherFailure : RasvenOutgoingTransportEvent
}

internal enum class RasvenOutgoingStartDisposition {
    Accepted,
    TransportUnavailable,
}

/** Call-engine boundary. Product consumes typed events and never raw IAX2 frames or causes. */
internal interface RasvenOutgoingCallGateway {
    fun start(
        attempt: RasvenOutgoingCallAttempt,
        onEvent: (RasvenOutgoingTransportEvent) -> Unit,
    ): RasvenOutgoingStartDisposition

    fun cancel(attemptId: Long)
}

/** Fail-closed fallback for tests or environments where the runtime transport is unavailable. */
internal object RasvenUnavailableOutgoingCallGateway : RasvenOutgoingCallGateway {
    override fun start(
        attempt: RasvenOutgoingCallAttempt,
        onEvent: (RasvenOutgoingTransportEvent) -> Unit,
    ): RasvenOutgoingStartDisposition = RasvenOutgoingStartDisposition.TransportUnavailable

    override fun cancel(attemptId: Long) = Unit
}

internal fun RasvenOutgoingCallState.attemptOrNull(): RasvenOutgoingCallAttempt? = when (this) {
    is RasvenOutgoingCallState.PreparingAttempt -> attempt
    is RasvenOutgoingCallState.Dialing -> attempt
    is RasvenOutgoingCallState.Ringing -> attempt
    is RasvenOutgoingCallState.Busy -> attempt
    is RasvenOutgoingCallState.Rejected -> attempt
    is RasvenOutgoingCallState.NoAnswer -> attempt
    is RasvenOutgoingCallState.NetworkError -> attempt
    is RasvenOutgoingCallState.ConnectedBoundary -> attempt
    is RasvenOutgoingCallState.Cancelled -> attempt
    is RasvenOutgoingCallState.Ended -> null
    RasvenOutgoingCallState.Idle,
    is RasvenOutgoingCallState.AwaitingMicrophonePermission -> null
}

internal fun RasvenOutgoingCallState.isFailureSurface(): Boolean =
    this is RasvenOutgoingCallState.Busy ||
        this is RasvenOutgoingCallState.Rejected ||
        this is RasvenOutgoingCallState.NoAnswer ||
        this is RasvenOutgoingCallState.NetworkError

/**
 * One lifecycle-retained owner for Home, Contacts and Retry. Attempt IDs reject stale callbacks,
 * and permission launch is explicit user action rather than a composition side effect.
 */
internal class RasvenOutgoingCallViewModel private constructor(
    private val gatewayProvider: () -> RasvenOutgoingCallGateway,
    private val nextAttemptId: () -> Long,
) : ViewModel() {
    constructor() : this({ RasvenRuntimeOutgoingCallGateway.current() }, java.util.concurrent.atomic.AtomicLong()::incrementAndGet)
    internal constructor(gateway: RasvenOutgoingCallGateway) : this({ gateway }, java.util.concurrent.atomic.AtomicLong()::incrementAndGet)
    internal constructor(gateway: RasvenOutgoingCallGateway, nextAttemptId: () -> Long) : this({ gateway }, nextAttemptId)

    var state: RasvenOutgoingCallState by mutableStateOf<RasvenOutgoingCallState>(RasvenOutgoingCallState.Idle)
        private set

    var hasRequestedMicrophonePermission: Boolean by mutableStateOf(false)
        private set

    var microphoneRequestInFlight: Boolean by mutableStateOf(false)
        private set

    internal var transportStartCount: Int = 0
        private set

    internal var transportCancelCount: Int = 0
        private set

    internal var transportUnavailableCount: Int = 0
        private set

    internal var ignoredUnknownFailureCount: Int = 0
        private set

    fun requestStart(
        destination: String,
        origin: RasvenOutgoingCallOrigin,
        microphoneGranted: Boolean,
        registrationState: RasvenRegistrationState,
        displayIdentity: String? = null,
    ): Boolean {
        val normalizedDestination = destination.trim()
        if (normalizedDestination.isEmpty()) return false
        if (registrationState !is RasvenRegistrationState.Registered) return false
        synchronized(this) {
            if (state !is RasvenOutgoingCallState.Idle) return false
            if (!microphoneGranted) {
                state = RasvenOutgoingCallState.AwaitingMicrophonePermission(
                    destination = normalizedDestination,
                    origin = origin,
                    displayIdentity = displayIdentity,
                )
                return true
            }
        }
        return launchNewAttempt(normalizedDestination, origin, displayIdentity)
    }

    /** One permission CTA tap owns one system request/settings launch. */
    fun beginMicrophonePermissionAction(): Boolean = synchronized(this) {
        if (state !is RasvenOutgoingCallState.AwaitingMicrophonePermission) return false
        if (microphoneRequestInFlight) return false
        microphoneRequestInFlight = true
        true
    }

    fun markMicrophonePermissionRequestIssued() {
        synchronized(this) {
            if (!microphoneRequestInFlight) return
            hasRequestedMicrophonePermission = true
        }
    }

    /** Duplicate permission callbacks cannot create a second attempt. */
    fun finishMicrophonePermissionAction(granted: Boolean): Boolean {
        val pending = synchronized(this) {
            microphoneRequestInFlight = false
            val current = state as? RasvenOutgoingCallState.AwaitingMicrophonePermission ?: return false
            if (!granted) return false
            current
        }
        return launchNewAttempt(pending.destination, pending.origin, pending.displayIdentity)
    }

    fun closePermissionSurface(): Boolean = synchronized(this) {
        if (state !is RasvenOutgoingCallState.AwaitingMicrophonePermission) return false
        microphoneRequestInFlight = false
        state = RasvenOutgoingCallState.Idle
        true
    }

    fun retry(
        microphoneGranted: Boolean,
        registrationState: RasvenRegistrationState,
    ): Boolean {
        val previous = synchronized(this) {
            if (!state.isFailureSurface()) return false
            if (registrationState !is RasvenRegistrationState.Registered) return false
            state.attemptOrNull() ?: return false
        }
        if (!microphoneGranted) {
            synchronized(this) {
                if (!state.isFailureSurface()) return false
                state = RasvenOutgoingCallState.AwaitingMicrophonePermission(
                    destination = previous.destination,
                    origin = RasvenOutgoingCallOrigin.RetryOfPreviousAttempt,
                    displayIdentity = previous.displayIdentity,
                )
            }
            return true
        }
        return launchNewAttempt(previous.destination, RasvenOutgoingCallOrigin.RetryOfPreviousAttempt, previous.displayIdentity)
    }

    fun closeFailureSurface(): Boolean = synchronized(this) {
        if (!state.isFailureSurface()) return false
        state = RasvenOutgoingCallState.Idle
        true
    }

    /** Cancel is transport-owned and idempotent. Connected calls retain an end summary. */
    fun cancel(): Boolean {
        val attempt = synchronized(this) {
            val current = state
            if (current !is RasvenOutgoingCallState.Dialing && current !is RasvenOutgoingCallState.Ringing &&
                current !is RasvenOutgoingCallState.PreparingAttempt &&
                current !is RasvenOutgoingCallState.ConnectedBoundary) {
                return false
            }
            current.attemptOrNull() ?: return false
        }
        val prior = synchronized(this) { state as? RasvenOutgoingCallState.ConnectedBoundary }
        gatewayProvider().cancel(attempt.id)
        synchronized(this) {
            if (state.attemptOrNull()?.id != attempt.id) return false
            if (state !is RasvenOutgoingCallState.Dialing && state !is RasvenOutgoingCallState.Ringing &&
                state !is RasvenOutgoingCallState.PreparingAttempt &&
                state !is RasvenOutgoingCallState.ConnectedBoundary) return false
            transportCancelCount += 1
            state = if (prior != null) endedState(attempt, prior.session) else RasvenOutgoingCallState.Cancelled(attempt)
        }
        return true
    }

    fun closeCancelled(): Boolean = synchronized(this) {
        if (state !is RasvenOutgoingCallState.Cancelled) return false
        state = RasvenOutgoingCallState.Idle
        true
    }

    fun closeCallEndSummary(): Boolean = synchronized(this) {
        if (state !is RasvenOutgoingCallState.Ended) return false
        state = RasvenOutgoingCallState.Idle
        true
    }

    private fun launchNewAttempt(destination: String, origin: RasvenOutgoingCallOrigin, displayIdentity: String? = null): Boolean {
        val attempt = synchronized(this) {
            if (
                state !is RasvenOutgoingCallState.Idle &&
                !state.isFailureSurface() &&
                state !is RasvenOutgoingCallState.AwaitingMicrophonePermission
            ) return false
            RasvenOutgoingCallAttempt(
                id = nextAttemptId(),
                destination = destination,
                origin = origin,
                displayIdentity = displayIdentity,
            ).also { state = RasvenOutgoingCallState.PreparingAttempt(it) }
        }

        val disposition = gatewayProvider().start(attempt) { event ->
            acceptTransportEvent(attempt.id, event)
        }
        synchronized(this) {
            transportStartCount += 1
            val stillCurrent = state.attemptOrNull()?.id == attempt.id
            if (!stillCurrent) return true
            when (disposition) {
                RasvenOutgoingStartDisposition.Accepted -> {
                    if (state is RasvenOutgoingCallState.PreparingAttempt) {
                        state = RasvenOutgoingCallState.Dialing(attempt)
                    }
                }
                RasvenOutgoingStartDisposition.TransportUnavailable -> {
                    transportUnavailableCount += 1
                    // Fail closed: do not fabricate NetworkError or Dialing when no real transport exists.
                    state = RasvenOutgoingCallState.Idle
                    return false
                }
            }
        }
        return disposition == RasvenOutgoingStartDisposition.Accepted
    }

    private fun acceptTransportEvent(attemptId: Long, event: RasvenOutgoingTransportEvent) {
        synchronized(this) {
            val currentAttempt = state.attemptOrNull() ?: return
            if (currentAttempt.id != attemptId) return
            val connected = state is RasvenOutgoingCallState.ConnectedBoundary
            if (!connected && state !is RasvenOutgoingCallState.Dialing && state !is RasvenOutgoingCallState.Ringing &&
                state !is RasvenOutgoingCallState.PreparingAttempt) return
            if (connected && event !is RasvenOutgoingTransportEvent.NetworkFailure && event !is RasvenOutgoingTransportEvent.OtherFailure &&
                event !is RasvenOutgoingTransportEvent.RemoteHangup && event !is RasvenOutgoingTransportEvent.PeerHeld &&
                event !is RasvenOutgoingTransportEvent.PeerResumed) return
            state = when (event) {
                RasvenOutgoingTransportEvent.Ringing -> RasvenOutgoingCallState.Ringing(currentAttempt)
                RasvenOutgoingTransportEvent.Busy -> RasvenOutgoingCallState.Busy(currentAttempt)
                RasvenOutgoingTransportEvent.Rejected -> RasvenOutgoingCallState.Rejected(currentAttempt)
                RasvenOutgoingTransportEvent.NoAnswer -> RasvenOutgoingCallState.NoAnswer(currentAttempt)
                RasvenOutgoingTransportEvent.NetworkFailure -> if (connected) {
                    endedState(currentAttempt, (state as RasvenOutgoingCallState.ConnectedBoundary).session)
                } else RasvenOutgoingCallState.NetworkError(currentAttempt)
                is RasvenOutgoingTransportEvent.Connected -> RasvenOutgoingCallState.ConnectedBoundary(
                    currentAttempt,
                    event.session,
                )
                RasvenOutgoingTransportEvent.OtherFailure -> {
                    if (state is RasvenOutgoingCallState.ConnectedBoundary) {
                        endedState(currentAttempt, (state as RasvenOutgoingCallState.ConnectedBoundary).session)
                    } else {
                    ignoredUnknownFailureCount += 1
                    // Local media/unknown failures terminate fail-closed without a false network label.
                    RasvenOutgoingCallState.Idle
                    }
                }
                RasvenOutgoingTransportEvent.RemoteHangup -> {
                    val session = (state as? RasvenOutgoingCallState.ConnectedBoundary)?.session ?: return
                    endedState(currentAttempt, session, remoteTermination = true)
                }
                RasvenOutgoingTransportEvent.PeerHeld -> (state as? RasvenOutgoingCallState.ConnectedBoundary)?.copy(peerHeld = true) ?: return
                RasvenOutgoingTransportEvent.PeerResumed -> (state as? RasvenOutgoingCallState.ConnectedBoundary)?.copy(peerHeld = false) ?: return
            }
        }
    }

    private fun endedState(
        attempt: RasvenOutgoingCallAttempt,
        session: RasvenOutgoingConnectedSession,
        remoteTermination: Boolean = false,
    ) =
        RasvenOutgoingCallState.Ended(createCallEndSummary(
            displayIdentity = attempt.displayIdentity,
            callableNumber = attempt.destination,
            connectedAtElapsedMillis = session.connectedAtElapsedMillis,
            endedAtElapsedMillis = System.nanoTime() / 1_000_000L,
            endedAtEpochMillis = System.currentTimeMillis(),
            remoteTermination = remoteTermination,
            callSessionId = session.opaqueId,
        ))
}
