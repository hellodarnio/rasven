package com.rasven.android.product.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.*
import com.rasven.android.product.setup.RasvenRegistrationState
import com.rasven.android.designsystem.components.RasvenBadgeLanguage
import com.rasven.android.designsystem.components.RasvenBottomNavigation
import com.rasven.android.designsystem.components.RasvenButton
import com.rasven.android.designsystem.components.RasvenButtonSize
import com.rasven.android.designsystem.components.RasvenDangerButton
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.designsystem.components.RasvenOutlineButton
import com.rasven.android.designsystem.components.RasvenTextField
import com.rasven.android.designsystem.components.RasvenTextFieldAppearance
import com.rasven.android.designsystem.components.RasvenTextFieldSize
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens
import com.rasven.android.product.setup.RasvenAdvancedSettingsErrors
import com.rasven.android.product.call.RasvenPbxTransferProfile
import com.rasven.android.product.call.RasvenPbxTransferProfileErrors
import com.rasven.android.product.call.RasvenAttendedCompletionMode

/**
 * Stage5B5 source ledger:
 * Product 145:2557; Android handoff 198:4151; DS 550:2142 / 680:5205 / 550:6046 / 550:4601.
 * Final Product: 901:9606 / 901:9697 / 901:9795 / 901:9880.
 * One Settings Bottom Navigation destination owns three internal sub-pages.
 */
