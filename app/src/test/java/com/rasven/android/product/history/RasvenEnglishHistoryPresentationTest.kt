package com.rasven.android.product.history

import com.rasven.android.designsystem.foundation.RasvenLanguage
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RasvenEnglishHistoryPresentationTest {
    private fun instant(value: String) = Instant.parse(value).toEpochMilli()
    private val now = instant("2026-10-11T10:00:00Z")
    @Test fun englishUsesAsciiTimeYesterdayAndSameCalendar() {
        val zone = ZoneId.of("UTC")
        assertEquals("09:15", formatHistoryMoment(instant("2026-10-11T09:15:00Z"), now, zone, RasvenLanguage.English))
        assertEquals("Yesterday", formatHistoryMoment(instant("2026-10-10T23:59:00Z"), now, zone, RasvenLanguage.English))
        assertEquals("2026/10/09", formatHistoryMoment(instant("2026-10-09T09:15:00Z"), now, zone, RasvenLanguage.English))
    }
    @Test fun bothLanguagesUseInjectedLocalDateNotDeviceZone() {
        val now = instant("2026-10-11T00:05:00Z")
        val timestamp = instant("2026-10-10T23:55:00Z")
        assertEquals("Yesterday", formatHistoryMoment(timestamp, now, ZoneId.of("UTC"), RasvenLanguage.English))
        assertEquals("03:25", formatHistoryMoment(timestamp, now, ZoneId.of("Asia/Tehran"), RasvenLanguage.English))
        assertEquals("۰۳:۲۵", formatHistoryMoment(timestamp, now, ZoneId.of("Asia/Tehran")))
    }
    @Test fun defaultAndExplicitPersianRemainIdenticalAcrossDates() {
        for (day in listOf("11", "10", "09")) {
            val timestamp = instant("2026-10-${day}T09:15:00Z")
            assertEquals(formatHistoryMoment(timestamp, now, ZoneId.of("UTC")),
                formatHistoryMoment(timestamp, now, ZoneId.of("UTC"), RasvenLanguage.Persian))
        }
    }
    @Test fun englishUsesLiveFigmaWiderYesterdaySlotWithoutChangingClockWidth() {
        assertEquals(56, historyMomentWidthDp("09:15", RasvenLanguage.English))
        assertEquals(64, historyMomentWidthDp("Yesterday", RasvenLanguage.English))
        assertEquals(64, historyMomentWidthDp("2026/10/09", RasvenLanguage.English))
    }
    @Test fun persianMomentGeometryIsUnchanged() {
        for (value in listOf("۰۹:۱۵", "دیروز", "۲۰۲۶/۱۰/۰۹")) {
            assertEquals(56, historyMomentWidthDp(value, RasvenLanguage.Persian))
        }
    }
}
