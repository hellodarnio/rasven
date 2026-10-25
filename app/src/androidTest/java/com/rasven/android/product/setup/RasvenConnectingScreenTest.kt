package com.rasven.android.product.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import org.junit.Rule
import org.junit.Test

class RasvenConnectingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun content() {
        composeRule.setContent {
            RasvenTheme {
                RasvenConnectingScreen(
                    persianFontFamily = RasvenAndroidFontFamilies.Persian,
                )
            }
        }
    }

    @Test
    fun screenRendersCanonicalRegisteringContent() {
        content()

        composeRule.onNodeWithTag("connecting_heading").assertIsDisplayed()
        composeRule.onNodeWithText("ثبت حساب روی سرور سازمانی").assertIsDisplayed()
        composeRule.onNodeWithTag("connecting_status_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("connecting_registration_card").assertIsDisplayed()
        composeRule.onNodeWithText("در حال ثبت حساب...").assertIsDisplayed()
        composeRule.onNodeWithText("این مرحله معمولاً چند ثانیه طول می‌کشد.").assertIsDisplayed()
    }

    @Test
    fun registeringSurfaceExposesNoUnauthorizedCallToAction() {
        content()

        composeRule.onNodeWithText("لغو").assertDoesNotExist()
        composeRule.onNodeWithText("تلاش مجدد").assertDoesNotExist()
        composeRule.onNodeWithText("ادامه و اتصال").assertDoesNotExist()
        composeRule.onNodeWithText("اتصال موفق").assertDoesNotExist()
        composeRule.onNodeWithText("اتصال برقرار نشد").assertDoesNotExist()
    }

    @Test
    fun loadingIndicatorUsesDedicatedAccessibleSemanticsNode() {
        content()

        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun screenDoesNotReceiveOrRenderCredentialInput() {
        content()

        composeRule.onNodeWithText("رمز عبور").assertDoesNotExist()
        composeRule.onNodeWithTag("advanced_settings_password").assertDoesNotExist()
        composeRule.onNodeWithTag("account_password").assertDoesNotExist()
    }
}
