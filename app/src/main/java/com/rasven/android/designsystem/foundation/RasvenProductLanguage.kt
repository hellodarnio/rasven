package com.rasven.android.designsystem.foundation

import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rasven.android.R
import com.rasven.android.designsystem.components.RasvenBadgeLanguage
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import java.util.Locale

/** Explicit language ownership; a geometry-only LTR/RTL island never changes UI language. */
internal val LocalRasvenProductLanguage = staticCompositionLocalOf { RasvenLanguage.Persian }

/** Saved language changes only UI locals. The provider is not keyed by language
 * and does not recreate the Activity.
 */
@Composable
internal fun RasvenGatedProductLanguageScope(
    language: RasvenLanguage,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (enabled) RasvenProductLanguageScope(language, content) else content()
}

internal object RasvenDailyPresentationContract {
    fun direction(language: RasvenLanguage) = if (language == RasvenLanguage.Persian) LayoutDirection.Rtl else LayoutDirection.Ltr
    fun alignment(language: RasvenLanguage) = if (language == RasvenLanguage.Persian) TextAlign.Right else TextAlign.Left
    fun paragraph(language: RasvenLanguage) = if (language == RasvenLanguage.Persian) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr
    // EN Figma mirrors only these value-first rows, never the technical text or brand symbol.
    fun valueFirstRowDirection(language: RasvenLanguage) = if (language == RasvenLanguage.Persian) LayoutDirection.Ltr else LayoutDirection.Rtl
    fun dndWidth(language: RasvenLanguage) = if (language == RasvenLanguage.Persian) 164 else 185
    fun settingsValueWidth(language: RasvenLanguage) = if (language == RasvenLanguage.Persian) 72 else 96
    fun displayDigits(value: String, language: RasvenLanguage): String = if (language == RasvenLanguage.English) value else
        value.map { if (it in '0'..'9') ('۰'.code + (it - '0')).toChar() else it }.joinToString("")
}

@Composable internal fun productIsEnglish() = LocalRasvenProductLanguage.current == RasvenLanguage.English
@Composable internal fun productUiDirection() = RasvenDailyPresentationContract.direction(LocalRasvenProductLanguage.current)
@Composable internal fun productTextAlignment() = RasvenDailyPresentationContract.alignment(LocalRasvenProductLanguage.current)
@Composable internal fun productTextDirection() = RasvenDailyPresentationContract.paragraph(LocalRasvenProductLanguage.current)
@Composable internal fun productSemanticSeparator() = if (productIsEnglish()) ", " else "، "
@Composable internal fun productValueRowDirection() = RasvenDailyPresentationContract.valueFirstRowDirection(LocalRasvenProductLanguage.current)
@Composable internal fun productBadgeLanguage() = if (productIsEnglish()) RasvenBadgeLanguage.En else RasvenBadgeLanguage.Fa
@Composable internal fun productPageFont(persian: FontFamily) = if (productIsEnglish()) RasvenAndroidFontFamilies.English else persian
@Composable internal fun productDataFont(existing: FontFamily) = if (productIsEnglish()) RasvenAndroidFontFamilies.English else existing

/** UI-local resources only. No global locale, stored account, permission or transport mutations. */
@Composable internal fun RasvenProductLanguageScope(language: RasvenLanguage, content: @Composable () -> Unit) {
    val parent = LocalContext.current
    // Capture before LocalContext becomes a configuration wrapper so system launchers can
    // still resolve their owning Activity after a product-language change.
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val parentConfiguration = LocalConfiguration.current
    val localized = remember(parent, parentConfiguration, language) {
        val configuration = Configuration(parentConfiguration).apply {
            setLocale(Locale.forLanguageTag(RasvenAppLanguagePolicy.storageTag(language)))
        }
        parent.createConfigurationContext(configuration)
    }
    check(language != RasvenLanguage.English || localized.resources.getBoolean(R.bool.rasven_english_setup_resources_ready)) {
        "English requires the complete product locale resources"
    }
    val localizedContent: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalRasvenProductLanguage provides language,
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
            LocalLayoutDirection provides RasvenDailyPresentationContract.direction(language),
            content = content,
        )
    }
    if (activityResultRegistryOwner != null) {
        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
            content = localizedContent,
        )
    } else {
        localizedContent()
    }
}

@Composable internal fun RasvenDailyAmbient() {
    val english = productIsEnglish()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painterResource(if (english) R.drawable.rasven_home_ambient_en else R.drawable.rasven_home_ambient),
                contentDescription = null,
                modifier = Modifier.align(if (english) Alignment.TopStart else Alignment.TopEnd)
                    .size(155.dp, 142.dp).testTag("daily_ambient"),
            )
        }
    }
}
