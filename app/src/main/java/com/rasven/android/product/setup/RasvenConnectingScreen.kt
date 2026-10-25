package com.rasven.android.product.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.components.RasvenBadge
import com.rasven.android.designsystem.components.RasvenBadgeLanguage
import com.rasven.android.designsystem.components.RasvenBadgeSize
import com.rasven.android.designsystem.components.RasvenBadgeStyle
import com.rasven.android.designsystem.components.RasvenBadgeType
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenResponsive
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens

/** Final Product Figma 766:5758 — Registering presentation only. */
internal object RasvenConnectingContract {
    const val headerToBadgeGapDp = 32
    const val badgeToIndicatorGapDp = 56
    const val indicatorSizeDp = 88
    const val loadingSlotSizeDp = 32
    const val loadingVisualWidthDp = 25.76f
    const val loadingVisualHeightDp = 26.67f
    const val indicatorToCardGapDp = 26
    const val cardInsetDp = 16
}

/**
 * Stage5A6 is a pure Registering-state surface. Registration starts outside the Composable through
 * the lifecycle-retained registration boundary, so recomposition cannot duplicate an attempt.
 */
@Composable
internal fun RasvenConnectingScreen(
    persianFontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    val pageFont = setupPageFont(persianFontFamily)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp),
    ) {
        val pageMargin = setupPageMargin(maxWidth.value)

        RasvenSetupAmbient()


        CompositionLocalProvider(LocalLayoutDirection provides setupLayoutDirection()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                ConnectingHeader(pageMargin, pageFont)

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            top = RasvenConnectingContract.headerToBadgeGapDp.dp,
                            start = pageMargin,
                            end = pageMargin,
                            bottom = RasvenSemanticLayout.spacingInsetDefault.dp,
                        ),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        RasvenBadge(
                            label = stringResource(R.string.connecting_status),
                            type = RasvenBadgeType.Info,
                            style = RasvenBadgeStyle.Soft,
                            size = RasvenBadgeSize.Medium,
                            language = setupBadgeLanguage(),
                            fontFamily = pageFont,
                            modifier = Modifier.align(Alignment.Center).testTag("connecting_status_badge"),
                        )
                    }

                    Spacer(Modifier.height(RasvenConnectingContract.badgeToIndicatorGapDp.dp))

                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ConnectingIndicator()
                    }

                    Spacer(Modifier.height(RasvenConnectingContract.indicatorToCardGapDp.dp))

                    ConnectingRegistrationCard(
                        modifier = Modifier.fillMaxWidth().testTag("connecting_registration_card"),
                        persianFontFamily = pageFont,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectingHeader(
    pageMargin: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
) {
    val headingStyle = RasvenTypographyTokens.headingL
    val subtitleStyle = RasvenTypographyTokens.bodyS
    val direction = LocalLayoutDirection.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(
            top = RasvenSemanticLayout.spacingInsetDefault.dp,
            start = pageMargin,
            end = pageMargin,
        ),
        horizontalArrangement = Arrangement.spacedBy(RasvenSemanticLayout.spacingInlineXl.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(RasvenSemanticLayout.spacingStackXs.dp),
        ) {
            BasicText(
                text = stringResource(R.string.connecting_heading),
                modifier = Modifier.semantics { heading() }.testTag("connecting_heading"),
                style = TextStyle(
                    color = RasvenDesignTheme.colors[RasvenSemanticColors.textPrimary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(headingStyle.fontWeight),
                    fontSize = headingStyle.fontSizeSp.sp,
                    lineHeight = headingStyle.lineHeightSp.sp,
                    textAlign = if (direction == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left,
                ),
            )
            BasicText(
                text = stringResource(R.string.connecting_subheading),
                style = TextStyle(
                    color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(subtitleStyle.fontWeight),
                    fontSize = subtitleStyle.fontSizeSp.sp,
                    lineHeight = subtitleStyle.lineHeightSp.sp,
                    textAlign = if (direction == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left,
                ),
            )
        }
        Image(
            painter = painterResource(R.drawable.rasven_brand_symbol_v01_candidate),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
    }
}

@Composable
private fun ConnectingIndicator() {
    val state = stringResource(R.string.connecting_registration_title)
    Box(
        modifier = Modifier
            .size(RasvenConnectingContract.indicatorSizeDp.dp)
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), CircleShape)
            .clip(CircleShape)
            .background(RasvenDesignTheme.colors[RasvenSemanticColors.statusInfoBackground])
            .border(
                RasvenSemanticLayout.borderWidthDefault.dp,
                RasvenDesignTheme.colors[RasvenSemanticColors.statusInfoBorder],
                CircleShape,
            )
            .semantics {
                stateDescription = state
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
            .testTag("connecting_loading_indicator"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(RasvenConnectingContract.loadingSlotSizeDp.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.rasven_connecting_loading_official),
                contentDescription = null,
                colorFilter = ColorFilter.tint(RasvenDesignTheme.colors[RasvenSemanticColors.iconPrimary]),
                modifier = Modifier.size(
                    width = RasvenConnectingContract.loadingVisualWidthDp.dp,
                    height = RasvenConnectingContract.loadingVisualHeightDp.dp,
                ),
            )
        }
    }
}

@Composable
private fun ConnectingRegistrationCard(
    modifier: Modifier,
    persianFontFamily: FontFamily,
) {
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusOverlay.dp)
    val stroke = RasvenSemanticLayout.borderWidthDefault.dp
    val title = RasvenTypographyTokens.headingS
    val body = RasvenTypographyTokens.bodyS

    Column(
        modifier = modifier
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape)
            .clip(shape)
            .background(RasvenDesignTheme.colors[RasvenSemanticColors.surfaceGlass])
            .border(stroke, RasvenDesignTheme.colors[RasvenSemanticColors.borderGlass], shape)
            .padding(stroke)
            .padding(RasvenConnectingContract.cardInsetDp.dp),
        verticalArrangement = Arrangement.spacedBy(RasvenSemanticLayout.spacingStackSm.dp),
    ) {
        BasicText(
            text = stringResource(R.string.connecting_registration_title),
            style = TextStyle(
                color = RasvenDesignTheme.colors[RasvenSemanticColors.textPrimary],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight(title.fontWeight),
                fontSize = title.fontSizeSp.sp,
                lineHeight = title.lineHeightSp.sp,
                textAlign = setupTextAlignment(),
            ),
        )
        BasicText(
            text = stringResource(R.string.connecting_registration_body),
            style = TextStyle(
                color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight(body.fontWeight),
                fontSize = body.fontSizeSp.sp,
                lineHeight = body.lineHeightSp.sp,
                textAlign = setupTextAlignment(),
            ),
        )
    }
}
