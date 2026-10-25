package com.rasven.android.product.call

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.product.contacts.RasvenContact
import com.rasven.android.product.contacts.RasvenContactsScreen
import com.rasven.android.product.home.RasvenHomeDialerScreen
import com.rasven.android.product.home.RasvenHomeDialerUiState
import com.rasven.android.product.setup.RasvenRegistrationState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RasvenOutgoingCallIntegrationTest {
    @get:Rule val composeRule = createComposeRule()

    private class Gateway : RasvenOutgoingCallGateway {
        val attempts = mutableListOf<RasvenOutgoingCallAttempt>()
        var callback: ((RasvenOutgoingTransportEvent) -> Unit)? = null
        var cancels = 0
        override fun start(
            attempt: RasvenOutgoingCallAttempt,
            onEvent: (RasvenOutgoingTransportEvent) -> Unit,
        ): RasvenOutgoingStartDisposition {
            attempts += attempt
            callback = onEvent
            return RasvenOutgoingStartDisposition.Accepted
        }
        override fun cancel(attemptId: Long) { cancels += 1 }
    }

    @Test fun homeDirectDial305NavigatesIntoSharedOutgoingCoordinatorWithoutPrefix() {
        val gateway = Gateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        composeRule.setContent {
            RasvenTheme {
                OutgoingOrHome(owner) { destination ->
                    owner.requestStart(
                        destination,
                        RasvenOutgoingCallOrigin.HomeDirectDial,
                        microphoneGranted = true,
                        registrationState = RasvenRegistrationState.Registered,
                    )
                }
            }
        }
        composeRule.onNodeWithTag("home_call_action").performClick()
        composeRule.onNodeWithText("در حال تماس").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, gateway.attempts.size)
            assertEquals("305", gateway.attempts.single().destination)
            assertEquals(RasvenOutgoingCallOrigin.HomeDirectDial, gateway.attempts.single().origin)
        }
    }

    @Test fun contactsPreparedDestinationNavigatesIntoSameCoordinatorExactlyOnce() {
        val gateway = Gateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        composeRule.setContent {
            RasvenTheme {
                if (owner.state is RasvenOutgoingCallState.Idle) {
                    RasvenContactsScreen(
                        permissionGranted = true,
                        contacts = listOf(RasvenContact("نمونه", "09121234567")),
                        query = "",
                        persianFontFamily = FontFamily.Default,
                        englishFontFamily = FontFamily.Default,
                        onQueryChange = {},
                        onRequestPermission = {},
                        contactPrefix = "9",
                        onStartOutgoingCall = { destination ->
                            owner.requestStart(
                                destination,
                                RasvenOutgoingCallOrigin.Contacts,
                                microphoneGranted = true,
                                registrationState = RasvenRegistrationState.Registered,
                            )
                        },
                        onNavigationDestination = {},
                    )
                } else {
                    OutgoingSurface(owner)
                }
            }
        }
        composeRule.onNodeWithTag("contact_row_09121234567").performClick()
        composeRule.onNodeWithText("در حال تماس").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, gateway.attempts.size)
            assertEquals("909121234567", gateway.attempts.single().destination)
            assertEquals(RasvenOutgoingCallOrigin.Contacts, gateway.attempts.single().origin)
        }
    }

    @Test fun failureRetryCreatesNewAttemptAndCloseReturnsToOriginSurface() {
        val gateway = Gateway()
        val owner = RasvenOutgoingCallViewModel(gateway)
        composeRule.setContent {
            RasvenTheme {
                if (owner.state is RasvenOutgoingCallState.Idle) {
                    RasvenHomeDialerScreen(
                        state = RasvenHomeDialerUiState(
                            connectionState = RasvenRegistrationState.Registered,
                            extension = "204",
                            doNotDisturb = false,
                            destination = "305",
                        ),
                        persianFontFamily = FontFamily.Default,
                        englishFontFamily = FontFamily.Default,
                        onDestinationChange = {},
                        onDialKey = {},
                        onDelete = {},
                        onDoNotDisturbChanged = {},
                        onStartOutgoingCall = { destination ->
                            owner.requestStart(
                                destination,
                                RasvenOutgoingCallOrigin.HomeDirectDial,
                                true,
                                RasvenRegistrationState.Registered,
                            )
                        },
                        onReconnect = {},
                        onNavigationDestination = {},
                    )
                } else {
                    OutgoingSurface(owner)
                }
            }
        }
        composeRule.onNodeWithTag("home_call_action").performClick()
        composeRule.runOnIdle { gateway.callback?.invoke(RasvenOutgoingTransportEvent.Busy) }
        composeRule.onNodeWithText("خط مقصد مشغول است").assertIsDisplayed()
        composeRule.onNodeWithTag("outgoing_retry").performClick()
        composeRule.onNodeWithText("در حال تماس").assertIsDisplayed()
        composeRule.runOnIdle { gateway.callback?.invoke(RasvenOutgoingTransportEvent.Rejected) }
        composeRule.onNodeWithText("تماس رد شد").assertIsDisplayed()
        composeRule.onNodeWithTag("outgoing_close").performClick()
        composeRule.onNodeWithTag("home_dialer_screen").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(2, gateway.attempts.size) }
    }

    @Composable
    private fun OutgoingOrHome(
        owner: RasvenOutgoingCallViewModel,
        start: (String) -> Unit,
    ) {
        if (owner.state is RasvenOutgoingCallState.Idle) {
            RasvenHomeDialerScreen(
                state = RasvenHomeDialerUiState(
                    connectionState = RasvenRegistrationState.Registered,
                    extension = "204",
                    doNotDisturb = false,
                    destination = "305",
                ),
                persianFontFamily = FontFamily.Default,
                englishFontFamily = FontFamily.Default,
                onDestinationChange = {},
                onDialKey = {},
                onDelete = {},
                onDoNotDisturbChanged = {},
                onStartOutgoingCall = start,
                onReconnect = {},
                onNavigationDestination = { _: RasvenNavDestination -> },
            )
        } else {
            OutgoingSurface(owner)
        }
    }

    @Composable
    private fun OutgoingSurface(owner: RasvenOutgoingCallViewModel) {
        RasvenOutgoingCallScreen(
            state = owner.state,
            persianFontFamily = FontFamily.Default,
            onPermissionRequest = {},
            onClosePermission = owner::closePermissionSurface,
            onCancel = {
                if (owner.cancel()) owner.closeCancelled()
            },
            onRetry = { owner.retry(true, RasvenRegistrationState.Registered) },
            onCloseFailure = owner::closeFailureSurface,
        )
    }
}
