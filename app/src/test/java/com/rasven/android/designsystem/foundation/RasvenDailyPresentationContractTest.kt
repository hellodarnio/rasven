package com.rasven.android.designsystem.foundation

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test

class RasvenDailyPresentationContractTest {
    @Test fun persianKeepsExplicitRtlRightFallback() {
        assertEquals(LayoutDirection.Rtl, RasvenDailyPresentationContract.direction(RasvenLanguage.Persian))
        assertEquals(TextAlign.Right, RasvenDailyPresentationContract.alignment(RasvenLanguage.Persian))
        assertEquals(TextDirection.ContentOrRtl, RasvenDailyPresentationContract.paragraph(RasvenLanguage.Persian))
    }
    @Test fun englishOwnsLtrLeftFallbackIndependentlyOfRowGeometry() {
        assertEquals(LayoutDirection.Ltr, RasvenDailyPresentationContract.direction(RasvenLanguage.English))
        assertEquals(TextAlign.Left, RasvenDailyPresentationContract.alignment(RasvenLanguage.English))
        assertEquals(TextDirection.ContentOrLtr, RasvenDailyPresentationContract.paragraph(RasvenLanguage.English))
    }
    @Test fun onlyValueFirstRowsMirrorEnglishGeometry() {
        assertEquals(LayoutDirection.Ltr, RasvenDailyPresentationContract.valueFirstRowDirection(RasvenLanguage.Persian))
        assertEquals(LayoutDirection.Rtl, RasvenDailyPresentationContract.valueFirstRowDirection(RasvenLanguage.English))
        assertEquals(TextAlign.Left, RasvenDailyPresentationContract.alignment(RasvenLanguage.English))
    }
    @Test fun persianCardGeometryIsUnchangedEnglishLabelsGetRealRoom() {
        assertEquals(164, RasvenDailyPresentationContract.dndWidth(RasvenLanguage.Persian))
        assertEquals(185, RasvenDailyPresentationContract.dndWidth(RasvenLanguage.English))
        assertEquals(72, RasvenDailyPresentationContract.settingsValueWidth(RasvenLanguage.Persian))
        assertEquals(96, RasvenDailyPresentationContract.settingsValueWidth(RasvenLanguage.English))
    }
    @Test fun headerDisplayDigitsNeverMutateTheTechnicalDestination() {
        val value = "204*#+98"
        assertEquals("۲۰۴*#+۹۸", RasvenDailyPresentationContract.displayDigits(value, RasvenLanguage.Persian))
        assertEquals(value, RasvenDailyPresentationContract.displayDigits(value, RasvenLanguage.English))
        assertEquals("204*#+98", value)
    }
    @Test fun alreadyPersianAndEmptyValuesArePreserved() {
        assertEquals("", RasvenDailyPresentationContract.displayDigits("", RasvenLanguage.Persian))
        assertEquals("۲۰۴", RasvenDailyPresentationContract.displayDigits("۲۰۴", RasvenLanguage.Persian))
    }
}
