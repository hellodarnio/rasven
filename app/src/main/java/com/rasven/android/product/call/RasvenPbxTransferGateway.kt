package com.rasven.android.product.call

/** Product-facing transfer requests remain independent of any one PBX wire protocol. */
internal enum class RasvenTransferCapability { Blind, Attended }

/** A destination's visible label is deliberately separate from its exact dial-plan value. */
internal data class RasvenTransferDestination(
    val label: String,
    val address: String,
) {
    init {
        require(label.isNotBlank())
        require(address.isNotBlank())
        require(address == address.trim())
    }
}

internal enum class RasvenCallLegRole { Original, Consultation }

/** Session and Telecom identities are kept distinct for a true two-leg attended call. */
internal data class RasvenTransferCallLeg(
    val role: RasvenCallLegRole,
    val iaxSessionId: Long,
    val telecomCallId: String,
    val historyId: String,
) {
    init {
        require(iaxSessionId > 0)
        require(telecomCallId.isNotBlank())
        require(historyId.isNotBlank())
    }
}

internal data class RasvenAttendedTransferLegs(
    val original: RasvenTransferCallLeg,
    val consultation: RasvenTransferCallLeg,
) {
    init {
        require(original.role == RasvenCallLegRole.Original)
        require(consultation.role == RasvenCallLegRole.Consultation)
        require(original.iaxSessionId != consultation.iaxSessionId)
        require(original.telecomCallId != consultation.telecomCallId)
    }
}

internal sealed interface RasvenPbxTransferResult {
    data class Started(val operationId: String) : RasvenPbxTransferResult
    data class Completed(val operationId: String) : RasvenPbxTransferResult
    data class Failed(val reason: Failure) : RasvenPbxTransferResult

    enum class Failure { Unsupported, InvalidState, Rejected, TimedOut, Network, Cancelled }
}

/**
 * Boundary for an explicitly configured PBX transfer contract. Implementations own protocol state;
 * Product state must never interpret a button press or a bare hangup as successful transfer.
 */
internal interface RasvenPbxTransferGateway {
    fun supports(capability: RasvenTransferCapability): Boolean
    fun blindTransfer(original: RasvenTransferCallLeg, destination: RasvenTransferDestination): RasvenPbxTransferResult
    fun startConsultation(original: RasvenTransferCallLeg, destination: RasvenTransferDestination): RasvenPbxTransferResult
    fun completeAttendedTransfer(legs: RasvenAttendedTransferLegs): RasvenPbxTransferResult
    fun cancelAttendedTransfer(legs: RasvenAttendedTransferLegs): RasvenPbxTransferResult
}

/** Safe production default until Amir supplies the PBX vendor/version and transfer contract. */
internal object RasvenUnsupportedPbxTransferGateway : RasvenPbxTransferGateway {
    override fun supports(capability: RasvenTransferCapability): Boolean = false

    override fun blindTransfer(
        original: RasvenTransferCallLeg,
        destination: RasvenTransferDestination,
    ) = RasvenPbxTransferResult.Failed(RasvenPbxTransferResult.Failure.Unsupported)

    override fun startConsultation(
        original: RasvenTransferCallLeg,
        destination: RasvenTransferDestination,
    ) = RasvenPbxTransferResult.Failed(RasvenPbxTransferResult.Failure.Unsupported)

    override fun completeAttendedTransfer(legs: RasvenAttendedTransferLegs) =
        RasvenPbxTransferResult.Failed(RasvenPbxTransferResult.Failure.Unsupported)

    override fun cancelAttendedTransfer(legs: RasvenAttendedTransferLegs) =
        RasvenPbxTransferResult.Failed(RasvenPbxTransferResult.Failure.Unsupported)
}
