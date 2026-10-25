package com.rasven.android.designsystem.foundation

import org.junit.Assert.*
import org.junit.Test

class RasvenDailyBottomContractTest {
    @Test fun navigationUsesFigmaSafeAreaBaselineWithoutExtraLift() {
        assertEquals(0f, RasvenDailyBottomContract.liftDp(640f), 0f)
        assertEquals(0f, RasvenDailyBottomContract.liftDp(800f), 0f)
        assertEquals(0f, RasvenDailyBottomContract.liftDp(828f), 0f)
        assertEquals(0f, RasvenDailyBottomContract.liftDp(0f), 0f)
    }
    @Test fun contentEndsSixteenDpBeforeSeventyTwoDpNavigation() {
        for (height in listOf(480f, 640f, 828f)) {
            val end = 205f + RasvenDailyBottomContract.contentHeightDp(height, 205f)
            val barTop = height - 72f
            assertEquals(16f, barTop - end, 0.001f)
        }
    }
    @Test fun impossibleShortViewportClampsContentInsteadOfNegativeMeasure() {
        assertEquals(0f, RasvenDailyBottomContract.contentHeightDp(200f, 262f), 0f)
    }
    @Test fun bottomAnchorsRetainTheirOriginalGapWhenBothUseSameLift() {
        val lift = RasvenDailyBottomContract.liftDp(828f)
        assertEquals(16f, (88f + lift) - (72f + lift), 0.001f)
        assertEquals(68f, (156f + lift) - (88f + lift), 0.001f)
    }
    @Test fun invalidGeometryFailsClosed() {
        for (height in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { RasvenDailyBottomContract.liftDp(height) }
        }
        assertThrows(IllegalArgumentException::class.java) { RasvenDailyBottomContract.contentHeightDp(800f, -1f) }
    }
}
