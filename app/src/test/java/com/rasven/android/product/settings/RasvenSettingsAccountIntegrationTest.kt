package com.rasven.android.product.settings

import com.rasven.android.product.setup.RasvenAdvancedSettingsUpdate
import com.rasven.android.product.setup.RasvenRegistrationGateway
import com.rasven.android.product.setup.RasvenRegistrationResult
import com.rasven.android.product.setup.RasvenRegistrationState
import com.rasven.android.product.setup.RasvenRegistrationViewModel
import com.rasven.android.product.setup.RasvenValidatedAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenSettingsAccountIntegrationTest {
    private class CapturingGateway : RasvenRegistrationGateway {
        val accounts = mutableListOf<RasvenValidatedAccount>()
        val terminals = mutableListOf<(RasvenRegistrationResult) -> Unit>()
        var invalidationCount = 0
        override fun start(account: RasvenValidatedAccount, onResult: (RasvenRegistrationResult) -> Unit) {
            accounts += account
            terminals += onResult
        }
        override fun invalidate() { invalidationCount += 1 }
    }

    private fun registeredOwner(gateway: CapturingGateway): RasvenRegistrationViewModel {
        val owner = RasvenRegistrationViewModel(gateway)
        owner.startOnce(RasvenValidatedAccount("pbx.example.net", 4569, "204", "fixture-credential"))
        gateway.terminals[0](RasvenRegistrationResult.Registered)
        return owner
    }

    @Test
    fun nonSecretProjectionNeverContainsPasswordAndSaveRequiresExplicitReconnect() {
        val gateway = CapturingGateway()
        val owner = registeredOwner(gateway)
        val projection = requireNotNull(owner.currentSettingsSnapshot())
        assertEquals("pbx.example.net", projection.server)
        assertEquals(4569, projection.port)
        assertEquals("204", projection.username)
        assertFalse(projection.javaClass.declaredFields.any { it.name.contains("password", ignoreCase = true) })

        assertTrue(owner.applySettingsUpdate(RasvenAdvancedSettingsUpdate("pbx-2.example.net", 4570, "205", null)))
        assertTrue(owner.state is RasvenRegistrationState.Unregistered)
        assertEquals(1, gateway.accounts.size)

        owner.reconnectFromHome()
        assertEquals(2, gateway.accounts.size)
        assertEquals("fixture-credential", gateway.accounts[1].password)
        assertEquals("pbx-2.example.net", gateway.accounts[1].server)
    }

    @Test
    fun transferOnlyAdvancedSaveDoesNotInvalidateRegisteredAccount() {
        val gateway = CapturingGateway()
        val owner = registeredOwner(gateway)
        assertTrue(owner.applySettingsUpdate(RasvenAdvancedSettingsUpdate("pbx.example.net", 4569, "204", null)))
        assertTrue(owner.state is RasvenRegistrationState.Registered)
        assertEquals(0, gateway.invalidationCount)
        assertEquals(1, gateway.accounts.size)
    }

    @Test
    fun nonEmptyPasswordReplacementIsUsedOnlyAtSecureOwnerBoundary() {
        val gateway = CapturingGateway()
        val owner = registeredOwner(gateway)
        owner.applySettingsUpdate(
            RasvenAdvancedSettingsUpdate("pbx-3.example.net", 4571, "206", "replacement-fixture"),
        )
        owner.reconnectFromHome()
        assertEquals("replacement-fixture", gateway.accounts[1].password)
        assertFalse(owner.currentSettingsSnapshot().toString().contains("replacement-fixture"))
    }
    @Test
    fun saveDuringReconnectInvalidatesOldAttemptAndStaleCallbackCannotRestoreRegistered() {
        val gateway = CapturingGateway()
        val owner = registeredOwner(gateway)
        owner.markConnectionLost()
        owner.reconnectFromHome()
        assertTrue(owner.state is RasvenRegistrationState.Registering)
        val staleTerminal = gateway.terminals[1]

        assertTrue(owner.applySettingsUpdate(RasvenAdvancedSettingsUpdate("pbx-4.example.net", 4572, "207", null)))
        assertTrue(owner.state is RasvenRegistrationState.Unregistered)

        staleTerminal(RasvenRegistrationResult.Registered)
        assertTrue(owner.state is RasvenRegistrationState.Unregistered)
    }

}
