package com.rasven.android.product.call

import org.junit.Assert.assertEquals
import org.junit.Test

class RasvenActiveCallViewModelTest {
    @Test fun durationUsesMonotonicElapsedTimeAndPersianDigits() {
        assertEquals("۰۴:۳۲", formatActiveCallDuration(10_000L, 282_000L))
        assertEquals("۰۰:۰۰", formatActiveCallDuration(282_000L, 10_000L))
    }

    @Test fun controlsPersistForSameSessionAndResetForReplacementSession() {
        val owner = RasvenActiveCallViewModel()
        owner.bind(77)
        owner.setSpeaker(true)
        owner.bind(77)
        assertEquals(true, owner.controls.speaker)
        owner.bind(78)
        assertEquals(false, owner.controls.speaker)
        assertEquals(78L, owner.controls.sessionId)
    }

    @Test fun platformHoldObservationOnlyUpdatesCurrentSession() {
        val owner = RasvenActiveCallViewModel()
        owner.bind(77)
        owner.observeHeld(77, true)
        assertEquals(true, owner.controls.held)
        owner.bind(78)
        owner.observeHeld(77, true)
        assertEquals(false, owner.controls.held)
        owner.observeHeld(78, true)
        assertEquals(true, owner.controls.held)
    }
}
