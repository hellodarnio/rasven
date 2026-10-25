package com.rasven.android.product.call

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.LocalRasvenProductLanguage
import com.rasven.android.designsystem.foundation.productDataFont
import com.rasven.android.designsystem.foundation.productIsEnglish
import com.rasven.android.designsystem.foundation.productPageFont
import com.rasven.android.designsystem.foundation.productTextAlignment
import com.rasven.android.designsystem.foundation.productTextDirection
import com.rasven.android.designsystem.foundation.productUiDirection
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenResponsive
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens
import kotlinx.coroutines.delay

@Composable
internal fun RasvenCallEndSummaryScreen(
    summary: RasvenCallEndSummary,
    persianFontFamily: FontFamily,
    onReturnToPrevious: () -> Unit,
) {
    BackHandler(onBack = onReturnToPrevious)
    LaunchedEffect(summary) {
        delay(5_000)
        onReturnToPrevious()
    }
    val pageFont = productPageFont(persianFontFamily)
    val english = productIsEnglish()
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val summaryText = formatActiveCallDuration(summary.connectedAtElapsedMillis, summary.endedAtElapsedMillis, LocalRasvenProductLanguage.current)

    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().clip(shape).rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp),
    ) {
        val horizontalMargin = RasvenResponsive.pageHorizontalMargin(maxWidth.value).dp
        if (english) Image(
            painterResource(R.drawable.rasven_setup_ambient_en), null,
            modifier = Modifier.align(Alignment.TopStart).size(210.dp, 200.dp),
        ) else Image(
            painter = painterResource(R.drawable.rasven_end_summary_ambient),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 90.dp, y = (-100).dp).size(300.dp),
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(96.dp))
            SummaryCard(
                modifier = Modifier.fillMaxWidth().widthIn(min = 288.dp, max = 364.dp).height(118.dp),
                shape = shape,
            ) {
                CompositionLocalDirectionLtr {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            SummaryText(
                                value = stringResource(R.string.call_end_summary_title),
                                font = pageFont,
                                size = 14,
                                lineHeight = 24,
                                weight = 600,
                                color = colors[RasvenSemanticColors.textPrimary],
                                modifier = Modifier.fillMaxWidth().semantics { heading() },
                            )
                            SummaryText(
                                value = stringResource(R.string.call_end_summary_duration, summaryText),
                                font = pageFont,
                                size = 12,
                                lineHeight = 18,
                                weight = 400,
                                color = colors[RasvenSemanticColors.textSecondary],
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Image(
                            painter = painterResource(if (english) R.drawable.rasven_en_end_summary_avatar
                                else R.drawable.rasven_end_summary_call_avatar),
                            contentDescription = null,
                            modifier = Modifier.size(68.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(274.dp))
            SummaryCard(
                modifier = Modifier.fillMaxWidth().widthIn(min = 288.dp, max = 364.dp).height(104.dp),
                shape = shape,
            ) {
                CompositionLocalProductDirection {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        SummaryText(summary.displayIdentity, pageFont, 14, 24, 600,
                            colors[RasvenSemanticColors.textPrimary], Modifier.fillMaxWidth())
                        TechnicalSummaryText(summary.callableNumber, colors[RasvenSemanticColors.textSecondary])
                        SummaryText(stringResource(R.string.call_end_summary_now), pageFont, 12, 18, 400,
                            colors[RasvenSemanticColors.textSecondary], Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            SummaryCard(
                modifier = Modifier.fillMaxWidth().widthIn(min = 288.dp, max = 364.dp).height(118.dp),
                shape = shape,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    SummaryText(stringResource(R.string.call_end_summary_history_title), pageFont,
                        14, 24, 600, colors[RasvenSemanticColors.textPrimary], Modifier.fillMaxWidth())
                    SummaryText(stringResource(R.string.call_end_summary_return_hint), pageFont,
                        12, 18, 400, colors[RasvenSemanticColors.textSecondary], Modifier.fillMaxWidth(),
                        maxLines = if (english) 3 else 1)
                }
            }
            Spacer(Modifier.height(62.dp))
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, shape: RoundedCornerShape, content: @Composable () -> Unit) {
    val colors = RasvenDesignTheme.colors
    Box(
        modifier.rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape)
            .clip(shape)
            .background(colors[RasvenSemanticColors.surfaceGlass])
            .border(1.dp, colors[RasvenSemanticColors.borderGlass], shape),
    ) { content() }
}

@Composable
private fun SummaryText(
    value: String,
    font: FontFamily,
    size: Int,
    lineHeight: Int,
    weight: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    maxLines: Int = 1,
) {
    androidx.compose.foundation.text.BasicText(
        value,
        modifier = modifier,
        style = androidx.compose.ui.text.TextStyle(
            color = color,
            fontFamily = font,
            fontWeight = FontWeight(weight),
            fontSize = size.sp,
            lineHeight = lineHeight.sp,
            textAlign = productTextAlignment(),
            textDirection = productTextDirection(),
        ),
        maxLines = maxLines,
    )
}

@Composable
private fun TechnicalSummaryText(value: String, color: androidx.compose.ui.graphics.Color) {
    CompositionLocalProductDirection {
        androidx.compose.foundation.text.BasicText(
            value,
            modifier = Modifier.fillMaxWidth(),
            style = androidx.compose.ui.text.TextStyle(
                color = color,
                fontFamily = productDataFont(FontFamily.Default),
                fontWeight = FontWeight(RasvenTypographyTokens.bodyS.fontWeight),
                fontSize = RasvenTypographyTokens.bodyS.fontSizeSp.sp,
                lineHeight = RasvenTypographyTokens.bodyS.lineHeightSp.sp,
                textAlign = productTextAlignment(),
                textDirection = TextDirection.Ltr,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun CompositionLocalProductDirection(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides productUiDirection()) {
        content()
    }
}

@Composable
private fun CompositionLocalDirectionLtr(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        content()
    }
}
