package com.rasven.android.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import com.rasven.android.product.call.*
import com.rasven.android.product.history.RasvenHistoryCallClassification
import com.rasven.android.product.history.RasvenHistoryEntry

/** Synthetic call presentation only: no account, transport, media, permissions or system lock operations. */
internal val englishCallFixtureStages = listOf(
    "outgoing-microphone", "outgoing-dialing", "outgoing-ringing", "outgoing-busy", "outgoing-rejected",
    "outgoing-no-answer", "outgoing-network",
    "incoming-open", "incoming-lock-presentation", "incoming-microphone", "incoming-answering",
    "incoming-active", "incoming-missed",
    "active-connected", "active-muted", "active-speaker", "active-held", "active-keypad", "active-dtmf",
    "call-summary",
    "transfer-choose", "transfer-blind-select", "transfer-attended-select",
    "transfer-blind-manual", "transfer-attended-manual", "transfer-manual-error",
    "transfer-blind-waiting", "transfer-blind-failed", "transfer-blind-ended-failed", "transfer-blind-unknown",
    "transfer-attended-waiting", "transfer-attended-ready", "transfer-confirm", "transfer-completing",
    "transfer-returning", "transfer-returned", "transfer-attended-unknown", "transfer-attended-busy",
    "transfer-attended-no-answer", "transfer-manual-hold-error", "transfer-attended-ended",
)

