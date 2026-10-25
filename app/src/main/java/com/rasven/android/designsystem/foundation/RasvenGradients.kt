package com.rasven.android.designsystem.foundation

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

internal data class RasvenGradientStop(
    val position: Float,
    val color: RasvenNamedColorToken,
)

internal data class RasvenGradientTransform(
    val m00: Float,
    val m01: Float,
    val m02: Float,
    val m10: Float,
    val m11: Float,
    val m12: Float,
)

internal data class RasvenLinearGradientSpec(
    val stops: List<RasvenGradientStop>,
    val transform: RasvenGradientTransform,
)

internal data class RasvenGradientLine(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

/**
 * Converts Figma's normalized linear-gradient transform into the equivalent Compose gradient line.
 *
 * Figma's design-to-code serializer derives the CSS angle from the transform's second column and
 * the target rectangle aspect ratio. The color axis is perpendicular to that normalized column,
 * so the pixel-space direction is `(m11 * height, m01 * width)`. The line is then centered and
 * extended to the rectangle's projected corners, matching CSS linear-gradient 0%/100% geometry.
 * This intentionally remains size-dependent: the same Figma transform has a different rendered
 * angle when a component is stretched to a different aspect ratio.
 */
internal fun RasvenGradientTransform.linearGradientLine(widthPx: Float, heightPx: Float): RasvenGradientLine {
    require(widthPx > 0f && heightPx > 0f)

    val directionX = m11 * heightPx
    val directionY = m01 * widthPx
    val magnitude = hypot(directionX, directionY)
    require(magnitude > 1e-6f) { "Figma linear-gradient transform has no usable color axis." }

    val unitX = directionX / magnitude
    val unitY = directionY / magnitude
    val halfLength = (abs(widthPx * unitX) + abs(heightPx * unitY)) / 2f
    val centerX = widthPx / 2f
    val centerY = heightPx / 2f

    return RasvenGradientLine(
        startX = centerX - unitX * halfLength,
        startY = centerY - unitY * halfLength,
        endX = centerX + unitX * halfLength,
        endY = centerY + unitY * halfLength,
    )
}

/** CSS angle emitted by Figma for the same transform/rectangle, used as a regression oracle. */
internal fun RasvenGradientTransform.figmaCssAngleDegrees(widthPx: Float, heightPx: Float): Float {
    require(widthPx > 0f && heightPx > 0f)
    val degrees = Math.toDegrees(atan2((m11 * heightPx).toDouble(), (-m01 * widthPx).toDouble())).toFloat()
    return if (degrees < 0f) degrees + 360f else degrees
}

/** Raw Figma linear-gradient transforms. Do not replace these with guessed degree angles. */
internal object RasvenGradients {
    val pageCanvas = RasvenLinearGradientSpec(
        stops = listOf(
            RasvenGradientStop(0f, RasvenSemanticColors.backgroundCanvas),
            RasvenGradientStop(1f, RasvenSemanticColors.backgroundCanvasEnd),
        ),
        transform = RasvenGradientTransform(
            m00 = 0.8600000143f,
            m01 = 0.1400000006f,
            m02 = 0f,
            m10 = -0.1400000006f,
            m11 = 0.8600000143f,
            m12 = 0.0799999982f,
        ),
    )

    private val primaryTransform = RasvenGradientTransform(
        m00 = 0.7799999714f,
        m01 = 0.2199999988f,
        m02 = 0f,
        m10 = -0.2199999988f,
        m11 = 0.7799999714f,
        m12 = 0.1599999964f,
    )

    val primaryDefault = RasvenLinearGradientSpec(
        stops = listOf(
            RasvenGradientStop(0f, RasvenSemanticColors.actionPrimaryBackground),
            RasvenGradientStop(1f, RasvenSemanticColors.actionPrimaryAccent),
        ),
        transform = primaryTransform,
    )

    val primaryHover = RasvenLinearGradientSpec(
        stops = listOf(
            RasvenGradientStop(0f, RasvenSemanticColors.actionPrimaryBackgroundHover),
            RasvenGradientStop(1f, RasvenSemanticColors.actionPrimaryAccentHover),
        ),
        transform = primaryTransform,
    )

    val primaryPressed = RasvenLinearGradientSpec(
        stops = listOf(
            RasvenGradientStop(0f, RasvenSemanticColors.actionPrimaryBackgroundPressed),
            RasvenGradientStop(1f, RasvenSemanticColors.actionPrimaryAccentPressed),
        ),
        transform = primaryTransform,
    )
}
