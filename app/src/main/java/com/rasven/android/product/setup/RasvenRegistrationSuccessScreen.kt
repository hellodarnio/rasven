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
import androidx.compose.ui.semantics.heading
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
import com.rasven.android.designsystem.components.RasvenButton
import com.rasven.android.designsystem.components.RasvenButtonSize
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenResponsive
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens

/** Final Product Figma 766:5830 — Registered presentation only. */
internal object RasvenRegistrationSuccessContract {
    const val headerToBadgeGapDp = 32
    const val badgeToIndicatorGapDp = 46
    const val indicatorSizeDp = 88
    const val successIconSizeDp = 32
    const val indicatorToCardGapDp = 26
    const val cardInsetDp = 16
}

/**
 * Stage5A7 is a pure Registered-state surface. It has no permission, account-data, registration,
 * or navigation side effects. The owner supplies [onEnterDialer]; the daily-use Dialer is outside
 * this stage's authorized implementation scope.
 */
@Composable
internal fun RasvenRegistrationSuccessScreen(
    persianFontFamily: FontFamily,
    onEnterDialer: () -> Unit,
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
                RegistrationSuccessHeader(pageMargin, pageFont)

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            top = RasvenRegistrationSuccessContract.headerToBadgeGapDp.dp,
                            start = pageMargin,
                            end = pageMargin,
                            bottom = RasvenSemanticLayout.spacingInsetDefault.dp,
                        ),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        RasvenBadge(
                            label = stringResource(R.string.registration_success_status),
                            type = RasvenBadgeType.Success,
                            style = RasvenBadgeStyle.Soft,
                            size = RasvenBadgeSize.Medium,
                            language = setupBadgeLanguage(),
                            fontFamily = pageFont,
                            modifier = Modifier.align(Alignment.Center)
                                .testTag("registration_success_badge"),
                        )
                    }

                    Spacer(Modifier.height(RasvenRegistrationSuccessContract.badgeToIndicatorGapDp.dp))

                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        RegistrationSuccessIndicator()
                    }

                    Spacer(Modifier.height(RasvenRegistrationSuccessContract.indicatorToCardGapDp.dp))

                    RegistrationSuccessCard(
                        modifier = Modifier.fillMaxWidth().testTag("registration_success_card"),
                        persianFontFamily = pageFont,
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth().padding(
                        start = pageMargin,
                        end = pageMargin,
                        bottom = RasvenSemanticLayout.spacingInsetDefault.dp,
                    ),
                ) {
                    RasvenButton(
                        label = stringResource(R.string.registration_success_enter_dialer),
                        size = RasvenButtonSize.Large,
                        fontFamily = pageFont,
                        onClick = onEnterDialer,
                        modifier = Modifier.fillMaxWidth().testTag("registration_success_enter_dialer"),
                        expandToFullWidth = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun RegistrationSuccessHeader(
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
                text = stringResource(R.string.registration_success_heading),
                modifier = Modifier.semantics { heading() }.testTag("registration_success_heading"),
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
                text = stringResource(R.string.registration_success_subheading),
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
private fun RegistrationSuccessIndicator() {
    val status = stringResource(R.string.registration_success_status)
    Box(
        modifier = Modifier
            .size(RasvenRegistrationSuccessContract.indicatorSizeDp.dp)
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), CircleShape)
            .clip(CircleShape)
            .background(RasvenDesignTheme.colors[RasvenSemanticColors.statusSuccessBackground])
            .border(
                RasvenSemanticLayout.borderWidthDefault.dp,
                RasvenDesignTheme.colors[RasvenSemanticColors.statusSuccessBorder],
                CircleShape,
            )
            .semantics { stateDescription = status }
            .testTag("registration_success_indicator"),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.rasven_registration_success_official),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                RasvenDesignTheme.colors[RasvenSemanticColors.statusSuccessIcon],
            ),
            modifier = Modifier.size(RasvenRegistrationSuccessContract.successIconSizeDp.dp),
        )
    }
}

@Composable
private fun RegistrationSuccessCard(
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
            .padding(RasvenRegistrationSuccessContract.cardInsetDp.dp),
        verticalArrangement = Arrangement.spacedBy(RasvenSemanticLayout.spacingStackSm.dp),
    ) {
        BasicText(
            text = stringResource(R.string.registration_success_card_title),
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
            text = stringResource(R.string.registration_success_card_body),
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
