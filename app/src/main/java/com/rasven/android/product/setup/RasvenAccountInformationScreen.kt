package com.rasven.android.product.setup

import androidx.compose.foundation.Image
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenResponsive
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens

/** Figma 765:5295 normal and 765:5480 error are one state-driven account screen. */
internal object RasvenAccountInformationContract {
    const val portWidthDp = 120
    const val fieldRowGapDp = 12
    const val formVerticalGapDp = 12
    const val headerToFormGapDp = 26

    // Password's mask characters are direction-neutral: TextDirection.Ltr alone cannot
    // position the empty caret or masked glyphs at the correct UI-language edge.
    fun passwordValueAlignment(interfaceDirection: LayoutDirection): TextAlign =
        if (interfaceDirection == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
}

/**
 * The only output is a validated in-memory callback. No persistence, IAX2 networking or Stage5A3.
 * Validation is intentionally silent until the first Continue; subsequently errors follow edits.
 */
@Composable
internal fun RasvenAccountInformationScreen(
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onContinue: (RasvenValidatedAccount) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageFont = setupPageFont(persianFontFamily)
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    // Password is never rememberSaveable; app/Activity do not persist or log it.
    var password by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val errors = if (attempted) {
        RasvenAccountValidation.validate(server, port, username, password)
    } else {
        RasvenAccountErrors()
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

        // This approved clipped asset is shared with Stage5A1: never rasterize a screenshot.
        RasvenSetupAmbient()


        CompositionLocalProvider(LocalLayoutDirection provides setupLayoutDirection()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                AccountHeader(
                    pageMargin = pageMargin,
                    persianFontFamily = pageFont,
                    englishFontFamily = englishFontFamily,
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = pageMargin,
                            end = pageMargin,
                            top = RasvenAccountInformationContract.headerToFormGapDp.dp,
                            bottom = RasvenSemanticLayout.spacingInsetDefault.dp,
                        )
                        .testTag("account_form"),
                    verticalArrangement = Arrangement.spacedBy(RasvenAccountInformationContract.formVerticalGapDp.dp),
                ) {
                    RasvenTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = stringResource(R.string.account_server),
                        placeholder = stringResource(R.string.account_server_placeholder),
                        helper = if (errors.server != null) {
                            stringResource(R.string.account_server_required)
                        } else {
                            stringResource(R.string.account_server_helper)
                        },
                        required = true,
                        requiredAccessibilityText = requiredSemantics,
                        isError = errors.server != null,
                        fontFamily = pageFont,
                        valueFontFamily = englishFontFamily,
                        layoutDirection = setupLayoutDirection(),
                        valueTextDirection = TextDirection.Ltr,
                        valueTextAlign = setupTextAlignment(),
                        appearance = appearance,
                        // Android Owner override: original Figma Medium -> official DS Large.
                        size = RasvenTextFieldSize.Large,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { if (englishFocusOrder) portFocus.requestFocus() else focusManager.moveFocus(FocusDirection.Next) }),
                        inputModifier = Modifier.testTag("account_server"),
                        modifier = Modifier.fillMaxWidth().testTag("account_server_field"),
                    )
                    RasvenSetupAccountRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RasvenAccountInformationContract.fieldRowGapDp.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Figma x=132: username is the right-hand flexible field in RTL.
                        RasvenTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = stringResource(R.string.account_username),
                            placeholder = stringResource(R.string.account_username_placeholder),
                            helper = if (errors.username != null) {
                                stringResource(R.string.account_username_required)
                            } else null,
                            required = true,
                            requiredAccessibilityText = requiredSemantics,
                            isError = errors.username != null,
                            fontFamily = pageFont,
                            valueFontFamily = englishFontFamily,
                            layoutDirection = setupLayoutDirection(),
                            valueTextDirection = TextDirection.Ltr,
                            valueTextAlign = setupTextAlignment(),
                            appearance = appearance,
                            // Android Owner override: original Figma Small -> official DS Medium.
                            size = RasvenTextFieldSize.Medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { if (englishFocusOrder) passwordFocus.requestFocus() else focusManager.moveFocus(FocusDirection.Next) }),
                            inputModifier = Modifier.focusRequester(usernameFocus).testTag("account_username"),
                            modifier = Modifier.weight(1f).testTag("account_username_field"),
                        )
                        // Figma x=0: port remains fixed 120dp at the left edge.
                        RasvenTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = stringResource(R.string.account_port),
                            placeholder = stringResource(R.string.account_port_placeholder),
                            helper = if (errors.port != null) {
                                stringResource(R.string.account_port_range)
                            } else {
                                stringResource(R.string.account_port_helper)
                            },
                            required = true,
                            requiredAccessibilityText = requiredSemantics,
                            isError = errors.port != null,
                            fontFamily = pageFont,
                            valueFontFamily = englishFontFamily,
                            layoutDirection = setupLayoutDirection(),
                            valueTextDirection = TextDirection.Ltr,
                            valueTextAlign = setupTextAlignment(),
                            appearance = appearance,
                            size = RasvenTextFieldSize.Medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { if (englishFocusOrder) usernameFocus.requestFocus() else focusManager.moveFocus(FocusDirection.Next) }),
                            inputModifier = Modifier.focusRequester(portFocus).testTag("account_port"),
                            modifier = Modifier.width(RasvenAccountInformationContract.portWidthDp.dp)
                                .testTag("account_port_field"),
                        )
                    }
                    RasvenTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.account_password),
                        helper = if (errors.password != null) {
                            stringResource(R.string.account_password_required)
                        } else {
                            stringResource(R.string.account_password_helper)
                        },
                        required = true,
                        requiredAccessibilityText = requiredSemantics,
                        isError = errors.password != null,
                        fontFamily = pageFont,
                        valueFontFamily = englishFontFamily,
                        layoutDirection = setupLayoutDirection(),
                        valueTextDirection = TextDirection.Ltr,
                        valueTextAlign = RasvenAccountInformationContract.passwordValueAlignment(LocalLayoutDirection.current),
                        appearance = appearance,
                        size = RasvenTextFieldSize.Medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        visualTransformation = PasswordVisualTransformation(),
                        inputModifier = Modifier.focusRequester(passwordFocus).testTag("account_password"),
                        modifier = Modifier.fillMaxWidth().testTag("account_password_field"),
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
                        label = stringResource(R.string.account_continue),
                        size = RasvenButtonSize.Large,
                        fontFamily = pageFont,
                        onClick = {
                            attempted = true
                            RasvenAccountValidation.validatedInput(server, port, username, password)?.let(onContinue)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("account_continue"),
                        expandToFullWidth = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountHeader(
    pageMargin: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
) {
    val heading = RasvenTypographyTokens.headingL
    val subtitle = RasvenTypographyTokens.bodyS
    val title = stringResource(R.string.account_heading)
    val latinPart = "IAX2"
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
                text = buildAnnotatedString {
                    val latinStart = title.indexOf(latinPart)
                    if (latinStart < 0) append(title) else {
                        append(title.substring(0, latinStart))
                        withStyle(SpanStyle(fontFamily = englishFontFamily)) { append(latinPart) }
                        append(title.substring(latinStart + latinPart.length))
                    }
                },
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
                text = stringResource(R.string.account_subheading),
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
