package com.rasven.android.product

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.R
import com.rasven.android.debug.RasvenEnglishDailyFixture
import com.rasven.android.debug.englishDailyFixtureStages
import com.rasven.android.designsystem.foundation.*
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Prepared, not automatically executed: permission from Amir is required before phone testing. */
class RasvenEnglishDailyScreenTest {
    @get:Rule val rule = createComposeRule()

    @Before fun requireOfflineQaOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.rasven.android.qa.debug", context.packageName)
        assertTrue(context.resources.getBoolean(R.bool.rasven_english_setup_resources_ready))
    }

    @Test fun captureSixteenEnglishDailyStatesAt320And360WithoutChangingFontScale() {
        var stage by mutableStateOf("home")
        var width by mutableStateOf(360)
        rule.setContent {
            ScopedAwake()
            RasvenTheme { RasvenProductLanguageScope(RasvenLanguage.English) {
                Box(Modifier.width(width.dp).fillMaxHeight()) { key(stage, width) { RasvenEnglishDailyFixture(stage) } }
            } }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "english-daily-audit").apply { mkdirs() }
        for (w in listOf(320, 360)) for (fixture in englishDailyFixtureStages) {
            rule.runOnIdle { width = w; stage = fixture }
            rule.waitForIdle()
            val name = "$fixture-$w"
            File(output, "$name.png").outputStream().use {
                assertTrue(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val metrics = collectEnglishMetrics(fixture)
            File(output, "$name.json").writeText(metrics.toString(2))
            assertNavigationLiftAndContentSeparation(fixture)
            rule.onNodeWithTag("daily_ambient").assertIsDisplayed()
            // Synthetic runtime fixture guarantees no real account/transport operations.
            if (fixture == "settings") rule.onNodeWithTag("settings_row_disconnect").assertIsDisplayed()
            if (fixture == "settings-advanced") {
                rule.onNodeWithTag("settings_transfer_restore_defaults").performScrollTo().assertIsDisplayed()
                File(output, "$fixture-scrolled-$w.json").writeText(collectEnglishMetrics("$fixture-scrolled").toString(2))
                File(output, "$fixture-scrolled-$w.png").outputStream().use {
                    assertTrue(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            if (fixture.startsWith("history")) {
                // Horizontal filter scrolling is intentional; offscreen semantics bounds
                // are empty, not clipped text. Verify the last chip can really be reached.
                rule.onNodeWithTag("history_filter_missed").performScrollTo().assertIsDisplayed()
                File(output, "$fixture-filter-scrolled-$w.json").writeText(collectEnglishMetrics("$fixture-filter-scrolled").toString(2))
                File(output, "$fixture-filter-scrolled-$w.png").outputStream().use {
                    assertTrue(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
        }
    }

    @Test fun ownerHomeNumberBackspaceAndDndCallbacksSurviveEnglishPresentation() {
        rule.setContent { RasvenTheme { RasvenProductLanguageScope(RasvenLanguage.English) {
            Box(Modifier.width(360.dp).fillMaxHeight()) { RasvenEnglishDailyFixture("home-number") }
        } } }
        val label = rule.onNodeWithTag("home_line_title").fetchSemanticsNode().boundsInRoot
        val badge = rule.onNodeWithTag("home_connected_badge").fetchSemanticsNode().boundsInRoot
        assertTrue("English title must be physically left of status", label.left < badge.left)
        rule.onNodeWithTag("home_dnd_switch").assertIsOff().performClick().assertIsOn()
        val input = rule.onNodeWithTag("home_destination_input")
        val layouts = mutableListOf<TextLayoutResult>()
        input.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("305", layouts.single().layoutInput.text.text)
        assertEquals(TextAlign.Left, layouts.single().layoutInput.style.textAlign)
        assertEquals(TextDirection.Ltr, layouts.single().layoutInput.style.textDirection)
        assertEquals(24.sp, layouts.single().layoutInput.style.fontSize)
        val delete = rule.onNodeWithTag("home_delete")
        assertTrue("Owner backspace stays at the physical right", delete.fetchSemanticsNode().boundsInRoot.center.x > input.fetchSemanticsNode().boundsInRoot.center.x)
        delete.performClick()
        input.assertTextContains("30")
        rule.onNodeWithTag("home_key_1").performClick()
        input.assertTextContains("301")
        delete.performTouchInput { longClick() }
        input.assertTextContains("")
        rule.onNodeWithTag("home_call_action").assertIsNotEnabled()
    }

    @Test fun languagePickerSwitchesUiLocalsAndKeepsDialogOpenWhenSaveFails() {
        var language by mutableStateOf(RasvenLanguage.English)
        var allowSave = true
        var changes = 0
        rule.setContent { RasvenTheme { RasvenProductLanguageScope(language) {
            RasvenEnglishDailyFixture("settings", onSelectLanguage = { requested ->
                changes++
                if (allowSave) language = requested
                allowSave
            })
        } } }
        rule.onNodeWithTag("settings_row_language").performClick()
        rule.onNodeWithTag("settings_language_dialog").assertIsDisplayed()
        rule.onNodeWithTag("settings_language_choice_fa").performClick()
        rule.onNodeWithTag("settings_language_dialog").assertDoesNotExist()
        rule.runOnIdle { assertEquals(RasvenLanguage.Persian, language); assertEquals(1, changes); allowSave = false }
        rule.onNodeWithTag("settings_row_language").performClick()
        rule.onNodeWithTag("settings_language_choice_en").performClick()
        rule.onNodeWithTag("settings_language_dialog").assertIsDisplayed()
        rule.runOnIdle { assertEquals(RasvenLanguage.Persian, language); assertEquals(2, changes); allowSave = true }
        rule.onNodeWithTag("settings_language_choice_en").performClick()
        rule.onNodeWithTag("settings_language_dialog").assertDoesNotExist()
        rule.runOnIdle { assertEquals(RasvenLanguage.English, language); assertEquals(3, changes) }
    }

    @Test fun explicitPersianDailyScopeMatchesDefaultFaResourcesInLtrGeometryHost() {
        var stage by mutableStateOf("home")
        var explicit by mutableStateOf(false)
        var width by mutableStateOf(360)
        rule.setContent {
            ScopedAwake()
            val parent = LocalContext.current
            val configuration = LocalConfiguration.current
            val fa = remember(parent, configuration) {
                parent.createConfigurationContext(Configuration(configuration).apply { setLocale(Locale("fa")) })
            }
            RasvenTheme { CompositionLocalProvider(LocalContext provides fa,
                LocalConfiguration provides fa.resources.configuration, LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.width(width.dp).fillMaxHeight()) { key(stage, explicit, width) {
                    if (explicit) RasvenProductLanguageScope(RasvenLanguage.Persian) { RasvenEnglishDailyFixture(stage) }
                    else RasvenEnglishDailyFixture(stage)
                } }
            } }
        }
        for (w in listOf(320, 360)) for (fixture in englishDailyFixtureStages) {
            rule.runOnIdle { width = w; stage = fixture; explicit = false }
            rule.waitForIdle()
            val defaultFa = rule.onRoot().captureToImage().asAndroidBitmap()
            rule.runOnIdle { explicit = true }
            rule.waitForIdle()
            assertTrue("Explicit FA differs from default FA: $fixture / $w",
                defaultFa.sameAs(rule.onRoot().captureToImage().asAndroidBitmap()))
        }
    }

    private fun collectEnglishMetrics(fixture: String): JSONArray {
        val metrics = JSONArray()
        val violations = mutableListOf<String>()
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        rule.runOnIdle {
            for (node in nodes) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                for (result in layouts) {
                    val text = result.layoutInput.text.text
                    val lineLeft = (0 until result.lineCount).minOfOrNull { result.getLineLeft(it) } ?: 0f
                    val lineRight = (0 until result.lineCount).maxOfOrNull { result.getLineRight(it) } ?: 0f
                    val ellipsized = (0 until result.lineCount).any { result.isLineEllipsized(it) }
                    // The real device returns the tight measured text size but a cached
                    // paragraph's allocated parent width (e.g.383 vs504 for Business line).
                    // Center alignment also retains that paragraph's origin. Spare layout
                    // allocation is not clipped ink: compare painted line advances to the
                    // measured result AND actual semantics bounds, retaining raw diagnostics.
                    val maxLineAdvance = (0 until result.lineCount).maxOfOrNull {
                        result.getLineRight(it) - result.getLineLeft(it)
                    } ?: 0f
                    // Do not compare to boundsInRoot: a legitimate scrollable chip/list
                    // can be outside the viewport and have clipped/empty semantics bounds.
                    // Its own measured text must fit; explicit critical control/list bounds
                    // are checked separately against the usable viewport/navigation below.
                    val widthFits = maxLineAdvance <= result.size.width + 1f
                    if (text.any { it in '\u0600'..'\u06ff' }) violations += "Untranslated UI copy: $text"
                    if (result.layoutInput.style.fontFamily != RasvenAndroidFontFamilies.English) violations += "Wrong EN font: $text"
                    if (!widthFits) violations += "Width clipping: $text; size=${result.size.width}; paragraph=${result.multiParagraph.width}; lines=[$lineLeft,$lineRight]"
                    if (result.didOverflowHeight) violations += "Height overflow: $text"
                    if (ellipsized) violations += "Ellipsized text: $text"
                    metrics.put(JSONObject().put("text", result.layoutInput.text.text).put("lines", result.lineCount)
                        .put("x", node.boundsInRoot.left).put("y", node.boundsInRoot.top)
                        .put("width", node.boundsInRoot.width).put("height", node.boundsInRoot.height)
                        .put("align", result.layoutInput.style.textAlign.toString())
                        .put("rawWidthOverflow", result.didOverflowWidth).put("sizeWidth", result.size.width)
                        .put("paragraphWidth", result.multiParagraph.width).put("lineLeft", lineLeft).put("lineRight", lineRight)
                        .put("maxPaintedLineAdvance", maxLineAdvance)
                        .put("widthFitsPhysicalPixelTolerance", widthFits).put("heightOverflow", result.didOverflowHeight)
                        .put("ellipsized", ellipsized))
                }
            }
        }
        assertTrue("Empty text audit in $fixture", metrics.length() > 0)
        if (violations.isNotEmpty()) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val output = File(context.getExternalFilesDir(null), "english-daily-audit").apply { mkdirs() }
            File(output, "$fixture-failure.json").writeText(metrics.toString(2))
            fail("$fixture: ${violations.joinToString("; ")}")
        }
        return metrics
    }

    private fun assertNavigationLiftAndContentSeparation(fixture: String) {
        val viewport = rule.onNodeWithTag("daily_usable_viewport").fetchSemanticsNode().boundsInRoot
        val prefix = when {
            fixture.startsWith("home") -> "home"
            fixture.startsWith("contacts") -> "contacts"
            fixture.startsWith("history") -> "history"
            else -> "settings"
        }
        val navContainer = rule.onNodeWithTag("${prefix}_bottom_navigation").fetchSemanticsNode()
        // The tagged outer modifier includes bottom padding; the first selectable Tab
        // gives the physical bar content edge. A tab has4dp bottom inset in the72dp bar.
        val tabs = rule.onAllNodes(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab
        )).fetchSemanticsNodes()
        assertEquals(4, tabs.size)
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val physicalBarBottom = tabs.maxOf { it.boundsInRoot.bottom } + 4f * density
        assertEquals("Navigation must align to the Figma safe-area baseline: $fixture",
            0f, viewport.bottom - physicalBarBottom, 1.5f)
        assertTrue(navContainer.boundsInRoot.bottom <= viewport.bottom + 1f)
        val barTop = physicalBarBottom - 72f * density
        val actionTag = when (fixture) {
            "home", "home-dnd", "home-number", "home-dnd-number" -> "home_call_action"
            "home-disconnected" -> "home_reconnect"
            "contacts-permission" -> "contacts_permission_cta"
            "settings-prefix" -> "settings_prefix_save"
            "settings-notifications" -> "settings_notifications_cta"
            "settings-advanced" -> "settings_advanced_save"
            else -> null
        }
        if (actionTag != null) {
            val action = rule.onNodeWithTag(actionTag).fetchSemanticsNode().boundsInRoot
            assertTrue("Action overlaps navigation: $fixture", action.bottom <= barTop - 15f * density)
        }
        if (fixture.startsWith("history") || fixture == "contacts") {
            val list = rule.onNodeWithTag(if (fixture == "contacts") "contacts_list" else "history_list").fetchSemanticsNode().boundsInRoot
            assertTrue("List extends under navigation: $fixture", list.bottom <= barTop - 15f * density)
        }
    }

    @Composable private fun ScopedAwake() {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
    }
}
