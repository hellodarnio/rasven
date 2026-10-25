package com.rasven.android.product.call

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
/** The only transfer DTMF port. It delegates each digit to the existing live IAX session sender. */
internal fun interface RasvenTransferDtmfSender {
    fun send(sessionId: Long, digit: Char): Boolean
}

internal enum class RasvenTransferMode { Blind, Attended }
internal enum class RasvenAttendedTransferFailure { Unconfirmed, DestinationBusy, NoAnswer, Delivery, ManualCallHold, CallEnded }

internal sealed interface RasvenTransferCommandResult {
    data object DeliveredLocally : RasvenTransferCommandResult
    data object HangupRequired : RasvenTransferCommandResult
    data object CancellationUnavailable : RasvenTransferCommandResult
    data class PartiallyDelivered(val acceptedDigits: Int, val totalDigits: Int) : RasvenTransferCommandResult
    data object InvalidProfile : RasvenTransferCommandResult
}

/** Portable feature-code policy; no PBX-specific constants are embedded in CallEngine or UI. */
internal class RasvenFeatureCodeTransferStrategy(
    private val profileProvider: () -> RasvenPbxTransferProfile,
    private val sender: RasvenTransferDtmfSender,
) {
    fun startBlindTransfer(sessionId: Long, destination: RasvenTransferDestination): RasvenTransferCommandResult {
        val profile = profileProvider()
        if (!profile.isValid()) return RasvenTransferCommandResult.InvalidProfile
        return sendOrdered(sessionId, profile.blindCode, destination.address)
    }

    fun startAttendedTransfer(sessionId: Long): RasvenTransferCommandResult {
        val profile = profileProvider()
        if (!profile.isValid()) return RasvenTransferCommandResult.InvalidProfile
        return sendOrdered(sessionId, profile.attendedCode)
    }

    fun sendAttendedDestination(sessionId: Long, destination: RasvenTransferDestination): RasvenTransferCommandResult {
        val profile = profileProvider()
        if (!profile.isValid()) return RasvenTransferCommandResult.InvalidProfile
        return sendOrdered(sessionId, destination.address)
    }

    fun cancelAttendedTransfer(sessionId: Long): RasvenTransferCommandResult {
        val profile = profileProvider()
        if (!profile.isValid()) return RasvenTransferCommandResult.InvalidProfile
        if (profile.cancelAttendedCode.isEmpty()) return RasvenTransferCommandResult.CancellationUnavailable
        return sendOrdered(sessionId, profile.cancelAttendedCode)
    }

    fun completeAttendedTransfer(sessionId: Long): RasvenTransferCommandResult {
        val profile = profileProvider()
        if (!profile.isValid()) return RasvenTransferCommandResult.InvalidProfile
        return when (profile.completionMode) {
            RasvenAttendedCompletionMode.Hangup -> RasvenTransferCommandResult.HangupRequired
            RasvenAttendedCompletionMode.FeatureCode -> sendOrdered(sessionId, profile.completeAttendedCode)
        }
    }

    private fun sendOrdered(sessionId: Long, vararg sequences: String): RasvenTransferCommandResult {
        var accepted = 0
        val total = sequences.sumOf(String::length)
        for (sequence in sequences) for (digit in sequence) {
            if (!sender.send(sessionId, digit)) return RasvenTransferCommandResult.PartiallyDelivered(accepted, total)
            accepted += 1
        }
        return RasvenTransferCommandResult.DeliveredLocally
    }
}

internal data class RasvenTransferCallContext(
    val sessionId: Long,
    val displayIdentity: String,
    val connectedAtElapsedMillis: Long,
    val outgoing: Boolean,
    val manuallyHeld: Boolean = false,
) {
    init { require(sessionId > 0); require(displayIdentity.isNotBlank()) }
}

