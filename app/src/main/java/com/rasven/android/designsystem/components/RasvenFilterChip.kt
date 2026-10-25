package com.rasven.android.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens

internal enum class RasvenFilterChipSize { Small, Medium }

internal enum class RasvenFilterChipVisualState { Default, Hover, Pressed, Focus, Disabled }

internal data class RasvenFilterChipGeometry(
    val height: Int,
    val horizontalPadding: Int,
    val gap: Int,
    val iconSize: Int,
)

/**
 * Optional semantic palette for Product states whose approved Figma instance overrides the base
 * Chip / Filter selected treatment. Null preserves the pre-Stage5B4 component behavior exactly.
 */
internal data class RasvenFilterChipPalette(
    val unselectedFill: Color,
    val unselectedBorder: Color,
    val selectedFill: Color,
    val selectedBorder: Color,
    val contentColor: Color,
    val unselectedBorderWidth: Dp = 1.dp,
    val selectedBorderWidth: Dp = 2.dp,
)

internal fun filterChipGeometry(size: RasvenFilterChipSize): RasvenFilterChipGeometry = when (size) {
    RasvenFilterChipSize.Small -> RasvenFilterChipGeometry(32, 12, 4, 16)
    RasvenFilterChipSize.Medium -> RasvenFilterChipGeometry(40, 16, 4, 20)
}

internal fun filterChipVisualState(
    enabled: Boolean,
    pressed: Boolean,
    focused: Boolean,
    hovered: Boolean,
): RasvenFilterChipVisualState = when {
    !enabled -> RasvenFilterChipVisualState.Disabled
    pressed -> RasvenFilterChipVisualState.Pressed
    focused -> RasvenFilterChipVisualState.Focus
    hovered -> RasvenFilterChipVisualState.Hover
    else -> RasvenFilterChipVisualState.Default
}

internal fun filterChipShowsContainer(selected: Boolean, state: RasvenFilterChipVisualState): Boolean =
    selected || state == RasvenFilterChipVisualState.Hover ||
        state == RasvenFilterChipVisualState.Pressed || state == RasvenFilterChipVisualState.Focus

/** Figma `Chip / Filter`: callers own the mutually-exclusive selection state. */
@Composable
internal fun RasvenFilterChip(
    label: String,
    selected: Boolean,
    size: RasvenFilterChipSize,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    palette: RasvenFilterChipPalette? = null,
    labelFontSizeSp: Int = RasvenTypographyTokens.controlLabel.fontSizeSp,
    labelLineHeightSp: Int = RasvenTypographyTokens.controlLabel.lineHeightSp,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val state = filterChipVisualState(enabled, pressed, focused, hovered)
    val geometry = filterChipGeometry(size)
    val shape = RoundedCornerShape(50)
    val colors = RasvenDesignTheme.colors

    val defaultSelectedFill = when (state) {
        RasvenFilterChipVisualState.Hover -> RasvenSemanticColors.selectionBackgroundHover
        RasvenFilterChipVisualState.Pressed -> RasvenSemanticColors.selectionBackgroundPressed
        else -> RasvenSemanticColors.selectionBackground
    }
    val defaultBorderColor = when (state) {
        RasvenFilterChipVisualState.Focus -> RasvenSemanticColors.focusRing
        RasvenFilterChipVisualState.Disabled -> RasvenSemanticColors.borderDisabled
        else -> RasvenSemanticColors.selectionBorder
    }
    val showContainer = palette != null || filterChipShowsContainer(selected, state)
    val containerFill = when {
        palette == null -> colors[defaultSelectedFill]
        state == RasvenFilterChipVisualState.Disabled -> colors[RasvenSemanticColors.surfaceDisabled]
        else -> if (selected) palette.selectedFill else palette.unselectedFill
    }
    val containerBorder = when {
        state == RasvenFilterChipVisualState.Focus -> colors[RasvenSemanticColors.focusRing]
        state == RasvenFilterChipVisualState.Disabled -> colors[RasvenSemanticColors.borderDisabled]
        palette != null -> if (selected) palette.selectedBorder else palette.unselectedBorder
        else -> colors[defaultBorderColor]
    }
    val borderWidth = when {
        state == RasvenFilterChipVisualState.Focus -> 2.dp
        palette != null -> if (selected) palette.selectedBorderWidth else palette.unselectedBorderWidth
        else -> 1.dp
    }
    val textColor = when {
        state == RasvenFilterChipVisualState.Disabled -> colors[RasvenSemanticColors.textDisabled]
        palette != null -> palette.contentColor
        else -> colors[RasvenSemanticColors.textPrimary]
    }

    Row(
        modifier = modifier
            .height(geometry.height.dp)
            .clip(shape)
            .then(if (showContainer) Modifier.background(containerFill) else Modifier)
            .then(
                if (showContainer) Modifier.border(
                    width = borderWidth,
                    color = containerBorder,
                    shape = shape,
                ) else Modifier
            )
            .hoverable(source, enabled = enabled)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = geometry.horizontalPadding.dp),
        horizontalArrangement = Arrangement.spacedBy(geometry.gap.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(Modifier.size(geometry.iconSize.dp), contentAlignment = Alignment.Center) {
                leadingIcon()
            }
        }
        BasicText(
            text = label,
            style = TextStyle(
                color = textColor,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = labelFontSizeSp.sp,
                lineHeight = labelLineHeightSp.sp,
            ),
            maxLines = 1,
        )
    }
}

/** Official history glyphs are supplied by the caller so Chip / Filter keeps its Instance Swap API. */
@Composable
internal fun RasvenFilterChipIcon(
    resourceId: Int,
    size: RasvenFilterChipSize,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tintColor: Color? = null,
) {
    val iconSize = filterChipGeometry(size).iconSize
    val colors = RasvenDesignTheme.colors
    val resolvedTint = if (!enabled) colors[RasvenSemanticColors.iconDisabled]
    else tintColor ?: colors[RasvenSemanticColors.iconPrimary]
    Image(
        painter = painterResource(resourceId),
        contentDescription = null,
        colorFilter = ColorFilter.tint(resolvedTint),
        modifier = modifier.size(iconSize.dp),
    )
}
