package com.rasven.android.designsystem.foundation

internal enum class RasvenWidthClass {
    Compact,
    Regular,
}

internal data class RasvenCallControlSize(
    val widthDp: Int,
    val heightDp: Int,
)

/** Pure responsive math from `45 / Responsive Mobile Contract`. */
internal object RasvenResponsive {
    fun requireSupportedViewport(viewportWidthDp: Float) {
        require(viewportWidthDp >= RasvenProductLayout.viewportReferenceCompact.toFloat()) {
            "Rasven minimum supported design width is ${RasvenProductLayout.viewportReferenceCompact}dp."
        }
    }

    fun requireSupportedViewport(viewportWidthDp: Int) = requireSupportedViewport(viewportWidthDp.toFloat())

    fun widthClass(viewportWidthDp: Float): RasvenWidthClass {
        requireSupportedViewport(viewportWidthDp)
        return if (viewportWidthDp < RasvenProductLayout.responsiveBaseThreshold.toFloat()) {
            RasvenWidthClass.Compact
        } else {
            RasvenWidthClass.Regular
        }
    }

    fun widthClass(viewportWidthDp: Int): RasvenWidthClass = widthClass(viewportWidthDp.toFloat())

    fun pageHorizontalMargin(viewportWidthDp: Float): Int =
        when (widthClass(viewportWidthDp)) {
            RasvenWidthClass.Compact -> RasvenProductLayout.pageMarginCompact
            RasvenWidthClass.Regular -> RasvenProductLayout.pageMarginBase
        }

    fun pageHorizontalMargin(viewportWidthDp: Int): Int =
        when (widthClass(viewportWidthDp)) {
            RasvenWidthClass.Compact -> RasvenProductLayout.pageMarginCompact
            RasvenWidthClass.Regular -> RasvenProductLayout.pageMarginBase
        }

    fun bottomNavigationWidth(viewportWidthDp: Int): Int {
        requireSupportedViewport(viewportWidthDp)
        return (viewportWidthDp - 16).coerceIn(
            RasvenProductLayout.bottomNavMinWidth,
            RasvenProductLayout.bottomNavMaxWidth,
        )
    }

    fun destinationInputWidth(viewportWidthDp: Int): Int {
        requireSupportedViewport(viewportWidthDp)
        return (viewportWidthDp - 48).coerceIn(
            RasvenProductLayout.destinationInputMinWidth,
            RasvenProductLayout.destinationInputMaxWidth,
        )
    }

    fun callControlsGroupWidth(viewportWidthDp: Int): Int {
        requireSupportedViewport(viewportWidthDp)
        return (viewportWidthDp - 32).coerceIn(
            RasvenProductLayout.callControlsGroupCompactWidth,
            RasvenProductLayout.callControlsGroupMaxWidth,
        )
    }

    fun callControlSize(viewportWidthDp: Int): RasvenCallControlSize =
        when (widthClass(viewportWidthDp)) {
            RasvenWidthClass.Compact -> RasvenCallControlSize(
                widthDp = RasvenProductLayout.callControlCompactWidth,
                heightDp = RasvenProductLayout.callControlHeight,
            )
            RasvenWidthClass.Regular -> RasvenCallControlSize(
                widthDp = RasvenProductLayout.callControlRegularWidth,
                heightDp = RasvenProductLayout.callControlHeight,
            )
        }
}
