package com.rasven.android.product.setup

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey

@RunWith(AndroidJUnit4::class)
class RasvenSecureSetupRepositoryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val account = RasvenValidatedAccount("pbx.example.org", 4569, "204", "fixture-secret-42")

    @After fun clear() { RasvenSecureSetupRepository(context).clear() }

    @Test fun encryptedRecordRestoresAllSetupValuesWithoutPlaintextPreference() {
        val repository = RasvenSecureSetupRepository(context)
        assertTrue(repository.save(account, "81"))
        val raw = context.getSharedPreferences("rasven_setup_secure_v1", Context.MODE_PRIVATE)
            .getString("record", null).orEmpty()
        assertFalse(raw.contains(account.password))
        assertFalse(raw.contains(account.server))
        val restored = RasvenSecureSetupRepository(context).load()
        assertEquals(account.server, restored?.account?.server)
        assertEquals(account.port, restored?.account?.port)
        assertEquals(account.username, restored?.account?.username)
        assertEquals(account.password, restored?.account?.password)
        assertEquals("81", restored?.contactPrefix)
        assertTrue(repository.clear())
        assertNull(repository.load())
    }

    @Test fun settingsEditsPreserveUnchangedCredentialAndPrefix() {
        val repository = RasvenSecureSetupRepository(context)
        assertTrue(repository.save(account, "81"))
        assertTrue(repository.saveAccount(account.applyAdvancedSettings(
            RasvenAdvancedSettingsUpdate("pbx2.example.org", 4570, "205", null))))
        assertTrue(repository.saveContactPrefix("82"))
        val restored = repository.load()
        assertEquals("pbx2.example.org", restored?.account?.server)
        assertEquals(4570, restored?.account?.port)
        assertEquals("205", restored?.account?.username)
        assertEquals(account.password, restored?.account?.password)
        assertEquals("82", restored?.contactPrefix)
    }

    @Test fun wrongPreferenceTypeFailsClosedAndAllowsExplicitAccountReplacement() {
        val prefs = context.getSharedPreferences("rasven_setup_secure_v1", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putInt("record", 7).commit())
        val repository = RasvenSecureSetupRepository(context)
        assertNull(repository.load())
        assertFalse(repository.saveContactPrefix("82"))
        assertTrue(repository.save(account, "81"))
        assertEquals(account.password, repository.load()?.account?.password)
    }

    @Test fun malformedAndTruncatedCiphertextNeverRestoresCredentials() {
        val prefs = context.getSharedPreferences("rasven_setup_secure_v1", Context.MODE_PRIVATE)
        val repository = RasvenSecureSetupRepository(context)
        for (record in listOf("not-base64!", Base64.encodeToString(ByteArray(12), Base64.NO_WRAP))) {
            assertTrue(prefs.edit().putString("record", record).commit())
            assertNull(repository.load())
            assertFalse(repository.saveAccount(account))
        }
    }

    @Test fun corruptAuthenticationTagFailsClosed() {
        val repository = RasvenSecureSetupRepository(context)
        assertTrue(repository.save(account, "81"))
        val prefs = context.getSharedPreferences("rasven_setup_secure_v1", Context.MODE_PRIVATE)
        val bytes = Base64.decode(prefs.getString("record", null), Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertTrue(prefs.edit().putString("record", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
        assertNull(repository.load())
    }

    @Test fun missingKeystoreKeyCannotRestoreButFreshSetupCanRecover() {
        val repository = RasvenSecureSetupRepository(context)
        assertTrue(repository.save(account, "81"))
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keys.deleteEntry("rasven_setup_v1_aes")
        assertNull(repository.load())
        assertFalse(repository.saveAccount(account))
        assertTrue(repository.save(account, "82"))
        assertEquals(account.password, repository.load()?.account?.password)
        assertEquals("82", repository.load()?.contactPrefix)
    }

    @Test fun restoredCiphertextWithDifferentKeyFailsClosed() {
        val repository = RasvenSecureSetupRepository(context)
        assertTrue(repository.save(account, "81"))
        val prefs = context.getSharedPreferences("rasven_setup_secure_v1", Context.MODE_PRIVATE)
        val oldCiphertext = prefs.getString("record", null)
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keys.deleteEntry("rasven_setup_v1_aes")
        assertTrue(repository.save(account, "82"))
        assertTrue(prefs.edit().putString("record", oldCiphertext).commit())
        assertNull(repository.load())
    }

    @Test fun authenticatedButPartialAccountRecordDoesNotRestore() {
        val repository = RasvenSecureSetupRepository(context)
        assertTrue(repository.save(account, "81"))
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keys.getKey("rasven_setup_v1_aes", null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val incomplete = "{\"server\":\"pbx.example.org\",\"port\":4569,\"username\":\"204\"}"
        val bytes = cipher.iv + cipher.doFinal(incomplete.toByteArray(Charsets.UTF_8))
        val prefs = context.getSharedPreferences("rasven_setup_secure_v1", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString("record", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
        assertNull(repository.load())
    }
}
