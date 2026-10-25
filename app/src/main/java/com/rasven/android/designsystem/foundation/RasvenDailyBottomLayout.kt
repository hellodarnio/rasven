package com.rasven.android.designsystem.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keep the application navigation and its dependent actions at the Figma safe-area baseline. */
internal object RasvenDailyBottomContract {
    const val liftFraction = 0f
    const val reservedNavigationHeightDp = 88f // Existing 72dp bar + 16dp separation.
    fun liftDp(usableHeightDp: Float): Float {
        require(usableHeightDp.isFinite() && usableHeightDp >= 0f)
        return usableHeightDp * liftFraction
    }
    fun contentHeightDp(usableHeightDp: Float, topDp: Float): Float {
        require(topDp.isFinite() && topDp >= 0f)
        return (usableHeightDp - topDp - reservedNavigationHeightDp - liftDp(usableHeightDp)).coerceAtLeast(0f)
    }
}

private val LocalDailyUsableHeight = staticCompositionLocalOf { 0.dp }
private val LocalDailyBottomLift = staticCompositionLocalOf { 0.dp }
@Composable internal fun dailyBottomLift(): Dp = LocalDailyBottomLift.current
@Composable internal fun dailyRemainingContentHeight(top: Dp): Dp =
    RasvenDailyBottomContract.contentHeightDp(LocalDailyUsableHeight.current.value, top.value).dp

/** Caller applies system/IME insets first. Bottom-anchored dependent controls share the lift. */
@Composable internal fun RasvenDailyBottomLayout(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(
            LocalDailyUsableHeight provides maxHeight,
            LocalDailyBottomLift provides RasvenDailyBottomContract.liftDp(maxHeight.value).dp,
        ) {
            Box(Modifier.fillMaxSize().testTag("daily_usable_viewport"), content = content)
        }
    }
}
