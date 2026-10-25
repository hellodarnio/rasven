package com.rasven.android.product.call

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.RasvenProductLanguageScope
import com.rasven.android.designsystem.foundation.RasvenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RasvenOutgoingCallScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val attempt = RasvenOutgoingCallAttempt(
        id = 1L,
        destination = "09121234567",
        origin = RasvenOutgoingCallOrigin.HomeDirectDial,
    )

    private fun render(
        state: RasvenOutgoingCallState,
        onPermission: () -> Unit = {},
        onClosePermission: () -> Unit = {},
        onCancel: () -> Unit = {},
        onRetry: () -> Unit = {},
        onCloseFailure: () -> Unit = {},
        modifier: Modifier = Modifier,
    ) {
        composeRule.setContent {
            RasvenTheme {
                RasvenProductLanguageScope(RasvenLanguage.Persian) {
                    RasvenOutgoingCallScreen(
                        state = state,
                        persianFontFamily = FontFamily.Default,
                        onPermissionRequest = onPermission,
                        onClosePermission = onClosePermission,
                        onCancel = onCancel,
                        onRetry = onRetry,
                        onCloseFailure = onCloseFailure,
                        modifier = modifier,
                    )
                }
            }
        }
    }

    @Test fun microphoneFrameShowsExactCopyAndActionsWithoutAutoRequest() {
        var requests = 0
        render(
            state = RasvenOutgoingCallState.AwaitingMicrophonePermission(
                destination = "09121234567",
                origin = RasvenOutgoingCallOrigin.HomeDirectDial,
            ),
            onPermission = { requests += 1 },
        )
        composeRule.onNodeWithText("دسترسی میکروفن لازم است").assertIsDisplayed()
        composeRule.onNodeWithText("بدون این مجوز تماس شروع نمی‌شود.").assertIsDisplayed()
        composeRule.onNodeWithText("اجازه میکروفن داده نشده").assertIsDisplayed()
        composeRule.onNodeWithText("برای برقراری تماس، دسترسی میکروفن را فعال کنید.").assertIsDisplayed()
        composeRule.onNodeWithTag("outgoing_permission_cta").assertIsDisplayed()
        composeRule.onNodeWithTag("outgoing_permission_close").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, requests) }
        composeRule.onNodeWithTag("outgoing_permission_cta").performClick()
        composeRule.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun dialingFrameShowsDestinationWaitingCopyAndCancelOnce() {
        var cancels = 0
        render(RasvenOutgoingCallState.Dialing(attempt), onCancel = { cancels += 1 })
        composeRule.onNodeWithText("در حال تماس").assertIsDisplayed()
        composeRule.onNodeWithText("۰۹۱۲ ۱۲۳ ۴۵۶۷").assertIsDisplayed()
        composeRule.onNodeWithText("در انتظار پاسخ مقصد").assertIsDisplayed()
        composeRule.onNodeWithTag("outgoing_cancel_action").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, cancels) }
    }

    @Test fun busyFrameShowsDistinctCopyRetryAndClose() {
        assertFailure(
            RasvenOutgoingCallState.Busy(attempt),
            title = "خط مقصد مشغول است",
            card = "مقصد در حال مکالمه است",
            guide = "چند لحظه بعد دوباره تلاش کنید.",
        )
    }

    @Test fun rejectedFrameShowsDistinctCopyRetryAndClose() {
        assertFailure(
            RasvenOutgoingCallState.Rejected(attempt),
            title = "تماس رد شد",
            card = "مقصد تماس را رد کرد",
            guide = "در صورت نیاز دوباره تماس بگیرید.",
        )
    }

    @Test fun noAnswerFrameShowsDistinctCopyRetryAndClose() {
        assertFailure(
            RasvenOutgoingCallState.NoAnswer(attempt),
            title = "پاسخی دریافت نشد",
            card = "مهلت پاسخ‌گویی پایان یافت",
            guide = "می‌توانید دوباره تلاش کنید.",
        )
    }

    @Test fun networkErrorFrameShowsSanitizedProductCopy() {
        assertFailure(
            RasvenOutgoingCallState.NetworkError(attempt),
            title = "خطای شبکه",
            card = "اتصال اینترنت یا سرور را بررسی کنید",
            guide = "اتصال را بررسی و دوباره تلاش کنید.",
        )
        composeRule.onNodeWithText("IOException").assertDoesNotExist()
        composeRule.onNodeWithText("SocketException").assertDoesNotExist()
    }

    @Test fun compact320KeepsFailureActionsReachable() {
        render(
            RasvenOutgoingCallState.NetworkError(attempt),
            modifier = Modifier.width(320.dp).fillMaxHeight(),
        )
        composeRule.onNodeWithTag("outgoing_retry").assertIsDisplayed()
        composeRule.onNodeWithTag("outgoing_close").assertIsDisplayed()
    }

    @Test fun longTechnicalDestinationRemainsRenderedWithoutReplacingDomainValue() {
        render(
            RasvenOutgoingCallState.Dialing(
                attempt.copy(destination = "+989121234567890123"),
            ),
        )
        composeRule.onNodeWithTag("outgoing_destination").assertIsDisplayed()
    }

    private fun assertFailure(
        state: RasvenOutgoingCallState,
        title: String,
        card: String,
        guide: String,
    ) {
        var retries = 0
        var closes = 0
        render(state, onRetry = { retries += 1 }, onCloseFailure = { closes += 1 })
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText("تماس برقرار نشد").assertIsDisplayed()
        composeRule.onNodeWithText(card).assertIsDisplayed()
        composeRule.onNodeWithText(guide).assertIsDisplayed()
        composeRule.onNodeWithTag("outgoing_retry").performClick()
        composeRule.onNodeWithTag("outgoing_close").performClick()
        composeRule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, closes)
        }
    }
}
