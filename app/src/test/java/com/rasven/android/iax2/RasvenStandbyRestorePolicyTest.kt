package com.rasven.android.iax2

import org.junit.Assert.assertEquals
import org.junit.Test

class RasvenStandbyRestorePolicyTest {
    @Test fun restoreRequiresAllThreePrerequisitesForEveryCombination() {
        for (paused in listOf(false, true)) for (account in listOf(false, true)) for (ready in listOf(false, true)) {
            assertEquals(!paused && account && ready, shouldRestoreStandbyConnection(paused, account, ready))
        }
    }
}
