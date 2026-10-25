package com.rasven.android.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenLinearGradientSpec
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens

internal enum class RasvenButtonSize { XSmall, Small, Medium, Large, XLarge }
internal enum class RasvenButtonVisualState { Default, Hover, Pressed, Focus, Disabled, Loading }

internal data class RasvenButtonGeometry(val height: Int, val horizontalPadding: Int, val gap: Int, val iconSize: Int)

internal fun buttonGeometry(size: RasvenButtonSize): RasvenButtonGeometry = when (size) {
    RasvenButtonSize.XSmall -> RasvenButtonGeometry(24, 8, 4, 16)
    RasvenButtonSize.Small -> RasvenButtonGeometry(32, 12, 4, 16)
    RasvenButtonSize.Medium -> RasvenButtonGeometry(40, 16, 8, 20)
    RasvenButtonSize.Large -> RasvenButtonGeometry(48, 16, 8, 20)
    RasvenButtonSize.XLarge -> RasvenButtonGeometry(56, 20, 8, 24)
}

/** State precedence keeps the semantic disabled/loading axes separate from transient interaction. */
internal fun buttonVisualState(
    enabled: Boolean, loading: Boolean, pressed: Boolean, focused: Boolean, hovered: Boolean,
): RasvenButtonVisualState = when {
    !enabled -> RasvenButtonVisualState.Disabled
    loading -> RasvenButtonVisualState.Loading
    pressed -> RasvenButtonVisualState.Pressed
    focused -> RasvenButtonVisualState.Focus
    hovered -> RasvenButtonVisualState.Hover
    else -> RasvenButtonVisualState.Default
}

internal fun buttonGradient(state: RasvenButtonVisualState): RasvenLinearGradientSpec = when (state) {
    RasvenButtonVisualState.Hover -> RasvenGradients.primaryHover
    RasvenButtonVisualState.Pressed -> RasvenGradients.primaryPressed
    else -> RasvenGradients.primaryDefault
}

