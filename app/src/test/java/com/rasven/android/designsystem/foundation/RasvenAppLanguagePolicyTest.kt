package com.rasven.android.designsystem.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RasvenAppLanguagePolicyTest {
    @Test fun completedProductDefaultsToEnglishWithoutDeviceLanguageInference() {
        assertEquals(RasvenLanguage.English, RasvenAppLanguagePolicy.resolve(null, true))
        assertEquals(RasvenLanguage.English, RasvenAppLanguagePolicy.resolve("unsupported", true))
    }

    @Test fun explicitPersianChoiceSurvivesEnglishDefault() {
        assertEquals(RasvenLanguage.Persian, RasvenAppLanguagePolicy.resolve("fa", true))
        assertEquals(RasvenLanguage.Persian, RasvenAppLanguagePolicy.resolve(" FA-IR ", true))
    }

    @Test fun incompleteEnglishCannotExposeMixedProductOrEraseSavedChoice() {
        assertEquals(RasvenLanguage.Persian, RasvenAppLanguagePolicy.resolve(null, false))
        assertEquals(RasvenLanguage.Persian, RasvenAppLanguagePolicy.resolve("en", false))
        assertEquals(RasvenLanguage.English, RasvenAppLanguagePolicy.savedChoice("en"))
        assertEquals(RasvenLanguage.English, RasvenAppLanguagePolicy.resolve("en", true))
    }

    @Test fun stableStorageTagsAndSupportedRegionAliases() {
        assertEquals("en", RasvenAppLanguagePolicy.storageTag(RasvenLanguage.English))
        assertEquals("fa", RasvenAppLanguagePolicy.storageTag(RasvenLanguage.Persian))
        assertEquals(RasvenLanguage.English, RasvenAppLanguagePolicy.savedChoice("en-GB"))
        assertEquals(RasvenLanguage.English, RasvenAppLanguagePolicy.savedChoice("en-US"))
        assertNull(RasvenAppLanguagePolicy.savedChoice(""))
        assertNull(RasvenAppLanguagePolicy.savedChoice(null))
        assertNull(RasvenAppLanguagePolicy.savedChoice("ar"))
    }

    @Test fun bothLanguagesKeepExistingDirectionAndTechnicalIslandContracts() {
        assertEquals(RasvenLayoutDirection.Ltr,
            RasvenDirectionContract.screenDirection(RasvenAppLanguagePolicy.resolve(null, true)))
        assertEquals(RasvenLayoutDirection.Rtl,
            RasvenDirectionContract.screenDirection(RasvenAppLanguagePolicy.resolve("fa", true)))
        RasvenDataIslandKind.entries.forEach {
            assertEquals(RasvenLayoutDirection.Ltr, RasvenDirectionContract.dataIslandDirection(it))
        }
    }
}
