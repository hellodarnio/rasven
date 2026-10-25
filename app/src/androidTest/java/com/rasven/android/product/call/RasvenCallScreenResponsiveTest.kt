package com.rasven.android.product.call

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import com.rasven.android.designsystem.foundation.RasvenTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RasvenCallScreenResponsiveTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun activeCallEndActionStaysInside320360And412DpViewports() {
        var viewportWidth by mutableIntStateOf(320)
        composeRule.setContent {
            RasvenTheme {
                Box(Modifier.width(viewportWidth.dp).fillMaxHeight().testTag("call_viewport")) {
                    RasvenActiveCallScreen(
                        destination = "09121234567",
                        connectedAtElapsedMillis = System.nanoTime() / 1_000_000L,
                        controls = RasvenActiveCallControls(),
                        persianFontFamily = FontFamily.Default,
                        onMute = {}, onSpeaker = {}, canHold = true, onHold = {},
                        onKeypad = {}, onDtmf = {}, onEnd = {},
                    )
                }
            }
        }
        for (width in listOf(320, 360, 412)) {
            composeRule.runOnIdle { viewportWidth = width }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("پایان تماس").assertIsDisplayed()
            val viewport = composeRule.onNodeWithTag("call_viewport").fetchSemanticsNode().boundsInRoot
            val endAction = composeRule.onNodeWithContentDescription("پایان تماس").fetchSemanticsNode().boundsInRoot
            val controls = composeRule.onNodeWithTag("active_call_controls").fetchSemanticsNode().boundsInRoot
            val mute = composeRule.onNodeWithTag("call_control_mute").fetchSemanticsNode().boundsInRoot
            val speaker = composeRule.onNodeWithTag("call_control_speaker").fetchSemanticsNode().boundsInRoot
            val hold = composeRule.onNodeWithTag("call_control_hold").fetchSemanticsNode().boundsInRoot
            assertTrue("Figma's physical first-row order must be preserved", mute.left < speaker.left && speaker.left < hold.left)
            assertTrue("end action must stay within ${width}dp viewport", endAction.left >= viewport.left - 1f)
            assertTrue("end action must stay within ${width}dp viewport", endAction.right <= viewport.right + 1f)
            assertTrue(
                "end action must clear system navigation at ${width}dp: end=$endAction viewport=$viewport",
                endAction.bottom <= viewport.bottom - viewport.height * 0.04f,
            )
            assertTrue("talk controls must remain above End", controls.bottom < endAction.top)
            assertTrue("talk controls belong in the lower half", controls.center.y > viewport.center.y)
        }
    }

    @Test fun incomingCallActionsStayVisibleInCompactAndRegularWidths() {
        var viewportWidth by mutableIntStateOf(320)
        composeRule.setContent {
            RasvenTheme {
                Box(Modifier.width(viewportWidth.dp).fillMaxHeight()) {
                    RasvenIncomingCallScreen(
                        state = RasvenIncomingCallState.Ringing(
                            RasvenIncomingCall(7, "history-7", "09121234567", "کاربر آزمایشی", null, 1L),
                        ),
                        locked = false,
                        persianFontFamily = FontFamily.Default,
                        onAnswer = {}, onReject = {}, onEnd = {}, onRequestMicrophone = {},
                        onClose = {}, onCallback = {},
                    )
                }
            }
        }
        for (width in listOf(320, 360, 412)) {
            composeRule.runOnIdle { viewportWidth = width }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("پاسخ").assertIsDisplayed()
            composeRule.onNodeWithText("رد تماس").assertIsDisplayed()
            composeRule.onNodeWithText("کاربر آزمایشی").assertIsDisplayed()
        }
    }

    @Test fun activeCallExposesWorkingHoldAndTransferActionsInTheControlsState() {
        var holdRequested: Boolean? = null
        var transferRequested = false
        composeRule.setContent {
            RasvenTheme {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    RasvenActiveCallScreen(
                        destination = "200",
                        connectedAtElapsedMillis = System.nanoTime() / 1_000_000L,
                        controls = RasvenActiveCallControls(),
                        persianFontFamily = FontFamily.Default,
                        onMute = {}, onSpeaker = {}, canHold = true, onHold = { holdRequested = it },
                        onKeypad = {}, onDtmf = {}, onEnd = {},
                        onTransfer = { transferRequested = true },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("call_control_hold").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("call_control_transfer").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertTrue("Hold control must request the held state", holdRequested == true)
            assertTrue("Transfer control must invoke the transfer flow", transferRequested)
        }
    }

    @Test fun keypadDigitsExposeButtonSemanticsAndDeliverTheSelectedDigit() {
        var sent: Char? = null
        composeRule.setContent {
            RasvenTheme {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    RasvenActiveCallScreen(
                        destination = "09121234567",
                        connectedAtElapsedMillis = System.nanoTime() / 1_000_000L,
                        controls = RasvenActiveCallControls(keypadVisible = true),
                        persianFontFamily = FontFamily.Default,
                        onMute = {}, onSpeaker = {}, canHold = true, onHold = {},
                        onKeypad = {}, onDtmf = { sent = it }, onEnd = {},
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("کلید تماس 5").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("active_call_keypad_toggle").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithContentDescription("پایان تماس").assertIsDisplayed()
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val firstKey = composeRule.onNodeWithContentDescription("کلید تماس 1").fetchSemanticsNode().boundsInRoot
        val nextRowKey = composeRule.onNodeWithContentDescription("کلید تماس 4").fetchSemanticsNode().boundsInRoot
        val end = composeRule.onNodeWithContentDescription("پایان تماس").fetchSemanticsNode().boundsInRoot
        val rowGapDp = with(composeRule.density) { (nextRowKey.top - firstKey.bottom).toDp().value }
        assertTrue("Figma keypad begins near 252/800 of the screen: key=$firstKey screen=$screen",
            firstKey.top / screen.height in 0.29f..0.34f)
        assertTrue("Figma uses a 20dp vertical gap between keypad rows: gap=$rowGapDp",
            rowGapDp in 18f..22f)
        assertTrue("Keypad End must not be clipped by navigation: end=$end screen=$screen",
            end.bottom <= screen.bottom - screen.height * 0.04f)
        composeRule.runOnIdle { org.junit.Assert.assertEquals('5', sent) }
    }

    @Test fun compactKeypadKeepsFigmaVerticalRowSpacing() {
        composeRule.setContent {
            RasvenTheme {
                Box(Modifier.width(360.dp).height(760.dp)) {
                    RasvenActiveCallScreen(
                        destination = "09121234567",
                        connectedAtElapsedMillis = System.nanoTime() / 1_000_000L,
                        controls = RasvenActiveCallControls(keypadVisible = true),
                        persianFontFamily = FontFamily.Default,
                        onMute = {}, onSpeaker = {}, canHold = true, onHold = {},
                        onKeypad = {}, onDtmf = {}, onEnd = {},
                    )
                }
            }
        }
        val firstRow = composeRule.onNodeWithContentDescription("کلید تماس 1").fetchSemanticsNode().boundsInRoot
        val secondRow = composeRule.onNodeWithContentDescription("کلید تماس 4").fetchSemanticsNode().boundsInRoot
        val gapDp = with(composeRule.density) { (secondRow.top - firstRow.bottom).toDp().value }
        assertTrue("Compact keypad must preserve Figma's 20dp row gap; gap=$gapDp", gapDp in 18f..22f)
    }
}
