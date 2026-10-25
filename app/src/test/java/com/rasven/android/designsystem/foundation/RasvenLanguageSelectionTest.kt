package com.rasven.android.designsystem.foundation

import org.junit.Assert.*
import org.junit.Test

class RasvenLanguageSelectionTest {
    private class Store(var tag: String? = null, var writable: Boolean = true) : RasvenLanguageChoiceStore {
        var writes = 0
        override fun readTag() = tag
        override fun writeTag(tag: String): Boolean {
            writes++
            if (!writable) return false
            this.tag = tag
            return true
        }
    }

    @Test fun unfinishedDefaultIsPersianAndCompletionUsesOwnerEnglishDefault() {
        val store = Store()
        var complete = false
        val selection = RasvenLanguageSelection(store) { complete }
        assertEquals(RasvenLanguage.Persian, selection.current())
        complete = true
        assertEquals(RasvenLanguage.English, selection.current())
        assertEquals(0, store.writes)
    }

    @Test fun savedPersianSurvivesCompletionWithoutMigrationRewrite() {
        val store = Store("fa")
        assertEquals(RasvenLanguage.Persian, RasvenLanguageSelection(store) { true }.current())
        assertEquals(0, store.writes)
    }

    @Test fun unfinishedEnglishSelectionCannotOverwriteExistingPreference() {
        val store = Store("fa")
        assertFalse(RasvenLanguageSelection(store) { false }.select(RasvenLanguage.English))
        assertEquals("fa", store.tag)
        assertEquals(0, store.writes)
    }

    @Test fun savedEnglishIsNotErasedWhileIncomplete() {
        val store = Store("en")
        assertEquals(RasvenLanguage.Persian, RasvenLanguageSelection(store) { false }.current())
        assertEquals("en", store.tag)
        assertEquals(0, store.writes)
    }

    @Test fun explicitSelectionStoresCanonicalTagAndSurvivesNewOwner() {
        val store = Store()
        assertTrue(RasvenLanguageSelection(store) { true }.select(RasvenLanguage.Persian))
        assertEquals("fa", store.tag)
        assertEquals(RasvenLanguage.Persian, RasvenLanguageSelection(store) { true }.current())
        assertTrue(RasvenLanguageSelection(store) { true }.select(RasvenLanguage.English))
        assertEquals("en", store.tag)
    }

    @Test fun persistenceFailureDoesNotClaimTheRequestedLanguage() {
        val store = Store("fa", writable = false)
        val selection = RasvenLanguageSelection(store) { true }
        assertFalse(selection.select(RasvenLanguage.English))
        assertEquals(RasvenLanguage.Persian, selection.current())
    }
}
