package com.rasven.android.product.setup

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/** Password's neutral mask characters must not decide caret alignment. */
class RasvenAccountPasswordAlignmentTest {
    @Test fun persianRtlPasswordStartsAtRightEdgeEvenWithLtrCredentialDirection() {
        assertEquals(
            TextAlign.Right,
            RasvenAccountInformationContract.passwordValueAlignment(LayoutDirection.Rtl),
        )
    }

    @Test fun futureEnglishLtrPasswordStartsAtLeftEdge() {
        assertEquals(
            TextAlign.Left,
            RasvenAccountInformationContract.passwordValueAlignment(LayoutDirection.Ltr),
        )
    }
}
