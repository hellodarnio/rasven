package com.rasven.android.product.setup

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RasvenRegistrationFailureScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun content(
        onRetry: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
    ) {
        composeRule.setContent {
            RasvenTheme {
                RasvenSetupLanguageScope(RasvenLanguage.Persian) {
                    RasvenRegistrationFailureScreen(
                        persianFontFamily = RasvenAndroidFontFamilies.Persian,
                        onRetry = onRetry,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }
        }
    }

    @Test
    fun screenRendersCanonicalFailedContent() {
        content()

        composeRule.onNodeWithTag("registration_failure_heading").assertIsDisplayed()
        composeRule.onNodeWithText("حساب ثبت نشد.").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_failure_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_failure_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_failure_card").assertIsDisplayed()
        composeRule.onNodeWithText("اطلاعات حساب یا شبکه را بررسی کنید").assertIsDisplayed()
        composeRule.onNodeWithText("سرور، پورت، نام کاربری و اتصال اینترنت را دوباره بررسی کنید.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("registration_failure_retry")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag("registration_failure_open_settings")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun retryAndOpenSettingsInvokeDistinctCallbacksExactlyOncePerClick() {
        var retries = 0
        var settings = 0
        content(
            onRetry = { retries += 1 },
            onOpenSettings = { settings += 1 },
        )

        composeRule.onNodeWithTag("registration_failure_retry").performClick()
        composeRule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, settings)
        }

        composeRule.onNodeWithTag("registration_failure_open_settings").performClick()
        composeRule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, settings)
        }
    }

    @Test
    fun failureSurfaceContainsNoRawTechnicalOrCredentialContent() {
        content()

        composeRule.onNodeWithText("رمز عبور").assertDoesNotExist()
        composeRule.onNodeWithTag("account_password").assertDoesNotExist()
        composeRule.onNodeWithTag("advanced_settings_password").assertDoesNotExist()
        composeRule.onNodeWithText("stack trace", substring = true, ignoreCase = true).assertDoesNotExist()
        composeRule.onNodeWithText("IAX2", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("SIP", substring = true).assertDoesNotExist()
    }

    @Test
    fun failureSurfaceDoesNotRenderRegisteringOrRegisteredTerminalActions() {
        content()

        composeRule.onNodeWithTag("connecting_loading_indicator").assertDoesNotExist()
        composeRule.onNodeWithTag("registration_success_heading").assertDoesNotExist()
        composeRule.onNodeWithTag("registration_success_enter_dialer").assertDoesNotExist()
    }
}
