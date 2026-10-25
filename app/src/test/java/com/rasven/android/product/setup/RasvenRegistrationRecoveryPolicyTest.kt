package com.rasven.android.product.setup

import org.junit.Assert.*
import org.junit.Test

class RasvenRegistrationRecoveryPolicyTest {
    @Test fun firstSetupAndPermanentRejectionNeverAutoRetry() {
        val policy = RasvenRegistrationRecoveryPolicy()
        policy.explicitStart()
        assertNull(policy.failed(true)); assertFalse(policy.keepStandby)
        policy.registered()
        assertNull(policy.failed(false)); assertFalse(policy.keepStandby)
        assertNull(policy.networkChanged())
    }
    @Test fun registeredTransientFailuresUseFiniteIncreasingBackoff() {
        val policy = RasvenRegistrationRecoveryPolicy(); policy.registered()
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L), (1..6).map { policy.failed(true) })
        assertNull(policy.failed(true)); assertTrue(policy.keepStandby)
    }
    @Test fun actualNetworkChangeReplenishesBudgetOnlyForEnabledRecoverableLine() {
        val policy = RasvenRegistrationRecoveryPolicy(); policy.registered()
        repeat(8) { policy.failed(true) }
        assertEquals(1000L, policy.networkChanged())
        assertEquals(2000L, policy.failed(true))
        policy.stop()
        assertNull(policy.networkChanged()); assertNull(policy.failed(true))
    }
    @Test fun explicitDisconnectCannotBeUndoneByLateNetworkOrFailure() {
        val policy = RasvenRegistrationRecoveryPolicy(); policy.registered(); policy.stop()
        repeat(5) { assertNull(policy.networkChanged()); assertNull(policy.failed(true)) }
        assertFalse(policy.keepStandby)
    }
    @Test fun successfulRecoveryResetsBackoffAndUserRetryCanUnblockRejectedCredentials() {
        val policy = RasvenRegistrationRecoveryPolicy(); policy.registered()
        policy.failed(true); policy.failed(true); policy.registered()
        assertEquals(1000L, policy.failed(true))
        policy.failed(false)
        policy.explicitStart(); assertFalse(policy.keepStandby)
        policy.registered(); assertEquals(1000L, policy.failed(true))
    }
}
