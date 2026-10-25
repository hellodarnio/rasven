package com.rasven.android.product.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenPbxTransferProfileTest {
    @Test fun factoryMigrationDefaultsMatchApprovedIssabelProfile() {
        val profile = RasvenPbxTransferProfile.issabelAsteriskDefaults
        assertEquals("*2", profile.blindCode)
        assertEquals("##", profile.attendedCode)
        assertEquals("*1", profile.cancelAttendedCode)
        assertEquals(RasvenAttendedCompletionMode.Hangup, profile.completionMode)
        assertTrue(profile.isValid())
    }

    @Test fun yeastarSitePresetUsesAdministratorConfirmedAttendedCodeWithoutInventingCancellation() {
        val yeastar = RasvenPbxTransferProfile.yeastarS50Defaults
        assertEquals(RasvenPbxProfileIdentity.Yeastar, yeastar.identity)
        assertEquals("##", yeastar.blindCode)
        assertEquals("*3", yeastar.attendedCode)
        assertEquals("", yeastar.cancelAttendedCode)
        assertEquals(RasvenAttendedCompletionMode.Hangup, yeastar.completionMode)
        assertTrue(yeastar.isValid())

        val withDestination = yeastar.copy(salesDestination = "200")
        assertEquals(RasvenPbxProfileIdentity.Yeastar, withDestination.withCode().identity)
        assertEquals("200", withDestination.withCode().salesDestination)
        assertEquals(RasvenPbxProfileIdentity.Custom, yeastar.copy(blindCode = "*9").withCode().identity)
        assertEquals("200", withDestination.withPreset(RasvenPbxTransferProfile.issabelAsteriskDefaults).salesDestination)
        assertEquals("*2", withDestination.withPreset(RasvenPbxTransferProfile.issabelAsteriskDefaults).blindCode)
    }

    @Test fun customFeatureMapsRemainExactAndCanBeReset() {
        val repository = MemoryProfileRepository()
        val custom = RasvenPbxTransferProfile.issabelAsteriskDefaults.copy(
            blindCode = "*7", attendedCode = "*8", cancelAttendedCode = "*9",
        ).withCode()
        assertTrue(repository.save(custom))
        assertEquals(custom, repository.load())
        assertEquals(RasvenPbxTransferProfile.issabelAsteriskDefaults, repository.resetToDefaults())
        assertEquals(RasvenPbxTransferProfile.issabelAsteriskDefaults, repository.load())
    }

    @Test fun validationAllowsOnlyTelephonySymbolsAndPreservesCodes() {
        assertNull(RasvenPbxTransferProfile.featureCodeError("*2"))
        assertNull(RasvenPbxTransferProfile.featureCodeError("##"))
        assertNull(RasvenPbxTransferProfile.featureCodeError("12*#09"))
        assertEquals("required", RasvenPbxTransferProfile.featureCodeError("  "))
        assertEquals("unsafe", RasvenPbxTransferProfile.featureCodeError(" *2"))
        assertEquals("unsafe", RasvenPbxTransferProfile.featureCodeError("*x"))
        assertEquals("unsafe", RasvenPbxTransferProfile.featureCodeError("*2+305"))
        assertTrue(RasvenPbxTransferProfile.yeastarS50Defaults.isValid())
        assertFalse(RasvenPbxTransferProfile.yeastarS50Defaults.copy(cancelAttendedCode = "*x").isValid())
    }

    @Test fun saveRejectsInvalidProfileAndDoesNotReplaceExistingValue() {
        val repository = MemoryProfileRepository()
        val original = repository.load()
        assertFalse(repository.save(original.copy(blindCode = "*2 305")))
        assertEquals(original, repository.load())
    }

    @Test fun persistentRepositoryMigratesOnlyTransferKeysAndKeepsExistingCallSettings() {
        val values = mutableMapOf("server" to "pbx.example", "port" to "4569", "username" to "204", "password" to "secret-fixture")
        val store = object : RasvenTransferProfileKeyValueStore {
            override fun get(key: String) = values[key]
            override fun contains(key: String) = key in values
            override fun putAll(valuesToWrite: Map<String, String>): Boolean { values.putAll(valuesToWrite); return true }
        }
        val repository = RasvenPersistentTransferProfileRepository(store)
        assertEquals(RasvenPbxTransferProfile.issabelAsteriskDefaults, repository.load())
        assertEquals("pbx.example", values["server"])
        assertEquals("4569", values["port"])
        assertEquals("204", values["username"])
        assertEquals("secret-fixture", values["password"])
        assertEquals("*2", values["blind_code"])
        assertEquals("##", values["attended_code"])
        assertEquals("*1", values["cancel_attended_code"])
        assertEquals("Hangup", values["completion_mode"])

        val custom = RasvenPbxTransferProfile.issabelAsteriskDefaults.copy(blindCode = "*7", attendedCode = "*8", cancelAttendedCode = "*9").withCode()
        assertTrue(repository.save(custom))
        assertEquals(custom, repository.load())
        assertEquals("pbx.example", values["server"])
    }

    @Test fun persistentRepositoryReportsFailedFeatureMapCommit() {
        val stored = mutableMapOf<String, String>()
        val store = object : RasvenTransferProfileKeyValueStore {
            override fun get(key: String) = stored[key]
            override fun contains(key: String) = key in stored
            override fun putAll(values: Map<String, String>) = false
        }
        val repository = RasvenPersistentTransferProfileRepository(store)
        assertFalse(repository.save(RasvenPbxTransferProfile.yeastarS50Defaults))
        assertTrue(stored.isEmpty())
    }

    @Test fun legacyYeastarSitePresetMigratesButCustomAndIssabelCodesDoNot() {
        val stored = mutableMapOf<String, String>()
        val store = object : RasvenTransferProfileKeyValueStore {
            override fun get(key: String) = stored[key]
            override fun contains(key: String) = key in stored
            override fun putAll(values: Map<String, String>): Boolean {
                stored.putAll(values)
                return true
            }
        }
        val repository = RasvenPersistentTransferProfileRepository(store)
        val legacy = RasvenPbxTransferProfile.yeastarS50Defaults.copy(
            attendedCode = "*2", cancelAttendedCode = "*1", salesDestination = "200",
        )
        assertTrue(repository.save(legacy))
        assertEquals("*3", repository.load().attendedCode)
        assertEquals("", repository.load().cancelAttendedCode)
        assertEquals("##", repository.load().blindCode)
        assertEquals("200", repository.load().salesDestination)

        val custom = legacy.copy(identity = RasvenPbxProfileIdentity.Custom)
        assertTrue(repository.save(custom))
        assertEquals(custom, repository.load())

        val issabel = RasvenPbxTransferProfile.issabelAsteriskDefaults
        assertTrue(repository.save(issabel))
        assertEquals(issabel, repository.load())
    }

    private class MemoryProfileRepository : RasvenPbxTransferProfileRepository {
        private var current = RasvenPbxTransferProfile.issabelAsteriskDefaults
        override fun load() = current
        override fun save(profile: RasvenPbxTransferProfile): Boolean {
            if (!profile.isValid()) return false
            current = profile
            return true
        }
        override fun resetToDefaults(): RasvenPbxTransferProfile = RasvenPbxTransferProfile.issabelAsteriskDefaults.also { current = it }
    }
}
