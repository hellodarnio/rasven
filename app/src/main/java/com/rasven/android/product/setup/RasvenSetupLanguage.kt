package com.rasven.android.product.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rasven.android.R
import com.rasven.android.designsystem.components.RasvenBadgeLanguage
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.RasvenResponsive
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import com.rasven.android.designsystem.foundation.LocalRasvenProductLanguage
import com.rasven.android.designsystem.foundation.RasvenProductLanguageScope

@Composable internal fun setupPageMargin(widthDp: Float) =
    RasvenSetupPresentationContract.pageMargin(LocalRasvenProductLanguage.current, widthDp).dp

internal object RasvenSetupPresentationContract {
    fun pageMargin(language: RasvenLanguage, widthDp: Float): Int {
        require(widthDp >= 320f) { "Unsupported viewport below 320dp" }
        return if (language == RasvenLanguage.Persian) RasvenResponsive.pageHorizontalMargin(widthDp) else 16
    }
    fun direction(language: RasvenLanguage) =
        if (language == RasvenLanguage.Persian) LayoutDirection.Rtl else LayoutDirection.Ltr
    fun alignment(language: RasvenLanguage) =
        if (language == RasvenLanguage.Persian) TextAlign.Right else TextAlign.Left
    // EN's introduction has three lines at 360px; its next card starts at y240, not y264.
    fun introToBenefitsGap(language: RasvenLanguage) = if (language == RasvenLanguage.English) 2 else 26
}

@Composable internal fun setupLayoutDirection(): LayoutDirection =
    RasvenSetupPresentationContract.direction(LocalRasvenProductLanguage.current)

@Composable internal fun setupTextAlignment(): TextAlign =
    RasvenSetupPresentationContract.alignment(LocalRasvenProductLanguage.current)

@Composable internal fun setupIntroToBenefitsGap() =
    RasvenSetupPresentationContract.introToBenefitsGap(LocalRasvenProductLanguage.current).dp

@Composable internal fun setupBadgeLanguage(): RasvenBadgeLanguage =
    if (LocalRasvenProductLanguage.current == RasvenLanguage.Persian) RasvenBadgeLanguage.Fa else RasvenBadgeLanguage.En

@Composable internal fun setupPageFont(persian: FontFamily): FontFamily =
    if (LocalRasvenProductLanguage.current == RasvenLanguage.Persian) persian else RasvenAndroidFontFamilies.English

/** Resource selection is local to these UI surfaces, not a device/app locale mutation.
 * Product-language direction, fonts and resource strings are scoped consistently for setup screens.
 */
@Composable internal fun RasvenSetupLanguageScope(language: RasvenLanguage, content: @Composable () -> Unit) {
    RasvenProductLanguageScope(language, content)
}

@Composable internal fun RasvenSetupAmbient() {
    val english = LocalRasvenProductLanguage.current == RasvenLanguage.English
    // Native conversion of the same Figma 300px radial ellipse, clipped to its 210×200 slot.
    // EN translates it by (-90,-100); FA by (0,-100). Never mirror logos or tint the gradient.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painterResource(if (english) R.drawable.rasven_setup_ambient_en else R.drawable.rasven_setup_welcome_ambient),
                contentDescription = null,
                modifier = Modifier.align(if (english) Alignment.TopStart else Alignment.TopEnd)
                    .size(width = 210.dp, height = 200.dp).testTag("setup_ambient"),
            )
        }
    }
}

/** Both live FA and EN frames keep Port physically left, Username right. Do not mirror this row.
 * Each field explicitly receives its own language direction; this geometry island cannot leak RTL.
 */
@Composable internal fun RasvenSetupAccountRow(
    modifier: Modifier,
    horizontalArrangement: Arrangement.Horizontal,
    verticalAlignment: Alignment.Vertical,
    content: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(modifier, horizontalArrangement, verticalAlignment, content)
    }
}
