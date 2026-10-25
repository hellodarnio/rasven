package com.rasven.android.product.call

import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** Exported launcher intents must not grant arbitrary apps authority to answer or wake over lock. */
internal object RasvenIncomingIntentAuthorization {
    private const val PREFERENCES = "rasven_incoming_intent_authorization_v1"

    fun authorize(context: Context, intent: Intent): Intent =
        authorizer(context).authorize(intent)

    fun isAuthorized(context: Context, intent: Intent?): Boolean =
        authorizer(context).isAuthorized(intent)

    private fun authorizer(context: Context) = RasvenIncomingIntentAuthorizer(
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE),
    )
}

/**
 * Private installation-scoped capability for immutable app-owned call PendingIntents. Unlike a
 * process-local nonce, this survives Android reclaiming the UI process while the notification's
 * PendingIntent remains posted. The preference file is private to this application installation.
 */
internal class RasvenIncomingIntentAuthorizer(private val preferences: SharedPreferences) {
    internal companion object {
        const val EXTRA_AUTHORIZATION = "com.rasven.android.call.AUTHORIZATION"
        private const val TOKEN_KEY = "pending_intent_authorization_token"
    }

    fun authorize(intent: Intent): Intent = synchronized(preferences) {
        val token = tokenLocked() ?: return@synchronized intent
        intent.putExtra(EXTRA_AUTHORIZATION, token)
    }

    fun isAuthorized(intent: Intent?): Boolean = synchronized(preferences) {
        val token = tokenLocked() ?: return@synchronized false
        intent?.getStringExtra(EXTRA_AUTHORIZATION) == token
    }

    private fun tokenLocked(): String? {
        preferences.getString(TOKEN_KEY, null)?.takeIf(String::isNotBlank)?.let { return it }
        val generated = UUID.randomUUID().toString()
        return if (preferences.edit().putString(TOKEN_KEY, generated).commit()) generated else null
    }
}
