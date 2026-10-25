package com.rasven.android.designsystem.components

import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenEffects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RasvenStage4ComponentContractTest {
    @Test fun filterChipSizesAndInteractionPrecedenceMatchFigma() {
        assertEquals(listOf(32, 40), RasvenFilterChipSize.entries.map { filterChipGeometry(it).height })
        assertEquals(listOf(12, 16), RasvenFilterChipSize.entries.map { filterChipGeometry(it).horizontalPadding })
        assertEquals(listOf(16, 20), RasvenFilterChipSize.entries.map { filterChipGeometry(it).iconSize })
        assertEquals(RasvenFilterChipVisualState.Disabled, filterChipVisualState(false, true, true, true))
        assertEquals(RasvenFilterChipVisualState.Pressed, filterChipVisualState(true, true, true, true))
        assertEquals(RasvenFilterChipVisualState.Focus, filterChipVisualState(true, false, true, true))
        assertEquals(RasvenFilterChipVisualState.Hover, filterChipVisualState(true, false, false, true))
    }

    @Test fun unselectedFilterChipStillShowsHoverPressedAndFocusFeedback() {
        assertEquals(false, filterChipShowsContainer(false, RasvenFilterChipVisualState.Default))
        assertEquals(true, filterChipShowsContainer(false, RasvenFilterChipVisualState.Hover))
        assertEquals(true, filterChipShowsContainer(false, RasvenFilterChipVisualState.Pressed))
        assertEquals(true, filterChipShowsContainer(false, RasvenFilterChipVisualState.Focus))
        assertEquals(false, filterChipShowsContainer(false, RasvenFilterChipVisualState.Disabled))
        assertEquals(true, filterChipShowsContainer(true, RasvenFilterChipVisualState.Disabled))
    }

    @Test fun historyFilterGlyphsResolveToDistinctOfficialAssets() {
        assertEquals(4, RasvenHistoryIcon.entries.map(::historyIconResource).toSet().size)
    }

    @Test fun callerAndAbstractAvatarIconsResolveToOfficialAssets() {
        assertEquals(7, RasvenAvatarIcon.entries.map(::avatarIconResource).toSet().size)
    }

    @Test fun fieldSizesAndStatePrecedence() {
        assertEquals(listOf(32, 40, 48, 64), RasvenTextFieldSize.entries.map(::textFieldControlHeight))
        assertEquals(listOf(16, 20, 20, 20), RasvenTextFieldSize.entries.map(::textFieldIconSize))
        assertEquals(RasvenTextFieldVisualState.Disabled, textFieldVisualState(false, true, true, true, true, true, true))
        assertEquals(RasvenTextFieldVisualState.ReadOnly, textFieldVisualState(true, true, true, true, true, true, true))
        assertEquals(RasvenTextFieldVisualState.Error, textFieldVisualState(true, false, true, true, true, true, true))
        assertEquals(RasvenTextFieldVisualState.Focus, textFieldVisualState(true, false, false, false, true, true, false))
    }

    @Test fun glassHoverAndBorderlessStayWithinTheirAppearanceContract() {
        assertSame(
            RasvenSemanticColors.borderInputGlass,
            textFieldPaint(RasvenTextFieldAppearance.Glass, RasvenTextFieldVisualState.Hover).border,
        )
        RasvenTextFieldVisualState.entries.forEach {
            assertNull(textFieldPaint(RasvenTextFieldAppearance.Borderless, it).border)
        }
        assertSame(
            RasvenSemanticColors.statusSuccessText,
            textFieldPaint(RasvenTextFieldAppearance.Default, RasvenTextFieldVisualState.Success).helper,
        )
    }

    @Test fun navigationAndCallControlUseOfficialDistinctIconVariants() {
        RasvenNavDestination.entries.forEach {
            assertEquals(false, navIcon(it, false) == navIcon(it, true))
        }
        RasvenCallControlAction.entries.forEach {
            assertEquals(false, callControlIcon(it, false) == callControlIcon(it, true))
        }
        assertEquals("خانه", navLabel(RasvenNavDestination.Home, RasvenBadgeLanguage.Fa))
        assertEquals("Home", navLabel(RasvenNavDestination.Home, RasvenBadgeLanguage.En))
        assertEquals("نگه‌داشتن", callControlLabel(RasvenCallControlAction.Hold, RasvenBadgeLanguage.Fa))
    }

    @Test fun renderedDropShadowGeometryMatchesFoundationContracts() {
        val nav = RasvenEffects.bottomNavigation.shadows.single()
        assertEquals(listOf(14f, 18f, -10f), listOf(nav.offsetYPx, nav.radiusPx, nav.spreadPx))
        val field = RasvenEffects.glassInput.shadows.single()
        assertEquals(listOf(12f, 28f, -10f), listOf(field.offsetYPx, field.radiusPx, field.spreadPx))
        val dial = RasvenEffects.dialKey.shadows.single()
        assertEquals(listOf(9f, 22f, -9f), listOf(dial.offsetYPx, dial.radiusPx, dial.spreadPx))
    }
}
