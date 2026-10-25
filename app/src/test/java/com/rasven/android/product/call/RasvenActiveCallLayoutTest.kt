package com.rasven.android.product.call

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class RasvenActiveCallLayoutTest {
    @Test fun normalHeightAnchorsControlsInsideSafeAreaWithoutScrolling() {
        assertEquals(false, activeCallControlsScrollable(800.dp, keypadVisible = false))
        assertEquals(false, activeCallControlsScrollable(700.dp, keypadVisible = false))
    }

    @Test fun shortHeightScrollsButNormalKeypadFitsAboveNavigation() {
        assertEquals(true, activeCallControlsScrollable(500.dp, keypadVisible = false))
        assertEquals(true, activeCallControlsScrollable(600.dp, keypadVisible = true))
        assertEquals(true, activeCallControlsScrollable(700.dp, keypadVisible = true))
        assertEquals(false, activeCallControlsScrollable(800.dp, keypadVisible = true))
    }
}
