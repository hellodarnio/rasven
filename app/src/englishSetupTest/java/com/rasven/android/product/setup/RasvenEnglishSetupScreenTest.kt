package com.rasven.android.product.setup

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Included only in the QA English-preview build. Pure UI, no networking/phone calls/accounts. */
class RasvenEnglishSetupScreenTest {
    @get:Rule val rule = createComposeRule()
    private val fa = RasvenAndroidFontFamilies.Persian
    private val en = RasvenAndroidFontFamilies.English

    @Before fun requireIsolatedEnglishResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.rasven.android.qa.debug", context.packageName)
        assertTrue(context.resources.getBoolean(R.bool.rasven_english_setup_resources_ready))
    }

    private val screens: List<Pair<String, @Composable () -> Unit>> get() = listOf(
        "welcome" to { RasvenSetupWelcomeScreen(fa, en, {}) },
        "account" to { RasvenAccountInformationScreen(fa, en, {}) },
        "call-settings" to { RasvenCallSettingsScreen(fa, en, {}) },
        "advanced" to { RasvenAdvancedSettingsScreen(RasvenAdvancedSettingsCurrentAccount("qa.invalid", 4569, "204"), fa, en, {}) },
        "permissions-missing" to { RasvenPermissionsEducationScreen(fa, false, {}, {}) },
        "permissions-ready" to { RasvenPermissionsEducationScreen(fa, true, {}, {}) },
        "connecting" to { RasvenConnectingScreen(fa) },
        "success" to { RasvenRegistrationSuccessScreen(fa, {}) },
        "failure" to { RasvenRegistrationFailureScreen(fa, {}, {}) },
    )

    @Test fun captureEveryEnglishSetupStateAt320And360WithProductionFont() {
        val cases = screens
        var selection by mutableStateOf(0)
        var width by mutableStateOf(360)
        rule.setContent {
            KeepSyntheticHostAwake()
            RasvenTheme {
                RasvenSetupLanguageScope(RasvenLanguage.English) {
                    Box(Modifier.width(width.dp).fillMaxHeight()) { key(selection, width) { cases[selection].second() } }
                }
            }
        }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "english-setup-audit").apply { mkdirs() }
        for (w in listOf(320, 360)) for (i in cases.indices) {
            rule.runOnIdle { width = w; selection = i }
            rule.waitForIdle()
            val metrics = JSONArray()
            val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
            rule.runOnIdle {
                nodes.forEach { node ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                    layouts.forEach { result ->
                        assertFalse("Persian fallback in ${cases[i].first}: ${result.layoutInput.text}",
                            result.layoutInput.text.text.any { it in '\u0600'..'\u06ff' })
                        assertTrue("EN must not keep right-aligned product copy",
                            result.layoutInput.style.textAlign != TextAlign.Right)
                        assertEquals(en, result.layoutInput.style.fontFamily)
                        metrics.put(JSONObject().put("text", result.layoutInput.text.text)
                            .put("lines", result.lineCount).put("widthOverflow", result.didOverflowWidth)
                            .put("heightOverflow", result.didOverflowHeight)
                            .put("ellipsized", (0 until result.lineCount).any { result.isLineEllipsized(it) })
                            .put("align", result.layoutInput.style.textAlign.toString())
                            .put("x", node.boundsInRoot.left).put("y", node.boundsInRoot.top)
                            .put("width", node.boundsInRoot.width).put("height", node.boundsInRoot.height))
                    }
                }
            }
            assertTrue(metrics.length() > 0)
            val name = "${cases[i].first}-$w"
            File(output, "$name.json").writeText(metrics.toString(2))
            File(output, "$name.png").outputStream().use {
                assertTrue(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            rule.onNodeWithTag("setup_ambient").assertIsDisplayed()
        }
        // Capture real validation state as well, not just a separate fabricated error screen.
        rule.runOnIdle { selection = 1; width = 320 }
        rule.onNodeWithTag("account_continue").performClick()
        rule.onNodeWithText("Server address is required").assertIsDisplayed()
        File(output, "account-validation-320.png").outputStream().use {
            assertTrue(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun explicitPersianScopeMatchesLegacyPixelsEvenInExternalLtrContainer() {
        val cases = screens
        var selection by mutableStateOf(0)
        var explicit by mutableStateOf(false)
        var width by mutableStateOf(360)
        rule.setContent {
            KeepSyntheticHostAwake()
            val parent = LocalContext.current
            val parentConfiguration = LocalConfiguration.current
            // Legacy Persian must receive its actual FA resource environment, even if the
            // device is English and this QA-only build contains opt-in English resources.
            val faContext = remember(parent, parentConfiguration) {
                parent.createConfigurationContext(Configuration(parentConfiguration).apply { setLocale(Locale("fa")) })
            }
            RasvenTheme {
                CompositionLocalProvider(LocalContext provides faContext,
                    LocalConfiguration provides faContext.resources.configuration,
                    LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.width(width.dp).fillMaxHeight()) {
                        key(selection, explicit, width) {
                            if (explicit) RasvenSetupLanguageScope(RasvenLanguage.Persian) { cases[selection].second() }
                            else cases[selection].second()
                        }
                    }
                }
            }
        }
        for (w in listOf(320, 360)) for (i in cases.indices) {
            rule.runOnIdle { selection = i; width = w; explicit = false }
            rule.waitForIdle()
            val legacy = rule.onRoot().captureToImage().asAndroidBitmap()
            rule.runOnIdle { explicit = true }
            rule.waitForIdle()
            val scoped = rule.onRoot().captureToImage().asAndroidBitmap()
            assertTrue("FA scope changed rendered ${cases[i].first} at $w", legacy.sameAs(scoped))
        }
    }

    @Test fun englishAccountKeepsPhysicalPortLeftLtrValuesAndOwnerUpgradedHeights() {
        var submitted: RasvenValidatedAccount? = null
        rule.setContent {
            RasvenTheme { RasvenSetupLanguageScope(RasvenLanguage.English) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { RasvenAccountInformationScreen(fa, en, { submitted = it }) }
            } }
        }
        val port = rule.onNodeWithTag("account_port_field").fetchSemanticsNode().boundsInRoot
        val user = rule.onNodeWithTag("account_username_field").fetchSemanticsNode().boundsInRoot
        assertTrue("Do not mirror the physical row in EN", port.right < user.left)
        // Port has a two-line helper in EN: its whole decorated field is legitimately taller.
        // Compare server/password, whose helper/label each have one line at this viewport.
        assertTrue("Owner Large server is taller than Medium password with equal helper lines",
            rule.onNodeWithTag("account_server_field").fetchSemanticsNode().boundsInRoot.height >
                rule.onNodeWithTag("account_password_field").fetchSemanticsNode().boundsInRoot.height)
        rule.onNodeWithTag("account_server").performClick().performTextInput("qa.invalid")
        rule.onNodeWithTag("account_server").performImeAction()
        rule.onNodeWithTag("account_port").assertIsFocused().performTextInput("4569")
        rule.onNodeWithTag("account_port").performImeAction()
        rule.onNodeWithTag("account_username").assertIsFocused().performTextInput("204")
        rule.onNodeWithTag("account_username").performImeAction()
        rule.onNodeWithTag("account_password").assertIsFocused().performTextInput("fixture-only-password")
        rule.onNodeWithTag("account_password").performImeAction()
        rule.onNodeWithTag("account_continue").performClick()
        rule.runOnIdle { assertEquals("204", submitted?.username); assertEquals(4569, submitted?.port) }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("account_server").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(TextDirection.Ltr, layouts.single().layoutInput.style.textDirection)
        assertEquals(TextAlign.Left, layouts.single().layoutInput.style.textAlign)
    }

    @Test fun englishAdvancedSaveNeverRecoversStoredPassword() {
        var saved: RasvenAdvancedSettingsUpdate? = null
        rule.setContent {
            RasvenTheme { RasvenSetupLanguageScope(RasvenLanguage.English) {
                RasvenAdvancedSettingsScreen(RasvenAdvancedSettingsCurrentAccount("qa.invalid", 4569, "204"), fa, en, { saved = it })
            } }
        }
        rule.onNodeWithTag("advanced_settings_password").assertTextContains("")
        rule.onNodeWithTag("advanced_settings_save").performClick()
        rule.runOnIdle { assertNotNull(saved); assertNull(saved!!.passwordReplacement) }
        rule.onNodeWithText("Your saved password is never displayed. Enter a replacement only to change it.").assertIsDisplayed()
    }

    @Test fun requiredPermissionActionsRemainSeparateWithoutOptionalSkipConnection() {
        var settings = 0
        var request = 0
        rule.setContent {
            RasvenTheme { RasvenSetupLanguageScope(RasvenLanguage.English) {
                RasvenPermissionsEducationScreen(fa, false, { settings++ }, { request++ })
            } }
        }
        rule.onNodeWithText("Required: microphone for two-way calls").assertIsDisplayed()
        rule.onNodeWithText("Required: call notifications and incoming-call display").assertIsDisplayed()
        rule.onNodeWithText("Optional: contacts for displaying names").assertIsDisplayed()
        rule.onNodeWithTag("permissions_open_required_settings").performClick()
        rule.runOnIdle { assertEquals(1, settings); assertEquals(0, request) }
        rule.onNodeWithTag("permissions_continue_connect").performClick()
        rule.runOnIdle { assertEquals(1, settings); assertEquals(1, request) }
    }

    @Composable private fun KeepSyntheticHostAwake() {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
    }
}
