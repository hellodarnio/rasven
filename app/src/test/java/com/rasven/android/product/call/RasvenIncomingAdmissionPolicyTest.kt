package com.rasven.android.product.call

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RasvenIncomingAdmissionPolicyTest {
    @Test fun dndSuppressesOneSessionAndDuplicateCannotCreateSecondOutcome() {
        val policy = RasvenIncomingAdmissionPolicy(true)
        assertEquals(RasvenIncomingAdmissionPolicy.Outcome.Suppress, policy.decide(41))
        assertEquals(RasvenIncomingAdmissionPolicy.Outcome.Duplicate, policy.decide(41))
        policy.setDoNotDisturb(false)
        assertEquals(RasvenIncomingAdmissionPolicy.Outcome.Duplicate, policy.decide(41))
        assertEquals(RasvenIncomingAdmissionPolicy.Outcome.Present, policy.decide(42))
    }

    @Test fun togglingDndDuringAdmissionKeepsOnePolicyOutcome() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val gate = CountDownLatch(1)
            val decision = pool.submit<RasvenIncomingAdmissionPolicy.Outcome> {
                gate.await()
                policyForRace.decide(91)
            }
            val toggle = pool.submit {
                gate.await()
                policyForRace.setDoNotDisturb(true)
            }
            gate.countDown()
            val first = decision.get(3, TimeUnit.SECONDS)
            toggle.get(3, TimeUnit.SECONDS)
            assertEquals(RasvenIncomingAdmissionPolicy.Outcome.Duplicate, policyForRace.decide(91))
            assert(first == RasvenIncomingAdmissionPolicy.Outcome.Present ||
                first == RasvenIncomingAdmissionPolicy.Outcome.Suppress)
        } finally {
            pool.shutdownNow()
        }
    }

    private val policyForRace = RasvenIncomingAdmissionPolicy()
}
