package com.rasven.android.product.setup

/** Bounded short-term recovery. Never retries first setup, credentials or explicit Disconnect. */
internal class RasvenRegistrationRecoveryPolicy {
    private var connectedBefore = false
    private var blocked = false
    private var attempts = 0
    private val delays = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)
    val keepStandby: Boolean get() = connectedBefore && !blocked

    fun explicitStart() { connectedBefore = false; blocked = false; attempts = 0 }
    fun registered() { connectedBefore = true; blocked = false; attempts = 0 }
    fun stop() { connectedBefore = false; blocked = true; attempts = 0 }
    fun failed(retryable: Boolean): Long? {
        if (!connectedBefore || !retryable) { blocked = true; return null }
        if (blocked || attempts >= delays.size) return null
        return delays[attempts++]
    }
    /** A real default-network transition may replenish the finite budget, never a blocked user intent. */
    fun networkChanged(): Long? {
        if (!keepStandby) return null
        attempts = 0
        return failed(true)
    }
}
