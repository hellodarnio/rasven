package com.rasven.android.product.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.components.RasvenButton
import com.rasven.android.designsystem.components.RasvenButtonSize
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.LocalRasvenProductLanguage
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies

/** Figma 1532:13011, both 360 x 800 welcome frames. Geometry is in logical dp. */
internal object RasvenSetupWelcomeContract {
    const val headerHeightDp = 48
    const val heroWidthDp = 280
    const val heroHeightDp = 252
    const val horizontalInsetDp = 16
    const val topInsetAfterStatusBarDp = 16
    const val minimumBottomInsetDp = 16

    fun ambientLeftDp(viewportWidthDp: Float, english: Boolean): Float =
        if (english) -115f else viewportWidthDp - 205f

    fun bottomInsetDp(navigationBarInsetDp: Float): Float =
        maxOf(minimumBottomInsetDp.toFloat(), navigationBarInsetDp)
}

@Composable
internal fun RasvenSetupWelcomeScreen(
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onStartSetup: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectLanguage: (RasvenLanguage) -> Unit = {},
) {
    val english = LocalRasvenProductLanguage.current == RasvenLanguage.English
    val pageFont = if (english) englishFontFamily else persianFontFamily
    val textDirection = if (english) TextDirection.ContentOrLtr else TextDirection.ContentOrRtl

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp)
            .clipToBounds()
            .testTag("welcome_screen"),
    ) {
        val viewportWidthDp = maxWidth.value
        // The decorative layer has physical coordinates; Persian must move it to the right
        // without letting the surrounding RTL layout reverse its own origin.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(R.drawable.rasven_welcome_ambient),
                    contentDescription = null,
                    modifier = Modifier.absoluteOffset(
                        x = RasvenSetupWelcomeContract.ambientLeftDp(viewportWidthDp, english).dp,
                        y = (-95).dp,
                    ).size(320.dp).testTag("welcome_ambient"),
                )
            }
        }

        val compact = maxHeight < 620.dp
        val navigationBarInsetDp = with(LocalDensity.current) {
            WindowInsets.navigationBars.getBottom(this).toDp().value
        }
        Column(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding()
                .padding(
                    top = RasvenSetupWelcomeContract.topInsetAfterStatusBarDp.dp,
                    bottom = RasvenSetupWelcomeContract.bottomInsetDp(navigationBarInsetDp).dp,
                    start = RasvenSetupWelcomeContract.horizontalInsetDp.dp,
                    end = RasvenSetupWelcomeContract.horizontalInsetDp.dp,
                )
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = if (compact) Arrangement.Top else Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WelcomeHeader(english, persianFontFamily, onSelectLanguage)
            if (compact) Spacer(Modifier.height(24.dp))
            WelcomeMain(pageFont, textDirection)
            if (compact) Spacer(Modifier.height(24.dp))
            WelcomeFooter(pageFont, textDirection, onStartSetup)
        }
    }
}

@Composable
private fun WelcomeHeader(english: Boolean, persianFontFamily: FontFamily, onSelectLanguage: (RasvenLanguage) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth().height(RasvenSetupWelcomeContract.headerHeightDp.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (english) WelcomeWordmark()
            WelcomeLanguageButton(english, persianFontFamily) {
                onSelectLanguage(if (english) RasvenLanguage.Persian else RasvenLanguage.English)
            }
            if (!english) WelcomeWordmark()
        }
    }
}

@Composable
private fun WelcomeWordmark() {
    Image(
        painter = painterResource(R.drawable.rasven_welcome_wordmark),
        contentDescription = stringResource(R.string.app_name),
        modifier = Modifier.size(width = 134.2.dp, height = 30.37.dp).testTag("welcome_wordmark"),
    )
}

