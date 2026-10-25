package com.rasven.android.product.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasvenCallSettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Composable
    private fun content(onSaveAndConnect: (String) -> Unit = {}) {
        RasvenTheme {
            RasvenCallSettingsScreen(
                persianFontFamily = RasvenAndroidFontFamilies.Persian,
                englishFontFamily = FontFamily.Default,
                onSaveAndConnect = onSaveAndConnect,
            )
        }
    }

    @Test fun callSettingsRendersCanonicalContentAndOfficialControl() {
        composeRule.setContent { content() }
        composeRule.onNode(hasText("تنظیمات تماس") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("رفتار شماره‌گیری و انتقال تماس").assertIsDisplayed()
        composeRule.onNodeWithText("پیش‌شماره مخاطبین").assertIsDisplayed()
        composeRule.onNodeWithText("فقط هنگام تماس از مخاطبین گوشی اضافه می‌شود").assertIsDisplayed()
        composeRule.onNodeWithText("انتقال تماس").assertIsDisplayed()
        composeRule.onNodeWithText("انتقال مستقیم و مشاوره‌ای از کنترل‌های داخل تماس اجرا می‌شوند.").assertIsDisplayed()
        composeRule.onNodeWithTag("call_settings_contact_prefix").assertExists()
        composeRule.onNodeWithTag("call_settings_transfer_card").assertExists()
        composeRule.onNodeWithTag("call_settings_save_connect").assertHasClickAction()
    }

    @Test fun optionalPrefixStartsEmptyEvenThoughFigmaShowsExampleNine() {
        var callbackCount = 0
        var received = "UNSET"
        composeRule.setContent { content { prefix -> callbackCount++; received = prefix } }
        composeRule.onNodeWithTag("call_settings_save_connect").performClick()
        composeRule.runOnIdle {
            assertEquals(1, callbackCount)
            assertEquals("", received)
        }
    }

    @Test fun typedContactPrefixIsPreservedUntilExplicitCallback() {
        var callbackCount = 0
        var received = "UNSET"
        composeRule.setContent { content { prefix -> callbackCount++; received = prefix } }
        composeRule.onNodeWithTag("call_settings_contact_prefix").performTextInput("09")
        composeRule.onNodeWithTag("call_settings_contact_prefix").assertTextContains("09")
        composeRule.runOnIdle { assertEquals(0, callbackCount) }
        composeRule.onNodeWithTag("call_settings_save_connect").performClick()
        composeRule.runOnIdle {
            assertEquals(1, callbackCount)
            assertEquals("09", received)
        }
    }

    @Test fun editablePrefixCanReceiveFocusAndImeDoneLeavesCtaAvailable() {
        composeRule.setContent { content() }
        composeRule.onNodeWithTag("call_settings_contact_prefix").performClick()
        composeRule.onNodeWithTag("call_settings_contact_prefix").assertIsFocused()
        composeRule.onNodeWithTag("call_settings_contact_prefix").performTextInput("9")
        composeRule.onNodeWithTag("call_settings_contact_prefix").performImeAction()
        composeRule.onNodeWithTag("call_settings_save_connect").assertHasClickAction()
    }

    @Test fun technicalValueAlignmentFollowsInterfaceButDirectionRemainsLtr() {
        assertEquals(TextAlign.Right, RasvenCallSettingsContract.prefixAlignment(LayoutDirection.Rtl))
        assertEquals(TextAlign.Left, RasvenCallSettingsContract.prefixAlignment(LayoutDirection.Ltr))
        composeRule.setContent { content() }
        composeRule.onNodeWithTag("call_settings_contact_prefix").performTextInput("9")
        composeRule.onNodeWithTag("call_settings_contact_prefix").assertTextContains("9")
    }

    @Test fun upgradedPrefixFieldFitsCompactViewportAndPreservesCta() {
        composeRule.setContent {
            Box(Modifier.width(320.dp).fillMaxHeight().testTag("call_320_viewport")) {
                content()
            }
        }
        val viewport = composeRule.onNodeWithTag("call_320_viewport").fetchSemanticsNode().boundsInRoot
        val field = composeRule.onNodeWithTag("call_settings_prefix_field").fetchSemanticsNode().boundsInRoot
        val card = composeRule.onNodeWithTag("call_settings_transfer_card").fetchSemanticsNode().boundsInRoot
        assertTrue("prefix begins inside viewport", field.left >= viewport.left - 1f)
        assertTrue("prefix ends inside viewport", field.right <= viewport.right + 1f)
        assertTrue("increased field cannot collide with transfer card", field.bottom < card.top)
        composeRule.onNodeWithTag("call_settings_contact_prefix").performTextInput("09")
        composeRule.onNodeWithTag("call_settings_contact_prefix").assertTextContains("09")
        composeRule.onNodeWithTag("call_settings_save_connect").assertIsDisplayed()
    }
}
