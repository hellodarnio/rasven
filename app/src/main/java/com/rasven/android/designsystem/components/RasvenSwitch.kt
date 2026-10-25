package com.rasven.android.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout

/** Android implementation of Figma 550:6521 — Switch. */
@Composable
internal fun RasvenSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = RasvenDesignTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val trackShape = RoundedCornerShape(RasvenSemanticLayout.radiusFull.dp)
    val onColor = colors[RasvenSemanticColors.textBrand]
    val offColor = colors[RasvenSemanticColors.textSecondary]
    val trackFill = when {
        checked -> onColor
        pressed -> colors[RasvenSemanticColors.surfaceGlassSelected]
        else -> colors[RasvenSemanticColors.surfaceDefault]
    }
    val trackBorder = if (checked) onColor else offColor
    val thumbColor = if (checked) colors[RasvenSemanticColors.actionPrimaryText] else offColor

    Box(
        modifier = modifier
            .size(40.dp)
            .then(if (focused && enabled) Modifier.rasvenFocusHalo(colors[RasvenSemanticColors.focusHalo], 12.dp) else Modifier)
            .alpha(if (enabled) 1f else 0.64f)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = source,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 20.dp)
                    .clip(trackShape)
                    .background(trackFill)
                    .border(1.dp, trackBorder, trackShape)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .align(if (checked) Alignment.CenterStart else Alignment.CenterEnd)
                        .size(14.dp)
                        .background(thumbColor, CircleShape),
                )
            }
        }
    }
}