@Composable
internal fun RasvenSettingsScreen(
    state: RasvenSettingsUiState,
    currentContactPrefix: String,
    notificationsEnabled: Boolean,
    advancedAvailable: Boolean,
    connectionState: RasvenRegistrationState = RasvenRegistrationState.Unregistered,
    advancedErrors: RasvenAdvancedSettingsErrors,
    transferProfileErrors: RasvenPbxTransferProfileErrors = RasvenPbxTransferProfileErrors(),
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onOpenContactPrefix: () -> Unit,
    onContactPrefixChanged: (String) -> Unit,
    onSaveContactPrefix: () -> Unit,
    onOpenCallNotifications: () -> Unit,
    onOpenSystemCallSettings: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onDisconnect: () -> Unit = {},
    onAdvancedServerChanged: (String) -> Unit,
    onAdvancedPortChanged: (String) -> Unit,
    onAdvancedUsernameChanged: (String) -> Unit,
    onAdvancedPasswordChanged: (String) -> Unit,
    onTransferProfileChanged: (RasvenPbxTransferProfile) -> Unit = {},
    onRestoreTransferDefaults: () -> Unit = {},
    onSaveAdvanced: () -> Unit,
    onBackToMain: () -> Unit,
    onNavigationDestination: (RasvenNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    onSelectLanguage: ((RasvenLanguage) -> Boolean)? = null,
) {
    val pageFont = productPageFont(persianFontFamily)
    val dataFont = productDataFont(englishFontFamily)
    var languageChooserOpen by remember { mutableStateOf(false) }
    var disconnectConfirmationOpen by remember { mutableStateOf(false) }
    LaunchedEffect(connectionState, state.destination) {
        if (connectionState !is RasvenRegistrationState.Registered || state.destination != RasvenSettingsDestination.Main) {
            disconnectConfirmationOpen = false
        }
    }
    if (languageChooserOpen && onSelectLanguage != null) {
        Dialog(onDismissRequest = { languageChooserOpen = false }) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 364.dp)
                    .rasvenGradientFill(RasvenGradients.pageCanvas, 16.dp)
                    .padding(16.dp).testTag("settings_language_dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicText(stringResource(R.string.settings_language),
                    style = TextStyle(fontFamily = pageFont, fontSize = 16.sp, lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = productTextAlignment(),
                        color = RasvenDesignTheme.colors[RasvenSemanticColors.textPrimary]),
                    modifier = Modifier.fillMaxWidth().semantics { heading() })
                for (choice in listOf(RasvenLanguage.English, RasvenLanguage.Persian)) {
                    SettingsRow(
                        title = stringResource(if (choice == RasvenLanguage.English) R.string.settings_language_english
                            else R.string.settings_language_persian),
                        value = if (choice == LocalRasvenProductLanguage.current)
                            stringResource(R.string.settings_language_current) else null,
                        persianFontFamily = pageFont,
                        testTag = if (choice == RasvenLanguage.English) "settings_language_choice_en" else "settings_language_choice_fa",
                        rowHeight = 56.dp,
                        onClick = { if (onSelectLanguage(choice)) languageChooserOpen = false },
                    )
                }
            }
        }
    }
    BackHandler(enabled = disconnectConfirmationOpen || state.destination != RasvenSettingsDestination.Main) {
        if (disconnectConfirmationOpen) disconnectConfirmationOpen = false else onBackToMain()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp)
            .testTag("settings_screen"),
    ) {
        val responsive = settingsResponsiveGeometry(maxWidth.value.toInt())
        val pageMargin = responsive.pageMarginDp.dp
        val contentWidth = responsive.contentWidthDp.dp

        RasvenDailyAmbient()

        CompositionLocalProvider(LocalLayoutDirection provides productUiDirection()) {
            RasvenDailyBottomLayout(
                modifier = Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                when (state.destination) {
                    RasvenSettingsDestination.Main -> SettingsMainPage(
                        currentContactPrefix = currentContactPrefix,
                        notificationsEnabled = notificationsEnabled,
                        advancedAvailable = advancedAvailable,
                        connectionState = connectionState,
                        contentWidth = contentWidth,
                        pageMargin = pageMargin,
                        persianFontFamily = pageFont,
                        onOpenContactPrefix = onOpenContactPrefix,
                        onOpenCallNotifications = onOpenCallNotifications,
                        onOpenAdvanced = onOpenAdvanced,
                        onDisconnect = { disconnectConfirmationOpen = true },
                        onOpenLanguage = if (onSelectLanguage != null) ({ languageChooserOpen = true }) else null,
                    )
                    RasvenSettingsDestination.ContactPrefix -> SettingsContactPrefixPage(
                        value = state.contactPrefixDraft,
                        contentWidth = contentWidth,
                        pageMargin = pageMargin,
                        persianFontFamily = pageFont,
                        englishFontFamily = dataFont,
                        onValueChanged = onContactPrefixChanged,
                        onSave = onSaveContactPrefix,
                    )
                    RasvenSettingsDestination.CallNotifications -> SettingsCallNotificationsPage(
                        contentWidth = contentWidth,
                        pageMargin = pageMargin,
                        persianFontFamily = pageFont,
                        onOpenSystemSettings = onOpenSystemCallSettings,
                    )
                    RasvenSettingsDestination.Advanced -> SettingsAdvancedPage(
                        draft = state.advancedDraft,
                        errors = advancedErrors,
                        contentWidth = contentWidth,
                        pageMargin = pageMargin,
                        persianFontFamily = pageFont,
                        englishFontFamily = dataFont,
                        onServerChanged = onAdvancedServerChanged,
                        onPortChanged = onAdvancedPortChanged,
                        onUsernameChanged = onAdvancedUsernameChanged,
                        onPasswordChanged = onAdvancedPasswordChanged,
                        transferProfile = state.advancedDraft.transferProfile,
                        transferErrors = transferProfileErrors,
                        onTransferProfileChanged = onTransferProfileChanged,
                        onRestoreTransferDefaults = onRestoreTransferDefaults,
                        onSave = onSaveAdvanced,
                    )
                }

                RasvenBottomNavigation(
                    language = productBadgeLanguage(),
                    selectedDestination = RasvenNavDestination.Settings,
                    fontFamily = pageFont,
                    onDestinationClick = onNavigationDestination,
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .padding(bottom = dailyBottomLift())
                        .testTag("settings_bottom_navigation"),
                    horizontalInset = 32.dp,
                    highlightSelected = true,
                )
            }
        }
        if (disconnectConfirmationOpen && state.destination == RasvenSettingsDestination.Main) {
            SettingsDisconnectConfirmation(
                fontFamily = pageFont,
                onCancel = { disconnectConfirmationOpen = false },
                onConfirm = {
                    disconnectConfirmationOpen = false
                    if (connectionState is RasvenRegistrationState.Registered) onDisconnect()
                },
            )
        }
    }
}

