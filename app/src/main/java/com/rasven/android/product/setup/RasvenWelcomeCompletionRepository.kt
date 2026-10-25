package com.rasven.android.product.setup

import android.content.Context

/** A completed welcome is not shown again until app data is cleared or the app is reinstalled. */
internal class RasvenWelcomeCompletionRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = preferences.getBoolean(COMPLETED, false)

    fun complete(): Boolean = preferences.edit().putBoolean(COMPLETED, true).commit()

    private companion object {
        const val PREFERENCES = "rasven_welcome_v1"
        const val COMPLETED = "completed"
    }
}
