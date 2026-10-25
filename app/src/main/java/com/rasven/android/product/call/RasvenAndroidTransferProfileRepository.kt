package com.rasven.android.product.call

import android.content.Context

/** SharedPreferences-backed transfer-only settings; account credentials stay in Registration owner. */
internal class RasvenAndroidTransferProfileRepository(context: Context) : RasvenPbxTransferProfileRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val delegate = RasvenPersistentTransferProfileRepository(object : RasvenTransferProfileKeyValueStore {
        override fun get(key: String): String? = preferences.getString(key, null)
        override fun contains(key: String): Boolean = preferences.contains(key)
        override fun putAll(values: Map<String, String>): Boolean = preferences.edit().also { editor ->
            values.forEach { (key, value) -> editor.putString(key, value) }
        }.commit()
    })

    override fun load(): RasvenPbxTransferProfile = delegate.load()

    override fun save(profile: RasvenPbxTransferProfile): Boolean = delegate.save(profile)

    override fun resetToDefaults(): RasvenPbxTransferProfile = delegate.resetToDefaults()

    private companion object {
        const val PREFERENCES = "rasven_pbx_transfer_profile_v1"
    }
}
