package com.rasven.android.product.call

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class RasvenIncomingRingerTest {
    @Test fun feedbackOwnershipSurvivesTenSecondsAndStaleStopCannotCancelReplacement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        RasvenCallNotification(context) // Establish both channels without modifying device mode.
        val ringer = RasvenIncomingRinger(context)
        try {
            instrumentation.runOnMainSync { ringer.start(10) }
            Thread.sleep(2_000)
            instrumentation.runOnMainSync {
                if (ringer.expectsSound) assertTrue("Audible policy must start the actual player", ringer.isSoundPlaying)
            }
            Thread.sleep(8_000)
            instrumentation.runOnMainSync {
                assertEquals(10, ringer.ownedNotificationId)
                if (ringer.expectsSound) assertTrue("Actual player must persist beyond the two-second regression", ringer.isSoundPlaying)
            }
            // This is an ownership/lifetime test in the device's existing sound mode, not
            // evidence that a human heard audio. Silent/DND behaviour is also intentional.
            instrumentation.runOnMainSync {
                ringer.start(11)
                ringer.stop(10)
                assertEquals(11, ringer.ownedNotificationId)
                ringer.stop(11)
                assertNull(ringer.ownedNotificationId)
                assertFalse(ringer.isSoundPlaying)
            }
        } finally { instrumentation.runOnMainSync { ringer.stopAll() } }
    }
}
