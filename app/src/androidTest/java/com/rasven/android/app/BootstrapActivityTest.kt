package com.rasven.android.app

import android.Manifest
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rasven.android.product.home.RasvenHomeDialerViewModel
import com.rasven.android.product.contacts.RasvenContactsViewModel
import com.rasven.android.product.call.RasvenCallDomain
import com.rasven.android.product.setup.RasvenRegistrationErrorCategory
import com.rasven.android.product.setup.RasvenRegistrationGateway
import com.rasven.android.product.setup.RasvenRegistrationResult
import com.rasven.android.product.setup.RasvenRegistrationState
import com.rasven.android.product.setup.RasvenRegistrationViewModel
import com.rasven.android.product.setup.RasvenSecureSetupRepository
import com.rasven.android.product.setup.RasvenRuntimeRegistrationGateway
import com.rasven.android.product.setup.RasvenValidatedAccount
import com.rasven.android.product.settings.RasvenRequiredCallAccess
import com.rasven.android.product.settings.RasvenRequiredCallAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootstrapActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<BootstrapActivity>()

    @Before
    fun grantRequiredAccessForRegistrationFlowFixtures() {
        RasvenRequiredCallAccess.setTestOverride(RasvenRequiredCallAccessStatus(true, true))
        // These legacy journey assertions exercise the Persian setup flow explicitly.
        // The production first-install language is English and the new welcome is one-time.
        composeRule.onNodeWithTag("welcome_language_button").performClick()
    }


    private class ControllableRegistrationGateway : RasvenRegistrationGateway {
        var starts = 0
        var lastAccount: RasvenValidatedAccount? = null
        private var terminal: ((RasvenRegistrationResult) -> Unit)? = null

        override fun start(
            account: RasvenValidatedAccount,
            onResult: (RasvenRegistrationResult) -> Unit,
        ) {
            starts += 1
            lastAccount = account
            terminal = onResult
        }

        fun emit(result: RasvenRegistrationResult) {
            terminal?.invoke(result)
        }
    }

    @After
    fun clearRegistrationQaOverride() {
        RasvenRuntimeRegistrationGateway.installQaOverride(null)
        RasvenRequiredCallAccess.setTestOverride(null)
        RasvenSecureSetupRepository(composeRule.activity).clear()
        composeRule.activity.getSharedPreferences("rasven_welcome_v1", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        composeRule.activity.getSharedPreferences("rasven_product_language_v1", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        composeRule.runOnUiThread { RasvenCallDomain.setDoNotDisturb(false) }
    }

    @Test
    fun launcherMountsAuthorizedStage5A1Screen() {
        composeRule.onNodeWithTag("welcome_promise").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_continue").assertIsDisplayed()
    }

    @Test
    fun startSetupOpensAuthorizedAccountInformationWithoutStage5A3() {
        composeRule.onNodeWithTag("welcome_continue").performClick()
        composeRule.onNodeWithText("حساب IAX2").assertIsDisplayed()
        composeRule.onNodeWithText("ادامه").assertIsDisplayed()
    }

    @Test
    fun invalidAccountDoesNotOpenStage5A3() {
        composeRule.onNodeWithTag("welcome_continue").performClick()
        composeRule.onNodeWithText("ادامه").performClick()
        composeRule.onNodeWithText("آدرس سرور الزامی است").assertIsDisplayed()
        composeRule.onNodeWithText("تنظیمات تماس").assertDoesNotExist()
    }

    @Test
    fun validStage5A2HandoffOpensStage5A3WithoutStartingNetwork() {
        composeRule.onNodeWithTag("welcome_continue").performClick()
        submitValidStage5A2(
            server = "pbx.example.net",
            port = "4569",
            username = "204",
            password = "p".repeat(8),
        )
        composeRule.onNodeWithText("تنظیمات تماس").assertIsDisplayed()
        composeRule.onNodeWithTag("call_settings_contact_prefix").assertExists()
        composeRule.onNodeWithText("ذخیره و اتصال").assertHasClickAction()
    }

    @Test
    fun firstRunSkipsAdvancedAndPreservesAccountPasswordAndPrefix() {
        val server = "pbx.integration.example"
        val port = "4569"
        val username = "204"
        val initialPassword = "initial-secret-42"

        composeRule.onNodeWithTag("welcome_continue").performClick()
        submitValidStage5A2(
            server = server,
            port = port,
            username = username,
            password = initialPassword,
        )

        composeRule.onNodeWithText("تنظیمات تماس").assertIsDisplayed()
        val gateway = ControllableRegistrationGateway()
        RasvenRuntimeRegistrationGateway.installQaOverride(gateway)
        composeRule.onNodeWithTag("call_settings_contact_prefix").performTextInput("81")
        composeRule.onNodeWithTag("call_settings_save_connect").performClick()

        composeRule.onNodeWithText("دسترسی‌های تماس").assertIsDisplayed()
        composeRule.onNodeWithTag("advanced_settings_server").assertDoesNotExist()
        composeRule.onNodeWithText(initialPassword).assertDoesNotExist()
        val contacts = ViewModelProvider(composeRule.activity)[RasvenContactsViewModel::class.java]
        composeRule.runOnIdle { assertEquals("81", contacts.contactPrefix) }
        composeRule.onNodeWithTag("permissions_continue_connect").performClick()
        composeRule.runOnIdle {
            assertEquals(1, gateway.starts)
            assertEquals(server, gateway.lastAccount?.server)
            assertEquals(4569, gateway.lastAccount?.port)
            assertEquals(username, gateway.lastAccount?.username)
            assertEquals(initialPassword, gateway.lastAccount?.password)
            assertEquals("81", contacts.contactPrefix)
        }
    }

    @Test
    fun realFlowReachesStage5A5WithoutPermissionPromptSettingsLaunchOrAutomaticConnection() {
        val activity = composeRule.activity
        val microphoneBefore = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val contactsBefore = activity.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        val notificationsBefore = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)

        reachStage5A5()

        composeRule.onNodeWithText("دسترسی‌های تماس").assertIsDisplayed()
        composeRule.onNodeWithTag("permissions_open_required_settings").assertHasClickAction()
        composeRule.onNodeWithTag("permissions_continue_connect").assertHasClickAction()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertDoesNotExist()

        composeRule.runOnIdle {
            assertEquals(microphoneBefore, activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            assertEquals(contactsBefore, activity.checkSelfPermission(Manifest.permission.READ_CONTACTS))
            assertEquals(notificationsBefore, activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            assertTrue(
                "Stage5A5 entry must not hand window focus to a permission dialog or system settings",
                activity.window.decorView.hasWindowFocus(),
            )
        }
    }

    @Test
    fun stage5A5ConnectsOnlyWhenRequiredAccessGateIsSatisfied() {
        val activity = composeRule.activity
        val microphoneBefore = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val contactsBefore = activity.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        val notificationsBefore = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        val gateway = ControllableRegistrationGateway()
        RasvenRuntimeRegistrationGateway.installQaOverride(gateway)

        reachStage5A5()
        composeRule.onNodeWithTag("permissions_continue_connect").performClick()

        composeRule.onNodeWithTag("connecting_heading").assertIsDisplayed()
        composeRule.onNodeWithTag("connecting_status_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("connecting_registration_card").assertIsDisplayed()
        composeRule.onNodeWithText("ادامه و اتصال").assertDoesNotExist()

        composeRule.runOnIdle {
            assertEquals(1, gateway.starts)
            assertEquals(microphoneBefore, activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            assertEquals(contactsBefore, activity.checkSelfPermission(Manifest.permission.READ_CONTACTS))
            assertEquals(notificationsBefore, activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            assertTrue(activity.window.decorView.hasWindowFocus())
        }
    }

    @Test
    fun stage5A6SurvivesActivityRecreationWithoutDuplicateRegistrationAttempt() {
        val gateway = ControllableRegistrationGateway()
        RasvenRuntimeRegistrationGateway.installQaOverride(gateway)
        reachStage5A5()
        composeRule.onNodeWithTag("permissions_continue_connect").performClick()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()

        val before = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
        assertEquals(1, before.startCount)
        assertEquals(1, gateway.starts)
        assertTrue(before.state is RasvenRegistrationState.Registering)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
        val after = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
        assertTrue(before === after)
        assertEquals(1, after.startCount)
        assertTrue(after.state is RasvenRegistrationState.Registering)
    }

    @Test
    fun registrationFixtureDoesNotRequestContactsOrChangePlatformPermissions() {
        val activity = composeRule.activity
        val permissions = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS)
        val before = permissions.map(activity::checkSelfPermission)
        val gateway = ControllableRegistrationGateway()
        RasvenRuntimeRegistrationGateway.installQaOverride(gateway)
        reachStage5A5()
        composeRule.onNodeWithTag("permissions_continue_connect").performClick()
        composeRule.onNodeWithTag("connecting_heading").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, gateway.starts)
            assertEquals(before, permissions.map(activity::checkSelfPermission))
            val restored = RasvenSecureSetupRepository(activity).load()
            assertEquals(gateway.lastAccount?.server, restored?.account?.server)
            assertEquals(gateway.lastAccount?.port, restored?.account?.port)
            assertEquals(gateway.lastAccount?.username, restored?.account?.username)
            assertEquals(gateway.lastAccount?.password, restored?.account?.password)
        }
    }


    @Test
    fun registeredResultTransitionsStage5A6ToStage5A7OnceWithoutPermissionRegression() {
        val activity = composeRule.activity
        val microphoneBefore = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val contactsBefore = activity.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        val notificationsBefore = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        val gateway = ControllableRegistrationGateway()
        RasvenRuntimeRegistrationGateway.installQaOverride(gateway)

        reachStage5A5()
        composeRule.onNodeWithTag("permissions_continue_connect").performClick()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, gateway.starts) }

        composeRule.runOnIdle { gateway.emit(RasvenRegistrationResult.Registered) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("connecting_loading_indicator").assertDoesNotExist()
        composeRule.onNodeWithTag("registration_success_heading").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_success_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_success_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_success_card").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_success_enter_dialer").assertHasClickAction()

        // A duplicate terminal callback must not create a new attempt or move away from Registered.
        composeRule.runOnIdle { gateway.emit(RasvenRegistrationResult.Registered) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val owner = ViewModelProvider(activity)[RasvenRegistrationViewModel::class.java]
            assertEquals(1, gateway.starts)
            assertEquals(1, owner.startCount)
            assertTrue(owner.state is RasvenRegistrationState.Registered)
            assertEquals(microphoneBefore, activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            assertEquals(contactsBefore, activity.checkSelfPermission(Manifest.permission.READ_CONTACTS))
            assertEquals(notificationsBefore, activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            assertTrue(activity.window.decorView.hasWindowFocus())
        }
    }

    @Test
    fun stage5A7SurvivesRecreationBackgroundAndCtaEntersRealHomeWithoutRegistrationRestart() {
        val gateway = ControllableRegistrationGateway()
        RasvenRuntimeRegistrationGateway.installQaOverride(gateway)
        reachStage5A5()
        composeRule.onNodeWithTag("permissions_continue_connect").performClick()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
        composeRule.runOnIdle { gateway.emit(RasvenRegistrationResult.Registered) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_success_heading").assertIsDisplayed()

        val before = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
        assertEquals(1, before.startCount)
        assertTrue(before.state is RasvenRegistrationState.Registered)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_success_heading").assertIsDisplayed()
        val afterRecreation = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
        assertTrue(before === afterRecreation)
        assertEquals(1, afterRecreation.startCount)
        assertTrue(afterRecreation.state is RasvenRegistrationState.Registered)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_success_heading").assertIsDisplayed()
        val afterForeground = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
        assertEquals(1, afterForeground.startCount)
        assertTrue(afterForeground.state is RasvenRegistrationState.Registered)

        val activity = composeRule.activity
        val microphoneBefore = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val contactsBefore = activity.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        val notificationsBefore = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        composeRule.onNodeWithTag("registration_success_enter_dialer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_success_heading").assertDoesNotExist()
        composeRule.onNodeWithTag("home_dialer_screen").assertIsDisplayed()
        composeRule.onNodeWithText("داخلی ۲۰۴").assertIsDisplayed()
        composeRule.onNodeWithTag("home_connected_badge").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, gateway.starts)
            assertEquals(1, afterForeground.startCount)
            assertEquals(microphoneBefore, activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            assertEquals(contactsBefore, activity.checkSelfPermission(Manifest.permission.READ_CONTACTS))
            assertEquals(notificationsBefore, activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            assertTrue(activity.window.decorView.hasWindowFocus())
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_dialer_screen").assertIsDisplayed()
        composeRule.runOnIdle {
            val retained = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
            assertEquals(1, retained.startCount)
            assertTrue(retained.state is RasvenRegistrationState.Registered)
        }
    }

    @Test
    fun failedRegistrationTransitionsStage5A6ToStage5A8WithoutPermissionRegression() {
        val activity = composeRule.activity
        val microphoneBefore = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val contactsBefore = activity.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        val notificationsBefore = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        val gateway = ControllableRegistrationGateway()
        RasvenRuntimeRegistrationGateway.installQaOverride(gateway)

        reachStage5A5()
        composeRule.onNodeWithTag("permissions_continue_connect").performClick()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
        composeRule.runOnIdle {
            gateway.emit(RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("connecting_loading_indicator").assertDoesNotExist()
        composeRule.onNodeWithTag("registration_success_heading").assertDoesNotExist()
        composeRule.onNodeWithTag("registration_failure_heading").assertIsDisplayed()
        composeRule.onNodeWithTag("home_dialer_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("registration_failure_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_failure_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_failure_card").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_failure_retry").assertHasClickAction()
        composeRule.onNodeWithTag("registration_failure_open_settings").assertHasClickAction()
        composeRule.waitUntil(5_000) { activity.window.decorView.hasWindowFocus() }
        composeRule.runOnIdle {
            val owner = ViewModelProvider(activity)[RasvenRegistrationViewModel::class.java]
            assertEquals(1, owner.startCount)
            assertEquals(1, gateway.starts)
            assertTrue(owner.state is RasvenRegistrationState.Failed)
            assertEquals(microphoneBefore, activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            assertEquals(contactsBefore, activity.checkSelfPermission(Manifest.permission.READ_CONTACTS))
            assertEquals(notificationsBefore, activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            assertTrue(activity.window.decorView.hasWindowFocus())
        }
    }

    @Test
    fun stage5A8RetryStartsExactlyOneNewAttemptAndReturnsToStage5A6() {
        val gateway = ControllableRegistrationGateway()
        reachStage5A8(gateway)

        val owner = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
        assertEquals(1, gateway.starts)
        assertEquals(1, owner.startCount)
        assertEquals(1L, owner.currentAttemptId ?: -1L)

        composeRule.onNodeWithTag("registration_failure_retry").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("registration_failure_heading").assertDoesNotExist()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(2, gateway.starts)
            assertEquals(2, owner.startCount)
            assertEquals(2L, owner.currentAttemptId ?: -1L)
            assertTrue(owner.state is RasvenRegistrationState.Registering)
        }

        // A recomposition/idle pass must not produce attempt #3.
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(2, gateway.starts)
            assertEquals(2, owner.startCount)
        }

        composeRule.runOnIdle {
            gateway.emit(RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_failure_heading").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(2, gateway.starts)
            assertEquals(2, owner.startCount)
            assertTrue(owner.state is RasvenRegistrationState.Failed)
        }
    }

    @Test
    fun stage5A8RetryThenRecreationPreservesAttemptTwoWithoutDuplicateStart() {
        val gateway = ControllableRegistrationGateway()
        reachStage5A8(gateway)

        composeRule.onNodeWithTag("registration_failure_retry").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
        val before = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
        assertEquals(2, gateway.starts)
        assertEquals(2, before.startCount)
        assertEquals(2L, before.currentAttemptId ?: -1L)
        assertTrue(before.state is RasvenRegistrationState.Registering)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
        val after = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
        assertTrue(before === after)
        assertEquals(2, gateway.starts)
        assertEquals(2, after.startCount)
        assertEquals(2L, after.currentAttemptId ?: -1L)
        assertTrue(after.state is RasvenRegistrationState.Registering)
    }

    @Test
    fun stage5A8OpenSettingsReturnsToStage5A2WithoutStartingRegistrationOrRecoveringPassword() {
        val gateway = ControllableRegistrationGateway()
        reachStage5A8(gateway)
        val owner = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]

        composeRule.onNodeWithTag("registration_failure_open_settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("registration_failure_heading").assertDoesNotExist()
        composeRule.onNodeWithText("حساب IAX2").assertIsDisplayed()
        composeRule.onNodeWithTag("account_server").assertIsDisplayed()
        composeRule.onNodeWithTag("account_password").assertIsDisplayed()
        composeRule.onNodeWithText("p".repeat(8)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, gateway.starts)
            assertEquals(1, owner.startCount)
            assertTrue(owner.state is RasvenRegistrationState.Idle)
            assertTrue(owner.currentAttemptId == null)
        }
    }

    @Test
    fun stage5A8FailedStateSurvivesRecreationAndBackgroundWithoutAutomaticRetry() {
        val gateway = ControllableRegistrationGateway()
        reachStage5A8(gateway)
        val before = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
        assertEquals(1, gateway.starts)
        assertEquals(1, before.startCount)
        assertTrue(before.state is RasvenRegistrationState.Failed)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_failure_heading").assertIsDisplayed()
        val afterRecreation = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
        assertTrue(before === afterRecreation)
        assertEquals(1, gateway.starts)
        assertEquals(1, afterRecreation.startCount)
        assertTrue(afterRecreation.state is RasvenRegistrationState.Failed)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_failure_heading").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, gateway.starts)
            assertEquals(1, afterRecreation.startCount)
            assertTrue(afterRecreation.state is RasvenRegistrationState.Failed)
        }
    }


    @Test
    fun homeRecoverySuccessStaysInHomeStartsOnceAndRestoresDestinationAndDnd() {
        val gateway = ControllableRegistrationGateway()
        reachConnectedHome(gateway)
        val activity = composeRule.activity
        val microphoneBefore = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val contactsBefore = activity.checkSelfPermission(Manifest.permission.READ_CONTACTS)
        val notificationsBefore = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        val registrationOwner = ViewModelProvider(activity)[RasvenRegistrationViewModel::class.java]
        val homeOwner = ViewModelProvider(activity)[RasvenHomeDialerViewModel::class.java]

        composeRule.runOnIdle {
            homeOwner.setDestination("305")
            RasvenCallDomain.setDoNotDisturb(true)
            homeOwner.setDoNotDisturb(true)
            registrationOwner.markConnectionLost()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("اتصال قطع است").assertIsDisplayed()
        composeRule.onNodeWithTag("home_disconnected_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("home_destination_input").assertIsNotEnabled().assertTextContains("305")
        composeRule.onNodeWithTag("home_dnd_switch").assertDoesNotExist()
        composeRule.onNodeWithTag("home_call_action").assertDoesNotExist()
        composeRule.onNodeWithTag("home_reconnect").assertHasClickAction()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertDoesNotExist()
        composeRule.onNodeWithTag("registration_failure_heading").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, gateway.starts)
            assertEquals(1, registrationOwner.startCount)
            assertTrue(registrationOwner.state is RasvenRegistrationState.Unregistered)
        }

        composeRule.onNodeWithTag("home_reconnect").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("در حال بازیابی اتصال").assertIsDisplayed()
        composeRule.onNodeWithTag("home_reconnecting_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("home_reconnect").assertDoesNotExist()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(2, gateway.starts)
            assertEquals(2, registrationOwner.startCount)
            assertTrue(registrationOwner.state is RasvenRegistrationState.Registering)
        }

        // Activity recreation and another idle pass cannot create a third reconnect attempt.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_reconnecting_badge").assertIsDisplayed()
        val retainedRegistration = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]
        val retainedHome = ViewModelProvider(composeRule.activity)[RasvenHomeDialerViewModel::class.java]
        composeRule.runOnIdle {
            assertEquals(2, gateway.starts)
            assertEquals(2, retainedRegistration.startCount)
            assertEquals("305", retainedHome.localState.destination)
            assertTrue(retainedHome.localState.doNotDisturb)
        }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(2, gateway.starts)
            assertEquals(2, retainedRegistration.startCount)
            gateway.emit(RasvenRegistrationResult.Registered)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_connected_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("home_destination_input").assertTextContains("305")
        composeRule.onNodeWithTag("home_dnd_switch").assertIsOn()
        composeRule.onNodeWithTag("home_call_action").assertIsEnabled()
        composeRule.runOnIdle {
            assertTrue(retainedRegistration.state is RasvenRegistrationState.Registered)
            assertEquals(microphoneBefore, composeRule.activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            assertEquals(contactsBefore, composeRule.activity.checkSelfPermission(Manifest.permission.READ_CONTACTS))
            assertEquals(notificationsBefore, composeRule.activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    @Test
    fun homeRecoveryFailureReturnsDisconnectedWithoutSetupA8OrAutomaticRetry() {
        val gateway = ControllableRegistrationGateway()
        reachConnectedHome(gateway)
        val owner = ViewModelProvider(composeRule.activity)[RasvenRegistrationViewModel::class.java]

        composeRule.runOnIdle { owner.markConnectionLost() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_disconnected_badge").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, gateway.starts)
            assertEquals(1, owner.startCount)
        }

        composeRule.onNodeWithTag("home_reconnect").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_reconnecting_badge").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(2, gateway.starts)
            assertEquals(2, owner.startCount)
            gateway.emit(RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_disconnected_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("home_reconnect").assertHasClickAction()
        composeRule.onNodeWithTag("registration_failure_heading").assertDoesNotExist()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(2, gateway.starts)
            assertEquals(2, owner.startCount)
            assertTrue(owner.state is RasvenRegistrationState.Unregistered)
        }
    }

    @Test
    fun stage5B5SettingsIsFourthRealDestinationAndCrossNavigationPreservesHomeState() {
        val gateway = ControllableRegistrationGateway()
        reachConnectedHome(gateway)
        val homeOwner = ViewModelProvider(composeRule.activity)[RasvenHomeDialerViewModel::class.java]
        val contactsOwner = ViewModelProvider(composeRule.activity)[com.rasven.android.product.contacts.RasvenContactsViewModel::class.java]
        val historyOwner = ViewModelProvider(composeRule.activity)[com.rasven.android.product.history.RasvenHistoryViewModel::class.java]
        composeRule.runOnIdle {
            homeOwner.setDestination("305")
            contactsOwner.updateQuery("پایدار")
            historyOwner.selectFilter(com.rasven.android.product.history.RasvenHistoryFilter.Missed)
        }

        composeRule.onNodeWithText("تنظیمات").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithText("تنظیمات روزمره و حساب").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("305", homeOwner.localState.destination)
            assertEquals("پایدار", contactsOwner.query)
            assertEquals(com.rasven.android.product.history.RasvenHistoryFilter.Missed, historyOwner.selectedFilter)
        }

        composeRule.onNodeWithText("خانه").performClick()
        composeRule.onNodeWithTag("home_destination_input").assertTextContains("305")
        composeRule.onNodeWithText("تنظیمات").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeRule.onNodeWithText("سوابق").performClick()
        composeRule.onNodeWithTag("history_bottom_navigation").assertIsDisplayed()
        composeRule.onNodeWithText("تنظیمات").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeRule.onNodeWithText("مخاطبین").performClick()
        composeRule.onNodeWithTag("contacts_heading").assertIsDisplayed()
        composeRule.onNodeWithText("تنظیمات").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("305", homeOwner.localState.destination) }
    }

    @Test
    fun advancedSettingsRemainsAvailableAfterOnboardingWithoutExposingPassword() {
        val gateway = ControllableRegistrationGateway()
        reachConnectedHome(gateway)
        composeRule.onNodeWithText("تنظیمات").performClick()
        composeRule.onNodeWithText("تنظیمات پیشرفته").performClick()
        composeRule.onNodeWithTag("settings_advanced_server").assertTextContains("pbx.permissions.example")
        composeRule.onNodeWithTag("settings_advanced_port").assertTextContains("4569")
        composeRule.onNodeWithTag("settings_advanced_username").assertTextContains("204")
        composeRule.onNodeWithTag("settings_advanced_password").assertTextEquals("")
        composeRule.onNodeWithTag("settings_advanced_save").assertHasClickAction()
        val restored = RasvenSecureSetupRepository(composeRule.activity).load()
        assertEquals("p".repeat(8), restored?.account?.password)
    }

    @Test
    fun stage5B5PrefixSaveCommitsToExistingContactsOwnerOnlyOnSave() {
        val gateway = ControllableRegistrationGateway()
        reachConnectedHome(gateway)
        composeRule.onNodeWithText("تنظیمات").performClick()
        composeRule.onNodeWithTag("settings_row_contact_prefix").performClick()

        val contactsOwner = ViewModelProvider(composeRule.activity)[com.rasven.android.product.contacts.RasvenContactsViewModel::class.java]
        val before = contactsOwner.contactPrefix
        composeRule.onNodeWithTag("settings_prefix_input").performTextReplacement("81")
        composeRule.runOnIdle { assertEquals(before, contactsOwner.contactPrefix) }

        composeRule.onNodeWithTag("settings_prefix_save").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("81", contactsOwner.contactPrefix) }
    }

    @Test
    fun launcherKeepsDarkSystemBarIconsForLightLockedProduct() {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertTrue(controller.isAppearanceLightStatusBars)
            assertTrue(controller.isAppearanceLightNavigationBars)
        }
    }


    private fun reachConnectedHome(gateway: ControllableRegistrationGateway) {
        RasvenRuntimeRegistrationGateway.installQaOverride(gateway)
        reachStage5A5()
        composeRule.onNodeWithTag("permissions_continue_connect").performClick()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
        composeRule.runOnIdle { gateway.emit(RasvenRegistrationResult.Registered) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_success_heading").assertIsDisplayed()
        composeRule.onNodeWithTag("registration_success_enter_dialer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_connected_badge").assertIsDisplayed()
    }

    private fun reachStage5A8(gateway: ControllableRegistrationGateway) {
        RasvenRuntimeRegistrationGateway.installQaOverride(gateway)
        reachStage5A5()
        composeRule.onNodeWithTag("permissions_continue_connect").performClick()
        composeRule.onNodeWithTag("connecting_loading_indicator").assertIsDisplayed()
        composeRule.runOnIdle {
            gateway.emit(RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("registration_failure_heading").assertIsDisplayed()
    }

    private fun reachStage5A5() {
        composeRule.onNodeWithTag("welcome_continue").performClick()
        submitValidStage5A2(
            server = "pbx.permissions.example",
            port = "4569",
            username = "204",
            password = "p".repeat(8),
        )
        composeRule.onNodeWithTag("call_settings_save_connect")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("advanced_settings_save").assertDoesNotExist()
        composeRule.onNodeWithText("دسترسی‌های تماس").assertIsDisplayed()
    }

    /**
     * Drives valid Stage5A2 input through the production focus/IME contract.
     * The RTL row focus order is Server -> Username -> Port -> Password. Using
     * synthetic out-of-order text injection here previously made Bootstrap flow
     * tests intermittently remain on Stage5A2 before the Stage5A3 click.
     */
    private fun submitValidStage5A2(
        server: String,
        port: String,
        username: String,
        password: String,
    ) {
        composeRule.onNodeWithTag("account_server")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("account_server").performTextInput(server)
        composeRule.onNodeWithTag("account_server").performImeAction()

        composeRule.onNodeWithTag("account_username").assertIsFocused()
        composeRule.onNodeWithTag("account_username").performTextInput(username)
        composeRule.onNodeWithTag("account_username").performImeAction()

        composeRule.onNodeWithTag("account_port").assertIsFocused()
        composeRule.onNodeWithTag("account_port").performTextInput(port)
        composeRule.onNodeWithTag("account_port").performImeAction()

        composeRule.onNodeWithTag("account_password").assertIsFocused()
        composeRule.onNodeWithTag("account_password").performTextInput(password)
        composeRule.onNodeWithTag("account_password").performImeAction()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("account_continue")
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("تنظیمات تماس").assertIsDisplayed()
    }
}
