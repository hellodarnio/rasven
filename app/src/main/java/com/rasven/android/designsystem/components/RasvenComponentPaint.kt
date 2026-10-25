package com.rasven.android.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenLinearGradientSpec
import com.rasven.android.designsystem.foundation.linearGradientLine
import com.rasven.android.designsystem.foundation.RasvenShadowSpec

/** Figma's normalized transform maps the gradient axis into the target rectangle. */
@Composable
internal fun Modifier.rasvenGradientFill(spec: RasvenLinearGradientSpec, radius: Dp): Modifier {
    val colors = RasvenDesignTheme.colors
    require(spec.stops.map { it.position } == listOf(0f, 1f))
    val stops = spec.stops.map { colors[it.color] }
    return drawBehind {
        val line = spec.transform.linearGradientLine(size.width, size.height)
        val brush = Brush.linearGradient(
            colors = stops,
            start = Offset(line.startX, line.startY),
            end = Offset(line.endX, line.endY),
        )
        drawRoundRect(brush = brush, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()))
    }
}

/** A zero-blur, three-pixel outward halo as specified by the foundation effect contract. */
internal fun Modifier.rasvenFocusHalo(color: Color, radius: Dp): Modifier = drawBehind {
    val spread = 3.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(-spread, -spread),
        size = androidx.compose.ui.geometry.Size(size.width + spread * 2, size.height + spread * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx() + spread),
    )
}

@Composable
internal fun Modifier.rasvenShadow(spec: RasvenShadowSpec, shape: Shape): Modifier {
    val color = RasvenDesignTheme.colors[spec.color]
    return dropShadow(
        shape = shape,
        shadow = Shadow(
            radius = spec.radiusPx.dp,
            spread = spec.spreadPx.dp,
            offset = DpOffset(spec.offsetXPx.dp, spec.offsetYPx.dp),
            color = color,
        ),
    )
}
