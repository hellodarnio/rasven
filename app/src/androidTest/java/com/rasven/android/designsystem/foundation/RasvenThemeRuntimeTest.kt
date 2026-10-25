package com.rasven.android.designsystem.foundation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasvenThemeRuntimeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theme_clampsSystemFontScaleToApprovedRangeWithoutChangingPhysicalDensity() {
        var lowResolved = Float.NaN
        var lowDensity = Float.NaN
        var highResolved = Float.NaN
        var highDensity = Float.NaN

        // Compose test rules permit one setContent call per test. Exercise both
        // owner-approved font-scale boundaries inside the same composition.
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 0.75f)) {
                RasvenTheme {
                    lowResolved = LocalDensity.current.fontScale
                    lowDensity = LocalDensity.current.density
                }
            }
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 2.00f)) {
                RasvenTheme {
                    highResolved = LocalDensity.current.fontScale
                    highDensity = LocalDensity.current.density
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(1.00f, lowResolved, 0.0001f)
            assertEquals(2.00f, lowDensity, 0.0001f)
            assertEquals(1.25f, highResolved, 0.0001f)
            assertEquals(2.00f, highDensity, 0.0001f)
        }
    }
}