@Composable
internal fun RasvenButton(
    label: String,
    size: RasvenButtonSize,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingStateDescription: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    expandToFullWidth: Boolean = false,
) {
    require(!loading || !loadingStateDescription.isNullOrBlank()) {
        "Loading Button requires a localized accessibility state description"
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val state = buttonVisualState(enabled, loading, pressed, focused, hovered)
    val geometry = buttonGeometry(size)
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusControl.dp)
    val colors = RasvenDesignTheme.colors
    val active = enabled && !loading
    val textMetrics = if (size == RasvenButtonSize.XSmall) RasvenTypographyTokens.labelM else RasvenTypographyTokens.controlLabel

    Box(
        modifier = modifier
            .sizeIn(minWidth = RasvenSemanticLayout.controlSizeLg.dp, minHeight = RasvenSemanticLayout.controlSizeLg.dp)
            .hoverable(source, enabled = active)
            .clickable(interactionSource = source, indication = null, enabled = active, role = Role.Button, onClick = onClick)
            .then(if (loading) Modifier.semantics { stateDescription = loadingStateDescription!! } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val visual = Modifier
            .height(geometry.height.dp)
            .then(if (expandToFullWidth) Modifier.fillMaxWidth() else Modifier)
            .then(if (state == RasvenButtonVisualState.Focus) Modifier.rasvenFocusHalo(colors[RasvenSemanticColors.focusHalo], 8.dp) else Modifier)
            .clip(shape)
            .then(
                if (state == RasvenButtonVisualState.Disabled)
                    Modifier.background(colors[RasvenSemanticColors.actionDisabledBackground])
                else Modifier.rasvenGradientFill(buttonGradient(state), 8.dp)
            )
            .then(
                when (state) {
                    RasvenButtonVisualState.Focus -> Modifier
                    RasvenButtonVisualState.Disabled -> Modifier.border(1.dp, colors[RasvenSemanticColors.actionDisabledBorder], shape)
                    else -> Modifier.border(1.dp, colors[RasvenSemanticColors.actionPrimaryBorder], shape)
                }
            )
            .padding(horizontal = geometry.horizontalPadding.dp)

        Row(
            modifier = visual,
            horizontalArrangement = if (expandToFullWidth) {
                Arrangement.spacedBy(geometry.gap.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(geometry.gap.dp)
            },
            verticalAlignment = Alignment.CenterVertically) {
            if (loading) RasvenButtonSpinner(geometry.iconSize)
            else if (leadingIcon != null) Box(Modifier.size(geometry.iconSize.dp), contentAlignment = Alignment.Center) { leadingIcon() }
            BasicText(
                text = label,
                style = TextStyle(
                    color = colors[if (state == RasvenButtonVisualState.Disabled) RasvenSemanticColors.actionDisabledText else RasvenSemanticColors.actionPrimaryText],
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = textMetrics.fontSizeSp.sp,
                    lineHeight = textMetrics.lineHeightSp.sp,
                ),
                maxLines = 1,
            )
            if (!loading && trailingIcon != null) Box(Modifier.size(geometry.iconSize.dp), contentAlignment = Alignment.Center) { trailingIcon() }
        }
    }
}

/** Android implementation of Figma `550:4731 — Button/Outline`. */
@Composable
internal fun RasvenOutlineButton(
    label: String,
    size: RasvenButtonSize,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expandToFullWidth: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val state = buttonVisualState(enabled, false, pressed, focused, hovered)
    val geometry = buttonGeometry(size)
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusControl.dp)
    val colors = RasvenDesignTheme.colors
    val textMetrics = if (size == RasvenButtonSize.XSmall) RasvenTypographyTokens.labelM else RasvenTypographyTokens.controlLabel
    val borderColor = colors[
        if (state == RasvenButtonVisualState.Disabled) RasvenSemanticColors.actionDisabledText
        else RasvenSemanticColors.textBrand
    ]
    val textColor = colors[
        if (state == RasvenButtonVisualState.Disabled) RasvenSemanticColors.actionDisabledText
        else RasvenSemanticColors.textBrand
    ]
    val backgroundColor = when (state) {
        RasvenButtonVisualState.Hover -> colors[RasvenSemanticColors.selectionBackground]
        RasvenButtonVisualState.Pressed -> colors[RasvenSemanticColors.selectionBackgroundHover]
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .sizeIn(minWidth = RasvenSemanticLayout.controlSizeLg.dp, minHeight = RasvenSemanticLayout.controlSizeLg.dp)
            .hoverable(source, enabled = enabled)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val visual = Modifier
            .height(geometry.height.dp)
            .then(if (expandToFullWidth) Modifier.fillMaxWidth() else Modifier)
            .then(if (state == RasvenButtonVisualState.Focus) Modifier.rasvenFocusHalo(colors[RasvenSemanticColors.focusHalo], 8.dp) else Modifier)
            .clip(shape)
            .background(backgroundColor)
            .border(RasvenSemanticLayout.borderWidthDefault.dp, borderColor, shape)
            .padding(horizontal = geometry.horizontalPadding.dp)

        Row(
            modifier = visual,
            horizontalArrangement = if (expandToFullWidth) {
                Arrangement.spacedBy(geometry.gap.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(geometry.gap.dp)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = label,
                style = TextStyle(
                    color = textColor,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = textMetrics.fontSizeSp.sp,
                    lineHeight = textMetrics.lineHeightSp.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

/** Figma `Button/Danger/Filled` for explicit, confirmed destructive actions. */
@Composable
internal fun RasvenDangerButton(
    label: String,
    size: RasvenButtonSize,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expandToFullWidth: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val state = buttonVisualState(enabled, false, pressed, focused, hovered)
    val geometry = buttonGeometry(size)
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusControl.dp)
    val colors = RasvenDesignTheme.colors
    val background = colors[when (state) {
        RasvenButtonVisualState.Disabled -> RasvenSemanticColors.actionDisabledBackground
        RasvenButtonVisualState.Hover -> RasvenSemanticColors.actionDangerBackgroundHover
        RasvenButtonVisualState.Pressed -> RasvenSemanticColors.actionDangerBackgroundPressed
        else -> RasvenSemanticColors.actionDangerBackground
    }]
    val border = colors[if (state == RasvenButtonVisualState.Disabled)
        RasvenSemanticColors.actionDisabledBorder else RasvenSemanticColors.actionDangerBorder]
    val textColor = colors[if (state == RasvenButtonVisualState.Disabled)
        RasvenSemanticColors.actionDisabledText else RasvenSemanticColors.actionDangerText]

    Box(
        modifier = modifier
            .sizeIn(minWidth = RasvenSemanticLayout.controlSizeLg.dp, minHeight = RasvenSemanticLayout.controlSizeLg.dp)
            .hoverable(source, enabled = enabled)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.height(geometry.height.dp)
                .then(if (expandToFullWidth) Modifier.fillMaxWidth() else Modifier)
                .then(if (state == RasvenButtonVisualState.Focus)
                    Modifier.rasvenFocusHalo(colors[RasvenSemanticColors.focusHalo], 8.dp) else Modifier)
                .clip(shape)
                .background(background)
                .border(RasvenSemanticLayout.borderWidthDefault.dp, border, shape)
                .padding(horizontal = geometry.horizontalPadding.dp),
            horizontalArrangement = Arrangement.spacedBy(geometry.gap.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = label,
                style = TextStyle(
                    color = textColor,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = RasvenTypographyTokens.controlLabel.fontSizeSp.sp,
                    lineHeight = RasvenTypographyTokens.controlLabel.lineHeightSp.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun RasvenButtonSpinner(size: Int) {
    val angle by rememberInfiniteTransition(label = "Rasven spinner").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "Rotation",
    )
    val resource = when (size) {
        16 -> R.drawable.rasven_spinner_16
        20 -> R.drawable.rasven_spinner_20
        else -> R.drawable.rasven_spinner_24
    }
    Image(painterResource(resource), contentDescription = null, modifier = Modifier.size(size.dp).rotate(angle))
}
