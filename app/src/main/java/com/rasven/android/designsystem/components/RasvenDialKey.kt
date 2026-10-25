package com.rasven.android.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenDialKeyTypography
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenEffects

internal enum class RasvenDialGlyph { Standard, Asterisk, Hash }
internal data class RasvenDialKeySpec(val glyph: RasvenDialGlyph, val primary: String, val secondary: String = "")

@Composable
internal fun RasvenDialKey(
    spec: RasvenDialKeySpec,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(spec.glyph == RasvenDialGlyph.Standard || spec.secondary.isEmpty())
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.94f else 1f, label = "dial key press")
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(21.dp)
    val fill = if ((pressed || hovered) && enabled) RasvenSemanticColors.surfaceGlassSelected else RasvenSemanticColors.surfaceDialKey
    val border = when {
        focused && enabled -> RasvenSemanticColors.borderFocus
        pressed && enabled -> RasvenSemanticColors.borderFocus
        hovered && enabled -> RasvenSemanticColors.borderGlassSelected
        else -> RasvenSemanticColors.borderDialKey
    }
    Column(
        modifier = modifier.size(76.dp, 68.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .then(if (focused && enabled) Modifier.rasvenFocusHalo(colors[RasvenSemanticColors.focusHalo], 21.dp) else Modifier)
            .rasvenShadow(RasvenEffects.dialKey.shadows.single(), shape)
            .alpha(if (enabled) 1f else 0.64f)
            .clip(shape)
            .background(colors[fill])
            .border(if (pressed && enabled) 2.dp else 1.dp, colors[border], shape)
            .hoverable(source, enabled = enabled)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        // Keep the primary baseline fixed for 1, *, # and keys with secondary letters.
        Spacer(Modifier.height(9.dp))
        val primary = RasvenDialKeyTypography.primary
        BasicText(spec.primary, style = TextStyle(
            color = colors[if (enabled) RasvenSemanticColors.textDialKeyPrimary else RasvenSemanticColors.textDisabled],
            fontFamily = fontFamily, fontSize = primary.fontSizeSp.sp, lineHeight = primary.lineHeightSp.sp,
            fontWeight = FontWeight.Medium,
        ), maxLines = 1)
        if (spec.glyph == RasvenDialGlyph.Standard && spec.secondary.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val secondary = RasvenDialKeyTypography.secondary
            BasicText(spec.secondary, style = TextStyle(
                color = colors[if (enabled) RasvenSemanticColors.textDialKeySecondary else RasvenSemanticColors.textDisabled],
                fontFamily = fontFamily, fontSize = secondary.fontSizeSp.sp, lineHeight = secondary.lineHeightSp.sp,
                fontWeight = FontWeight.Medium,
            ), maxLines = 1)
        }
    }
}

/** Optional composition of twelve approved keys; values and actual DTMF behavior remain caller-owned. */
@Composable
internal fun RasvenDialPad(
    keys: List<RasvenDialKeySpec>,
    fontFamily: FontFamily,
    onKey: (RasvenDialKeySpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(keys.size == 12)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key -> RasvenDialKey(key, fontFamily, { onKey(key) }) }
            }
        }
    }
}
