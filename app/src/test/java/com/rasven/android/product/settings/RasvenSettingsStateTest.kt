package com.rasven.android.product.settings

import com.rasven.android.product.setup.RasvenAdvancedSettingsCurrentAccount
import com.rasven.android.product.call.RasvenPbxTransferProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenSettingsStateTest {
    @Test
    fun internalNavigationReturnsEachSubpageToMain() {
        val owner = RasvenSettingsViewModel()
        owner.openContactPrefix("9")
        assertEquals(RasvenSettingsDestination.ContactPrefix, owner.uiState.destination)
        assertTrue(owner.backToMain())
        owner.openCallNotifications()
        assertEquals(RasvenSettingsDestination.CallNotifications, owner.uiState.destination)
        assertTrue(owner.backToMain())
        owner.openAdvanced(RasvenAdvancedSettingsCurrentAccount("pbx.example.net", 4569, "204"))
        assertEquals(RasvenSettingsDestination.Advanced, owner.uiState.destination)
        assertTrue(owner.backToMain())
        assertEquals(RasvenSettingsDestination.Main, owner.uiState.destination)
    }

    @Test
    fun prefixDraftDoesNotCommitUntilSaveAndSaveCommitsOnce() {
        val owner = RasvenSettingsViewModel()
        var activePrefix = "9"
        var commits = 0
        owner.openContactPrefix(activePrefix)
        owner.updateContactPrefixDraft("81")
        assertEquals("9", activePrefix)

        assertTrue(owner.saveContactPrefix { activePrefix = it; commits += 1 })
        assertEquals("81", activePrefix)
        assertEquals(1, commits)
        assertFalse(owner.saveContactPrefix { commits += 1 })
        assertEquals(1, commits)
    }

    @Test
    fun responsiveGeometryMatchesApproved320360412Contracts() {
        assertEquals(RasvenSettingsResponsiveGeometry(16, 288), settingsResponsiveGeometry(320))
        assertEquals(RasvenSettingsResponsiveGeometry(24, 312), settingsResponsiveGeometry(360))
        assertEquals(RasvenSettingsResponsiveGeometry(24, 364), settingsResponsiveGeometry(412))
    }

    @Test
    fun advancedProjectionStartsWithEmptyReplacementAndLeavingScrubsIt() {
        val owner = RasvenSettingsViewModel()
        owner.openAdvanced(RasvenAdvancedSettingsCurrentAccount("pbx.example.net", 4569, "204"))
        assertEquals("pbx.example.net", owner.uiState.advancedDraft.server)
        assertEquals("4569", owner.uiState.advancedDraft.port)
        assertEquals("204", owner.uiState.advancedDraft.username)
        assertEquals("", owner.uiState.advancedDraft.passwordReplacement)

        owner.updateAdvancedPasswordReplacement("replacement-fixture")
        owner.openMain()
        assertEquals("", owner.uiState.advancedDraft.passwordReplacement)
        assertFalse(owner.uiState.advancedDraft.attemptedSave)
        assertFalse(owner.uiState.toString().contains("replacement-fixture"))
    }

    @Test
    fun advancedValidationReusesExistingValidatorAndDoesNotCreateLanguageDestination() {
        val owner = RasvenSettingsViewModel()
        owner.openAdvanced(RasvenAdvancedSettingsCurrentAccount("pbx.example.net", 4569, "204"))
        owner.updateAdvancedPort("0")
        assertNull(owner.validatedAdvancedUpdate())
        assertFalse(owner.advancedErrors().isValid)
        assertEquals(
            setOf("Main", "ContactPrefix", "CallNotifications", "Advanced"),
            enumValues<RasvenSettingsDestination>().map { it.name }.toSet(),
        )
    }

    @Test
    fun transferProfileDraftCoexistsWithAccountFieldsAndRestoresOnlyTransferDefaults() {
        val owner = RasvenSettingsViewModel()
        owner.openAdvanced(RasvenAdvancedSettingsCurrentAccount("pbx.example.net", 4569, "204"))
        owner.updateTransferProfile(RasvenPbxTransferProfile.issabelAsteriskDefaults.copy(
            blindCode = "*7", attendedCode = "*8", cancelAttendedCode = "*9",
        ))
        assertEquals("*7", owner.uiState.advancedDraft.transferProfile.blindCode)
        assertEquals("pbx.example.net", owner.uiState.advancedDraft.server)
        assertEquals("204", owner.uiState.advancedDraft.username)
        assertTrue(owner.transferProfileErrors().isEmpty)

        owner.updateTransferProfile(owner.uiState.advancedDraft.transferProfile.copy(blindCode = "word"))
        assertFalse(owner.transferProfileErrors().isEmpty)
        owner.restoreTransferProfileDefaults()
        assertEquals(RasvenPbxTransferProfile.issabelAsteriskDefaults, owner.validatedTransferProfile())
        assertEquals("pbx.example.net", owner.uiState.advancedDraft.server)
    }
}