@Composable
private fun SettingsMainPage(
    currentContactPrefix: String,
    notificationsEnabled: Boolean,
    advancedAvailable: Boolean,
    connectionState: RasvenRegistrationState,
    contentWidth: androidx.compose.ui.unit.Dp,
    pageMargin: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
    onOpenContactPrefix: () -> Unit,
    onOpenCallNotifications: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenLanguage: (() -> Unit)? = null,
) {
    SettingsHeader(
        title = stringResource(R.string.settings_heading),
        subtitle = stringResource(R.string.settings_subtitle),
        persianFontFamily = persianFontFamily,
        pageMargin = pageMargin,
        titleTop = 8.dp,
        subtitleTop = 44.dp,
    )

    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 118.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
    Column(
        modifier = Modifier.width(contentWidth).heightIn(max = dailyRemainingContentHeight(118.dp))
            .verticalScroll(rememberScrollState()).testTag("settings_main_rows"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsRow(
            title = stringResource(R.string.settings_contact_prefix),
            value = currentContactPrefix,
            persianFontFamily = persianFontFamily,
            leadingVisual = {
                SettingsRowIcon(R.drawable.rasven_settings_icon_call, "settings_icon_contact_prefix")
            },
            onClick = onOpenContactPrefix,
            testTag = "settings_row_contact_prefix",
        )
        SettingsRow(
            title = stringResource(R.string.settings_advanced),
            persianFontFamily = persianFontFamily,
            leadingVisual = {
                SettingsRowIcon(R.drawable.rasven_settings_icon_advanced, "settings_icon_advanced")
            },
            onClick = onOpenAdvanced.takeIf { advancedAvailable },
            testTag = "settings_row_advanced",
        )
        SettingsRow(
            title = stringResource(R.string.settings_call_notifications),
            value = stringResource(
                if (notificationsEnabled) R.string.settings_notification_enabled
                else R.string.settings_notification_disabled,
            ),
            persianFontFamily = persianFontFamily,
            leadingVisual = {
                SettingsRowIcon(R.drawable.rasven_settings_icon_notification, "settings_icon_call_notifications")
            },
            onClick = onOpenCallNotifications,
            testTag = "settings_row_call_notifications",
        )
        SettingsRow(
            title = stringResource(R.string.settings_language),
            value = stringResource(if (productIsEnglish()) R.string.settings_language_english else R.string.settings_language_persian),
            persianFontFamily = persianFontFamily,
            leadingVisual = {
                SettingsRowIcon(R.drawable.rasven_settings_icon_globe, "settings_icon_language")
            },
            // Null keeps the picker hidden only when the host has not enabled a complete locale.
            onClick = onOpenLanguage,
            testTag = "settings_row_language",
        )
        Box(Modifier.padding(top = 12.dp)) { SettingsRow(
            title = stringResource(R.string.settings_disconnect),
            secondary = stringResource(R.string.settings_disconnect_secondary),
            titleColor = RasvenDesignTheme.colors[RasvenSemanticColors.statusErrorText],
            leadingVisual = {
                SettingsRowIcon(
                    R.drawable.rasven_settings_icon_logout,
                    "settings_icon_disconnect",
                    RasvenDesignTheme.colors[RasvenSemanticColors.statusErrorText],
                )
            },
            persianFontFamily = persianFontFamily,
            onClick = onDisconnect.takeIf { connectionState is RasvenRegistrationState.Registered },
            testTag = "settings_row_disconnect",
        ) }
    }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    persianFontFamily: FontFamily,
    testTag: String,
    secondary: String? = null,
    value: String? = null,
    rowHeight: androidx.compose.ui.unit.Dp = 84.dp,
    leadingVisual: (@Composable () -> Unit)? = null,
    titleColor: androidx.compose.ui.graphics.Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier.fillMaxWidth().heightIn(min = rowHeight)
        .clip(shape)
        .background(colors[RasvenSemanticColors.surfaceGlass])
        .border(1.dp, colors[RasvenSemanticColors.borderGlass], shape)
        .then(
            if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
            else Modifier
        )
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .testTag(testTag)

    val semanticLabel = listOfNotNull(title, secondary, value).joinToString(productSemanticSeparator())
    CompositionLocalProvider(LocalLayoutDirection provides productValueRowDirection()) {
        Row(
            modifier = base.semantics(mergeDescendants = true) {
                contentDescription = semanticLabel
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!value.isNullOrEmpty()) {
                BasicText(
                    text = value,
                    modifier = Modifier.width(72.dp),
                    style = TextStyle(
                        color = colors[RasvenSemanticColors.textBrand],
                        fontFamily = persianFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Left,
                        textDirection = TextDirection.ContentOrLtr,
                    ),
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides productUiDirection()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (leadingVisual != null) {
                            leadingVisual()
                            Spacer(Modifier.width(12.dp))
                        }
                        BasicText(
                            text = title,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                color = titleColor ?: colors[RasvenSemanticColors.textPrimary],
                                fontFamily = persianFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                lineHeight = 24.sp,
                                textAlign = productTextAlignment(),
                                textDirection = productTextDirection(),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (secondary != null) {
                        BasicText(
                            text = secondary,
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                color = colors[RasvenSemanticColors.textSecondary],
                                fontFamily = persianFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                textAlign = productTextAlignment(),
                                textDirection = productTextDirection(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRowIcon(drawable: Int, testTag: String, tint: Color = RasvenDesignTheme.colors[RasvenSemanticColors.iconPrimary]) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(20.dp).testTag(testTag),
    )
}

@Composable
private fun SettingsDisconnectConfirmation(
    fontFamily: FontFamily,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = RasvenDesignTheme.colors
    val cancel = stringResource(R.string.settings_disconnect_cancel_action)
    CompositionLocalProvider(LocalLayoutDirection provides productUiDirection()) {
        Box(Modifier.fillMaxSize().testTag("settings_disconnect_confirmation")) {
            Box(
                Modifier.fillMaxSize().background(Color(0x6105141F))
                    .clickable(role = Role.Button, onClick = onCancel)
                    .semantics { contentDescription = cancel }
                    .testTag("settings_disconnect_scrim"),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .heightIn(min = 352.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(colors[RasvenSemanticColors.surfaceRaised])
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)
                    .navigationBarsPadding()
                    .testTag("settings_disconnect_sheet"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                            .background(colors[RasvenSemanticColors.statusErrorBackground])
                            .testTag("settings_disconnect_sheet_icon_container"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.rasven_settings_icon_logout_sheet),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors[RasvenSemanticColors.statusErrorText]),
                            modifier = Modifier.size(24.dp).testTag("settings_disconnect_sheet_icon"),
                        )
                    }
                }
                // Visual-only offset preserves the authored 352dp sheet and its bottom anchoring.
                Column(Modifier.fillMaxWidth().offset(y = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(
                        text = stringResource(R.string.settings_disconnect_confirm_title),
                        modifier = Modifier.fillMaxWidth().semantics { heading() }
                            .testTag("settings_disconnect_confirm_title"),
                        style = TextStyle(
                            color = colors[RasvenSemanticColors.textPrimary],
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            lineHeight = 32.sp,
                            textAlign = productTextAlignment(),
                            textDirection = productTextDirection(),
                        ),
                    )
                    BasicText(
                        text = stringResource(R.string.settings_disconnect_confirm_body),
                        modifier = Modifier.fillMaxWidth().testTag("settings_disconnect_confirm_body"),
                        style = TextStyle(
                            color = colors[RasvenSemanticColors.textSecondary],
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            lineHeight = 24.sp,
                            textAlign = productTextAlignment(),
                            textDirection = productTextDirection(),
                        ),
                    )
                }
                Column(Modifier.fillMaxWidth().offset(y = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RasvenDangerButton(
                        label = stringResource(R.string.settings_disconnect_confirm_action),
                        size = RasvenButtonSize.Large,
                        fontFamily = fontFamily,
                        onClick = onConfirm,
                        expandToFullWidth = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_disconnect_confirm_action"),
                    )
                    RasvenOutlineButton(
                        label = cancel,
                        size = RasvenButtonSize.Large,
                        fontFamily = fontFamily,
                        onClick = onCancel,
                        expandToFullWidth = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_disconnect_cancel_action"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsContactPrefixPage(
    value: String,
    contentWidth: androidx.compose.ui.unit.Dp,
    pageMargin: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onValueChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    SettingsHeader(
        title = stringResource(R.string.settings_prefix_heading),
        subtitle = stringResource(R.string.settings_prefix_subtitle),
        persianFontFamily = persianFontFamily,
        pageMargin = pageMargin,
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 136.dp)
            .padding(bottom = 160.dp + dailyBottomLift())
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RasvenTextField(
            value = value,
            onValueChange = onValueChanged,
            label = stringResource(R.string.settings_prefix_field),
            helper = stringResource(R.string.settings_prefix_helper),
            appearance = RasvenTextFieldAppearance.Glass,
            size = RasvenTextFieldSize.Medium,
            fontFamily = persianFontFamily,
            valueFontFamily = englishFontFamily,
            layoutDirection = productUiDirection(),
        valueTextDirection = TextDirection.Ltr,
            valueTextAlign = productTextAlignment(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            inputModifier = Modifier.testTag("settings_prefix_input"),
            modifier = Modifier.width(contentWidth).testTag("settings_prefix_field"),
        )
    }
    SettingsBottomAction(
        label = stringResource(R.string.settings_save_changes),
        contentWidth = contentWidth,
        persianFontFamily = persianFontFamily,
        onClick = onSave,
        testTag = "settings_prefix_save",
    )
}

@Composable
private fun SettingsCallNotificationsPage(
    contentWidth: androidx.compose.ui.unit.Dp,
    pageMargin: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
    onOpenSystemSettings: () -> Unit,
) {
    SettingsHeader(
        title = stringResource(R.string.settings_notifications_heading),
        subtitle = stringResource(R.string.settings_notifications_subtitle),
        persianFontFamily = persianFontFamily,
        pageMargin = pageMargin,
    )
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 130.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier.width(contentWidth).height(156.dp)
                .clip(shape)
                .background(colors[RasvenSemanticColors.surfaceGlass])
                .border(1.dp, colors[RasvenSemanticColors.borderGlass], shape)
                .padding(17.dp)
                .testTag("settings_notifications_guide"),
            contentAlignment = Alignment.TopEnd,
        ) {
            BasicText(
                text = stringResource(R.string.settings_notifications_guide),
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = productTextAlignment(),
                    textDirection = productTextDirection(),
                ),
            )
        }
    }
    SettingsBottomAction(
        label = stringResource(R.string.settings_notifications_cta),
        contentWidth = contentWidth,
        persianFontFamily = persianFontFamily,
        onClick = onOpenSystemSettings,
        testTag = "settings_notifications_cta",
    )
}

@Composable
private fun SettingsAdvancedPage(
    draft: RasvenAdvancedSettingsDraft,
    errors: RasvenAdvancedSettingsErrors,
    contentWidth: androidx.compose.ui.unit.Dp,
    pageMargin: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onServerChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    transferProfile: RasvenPbxTransferProfile,
    transferErrors: RasvenPbxTransferProfileErrors,
    onTransferProfileChanged: (RasvenPbxTransferProfile) -> Unit,
    onRestoreTransferDefaults: () -> Unit,
    onSave: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val requiredText = stringResource(R.string.account_required_accessibility)
    SettingsHeader(
        title = stringResource(R.string.advanced_settings_heading),
        subtitle = stringResource(R.string.advanced_settings_subheading),
        persianFontFamily = persianFontFamily,
        pageMargin = pageMargin,
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp)
            // Reserve the fixed Save/navigation area OUTSIDE the scroll viewport.
            // An inner footer alone leaves visible buttons underneath those overlays.
            .padding(bottom = 170.dp + dailyBottomLift())
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = stringResource(R.string.advanced_settings_security_note),
            modifier = Modifier.width(contentWidth).then(if (productIsEnglish()) Modifier.heightIn(min = 36.dp) else Modifier.height(36.dp)).testTag("settings_advanced_security_note"),
            style = TextStyle(
                color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = productTextAlignment(),
                textDirection = productTextDirection(),
            ),
        )
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier.width(contentWidth).testTag("settings_advanced_form"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RasvenTextField(
                value = draft.server,
                onValueChange = onServerChanged,
                label = stringResource(R.string.advanced_settings_server),
                helper = if (errors.server != null) stringResource(R.string.account_server_required) else null,
                required = true,
                requiredAccessibilityText = requiredText,
                isError = errors.server != null,
                appearance = RasvenTextFieldAppearance.Glass,
                size = RasvenTextFieldSize.Medium,
                fontFamily = persianFontFamily,
                valueFontFamily = englishFontFamily,
                layoutDirection = productUiDirection(),
                valueTextDirection = TextDirection.Ltr,
                valueTextAlign = productTextAlignment(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                inputModifier = Modifier.testTag("settings_advanced_server"),
                modifier = Modifier.fillMaxWidth().testTag("settings_advanced_server_field"),
            )
            RasvenTextField(
                value = draft.port,
                onValueChange = onPortChanged,
                label = stringResource(R.string.advanced_settings_port),
                helper = if (errors.port != null) stringResource(R.string.account_port_range) else null,
                required = true,
                requiredAccessibilityText = requiredText,
                isError = errors.port != null,
                appearance = RasvenTextFieldAppearance.Glass,
                size = RasvenTextFieldSize.Medium,
                fontFamily = persianFontFamily,
                valueFontFamily = englishFontFamily,
                layoutDirection = productUiDirection(),
                valueTextDirection = TextDirection.Ltr,
                valueTextAlign = productTextAlignment(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                inputModifier = Modifier.testTag("settings_advanced_port"),
                modifier = Modifier.fillMaxWidth().testTag("settings_advanced_port_field"),
            )
            RasvenTextField(
                value = draft.username,
                onValueChange = onUsernameChanged,
                label = stringResource(R.string.advanced_settings_username),
                helper = if (errors.username != null) stringResource(R.string.account_username_required) else null,
                required = true,
                requiredAccessibilityText = requiredText,
                isError = errors.username != null,
                appearance = RasvenTextFieldAppearance.Glass,
                size = RasvenTextFieldSize.Medium,
                fontFamily = persianFontFamily,
                valueFontFamily = englishFontFamily,
                layoutDirection = productUiDirection(),
                valueTextDirection = TextDirection.Ltr,
                valueTextAlign = productTextAlignment(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                inputModifier = Modifier.testTag("settings_advanced_username"),
                modifier = Modifier.fillMaxWidth().testTag("settings_advanced_username_field"),
            )
            RasvenTextField(
                value = draft.passwordReplacement,
                onValueChange = onPasswordChanged,
                label = stringResource(R.string.advanced_settings_password),
                helper = stringResource(R.string.advanced_settings_password_helper),
                // Figma 901:9880 keeps the visual marker; domain validation intentionally allows
                // an empty replacement so the current credential remains inside Registration owner.
                required = true,
                appearance = RasvenTextFieldAppearance.Glass,
                size = RasvenTextFieldSize.Medium,
                fontFamily = persianFontFamily,
                valueFontFamily = englishFontFamily,
                layoutDirection = productUiDirection(),
                valueTextDirection = TextDirection.Ltr,
                valueTextAlign = productTextAlignment(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                visualTransformation = PasswordVisualTransformation(),
                inputModifier = Modifier.testTag("settings_advanced_password").semantics { password() },
                modifier = Modifier.fillMaxWidth().testTag("settings_advanced_password_field"),
            )
            BasicText(
                text = stringResource(R.string.settings_transfer_heading),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).semantics { heading() }
                    .testTag("settings_transfer_heading"),
                style = TextStyle(color = RasvenDesignTheme.colors[RasvenSemanticColors.textPrimary],
                    fontFamily = persianFontFamily, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp, lineHeight = 24.sp, textAlign = productTextAlignment()),
            )
            BasicText(
                text = stringResource(when (transferProfile.identity) {
                    com.rasven.android.product.call.RasvenPbxProfileIdentity.IssabelAsterisk -> R.string.settings_transfer_profile_issabel
                    com.rasven.android.product.call.RasvenPbxProfileIdentity.Yeastar -> R.string.settings_transfer_profile_yeastar
                    com.rasven.android.product.call.RasvenPbxProfileIdentity.Grandstream -> R.string.settings_transfer_profile_grandstream
                    com.rasven.android.product.call.RasvenPbxProfileIdentity.Custom -> R.string.settings_transfer_profile_custom
                }),
                modifier = Modifier.fillMaxWidth().testTag("settings_transfer_profile_identity"),
                style = TextStyle(color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily, fontSize = 12.sp, lineHeight = 18.sp,
                    textAlign = productTextAlignment()),
            )
            BasicText(
                text = stringResource(R.string.settings_transfer_completion_hangup),
                modifier = Modifier.fillMaxWidth().testTag("settings_transfer_completion_mode"),
                style = TextStyle(color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily, fontSize = 13.sp, lineHeight = 20.sp,
                    textAlign = productTextAlignment()),
            )
            BasicText(
                text = stringResource(R.string.settings_transfer_choose_profile),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                style = TextStyle(color = RasvenDesignTheme.colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily, fontSize = 12.sp, lineHeight = 18.sp,
                    textAlign = productTextAlignment()),
            )
            RasvenButton(
                label = stringResource(R.string.settings_transfer_profile_yeastar_preset),
                size = RasvenButtonSize.Small,
                fontFamily = persianFontFamily,
                onClick = { onTransferProfileChanged(transferProfile.withPreset(RasvenPbxTransferProfile.yeastarS50Defaults)) },
                modifier = Modifier.fillMaxWidth().testTag("settings_transfer_preset_yeastar"),
            )
            RasvenButton(
                label = stringResource(R.string.settings_transfer_profile_issabel_preset),
                size = RasvenButtonSize.Small,
                fontFamily = persianFontFamily,
                onClick = { onTransferProfileChanged(transferProfile.withPreset(RasvenPbxTransferProfile.issabelAsteriskDefaults)) },
                modifier = Modifier.fillMaxWidth().testTag("settings_transfer_preset_issabel"),
            )
            TransferProfileTextField(
                value = transferProfile.blindCode,
                label = stringResource(R.string.settings_transfer_blind_code),
                error = transferErrors.blindCode,
                tag = "settings_transfer_blind_code",
                persianFontFamily = persianFontFamily,
                englishFontFamily = englishFontFamily,
                onValueChanged = { onTransferProfileChanged(transferProfile.copy(blindCode = it).withCode()) },
            )
            TransferProfileTextField(
                value = transferProfile.attendedCode,
                label = stringResource(R.string.settings_transfer_attended_code),
                error = transferErrors.attendedCode,
                tag = "settings_transfer_attended_code",
                persianFontFamily = persianFontFamily,
                englishFontFamily = englishFontFamily,
                onValueChanged = { onTransferProfileChanged(transferProfile.copy(attendedCode = it).withCode()) },
            )
            TransferProfileTextField(
                value = transferProfile.cancelAttendedCode,
                label = stringResource(R.string.settings_transfer_cancel_code),
                error = transferErrors.cancelAttendedCode,
                tag = "settings_transfer_cancel_code",
                persianFontFamily = persianFontFamily,
                englishFontFamily = englishFontFamily,
                onValueChanged = { onTransferProfileChanged(transferProfile.copy(cancelAttendedCode = it).withCode()) },
            )
            if (transferProfile.completionMode == RasvenAttendedCompletionMode.FeatureCode) {
                TransferProfileTextField(
                    value = transferProfile.completeAttendedCode,
                    label = stringResource(R.string.settings_transfer_complete_code),
                    error = transferErrors.completeAttendedCode,
                    tag = "settings_transfer_complete_code",
                    persianFontFamily = persianFontFamily,
                    englishFontFamily = englishFontFamily,
                    onValueChanged = { onTransferProfileChanged(transferProfile.copy(completeAttendedCode = it).withCode()) },
                )
            }
            TransferProfileTextField(
                value = transferProfile.salesDestination,
                label = stringResource(R.string.settings_transfer_sales_destination),
                error = transferErrors.salesDestination,
                tag = "settings_transfer_sales_destination",
                persianFontFamily = persianFontFamily,
                englishFontFamily = englishFontFamily,
                onValueChanged = { onTransferProfileChanged(transferProfile.copy(salesDestination = it).withCode()) },
            )
            TransferProfileTextField(
                value = transferProfile.managementDestination,
                label = stringResource(R.string.settings_transfer_management_destination),
                error = transferErrors.managementDestination,
                tag = "settings_transfer_management_destination",
                persianFontFamily = persianFontFamily,
                englishFontFamily = englishFontFamily,
                onValueChanged = { onTransferProfileChanged(transferProfile.copy(managementDestination = it).withCode()) },
            )
            RasvenButton(
                label = stringResource(R.string.settings_transfer_restore_defaults),
                size = RasvenButtonSize.Small,
                fontFamily = persianFontFamily,
                onClick = onRestoreTransferDefaults,
                modifier = Modifier.fillMaxWidth().testTag("settings_transfer_restore_defaults"),
            )
        }
    }
    SettingsBottomAction(
        label = stringResource(R.string.settings_save_changes),
        contentWidth = contentWidth,
        persianFontFamily = persianFontFamily,
        onClick = onSave,
        testTag = "settings_advanced_save",
    )
}

@Composable
private fun TransferProfileTextField(
    value: String,
    label: String,
    error: String?,
    tag: String,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onValueChanged: (String) -> Unit,
) {
    RasvenTextField(
        value = value,
        onValueChange = onValueChanged,
        label = label,
        helper = if (error != null) stringResource(R.string.settings_transfer_invalid_value) else null,
        isError = error != null,
        appearance = RasvenTextFieldAppearance.Glass,
        size = RasvenTextFieldSize.Medium,
        fontFamily = persianFontFamily,
        valueFontFamily = englishFontFamily,
        layoutDirection = productUiDirection(),
        valueTextDirection = TextDirection.Ltr,
        valueTextAlign = productTextAlignment(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
        inputModifier = Modifier.testTag(tag),
        modifier = Modifier.fillMaxWidth().testTag("${tag}_field"),
    )
}

@Composable
private fun SettingsHeader(
    title: String,
    subtitle: String,
    persianFontFamily: FontFamily,
    pageMargin: androidx.compose.ui.unit.Dp,
    titleTop: androidx.compose.ui.unit.Dp = 16.dp,
    subtitleTop: androidx.compose.ui.unit.Dp = 54.dp,
) {
    val colors = RasvenDesignTheme.colors
    val heading = RasvenTypographyTokens.headingL
    val body = RasvenTypographyTokens.bodyS
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            BasicText(
                text = title,
                modifier = Modifier.padding(start = pageMargin, end = pageMargin, top = titleTop)
                    .fillMaxWidth()
                    .semantics { heading() }
                    .testTag("settings_page_heading"),
                style = TextStyle(
                    color = colors[RasvenSemanticColors.textPrimary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(heading.fontWeight),
                    fontSize = heading.fontSizeSp.sp,
                    lineHeight = heading.lineHeightSp.sp,
                    textAlign = productTextAlignment(),
                    textDirection = productTextDirection(),
                ),
            )
            BasicText(
                text = subtitle,
                modifier = Modifier.padding(start = pageMargin, end = pageMargin, top = subtitleTop)
                    .fillMaxWidth()
                    .testTag("settings_page_subtitle"),
                style = TextStyle(
                    color = colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(body.fontWeight),
                    fontSize = body.fontSizeSp.sp,
                    lineHeight = body.lineHeightSp.sp,
                    textAlign = productTextAlignment(),
                    textDirection = productTextDirection(),
                ),
            )
        }
    }
}

@Composable
private fun SettingsBottomAction(
    label: String,
    contentWidth: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
    onClick: () -> Unit,
    testTag: String,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 88.dp + dailyBottomLift()).testTag("settings_action_container"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        RasvenButton(
            label = label,
            size = RasvenButtonSize.Large,
            fontFamily = persianFontFamily,
            onClick = onClick,
            expandToFullWidth = true,
            modifier = Modifier.width(contentWidth).testTag(testTag),
        )
    }
}
