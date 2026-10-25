package com.rasven.android.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rasven.android.product.contacts.RasvenContactsViewModel
import com.rasven.android.product.setup.RasvenRegistrationGateway
import com.rasven.android.product.setup.RasvenRegistrationState
import com.rasven.android.product.setup.RasvenRegistrationViewModel
import com.rasven.android.product.setup.RasvenRuntimeRegistrationGateway
import com.rasven.android.product.setup.RasvenSecureSetupRepository
import com.rasven.android.product.setup.RasvenValidatedAccount
import com.rasven.android.product.settings.RasvenRequiredCallAccess
import com.rasven.android.product.settings.RasvenRequiredCallAccessStatus
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasvenSavedSetupRestoreTest {
    private val repository: RasvenSecureSetupRepository
        get() = RasvenSecureSetupRepository(ApplicationProvider.getApplicationContext())

    @Before fun arrangeReadyAndUserEnabledRestore() {
        // This fixture injects a registration gateway, not Android permission dialogs. Arrange
        // its prerequisites explicitly rather than depending on earlier tests' shared state.
        RasvenRequiredCallAccess.setTestOverride(RasvenRequiredCallAccessStatus(true, true))
        connectionPreferences().edit().putBoolean("paused", false).commit()
        repository.clear()
    }

    private fun connectionPreferences() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("rasven_user_connection_v1", Context.MODE_PRIVATE)

    @After fun clear() {
        RasvenRuntimeRegistrationGateway.installQaOverride(null)
        RasvenRequiredCallAccess.setTestOverride(null)
        connectionPreferences().edit().putBoolean("paused", false).commit()
        repository.clear()
    }

    @Test fun savedAccountAndPrefixRestoreIntoOneRegistrationAttempt() {
        val account = RasvenValidatedAccount("pbx.restore.example", 4569, "204", "saved-secret-42")
        assertTrue(repository.save(account, "81"))
        var starts = 0
        var received: RasvenValidatedAccount? = null
        RasvenRuntimeRegistrationGateway.installQaOverride(RasvenRegistrationGateway { value, _ ->
            starts += 1
            received = value
        })
        ActivityScenario.launch(BootstrapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val registration = ViewModelProvider(activity)[RasvenRegistrationViewModel::class.java]
                val contacts = ViewModelProvider(activity)[RasvenContactsViewModel::class.java]
                assertEquals(1, starts)
                assertEquals(1, registration.startCount)
                assertTrue(registration.state is RasvenRegistrationState.Registering)
                assertEquals(account.password, received?.password)
                assertEquals("81", contacts.contactPrefix)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(1, starts)
                assertEquals(1, ViewModelProvider(activity)[RasvenRegistrationViewModel::class.java].startCount)
            }
        }
    }

    @Test fun savedAccountWithoutRequiredAccessDoesNotAutoRegister() {
        RasvenRequiredCallAccess.setTestOverride(RasvenRequiredCallAccessStatus(false, false))
        assertBlockedRestore()
    }

    @Test fun explicitUserDisconnectIsPreservedWhenSavedAccountRestores() {
        connectionPreferences().edit().putBoolean("paused", true).commit()
        assertBlockedRestore()
    }

    private fun assertBlockedRestore() {
        assertTrue(repository.save(RasvenValidatedAccount("pbx.restore.example", 4569,
            "fixture", "fixture-only"), "81"))
        var starts = 0
        RasvenRuntimeRegistrationGateway.installQaOverride(RasvenRegistrationGateway { _, _ -> starts += 1 })
        ActivityScenario.launch(BootstrapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val registration = ViewModelProvider(activity)[RasvenRegistrationViewModel::class.java]
                assertEquals(0, starts)
                assertEquals(0, registration.startCount)
                assertTrue(registration.state is RasvenRegistrationState.Unregistered)
            }
            scenario.recreate()
            scenario.onActivity { assertEquals(0, starts) }
        }
    }
}