@Composable
private fun WelcomeLanguageButton(english: Boolean, persianFontFamily: FontFamily, onClick: () -> Unit) {
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusControl.dp)
    Row(
        modifier = Modifier.size(width = if (english) 122.dp else 128.dp, height = 48.dp)
            .clip(shape)
            .background(colors[RasvenSemanticColors.actionSecondaryBackground])
            .border(1.dp, colors[RasvenSemanticColors.actionSecondaryBorder], shape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("welcome_language_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (!english) WelcomeGlobe()
        BasicText(
            text = stringResource(if (english) R.string.welcome_switch_to_persian else R.string.welcome_switch_to_english),
            style = TextStyle(
                color = colors[RasvenSemanticColors.actionSecondaryText],
                fontFamily = if (english) persianFontFamily else RasvenAndroidFontFamilies.English,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 24.sp,
            ),
            maxLines = 1,
        )
        if (english) WelcomeGlobe()
    }
}

@Composable
private fun WelcomeGlobe() {
    Image(
        painter = painterResource(R.drawable.rasven_welcome_globe),
        contentDescription = null,
        modifier = Modifier.size(20.dp).testTag("welcome_globe"),
    )
}

@Composable
private fun WelcomeMain(pageFont: FontFamily, textDirection: TextDirection) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WelcomeHero()
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                text = stringResource(R.string.welcome_promise),
                modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag("welcome_promise"),
                style = TextStyle(
                    color = RasvenDesignTheme.colors[RasvenSemanticColors.textPrimary],
                    fontFamily = pageFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    lineHeight = 40.sp,
                    textAlign = TextAlign.Center,
                    textDirection = textDirection,
                ),
            )
            BasicText(
                text = stringResource(R.string.welcome_explanation),
                modifier = Modifier.fillMaxWidth().testTag("welcome_explanation"),
                style = TextStyle(
                    color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                    fontFamily = pageFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    textDirection = textDirection,
                ),
            )
        }
    }
}

@Composable
private fun WelcomeHero() {
    // Figma's hero is one physical artwork in both locales. Its absolute child coordinates
    // must never be resolved from the right edge when the surrounding copy is RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier.size(
                width = RasvenSetupWelcomeContract.heroWidthDp.dp,
                height = RasvenSetupWelcomeContract.heroHeightDp.dp,
            ).testTag("welcome_hero"),
        ) {
            WelcomeHeroImage(R.drawable.rasven_welcome_halo, 8f, -6f, 264f, 264f, "welcome_halo")
            WelcomeHeroImage(R.drawable.rasven_welcome_orbit_outer, 25f, 11f, 230f, 230f, "welcome_orbit_outer")
            WelcomeHeroImage(R.drawable.rasven_welcome_orbit_inner, 47f, 33f, 186f, 186f, "welcome_orbit_inner")
            WelcomeHeroImage(R.drawable.rasven_welcome_dot_left, 24f, 155f, 14f, 14f, "welcome_dot_left")
            WelcomeHeroImage(R.drawable.rasven_welcome_dot_right, 245f, 73f, 10f, 10f, "welcome_dot_right")

            val cardShape = RoundedCornerShape(36.dp)
            Box(
                modifier = Modifier.absoluteOffset(x = 74.dp, y = 59.dp)
                    .size(132.dp)
                    .rasvenShadow(RasvenEffects.glassInput.shadows.single(), cardShape)
                    .clip(cardShape)
                    .background(Color.White.copy(alpha = 0.84f))
                    .border(1.dp, Color.White.copy(alpha = 0.94f), cardShape)
                    .testTag("welcome_symbol_card"),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.rasven_welcome_symbol),
                    contentDescription = null,
                    modifier = Modifier.size(width = 64.dp, height = 62.44.dp).testTag("welcome_symbol"),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.WelcomeHeroImage(
    drawable: Int, xDp: Float, yDp: Float, widthDp: Float, heightDp: Float, tag: String,
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier = Modifier.absoluteOffset(x = xDp.dp, y = yDp.dp)
            // The halo intentionally overflows the 252 dp hero by 6 dp on each edge.
            // Lift the parent's height cap without Compose centering the overflow.
            .wrapContentSize(align = Alignment.TopStart, unbounded = true)
            .size(width = widthDp.dp, height = heightDp.dp)
            .testTag(tag),
    )
}

@Composable
private fun WelcomeFooter(pageFont: FontFamily, textDirection: TextDirection, onStartSetup: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText(
            text = stringResource(R.string.welcome_account_hint),
            modifier = Modifier.fillMaxWidth().testTag("welcome_account_hint"),
            style = TextStyle(
                color = RasvenDesignTheme.colors[RasvenSemanticColors.textTertiary],
                fontFamily = pageFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                textDirection = textDirection,
            ),
            maxLines = 2,
        )
        RasvenButton(
            label = stringResource(R.string.welcome_continue),
            size = RasvenButtonSize.Large,
            fontFamily = pageFont,
            onClick = onStartSetup,
            modifier = Modifier.fillMaxWidth().testTag("welcome_continue"),
            expandToFullWidth = true,
        )
    }
}
