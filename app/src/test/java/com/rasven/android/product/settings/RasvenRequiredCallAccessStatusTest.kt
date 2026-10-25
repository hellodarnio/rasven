package com.rasven.android.product.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenRequiredCallAccessStatusTest {
    @Test fun connectionRequiresBothMicrophoneAndCallNotificationReadiness() {
        assertFalse(RasvenRequiredCallAccessStatus(microphoneGranted = false, callNotificationsReady = true).canConnect)
        assertFalse(RasvenRequiredCallAccessStatus(microphoneGranted = true, callNotificationsReady = false).canConnect)
        assertFalse(RasvenRequiredCallAccessStatus(microphoneGranted = false, callNotificationsReady = false).canConnect)
        assertTrue(RasvenRequiredCallAccessStatus(microphoneGranted = true, callNotificationsReady = true).canConnect)
    }
}
