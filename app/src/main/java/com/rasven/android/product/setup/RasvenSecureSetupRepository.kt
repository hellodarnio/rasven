package com.rasven.android.product.setup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class RasvenSavedSetup(val account: RasvenValidatedAccount, val contactPrefix: String)

/** App-private encrypted setup record. The Android Keystore key is never exported or backed up. */
internal class RasvenSecureSetupRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val lock = Any()

    fun load(): RasvenSavedSetup? = synchronized(lock) {
        runCatching {
            // A partial/corrupt preferences record may contain a value of the wrong type.
            val encoded = preferences.getString(RECORD, null) ?: return null
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            if (bytes.size <= IV_BYTES) return null
            val key = existingKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)))
            val data = JSONObject(String(cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)), Charsets.UTF_8))
            val server = data.getString("server")
            val port = data.getInt("port")
            val username = data.getString("username")
            val password = data.getString("password")
            val account = RasvenAccountValidation.validatedInput(server, port.toString(), username, password)
                ?: return null
            RasvenSavedSetup(account, data.optString("contactPrefix"))
        }.getOrNull()
    }

    fun save(account: RasvenValidatedAccount, contactPrefix: String): Boolean = synchronized(lock) {
        runCatching {
            val data = JSONObject().apply {
                put("server", account.server)
                put("port", account.port)
                put("username", account.username)
                put("password", account.password)
                put("contactPrefix", contactPrefix)
            }.toString().toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
            val record = cipher.iv + cipher.doFinal(data)
            preferences.edit().putString(RECORD, Base64.encodeToString(record, Base64.NO_WRAP)).commit()
        }.getOrDefault(false)
    }

    fun saveAccount(account: RasvenValidatedAccount): Boolean = synchronized(lock) {
        val current = load() ?: return false
        save(account, current.contactPrefix)
    }

    fun saveContactPrefix(contactPrefix: String): Boolean = synchronized(lock) {
        val current = load() ?: return false
        save(current.account, contactPrefix)
    }

    fun clear(): Boolean = synchronized(lock) { preferences.edit().remove(RECORD).commit() }

    private fun existingKey(): SecretKey? =
        (KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(KEY_ALIAS, null) as? SecretKey)

    private fun createKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
    }.generateKey()

    companion object {
        private const val PREFERENCES = "rasven_setup_secure_v1"
        private const val RECORD = "record"
        private const val KEY_ALIAS = "rasven_setup_v1_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
