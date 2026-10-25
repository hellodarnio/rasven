package com.rasven.android.product.setup

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RasvenRegistrationSuccessScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun content(onEnterDialer: () -> Unit = {}) {
        composeRule.setContent {
            RasvenTheme {
                RasvenRegistrationSuccessScreen(
                    persianFontFamily = RasvenAndroidFontFamilies.Persian,
                    onEnterDialer = onEnterDialer,
                )
            }
        }
    }

    @Test
    fun screenRendersCanonicalRegisteredContent() {
        content()

        composeRule.onNodeWithTag("registration_success_heading").assertIsDisplayed()
        composeRule.onNodeWithText("حساب با موفقیت ثبت شد.").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_success_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_success_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_success_card").assertIsDisplayed()
        composeRule.onNodeWithText("خط سازمانی آماده است").assertIsDisplayed()
        composeRule.onNodeWithText("اکنون تماس‌های ورودی و خروجی از طریق حساب سازمانی انجام می‌شوند.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("registration_success_enter_dialer")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun enterDialerInvokesBoundedCallbackExactlyOncePerClick() {
        var count = 0
        content { count += 1 }

        composeRule.onNodeWithTag("registration_success_enter_dialer").performClick()
        composeRule.runOnIdle { assertEquals(1, count) }
    }

    @Test
    fun successSurfaceContainsNoFailureOrUnauthorizedSetupActions() {
        content()

        composeRule.onNodeWithText("تلاش مجدد").assertDoesNotExist()
        composeRule.onNodeWithText("لغو").assertDoesNotExist()
        composeRule.onNodeWithText("ادامه و اتصال").assertDoesNotExist()
        composeRule.onNodeWithText("در حال ثبت حساب...").assertDoesNotExist()
    }

    @Test
    fun successSurfaceDoesNotReceiveOrRenderCredentialInput() {
        content()

        composeRule.onNodeWithText("رمز عبور").assertDoesNotExist()
        composeRule.onNodeWithTag("advanced_settings_password").assertDoesNotExist()
        composeRule.onNodeWithTag("account_password").assertDoesNotExist()
    }
}
