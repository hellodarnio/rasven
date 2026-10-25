package com.rasven.android.designsystem.components

import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RasvenStage4A1ContractTest {
    @Test fun textFieldDefaultsPersianContentToRightAndEnglishContentToLeft() {
        assertEquals(TextAlign.Right, textFieldDefaultValueAlignment(LayoutDirection.Rtl))
        assertEquals(TextAlign.Left, textFieldDefaultValueAlignment(LayoutDirection.Ltr))
        assertEquals(TextDirection.ContentOrRtl, textFieldDefaultValueDirection(LayoutDirection.Rtl))
        assertEquals(TextDirection.ContentOrLtr, textFieldDefaultValueDirection(LayoutDirection.Ltr))
    }

    @Test fun buttonGeometryMatchesAllFiveFigmaSizes() {
        assertEquals(
            listOf(24, 32, 40, 48, 56),
            RasvenButtonSize.entries.map { buttonGeometry(it).height },
        )
        assertEquals(
            listOf(16, 16, 20, 20, 24),
            RasvenButtonSize.entries.map { buttonGeometry(it).iconSize },
        )
        assertEquals(
            listOf(8, 12, 16, 16, 20),
            RasvenButtonSize.entries.map { buttonGeometry(it).horizontalPadding },
        )
    }

    @Test fun semanticStatesOverrideTransientInteractions() {
        assertEquals(RasvenButtonVisualState.Disabled, buttonVisualState(false, true, true, true, true))
        assertEquals(RasvenButtonVisualState.Loading, buttonVisualState(true, true, true, true, true))
        assertEquals(RasvenButtonVisualState.Pressed, buttonVisualState(true, false, true, true, true))
        assertEquals(RasvenButtonVisualState.Focus, buttonVisualState(true, false, false, true, true))
        assertEquals(RasvenButtonVisualState.Hover, buttonVisualState(true, false, false, false, true))
        assertEquals(RasvenButtonVisualState.Default, buttonVisualState(true, false, false, false, false))
    }

    @Test fun gradientAndBadgeColorsUseFoundationTokens() {
        assertSame(RasvenGradients.primaryPressed, buttonGradient(RasvenButtonVisualState.Pressed))
        assertSame(RasvenGradients.primaryHover, buttonGradient(RasvenButtonVisualState.Hover))
        assertSame(RasvenGradients.primaryDefault, buttonGradient(RasvenButtonVisualState.Focus))
        assertSame(RasvenSemanticColors.statusErrorBorder, badgePalette(RasvenBadgeType.Error).border)
        assertSame(RasvenSemanticColors.statusSuccessText, badgePalette(RasvenBadgeType.Success).text)
    }
}
