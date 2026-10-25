package com.rasven.android.designsystem.foundation

internal enum class RasvenFontRole {
    Persian,
    English,
}

/** Design-authoring and Android runtime font contracts are intentionally distinct. */
internal object RasvenFontContract {
    const val figmaPersianFamilyName = "Vazirmatn"
    const val androidPersianFamilyName = "IRANSansX Pro"
    const val englishFamilyName = "Inter"
}

internal data class RasvenTextMetrics(
    val fontSizeSp: Int,
    val lineHeightSp: Int,
    val fontWeight: Int,
    val letterSpacingSp: Int = 0,
)

/** Semantic typography roles resolved from the live Design System. */
internal object RasvenTypographyTokens {
    val headingXl = RasvenTextMetrics(fontSizeSp = 32, lineHeightSp = 40, fontWeight = 600)
    val headingL = RasvenTextMetrics(fontSizeSp = 20, lineHeightSp = 32, fontWeight = 600)
    val headingM = RasvenTextMetrics(fontSizeSp = 16, lineHeightSp = 26, fontWeight = 600)
    val headingS = RasvenTextMetrics(fontSizeSp = 14, lineHeightSp = 24, fontWeight = 600)
    val bodyL = RasvenTextMetrics(fontSizeSp = 16, lineHeightSp = 26, fontWeight = 400)
    val bodyM = RasvenTextMetrics(fontSizeSp = 14, lineHeightSp = 24, fontWeight = 400)
    val bodyS = RasvenTextMetrics(fontSizeSp = 12, lineHeightSp = 18, fontWeight = 400)
    val controlLabel = RasvenTextMetrics(fontSizeSp = 14, lineHeightSp = 24, fontWeight = 500)
    val dataHeader = RasvenTextMetrics(fontSizeSp = 14, lineHeightSp = 24, fontWeight = 600)
    val labelL = RasvenTextMetrics(fontSizeSp = 14, lineHeightSp = 22, fontWeight = 500)
    val labelM = RasvenTextMetrics(fontSizeSp = 12, lineHeightSp = 18, fontWeight = 500)
    val labelS = RasvenTextMetrics(fontSizeSp = 11, lineHeightSp = 16, fontWeight = 500)
    val caption = RasvenTextMetrics(fontSizeSp = 11, lineHeightSp = 16, fontWeight = 400)
}

/**
 * Dial Key typography uses actual live component bindings/geometry as the implementation truth.
 * The current Secondary text is 11/16/500. Unbound raw Figma variables named
 * `dial-key/secondary/font-size` (9) and `line-height` (22) are deliberately not used.
 */
internal object RasvenDialKeyTypography {
    val primary = RasvenTextMetrics(fontSizeSp = 25, lineHeightSp = 22, fontWeight = 500)
    val secondary = RasvenTextMetrics(fontSizeSp = 11, lineHeightSp = 16, fontWeight = 500)
}