internal sealed interface RasvenTransferUiState {
    data object Idle : RasvenTransferUiState
    data class ChoosingMode(val call: RasvenTransferCallContext) : RasvenTransferUiState
    data class SelectingDestination(
        val call: RasvenTransferCallContext,
        val mode: RasvenTransferMode,
        val value: String = "",
        val invalid: Boolean = false,
    ) : RasvenTransferUiState
    data class EnteringDestination(val call: RasvenTransferCallContext, val mode: RasvenTransferMode, val value: String = "", val invalid: Boolean = false) : RasvenTransferUiState
    data class BlindAwaitingPbx(val call: RasvenTransferCallContext, val destination: RasvenTransferDestination) : RasvenTransferUiState
    data class BlindFailed(val call: RasvenTransferCallContext, val destination: RasvenTransferDestination?, val canReturnToCall: Boolean) : RasvenTransferUiState
    data class BlindOutcomeUnknown(val call: RasvenTransferCallContext, val destination: RasvenTransferDestination) : RasvenTransferUiState
    data class AttendedAwaitingDestinationTone(
        val call: RasvenTransferCallContext,
        val destination: RasvenTransferDestination,
        val observedPeerHold: Boolean = false,
    ) : RasvenTransferUiState
    data class AttendedAwaitingConsultation(
        val call: RasvenTransferCallContext,
        val destination: RasvenTransferDestination,
        val observedPeerHold: Boolean = false,
    ) : RasvenTransferUiState
    data class AttendedReadyToComplete(val call: RasvenTransferCallContext, val destination: RasvenTransferDestination) : RasvenTransferUiState
    data class ConfirmingCompletion(val call: RasvenTransferCallContext, val destination: RasvenTransferDestination) : RasvenTransferUiState
    data class CompletionArmed(val call: RasvenTransferCallContext, val destination: RasvenTransferDestination) : RasvenTransferUiState
    data class AttendedCancelAwaitingRecovery(val call: RasvenTransferCallContext, val destination: RasvenTransferDestination, val observedPeerHold: Boolean) : RasvenTransferUiState
    data class AttendedCancelledReturned(val call: RasvenTransferCallContext, val destination: RasvenTransferDestination) : RasvenTransferUiState
    data class AttendedOutcomeUnknown(val call: RasvenTransferCallContext, val destination: RasvenTransferDestination) : RasvenTransferUiState
    data class AttendedFailed(
        val call: RasvenTransferCallContext,
        val destination: RasvenTransferDestination?,
        val canReturnToCall: Boolean,
        val reason: RasvenAttendedTransferFailure = RasvenAttendedTransferFailure.Unconfirmed,
        val observedPeerHold: Boolean = false,
    ) : RasvenTransferUiState
}

