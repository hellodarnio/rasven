package com.rasven.android.diagnostics

import android.util.Log

/** Investigation metadata only. Never pass caller identifiers, secrets, digits or media. */
internal object RasvenDiagnostics {
    fun debug(tag: String, message: String) { Log.d(tag, message) }
    fun error(tag: String, message: String) { Log.e(tag, message) }
}
