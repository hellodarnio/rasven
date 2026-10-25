package com.rasven.android.product.setup

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.RasvenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasvenSetupWelcomeScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun persianFrame_hasFigmaCopyAndAction() {
        var starts = 0
        composeRule.setContent {
            RasvenTheme {
                RasvenSetupLanguageScope(RasvenLanguage.Persian) {
                    RasvenSetupWelcomeScreen(FontFamily.Default, FontFamily.Default, { starts++ })
                }
            }
        }
        composeRule.onNode(hasText("خط کاری‌ات، همراهت.") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("با حساب تلفنی سازمانت تماس بگیر و پاسخ بده.").assertIsDisplayed()
        composeRule.onNodeWithText("برای شروع، اطلاعات حساب سازمانی رو آماده داشته باش.").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_continue").assertIsDisplayed().assertHasClickAction().performClick()
        composeRule.runOnIdle { assertEquals(1, starts) }
    }

    @Test fun englishFrame_hasFigmaCopyAndSwitchesToPersian() {
        var selected: RasvenLanguage? = null
        composeRule.setContent {
            RasvenTheme {
                RasvenSetupLanguageScope(RasvenLanguage.English) {
                    RasvenSetupWelcomeScreen(FontFamily.Default, FontFamily.Default, {},
                        onSelectLanguage = { selected = it })
                }
            }
        }
        composeRule.onNodeWithText("Your work line,within reach.").assertIsDisplayed()
        composeRule.onNodeWithText("Make and receive calls with your organization's phone account.").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_language_button").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(RasvenLanguage.Persian, selected) }
    }

    @Test fun persianLanguageAction_selectsEnglish() {
        var selected: RasvenLanguage? = null
        composeRule.setContent {
            RasvenTheme {
                RasvenSetupLanguageScope(RasvenLanguage.Persian) {
                    RasvenSetupWelcomeScreen(FontFamily.Default, FontFamily.Default, {},
                        onSelectLanguage = { selected = it })
                }
            }
        }
        composeRule.onNodeWithTag("welcome_language_button").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(RasvenLanguage.English, selected) }
    }

    @Test fun persianHero_keepsFigmaPhysicalAssetSlots() = assertHeroAssetSlots(RasvenLanguage.Persian)

    @Test fun englishHero_keepsFigmaPhysicalAssetSlots() = assertHeroAssetSlots(RasvenLanguage.English)

    private fun assertHeroAssetSlots(language: RasvenLanguage) {
        composeRule.setContent {
            RasvenTheme {
                RasvenSetupLanguageScope(language) {
                    RasvenSetupWelcomeScreen(FontFamily.Default, FontFamily.Default, {})
                }
            }
        }
        val hero = composeRule.onNodeWithTag("welcome_hero").fetchSemanticsNode().boundsInRoot
        val slots = listOf(
            AssetSlot("welcome_halo", 8f, -6f, 264f, 264f),
            AssetSlot("welcome_orbit_outer", 25f, 11f, 230f, 230f),
            AssetSlot("welcome_orbit_inner", 47f, 33f, 186f, 186f),
            AssetSlot("welcome_dot_left", 24f, 155f, 14f, 14f),
            AssetSlot("welcome_dot_right", 245f, 73f, 10f, 10f),
            AssetSlot("welcome_symbol_card", 74f, 59f, 132f, 132f),
            AssetSlot("welcome_symbol", 108f, 93.78f, 64f, 62.44f),
        )
        for (slot in slots) {
            val actual = composeRule.onNodeWithTag(slot.tag).fetchSemanticsNode().boundsInRoot
            with(composeRule.density) {
                assertEquals("${language.name} ${slot.tag} left", slot.x, (actual.left - hero.left).toDp().value, 1f)
                assertEquals("${language.name} ${slot.tag} top", slot.y, (actual.top - hero.top).toDp().value, 1f)
                assertEquals("${language.name} ${slot.tag} width", slot.width, actual.width.toDp().value, 1f)
                assertEquals("${language.name} ${slot.tag} height", slot.height, actual.height.toDp().value, 1f)
            }
        }
    }

    private data class AssetSlot(val tag: String, val x: Float, val y: Float, val width: Float, val height: Float)
}
