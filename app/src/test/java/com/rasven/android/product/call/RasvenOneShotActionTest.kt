package com.rasven.android.product.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class RasvenOneShotActionTest {
    @Test fun concurrentTerminalCallbacksFinalizeHistoryOnlyOnce() {
        val gate = RasvenOneShotAction()
        val calls = AtomicInteger()
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            repeat(8) {
                pool.execute {
                    ready.countDown()
                    start.await()
                    gate.run { calls.incrementAndGet() }
                }
            }
            ready.await()
            start.countDown()
            pool.shutdown()
            while (!pool.isTerminated) Thread.yield()
            assertEquals(1, calls.get())
            assertFalse(gate.run { calls.incrementAndGet() })
            assertEquals(1, calls.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun firstTerminalCallbackOwnsFinalization() {
        val gate = RasvenOneShotAction()
        var finalized = false
        assertTrue(gate.run { finalized = true })
        assertTrue(finalized)
        assertFalse(gate.run { finalized = false })
        assertTrue(finalized)
    }
}
