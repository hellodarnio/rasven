package com.rasven.android.product.call

import com.rasven.android.product.setup.RasvenRegistrationState
import org.junit.Assert.*
import org.junit.Test

class RasvenOutgoingCallStoreTest {
    private class Gateway : RasvenOutgoingCallGateway {
        val starts = mutableListOf<RasvenOutgoingCallAttempt>()
        var callback: ((RasvenOutgoingTransportEvent) -> Unit)? = null
        override fun start(attempt: RasvenOutgoingCallAttempt, onEvent: (RasvenOutgoingTransportEvent) -> Unit): RasvenOutgoingStartDisposition {
            starts += attempt; callback = onEvent
            return RasvenOutgoingStartDisposition.Accepted
        }
        override fun cancel(attemptId: Long) = Unit
    }
    @Test fun freshUiReattachesConnectedCallWithoutReplayingDial() {
        val gateway = Gateway(); val store = RasvenOutgoingCallStore()
        val original = store.current(gateway)
        assertTrue(original.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, RasvenRegistrationState.Registered))
        gateway.callback!!(RasvenOutgoingTransportEvent.Connected(RasvenOutgoingConnectedSession(99, 1_000)))
        val replacementUi = store.current(gateway)
        assertSame(original, replacementUi)
        assertEquals(original.state, replacementUi.state)
        assertEquals(1, gateway.starts.size)
        assertFalse(replacementUi.requestStart("306", RasvenOutgoingCallOrigin.HomeDirectDial, true, RasvenRegistrationState.Registered))
        gateway.callback!!(RasvenOutgoingTransportEvent.RemoteHangup)
        assertTrue(replacementUi.state is RasvenOutgoingCallState.Ended)
    }
    @Test fun gatewayReplacementCannotReuseOldAttemptOrAcceptItsLateEvent() {
        val store = RasvenOutgoingCallStore(); val first = Gateway(); val next = Gateway()
        val old = store.current(first)
        old.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, RasvenRegistrationState.Registered)
        val fresh = store.current(next)
        fresh.requestStart("306", RasvenOutgoingCallOrigin.HomeDirectDial, true, RasvenRegistrationState.Registered)
        assertNotEquals(first.starts.single().id, next.starts.single().id)
        first.callback!!(RasvenOutgoingTransportEvent.Busy)
        assertTrue(fresh.state is RasvenOutgoingCallState.Dialing)
        assertEquals("306", fresh.state.attemptOrNull()!!.destination)
    }
    @Test fun dismissalAndNextCallReuseOwnerButNeverCallIdentity() {
        val store = RasvenOutgoingCallStore(); val gateway = Gateway(); val owner = store.current(gateway)
        owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, RasvenRegistrationState.Registered)
        gateway.callback!!(RasvenOutgoingTransportEvent.Busy)
        assertTrue(store.current(gateway).closeFailureSurface())
        store.current(gateway).requestStart("306", RasvenOutgoingCallOrigin.HomeDirectDial, true, RasvenRegistrationState.Registered)
        assertEquals(2, gateway.starts.size)
        assertTrue(gateway.starts.last().id > gateway.starts.first().id)
    }
}
