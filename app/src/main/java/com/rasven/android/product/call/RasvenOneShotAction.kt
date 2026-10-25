package com.rasven.android.product.call

import java.util.concurrent.atomic.AtomicBoolean

/** Per-lifecycle idempotency gate; unlike a process-wide ID set, it releases with its call record. */
internal class RasvenOneShotAction {
    private val completed = AtomicBoolean(false)

    fun run(action: () -> Unit): Boolean {
        if (!completed.compareAndSet(false, true)) return false
        action()
        return true
    }
}
