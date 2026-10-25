package com.rasven.android.product.call

import org.junit.Assert.*
import org.junit.Test

class RasvenRingerPolicyTest {
    private fun policy(allowed: Boolean = true, mode: Int = 2, volume: Int = 8,
                       sound: Boolean = true, vibration: Boolean = true,
                       hardware: Boolean = true, preference: Boolean = true) =
        RasvenRingerPolicy.evaluate(allowed, mode, volume, sound, vibration, hardware, preference)
    @Test fun silentNeverMakesSoundOrVibrates() {
        assertEquals(RasvenRingerPolicy(false, false), policy(mode = 0))
    }
    @Test fun vibrateModeNeverMakesSound() {
        assertEquals(RasvenRingerPolicy(false, true), policy(mode = 1, preference = false))
    }
    @Test fun dndOrBlockedChannelNeverMakesFeedback() {
        for (mode in 0..2) assertEquals(RasvenRingerPolicy(false, false), policy(allowed = false, mode = mode))
    }
    @Test fun normalRespectsVolumeChannelAndVibrationPreference() {
        assertEquals(RasvenRingerPolicy(true, true), policy())
        assertFalse(policy(volume = 0).sound)
        assertFalse(policy(sound = false).sound)
        assertFalse(policy(vibration = false).vibrate)
        assertFalse(policy(hardware = false).vibrate)
        assertFalse(policy(preference = false).vibrate)
    }
}