@Composable internal fun RasvenEnglishCallFixture(stage: String, onNavigate: (String) -> Unit = {}) {
    val fa = RasvenAndroidFontFamilies.Persian
    val en = RasvenAndroidFontFamilies.English
    val now = remember(stage) { System.nanoTime() / 1_000_000L }
    val connected = now - 272_000L
    val incoming = RasvenIncomingCall(991L, "synthetic-call", "+98 912 123 4567", "Ali Rezaei", "204",
        1_792_000_000_000L, connected)
    val summary = RasvenCallEndSummary("Ali Rezaei", "+98 912 123 4567", connected, now, 1_792_000_000_000L)
    when {
        stage.startsWith("outgoing-") -> {
            val attempt = RasvenOutgoingCallAttempt(991L, "09121234567", RasvenOutgoingCallOrigin.HomeDirectDial)
            val state = when (stage) {
                "outgoing-microphone" -> RasvenOutgoingCallState.AwaitingMicrophonePermission(attempt.destination, attempt.origin)
                "outgoing-dialing" -> RasvenOutgoingCallState.Dialing(attempt)
                "outgoing-ringing" -> RasvenOutgoingCallState.Ringing(attempt)
                "outgoing-busy" -> RasvenOutgoingCallState.Busy(attempt)
                "outgoing-rejected" -> RasvenOutgoingCallState.Rejected(attempt)
                "outgoing-no-answer" -> RasvenOutgoingCallState.NoAnswer(attempt)
                "outgoing-network" -> RasvenOutgoingCallState.NetworkError(attempt)
                else -> error("Unknown outgoing fixture: $stage")
            }
            RasvenOutgoingCallScreen(state, fa, {}, {}, { onNavigate("home") },
                { onNavigate("outgoing-dialing") }, { onNavigate("home") })
        }
        stage.startsWith("incoming-") -> {
            val state = when (stage) {
                "incoming-open", "incoming-lock-presentation" -> RasvenIncomingCallState.Ringing(incoming)
                "incoming-microphone" -> RasvenIncomingCallState.AwaitingMicrophone(incoming)
                "incoming-answering" -> RasvenIncomingCallState.Answering(incoming)
                "incoming-active" -> RasvenIncomingCallState.Active(incoming)
                "incoming-missed" -> RasvenIncomingCallState.Missed(RasvenHistoryEntry("synthetic-missed",
                    RasvenHistoryCallClassification.Missed, incoming.name, incoming.number, incoming.arrivedEpochMillis))
                else -> error("Unknown incoming fixture: $stage")
            }
            RasvenIncomingCallScreen(state, stage == "incoming-lock-presentation", fa,
                onAnswer = { onNavigate("active-connected") }, onReject = { onNavigate("home") },
                onEnd = { onNavigate("call-summary") }, onRequestMicrophone = {},
                onClose = { onNavigate("home") }, onCallback = { onNavigate("outgoing-dialing") })
        }
        stage.startsWith("active-") -> {
            var controls by remember(stage) { mutableStateOf(RasvenActiveCallControls(991L,
                muted = stage == "active-muted", speaker = stage == "active-speaker", held = stage == "active-held",
                keypadVisible = stage == "active-keypad" || stage == "active-dtmf",
                lastDtmfAccepted = if (stage == "active-dtmf") '5' else null)) }
            RasvenActiveCallScreen("Ali Rezaei", connected, controls, fa,
                onMute = { controls = controls.copy(muted = it) },
                onSpeaker = { controls = controls.copy(speaker = it) },
                canHold = true, onHold = { controls = controls.copy(held = it) },
                onKeypad = { controls = controls.copy(keypadVisible = !controls.keypadVisible) },
                onDtmf = { controls = controls.copy(lastDtmfAccepted = it) },
                onEnd = { onNavigate("call-summary") }, onTransfer = { onNavigate("transfer-choose") },
                lineIdentity = "204")
        }
        stage == "call-summary" -> RasvenCallEndSummaryScreen(summary, fa, { onNavigate("home") })
        stage.startsWith("transfer-") -> {
            val call = RasvenTransferCallContext(991L, "Ali Rezaei", connected, outgoing = false)
            val destination = RasvenTransferDestination("پشتیبانی فنی", "305")
            var manual by remember(stage) { mutableStateOf(if (stage == "transfer-manual-error") "invalid" else "305") }
            val state = when (stage) {
                "transfer-choose" -> RasvenTransferUiState.ChoosingMode(call)
                "transfer-blind-select" -> RasvenTransferUiState.SelectingDestination(call, RasvenTransferMode.Blind)
                "transfer-attended-select" -> RasvenTransferUiState.SelectingDestination(call, RasvenTransferMode.Attended)
                "transfer-blind-manual", "transfer-manual-error" -> RasvenTransferUiState.EnteringDestination(
                    call, RasvenTransferMode.Blind, manual, invalid = stage == "transfer-manual-error")
                "transfer-attended-manual" -> RasvenTransferUiState.EnteringDestination(call, RasvenTransferMode.Attended, manual)
                "transfer-blind-waiting" -> RasvenTransferUiState.BlindAwaitingPbx(call, destination)
                "transfer-blind-failed" -> RasvenTransferUiState.BlindFailed(call, destination, true)
                "transfer-blind-ended-failed" -> RasvenTransferUiState.BlindFailed(call, destination, false)
                "transfer-blind-unknown" -> RasvenTransferUiState.BlindOutcomeUnknown(call, destination)
                "transfer-attended-waiting" -> RasvenTransferUiState.AttendedAwaitingConsultation(call, destination)
                "transfer-attended-ready" -> RasvenTransferUiState.AttendedReadyToComplete(call, destination)
                "transfer-confirm" -> RasvenTransferUiState.ConfirmingCompletion(call, destination)
                "transfer-completing" -> RasvenTransferUiState.CompletionArmed(call, destination)
                "transfer-returning" -> RasvenTransferUiState.AttendedCancelAwaitingRecovery(call, destination, true)
                "transfer-returned" -> RasvenTransferUiState.AttendedCancelledReturned(call, destination)
                "transfer-attended-unknown" -> RasvenTransferUiState.AttendedOutcomeUnknown(call, destination)
                "transfer-attended-busy" -> RasvenTransferUiState.AttendedFailed(call, destination, true, RasvenAttendedTransferFailure.DestinationBusy)
                "transfer-attended-no-answer" -> RasvenTransferUiState.AttendedFailed(call, destination, true, RasvenAttendedTransferFailure.NoAnswer)
                "transfer-manual-hold-error" -> RasvenTransferUiState.AttendedFailed(call, null, true, RasvenAttendedTransferFailure.ManualCallHold)
                "transfer-attended-ended" -> RasvenTransferUiState.AttendedFailed(call, destination, false, RasvenAttendedTransferFailure.CallEnded)
                else -> error("Unknown transfer fixture: $stage")
            }
            RasvenTransferScreen(state, fa, en, listOf(destination, RasvenTransferDestination("مدیریت", "101")),
                onChooseMode = { onNavigate(if (it == RasvenTransferMode.Blind) "transfer-blind-select" else "transfer-attended-select") },
                onEnterManual = { onNavigate(if ((state as? RasvenTransferUiState.SelectingDestination)?.mode == RasvenTransferMode.Attended)
                    "transfer-attended-manual" else "transfer-blind-manual") },
                onManualChanged = { manual = it }, onSubmitManual = {}, onSelectDestination = {},
                onConfirmConsultation = { onNavigate("transfer-attended-ready") },
                onRequestComplete = { onNavigate("transfer-confirm") }, onConfirmComplete = {},
                onCancelAttended = { onNavigate("transfer-returning") }, onReportBusy = {},
                onReportNoAnswer = {}, onReportBlindStillActive = {}, onReturnToCall = { onNavigate("active-connected") },
                onDismiss = { onNavigate("home") })
        }
        else -> error("Unknown offline call fixture: $stage")
    }
}