/** Stateful Product flow. A local DTMF write never becomes a success state. */
internal class RasvenTransferViewModel(
) : ViewModel() {
    var state by mutableStateOf<RasvenTransferUiState>(RasvenTransferUiState.Idle)
        private set
    var minimized by mutableStateOf(false)
        private set

    fun begin(call: RasvenTransferCallContext): Boolean {
        if (state !is RasvenTransferUiState.Idle) return false
        minimized = false
        state = RasvenTransferUiState.ChoosingMode(call)
        return true
    }

    fun chooseMode(mode: RasvenTransferMode): Boolean {
        val call = (state as? RasvenTransferUiState.ChoosingMode)?.call ?: return false
        if (mode == RasvenTransferMode.Attended && call.manuallyHeld) {
            state = RasvenTransferUiState.AttendedFailed(call, null, canReturnToCall = true, reason = RasvenAttendedTransferFailure.ManualCallHold)
            return true
        }
        state = RasvenTransferUiState.SelectingDestination(call, mode)
        return true
    }

    fun enterManualDestination(): Boolean {
        val selection = state as? RasvenTransferUiState.SelectingDestination ?: return false
        state = RasvenTransferUiState.EnteringDestination(selection.call, selection.mode)
        return true
    }

    fun backToModeSelection(): Boolean {
        val selection = state as? RasvenTransferUiState.SelectingDestination ?: return false
        state = RasvenTransferUiState.ChoosingMode(selection.call)
        return true
    }

    fun editManualDestination(value: String): Boolean {
        return when (val current = state) {
            is RasvenTransferUiState.EnteringDestination -> {
                state = current.copy(value = value, invalid = false)
                true
            }
            is RasvenTransferUiState.SelectingDestination -> {
                state = current.copy(value = value, invalid = false)
                true
            }
            else -> false
        }
    }

    fun submitManualDestination(sender: RasvenTransferDtmfSender): Boolean {
        val entry = when (val current = state) {
            is RasvenTransferUiState.EnteringDestination -> current
            is RasvenTransferUiState.SelectingDestination -> RasvenTransferUiState.EnteringDestination(
                current.call, current.mode, current.value, current.invalid,
            )
            else -> return false
        }
        if (entry.value.isEmpty() || RasvenPbxTransferProfile.destinationError(entry.value) != null) {
            state = entry.copy(invalid = true)
            return false
        }
        return execute(entry.call, entry.mode, RasvenTransferDestination(entry.value, entry.value), sender)
    }

    fun configuredDestinations(): List<RasvenTransferDestination> {
        val profile = RasvenPbxTransferProfileSource.current()
        return buildList {
            profile.salesDestination.takeIf(String::isNotEmpty)?.let { add(RasvenTransferDestination("پشتیبانی فنی", it)) }
            profile.managementDestination.takeIf(String::isNotEmpty)?.let { add(RasvenTransferDestination("مدیریت", it)) }
        }
    }

    fun selectConfiguredDestination(label: String, sender: RasvenTransferDtmfSender): Boolean {
        val selection = state as? RasvenTransferUiState.SelectingDestination ?: return false
        val dest = configuredDestinations().firstOrNull { it.label == label } ?: return false
        return execute(selection.call, selection.mode, dest, sender)
    }

    fun startWithDestination(destination: RasvenTransferDestination, sender: RasvenTransferDtmfSender): Boolean {
        val selection = state as? RasvenTransferUiState.SelectingDestination ?: return false
        if (RasvenPbxTransferProfile.destinationError(destination.address) != null) return false
        return execute(selection.call, selection.mode, destination, sender)
    }

    private fun execute(
        call: RasvenTransferCallContext,
        mode: RasvenTransferMode,
        destination: RasvenTransferDestination,
        sender: RasvenTransferDtmfSender,
    ): Boolean {
        // Set a non-entry state before touching transport so rapid repeated clicks cannot replay codes.
        state = when (mode) {
            RasvenTransferMode.Blind -> RasvenTransferUiState.BlindFailed(call, destination, canReturnToCall = true)
            RasvenTransferMode.Attended -> RasvenTransferUiState.AttendedFailed(call, destination, canReturnToCall = true)
        }
        val strategy = RasvenFeatureCodeTransferStrategy(RasvenPbxTransferProfileSource::current, sender)
        if (mode == RasvenTransferMode.Attended) {
            val result = strategy.startAttendedTransfer(call.sessionId)
            state = if (result == RasvenTransferCommandResult.DeliveredLocally)
                RasvenTransferUiState.AttendedAwaitingDestinationTone(call, destination)
            else RasvenTransferUiState.AttendedFailed(call, destination, canReturnToCall = true)
            return result == RasvenTransferCommandResult.DeliveredLocally
        }
        val result = strategy.startBlindTransfer(call.sessionId, destination)
        state = when (mode) {
            RasvenTransferMode.Blind -> if (result == RasvenTransferCommandResult.DeliveredLocally)
                RasvenTransferUiState.BlindAwaitingPbx(call, destination)
            else RasvenTransferUiState.BlindFailed(call, destination, canReturnToCall = true)
            RasvenTransferMode.Attended -> error("Attended transfer is handled as a two-stage dial flow")
        }
        return result == RasvenTransferCommandResult.DeliveredLocally
    }

    /** Called only after the PBX dial tone is heard following the attended feature code. */
    fun continueAttendedDestination(sender: RasvenTransferDtmfSender): Boolean {
        val current = state as? RasvenTransferUiState.AttendedAwaitingDestinationTone ?: return false
        // Consume this state before sending so a rapid second tap cannot redial the destination.
        state = RasvenTransferUiState.AttendedAwaitingConsultation(
            current.call, current.destination, observedPeerHold = current.observedPeerHold,
        )
        val result = RasvenFeatureCodeTransferStrategy(RasvenPbxTransferProfileSource::current, sender)
            .sendAttendedDestination(current.call.sessionId, current.destination)
        if (result != RasvenTransferCommandResult.DeliveredLocally) {
            state = RasvenTransferUiState.AttendedFailed(
                call = current.call,
                destination = current.destination,
                canReturnToCall = !current.observedPeerHold,
                reason = RasvenAttendedTransferFailure.Delivery,
                observedPeerHold = current.observedPeerHold,
            )
            return false
        }
        return true
    }

    fun confirmConsultationConnected(): Boolean {
        val current = state as? RasvenTransferUiState.AttendedAwaitingConsultation ?: return false
        state = RasvenTransferUiState.AttendedReadyToComplete(current.call, current.destination)
        return true
    }

    fun requestAttendedCompletion(): Boolean {
        val current = state as? RasvenTransferUiState.AttendedReadyToComplete ?: return false
        state = RasvenTransferUiState.ConfirmingCompletion(current.call, current.destination)
        return true
    }

    fun confirmAttendedCompletion(sender: RasvenTransferDtmfSender): RasvenTransferCommandResult? {
        val current = state as? RasvenTransferUiState.ConfirmingCompletion ?: return null
        val result = RasvenFeatureCodeTransferStrategy(RasvenPbxTransferProfileSource::current, sender).completeAttendedTransfer(current.call.sessionId)
        state = when (result) {
            RasvenTransferCommandResult.HangupRequired -> RasvenTransferUiState.CompletionArmed(current.call, current.destination)
            RasvenTransferCommandResult.DeliveredLocally -> RasvenTransferUiState.CompletionArmed(current.call, current.destination)
            else -> RasvenTransferUiState.AttendedFailed(current.call, current.destination, canReturnToCall = true)
        }
        return result
    }

    fun cancelAttendedTransfer(sender: RasvenTransferDtmfSender): Boolean {
        val (call, destination, observedPeerHold) = when (val value = state) {
            is RasvenTransferUiState.AttendedAwaitingDestinationTone -> Triple(value.call, value.destination, value.observedPeerHold)
            is RasvenTransferUiState.AttendedAwaitingConsultation -> Triple(value.call, value.destination, value.observedPeerHold)
            is RasvenTransferUiState.AttendedReadyToComplete -> Triple(value.call, value.destination, false)
            is RasvenTransferUiState.ConfirmingCompletion -> Triple(value.call, value.destination, false)
            is RasvenTransferUiState.AttendedFailed -> {
                if (value.reason != RasvenAttendedTransferFailure.DestinationBusy && value.reason != RasvenAttendedTransferFailure.NoAnswer) return false
                Triple(value.call, value.destination ?: return false, value.observedPeerHold)
            }
            else -> return false
        }
        val result = RasvenFeatureCodeTransferStrategy(RasvenPbxTransferProfileSource::current, sender).cancelAttendedTransfer(call.sessionId)
        if (result == RasvenTransferCommandResult.CancellationUnavailable) return false
        if (result != RasvenTransferCommandResult.DeliveredLocally) {
            state = RasvenTransferUiState.AttendedFailed(call, destination, canReturnToCall = true)
            return false
        }
        state = RasvenTransferUiState.AttendedCancelAwaitingRecovery(call, destination, observedPeerHold)
        return true
    }

    fun confirmHangupAccepted(sessionId: Long, accepted: Boolean): Boolean {
        val armed = state as? RasvenTransferUiState.CompletionArmed ?: return false
        if (armed.call.sessionId != sessionId) return false
        if (!accepted) state = RasvenTransferUiState.AttendedFailed(armed.call, armed.destination, canReturnToCall = true)
        return accepted
    }

    fun observeCallEnded(summary: RasvenCallEndSummary) {
        val owner = activeCallContext() ?: return
        if (summary.callSessionId != null && summary.callSessionId != owner.sessionId) return
        when (val current = state) {
            is RasvenTransferUiState.BlindAwaitingPbx -> state =
                RasvenTransferUiState.BlindOutcomeUnknown(current.call, current.destination)
            is RasvenTransferUiState.CompletionArmed -> state =
                RasvenTransferUiState.AttendedOutcomeUnknown(current.call, current.destination)
            is RasvenTransferUiState.AttendedAwaitingDestinationTone,
            is RasvenTransferUiState.AttendedAwaitingConsultation,
            is RasvenTransferUiState.AttendedReadyToComplete,
            is RasvenTransferUiState.ConfirmingCompletion,
            is RasvenTransferUiState.AttendedCancelAwaitingRecovery -> state = RasvenTransferUiState.AttendedFailed(
                call = when (current) {
                    is RasvenTransferUiState.AttendedAwaitingDestinationTone -> current.call
                    is RasvenTransferUiState.AttendedAwaitingConsultation -> current.call
                    is RasvenTransferUiState.AttendedReadyToComplete -> current.call
                    is RasvenTransferUiState.ConfirmingCompletion -> current.call
                    is RasvenTransferUiState.AttendedCancelAwaitingRecovery -> current.call
                },
                destination = when (current) {
                    is RasvenTransferUiState.AttendedAwaitingDestinationTone -> current.destination
                    is RasvenTransferUiState.AttendedAwaitingConsultation -> current.destination
                    is RasvenTransferUiState.AttendedReadyToComplete -> current.destination
                    is RasvenTransferUiState.ConfirmingCompletion -> current.destination
                    is RasvenTransferUiState.AttendedCancelAwaitingRecovery -> current.destination
                },
                canReturnToCall = false,
                reason = RasvenAttendedTransferFailure.CallEnded,
            )
            else -> Unit
        }
        if (state is RasvenTransferUiState.BlindOutcomeUnknown || state is RasvenTransferUiState.BlindFailed ||
            state is RasvenTransferUiState.AttendedOutcomeUnknown || state is RasvenTransferUiState.AttendedFailed) minimized = false
    }

    fun observePeerHeld(sessionId: Long, held: Boolean) {
        when (val current = state) {
            is RasvenTransferUiState.AttendedAwaitingDestinationTone -> if (current.call.sessionId == sessionId && held) {
                state = current.copy(observedPeerHold = true)
            }
            is RasvenTransferUiState.AttendedAwaitingConsultation -> if (current.call.sessionId == sessionId && held) {
                state = current.copy(observedPeerHold = true)
            }
            is RasvenTransferUiState.AttendedCancelAwaitingRecovery -> {
                if (current.call.sessionId != sessionId) return
                if (held) state = current.copy(observedPeerHold = true)
                else if (current.observedPeerHold) state = RasvenTransferUiState.AttendedCancelledReturned(current.call, current.destination)
            }
            else -> Unit
        }
    }

    fun reportAttendedDestinationBusy(sessionId: Long): Boolean =
        reportAttendedFailure(sessionId, RasvenAttendedTransferFailure.DestinationBusy)

    fun reportAttendedNoAnswer(sessionId: Long): Boolean =
        reportAttendedFailure(sessionId, RasvenAttendedTransferFailure.NoAnswer)

    fun reportBlindOriginalStillActive(sessionId: Long): Boolean {
        val current = state as? RasvenTransferUiState.BlindAwaitingPbx ?: return false
        if (current.call.sessionId != sessionId) return false
        state = RasvenTransferUiState.BlindFailed(current.call, current.destination, canReturnToCall = true)
        return true
    }

    private fun reportAttendedFailure(sessionId: Long, reason: RasvenAttendedTransferFailure): Boolean {
        val current = state as? RasvenTransferUiState.AttendedAwaitingConsultation ?: return false
        if (current.call.sessionId != sessionId) return false
        state = RasvenTransferUiState.AttendedFailed(current.call, current.destination, canReturnToCall = !current.observedPeerHold,
            reason = reason, observedPeerHold = current.observedPeerHold)
        return true
    }

    fun dismissResult(): Boolean {
        if (state !is RasvenTransferUiState.BlindOutcomeUnknown && state !is RasvenTransferUiState.BlindFailed &&
            state !is RasvenTransferUiState.AttendedCancelledReturned &&
            state !is RasvenTransferUiState.AttendedOutcomeUnknown && state !is RasvenTransferUiState.AttendedFailed) return false
        state = RasvenTransferUiState.Idle
        minimized = false
        return true
    }

    fun returnToActiveCall(): Boolean {
        if ((state as? RasvenTransferUiState.AttendedFailed)?.canReturnToCall == false) return false
        if (state !is RasvenTransferUiState.ChoosingMode && state !is RasvenTransferUiState.SelectingDestination &&
            state !is RasvenTransferUiState.EnteringDestination && state !is RasvenTransferUiState.BlindFailed &&
            state !is RasvenTransferUiState.AttendedFailed && state !is RasvenTransferUiState.AttendedCancelledReturned) return false
        state = RasvenTransferUiState.Idle
        minimized = false
        return true
    }

    fun minimizeToActiveCall(): Boolean {
        val failed = state as? RasvenTransferUiState.AttendedFailed
        if (state is RasvenTransferUiState.Idle || state is RasvenTransferUiState.BlindOutcomeUnknown ||
            state is RasvenTransferUiState.AttendedOutcomeUnknown ||
            (failed != null && (failed.canReturnToCall || failed.reason == RasvenAttendedTransferFailure.CallEnded)) ||
            state is RasvenTransferUiState.BlindFailed) return false
        minimized = true
        return true
    }

    fun backFromAttendedCompletion(): Boolean {
        val current = state as? RasvenTransferUiState.ConfirmingCompletion ?: return false
        state = RasvenTransferUiState.AttendedReadyToComplete(current.call, current.destination)
        return true
    }

    fun reopen() { if (state !is RasvenTransferUiState.Idle) minimized = false }

    fun activeCallContext(): RasvenTransferCallContext? = when (val current = state) {
        is RasvenTransferUiState.ChoosingMode -> current.call
        is RasvenTransferUiState.SelectingDestination -> current.call
        is RasvenTransferUiState.EnteringDestination -> current.call
        is RasvenTransferUiState.BlindAwaitingPbx -> current.call
        is RasvenTransferUiState.BlindFailed -> current.call
        is RasvenTransferUiState.BlindOutcomeUnknown -> current.call
        is RasvenTransferUiState.AttendedAwaitingDestinationTone -> current.call
        is RasvenTransferUiState.AttendedAwaitingConsultation -> current.call
        is RasvenTransferUiState.AttendedReadyToComplete -> current.call
        is RasvenTransferUiState.ConfirmingCompletion -> current.call
        is RasvenTransferUiState.CompletionArmed -> current.call
        is RasvenTransferUiState.AttendedCancelAwaitingRecovery -> current.call
        is RasvenTransferUiState.AttendedCancelledReturned -> current.call
        is RasvenTransferUiState.AttendedOutcomeUnknown -> current.call
        is RasvenTransferUiState.AttendedFailed -> current.call
        RasvenTransferUiState.Idle -> null
    }
}
