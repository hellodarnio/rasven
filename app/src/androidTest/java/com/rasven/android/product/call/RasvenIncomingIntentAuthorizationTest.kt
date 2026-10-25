package com.rasven.android.product.call

import android.content.Intent
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class RasvenIncomingIntentAuthorizationTest {
    @Test fun forgedLauncherActionCannotAcquireIncomingAnswerAuthority() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("incoming_authorization_forgery_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val authorizer = RasvenIncomingIntentAuthorizer(preferences)
        val forged = Intent(RasvenCallNotification.ACTION_ANSWER)
            .putExtra(RasvenCallNotification.EXTRA_CALL_ID, 23L)
        assertFalse(authorizer.isAuthorized(forged))
        assertFalse(authorizer.isAuthorized(null))
        forged.putExtra(RasvenIncomingIntentAuthorizer.EXTRA_AUTHORIZATION, "attacker-supplied")
        assertFalse(authorizer.isAuthorized(forged))
        preferences.edit().clear().commit()
    }

    @Test fun appOwnedPendingIntentAuthoritySurvivesAuthorizerRecreationAfterProcessDeath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("incoming_authorization_process_restart_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val beforeProcessDeath = RasvenIncomingIntentAuthorizer(preferences)
            val own = beforeProcessDeath.authorize(Intent(RasvenCallNotification.ACTION_OPEN_INCOMING))

            // A newly created owner reads the same app-private token, as a cold-start process does.
            val afterProcessDeath = RasvenIncomingIntentAuthorizer(
                context.getSharedPreferences("incoming_authorization_process_restart_test", Context.MODE_PRIVATE),
            )
            assertTrue(afterProcessDeath.isAuthorized(Intent(own)))
            assertFalse(afterProcessDeath.isAuthorized(Intent(own.action)))
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
