package com.rasven.android.product.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenRegistrationBoundaryTest {
    private class FakeGateway : RasvenRegistrationGateway {
        var starts = 0
        var invalidations = 0
        var detached = 0
        var releaseConfirmed = false
        var recoverySupported = false
        override val supportsAutomaticRecovery: Boolean get() = recoverySupported
        override fun onRegistrationFailed() { if (!recoverySupported) invalidate() }
        val terminals = mutableListOf<(RasvenRegistrationResult) -> Unit>()
        val terminal: ((RasvenRegistrationResult) -> Unit)? get() = terminals.lastOrNull()

        override fun start(
            account: RasvenValidatedAccount,
            onResult: (RasvenRegistrationResult) -> Unit,
        ) {
            starts += 1
            terminals += onResult
        }

        override fun invalidate() {
            invalidations += 1
        }
        override fun disconnect(onComplete: (Boolean) -> Unit) {
            invalidations += 1
            onComplete(releaseConfirmed)
        }
        override fun detachObserver() { detached++ }
    }

    private fun account() = RasvenValidatedAccount(
        server = "pbx.example.net",
        port = 4569,
        username = "204",
        password = "p".repeat(8),
    )

    @Test fun validRecoveryRepublishesRegisteredWithoutNewUiAttempt() {
        val gateway = FakeGateway().apply { recoverySupported = true }
        val owner = RasvenRegistrationViewModel(gateway)
        owner.startOnce(account())
        gateway.terminal!!(RasvenRegistrationResult.Registered)
        gateway.terminal!!(RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown))
        assertTrue(owner.state is RasvenRegistrationState.Unregistered)
        gateway.terminal!!(RasvenRegistrationResult.Registered)
        assertTrue(owner.state is RasvenRegistrationState.Registered)
        assertEquals(1, owner.startCount)
        assertEquals(0, gateway.invalidations)
    }
    @Test fun explicitDisconnectStillIgnoresLateAutoRecoveryResult() {
        val gateway = FakeGateway().apply { recoverySupported = true }
        val owner = RasvenRegistrationViewModel(gateway)
        owner.startOnce(account())
        val callback = gateway.terminal!!
        callback(RasvenRegistrationResult.Registered)
        assertTrue(owner.disconnectFromSettings())
        callback(RasvenRegistrationResult.Registered)
        assertTrue(owner.state is RasvenRegistrationState.Unregistered)
        assertNull(owner.currentAttemptId)
    }

    @Test fun clearingUiOwnerDetachesObserverWithoutDisconnectingStandby() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)
        owner.startOnce(account())
        gateway.terminal!!.invoke(RasvenRegistrationResult.Registered)
        val store = androidx.lifecycle.ViewModelStore()
        store.put("registration", owner)
        store.clear()
        assertEquals(1, gateway.detached)
        assertEquals(0, gateway.invalidations)
        assertNull(owner.currentAttemptId)
        gateway.terminal!!.invoke(RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown))
        assertTrue(owner.state is RasvenRegistrationState.Registered)
    }

    @Test fun unavailableRuntimeFailsClosedInsteadOfLeavingRegistering() {
        val owner = RasvenRegistrationViewModel(RasvenUnavailableRegistrationGateway)
        owner.startOnce(account())
        assertTrue(owner.state is RasvenRegistrationState.Failed)
    }

    @Test
    fun registrationStartsExactlyOnceAndRemainsRegisteringWithoutTerminalResult() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)
        val account = account()

        owner.startOnce(account)
        owner.startOnce(account)

        assertEquals(1, gateway.starts)
        assertEquals(1, owner.startCount)
        assertEquals(1L, owner.currentAttemptId ?: -1L)
        assertTrue(owner.state is RasvenRegistrationState.Registering)
    }

    @Test
    fun registeredResultIsSurfacedAsStableProductState() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)

        owner.startOnce(account())
        gateway.terminal?.invoke(RasvenRegistrationResult.Registered)

        assertTrue(owner.state is RasvenRegistrationState.Registered)
    }

    @Test
    fun registeredStateIgnoresDuplicateTerminalResultsAndRepeatedStart() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)
        val account = account()

        owner.startOnce(account)
        gateway.terminal?.invoke(RasvenRegistrationResult.Registered)
        gateway.terminal?.invoke(RasvenRegistrationResult.Registered)
        owner.startOnce(account)

        assertEquals(1, gateway.starts)
        assertEquals(1, owner.startCount)
        assertEquals(1L, owner.currentAttemptId ?: -1L)
        assertTrue(owner.state is RasvenRegistrationState.Registered)
    }

    @Test
    fun failedResultIsSurfacedAsProductSafeCategory() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)

        owner.startOnce(account())
        gateway.terminal?.invoke(
            RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown),
        )

        val state = owner.state
        assertTrue(state is RasvenRegistrationState.Failed)
        assertEquals(
            RasvenRegistrationErrorCategory.Unknown,
            (state as RasvenRegistrationState.Failed).category,
        )
    }

    @Test
    fun retryFromFailedStartsExactlyOneNewIdentifiableAttempt() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)

        owner.startOnce(account())
        gateway.terminals[0](
            RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown),
        )
        assertTrue(owner.state is RasvenRegistrationState.Failed)
        assertEquals(1L, owner.currentAttemptId ?: -1L)

        owner.retryFailedAttempt()
        owner.retryFailedAttempt()

        assertEquals(2, gateway.starts)
        assertEquals(2, owner.startCount)
        assertEquals(2L, owner.currentAttemptId ?: -1L)
        assertTrue(owner.state is RasvenRegistrationState.Registering)
    }

    @Test
    fun staleTerminalFromAttemptOneCannotCompleteRetryAttemptTwo() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)

        owner.startOnce(account())
        val attemptOneTerminal = gateway.terminals[0]
        attemptOneTerminal(
            RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown),
        )
        owner.retryFailedAttempt()
        val attemptTwoTerminal = gateway.terminals[1]

        attemptOneTerminal(RasvenRegistrationResult.Registered)
        assertTrue(owner.state is RasvenRegistrationState.Registering)
        assertEquals(2L, owner.currentAttemptId ?: -1L)

        attemptTwoTerminal(
            RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown),
        )
        assertTrue(owner.state is RasvenRegistrationState.Failed)
        assertEquals(2, owner.startCount)
    }

    @Test
    fun prepareForAccountEditAfterFailureDoesNotStartRegistrationAndClearsAttemptIdentity() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)

        owner.startOnce(account())
        gateway.terminal?.invoke(
            RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown),
        )
        owner.prepareForAccountEditAfterFailure()

        assertEquals(1, gateway.starts)
        assertEquals(1, owner.startCount)
        assertNull(owner.currentAttemptId)
        assertTrue(owner.state is RasvenRegistrationState.Idle)
    }

    @Test
    fun registeredConnectionLossBecomesUnregisteredWithoutStartingAnything() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)
        owner.startOnce(account())
        gateway.terminals[0](RasvenRegistrationResult.Registered)

        owner.markConnectionLost()
        owner.markConnectionLost()

        assertTrue(owner.state is RasvenRegistrationState.Unregistered)
        assertEquals(1, gateway.starts)
        assertEquals(1, owner.startCount)
        assertEquals(1, gateway.invalidations)
        assertEquals("204", owner.currentUsernameForDisplay())
    }

    @Test
    fun registeredRefreshFailureBecomesUnregisteredWithoutCreatingNewAttemptIdentity() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)
        owner.startOnce(account())
        val runtimeCallback = gateway.terminals[0]
        runtimeCallback(RasvenRegistrationResult.Registered)
        assertTrue(owner.state is RasvenRegistrationState.Registered)

        runtimeCallback(RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown))

        assertTrue(owner.state is RasvenRegistrationState.Unregistered)
        assertEquals(1, gateway.starts)
        assertEquals(1L, owner.currentAttemptId ?: -1L)
    }

    @Test
    fun homeReconnectStartsExactlyOneAttemptAndSuccessReturnsRegistered() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)
        owner.startOnce(account())
        gateway.terminals[0](RasvenRegistrationResult.Registered)
        owner.markConnectionLost()

        owner.reconnectFromHome()
        owner.reconnectFromHome()

        assertEquals(2, gateway.starts)
        assertEquals(2, owner.startCount)
        assertTrue(owner.state is RasvenRegistrationState.Registering)
        gateway.terminals[1](RasvenRegistrationResult.Registered)
        assertTrue(owner.state is RasvenRegistrationState.Registered)
    }

    @Test fun explicitSettingsDisconnectInvalidatesOnceAndPreservesReconnectAccount() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)
        owner.startOnce(account())
        gateway.terminals[0](RasvenRegistrationResult.Registered)

        var confirmed: Boolean? = null
        assertTrue(owner.disconnectFromSettings { confirmed = it })
        assertTrue(!owner.disconnectFromSettings())
        assertEquals(false, confirmed)
        assertTrue(owner.state is RasvenRegistrationState.Unregistered)
        assertEquals(1, gateway.invalidations)
        assertEquals("204", owner.currentUsernameForDisplay())

        gateway.terminals[0](RasvenRegistrationResult.Registered)
        assertTrue(owner.state is RasvenRegistrationState.Unregistered)
        owner.reconnectFromHome()
        assertEquals(2, gateway.starts)
        assertTrue(owner.state is RasvenRegistrationState.Registering)
    }

    @Test fun savedAccountWithPausedConnectionRestoresDisconnectedWithoutNetworkAttempt() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)
        owner.restoreSavedAccountOnce(account(), autoConnect = false)
        owner.restoreSavedAccountOnce(account(), autoConnect = true)
        assertTrue(owner.state is RasvenRegistrationState.Unregistered)
        assertEquals(0, gateway.starts)
        assertEquals("204", owner.currentUsernameForDisplay())
        owner.reconnectFromHome()
        assertEquals(1, gateway.starts)
    }

    @Test
    fun homeReconnectFailureReturnsUnregisteredInsteadOfSetupFailure() {
        val gateway = FakeGateway()
        val owner = RasvenRegistrationViewModel(gateway)
        owner.startOnce(account())
        gateway.terminals[0](RasvenRegistrationResult.Registered)
        owner.markConnectionLost()
        owner.reconnectFromHome()

        gateway.terminals[1](RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown))

        assertTrue(owner.state is RasvenRegistrationState.Unregistered)
        assertEquals(2, gateway.starts)
        assertEquals(2, owner.startCount)
    }

    @Test
    fun advancedSettingsMergePreservesOrReplacesCredentialWithoutUiRoundTrip() {
        val original = account()
        val unchanged = original.applyAdvancedSettings(
            RasvenAdvancedSettingsUpdate("pbx-2.example.net", 4570, "205", null),
        )
        val replacement = "n".repeat(12)
        val changed = original.applyAdvancedSettings(
            RasvenAdvancedSettingsUpdate("pbx-3.example.net", 4571, "206", replacement),
        )

        assertEquals("pbx-2.example.net", unchanged.server)
        assertEquals(4570, unchanged.port)
        assertEquals("205", unchanged.username)
        assertTrue(unchanged.password == original.password)
        assertTrue(changed.password == replacement)
    }
}
