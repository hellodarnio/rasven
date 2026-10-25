package com.rasven.android.product

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.R
import com.rasven.android.debug.RasvenEnglishCallFixture
import com.rasven.android.debug.englishCallFixtureStages
import com.rasven.android.designsystem.foundation.*
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import com.rasven.android.product.call.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Prepared only. Requires a newly authorized phone test; never connects to a real account. */
class RasvenEnglishCallScreenTest {
    @get:Rule val rule = createComposeRule()

    @Before fun requireOfflineQaOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.rasven.android.qa.debug", context.packageName)
        assertTrue(context.resources.getBoolean(R.bool.rasven_english_setup_resources_ready))
    }

    @Test fun captureCallPresentationStatesAt320And360AtTheExistingBaseFontScale() {
        var stage by mutableStateOf("outgoing-ringing")
        var width by mutableStateOf(360)
        rule.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            RasvenTheme { RasvenProductLanguageScope(RasvenLanguage.English) {
                Box(Modifier.width(width.dp).height(800.dp).testTag("english_call_reference_viewport")) {
                    key(stage, width) { RasvenEnglishCallFixture(stage) }
                }
            } }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "english-call-audit").apply { mkdirs() }
        for (w in listOf(320, 360)) for (fixture in englishCallFixtureStages) {
            rule.runOnIdle { width = w; stage = fixture }
            rule.waitForIdle()
            val name = "$fixture-$w"
            File(output, "$name.png").outputStream().use {
                assertTrue(rule.onNodeWithTag("english_call_reference_viewport").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            File(output, "$name.json").writeText(collectMetrics(fixture).toString(2))
            if (fixture == "outgoing-ringing") {
                val viewport = rule.onNodeWithTag("english_call_reference_viewport").fetchSemanticsNode().boundsInRoot
                val cancel = rule.onNodeWithTag("outgoing_cancel_action").fetchSemanticsNode().boundsInRoot
                assertTrue("$name: outgoing cancel action should occupy the lower Figma action zone: $cancel / $viewport",
                    cancel.top >= viewport.height * 0.72f && cancel.bottom <= viewport.height * 0.95f)
            }
            if (fixture == "transfer-blind-manual" || fixture == "transfer-attended-manual" || fixture == "transfer-manual-error") {
                val viewport = rule.onNodeWithTag("english_call_reference_viewport").fetchSemanticsNode().boundsInRoot
                val back = rule.onNodeWithTag("transfer_return_call").fetchSemanticsNode().boundsInRoot
                val submit = rule.onNodeWithTag("transfer_submit_manual").fetchSemanticsNode().boundsInRoot
                assertTrue("$name: primary transfer action must sit near the safe bottom: $submit / $viewport",
                    viewport.bottom - submit.bottom in 0f..with(rule.density) { 100.dp.toPx() })
                assertTrue("$name: primary transfer action must follow Back and occupy the Figma bottom position",
                    back.top < submit.top)
            }
            if (fixture == "active-keypad" || fixture == "active-dtmf") {
                // Matched800dp reference viewport is deliberately shorter than the phone.
                // The approved keypad scrolls there; capture its reachable safe-inset End too.
                rule.onNodeWithContentDescription("End call").performScrollTo().assertIsDisplayed()
                File(output, "$name-scrolled.png").outputStream().use {
                    assertTrue(rule.onNodeWithTag("english_call_reference_viewport").captureToImage().asAndroidBitmap()
                        .compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                File(output, "$name-scrolled.json").writeText(collectMetrics("$fixture-scrolled").toString(2))
            }
            if (fixture.startsWith("transfer-") && fixture != "transfer-blind-manual" &&
                fixture != "transfer-attended-manual" && fixture != "transfer-manual-error") {
                val tags = listOf("transfer_result_action", "transfer_cancel_attended", "transfer_attended_waiting_return",
                    "transfer_attended_ready_return", "transfer_completion_pending_return", "transfer_submit_manual",
                    "transfer_return_call")
                val reachable = tags.firstOrNull { rule.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
                if (reachable != null) {
                    rule.onNodeWithTag(reachable).performScrollTo().assertIsDisplayed()
                    File(output, "$name-scrolled.png").outputStream().use {
                        assertTrue(rule.onNodeWithTag("english_call_reference_viewport").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    File(output, "$name-scrolled.json").writeText(collectMetrics("$fixture-scrolled").toString(2))
                }
            }
        }
    }

    @Test fun incomingEnglishAnswerAndDeclinePreserveSessionIdentityAndPhysicalOrder() {
        var answered: Long? = null
        var rejected: Long? = null
        val call = RasvenIncomingCall(991L, "synthetic", "305", "Alex Taylor", "204", 1_792_000_000_000L)
        rule.setContent { RasvenTheme { RasvenProductLanguageScope(RasvenLanguage.English) {
            RasvenIncomingCallScreen(RasvenIncomingCallState.Ringing(call), false, RasvenAndroidFontFamilies.Persian,
                { answered = it }, { rejected = it }, {}, {}, {}, {})
        } } }
        val answer = rule.onNodeWithContentDescription("Answer")
        val decline = rule.onNodeWithContentDescription("Decline")
        assertTrue("Decline must be physically left of Answer",
            decline.fetchSemanticsNode().boundsInRoot.center.x < answer.fetchSemanticsNode().boundsInRoot.center.x)
        answer.performClick()
        rule.runOnIdle { assertEquals(991L, answered) }
        decline.performClick()
        rule.runOnIdle { assertEquals(991L, rejected) }
    }

    @Test fun translatedTransferPresetStillSendsOriginalConfiguredKey() {
        var selected: String? = null
        rule.setContent { RasvenTheme { RasvenProductLanguageScope(RasvenLanguage.English) {
            TransferFixture(RasvenTransferUiState.SelectingDestination(callContext(), RasvenTransferMode.Blind)) { selected = it }
        } } }
        rule.onNodeWithTag("transfer_preset_0").performClick()
        rule.runOnIdle { assertEquals("پشتیبانی فنی", selected) }
        rule.onNodeWithText("Technical Support").assertExists()
    }

    @Test fun englishTransferDurationIsPhysicallyTimeFirstWithSeparateTextWeights() {
        rule.setContent { RasvenTheme { RasvenProductLanguageScope(RasvenLanguage.English) {
            TransferFixture(RasvenTransferUiState.ChoosingMode(callContext())) {}
        } } }
        val value = rule.onNodeWithTag("transfer_duration_value")
        val label = rule.onNodeWithTag("transfer_duration_label")
        assertTrue(value.fetchSemanticsNode().boundsInRoot.right < label.fetchSemanticsNode().boundsInRoot.left)
        val valueLayouts = mutableListOf<TextLayoutResult>()
        val labelLayouts = mutableListOf<TextLayoutResult>()
        value.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(valueLayouts) }
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(labelLayouts) }
        assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, valueLayouts.single().layoutInput.style.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, labelLayouts.single().layoutInput.style.fontWeight)
        label.assertTextEquals("Call duration")
    }

    @Test fun englishTransferFieldHasExplicitLeftAlignmentAndTechnicalLtr() {
        rule.setContent { RasvenTheme { RasvenProductLanguageScope(RasvenLanguage.English) {
            TransferFixture(RasvenTransferUiState.EnteringDestination(callContext(), RasvenTransferMode.Attended, "305")) {}
        } } }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("transfer_manual_input").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("305", layouts.single().layoutInput.text.text)
        assertEquals(TextAlign.Left, layouts.single().layoutInput.style.textAlign)
        assertEquals(TextDirection.Ltr, layouts.single().layoutInput.style.textDirection)
    }

    @Test fun aLoneEnglishStatusBadgeIsCenteredAndSideBySideStatusAlignsWithDuration() {
        var stage by mutableStateOf("transfer-choose")
        var width by mutableStateOf(360)
        rule.setContent { RasvenTheme { RasvenProductLanguageScope(RasvenLanguage.English) {
            Box(Modifier.width(width.dp).height(800.dp).testTag("english_call_reference_viewport")) {
                key(stage, width) { RasvenEnglishCallFixture(stage) }
            }
        } } }
        for (viewportWidth in listOf(320, 360)) {
            rule.runOnIdle { width = viewportWidth; stage = "transfer-choose" }
            rule.waitForIdle()
            val viewport = rule.onNodeWithTag("english_call_reference_viewport").fetchSemanticsNode().boundsInRoot
            val transferBadge = rule.onNodeWithTag("transfer_status_badge").fetchSemanticsNode().boundsInRoot
            assertTrue("$viewportWidth: standalone transfer status badge should be centered",
                abs(transferBadge.center.x - viewport.center.x) < with(rule.density) { 1.dp.toPx() })

            rule.runOnIdle { stage = "active-connected" }
            rule.waitForIdle()
            val activeViewport = rule.onNodeWithTag("english_call_reference_viewport").fetchSemanticsNode().boundsInRoot
            val activeBadge = rule.onNodeWithTag("active_call_status_badge").fetchSemanticsNode().boundsInRoot
            assertTrue("$viewportWidth: standalone active-call status badge should be centered",
                abs(activeBadge.center.x - activeViewport.center.x) < with(rule.density) { 1.dp.toPx() })
            val activeControls = rule.onNodeWithTag("active_call_controls").fetchSemanticsNode().boundsInRoot
            val activeEnd = rule.onNodeWithContentDescription("End call").fetchSemanticsNode().boundsInRoot
            assertTrue("$viewportWidth: connected controls should occupy the Figma lower-middle action zone",
                activeControls.top >= activeViewport.height * 0.40f && activeControls.top <= activeViewport.height * 0.55f)
            assertTrue("$viewportWidth: End Call must follow controls in the Figma action order",
                activeEnd.top > activeControls.bottom)

            rule.runOnIdle { stage = "active-held" }
            rule.waitForIdle()
            val heldControls = rule.onNodeWithTag("active_call_controls").fetchSemanticsNode().boundsInRoot
            val heldEnd = rule.onNodeWithContentDescription("End call").fetchSemanticsNode().boundsInRoot
            assertTrue("$viewportWidth: held-call controls should follow the Figma hold message",
                heldControls.top >= activeViewport.height * 0.52f && heldControls.top <= activeViewport.height * 0.70f)
            assertTrue("$viewportWidth: held End Call must follow controls",
                heldEnd.top > heldControls.bottom)

            rule.runOnIdle { stage = "active-keypad" }
            rule.waitForIdle()
            val pairedBadge = rule.onNodeWithTag("active_call_status_badge").fetchSemanticsNode().boundsInRoot
            val duration = rule.onNodeWithTag("active_call_duration_compact").fetchSemanticsNode().boundsInRoot
            assertTrue("$viewportWidth: side-by-side badge and duration pill must share a vertical center",
                abs(pairedBadge.center.y - duration.center.y) < with(rule.density) { 1.dp.toPx() })
        }
    }

    @Test fun unknownTransferDoesNotClaimConnectedOrSuccessful() {
        rule.setContent { RasvenTheme { RasvenProductLanguageScope(RasvenLanguage.English) {
            TransferFixture(RasvenTransferUiState.BlindOutcomeUnknown(callContext(), RasvenTransferDestination("پشتیبانی فنی", "305"))) {}
        } } }
        rule.onNodeWithText("Transfer status unknown").assertExists()
        rule.onNodeWithText("Call ended").assertExists()
        rule.onNodeWithText("Connected").assertDoesNotExist()
        rule.onNodeWithTag("transfer_result_success").assertDoesNotExist()
    }

    private fun callContext() = RasvenTransferCallContext(991L, "Alex Taylor", 10_000L, false)

    @Composable private fun TransferFixture(state: RasvenTransferUiState, onSelect: (String) -> Unit) {
        RasvenTransferScreen(state, RasvenAndroidFontFamilies.Persian, RasvenAndroidFontFamilies.English,
            listOf(RasvenTransferDestination("پشتیبانی فنی", "305")), {}, {}, {}, {}, onSelect,
            {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    private fun collectMetrics(fixture: String): JSONArray {
        val metrics = JSONArray()
        val violations = mutableListOf<String>()
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true).fetchSemanticsNodes()
        rule.runOnIdle {
            for (node in nodes) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                for (result in layouts) {
                    val text = result.layoutInput.text.text
                    val advance = (0 until result.lineCount).maxOfOrNull {
                        result.getLineRight(it) - result.getLineLeft(it)
                    } ?: 0f
                    val ellipsized = (0 until result.lineCount).any { result.isLineEllipsized(it) }
                    if (text.any { it in '\u0600'..'\u06ff' }) violations += "Untranslated UI copy: $text"
                    if (result.layoutInput.style.fontFamily != RasvenAndroidFontFamilies.English) violations += "Wrong EN font: $text"
                    if (advance > result.size.width + 1f || result.didOverflowHeight || ellipsized) violations += "Clipped text: $text"
                    metrics.put(JSONObject().put("text", text).put("lines", result.lineCount)
                        .put("x", node.boundsInRoot.left).put("y", node.boundsInRoot.top)
                        .put("width", node.boundsInRoot.width).put("height", node.boundsInRoot.height)
                        .put("rawWidthOverflow", result.didOverflowWidth).put("maxPaintedLineAdvance", advance)
                        .put("sizeWidth", result.size.width).put("heightOverflow", result.didOverflowHeight)
                        .put("ellipsized", ellipsized))
                }
            }
        }
        assertTrue("Empty text audit: $fixture", metrics.length() > 0)
        if (violations.isNotEmpty()) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val output = File(context.getExternalFilesDir(null), "english-call-audit").apply { mkdirs() }
            File(output, "$fixture-failure.json").writeText(metrics.toString(2))
            fail("$fixture: "+violations.joinToString("; "))
        }
        return metrics
    }
}
