package com.rasven.android.product.call

/** Snapshot of the real leg at termination; durations use monotonic time, never wall-clock subtraction. */
internal data class RasvenCallEndSummary(
    val displayIdentity: String,
    val callableNumber: String,
    val connectedAtElapsedMillis: Long,
    val endedAtElapsedMillis: Long,
    val endedAtEpochMillis: Long,
    val remoteTermination: Boolean = false,
    val callSessionId: Long? = null,
) {
    init {
        require(displayIdentity.isNotBlank())
        require(callableNumber.isNotBlank())
        require(endedAtElapsedMillis >= connectedAtElapsedMillis)
        require(endedAtEpochMillis >= 0)
    }

    val durationSeconds: Long
        get() = (endedAtElapsedMillis - connectedAtElapsedMillis) / 1_000L
}

internal fun createCallEndSummary(
    displayIdentity: String?,
    callableNumber: String,
    connectedAtElapsedMillis: Long?,
    endedAtElapsedMillis: Long,
    endedAtEpochMillis: Long,
    remoteTermination: Boolean = false,
    callSessionId: Long? = null,
): RasvenCallEndSummary {
    val connectedAt = (connectedAtElapsedMillis ?: endedAtElapsedMillis).coerceAtMost(endedAtElapsedMillis)
    return RasvenCallEndSummary(
        displayIdentity = displayIdentity?.takeIf(String::isNotBlank) ?: callableNumber,
        callableNumber = callableNumber,
        connectedAtElapsedMillis = connectedAt,
        endedAtElapsedMillis = endedAtElapsedMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        remoteTermination = remoteTermination,
        callSessionId = callSessionId,
    )
}
