package com.rasven.android.product.call

/** Profile identity is metadata; execution is deliberately independent of a specific PBX API. */
internal enum class RasvenPbxProfileIdentity { IssabelAsterisk, Yeastar, Grandstream, Custom }

internal enum class RasvenAttendedCompletionMode { Hangup, FeatureCode }

internal data class RasvenPbxTransferProfile(
    val identity: RasvenPbxProfileIdentity,
    val blindCode: String,
    val attendedCode: String,
    val cancelAttendedCode: String,
    val completionMode: RasvenAttendedCompletionMode,
    val completeAttendedCode: String = "",
    val salesDestination: String = "",
    val managementDestination: String = "",
) {
    fun validate(): RasvenPbxTransferProfileErrors = RasvenPbxTransferProfileErrors(
        blindCode = featureCodeError(blindCode),
        attendedCode = featureCodeError(attendedCode),
        // Some PBXs expose attended transfer but no in-call cancellation feature code.
        cancelAttendedCode = cancelAttendedCode.takeIf(String::isNotEmpty)?.let(::featureCodeError),
        completeAttendedCode = if (completionMode == RasvenAttendedCompletionMode.FeatureCode) featureCodeError(completeAttendedCode) else null,
        salesDestination = destinationError(salesDestination),
        managementDestination = destinationError(managementDestination),
    )

    fun isValid(): Boolean = validate().isEmpty

    fun withCode(): RasvenPbxTransferProfile {
        val map = copy(salesDestination = "", managementDestination = "")
        val identity = when (map) {
            issabelAsteriskDefaults -> RasvenPbxProfileIdentity.IssabelAsterisk
            yeastarS50Defaults -> RasvenPbxProfileIdentity.Yeastar
            else -> RasvenPbxProfileIdentity.Custom
        }
        return copy(identity = identity)
    }

    /** Selects a known feature-code map without discarding administrator-defined destinations. */
    fun withPreset(preset: RasvenPbxTransferProfile): RasvenPbxTransferProfile = preset.copy(
        salesDestination = salesDestination,
        managementDestination = managementDestination,
    )

    companion object {
        /** The single canonical Issabel/Asterisk-compatible V1 factory profile. */
        val issabelAsteriskDefaults = RasvenPbxTransferProfile(
            identity = RasvenPbxProfileIdentity.IssabelAsterisk,
            blindCode = "*2",
            attendedCode = "##",
            cancelAttendedCode = "*1",
            completionMode = RasvenAttendedCompletionMode.Hangup,
        )

        /** This deployment's administrator-confirmed transfer map; not Yeastar factory defaults. */
        val yeastarS50Defaults = RasvenPbxTransferProfile(
            identity = RasvenPbxProfileIdentity.Yeastar,
            blindCode = "##",
            attendedCode = "*3",
            cancelAttendedCode = "",
            completionMode = RasvenAttendedCompletionMode.Hangup,
        )

        fun featureCodeError(value: String): String? = when {
            value.isBlank() -> "required"
            value != value.trim() -> "unsafe"
            value.length > 24 -> "too_long"
            value.any { it !in "0123456789*#" } -> "unsafe"
            else -> null
        }

        fun destinationError(value: String): String? = when {
            value.isEmpty() -> null
            value != value.trim() -> "unsafe"
            value.length > 64 -> "too_long"
            value.any { it !in "0123456789*#" } -> "unsafe"
            else -> null
        }
    }
}

internal data class RasvenPbxTransferProfileErrors(
    val blindCode: String? = null,
    val attendedCode: String? = null,
    val cancelAttendedCode: String? = null,
    val completeAttendedCode: String? = null,
    val salesDestination: String? = null,
    val managementDestination: String? = null,
) {
    val isEmpty: Boolean get() = listOf(blindCode, attendedCode, cancelAttendedCode, completeAttendedCode, salesDestination, managementDestination).all { it == null }
}

internal fun interface RasvenPbxTransferProfileRepository {
    fun load(): RasvenPbxTransferProfile
    fun save(profile: RasvenPbxTransferProfile): Boolean = false
    fun resetToDefaults(): RasvenPbxTransferProfile = RasvenPbxTransferProfile.issabelAsteriskDefaults
}

/** Process-scoped profile source used at action time so saved admin edits apply without restart. */
internal object RasvenPbxTransferProfileSource {
    @Volatile var repository: RasvenPbxTransferProfileRepository? = null
    fun current(): RasvenPbxTransferProfile = repository?.load() ?: RasvenPbxTransferProfile.issabelAsteriskDefaults
}
