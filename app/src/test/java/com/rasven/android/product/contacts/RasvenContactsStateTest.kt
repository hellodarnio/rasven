package com.rasven.android.product.contacts

import com.rasven.android.product.home.sanitizeDirectDialInput
import com.rasven.android.designsystem.components.RasvenTextFieldSize
import com.rasven.android.designsystem.components.textFieldControlHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenContactsStateTest {
    private val fixtures = listOf(
        RasvenContact("علی رضایی", "09121234567"),
        RasvenContact("پشتیبانی فروش", "+982188880000"),
        RasvenContact("مریم احمدی", "09351234567"),
    )

    @Test fun searchByNameUsesAlreadyLoadedProjection() {
        assertEquals(listOf(fixtures[2]), filterContacts(fixtures, "مریم"))
    }

    @Test fun searchByNumberUsesAlreadyLoadedProjection() {
        // Both mobile numbers share this suffix; substring search must return both.
        assertEquals(listOf(fixtures[0], fixtures[2]), filterContacts(fixtures, "1234567"))
        assertEquals(listOf(fixtures[0]), filterContacts(fixtures, "0912123"))
    }

    @Test fun searchNoMatchReturnsEmptySafely() {
        assertTrue(filterContacts(fixtures, "وجود ندارد").isEmpty())
    }

    @Test fun iranianRepresentationsNormalizeToSameNationalFormBeforePrefix() {
        val expected = "909121234567"
        assertEquals(expected, prepareContactDialDestination("09121234567", "9"))
        assertEquals(expected, prepareContactDialDestination("+989121234567", "9"))
        assertEquals(expected, prepareContactDialDestination("00989121234567", "9"))
    }

    @Test fun iranianLandlineInternationalFormNormalizesBeforePrefix() {
        assertEquals("902188880000", prepareContactDialDestination("+982188880000", "9"))
        assertEquals("902188880000", prepareContactDialDestination("00982188880000", "9"))
        assertEquals("902188880000", prepareContactDialDestination("02188880000", "9"))
    }

    @Test fun internalExtensionAndShortEmergencyStyleCodesRemainUnchanged() {
        assertEquals("305", prepareContactDialDestination("305", "9"))
        assertEquals("112", prepareContactDialDestination("112", "9"))
        assertEquals("*123#", prepareContactDialDestination("*123#", "9"))
    }

    @Test fun contactsSearchUsesAuthorizedAndroidLarge48DpVariant() {
        assertEquals(48, textFieldControlHeight(RasvenTextFieldSize.Large))
    }

    @Test fun directDialRemainsOutsideContactsPrefixPipeline() {
        assertEquals("305", sanitizeDirectDialInput("305"))
        assertFalse(sanitizeDirectDialInput("305").startsWith("9"))
    }

    @Test fun permissionDecisionRequestsFirstThenUsesSettingsOnlyAfterPermanentDenial() {
        assertEquals(
            RasvenContactsPermissionAction.RequestSystemPermission,
            contactsPermissionAction(hasRequestedBefore = false, shouldShowRationale = false),
        )
        assertEquals(
            RasvenContactsPermissionAction.RequestSystemPermission,
            contactsPermissionAction(hasRequestedBefore = true, shouldShowRationale = true),
        )
        assertEquals(
            RasvenContactsPermissionAction.OpenAppSettings,
            contactsPermissionAction(hasRequestedBefore = true, shouldShowRationale = false),
        )
    }

    @Test fun permissionRecoveryGuardBlocksDuplicateInFlightAction() {
        val owner = RasvenContactsViewModel()
        assertTrue(owner.beginPermissionRecovery())
        assertFalse(owner.beginPermissionRecovery())
        owner.markPermissionRequestIssued()
        assertTrue(owner.hasRequestedPermission)
        owner.finishPermissionRecovery()
        assertTrue(owner.beginPermissionRecovery())
    }

    @Test fun stateOwnerFiltersLoadedContactsWithoutRepositoryRequery() {
        var loads = 0
        val owner = RasvenContactsViewModel()
        owner.loadContacts(RasvenContactsRepository { loads += 1; fixtures })
        owner.updateQuery("فروش")
        assertEquals(1, loads)
        assertEquals(listOf(fixtures[1]), owner.visibleContacts)
        owner.updateQuery("0912")
        assertEquals(1, loads)
        assertEquals(listOf(fixtures[0]), owner.visibleContacts)
    }
}
