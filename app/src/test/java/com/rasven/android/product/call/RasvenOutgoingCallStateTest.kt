package com.rasven.android.product.call

import com.rasven.android.product.contacts.prepareContactDialDestination
import com.rasven.android.product.setup.RasvenRegistrationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenOutgoingCallStateTest {
    private class FakeGateway : RasvenOutgoingCallGateway {
        val attempts = mutableListOf<RasvenOutgoingCallAttempt>()
        val callbacks = mutableMapOf<Long, (RasvenOutgoingTransportEvent) -> Unit>()
        val cancels = mutableListOf<Long>()
        var disposition = RasvenOutgoingStartDisposition.Accepted

        override fun start(
            attempt: RasvenOutgoingCallAttempt,
            onEvent: (RasvenOutgoingTransportEvent) -> Unit,
        ): RasvenOutgoingStartDisposition {
            attempts += attempt
            callbacks[attempt.id] = onEvent
            return disposition
        }

        override fun cancel(attemptId: Long) {
            cancels += attemptId
        }

        fun emit(id: Long, event: RasvenOutgoingTransportEvent) {
            callbacks[id]?.invoke(event)
        }
    }

    private fun registered() = RasvenRegistrationState.Registered

    @Test fun homeDirectDialStartsExactlyOneAttemptAndPreserves305() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        assertTrue(owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered()))
        assertFalse(owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered()))
        assertEquals(1, gateway.attempts.size)
        assertEquals("305", gateway.attempts.single().destination)
        assertEquals(RasvenOutgoingCallOrigin.HomeDirectDial, gateway.attempts.single().origin)
    }

    @Test fun contactOriginUsesAlreadyPreparedContactsDestinationWithoutSecondTransformation() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        val prepared = prepareContactDialDestination("09121234567", "9")
        assertTrue(owner.requestStart(prepared, RasvenOutgoingCallOrigin.Contacts, true, registered()))
        assertEquals(prepared, gateway.attempts.single().destination)
    }

    @Test fun contactInternal305Remains305() {
        assertEquals("305", prepareContactDialDestination("305", "9"))
    }

    @Test fun noCallStartsBeforeMicrophoneGrant() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        assertTrue(owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, false, registered()))
        assertTrue(owner.state is RasvenOutgoingCallState.AwaitingMicrophonePermission)
        assertEquals(0, gateway.attempts.size)
    }

    @Test fun permissionGrantStartsExactlyOneAttemptAndDuplicateCallbackDoesNot() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, false, registered())
        assertTrue(owner.beginMicrophonePermissionAction())
        owner.markMicrophonePermissionRequestIssued()
        assertTrue(owner.finishMicrophonePermissionAction(true))
        assertFalse(owner.finishMicrophonePermissionAction(true))
        assertEquals(1, gateway.attempts.size)
        assertTrue(owner.state is RasvenOutgoingCallState.Dialing)
    }

    @Test fun permissionDenialStartsZeroAttempts() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, false, registered())
        assertTrue(owner.beginMicrophonePermissionAction())
        owner.markMicrophonePermissionRequestIssued()
        assertFalse(owner.finishMicrophonePermissionAction(false))
        assertEquals(0, gateway.attempts.size)
        assertTrue(owner.state is RasvenOutgoingCallState.AwaitingMicrophonePermission)
    }

    @Test fun permissionActionIsDuplicateProtected() {
        val owner = RasvenOutgoingCallViewModel(FakeGateway())
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, false, registered())
        assertTrue(owner.beginMicrophonePermissionAction())
        assertFalse(owner.beginMicrophonePermissionAction())
    }

    @Test fun dialingMapsBusyRejectedNoAnswerAndNetworkDistinctly() {
        fun stateFor(event: RasvenOutgoingTransportEvent): RasvenOutgoingCallState {
            val gateway = FakeGateway()
            val owner = RasvenOutgoingCallViewModel(gateway)
            owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
            val id = gateway.attempts.single().id
            gateway.emit(id, event)
            return owner.state
        }
        assertTrue(stateFor(RasvenOutgoingTransportEvent.Busy) is RasvenOutgoingCallState.Busy)
        assertTrue(stateFor(RasvenOutgoingTransportEvent.Rejected) is RasvenOutgoingCallState.Rejected)
        assertTrue(stateFor(RasvenOutgoingTransportEvent.NoAnswer) is RasvenOutgoingCallState.NoAnswer)
        assertTrue(stateFor(RasvenOutgoingTransportEvent.NetworkFailure) is RasvenOutgoingCallState.NetworkError)
    }

    @Test fun ringingIsVisibleAndRemainsCancelableUntilAnswer() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        assertTrue(owner.requestStart("201", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered()))
        val id = gateway.attempts.single().id
        gateway.emit(id, RasvenOutgoingTransportEvent.Ringing)
        assertTrue(owner.state is RasvenOutgoingCallState.Ringing)
        assertTrue(owner.cancel())
        assertEquals(listOf(id), gateway.cancels)
        gateway.emit(id, RasvenOutgoingTransportEvent.Connected(RasvenOutgoingConnectedSession(99)))
        assertTrue(owner.state is RasvenOutgoingCallState.Cancelled)
    }

    @Test fun retryCreatesNewAttemptIdentityAndRetryOrigin() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        val first = gateway.attempts.single()
        gateway.emit(first.id, RasvenOutgoingTransportEvent.Busy)
        assertTrue(owner.retry(true, registered()))
        val second = gateway.attempts.last()
        assertNotEquals(first.id, second.id)
        assertEquals(first.destination, second.destination)
        assertEquals(RasvenOutgoingCallOrigin.RetryOfPreviousAttempt, second.origin)
    }

    @Test fun staleOldCallbackCannotMutateNewRetryAttempt() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        val first = gateway.attempts.single()
        gateway.emit(first.id, RasvenOutgoingTransportEvent.Busy)
        owner.retry(true, registered())
        val second = gateway.attempts.last()
        gateway.emit(first.id, RasvenOutgoingTransportEvent.Rejected)
        val state = owner.state as RasvenOutgoingCallState.Dialing
        assertEquals(second.id, state.attempt.id)
    }

    @Test fun duplicateOldFailureAfterRetryCannotOverwriteCurrent() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        val first = gateway.attempts.single()
        gateway.emit(first.id, RasvenOutgoingTransportEvent.NoAnswer)
        owner.retry(true, registered())
        val second = gateway.attempts.last()
        gateway.emit(first.id, RasvenOutgoingTransportEvent.NoAnswer)
        assertEquals(second.id, (owner.state as RasvenOutgoingCallState.Dialing).attempt.id)
    }

    @Test fun doubleRetryStartsOnce() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        gateway.emit(gateway.attempts.single().id, RasvenOutgoingTransportEvent.Busy)
        assertTrue(owner.retry(true, registered()))
        assertFalse(owner.retry(true, registered()))
        assertEquals(2, gateway.attempts.size)
    }

    @Test fun doubleCancelSendsOneTerminationCommand() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        assertTrue(owner.cancel())
        assertFalse(owner.cancel())
        assertEquals(1, gateway.cancels.size)
        assertTrue(owner.state is RasvenOutgoingCallState.Cancelled)
    }

    @Test fun callbackAfterCancelIsStaleAndCannotChangeCancelledState() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        val attempt = gateway.attempts.single()
        owner.cancel()
        gateway.emit(attempt.id, RasvenOutgoingTransportEvent.Busy)
        assertTrue(owner.state is RasvenOutgoingCallState.Cancelled)
    }

    @Test fun cancelAStartBThenLateACallbackCannotMutateB() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("111", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        val first = gateway.attempts.single()
        owner.cancel()
        owner.closeCancelled()
        owner.requestStart("222", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        val second = gateway.attempts.last()
        gateway.emit(first.id, RasvenOutgoingTransportEvent.NetworkFailure)
        assertEquals(second.id, (owner.state as RasvenOutgoingCallState.Dialing).attempt.id)
    }

    @Test fun unknownFailureDoesNotMasqueradeAsKnownProductCause() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        gateway.emit(gateway.attempts.single().id, RasvenOutgoingTransportEvent.OtherFailure)
        assertTrue(owner.state is RasvenOutgoingCallState.Idle)
        assertEquals(1, owner.ignoredUnknownFailureCount)
    }

    @Test fun registrationGateBlocksUnregisteredRegisteringAndFailed() {
        val states = listOf(
            RasvenRegistrationState.Unregistered,
            RasvenRegistrationState.Registering,
            RasvenRegistrationState.Failed(com.rasven.android.product.setup.RasvenRegistrationErrorCategory.Unknown),
        )
        states.forEach { registration ->
            val gateway = FakeGateway()
            val owner = RasvenOutgoingCallViewModel(gateway)
            assertFalse(owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registration))
            assertEquals(0, gateway.attempts.size)
        }
    }

    @Test fun connectedProducesTypedSessionHandoffWithoutDroppingAttemptIdentity() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        val attempt = gateway.attempts.single()
        gateway.emit(attempt.id, RasvenOutgoingTransportEvent.Connected(RasvenOutgoingConnectedSession(77L)))
        val connected = owner.state as RasvenOutgoingCallState.ConnectedBoundary
        assertEquals(attempt.id, connected.attempt.id)
        assertEquals(77L, connected.session.opaqueId)
    }

    @Test fun activeCallEndUsesTheSameAttemptCancellationBoundary() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        val attempt = gateway.attempts.single()
        gateway.emit(attempt.id, RasvenOutgoingTransportEvent.Connected(RasvenOutgoingConnectedSession(78L)))
        assertTrue(owner.cancel())
        assertEquals(listOf(attempt.id), gateway.cancels)
        assertTrue(owner.state is RasvenOutgoingCallState.Ended)
        assertTrue(owner.closeCallEndSummary())
        assertTrue(owner.state is RasvenOutgoingCallState.Idle)
    }

    @Test fun remoteHangupAfterConnectedRetainsEndSummaryAndIgnoresLateEvents() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered(), displayIdentity = "Contact")
        val attempt = gateway.attempts.single()
        gateway.emit(attempt.id, RasvenOutgoingTransportEvent.Connected(RasvenOutgoingConnectedSession(79L, 1_000L)))
        gateway.emit(attempt.id, RasvenOutgoingTransportEvent.RemoteHangup)
        val ended = owner.state as RasvenOutgoingCallState.Ended
        assertEquals("Contact", ended.summary.displayIdentity)
        assertEquals("305", ended.summary.callableNumber)
        assertTrue(ended.summary.durationSeconds >= 0)
        gateway.emit(attempt.id, RasvenOutgoingTransportEvent.NetworkFailure)
        assertEquals(ended, owner.state)
    }

    @Test fun unavailableProductionStyleGatewayFailsClosedWithoutFakeDialingOrFailure() {
        val owner = RasvenOutgoingCallViewModel(RasvenUnavailableOutgoingCallGateway)
        assertFalse(owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered()))
        assertTrue(owner.state is RasvenOutgoingCallState.Idle)
        assertEquals(1, owner.transportUnavailableCount)
    }

    @Test fun closingPermissionCreatesNoAttempt() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, false, registered())
        assertTrue(owner.closePermissionSurface())
        assertEquals(0, gateway.attempts.size)
        assertTrue(owner.state is RasvenOutgoingCallState.Idle)
    }

    @Test fun closingFailureCreatesNoRetry() {
        val gateway = FakeGateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, registered())
        gateway.emit(gateway.attempts.single().id, RasvenOutgoingTransportEvent.Rejected)
        assertTrue(owner.closeFailureSurface())
        assertEquals(1, gateway.attempts.size)
        assertTrue(owner.state is RasvenOutgoingCallState.Idle)
    }

    @Test fun displayFormattingIsPersianButDestinationTruthRemainsTechnical() {
        assertEquals("۰۹۱۲ ۱۲۳ ۴۵۶۷", formatOutgoingDestinationForDisplay("09121234567"))
        assertEquals("۳۰۵", formatOutgoingDestinationForDisplay("305"))
    }
}
