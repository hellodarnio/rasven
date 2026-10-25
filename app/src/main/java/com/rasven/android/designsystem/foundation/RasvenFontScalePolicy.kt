package com.rasven.android.designsystem.foundation

/**
 * Product typography follows the system font scale only inside the owner-approved range.
 * Design-default typography is the hard floor; accessibility growth is capped at +25%.
 */
internal object RasvenFontScalePolicy {
    const val minimum = 1.00f
    const val maximum = 1.25f

    fun resolve(systemFontScale: Float): Float =
        systemFontScale.coerceIn(minimum, maximum)
}
