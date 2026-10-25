package com.rasven.android.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.LayoutDirection
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenNamedColorToken
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens
import com.rasven.android.designsystem.foundation.RasvenEffects

internal enum class RasvenTextFieldAppearance { Default, Glass, Borderless }
internal enum class RasvenTextFieldSize { Small, Medium, Large, XLarge }
internal enum class RasvenTextFieldVisualState { Default, Hover, Focus, Filled, Error, Success, Disabled, ReadOnly }

internal fun textFieldControlHeight(size: RasvenTextFieldSize): Int = when (size) {
    RasvenTextFieldSize.Small -> 32
    RasvenTextFieldSize.Medium -> 40
    RasvenTextFieldSize.Large -> 48
    RasvenTextFieldSize.XLarge -> 64
}

internal fun textFieldIconSize(size: RasvenTextFieldSize): Int =
    if (size == RasvenTextFieldSize.Small) 16 else 20

internal fun textFieldDefaultValueAlignment(layoutDirection: LayoutDirection): TextAlign =
    if (layoutDirection == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left

internal fun textFieldDefaultValueDirection(layoutDirection: LayoutDirection): TextDirection =
    if (layoutDirection == LayoutDirection.Rtl) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr

internal fun textFieldVisualState(
    enabled: Boolean, readOnly: Boolean, error: Boolean, success: Boolean,
    focused: Boolean, hovered: Boolean, filled: Boolean,
): RasvenTextFieldVisualState = when {
    !enabled -> RasvenTextFieldVisualState.Disabled
    readOnly -> RasvenTextFieldVisualState.ReadOnly
    error -> RasvenTextFieldVisualState.Error
    success -> RasvenTextFieldVisualState.Success
    focused -> RasvenTextFieldVisualState.Focus
    filled -> RasvenTextFieldVisualState.Filled
    hovered -> RasvenTextFieldVisualState.Hover
    else -> RasvenTextFieldVisualState.Default
}

internal data class RasvenFieldPaint(
    val fill: RasvenNamedColorToken,
    val border: RasvenNamedColorToken?,
    val value: RasvenNamedColorToken,
    val helper: RasvenNamedColorToken,
)

internal fun textFieldPaint(appearance: RasvenTextFieldAppearance, state: RasvenTextFieldVisualState): RasvenFieldPaint {
    val c = RasvenSemanticColors
    val fill = when {
        state == RasvenTextFieldVisualState.Disabled -> c.surfaceDisabled
        state == RasvenTextFieldVisualState.ReadOnly && appearance != RasvenTextFieldAppearance.Glass -> c.surfaceSubtle
        appearance == RasvenTextFieldAppearance.Glass -> c.surfaceInputGlass
        else -> c.surfaceDefault
    }
    val border = when {
        appearance == RasvenTextFieldAppearance.Borderless -> null
        state == RasvenTextFieldVisualState.Disabled -> c.borderDisabled
        state == RasvenTextFieldVisualState.Error -> c.borderError
        state == RasvenTextFieldVisualState.Success -> c.statusSuccessBorder
        state == RasvenTextFieldVisualState.Focus -> c.borderFocus
        appearance == RasvenTextFieldAppearance.Glass -> c.borderInputGlass
        state == RasvenTextFieldVisualState.Hover -> c.borderSelected
        else -> c.borderStrong
    }
    val value = if (state == RasvenTextFieldVisualState.Disabled) c.textDisabled else c.textPrimary
    val helper = when (state) {
        RasvenTextFieldVisualState.Error -> c.statusErrorText
        RasvenTextFieldVisualState.Success -> c.statusSuccessText
        RasvenTextFieldVisualState.Disabled -> c.textDisabled
        else -> c.textSecondary
    }
    return RasvenFieldPaint(fill, border, value, helper)
}

/** Generic FA/EN field; the approved language font and product icon slots are supplied by the caller. */
@Composable
internal fun RasvenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    fontFamily: FontFamily,
    appearance: RasvenTextFieldAppearance,
    size: RasvenTextFieldSize,
    modifier: Modifier = Modifier,
    label: String? = null,
    helper: String? = null,
    placeholder: String = "",
    required: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    isSuccess: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    valueFontFamily: FontFamily = fontFamily,
    valueFontSize: TextUnit = 14.sp,
    layoutDirection: LayoutDirection = LayoutDirection.Rtl,
    valueTextDirection: TextDirection = textFieldDefaultValueDirection(layoutDirection),
    valueTextAlign: TextAlign = textFieldDefaultValueAlignment(layoutDirection),
    inputModifier: Modifier = Modifier,
    requiredAccessibilityText: String? = null,
    accessibilityLabel: String? = label ?: placeholder.takeIf { it.isNotBlank() },
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val state = textFieldVisualState(enabled, readOnly, isError, isSuccess, focused, hovered, value.isNotEmpty())
    val paint = textFieldPaint(appearance, state)
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(if (appearance == RasvenTextFieldAppearance.Glass) 18.dp else 8.dp)
    val iconSize = textFieldIconSize(size)
    val labelStyle = TextStyle(fontFamily = fontFamily, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val valueStyle = TextStyle(fontFamily = valueFontFamily, fontSize = valueFontSize, lineHeight = if (valueFontSize == 14.sp) 24.sp else 30.sp, fontWeight = FontWeight.Normal, color = colors[paint.value], textDirection = valueTextDirection, textAlign = valueTextAlign)
    val helperStyle = TextStyle(fontFamily = fontFamily, fontSize = 11.sp, lineHeight = 16.sp)
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) BasicText(
            text = label + if (required) " *" else "",
            modifier = Modifier.fillMaxWidth(),
            style = labelStyle.copy(
                color = colors[when (state) {
                RasvenTextFieldVisualState.Error -> RasvenSemanticColors.statusErrorText
                RasvenTextFieldVisualState.Disabled -> RasvenSemanticColors.textDisabled
                else -> RasvenSemanticColors.textSecondary
            }],
                textAlign = textFieldDefaultValueAlignment(layoutDirection),
                textDirection = textFieldDefaultValueDirection(layoutDirection),
            ),
        )
        var control = Modifier
            .fillMaxWidth()
            .height(textFieldControlHeight(size).dp)
            .hoverable(source, enabled = enabled && !readOnly)
            .then(if (state == RasvenTextFieldVisualState.Focus) Modifier.rasvenFocusHalo(colors[RasvenSemanticColors.focusHalo], if (appearance == RasvenTextFieldAppearance.Glass) 18.dp else 8.dp) else Modifier)
            .then(if (appearance == RasvenTextFieldAppearance.Glass && state != RasvenTextFieldVisualState.Disabled)
                Modifier.rasvenShadow(RasvenEffects.glassInput.shadows.single(), shape) else Modifier)
            .clip(shape)
            .background(colors[paint.fill])
        if (paint.border != null) control = control.border(1.dp, colors[paint.border], shape)
        Box(control.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (leadingIcon != null) Box(Modifier.size(iconSize.dp), contentAlignment = Alignment.Center) { leadingIcon() }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    readOnly = readOnly,
                    interactionSource = source,
                    textStyle = valueStyle,
                    cursorBrush = SolidColor(colors[RasvenSemanticColors.borderFocus]),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    singleLine = true,
                    modifier = Modifier.weight(1f).then(inputModifier).semantics {
                        if (!accessibilityLabel.isNullOrBlank()) {
                            contentDescription = listOfNotNull(
                                accessibilityLabel,
                                requiredAccessibilityText?.takeIf { required },
                            ).joinToString("، ")
                        }
                        if (isError && !helper.isNullOrBlank()) error(helper)
                    },
                    decorationBox = { inner ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (value.isEmpty()) {
                                BasicText(
                                    text = placeholder,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = valueStyle.copy(
                                        color = colors[RasvenSemanticColors.textSecondary],
                                        textAlign = textFieldDefaultValueAlignment(layoutDirection),
                                        textDirection = textFieldDefaultValueDirection(layoutDirection),
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (trailingIcon != null) Box(Modifier.size(iconSize.dp), contentAlignment = Alignment.Center) { trailingIcon() }
            }
        }
        if (helper != null) BasicText(
            text = helper,
            modifier = Modifier.fillMaxWidth(),
            style = helperStyle.copy(
                color = colors[paint.helper],
                textAlign = textFieldDefaultValueAlignment(layoutDirection),
                textDirection = textFieldDefaultValueDirection(layoutDirection),
            ),
        )
        }
    }
}
