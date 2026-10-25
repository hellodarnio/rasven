package com.rasven.android.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenEffects

internal enum class RasvenCallControlAction { Mute, Speaker, Hold, Keypad, Transfer }
internal enum class RasvenCallControlSize { Compact, Regular }

internal fun callControlLabel(action: RasvenCallControlAction, language: RasvenBadgeLanguage): String = when (action) {
    RasvenCallControlAction.Mute -> if (language == RasvenBadgeLanguage.Fa) "بی‌صدا" else "Mute"
    RasvenCallControlAction.Speaker -> if (language == RasvenBadgeLanguage.Fa) "بلندگو" else "Speaker"
    RasvenCallControlAction.Hold -> if (language == RasvenBadgeLanguage.Fa) "نگه‌داشتن" else "Hold"
    RasvenCallControlAction.Keypad -> if (language == RasvenBadgeLanguage.Fa) "صفحه‌کلید" else "Keypad"
    RasvenCallControlAction.Transfer -> if (language == RasvenBadgeLanguage.Fa) "انتقال" else "Transfer"
}

internal fun callControlIcon(action: RasvenCallControlAction, selected: Boolean): Int = when (action) {
    RasvenCallControlAction.Mute -> if (selected) R.drawable.rasven_mute_solid else R.drawable.rasven_mute_outline
    RasvenCallControlAction.Speaker -> if (selected) R.drawable.rasven_speaker_solid else R.drawable.rasven_speaker_outline
    RasvenCallControlAction.Hold -> if (selected) R.drawable.rasven_hold_solid else R.drawable.rasven_hold_outline
    RasvenCallControlAction.Keypad -> if (selected) R.drawable.rasven_keypad_solid else R.drawable.rasven_keypad_outline
    RasvenCallControlAction.Transfer -> if (selected) R.drawable.rasven_transfer_solid else R.drawable.rasven_transfer_outline
}

@Composable
internal fun RasvenCallControl(
    action: RasvenCallControlAction,
    language: RasvenBadgeLanguage,
    size: RasvenCallControlSize,
    fontFamily: FontFamily,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val width = if (size == RasvenCallControlSize.Compact) 80.dp else 88.dp
    val surface = when {
        pressed -> RasvenSemanticColors.surfaceGlass
        selected -> RasvenSemanticColors.surfaceCallControlSelected
        else -> RasvenSemanticColors.surfaceCallControl
    }
    val border = when {
        focused && enabled -> RasvenSemanticColors.borderFocus
        pressed -> RasvenSemanticColors.borderGlass
        selected -> RasvenSemanticColors.borderCallControlSelected
        else -> RasvenSemanticColors.borderCallControl
    }
    Column(
        modifier = modifier.testTag("call_control_${action.name.lowercase()}").width(width).height(76.dp)
            .then(if (focused && enabled) Modifier.rasvenFocusHalo(colors[RasvenSemanticColors.focusHalo], 20.dp) else Modifier)
            .then(if (selected && !pressed) Modifier.rasvenShadow(RasvenEffects.selectedCallControl.shadows.single(), shape) else Modifier)
            .alpha(if (enabled) 1f else 0.64f)
            .clip(shape)
            .background(colors[surface])
            .border(1.dp, colors[border], shape)
            .semantics { this.selected = selected }
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(callControlIcon(action, selected)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors[when {
                !enabled -> RasvenSemanticColors.iconDisabled
                selected -> RasvenSemanticColors.iconCallControlSelected
                else -> RasvenSemanticColors.iconCallControl
            }]),
            modifier = Modifier.size(22.dp),
        )
        BasicText(
            text = callControlLabel(action, language),
            style = TextStyle(
                color = colors[when {
                    !enabled -> RasvenSemanticColors.textDisabled
                    selected -> RasvenSemanticColors.textCallControlSelected
                    else -> RasvenSemanticColors.textCallControl
                }],
                fontFamily = fontFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

/** Five independent selection axes cover the 128 FA/EN × Compact/Regular variants. */
@Composable
internal fun RasvenCallControlsGroup(
    language: RasvenBadgeLanguage,
    size: RasvenCallControlSize,
    fontFamily: FontFamily,
    selected: Set<RasvenCallControlAction>,
    enabled: Set<RasvenCallControlAction>,
    onAction: (RasvenCallControlAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RasvenDesignTheme.colors
    val width = if (size == RasvenCallControlSize.Compact) 288.dp else 328.dp
    val gap = if (size == RasvenCallControlSize.Compact) 12.dp else 20.dp
    val shape = RoundedCornerShape(28.dp)
    // Figma keeps the physical grid order Mute–Speaker–Hold / Keypad–Transfer in both locales.
    val direction = LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(
            modifier = modifier.size(width, 212.dp)
                .rasvenShadow(RasvenComponentEffects.callControlsGroup, shape)
                .clip(shape)
                .background(colors[RasvenSemanticColors.surfaceGlass])
                .border(1.dp, colors[RasvenSemanticColors.borderGlass], shape)
                .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                listOf(RasvenCallControlAction.Mute, RasvenCallControlAction.Speaker, RasvenCallControlAction.Hold).forEach { action ->
                    RasvenCallControl(action, language, size, fontFamily, action in selected, { onAction(action) }, enabled = action in enabled)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                listOf(RasvenCallControlAction.Keypad, RasvenCallControlAction.Transfer).forEach { action ->
                    RasvenCallControl(action, language, size, fontFamily, action in selected, { onAction(action) }, enabled = action in enabled)
                }
            }
        }
    }
}
