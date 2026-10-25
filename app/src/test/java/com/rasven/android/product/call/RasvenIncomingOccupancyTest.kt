package com.rasven.android.product.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenIncomingOccupancyTest {
    @Test fun completedSummaryDoesNotKeepTheLineBusyUntilTheUserDismissesIt() {
        val ended = RasvenIncomingCallState.Ended(createCallEndSummary(
            displayIdentity = null, callableNumber = "offline-fixture",
            connectedAtElapsedMillis = 1000, endedAtElapsedMillis = 2000,
            endedAtEpochMillis = 2000, remoteTermination = true, callSessionId = 7))
        assertFalse("A completed call has no live leg; a locked user need not dismiss its summary",
            ended.blocksNewIncomingCall())
        assertFalse(RasvenIncomingCallState.Idle.blocksNewIncomingCall())
    }

    @Test fun realRingingPermissionAnsweringAndActiveOwnersStillBlockASecondCall() {
        val call = RasvenIncomingCall(7, "fixture", "fixture", null, null, 2000)
        listOf(RasvenIncomingCallState.Ringing(call), RasvenIncomingCallState.AwaitingMicrophone(call),
            RasvenIncomingCallState.Answering(call), RasvenIncomingCallState.Active(call)).forEach {
            assertTrue(it.blocksNewIncomingCall())
        }
    }
}
