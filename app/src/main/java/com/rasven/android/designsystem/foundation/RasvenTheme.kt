package com.rasven.android.designsystem.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Runtime Product theme is intentionally locked to Light in Stage 3.
 * Dark values remain preserved in [RasvenSemanticColors] for future authorized use.
 */
private enum class RasvenRuntimeColorMode {
    Light,
    InverseCall,
}

internal class RasvenColorResolver private constructor(
    private val mode: RasvenRuntimeColorMode,
) {
    operator fun get(token: RasvenNamedColorToken): Color =
        when (mode) {
            RasvenRuntimeColorMode.Light -> token.pair.lightArgb.toComposeColor()
            RasvenRuntimeColorMode.InverseCall -> token.pair.darkArgb.toComposeColor()
        }

    companion object {
        val Light = RasvenColorResolver(RasvenRuntimeColorMode.Light)
        val InverseCall = RasvenColorResolver(RasvenRuntimeColorMode.InverseCall)
    }
}

private fun ULong.toComposeColor(): Color {
    val alpha = ((this shr 24) and 0xFFUL).toFloat() / 255f
    val red = ((this shr 16) and 0xFFUL).toFloat() / 255f
    val green = ((this shr 8) and 0xFFUL).toFloat() / 255f
    val blue = (this and 0xFFUL).toFloat() / 255f
    return Color(red = red, green = green, blue = blue, alpha = alpha)
}

private val LocalRasvenColors = staticCompositionLocalOf { RasvenColorResolver.Light }

@Composable
internal fun RasvenTheme(content: @Composable () -> Unit) {
    val systemDensity = LocalDensity.current
    val controlledDensity = Density(
        density = systemDensity.density,
        fontScale = RasvenFontScalePolicy.resolve(systemDensity.fontScale),
    )

    CompositionLocalProvider(
        LocalRasvenColors provides RasvenColorResolver.Light,
        LocalDensity provides controlledDensity,
        content = content,
    )
}

/** Opt-in Figma inverse palette for call controls/dial keys, not system dark mode. */
@Composable
internal fun RasvenInverseCallSurface(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRasvenColors provides RasvenColorResolver.InverseCall, content = content)
}

internal object RasvenDesignTheme {
    val colors: RasvenColorResolver
        @Composable
        @ReadOnlyComposable
        get() = LocalRasvenColors.current

    val typography: RasvenTypographyTokens
        get() = RasvenTypographyTokens

    val semanticLayout: RasvenSemanticLayout
        get() = RasvenSemanticLayout

    val productLayout: RasvenProductLayout
        get() = RasvenProductLayout
}
