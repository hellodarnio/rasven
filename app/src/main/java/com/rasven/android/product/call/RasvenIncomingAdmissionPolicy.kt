package com.rasven.android.product.call

/** The first decision for an incoming session wins across a DND toggle or duplicate event. */
internal class RasvenIncomingAdmissionPolicy(initialDoNotDisturb: Boolean = false) {
    internal enum class Outcome { Present, Suppress, Duplicate }

    private var doNotDisturb = initialDoNotDisturb
    private val decided = LinkedHashMap<Long, Outcome>()

    @Synchronized fun setDoNotDisturb(enabled: Boolean) {
        doNotDisturb = enabled
    }

    @Synchronized fun isDoNotDisturbEnabled(): Boolean = doNotDisturb

    @Synchronized fun decide(sessionId: Long): Outcome {
        if (decided.containsKey(sessionId)) return Outcome.Duplicate
        val outcome = if (doNotDisturb) Outcome.Suppress else Outcome.Present
        decided[sessionId] = outcome
        if (decided.size > 128) decided.remove(decided.keys.first())
        return outcome
    }
}
