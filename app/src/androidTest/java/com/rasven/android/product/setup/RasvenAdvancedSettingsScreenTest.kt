package com.rasven.android.product.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasvenAdvancedSettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val current = RasvenAdvancedSettingsCurrentAccount(
        server = "pbx.current.example",
        port = 4569,
        username = "204",
    )

    @Composable
    private fun content(onSave: (RasvenAdvancedSettingsUpdate) -> Unit = {}) {
        RasvenTheme {
            RasvenAdvancedSettingsScreen(
                currentAccount = current,
                persianFontFamily = RasvenAndroidFontFamilies.Persian,
                englishFontFamily = FontFamily.Default,
                onSaveChanges = onSave,
            )
        }
    }

    @Test fun screenRendersCanonicalAdvancedSettingsContentAndCurrentNonSecretValues() {
        composeRule.setContent { content() }
        composeRule.onNode(hasText("تنظیمات پیشرفته") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("قابل دسترسی؛ اطلاعات حساس محافظت می‌شوند.").assertIsDisplayed()
        composeRule.onNodeWithText("رمز عبور برای کاربر قابل مشاهده نیست و فقط مقدار جایگزین نمایش داده می‌شود.").assertIsDisplayed()
        composeRule.onNodeWithTag("advanced_settings_security_note").assertIsDisplayed()
        composeRule.onNodeWithTag("advanced_settings_server").assertTextContains("pbx.current.example")
        composeRule.onNodeWithTag("advanced_settings_port").assertTextContains("4569")
        composeRule.onNodeWithTag("advanced_settings_username").assertTextContains("204")
        composeRule.onNodeWithText("برای تغییر، رمز جدید وارد کنید").assertIsDisplayed()
        composeRule.onNodeWithTag("advanced_settings_save").assertHasClickAction()
    }

    @Test fun passwordStartsAsEmptyReplacementAndNeverUsesFakeStoredLengthBullets() {
        composeRule.setContent { content() }
        composeRule.onNodeWithTag("advanced_settings_password").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Password),
        )
        composeRule.onNodeWithText("••••••••••••").assertDoesNotExist()
    }

    @Test fun passwordReplacementHasPasswordSemanticsAndNoSecretInContentDescription() {
        val secret = "replacement-secret"
        composeRule.setContent { content() }
        composeRule.onNodeWithTag("advanced_settings_password").performTextInput(secret)
        val config = composeRule.onNodeWithTag("advanced_settings_password").fetchSemanticsNode().config
        assertTrue(config.contains(SemanticsProperties.Password))
        val descriptions: List<String> = if (config.contains(SemanticsProperties.ContentDescription)) {
            config[SemanticsProperties.ContentDescription]
        } else {
            emptyList()
        }
        assertTrue(descriptions.none { it.contains(secret) })
    }

    @Test fun emptyReplacementSavesNonSecretChangesExactlyOnceWithoutPasswordChange() {
        var count = 0
        var received: RasvenAdvancedSettingsUpdate? = null
        composeRule.setContent { content { update -> count++; received = update } }
        composeRule.onNodeWithTag("advanced_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(1, count)
            assertEquals("pbx.current.example", received?.server)
            assertEquals(4569, received?.port)
            assertEquals("204", received?.username)
            assertNull(received?.passwordReplacement)
        }
    }

    @Test fun typedReplacementIsMaskedAndForwardedExactlyOnce() {
        val replacement = "new-pass-42"
        var count = 0
        var received: String? = null
        composeRule.setContent { content { update -> count++; received = update.passwordReplacement } }
        composeRule.onNodeWithTag("advanced_settings_password").performTextInput(replacement)
        composeRule.onNodeWithTag("advanced_settings_password").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Password),
        )
        composeRule.onNodeWithTag("advanced_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(1, count)
            assertEquals(replacement, received)
        }
    }

    @Test fun invalidDataBlocksSaveAndShowsApprovedValidationCopy() {
        var count = 0
        composeRule.setContent { content { count++ } }
        composeRule.onNodeWithTag("advanced_settings_server").performTextClearance()
        composeRule.onNodeWithTag("advanced_settings_port").performTextReplacement("70000")
        composeRule.onNodeWithTag("advanced_settings_username").performTextClearance()
        composeRule.onNodeWithTag("advanced_settings_save").performClick()
        composeRule.onNodeWithText("آدرس سرور الزامی است").assertIsDisplayed()
        composeRule.onNodeWithText("پورت باید بین 1 تا 65535 باشد").assertIsDisplayed()
        composeRule.onNodeWithText("نام کاربری یا داخلی الزامی است").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, count) }
    }

    @Test fun focusAndImeFollowPreviouslyClosedServerUsernamePortPasswordChain() {
        composeRule.setContent { content() }
        composeRule.onNodeWithTag("advanced_settings_server").performClick()
        composeRule.onNodeWithTag("advanced_settings_server").assertIsFocused()
        composeRule.onNodeWithTag("advanced_settings_server").performImeAction()
        composeRule.onNodeWithTag("advanced_settings_username").assertIsFocused()
        composeRule.onNodeWithTag("advanced_settings_username").performImeAction()
        composeRule.onNodeWithTag("advanced_settings_port").assertIsFocused()
        composeRule.onNodeWithTag("advanced_settings_port").performImeAction()
        composeRule.onNodeWithTag("advanced_settings_password").assertIsFocused()
        composeRule.onNodeWithTag("advanced_settings_password").performImeAction()
        composeRule.onNodeWithTag("advanced_settings_save").assertHasClickAction()
    }

    @Test fun technicalValuesRemainEditableAsLatinNumericDataInsideRtlScreen() {
        composeRule.setContent { content() }
        composeRule.onNodeWithTag("advanced_settings_server").performTextReplacement("198.51.100.24")
        composeRule.onNodeWithTag("advanced_settings_server").assertTextContains("198.51.100.24")
        composeRule.onNodeWithTag("advanced_settings_username").performTextReplacement("agent204")
        composeRule.onNodeWithTag("advanced_settings_username").assertTextContains("agent204")
        composeRule.onNodeWithTag("advanced_settings_port").performTextReplacement("5060")
        composeRule.onNodeWithTag("advanced_settings_port").assertTextContains("5060")
    }

    @Test fun passwordAlignmentPreservesClosedRtlLtrContract() {
        assertEquals(TextAlign.Right, RasvenAdvancedSettingsContract.passwordValueAlignment(LayoutDirection.Rtl))
        assertEquals(TextAlign.Left, RasvenAdvancedSettingsContract.passwordValueAlignment(LayoutDirection.Ltr))
    }

    @Test fun ownerTextFieldOverrideMakesServerTallerThanRowFieldsWithoutChangingRootDs() {
        composeRule.setContent { content() }
        val server = composeRule.onNodeWithTag("advanced_settings_server_field").fetchSemanticsNode().boundsInRoot
        val username = composeRule.onNodeWithTag("advanced_settings_username_field").fetchSemanticsNode().boundsInRoot
        val port = composeRule.onNodeWithTag("advanced_settings_port_field").fetchSemanticsNode().boundsInRoot
        assertTrue("Large server consumer must be taller than Medium username consumer", server.height > username.height)
        assertEquals("Medium username and port consumers must match", username.height, port.height, 1f)
    }

    @Test fun compact320DpViewportKeepsSecurityFormAndCtaInsideBounds() {
        composeRule.setContent {
            Box(Modifier.width(320.dp).fillMaxHeight().testTag("advanced_320_viewport")) {
                content()
            }
        }
        val viewport = composeRule.onNodeWithTag("advanced_320_viewport").fetchSemanticsNode().boundsInRoot
        for (tag in listOf(
            "advanced_settings_security_note",
            "advanced_settings_server_field",
            "advanced_settings_port_field",
            "advanced_settings_username_field",
            "advanced_settings_password_field",
            "advanced_settings_save",
        )) {
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag starts inside 320dp viewport", bounds.left >= viewport.left - 1f)
            assertTrue("$tag ends inside 320dp viewport", bounds.right <= viewport.right + 1f)
        }
        composeRule.onNodeWithTag("advanced_settings_save").assertIsDisplayed()
    }

    @Test fun portRemainsPhysicalLeftOfFlexibleUsernameInRtlRow() {
        composeRule.setContent { content() }
        val portLeft = composeRule.onNodeWithTag("advanced_settings_port").fetchSemanticsNode().boundsInRoot.left
        val usernameLeft = composeRule.onNodeWithTag("advanced_settings_username").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Port should stay at the physical left of Username", portLeft < usernameLeft)
    }
}
