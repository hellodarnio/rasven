package com.rasven.android.product.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class RasvenSetupWelcomeContractTest {
    @Test
    fun welcomeGeometry_matchesNewFigmaFrame() {
        assertEquals(48, RasvenSetupWelcomeContract.headerHeightDp)
        assertEquals(280, RasvenSetupWelcomeContract.heroWidthDp)
        assertEquals(252, RasvenSetupWelcomeContract.heroHeightDp)
        assertEquals(16, RasvenSetupWelcomeContract.horizontalInsetDp)
        assertEquals(16, RasvenSetupWelcomeContract.topInsetAfterStatusBarDp)
        assertEquals(16, RasvenSetupWelcomeContract.minimumBottomInsetDp)
    }

    @Test
    fun ambientUsesPhysicalFigmaSideAtAllRequestedWidths() {
        for (width in listOf(320f, 360f, 412f)) {
            assertEquals(-115f, RasvenSetupWelcomeContract.ambientLeftDp(width, english = true), 0.001f)
            assertEquals(width - 205f, RasvenSetupWelcomeContract.ambientLeftDp(width, english = false), 0.001f)
        }
        assertEquals(155f, RasvenSetupWelcomeContract.ambientLeftDp(360f, english = false), 0.001f)
    }

    @Test
    fun footerMatchesFigmaWhenSafeAndClearsTallerNavigationBars() {
        assertEquals(16f, RasvenSetupWelcomeContract.bottomInsetDp(0f), 0.001f)
        assertEquals(16f, RasvenSetupWelcomeContract.bottomInsetDp(16f), 0.001f)
        assertEquals(24f, RasvenSetupWelcomeContract.bottomInsetDp(24f), 0.001f)
    }
}
