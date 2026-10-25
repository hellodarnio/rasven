package com.rasven.android.product.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.product.setup.RasvenRegistrationState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RasvenHomeDialerScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun render(
        dnd: Boolean,
        destination: String,
        connectionState: RasvenRegistrationState = RasvenRegistrationState.Registered,
        onCall: (String) -> Unit = {},
        onReconnect: () -> Unit = {},
    ) {
        composeRule.setContent {
            RasvenTheme {
                RasvenHomeDialerScreen(
                    state = RasvenHomeDialerUiState(connectionState, "204", dnd, destination),
                    persianFontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    onDestinationChange = {}, onDialKey = {}, onDelete = {},
                    onDoNotDisturbChanged = {}, onStartOutgoingCall = onCall,
                    onReconnect = onReconnect, onNavigationDestination = {},
                )
            }
        }
    }

    @Test fun baseStateConnectedDndOffEmptyDisablesCallAndHidesDelete() {
        render(false, "")
        composeRule.onNodeWithTag("home_connected_badge").assertIsDisplayed()
        composeRule.onNodeWithText("داخلی ۲۰۴").assertIsDisplayed()
        composeRule.onNodeWithTag("home_dnd_switch").assertIsOff()
        composeRule.onNodeWithText("شماره را وارد کنید یا از مخاطبین انتخاب کنید").assertIsDisplayed()
        composeRule.onNodeWithTag("home_call_action").assertIsNotEnabled()
        composeRule.onNodeWithTag("home_delete").assertDoesNotExist()
    }

    @Test fun dndOnEmptyIsIndependentAndCallRemainsDisabled() {
        render(true, "")
        composeRule.onNodeWithTag("home_dnd_switch").assertIsOn()
        composeRule.onNodeWithTag("home_call_action").assertIsNotEnabled()
    }

    @Test fun tappingDndLabelTogglesTheSameSwitch() {
        composeRule.setContent {
            RasvenTheme {
                var dnd by remember { mutableStateOf(false) }
                RasvenHomeDialerScreen(
                    state = RasvenHomeDialerUiState(RasvenRegistrationState.Registered, "204", dnd, ""),
                    persianFontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    onDestinationChange = {}, onDialKey = {}, onDelete = {},
                    onDoNotDisturbChanged = { dnd = it },
                    onStartOutgoingCall = {}, onReconnect = {}, onNavigationDestination = {},
                )
            }
        }
        composeRule.onNodeWithTag("home_dnd_label").performClick()
        composeRule.onNodeWithTag("home_dnd_switch").assertIsOn()
    }

    @Test fun numberEnteredEnablesCallAndShowsDelete() {
        render(false, "305")
        composeRule.onNodeWithText("305").assertIsDisplayed()
        composeRule.onNodeWithText("شماره یا داخلی آماده تماس است").assertIsDisplayed()
        composeRule.onNodeWithTag("home_delete").assertHasClickAction()
        composeRule.onNodeWithTag("home_call_action").assertIsEnabled()
    }

    @Test fun dndAndNumberCanCoexist() {
        render(true, "305")
        composeRule.onNodeWithTag("home_dnd_switch").assertIsOn()
        composeRule.onNodeWithTag("home_call_action").assertIsEnabled()
        composeRule.onNodeWithText("305").assertIsDisplayed()
    }

    @Test fun allTwelveOfficialDialKeysRender() {
        render(false, "")
        listOf("1","2","3","4","5","6","7","8","9","*","0","#").forEach {
            composeRule.onNodeWithTag("home_key_$it").assertIsDisplayed().assertHasClickAction()
        }
    }

    @Test fun keypadAndDeleteDriveOneStateTreeWithoutPrefixRewrite() {
        composeRule.setContent {
            RasvenTheme {
                var destination by remember { mutableStateOf("") }
                var dnd by remember { mutableStateOf(false) }
                RasvenHomeDialerScreen(
                    state = RasvenHomeDialerUiState(RasvenRegistrationState.Registered, "204", dnd, destination),
                    persianFontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    onDestinationChange = { destination = sanitizeDirectDialInput(it) },
                    onDialKey = { destination = appendDialCharacter(destination, it) },
                    onDelete = { destination = deleteLastDialCharacter(destination) },
                    onDoNotDisturbChanged = { dnd = it },
                    onStartOutgoingCall = {}, onReconnect = {}, onNavigationDestination = {},
                )
            }
        }
        composeRule.onNodeWithTag("home_key_3").performClick()
        composeRule.onNodeWithTag("home_key_0").performClick()
        composeRule.onNodeWithTag("home_key_5").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_destination_control").assertIsDisplayed()
        composeRule.onNodeWithTag("home_destination_input").assertTextContains("305")
        composeRule.onNodeWithTag("home_call_action").assertIsEnabled()
        composeRule.onNodeWithTag("home_delete").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_destination_control").assertIsDisplayed()
        composeRule.onNodeWithTag("home_destination_input").assertTextContains("30")
        composeRule.onNodeWithTag("home_dnd_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_dnd_switch").assertIsOn()
        composeRule.onNodeWithTag("home_destination_control").assertIsDisplayed()
        composeRule.onNodeWithTag("home_destination_input").assertTextContains("30")
    }


    @Test fun disconnectedStateUsesOfficialDisabledControlsAndShowsReconnectOnly() {
        var reconnects = 0
        render(
            dnd = true,
            destination = "305",
            connectionState = RasvenRegistrationState.Unregistered,
            onReconnect = { reconnects += 1 },
        )
        composeRule.onNodeWithText("اتصال قطع است").assertIsDisplayed()
        composeRule.onNodeWithTag("home_disconnected_badge").assertIsDisplayed()
        composeRule.onNodeWithText("خط سازمانی ثبت نشده است.").assertIsDisplayed()
        composeRule.onNodeWithTag("home_destination_input").assertIsNotEnabled().assertTextContains("305")
        listOf("1","2","3","4","5","6","7","8","9","*","0","#").forEach {
            composeRule.onNodeWithTag("home_key_$it").assertIsDisplayed().assertIsNotEnabled()
        }
        composeRule.onNodeWithTag("home_dnd_switch").assertDoesNotExist()
        composeRule.onNodeWithTag("home_call_action").assertDoesNotExist()
        composeRule.onNodeWithTag("home_delete").assertDoesNotExist()
        composeRule.onNodeWithTag("home_reconnect").assertIsDisplayed().assertHasClickAction().performClick()
        composeRule.onNodeWithTag("home_bottom_navigation").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, reconnects) }
    }

    @Test fun reconnectingStateDisablesControlsAndBlocksSecondReconnectSurface() {
        render(
            dnd = true,
            destination = "305",
            connectionState = RasvenRegistrationState.Registering,
        )
        composeRule.onNodeWithText("در حال بازیابی اتصال").assertIsDisplayed()
        composeRule.onNodeWithTag("home_reconnecting_badge").assertIsDisplayed()
        composeRule.onNodeWithText("در حال تلاش برای اتصال مجدد...").assertIsDisplayed()
        composeRule.onNodeWithTag("home_destination_input").assertIsNotEnabled().assertTextContains("305")
        listOf("1","2","3","4","5","6","7","8","9","*","0","#").forEach {
            composeRule.onNodeWithTag("home_key_$it").assertIsDisplayed().assertIsNotEnabled()
        }
        composeRule.onNodeWithTag("home_dnd_switch").assertDoesNotExist()
        composeRule.onNodeWithTag("home_call_action").assertDoesNotExist()
        composeRule.onNodeWithTag("home_delete").assertDoesNotExist()
        composeRule.onNodeWithTag("home_reconnect").assertDoesNotExist()
        composeRule.onNodeWithTag("home_bottom_navigation").assertIsDisplayed()
    }

    @Test fun callCallbackFiresExactlyOnceWithExactDestination() {
        var calls = 0
        var destination = ""
        render(false, "305", onCall = { calls += 1; destination = it })
        composeRule.onNodeWithTag("home_call_action").performClick()
        composeRule.runOnIdle {
            assertEquals(1, calls)
            assertEquals("305", destination)
        }
    }

    @Test fun homeContactsNavigationCallbackIsWiredWithoutChangingHomeDialContract() {
        var selected: RasvenNavDestination? = null
        composeRule.setContent {
            RasvenTheme {
                RasvenHomeDialerScreen(
                    state = RasvenHomeDialerUiState(RasvenRegistrationState.Registered, "204", false, "305"),
                    persianFontFamily = FontFamily.Default, englishFontFamily = FontFamily.Default,
                    onDestinationChange = {}, onDialKey = {}, onDelete = {}, onDoNotDisturbChanged = {},
                    onStartOutgoingCall = {}, onReconnect = {}, onNavigationDestination = { selected = it },
                )
            }
        }
        composeRule.onNodeWithText("مخاطبین").performClick()
        composeRule.runOnIdle { assertEquals(RasvenNavDestination.Contacts, selected) }
        composeRule.onNodeWithTag("home_destination_input").assertTextContains("305")
    }

    @Test fun homeHistoryNavigationCallbackPreservesDirectDial305() {
        var selected: RasvenNavDestination? = null
        composeRule.setContent {
            RasvenTheme {
                RasvenHomeDialerScreen(
                    state = RasvenHomeDialerUiState(RasvenRegistrationState.Registered, "204", false, "305"),
                    persianFontFamily = FontFamily.Default, englishFontFamily = FontFamily.Default,
                    onDestinationChange = {}, onDialKey = {}, onDelete = {}, onDoNotDisturbChanged = {},
                    onStartOutgoingCall = {}, onReconnect = {}, onNavigationDestination = { selected = it },
                )
            }
        }
        composeRule.onNodeWithText("سوابق").performClick()
        composeRule.runOnIdle { assertEquals(RasvenNavDestination.History, selected) }
        composeRule.onNodeWithTag("home_destination_input").assertTextContains("305")
    }

    @Test fun homeSettingsNavigationCallbackPreservesDirectDial305() {
        var selected: RasvenNavDestination? = null
        composeRule.setContent {
            RasvenTheme {
                RasvenHomeDialerScreen(
                    state = RasvenHomeDialerUiState(RasvenRegistrationState.Registered, "204", false, "305"),
                    persianFontFamily = FontFamily.Default, englishFontFamily = FontFamily.Default,
                    onDestinationChange = {}, onDialKey = {}, onDelete = {}, onDoNotDisturbChanged = {},
                    onStartOutgoingCall = {}, onReconnect = {}, onNavigationDestination = { selected = it },
                )
            }
        }
        composeRule.onNodeWithText("تنظیمات").performClick()
        composeRule.runOnIdle { assertEquals(RasvenNavDestination.Settings, selected) }
        composeRule.onNodeWithTag("home_destination_input").assertTextContains("305")
    }

    @Test fun homeBottomNavigationIsPresentWithFourDestinations() {
        render(false, "")
        composeRule.onNodeWithTag("home_bottom_navigation").assertIsDisplayed()
        listOf("خانه","مخاطبین","سوابق","تنظیمات").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }
}
