package com.rasven.android.product.call

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RasvenFeatureCodeTransferTest {
    private val defaults = RasvenPbxTransferProfile.issabelAsteriskDefaults
    private lateinit var profiles: MutableProfileRepository
    private lateinit var vm: RasvenTransferViewModel
    private lateinit var pbx: DeterministicFeatureCodePbxHarness
    private val call = RasvenTransferCallContext(77L, "Support", 1_000L, outgoing = true)

    @Before fun setUp() {
        profiles = MutableProfileRepository(defaults)
        RasvenPbxTransferProfileSource.repository = profiles
        vm = RasvenTransferViewModel()
        pbx = DeterministicFeatureCodePbxHarness(vm)
    }

    @After fun tearDown() { RasvenPbxTransferProfileSource.repository = null }

    @Test fun blindReadsLatestPersistedCustomCodesAndPreservesDestinationWithoutPrefix() {
        profiles.current = defaults.copy(blindCode = "*7", attendedCode = "*8", cancelAttendedCode = "*9").withCode()
        val destination = RasvenTransferDestination("manual", "305")
        assertTrue(vm.begin(call))
        assertTrue(vm.chooseMode(RasvenTransferMode.Blind))
        assertTrue(vm.startWithDestination(destination, pbx.sender))
        assertEquals("*7305", pbx.sentDigits.joinToString(""))
        assertTrue(pbx.sessionIds.isNotEmpty())
        assertTrue(pbx.sessionIds.all { it == 77L })
        assertTrue(vm.state is RasvenTransferUiState.BlindAwaitingPbx)
        assertFalse(vm.state is RasvenTransferUiState.BlindOutcomeUnknown)
        assertFalse(vm.startWithDestination(destination, pbx.sender))
        assertEquals("*7305", pbx.sentDigits.joinToString(""))
    }

    @Test fun destinationScreenAcceptsManualNumberAndBackReturnsToModeMenu() {
        assertTrue(vm.begin(call))
        assertTrue(vm.chooseMode(RasvenTransferMode.Blind))
        assertTrue(vm.editManualDestination("305"))
        val selection = vm.state as RasvenTransferUiState.SelectingDestination
        assertEquals("305", selection.value)
        assertTrue(vm.backToModeSelection())
        assertTrue(vm.state is RasvenTransferUiState.ChoosingMode)

        assertTrue(vm.chooseMode(RasvenTransferMode.Blind))
        assertTrue(vm.editManualDestination("305"))
        assertTrue(vm.submitManualDestination(pbx.sender))
        assertEquals("${defaults.blindCode}305", pbx.sentDigits.joinToString(""))
        assertTrue(vm.state is RasvenTransferUiState.BlindAwaitingPbx)
    }

    @Test fun blindCallEndIsNotReportedAsTransferSuccessWithoutPbxConfirmation() {
        pbx.startBlind("305")
        assertTrue(vm.state is RasvenTransferUiState.BlindAwaitingPbx)
        pbx.remoteBlindTransferAccepted()
        assertTrue(vm.state is RasvenTransferUiState.BlindOutcomeUnknown)

        vm.dismissResult()
        pbx.startBlind("306")
        pbx.originalCallStillActive()
        val failed = vm.state as RasvenTransferUiState.BlindFailed
        assertTrue(failed.canReturnToCall)
    }

    @Test fun failedPartialDtmfDeliveryDoesNotRetryOrEndOriginalSession() {
        pbx.failAtDigitIndex = 2
        assertTrue(vm.begin(call))
        vm.chooseMode(RasvenTransferMode.Blind)
        assertFalse(vm.startWithDestination(RasvenTransferDestination("manual", "305"), pbx.sender))
        assertTrue(vm.state is RasvenTransferUiState.BlindFailed)
        assertEquals("*2", pbx.sentDigits.joinToString(""))
        assertEquals(77L, pbx.sessionIds.first())
    }

    @Test fun attendedConsultationCancelUsesCurrentCodeAndWaitsForRealHoldResumeCycle() {
        profiles.current = defaults.copy(attendedCode = "*8", cancelAttendedCode = "*9").withCode()
        pbx.startAttended("305")
        assertEquals("*8305", pbx.sentDigits.joinToString(""))
        assertTrue(vm.state is RasvenTransferUiState.AttendedAwaitingConsultation)
        assertTrue(vm.cancelAttendedTransfer(pbx.sender))
        assertEquals("*8305*9", pbx.sentDigits.joinToString(""))
        assertTrue(vm.state is RasvenTransferUiState.AttendedCancelAwaitingRecovery)
        vm.observePeerHeld(999L, true)
        assertTrue(vm.state is RasvenTransferUiState.AttendedCancelAwaitingRecovery)
        vm.observePeerHeld(call.sessionId, true)
        vm.observePeerHeld(call.sessionId, false)
        assertTrue(vm.state is RasvenTransferUiState.AttendedCancelledReturned)
    }

    @Test fun attendedTransferWaitsForTransferToneBeforeSendingDestination() {
        assertTrue(vm.begin(call))
        assertTrue(vm.chooseMode(RasvenTransferMode.Attended))
        assertTrue(vm.startWithDestination(RasvenTransferDestination("manual", "305"), pbx.sender))
        assertEquals("##", pbx.sentDigits.joinToString(""))
        assertTrue(vm.state is RasvenTransferUiState.AttendedAwaitingDestinationTone)

        assertTrue(vm.continueAttendedDestination(pbx.sender))
        assertEquals("##305", pbx.sentDigits.joinToString(""))
        assertTrue(vm.state is RasvenTransferUiState.AttendedAwaitingConsultation)
        assertFalse(vm.continueAttendedDestination(pbx.sender))
        assertEquals("##305", pbx.sentDigits.joinToString(""))
    }

    @Test fun persistedYeastarSiteProfileFeedsTheLiveTransferStrategy() {
        val stored = mutableMapOf<String, String>()
        val store = object : RasvenTransferProfileKeyValueStore {
            override fun get(key: String) = stored[key]
            override fun contains(key: String) = key in stored
            override fun putAll(values: Map<String, String>): Boolean {
                stored.putAll(values)
                return true
            }
        }
        val repository = RasvenPersistentTransferProfileRepository(store)
        assertTrue(repository.save(RasvenPbxTransferProfile.yeastarS50Defaults))
        RasvenPbxTransferProfileSource.repository = repository

        assertTrue(vm.begin(call))
        assertTrue(vm.chooseMode(RasvenTransferMode.Attended))
        assertTrue(vm.startWithDestination(RasvenTransferDestination("manual", "205"), pbx.sender))
        assertEquals("*3", pbx.sentDigits.joinToString(""))
        assertTrue(vm.state is RasvenTransferUiState.AttendedAwaitingDestinationTone)
        assertTrue(vm.continueAttendedDestination(pbx.sender))
        assertEquals("*3205", pbx.sentDigits.joinToString(""))
        assertTrue(vm.state is RasvenTransferUiState.AttendedAwaitingConsultation)
        assertFalse(vm.cancelAttendedTransfer(pbx.sender))
        assertEquals("*3205", pbx.sentDigits.joinToString(""))
        assertTrue(vm.state is RasvenTransferUiState.AttendedAwaitingConsultation)
    }

    @Test fun cancellingWhileWaitingForTransferToneDoesNotSendDestinationDigits() {
        assertTrue(vm.begin(call))
        assertTrue(vm.chooseMode(RasvenTransferMode.Attended))
        assertTrue(vm.startWithDestination(RasvenTransferDestination("manual", "305"), pbx.sender))
        assertTrue(vm.cancelAttendedTransfer(pbx.sender))
        assertEquals("##*1", pbx.sentDigits.joinToString(""))
        assertTrue(vm.state is RasvenTransferUiState.AttendedCancelAwaitingRecovery)
    }

    @Test fun pendingRecoveryCanShowCallControlsWithoutClaimingPbxRecovery() {
        pbx.startAttended("305")
        assertTrue(vm.cancelAttendedTransfer(pbx.sender))
        assertTrue(vm.state is RasvenTransferUiState.AttendedCancelAwaitingRecovery)
        assertTrue(vm.minimizeToActiveCall())
        assertTrue(vm.minimized)
        assertTrue(vm.state is RasvenTransferUiState.AttendedCancelAwaitingRecovery)
    }

    @Test fun noCancelProfileKeepsUnrecoveredCallStateWhenShowingControls() {
        profiles.current = RasvenPbxTransferProfile.yeastarS50Defaults
        pbx.startAttended("205")
        vm.observePeerHeld(call.sessionId, true)
        assertTrue(vm.reportAttendedDestinationBusy(call.sessionId))
        val failed = vm.state as RasvenTransferUiState.AttendedFailed
        assertFalse(failed.canReturnToCall)
        assertFalse(vm.cancelAttendedTransfer(pbx.sender))
        assertFalse(vm.returnToActiveCall())
        assertTrue(vm.minimizeToActiveCall())
        assertEquals(failed, vm.state)
        assertTrue(vm.minimized)
        assertEquals("*3205", pbx.sentDigits.joinToString(""))
    }

    @Test fun completionBackReturnsToConsultationWithoutSendingDtmf() {
        profiles.current = RasvenPbxTransferProfile.yeastarS50Defaults
        pbx.startAttended("205")
        assertTrue(vm.confirmConsultationConnected())
        assertTrue(vm.requestAttendedCompletion())
        val before = pbx.sentDigits.toList()
        assertTrue(vm.backFromAttendedCompletion())
        assertTrue(vm.state is RasvenTransferUiState.AttendedReadyToComplete)
        assertEquals(before, pbx.sentDigits)
    }

    @Test fun attendedCompletionRequiresExplicitConfirmationAndActualCallEnd() {
        pbx.startAttended("305")
        vm.confirmConsultationConnected()
        assertTrue(vm.state is RasvenTransferUiState.AttendedReadyToComplete)
        vm.requestAttendedCompletion()
        assertTrue(vm.state is RasvenTransferUiState.ConfirmingCompletion)
        assertEquals(RasvenTransferCommandResult.HangupRequired, vm.confirmAttendedCompletion(pbx.sender))
        assertTrue(vm.state is RasvenTransferUiState.CompletionArmed)
        vm.confirmHangupAccepted(call.sessionId, true)
        assertTrue(vm.state is RasvenTransferUiState.CompletionArmed)
        pbx.localOriginalCallEnded()
        assertTrue(vm.state is RasvenTransferUiState.AttendedOutcomeUnknown)
    }

    @Test fun accidentalCallEndBeforeCompletionConfirmationIsFailure() {
        pbx.startAttended("305")
        pbx.remoteOriginalCallEnded()
        val failed = vm.state as RasvenTransferUiState.AttendedFailed
        assertFalse(failed.canReturnToCall)
        assertEquals(RasvenAttendedTransferFailure.CallEnded, failed.reason)
    }

    @Test fun busyAndNoAnswerStayFailureAndKeepRealOriginalSessionRecoverable() {
        pbx.startAttended("305")
        assertTrue(vm.reportAttendedDestinationBusy(call.sessionId))
        val busy = vm.state as RasvenTransferUiState.AttendedFailed
        assertTrue(busy.canReturnToCall)
        assertEquals(RasvenAttendedTransferFailure.DestinationBusy, busy.reason)
        vm.returnToActiveCall()

        pbx.startAttended("306")
        assertTrue(vm.reportAttendedNoAnswer(call.sessionId))
        val noAnswer = vm.state as RasvenTransferUiState.AttendedFailed
        assertTrue(noAnswer.canReturnToCall)
        assertEquals(RasvenAttendedTransferFailure.NoAnswer, noAnswer.reason)
    }

    @Test fun staleEndedCallCallbackCannotChangeNewerTransfer() {
        pbx.startBlind("305")
        val before = vm.state
        vm.observeCallEnded(summary(sessionId = 88L, remote = true))
        assertEquals(before, vm.state)
        assertNotNull(vm.activeCallContext())
    }

    @Test fun configuredPresetDestinationsStaySeparateFromLabelsAndManualDoesNotPrefix() {
        profiles.current = defaults.copy(salesDestination = "210", managementDestination = "101")
        val rows = vm.configuredDestinations()
        assertEquals(listOf("پشتیبانی فنی" to "210", "مدیریت" to "101"), rows.map { it.label to it.address })
        assertTrue(vm.begin(call))
        vm.chooseMode(RasvenTransferMode.Blind)
        assertTrue(vm.enterManualDestination())
        vm.editManualDestination("305")
        assertTrue(vm.submitManualDestination(pbx.sender))
        assertEquals("*2305", pbx.sentDigits.joinToString(""))
    }

    private fun summary(sessionId: Long, remote: Boolean) = createCallEndSummary(
        displayIdentity = "Support", callableNumber = "305", connectedAtElapsedMillis = 1_000L,
        endedAtElapsedMillis = 2_000L, endedAtEpochMillis = 1_800_000_000_000L,
        remoteTermination = remote, callSessionId = sessionId,
    )

    private class MutableProfileRepository(var current: RasvenPbxTransferProfile) : RasvenPbxTransferProfileRepository {
        override fun load() = current
        override fun save(profile: RasvenPbxTransferProfile): Boolean {
            if (!profile.isValid()) return false
            current = profile
            return true
        }
        override fun resetToDefaults() = RasvenPbxTransferProfile.issabelAsteriskDefaults.also { current = it }
    }

    /** Deterministic fake PBX drives the real feature-code strategy and transfer domain. */
    private inner class DeterministicFeatureCodePbxHarness(private val domain: RasvenTransferViewModel) {
        val sentDigits = mutableListOf<Char>()
        val sessionIds = mutableListOf<Long>()
        val sender = RasvenTransferDtmfSender { sessionId, digit ->
            sessionIds += sessionId
            if (failAtDigitIndex == sentDigits.size) false else {
                sentDigits += digit
                true
            }
        }
        var failAtDigitIndex: Int = -1

        fun startBlind(destination: String) {
            domain.begin(call)
            domain.chooseMode(RasvenTransferMode.Blind)
            domain.startWithDestination(RasvenTransferDestination("manual", destination), sender)
        }
        fun startAttended(destination: String) {
            domain.begin(call)
            domain.chooseMode(RasvenTransferMode.Attended)
            domain.startWithDestination(RasvenTransferDestination("manual", destination), sender)
            domain.continueAttendedDestination(sender)
        }
        fun remoteBlindTransferAccepted() = domain.observeCallEnded(summary(77L, remote = true))
        fun originalCallStillActive() = domain.reportBlindOriginalStillActive(77L)
        fun localOriginalCallEnded() = domain.observeCallEnded(summary(77L, remote = false))
        fun remoteOriginalCallEnded() = domain.observeCallEnded(summary(77L, remote = true))
    }
}
