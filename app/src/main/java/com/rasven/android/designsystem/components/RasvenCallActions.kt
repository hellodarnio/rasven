package com.rasven.android.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenSemanticColors

/** Uses the DS 92×56 control. Call semantics and actual registration state stay with the caller. */
@Composable
internal fun RasvenPrimaryCallAction(
    contentLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingStateDescription: String? = null,
) {
    require(!loading || !loadingStateDescription.isNullOrBlank())
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(999.dp)
    Box(modifier.clickable(source, indication = null, enabled = enabled && !loading, role = Role.Button, onClick = onClick)
        .semantics {
            contentDescription = contentLabel
            if (loading) stateDescription = loadingStateDescription!!
        }, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(92.dp, 56.dp)
                .then(if (enabled) Modifier.rasvenShadow(RasvenEffects.primaryCall.shadows.single(), shape) else Modifier)
                .then(if (focused && enabled) Modifier.rasvenFocusHalo(colors[RasvenSemanticColors.focusHalo], 999.dp) else Modifier)
                .clip(shape)
                .then(
                    if (!enabled) Modifier.background(colors[RasvenSemanticColors.actionDisabledBackground])
                    else Modifier.rasvenGradientFill(if (pressed) RasvenGradients.primaryPressed else RasvenGradients.primaryDefault, 999.dp)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) RasvenButtonSpinner(24)
            else Image(
                painterResource(R.drawable.rasven_call), contentDescription = null,
                colorFilter = ColorFilter.tint(colors[if (enabled) RasvenSemanticColors.actionPrimaryIcon else RasvenSemanticColors.actionDisabledIcon]),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Incoming-call Figma variant: 128×58; does not resize the existing 92×56 outbound action. */
@Composable
internal fun RasvenIncomingAnswerAction(
    contentLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(29.dp)
    Box(
        modifier.clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = contentLabel },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(128.dp, 58.dp)
                .then(if (enabled) Modifier.rasvenShadow(RasvenEffects.primaryCall.shadows.single(), shape) else Modifier)
                .then(if (focused && enabled) Modifier.rasvenFocusHalo(colors[RasvenSemanticColors.focusHalo], 29.dp) else Modifier)
                .clip(shape)
                .then(if (enabled) Modifier.rasvenGradientFill(
                    if (pressed) RasvenGradients.primaryPressed else RasvenGradients.primaryDefault, 29.dp,
                ) else Modifier.background(colors[RasvenSemanticColors.actionDisabledBackground])),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.rasven_call), null,
                colorFilter = ColorFilter.tint(colors[
                    if (enabled) RasvenSemanticColors.actionPrimaryIcon else RasvenSemanticColors.actionDisabledIcon
                ]),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** DS End Call has no Loading state. Disabled keeps the danger surface at 64% opacity. */
@Composable
internal fun RasvenEndCallAction(
    contentLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(29.dp)
    Box(modifier.clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = contentLabel }, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(128.dp, 58.dp)
                .rasvenShadow(RasvenEffects.endCall.shadows.single(), shape)
                .then(if (focused && enabled) Modifier.rasvenFocusHalo(colors[RasvenSemanticColors.focusHalo], 29.dp) else Modifier)
                .alpha(if (enabled) 1f else 0.64f)
                .clip(shape)
                .background(colors[if (pressed && enabled) RasvenSemanticColors.actionDangerBackgroundPressed else RasvenSemanticColors.actionDangerBackground])
                .then(if (focused && enabled) Modifier.border(1.dp, colors[RasvenSemanticColors.borderFocus], shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.rasven_end_call), contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}
