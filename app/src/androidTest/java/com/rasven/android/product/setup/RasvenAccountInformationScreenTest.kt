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
class RasvenAccountInformationScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Composable
    private fun accountContent(onContinue: (RasvenValidatedAccount) -> Unit = {}) {
        RasvenTheme {
            RasvenAccountInformationScreen(
                persianFontFamily = RasvenAndroidFontFamilies.Persian,
                englishFontFamily = FontFamily.Default,
                onContinue = onContinue,
            )
        }
    }

    @Test fun normalScreenRendersWithOfficialFieldsAndNoPrematureError() {
        composeRule.setContent { accountContent() }
        composeRule.onNode(hasText("حساب IAX2") and isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("اطلاعات اتصال سازمانی").assertIsDisplayed()
        for (field in listOf("account_server", "account_port", "account_username", "account_password")) {
            composeRule.onNodeWithTag(field).assertExists()
        }
        composeRule.onNodeWithText("دامنه یا IP سرور").assertIsDisplayed()
        composeRule.onNodeWithText("آدرس سرور الزامی است").assertDoesNotExist()
        composeRule.onNodeWithText("ادامه").assertHasClickAction()
    }

    @Test fun continueOnBlankFormShowsAllApprovedErrorsAndDoesNotContinue() {
        var invoked = 0
        composeRule.setContent { accountContent { invoked++ } }
        composeRule.onNodeWithText("ادامه").performClick()
        for (message in listOf(
            "آدرس سرور الزامی است", "پورت باید بین 1 تا 65535 باشد",
            "نام کاربری یا داخلی الزامی است", "مقدار این فیلد الزامی است",
        )) composeRule.onNodeWithText(message).assertExists()
        composeRule.runOnIdle { assertEquals(0, invoked) }
    }

    @Test fun errorsReactToIndividualCorrectionsAfterFirstContinue() {
        composeRule.setContent { accountContent() }
        composeRule.onNodeWithText("ادامه").performClick()
        composeRule.onNodeWithTag("account_server").performTextInput("pbx.example.net")
        composeRule.onNodeWithText("آدرس سرور الزامی است").assertDoesNotExist()
        composeRule.onNodeWithText("نام کاربری یا داخلی الزامی است").assertExists()
        composeRule.onNodeWithTag("account_port").performTextInput("4569")
        composeRule.onNodeWithText("پورت باید بین 1 تا 65535 باشد").assertDoesNotExist()
        composeRule.onNodeWithText("مقدار این فیلد الزامی است").assertExists()
    }

    @Test fun fixingAllFieldsAfterFailedContinueWaitsForExplicitSecondContinue() {
        var invoked = 0
        composeRule.setContent { accountContent { invoked++ } }
        composeRule.onNodeWithText("ادامه").performClick()
        composeRule.onNodeWithTag("account_server").performTextInput("pbx.example.net")
        composeRule.onNodeWithTag("account_username").performTextInput("204")
        composeRule.onNodeWithTag("account_port").performTextInput("4569")
        composeRule.onNodeWithTag("account_password").performTextInput("p".repeat(8))
        composeRule.onNodeWithText("آدرس سرور الزامی است").assertDoesNotExist()
        composeRule.onNodeWithText("مقدار این فیلد الزامی است").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, invoked) }
        composeRule.onNodeWithText("ادامه").performClick()
        composeRule.runOnIdle { assertEquals(1, invoked) }
    }

    @Test fun invalidPortDoesNotPassCallback() {
        var invoked = 0
        composeRule.setContent { accountContent { invoked++ } }
        composeRule.onNodeWithTag("account_server").performTextInput("pbx.example.net")
        composeRule.onNodeWithTag("account_port").performTextInput("70000")
        composeRule.onNodeWithTag("account_username").performTextInput("204")
        composeRule.onNodeWithTag("account_password").performTextInput("p".repeat(8))
        composeRule.onNodeWithText("ادامه").performClick()
        composeRule.onNodeWithText("پورت باید بین 1 تا 65535 باشد").assertExists()
        composeRule.runOnIdle { assertEquals(0, invoked) }
    }

    @Test fun validTechnicalInputsInvokeCallbackExactlyOnce() {
        var invoked = 0
        var parsedPort: Int? = null
        composeRule.setContent { accountContent { account -> invoked++; parsedPort = account.port } }

        // Drive the direct-valid-submit case through the screen's real focus contract.
        // The RTL row is composed/focused as Username -> Port, so avoid synthetic
        // out-of-order SetText actions that are unstable on newer Compose test runtimes.
        composeRule.onNodeWithTag("account_server").performClick()
        composeRule.onNodeWithTag("account_server").performTextInput("pbx.example.net")
        composeRule.onNodeWithTag("account_server").performImeAction()
        composeRule.onNodeWithTag("account_username").assertIsFocused()
        composeRule.onNodeWithTag("account_username").performTextInput("204")
        composeRule.onNodeWithTag("account_username").performImeAction()
        composeRule.onNodeWithTag("account_port").assertIsFocused()
        composeRule.onNodeWithTag("account_port").performTextInput("4569")
        composeRule.onNodeWithTag("account_port").performImeAction()
        composeRule.onNodeWithTag("account_password").assertIsFocused()
        composeRule.onNodeWithTag("account_password").performTextInput("p".repeat(8))
        composeRule.onNodeWithTag("account_password").performImeAction()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("ادامه").assertHasClickAction().performClick()
        composeRule.runOnIdle { assertEquals(1, invoked); assertEquals(4569, parsedPort) }
    }

    @Test fun latinServerAndNumericExtensionAcceptInputInsideRtlScreen() {
        composeRule.setContent { accountContent() }
        composeRule.onNodeWithTag("account_server").performTextInput("198.51.100.24")
        composeRule.onNodeWithTag("account_server").assertTextContains("198.51.100.24")
        composeRule.onNodeWithTag("account_username").performTextInput("204")
        composeRule.onNodeWithTag("account_username").assertTextContains("204")
    }

    @Test fun passwordFieldExposesPasswordSemanticsNotOrdinaryVisibleText() {
        composeRule.setContent { accountContent() }
        composeRule.onNodeWithTag("account_password").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Password),
        )
        composeRule.onNodeWithTag("account_password").performClick()
        composeRule.onNodeWithTag("account_password").assertIsFocused()
        composeRule.onNodeWithTag("account_password").performTextInput("p".repeat(8))
        composeRule.onNodeWithTag("account_password").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Password),
        )
    }

    @Test fun passwordTypingAlignmentUsesInterfaceDirectionWhileCredentialStaysLtr() {
        assertEquals(TextAlign.Right,
            RasvenAccountInformationContract.passwordValueAlignment(LayoutDirection.Rtl))
        assertEquals(TextAlign.Left,
            RasvenAccountInformationContract.passwordValueAlignment(LayoutDirection.Ltr))
        composeRule.setContent { accountContent() }
        composeRule.onNodeWithTag("account_password").performClick()
        composeRule.onNodeWithTag("account_password").assertIsFocused()
    }

    @Test fun rowPlacesPortLeftOfUsernameAsInFinalFigma() {
        composeRule.setContent { accountContent() }
        val portLeft = composeRule.onNodeWithTag("account_port").fetchSemanticsNode().boundsInRoot.left
        val usernameLeft = composeRule.onNodeWithTag("account_username").fetchSemanticsNode().boundsInRoot.left
        org.junit.Assert.assertTrue("Figma positions Port at x=0 and Username at x=132", portLeft < usernameLeft)
    }

    @Test fun imeNextMovesFocusFromServerToUsername() {
        composeRule.setContent { accountContent() }
        composeRule.onNodeWithTag("account_server").performClick()
        composeRule.onNodeWithTag("account_server").performImeAction()
        composeRule.onNodeWithTag("account_username").assertIsFocused()
    }

    @Test fun upgradedTextFieldsFitAt320DpWithOriginalErrorAndCtaBehavior() {
        composeRule.setContent {
            Box(Modifier.width(320.dp).fillMaxHeight().testTag("account_320_viewport")) {
                accountContent()
            }
        }
        val viewport = composeRule.onNodeWithTag("account_320_viewport").fetchSemanticsNode().boundsInRoot
        for (tag in listOf(
            "account_server_field", "account_port_field",
            "account_username_field", "account_password_field",
        )) {
            val field = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag begins inside 320dp viewport", field.left >= viewport.left - 1f)
            assertTrue("$tag ends inside 320dp viewport", field.right <= viewport.right + 1f)
        }
        val largeServer = composeRule.onNodeWithTag("account_server_field").fetchSemanticsNode().boundsInRoot
        val mediumPassword = composeRule.onNodeWithTag("account_password_field").fetchSemanticsNode().boundsInRoot
        assertTrue("Large server field must be taller than Medium password field",
            largeServer.height > mediumPassword.height)
        composeRule.onNodeWithTag("account_continue").assertIsDisplayed()
        composeRule.onNodeWithTag("account_continue").performClick()
        composeRule.onNodeWithText("آدرس سرور الزامی است").assertExists()
        composeRule.onNodeWithText("مقدار این فیلد الزامی است").assertExists()
    }
}
