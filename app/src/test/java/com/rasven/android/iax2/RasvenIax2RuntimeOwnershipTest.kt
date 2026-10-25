package com.rasven.android.iax2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenIax2RuntimeOwnershipTest {
    @Test fun staleCleanupCannotStopServiceOwnedByAnyCurrentCallLayer() {
        assertFalse(shouldStopCallMediaService(true, null, null, null))
        assertFalse(shouldStopCallMediaService(false, 12L, null, null))
        assertFalse(shouldStopCallMediaService(false, null, 13L, null))
        assertFalse(shouldStopCallMediaService(false, null, null, 14L))
        assertTrue(shouldStopCallMediaService(false, null, null, null))
    }
}
