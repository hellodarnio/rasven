package com.rasven.android.product.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RasvenPbxTransferGatewayTest {
    private val original = RasvenTransferCallLeg(
        RasvenCallLegRole.Original, iaxSessionId = 41, telecomCallId = "telecom-original", historyId = "history-original",
    )
    private val consultation = RasvenTransferCallLeg(
        RasvenCallLegRole.Consultation, iaxSessionId = 42, telecomCallId = "telecom-consult", historyId = "history-consult",
    )
    private val destination = RasvenTransferDestination(label = "فروش", address = "305")

    @Test fun productionGatewayFailsClosedForBothCapabilitiesAndEveryOperation() {
        assertFalse(RasvenUnsupportedPbxTransferGateway.supports(RasvenTransferCapability.Blind))
        assertFalse(RasvenUnsupportedPbxTransferGateway.supports(RasvenTransferCapability.Attended))
        val expected = RasvenPbxTransferResult.Failed(RasvenPbxTransferResult.Failure.Unsupported)
        assertEquals(expected, RasvenUnsupportedPbxTransferGateway.blindTransfer(original, destination))
        assertEquals(expected, RasvenUnsupportedPbxTransferGateway.startConsultation(original, destination))
        val legs = RasvenAttendedTransferLegs(original, consultation)
        assertEquals(expected, RasvenUnsupportedPbxTransferGateway.completeAttendedTransfer(legs))
        assertEquals(expected, RasvenUnsupportedPbxTransferGateway.cancelAttendedTransfer(legs))
    }

    @Test fun transferDestinationPreservesTheExactDialPlanAddress() {
        assertEquals("305", destination.address)
    }

    @Test(expected = IllegalArgumentException::class)
    fun manualDestinationCannotSilentlyTrimUserInput() {
        RasvenTransferDestination(label = "دستی", address = " 305 ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun attendedLegsCannotAliasOneIaxSession() {
        val duplicate = consultation.copy(iaxSessionId = original.iaxSessionId)
        RasvenAttendedTransferLegs(original, duplicate)
    }
}
