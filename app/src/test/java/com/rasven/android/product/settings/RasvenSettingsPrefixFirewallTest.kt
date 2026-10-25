package com.rasven.android.product.settings

import com.rasven.android.product.contacts.RasvenContact
import com.rasven.android.product.contacts.RasvenContactsViewModel
import com.rasven.android.product.contacts.prepareContactDialDestination
import com.rasven.android.product.history.RasvenHistoryCallClassification
import com.rasven.android.product.history.RasvenHistoryEntry
import com.rasven.android.product.history.historyCallableDestination
import com.rasven.android.product.home.sanitizeDirectDialInput
import org.junit.Assert.assertEquals
import org.junit.Test

class RasvenSettingsPrefixFirewallTest {
    @Test
    fun updatedPrefixAppliesOnlyToEligibleContacts() {
        assertEquals("909121234567", prepareContactDialDestination("+98 912 123 4567", "9"))
        assertEquals("305", prepareContactDialDestination("305", "9"))
        assertEquals("305", sanitizeDirectDialInput("305"))
        assertEquals(
            "305",
            historyCallableDestination(
                RasvenHistoryEntry("fixture", RasvenHistoryCallClassification.Outgoing, null, "305", 1L),
            ),
        )
    }
    @Test
    fun settingsSaveUpdatesContactsOwnerThenEligibleContactUsesCommittedPrefix() {
        val settings = RasvenSettingsViewModel()
        val contacts = RasvenContactsViewModel()
        contacts.updateContactPrefix("9")
        settings.openContactPrefix(contacts.contactPrefix)
        settings.updateContactPrefixDraft("81")

        assertEquals("909121234567", contacts.prepareSelectedContact(RasvenContact("fixture", "+98 912 123 4567")))
        settings.saveContactPrefix(contacts::updateContactPrefix)

        assertEquals("8109121234567", contacts.prepareSelectedContact(RasvenContact("fixture", "+98 912 123 4567")))
        assertEquals("305", contacts.prepareSelectedContact(RasvenContact("internal", "305")))
    }

}
