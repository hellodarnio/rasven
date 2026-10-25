package com.rasven.android.product.setup

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.RasvenResponsive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenSetupPresentationContractTest {
    @Test fun englishIsExplicitLtrAndPersianContractRemainsRtl() {
        assertEquals(LayoutDirection.Ltr, RasvenSetupPresentationContract.direction(RasvenLanguage.English))
        assertEquals(TextAlign.Left, RasvenSetupPresentationContract.alignment(RasvenLanguage.English))
        assertEquals(LayoutDirection.Rtl, RasvenSetupPresentationContract.direction(RasvenLanguage.Persian))
        assertEquals(TextAlign.Right, RasvenSetupPresentationContract.alignment(RasvenLanguage.Persian))
    }
    @Test fun englishFigmaInsetsDoNotChangeApprovedPersianResponsiveInsets() {
        for (width in listOf(320, 360, 412)) {
            assertEquals(16, RasvenSetupPresentationContract.pageMargin(RasvenLanguage.English, width.toFloat()))
            assertEquals(RasvenResponsive.pageHorizontalMargin(width),
                RasvenSetupPresentationContract.pageMargin(RasvenLanguage.Persian, width.toFloat()))
            val available = width - 32 - RasvenAccountInformationContract.portWidthDp - RasvenAccountInformationContract.fieldRowGapDp
            assertTrue("Username remains wide enough", available >= 156)
        }
    }
    @Test(expected = IllegalArgumentException::class)
    fun undersizedEnglishViewportStillFailsClosed() {
        RasvenSetupPresentationContract.pageMargin(RasvenLanguage.English, 319f)
    }
    @Test fun welcomeCardSpacingIsLocaleSpecificWithoutChangingPersian() {
        assertEquals(26, RasvenSetupPresentationContract.introToBenefitsGap(RasvenLanguage.Persian))
        assertEquals(2, RasvenSetupPresentationContract.introToBenefitsGap(RasvenLanguage.English))
    }
    @Test fun passwordAndPrefixAlignmentUseInterfaceDirectionAndKeepLtrParagraphs() {
        for (direction in listOf(LayoutDirection.Rtl, LayoutDirection.Ltr)) {
            val expected = if (direction == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
            assertEquals(expected, RasvenAccountInformationContract.passwordValueAlignment(direction))
            assertEquals(expected, RasvenAdvancedSettingsContract.passwordValueAlignment(direction))
            assertEquals(expected, RasvenCallSettingsContract.prefixAlignment(direction))
        }
    }
}
