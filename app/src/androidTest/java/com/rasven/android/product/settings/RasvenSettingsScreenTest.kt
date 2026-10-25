package com.rasven.android.product.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.RasvenProductLanguageScope
import com.rasven.android.product.setup.RasvenAdvancedSettingsErrors
import com.rasven.android.product.setup.RasvenRegistrationState
import com.rasven.android.product.call.RasvenPbxTransferProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RasvenSettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun render(
        initialState: RasvenSettingsUiState = RasvenSettingsUiState(),
        currentPrefix: String = "9",
        notificationsEnabled: Boolean = true,
        onPrefixSave: () -> Unit = {},
        onSystemSettings: () -> Unit = {},
        onAdvancedSave: () -> Unit = {},
        onNavigation: (RasvenNavDestination) -> Unit = {},
        connectionState: RasvenRegistrationState = RasvenRegistrationState.Unregistered,
        onDisconnect: () -> Unit = {},
        onTransferProfileChanged: (RasvenPbxTransferProfile) -> Unit = {},
        screenModifier: Modifier = Modifier,
        language: RasvenLanguage = RasvenLanguage.Persian,
    ) {
        composeRule.setContent {
            RasvenTheme {
                RasvenProductLanguageScope(language) {
                var state by remember { mutableStateOf(initialState) }
                RasvenSettingsScreen(
                    state = state,
                    currentContactPrefix = currentPrefix,
                    notificationsEnabled = notificationsEnabled,
                    advancedAvailable = true,
                    connectionState = connectionState,
                    advancedErrors = RasvenAdvancedSettingsErrors(),
                    persianFontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    onOpenContactPrefix = { state = state.copy(destination = RasvenSettingsDestination.ContactPrefix, contactPrefixDraft = currentPrefix) },
                    onContactPrefixChanged = { state = state.copy(contactPrefixDraft = it) },
                    onSaveContactPrefix = onPrefixSave,
                    onOpenCallNotifications = { state = state.copy(destination = RasvenSettingsDestination.CallNotifications) },
                    onOpenSystemCallSettings = onSystemSettings,
                    onOpenAdvanced = { state = state.copy(destination = RasvenSettingsDestination.Advanced) },
                    onDisconnect = onDisconnect,
                    onAdvancedServerChanged = { state = state.copy(advancedDraft = state.advancedDraft.copy(server = it)) },
                    onAdvancedPortChanged = { state = state.copy(advancedDraft = state.advancedDraft.copy(port = it)) },
                    onAdvancedUsernameChanged = { state = state.copy(advancedDraft = state.advancedDraft.copy(username = it)) },
                    onAdvancedPasswordChanged = { state = state.copy(advancedDraft = state.advancedDraft.copy(passwordReplacement = it)) },
                    onTransferProfileChanged = { profile ->
                        state = state.copy(advancedDraft = state.advancedDraft.copy(transferProfile = profile))
                        onTransferProfileChanged(profile)
                    },
                    onSaveAdvanced = onAdvancedSave,
                    onBackToMain = { state = state.copy(destination = RasvenSettingsDestination.Main) },
                    onNavigationDestination = onNavigation,
                    modifier = screenModifier,
                )
                }
            }
        }
    }

    @Test fun mainFrameShowsExactRowsPersianLanguageAndSettingsCurrent() {
        render()
        composeRule.onNodeWithTag("settings_page_heading").assertIsDisplayed().assertTextContains("تنظیمات")
        composeRule.onNodeWithText("تنظیمات روزمره و حساب").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_row_contact_prefix").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_row_advanced").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_row_call_notifications").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_row_language").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_row_disconnect").assertIsDisplayed().assertHasNoClickAction()
        for (icon in listOf("settings_icon_contact_prefix", "settings_icon_advanced",
            "settings_icon_call_notifications", "settings_icon_language", "settings_icon_disconnect")) {
            composeRule.onNodeWithTag(icon, useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithText("سرور، پورت و حساب").assertDoesNotExist()
        composeRule.onNodeWithText("فارسی").assertIsDisplayed()
        composeRule.onNodeWithText("فعال").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_bottom_navigation").assertIsDisplayed()
    }

    @Test fun connectedSettingsRequiresConfirmationAndCancelNeverDisconnects() {
        var disconnects = 0
        render(connectionState = RasvenRegistrationState.Registered, onDisconnect = { disconnects++ })
        composeRule.onNodeWithTag("settings_row_disconnect").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("settings_disconnect_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_disconnect_confirm_title").assertTextContains("قطع اتصال خط کاری؟")
        composeRule.runOnIdle { assertEquals(0, disconnects) }
        composeRule.onNodeWithTag("settings_disconnect_cancel_action").performClick()
        composeRule.onNodeWithTag("settings_disconnect_sheet").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, disconnects) }
        composeRule.onNodeWithTag("settings_row_disconnect").performClick()
        composeRule.onNodeWithTag("settings_disconnect_confirm_action").performClick()
        composeRule.runOnIdle { assertEquals(1, disconnects) }
        composeRule.onNodeWithTag("settings_disconnect_sheet").assertDoesNotExist()
    }

    @Test fun englishSettingsUsesExactCopyAndLeftIconOrder() {
        render(connectionState = RasvenRegistrationState.Registered, language = RasvenLanguage.English)
        composeRule.onNodeWithTag("settings_page_subtitle").assertTextContains("Daily and account settings")
        composeRule.onNodeWithText("Server, port and account").assertDoesNotExist()
        composeRule.onNodeWithText("Server, port, and account").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_icon_advanced", useUnmergedTree = true).assertIsDisplayed()
        val icon = composeRule.onNodeWithTag("settings_icon_contact_prefix", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithText("Contacts prefix", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("English icon must precede the text on the left", icon.right <= title.left)
        composeRule.onNodeWithTag("settings_row_disconnect").performClick()
        composeRule.onNodeWithTag("settings_disconnect_confirm_title").assertTextContains("Disconnect work line?")
        composeRule.onNodeWithTag("settings_disconnect_confirm_body")
            .assertTextContains("Calls on this line will be unavailable on this device until you reconnect it.")
        composeRule.onNodeWithTag("settings_disconnect_sheet_icon").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_disconnect_cancel_action").performClick()
    }

    private fun verifyFigmaWidth(width: Int, language: RasvenLanguage) {
        render(
            connectionState = RasvenRegistrationState.Registered,
            screenModifier = Modifier.width(width.dp).fillMaxHeight(),
            language = language,
        )
        val viewport = composeRule.onNodeWithTag("settings_screen").fetchSemanticsNode().boundsInRoot
        for (row in listOf("settings_row_contact_prefix", "settings_row_advanced",
            "settings_row_call_notifications", "settings_row_language", "settings_row_disconnect")) {
            val bounds = composeRule.onNodeWithTag(row).fetchSemanticsNode().boundsInRoot
            assertTrue("$row escapes $width dp horizontally", bounds.left >= viewport.left && bounds.right <= viewport.right)
        }
        for (icon in listOf("settings_icon_contact_prefix", "settings_icon_advanced",
            "settings_icon_call_notifications", "settings_icon_language", "settings_icon_disconnect")) {
            composeRule.onNodeWithTag(icon, useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("settings_row_disconnect").performClick()
        val sheet = composeRule.onNodeWithTag("settings_disconnect_sheet").fetchSemanticsNode().boundsInRoot
        val danger = composeRule.onNodeWithTag("settings_disconnect_confirm_action").fetchSemanticsNode().boundsInRoot
        val cancel = composeRule.onNodeWithTag("settings_disconnect_cancel_action").fetchSemanticsNode().boundsInRoot
        assertTrue("Confirmation action clipped at $width dp", danger.left >= sheet.left && danger.right <= sheet.right)
        assertTrue("Cancel action clipped at $width dp", cancel.left >= sheet.left && cancel.right <= sheet.right)
        assertTrue("Confirmation buttons overlap at $width dp", danger.bottom < cancel.top)
    }

    @Test fun persian320SettingsAndConfirmationFit() = verifyFigmaWidth(320, RasvenLanguage.Persian)
    @Test fun persian360SettingsAndConfirmationFit() = verifyFigmaWidth(360, RasvenLanguage.Persian)
    @Test fun persian412SettingsAndConfirmationFit() = verifyFigmaWidth(412, RasvenLanguage.Persian)
    @Test fun english320SettingsAndConfirmationFit() = verifyFigmaWidth(320, RasvenLanguage.English)
    @Test fun english360SettingsAndConfirmationFit() = verifyFigmaWidth(360, RasvenLanguage.English)
    @Test fun english412SettingsAndConfirmationFit() = verifyFigmaWidth(412, RasvenLanguage.English)

    @Test fun contactPrefixRowNavigatesToPrefixSubpage() {
        render()
        composeRule.onNodeWithTag("settings_row_contact_prefix").performClick()
        composeRule.onNodeWithText("منطقه شماره‌گیری: ایران (+98)").assertIsDisplayed()
    }

    @Test fun notificationRowNavigatesToNotificationSubpage() {
        render()
        composeRule.onNodeWithTag("settings_row_call_notifications").performClick()
        composeRule.onNodeWithText("اعلان و نمایش تماس ورودی").assertIsDisplayed()
    }

    @Test fun incompleteIncomingCallReadinessIsNotMislabelledAsEnabled() {
        render(notificationsEnabled = false)
        composeRule.onNodeWithText("نیاز به تنظیم").assertIsDisplayed()
    }

    @Test fun advancedRowNavigatesToAdvancedSubpageAndLanguageHasNoDeadRoute() {
        render()
        composeRule.onNodeWithTag("settings_row_language").assertHasNoClickAction()
        composeRule.onNodeWithTag("settings_row_advanced").performClick()
        composeRule.onNodeWithText("تنظیمات پیشرفته").assertIsDisplayed()
    }

    @Test fun prefixFrameLoadsEditsAndSaveCallbackFiresOncePerTap() {
        var saves = 0
        render(
            initialState = RasvenSettingsUiState(RasvenSettingsDestination.ContactPrefix, contactPrefixDraft = "9"),
            onPrefixSave = { saves += 1 },
        )
        composeRule.onNodeWithTag("settings_prefix_input").assertTextContains("9")
            .performTextReplacement("81")
        composeRule.onNodeWithTag("settings_prefix_input").assertTextContains("81")
        composeRule.onNodeWithText("پس از یکسان‌سازی شماره‌های ایرانی اعمال می‌شود؛ داخلی‌ها و شماره‌گیری مستقیم تغییر نمی‌کنند").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_prefix_save").performClick()
        composeRule.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun notificationsFrameUsesGuideAndBoundedPlatformCallbackExactlyOnce() {
        var launches = 0
        render(
            initialState = RasvenSettingsUiState(RasvenSettingsDestination.CallNotifications),
            onSystemSettings = { launches += 1 },
        )
        composeRule.onNodeWithText("اعلان تماس‌ها").assertIsDisplayed()
        composeRule.onNodeWithText("اعلان و نمایش تماس ورودی").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_notifications_guide").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_notifications_cta").performClick()
        composeRule.runOnIdle { assertEquals(1, launches) }
    }

    @Test fun advancedFrameUsesMediumFieldsAndNeverSeedsCurrentSecret() {
        render(
            initialState = RasvenSettingsUiState(
                destination = RasvenSettingsDestination.Advanced,
                advancedDraft = RasvenAdvancedSettingsDraft("pbx.example.net", "4569", "204", ""),
            ),
        )
        composeRule.onNodeWithText("رمز عبور برای کاربر قابل مشاهده نیست و فقط مقدار جایگزین نمایش داده می‌شود.").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_advanced_server").assertTextContains("pbx.example.net")
        composeRule.onNodeWithTag("settings_advanced_port").assertTextContains("4569")
        composeRule.onNodeWithTag("settings_advanced_username").assertTextContains("204")
        composeRule.onNodeWithTag("settings_advanced_password").assertTextEquals("")
        composeRule.onNodeWithText("برای تغییر، رمز جدید وارد کنید").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_advanced_save").assertIsDisplayed()
    }

    @Test fun yeastarPresetIsVisibleAndAppliesCodesWithoutDroppingDestinations() {
        var selected: RasvenPbxTransferProfile? = null
        render(
            initialState = RasvenSettingsUiState(
                destination = RasvenSettingsDestination.Advanced,
                advancedDraft = RasvenAdvancedSettingsDraft(
                    "pbx.example.net", "4569", "204", "",
                    transferProfile = RasvenPbxTransferProfile.yeastarS50Defaults.copy(salesDestination = "200"),
                ),
            ),
            onTransferProfileChanged = { selected = it },
        )
        // The form scroll viewport excludes the floating Save/navigation overlays.
        composeRule.onNodeWithTag("settings_transfer_preset_yeastar").performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals("##", selected?.blindCode)
            assertEquals("*3", selected?.attendedCode)
            assertEquals("", selected?.cancelAttendedCode)
            assertEquals("200", selected?.salesDestination)
            assertEquals(com.rasven.android.product.call.RasvenPbxProfileIdentity.Yeastar, selected?.identity)
        }
    }

    @Test fun compact320PrefixKeepsLongHelperAndSaveReachable() {
        render(
            initialState = RasvenSettingsUiState(RasvenSettingsDestination.ContactPrefix, contactPrefixDraft = "9"),
            screenModifier = Modifier.width(320.dp).fillMaxHeight(),
        )
        composeRule.onNodeWithTag("settings_prefix_field").assertIsDisplayed()
        composeRule.onNodeWithText("پس از یکسان‌سازی شماره‌های ایرانی اعمال می‌شود؛ داخلی‌ها و شماره‌گیری مستقیم تغییر نمی‌کنند").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_prefix_save").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_bottom_navigation").assertIsDisplayed()
    }

    @Test fun advancedPasswordImeKeepsSaveActionReachable() {
        render(
            initialState = RasvenSettingsUiState(
                destination = RasvenSettingsDestination.Advanced,
                advancedDraft = RasvenAdvancedSettingsDraft("pbx.example.net", "4569", "204", ""),
            ),
        )
        composeRule.onNodeWithTag("settings_advanced_password").performClick().performTextInput("qa-replacement")
        composeRule.onNodeWithTag("settings_advanced_save").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_bottom_navigation").assertIsDisplayed()
    }

    @Test fun bottomNavigationExposesAllThreeOtherRealDestinationsFromSubpage() {
        val destinations = mutableListOf<RasvenNavDestination>()
        render(
            initialState = RasvenSettingsUiState(RasvenSettingsDestination.CallNotifications),
            onNavigation = { destinations += it },
        )
        composeRule.onNodeWithText("خانه").performClick()
        composeRule.onNodeWithText("مخاطبین").performClick()
        composeRule.onNodeWithText("سوابق").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(RasvenNavDestination.Home, RasvenNavDestination.Contacts, RasvenNavDestination.History),
                destinations,
            )
        }
    }
}
