package com.rasven.android.product.call

/** Minimal key/value boundary keeps preference migration deterministic and independently testable. */
internal interface RasvenTransferProfileKeyValueStore {
    fun get(key: String): String?
    fun contains(key: String): Boolean
    fun putAll(values: Map<String, String>): Boolean
}

internal class RasvenPersistentTransferProfileRepository(
    private val store: RasvenTransferProfileKeyValueStore,
) : RasvenPbxTransferProfileRepository {
    override fun load(): RasvenPbxTransferProfile {
        val defaults = RasvenPbxTransferProfile.issabelAsteriskDefaults
        val profile = RasvenPbxTransferProfile(
            identity = store.get(KEY_IDENTITY)?.let { runCatching { RasvenPbxProfileIdentity.valueOf(it) }.getOrNull() }
                ?: RasvenPbxProfileIdentity.IssabelAsterisk,
            blindCode = store.get(KEY_BLIND) ?: defaults.blindCode,
            attendedCode = store.get(KEY_ATTENDED) ?: defaults.attendedCode,
            cancelAttendedCode = store.get(KEY_CANCEL) ?: defaults.cancelAttendedCode,
            completionMode = store.get(KEY_COMPLETION)?.let { runCatching { RasvenAttendedCompletionMode.valueOf(it) }.getOrNull() }
                ?: defaults.completionMode,
            completeAttendedCode = store.get(KEY_COMPLETE) ?: defaults.completeAttendedCode,
            salesDestination = store.get(KEY_SALES) ?: "",
            managementDestination = store.get(KEY_MANAGEMENT) ?: "",
        )
        // Upgrade only the former Yeastar site preset. Custom and Issabel profiles must
        // never be rewritten merely because another PBX uses different feature codes.
        val migrated = if (profile.identity == RasvenPbxProfileIdentity.Yeastar &&
            profile.blindCode == "##" && profile.attendedCode == "*2" &&
            profile.cancelAttendedCode == "*1" && profile.completionMode == RasvenAttendedCompletionMode.Hangup &&
            profile.completeAttendedCode.isEmpty()
        ) profile.copy(attendedCode = "*3", cancelAttendedCode = "") else null
        if (migrated != null && save(migrated)) return migrated
        if (KEYS.any { !store.contains(it) }) save(profile)
        return profile
    }

    override fun save(profile: RasvenPbxTransferProfile): Boolean {
        if (!profile.isValid()) return false
        val defaults = RasvenPbxTransferProfile.issabelAsteriskDefaults
        val identity = when (profile.identity) {
            RasvenPbxProfileIdentity.Yeastar, RasvenPbxProfileIdentity.Grandstream -> profile.identity
            RasvenPbxProfileIdentity.IssabelAsterisk -> if (profile.copy(identity = defaults.identity) == defaults) defaults.identity else RasvenPbxProfileIdentity.Custom
            RasvenPbxProfileIdentity.Custom -> RasvenPbxProfileIdentity.Custom
        }
        return store.putAll(
            mapOf(
                KEY_IDENTITY to identity.name,
                KEY_BLIND to profile.blindCode,
                KEY_ATTENDED to profile.attendedCode,
                KEY_CANCEL to profile.cancelAttendedCode,
                KEY_COMPLETION to profile.completionMode.name,
                KEY_COMPLETE to profile.completeAttendedCode,
                KEY_SALES to profile.salesDestination,
                KEY_MANAGEMENT to profile.managementDestination,
            ),
        )
    }

    override fun resetToDefaults(): RasvenPbxTransferProfile = RasvenPbxTransferProfile.issabelAsteriskDefaults.also(::save)

    private companion object {
        const val KEY_IDENTITY = "profile_identity"
        const val KEY_BLIND = "blind_code"
        const val KEY_ATTENDED = "attended_code"
        const val KEY_CANCEL = "cancel_attended_code"
        const val KEY_COMPLETION = "completion_mode"
        const val KEY_COMPLETE = "complete_attended_code"
        const val KEY_SALES = "sales_destination"
        const val KEY_MANAGEMENT = "management_destination"
        val KEYS = setOf(KEY_IDENTITY, KEY_BLIND, KEY_ATTENDED, KEY_CANCEL, KEY_COMPLETION, KEY_COMPLETE, KEY_SALES, KEY_MANAGEMENT)
    }
}
