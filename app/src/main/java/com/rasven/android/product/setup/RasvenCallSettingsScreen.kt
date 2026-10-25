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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.components.RasvenButton
import com.rasven.android.designsystem.components.RasvenButtonSize
import com.rasven.android.designsystem.components.RasvenTextField
import com.rasven.android.designsystem.components.RasvenTextFieldAppearance
import com.rasven.android.designsystem.components.RasvenTextFieldSize
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenResponsive
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens

/** Production Figma 765:5633. All dimensions are DP and use the existing responsive override. */
internal object RasvenCallSettingsContract {
    const val headerToFieldGapDp = 28
    const val fieldToTransferCardGapDp = 32
    const val transferCardInsetDp = 16
    const val transferCardContentGapDp = 8

    fun prefixAlignment(direction: LayoutDirection): TextAlign =
        if (direction == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
}

/**
 * Bounded Stage5A3 screen. The example prefix "9" is a hint, not real account state.
 * The Product defines no required flag, error variant, digit limit or input sanitization for
 * this field: preserve exactly what the user enters. Do not implement dial routing here.
 * "Save and connect" is an event boundary. The authorized Bootstrap host may route that event
 * to Stage5A4; registration, persistence and network connectivity remain out of scope here.
 */
@Composable
internal fun RasvenCallSettingsScreen(
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onSaveAndConnect: (contactPrefix: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageFont = setupPageFont(persianFontFamily)
    var contactPrefix by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    BoxWithConstraints(modifier = modifier.fillMaxSize().rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp)) {
        val pageMargin = setupPageMargin(maxWidth.value)
        // Exported Figma ambient has its own crop; reusing the approved Stage5A1 asset.
        RasvenSetupAmbient()


        CompositionLocalProvider(LocalLayoutDirection provides setupLayoutDirection()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                CallSettingsHeader(pageMargin = pageMargin, persianFontFamily = pageFont)
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            top = RasvenCallSettingsContract.headerToFieldGapDp.dp,
                            start = pageMargin,
                            end = pageMargin,
                            bottom = RasvenSemanticLayout.spacingInsetDefault.dp,
                        )
                        .testTag("call_settings_content"),
                ) {
                    RasvenTextField(
                        value = contactPrefix,
                        onValueChange = { contactPrefix = it },
                        label = stringResource(R.string.call_settings_contact_prefix),
                        placeholder = stringResource(R.string.call_settings_contact_prefix_example),
                        helper = stringResource(R.string.call_settings_contact_prefix_helper),
                        required = false,
                        appearance = RasvenTextFieldAppearance.Glass,
                        // Android Owner override: original Figma Small -> official DS Medium.
                        size = RasvenTextFieldSize.Medium,
                        fontFamily = pageFont,
                        valueFontFamily = englishFontFamily,
                        layoutDirection = setupLayoutDirection(),
                        valueTextDirection = TextDirection.Ltr,
                        valueTextAlign = RasvenCallSettingsContract.prefixAlignment(LocalLayoutDirection.current),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        inputModifier = Modifier.testTag("call_settings_contact_prefix"),
                        modifier = Modifier.fillMaxWidth().testTag("call_settings_prefix_field"),
                    )
                    Spacer(modifier = Modifier.size(RasvenCallSettingsContract.fieldToTransferCardGapDp.dp))
                    CallTransferExplanationCard(pageFont)
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(
                        start = pageMargin,
                        end = pageMargin,
                        bottom = RasvenSemanticLayout.spacingInsetDefault.dp,
                    ),
                ) {
                    RasvenButton(
                        label = stringResource(R.string.call_settings_save_connect),
                        size = RasvenButtonSize.Large,
                        fontFamily = pageFont,
                        onClick = { onSaveAndConnect(contactPrefix) },
                        modifier = Modifier.fillMaxWidth().testTag("call_settings_save_connect"),
                        expandToFullWidth = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun CallSettingsHeader(
    pageMargin: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
) {
    val heading = RasvenTypographyTokens.headingL
    val subtitle = RasvenTypographyTokens.bodyS
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
                text = stringResource(R.string.call_settings_heading),
                modifier = Modifier.semantics { heading() },
                style = TextStyle(
                    color = RasvenDesignTheme.colors[RasvenSemanticColors.textPrimary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(heading.fontWeight),
                    fontSize = heading.fontSizeSp.sp,
                    lineHeight = heading.lineHeightSp.sp,
                    textAlign = setupTextAlignment(),
                ),
            )
            BasicText(
                text = stringResource(R.string.call_settings_subheading),
                style = TextStyle(
                    color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(subtitle.fontWeight),
                    fontSize = subtitle.fontSizeSp.sp,
                    lineHeight = subtitle.lineHeightSp.sp,
                    textAlign = setupTextAlignment(),
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
private fun CallTransferExplanationCard(fontFamily: FontFamily) {
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusOverlay.dp)
    val stroke = RasvenSemanticLayout.borderWidthDefault.dp
    Column(
        modifier = Modifier.fillMaxWidth()
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape)
            .clip(shape)
            .background(RasvenDesignTheme.colors[RasvenSemanticColors.surfaceGlass])
            .border(stroke, RasvenDesignTheme.colors[RasvenSemanticColors.borderGlass], shape)
            .padding(stroke)
            .padding(RasvenCallSettingsContract.transferCardInsetDp.dp)
            .testTag("call_settings_transfer_card"),
        verticalArrangement = Arrangement.spacedBy(RasvenCallSettingsContract.transferCardContentGapDp.dp),
    ) {
        val smallHeading = RasvenTypographyTokens.headingS
        BasicText(
            text = stringResource(R.string.call_settings_transfer_heading),
            style = TextStyle(
                color = RasvenDesignTheme.colors[RasvenSemanticColors.textPrimary],
                fontFamily = fontFamily,
                fontWeight = FontWeight(smallHeading.fontWeight),
                fontSize = smallHeading.fontSizeSp.sp,
                lineHeight = smallHeading.lineHeightSp.sp,
                textAlign = setupTextAlignment(),
            ),
        )
        val body = RasvenTypographyTokens.bodyS
        BasicText(
            text = stringResource(R.string.call_settings_transfer_explanation),
            style = TextStyle(
                color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                fontFamily = fontFamily,
                fontWeight = FontWeight(body.fontWeight),
                fontSize = body.fontSizeSp.sp,
                lineHeight = body.lineHeightSp.sp,
                textAlign = setupTextAlignment(),
            ),
        )
    }
}
