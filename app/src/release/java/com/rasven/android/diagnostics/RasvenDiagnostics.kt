package com.rasven.android.diagnostics

/** Release does not write investigation call/protocol metadata to system logs. */
internal object RasvenDiagnostics {
    fun debug(@Suppress("UNUSED_PARAMETER") tag: String, @Suppress("UNUSED_PARAMETER") message: String) = Unit
    fun error(@Suppress("UNUSED_PARAMETER") tag: String, @Suppress("UNUSED_PARAMETER") message: String) = Unit
}
