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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

/** Final Product Figma 765:5759 with the frozen Android TextField size override. */
internal object RasvenAdvancedSettingsContract {
    const val portWidthDp = 120
    const val fieldRowGapDp = 12
    const val formVerticalGapDp = 12
    const val headerToSecurityGapDp = 12
    const val securityToFormGapDp = 16
    const val securityCardInsetDp = 16

    fun passwordValueAlignment(interfaceDirection: LayoutDirection): TextAlign =
        if (interfaceDirection == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
}

/**
 * Stage5A4 edits non-secret account values plus an optional NEW password replacement.
 * The current password is structurally unavailable to this composable and cannot be rendered or recovered.
 * No persistence, registration, reconnect, permission flow, Stage5A5 route or IAX2 action is performed here.
 */
@Composable
internal fun RasvenAdvancedSettingsScreen(
    currentAccount: RasvenAdvancedSettingsCurrentAccount,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onSaveChanges: (RasvenAdvancedSettingsUpdate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageFont = setupPageFont(persianFontFamily)
    var server by remember(currentAccount.server) { mutableStateOf(currentAccount.server) }
    var port by remember(currentAccount.port) { mutableStateOf(currentAccount.port.toString()) }
    var username by remember(currentAccount.username) { mutableStateOf(currentAccount.username) }
    // Replacement only. Never seeded from, saved as, or compared with the current stored secret.
    var passwordReplacement by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val errors = if (attempted) {
        RasvenAdvancedSettingsValidation.validate(server, port, username)
    } else {
        RasvenAdvancedSettingsErrors()
    }
    val focusManager = LocalFocusManager.current
    val englishFocusOrder = setupLayoutDirection() == LayoutDirection.Ltr
    val portFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val requiredSemantics = stringResource(R.string.account_required_accessibility)
    val appearance = RasvenTextFieldAppearance.Glass

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp),
    ) {
        val pageMargin = setupPageMargin(maxWidth.value)

        RasvenSetupAmbient()


        CompositionLocalProvider(LocalLayoutDirection provides setupLayoutDirection()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                AdvancedSettingsHeader(
                    pageMargin = pageMargin,
                    persianFontFamily = pageFont,
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = pageMargin,
                            end = pageMargin,
                            top = RasvenAdvancedSettingsContract.headerToSecurityGapDp.dp,
                            bottom = RasvenSemanticLayout.spacingInsetDefault.dp,
                        )
                        .testTag("advanced_settings_content"),
                ) {
                    AdvancedSettingsSecurityNote(pageFont)
                    Spacer(Modifier.height(RasvenAdvancedSettingsContract.securityToFormGapDp.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("advanced_settings_form"),
                        verticalArrangement = Arrangement.spacedBy(RasvenAdvancedSettingsContract.formVerticalGapDp.dp),
                    ) {
                        RasvenTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = stringResource(R.string.advanced_settings_server),
                            helper = if (errors.server != null) stringResource(R.string.account_server_required) else null,
                            required = true,
                            requiredAccessibilityText = requiredSemantics,
                            isError = errors.server != null,
                            fontFamily = pageFont,
                            valueFontFamily = englishFontFamily,
                            layoutDirection = setupLayoutDirection(),
                            valueTextDirection = TextDirection.Ltr,
                            valueTextAlign = setupTextAlignment(),
                            appearance = appearance,
                            // Owner override: Figma Medium -> existing official DS Large.
                            size = RasvenTextFieldSize.Large,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { if (englishFocusOrder) portFocus.requestFocus() else focusManager.moveFocus(FocusDirection.Next) }),
                            inputModifier = Modifier.testTag("advanced_settings_server"),
                            modifier = Modifier.fillMaxWidth().testTag("advanced_settings_server_field"),
                        )
                        RasvenSetupAccountRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(RasvenAdvancedSettingsContract.fieldRowGapDp.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Same verified RTL composition/focus order as Stage5A2: Username -> Port.
                            RasvenTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = stringResource(R.string.advanced_settings_username),
                                helper = if (errors.username != null) stringResource(R.string.account_username_required) else null,
                                required = true,
                                requiredAccessibilityText = requiredSemantics,
                                isError = errors.username != null,
                                fontFamily = pageFont,
                                valueFontFamily = englishFontFamily,
                                layoutDirection = setupLayoutDirection(),
                                valueTextDirection = TextDirection.Ltr,
                                valueTextAlign = setupTextAlignment(),
                                appearance = appearance,
                                // Owner override: Figma Small -> existing official DS Medium.
                                size = RasvenTextFieldSize.Medium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { if (englishFocusOrder) passwordFocus.requestFocus() else focusManager.moveFocus(FocusDirection.Next) }),
                                inputModifier = Modifier.focusRequester(usernameFocus).testTag("advanced_settings_username"),
                                modifier = Modifier.weight(1f).testTag("advanced_settings_username_field"),
                            )
                            RasvenTextField(
                                value = port,
                                onValueChange = { port = it },
                                label = stringResource(R.string.advanced_settings_port),
                                helper = if (errors.port != null) stringResource(R.string.account_port_range) else null,
                                required = true,
                                requiredAccessibilityText = requiredSemantics,
                                isError = errors.port != null,
                                fontFamily = pageFont,
                                valueFontFamily = englishFontFamily,
                                layoutDirection = setupLayoutDirection(),
                                valueTextDirection = TextDirection.Ltr,
                                valueTextAlign = setupTextAlignment(),
                                appearance = appearance,
                                // Owner override: Figma Small -> existing official DS Medium.
                                size = RasvenTextFieldSize.Medium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { if (englishFocusOrder) usernameFocus.requestFocus() else focusManager.moveFocus(FocusDirection.Next) }),
                                inputModifier = Modifier.focusRequester(portFocus).testTag("advanced_settings_port"),
                                modifier = Modifier.width(RasvenAdvancedSettingsContract.portWidthDp.dp)
                                    .testTag("advanced_settings_port_field"),
                            )
                        }
                        RasvenTextField(
                            value = passwordReplacement,
                            onValueChange = { passwordReplacement = it },
                            label = stringResource(R.string.advanced_settings_password),
                            helper = stringResource(R.string.advanced_settings_password_helper),
                            required = false,
                            isError = false,
                            fontFamily = pageFont,
                            valueFontFamily = englishFontFamily,
                            layoutDirection = setupLayoutDirection(),
                            valueTextDirection = TextDirection.Ltr,
                            valueTextAlign = RasvenAdvancedSettingsContract.passwordValueAlignment(LocalLayoutDirection.current),
                            appearance = appearance,
                            // Owner override: Figma Small -> existing official DS Medium.
                            size = RasvenTextFieldSize.Medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            visualTransformation = PasswordVisualTransformation(),
                            inputModifier = Modifier.focusRequester(passwordFocus).testTag("advanced_settings_password"),
                            accessibilityLabel = stringResource(R.string.advanced_settings_password),
                            modifier = Modifier.fillMaxWidth().testTag("advanced_settings_password_field"),
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(
                        start = pageMargin,
                        end = pageMargin,
                        bottom = RasvenSemanticLayout.spacingInsetDefault.dp,
                    ),
                ) {
                    RasvenButton(
                        label = stringResource(R.string.advanced_settings_save),
                        size = RasvenButtonSize.Large,
                        fontFamily = pageFont,
                        onClick = {
                            attempted = true
                            RasvenAdvancedSettingsValidation.validatedUpdate(
                                server = server,
                                port = port,
                                username = username,
                                passwordReplacement = passwordReplacement,
                            )?.let(onSaveChanges)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("advanced_settings_save"),
                        expandToFullWidth = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedSettingsHeader(
    pageMargin: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
) {
    val headingStyle = RasvenTypographyTokens.headingL
    val subtitleStyle = RasvenTypographyTokens.bodyS
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
                text = stringResource(R.string.advanced_settings_heading),
                modifier = Modifier.semantics { heading() },
                style = TextStyle(
                    color = RasvenDesignTheme.colors[RasvenSemanticColors.textPrimary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(headingStyle.fontWeight),
                    fontSize = headingStyle.fontSizeSp.sp,
                    lineHeight = headingStyle.lineHeightSp.sp,
                    textAlign = setupTextAlignment(),
                ),
            )
            BasicText(
                text = stringResource(R.string.advanced_settings_subheading),
                style = TextStyle(
                    color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(subtitleStyle.fontWeight),
                    fontSize = subtitleStyle.fontSizeSp.sp,
                    lineHeight = subtitleStyle.lineHeightSp.sp,
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
private fun AdvancedSettingsSecurityNote(fontFamily: FontFamily) {
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusOverlay.dp)
    val stroke = RasvenSemanticLayout.borderWidthDefault.dp
    val body = RasvenTypographyTokens.bodyS
    Box(
        modifier = Modifier.fillMaxWidth()
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape)
            .clip(shape)
            .background(RasvenDesignTheme.colors[RasvenSemanticColors.surfaceGlass])
            .border(stroke, RasvenDesignTheme.colors[RasvenSemanticColors.borderGlass], shape)
            .padding(stroke)
            .padding(RasvenAdvancedSettingsContract.securityCardInsetDp.dp)
            .testTag("advanced_settings_security_note"),
    ) {
        BasicText(
            text = stringResource(R.string.advanced_settings_security_note),
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
