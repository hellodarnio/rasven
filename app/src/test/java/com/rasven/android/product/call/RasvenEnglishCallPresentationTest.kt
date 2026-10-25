package com.rasven.android.product.call

import com.rasven.android.designsystem.foundation.RasvenLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

/** Prepared during development-only phase; NOT RUN until testing is authorized. */
class RasvenEnglishCallPresentationTest {
    @Test fun defaultDurationRemainsPersian() {
        assertEquals("۰۴:۳۲", formatActiveCallDuration(1_000, 273_000))
        assertEquals("۰۰:۰۰", formatActiveCallDuration(273_000, 1_000))
    }

    @Test fun englishDurationUsesAsciiAndRetainsMonotonicClamping() {
        assertEquals("04:32", formatActiveCallDuration(1_000, 273_000, RasvenLanguage.English))
        assertEquals("00:00", formatActiveCallDuration(273_000, 1_000, RasvenLanguage.English))
        assertEquals("125:00", formatActiveCallDuration(1_000, 7_501_000, RasvenLanguage.English))
    }

    @Test fun defaultDestinationFormattingIsPreserved() {
        assertEquals("۰۹۱۲ ۱۲۳ ۴۵۶۷", formatOutgoingDestinationForDisplay("09121234567"))
        assertEquals("۳۰۵", formatOutgoingDestinationForDisplay("305"))
    }

    @Test fun englishPresentationDoesNotTranslateOrMutateDialPlanValues() {
        assertEquals("0912 123 4567", formatOutgoingDestinationForDisplay("09121234567", RasvenLanguage.English))
        assertEquals("*2305#", formatOutgoingDestinationForDisplay("*2305#", RasvenLanguage.English))
        assertEquals("+989121234567", formatOutgoingDestinationForDisplay("+989121234567", RasvenLanguage.English))
    }
}
