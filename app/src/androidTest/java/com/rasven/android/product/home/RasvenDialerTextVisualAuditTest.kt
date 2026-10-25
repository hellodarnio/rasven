package com.rasven.android.product.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import com.rasven.android.product.call.RasvenOutgoingCallAttempt
import com.rasven.android.product.call.RasvenOutgoingCallOrigin
import com.rasven.android.product.call.RasvenOutgoingCallScreen
import com.rasven.android.product.call.RasvenOutgoingCallState
import com.rasven.android.product.setup.RasvenRegistrationState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pure UI fixtures only: no PBX, real registration, Telecom or microphone. */
class RasvenDialerTextVisualAuditTest {
    @get:Rule val composeRule = createComposeRule()
    private val output get() = File(
        InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-text-audit",
    ).apply { mkdirs() }

    @Test fun shortPressDeletesOneAndPhysicalLongPressClearsEverything() {
        var destination by mutableStateOf("1234567890")
        var shortClicks = 0
        var clearCallbacks = 0
        var imeHeight = 0
        composeRule.setContent {
            RasvenTheme {
                val inset = WindowInsets.ime.getBottom(LocalDensity.current)
                SideEffect { imeHeight = inset }
                RasvenHomeDialerScreen(
                    state = RasvenHomeDialerUiState(RasvenRegistrationState.Registered, "204", false, destination),
                    persianFontFamily = RasvenAndroidFontFamilies.Persian, englishFontFamily = FontFamily.Default,
                    onDestinationChange = { destination = sanitizeDirectDialInput(it); clearCallbacks++ },
                    onDialKey = { destination = appendDialCharacter(destination, it) },
                    onDelete = { destination = deleteLastDialCharacter(destination); shortClicks++ },
                    onDoNotDisturbChanged = {}, onStartOutgoingCall = {}, onReconnect = {}, onNavigationDestination = {},
                )
            }
        }
        capture("home-number-device")
        composeRule.onNodeWithTag("home_destination_input").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals("Read-only dialer must not open keyboard", 0, imeHeight) }
        composeRule.onNodeWithTag("home_delete").performClick()
        composeRule.onNodeWithTag("home_destination_input").assertTextContains("123456789")
        composeRule.onNodeWithTag("home_delete").performTouchInput { longClick() }
        composeRule.onNodeWithTag("home_delete").assertDoesNotExist()
        composeRule.onNodeWithTag("home_call_action").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals("", destination)
            assertEquals("Long press must not also delete one character", 1, shortClicks)
            assertEquals("Long press must issue one controlled clear", 1, clearCallbacks)
        }
        capture("home-cleared-device")
    }

    @Test fun actualDigitPixelsBeginAtLeftInsetNotAtCenterInBothLocales() {
        var destination by mutableStateOf(" ")
        var rtl by mutableStateOf(true)
        var density = 1f
        composeRule.setContent {
            RasvenTheme {
                val d = LocalDensity.current.density
                SideEffect { density = d }
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    RasvenHomeDialerScreen(
                        RasvenHomeDialerUiState(RasvenRegistrationState.Registered, "204", false, destination),
                        RasvenAndroidFontFamilies.Persian, FontFamily.Default, {}, {}, {}, {}, {}, {}, {},
                        modifier = Modifier.width(360.dp),
                    )
                }
            }
        }
        for (direction in listOf(true, false)) {
            composeRule.runOnIdle { rtl = direction; destination = " " }
            val baseline = composeRule.onNodeWithTag("home_destination_control").captureToImage().asAndroidBitmap()
            composeRule.runOnIdle { destination = "1234567890" }
            val filled = composeRule.onNodeWithTag("home_destination_control").captureToImage().asAndroidBitmap()
            var firstGlyphPixel = filled.width
            // Same read-only, nonempty state, same icon and same surface. Differences inside
            // the number viewport isolate glyph pixels, not alpha/background pixels.
            for (x in 0 until (filled.width - 72 * density).toInt()) for (y in 0 until filled.height) {
                val a = baseline.getPixel(x, y)
                val b = filled.getPixel(x, y)
                val difference = kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                    kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                    kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
                if (difference > 30) firstGlyphPixel = minOf(firstGlyphPixel, x)
            }
            assertTrue("Number starts at left 12dp inset, not middle; first pixel=$firstGlyphPixel",
                firstGlyphPixel >= (12 * density).toInt() && firstGlyphPixel <= (20 * density).toInt())
            capture("home-left-glyph-${if (direction) "rtl" else "ltr"}")
        }
    }

    @Test fun physicalRightDeleteAndLtrLeftDigitsAt320360AndDeviceWidthInBothLocales() {
        var width by mutableStateOf(360)
        var rtl by mutableStateOf(true)
        var connected by mutableStateOf(true)
        var scale by mutableStateOf(1f)
        var density = 1f
        composeRule.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(systemDensity.density, scale)) {
                RasvenTheme {
                    val currentDensity = LocalDensity.current.density
                    SideEffect { density = currentDensity }
                    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        RasvenHomeDialerScreen(
                            state = RasvenHomeDialerUiState(
                                if (connected) RasvenRegistrationState.Registered else RasvenRegistrationState.Unregistered,
                                "204", false, "+12345678901234567890",
                            ),
                            persianFontFamily = RasvenAndroidFontFamilies.Persian, englishFontFamily = FontFamily.Default,
                            onDestinationChange = {}, onDialKey = {}, onDelete = {}, onDoNotDisturbChanged = {},
                            onStartOutgoingCall = {}, onReconnect = {}, onNavigationDestination = {},
                            modifier = if (width == 0) Modifier else Modifier.width(width.dp),
                        )
                    }
                }
            }
        }
        // Owner: review only the first/base font size; do not run the second enlargement.
        for (w in listOf(320, 360, 0)) for (direction in listOf(true, false)) for (fontScale in listOf(1f)) {
            composeRule.runOnIdle { width = w; rtl = direction; scale = fontScale; connected = true }
            val field = composeRule.onNodeWithTag("home_destination_control").fetchSemanticsNode().boundsInRoot
            val delete = composeRule.onNodeWithTag("home_delete").fetchSemanticsNode().boundsInRoot
            assertEquals("Physical right inset", 8 * density, field.right - delete.right, 1f)
            assertEquals("48dp touch target", 48 * density, delete.width, 1f)
            assertTrue(delete.left > field.center.x)
            val text = layout("home_destination_input")
            assertEquals(TextDirection.Ltr, text.layoutInput.style.textDirection)
            assertEquals(TextAlign.Left, text.layoutInput.style.textAlign)
            assertEquals(1, text.lineCount)
            // A long number may scroll horizontally; it must never render under the delete target.
            val viewport = composeRule.onNodeWithTag("home_destination_input").fetchSemanticsNode().boundsInRoot
            assertTrue("Text viewport must end before the delete target", viewport.right < delete.left)
            capture("home-$w-${if (direction) "rtl" else "ltr"}-$fontScale")
        }
        composeRule.runOnIdle { connected = false }
        composeRule.onNodeWithTag("home_delete").assertDoesNotExist()
        composeRule.onNodeWithTag("home_destination_input").assertIsNotEnabled()
    }

    @Test fun allSevenOutgoingHeadersUseFigmaPhysicalEdgesAndKeepTextUnclipped() {
        val attempt = RasvenOutgoingCallAttempt(1L, "09121234567", RasvenOutgoingCallOrigin.HomeDirectDial)
        val states = listOf(
            "permission" to RasvenOutgoingCallState.AwaitingMicrophonePermission("09121234567", RasvenOutgoingCallOrigin.HomeDirectDial),
            "dialing" to RasvenOutgoingCallState.Dialing(attempt),
            "ringing" to RasvenOutgoingCallState.Ringing(attempt),
            "busy" to RasvenOutgoingCallState.Busy(attempt),
            "rejected" to RasvenOutgoingCallState.Rejected(attempt),
            "no-answer" to RasvenOutgoingCallState.NoAnswer(attempt),
            "network" to RasvenOutgoingCallState.NetworkError(attempt),
        )
        var state by mutableStateOf<RasvenOutgoingCallState>(states.first().second)
        var width by mutableStateOf(360)
        var scale by mutableStateOf(1f)
        var density = 1f
        var statusBar = 0
        composeRule.setContent {
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(systemDensity.density, scale)) {
                RasvenTheme {
                    val d = LocalDensity.current
                    val inset = WindowInsets.statusBars.getTop(d)
                    SideEffect { density = d.density; statusBar = inset }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        RasvenOutgoingCallScreen(
                            state = state, persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            onPermissionRequest = {}, onClosePermission = {}, onCancel = {}, onRetry = {}, onCloseFailure = {},
                            modifier = if (width == 0) Modifier else Modifier.width(width.dp),
                        )
                    }
                }
            }
        }
        for (w in listOf(320, 360, 0)) for (fontScale in listOf(1f)) for ((name, next) in states) {
            composeRule.runOnIdle { width = w; scale = fontScale; state = next }
            val tag = when (name) {
                "permission" -> "outgoing_permission_heading"
                "dialing", "ringing" -> "outgoing_dialing_heading"
                else -> "outgoing_failure_heading"
            }
            val root = composeRule.onNodeWithTag("outgoing_call_screen").fetchSemanticsNode().boundsInRoot
            val heading = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val brand = composeRule.onNodeWithTag("outgoing_brand").fetchSemanticsNode().boundsInRoot
            assertEquals("Figma logo physical left", root.left + 16 * density, brand.left, 1f)
            assertEquals("Figma logo top", root.top + 40 * density, brand.top, 1f)
            assertEquals("Figma logo size", 64 * density, brand.width, 1f)
            assertEquals("Figma right edge $name/$w/$fontScale", root.right - 16 * density, heading.right, 1f)
            assertEquals("Figma left edge $name/$w/$fontScale", root.left + 96 * density, heading.left, 1f)
            val headerInset = if (name == "dialing" || name == "ringing") 18 else 14
            assertEquals("Header inset only once", root.top + statusBar + headerInset * density, heading.top, 1f)
            val title = layout(tag)
            assertTrue("Unclipped heading $name/$w/$fontScale", !title.hasVisualOverflow)
            if (fontScale == 1f && w != 320) assertEquals("Reference heading is single-line $name", 1, title.lineCount)
            assertEquals(TextAlign.Right, title.layoutInput.style.textAlign)
            assertEquals("Heading font size is not scaled down with viewport", if (name == "dialing" || name == "ringing") 20f else 16f,
                title.layoutInput.style.fontSize.value, 0f)
            assertEquals(1, layout("outgoing_destination").lineCount)
            capture("outgoing-$name-$w-$fontScale")
        }
    }

    private fun layout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        File(output, "$name.png").outputStream().use {
            check(composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
