package com.rasven.android.product.setup

import com.rasven.android.designsystem.components.RasvenTextFieldSize
import com.rasven.android.designsystem.components.textFieldControlHeight
import com.rasven.android.designsystem.foundation.RasvenResponsive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the unchanged official DS sizes used by the owner-authorized Android-only upgrade. */
class RasvenGlobalTextFieldSizeContractTest {
    @Test fun officialSizeLadderIsUnchangedAndUpgradedControlsUse40And48Dp() {
        assertEquals(32, textFieldControlHeight(RasvenTextFieldSize.Small))
        assertEquals(40, textFieldControlHeight(RasvenTextFieldSize.Medium))
        assertEquals(48, textFieldControlHeight(RasvenTextFieldSize.Large))
        assertEquals(64, textFieldControlHeight(RasvenTextFieldSize.XLarge))
    }

    @Test fun fixedPortAndFlexibleUsernameRemainPositiveAtApprovedWidths() {
        for (widthDp in listOf(320, 360, 412)) {
            val horizontal = RasvenResponsive.pageHorizontalMargin(widthDp)
            val formWidthDp = widthDp - 2 * horizontal
            val usernameWidthDp = formWidthDp -
                RasvenAccountInformationContract.portWidthDp -
                RasvenAccountInformationContract.fieldRowGapDp
            assertTrue("Username has enough width at ${widthDp}dp", usernameWidthDp >= 156)
        }
        assertEquals(12, RasvenAccountInformationContract.formVerticalGapDp)
    }
}
