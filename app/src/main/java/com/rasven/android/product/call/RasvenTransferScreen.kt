package com.rasven.android.product.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.LocalRasvenProductLanguage
import com.rasven.android.designsystem.foundation.productPageFont
import com.rasven.android.designsystem.foundation.productDataFont
import com.rasven.android.designsystem.foundation.productIsEnglish
import com.rasven.android.designsystem.foundation.productTextAlignment
import com.rasven.android.designsystem.foundation.productTextDirection
import com.rasven.android.designsystem.foundation.productUiDirection
import com.rasven.android.designsystem.components.RasvenBadge
import com.rasven.android.designsystem.components.RasvenBadgeType
import com.rasven.android.designsystem.components.RasvenBadgeStyle
import com.rasven.android.designsystem.components.RasvenBadgeSize
import com.rasven.android.designsystem.components.RasvenBadgeLanguage
import com.rasven.android.designsystem.components.RasvenButton
import com.rasven.android.designsystem.components.RasvenOutlineButton
import com.rasven.android.designsystem.components.RasvenButtonSize
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.components.RasvenTextField
import com.rasven.android.designsystem.components.RasvenTextFieldAppearance
import com.rasven.android.designsystem.components.RasvenTextFieldSize
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.foundation.RasvenSemanticColors

@Composable
internal fun RasvenTransferScreen(
    state: RasvenTransferUiState,
    fontFamily: FontFamily,
    englishFontFamily: FontFamily,
    configuredDestinations: List<RasvenTransferDestination>,
    onChooseMode: (RasvenTransferMode) -> Unit,
    onEnterManual: () -> Unit,
    onManualChanged: (String) -> Unit,
    onSubmitManual: () -> Unit,
    onSelectDestination: (String) -> Unit,
    onContinueAttendedDestination: () -> Unit = {},
    onConfirmConsultation: () -> Unit,
    onRequestComplete: () -> Unit,
    onConfirmComplete: () -> Unit,
    onCancelAttended: () -> Unit,
    canCancelAttended: Boolean = true,
    onBackFromCompletion: () -> Unit = {},
    onReportBusy: () -> Unit,
    onReportNoAnswer: () -> Unit,
    onReportBlindStillActive: () -> Unit,
    onReturnToCall: () -> Unit,
    onDismiss: () -> Unit,
    onBackToMode: () -> Unit = {},
) {
    val pageFont = productPageFont(fontFamily)
    val dataFont = productDataFont(englishFontFamily)
    val english = productIsEnglish()
    // English transfer screens use Figma's fixed lower action area. The scrolling
    // content is kept inside each screen so the primary controls do not drift upward.
    val pinEnglishActions = english && state != RasvenTransferUiState.Idle
    val language = LocalRasvenProductLanguage.current
    val noCancelHint = if (canCancelAttended) "" else "\n" + stringResource(R.string.transfer_cancel_unavailable_hint)
    val controlsLabel = if (canCancelAttended) null else stringResource(R.string.transfer_show_call_controls)
    val primaryStillActive = when (state) {
        is RasvenTransferUiState.BlindOutcomeUnknown, is RasvenTransferUiState.AttendedOutcomeUnknown,
        RasvenTransferUiState.Idle -> false
        is RasvenTransferUiState.BlindFailed -> state.canReturnToCall
        is RasvenTransferUiState.AttendedFailed -> state.reason != RasvenAttendedTransferFailure.CallEnded
        else -> true
    }
    val call = when (state) {
        is RasvenTransferUiState.ChoosingMode -> state.call
        is RasvenTransferUiState.SelectingDestination -> state.call
        is RasvenTransferUiState.EnteringDestination -> state.call
        is RasvenTransferUiState.BlindAwaitingPbx -> state.call
        is RasvenTransferUiState.BlindFailed -> state.call
        is RasvenTransferUiState.BlindOutcomeUnknown -> state.call
        is RasvenTransferUiState.AttendedAwaitingDestinationTone -> state.call
        is RasvenTransferUiState.AttendedAwaitingConsultation -> state.call
        is RasvenTransferUiState.AttendedReadyToComplete -> state.call
        is RasvenTransferUiState.ConfirmingCompletion -> state.call
        is RasvenTransferUiState.CompletionArmed -> state.call
        is RasvenTransferUiState.AttendedCancelAwaitingRecovery -> state.call
        is RasvenTransferUiState.AttendedCancelledReturned -> state.call
        is RasvenTransferUiState.AttendedOutcomeUnknown -> state.call
        is RasvenTransferUiState.AttendedFailed -> state.call
        RasvenTransferUiState.Idle -> null
    }
    BoxWithConstraints(Modifier.fillMaxSize().rasvenGradientFill(RasvenGradients.pageCanvas, 16.dp)
        .testTag("transfer_screen")) {
        val colors = RasvenDesignTheme.colors
        val margin = if (maxWidth < 360.dp) 16.dp else 24.dp
        if (english) Image(
            painterResource(R.drawable.rasven_setup_ambient_en), null,
            Modifier.align(Alignment.TopStart).size(210.dp, 200.dp),
        ) else Image(
            painterResource(R.drawable.rasven_home_ambient), null,
            Modifier.align(Alignment.TopEnd).size(155.dp, 142.dp),
        )
        Column(
            Modifier.fillMaxSize()
                .then(if (english) Modifier.statusBarsPadding().navigationBarsPadding().imePadding() else Modifier.statusBarsPadding().navigationBarsPadding().imePadding())
                .then(if (pinEnglishActions) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = margin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (english) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    RasvenBadge(
                        label = stringResource(if (primaryStillActive) R.string.active_call_connected else R.string.incoming_call_ended),
                        type = if (primaryStillActive) RasvenBadgeType.Success else RasvenBadgeType.Neutral,
                        style = RasvenBadgeStyle.Soft,
                        size = RasvenBadgeSize.Medium, language = RasvenBadgeLanguage.En, fontFamily = pageFont,
                        modifier = Modifier.testTag("transfer_status_badge"),
                    )
                }
                Spacer(Modifier.height(26.dp))
                call?.let {
                    BasicText(it.displayIdentity, Modifier.fillMaxWidth(), style = TextStyle(
                        color = colors[RasvenSemanticColors.textPrimary], fontFamily = pageFont,
                        fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ))
                    Spacer(Modifier.height(14.dp))
                    BasicText(stringResource(R.string.transfer_header), Modifier.fillMaxWidth(), style = TextStyle(
                        color = colors[RasvenSemanticColors.textSecondary], fontFamily = pageFont,
                        fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ))
                    Spacer(Modifier.height(8.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("transfer_duration_row")) {
                            BasicText(formatActiveCallDuration(it.connectedAtElapsedMillis, System.nanoTime() / 1_000_000L, language),
                                Modifier.widthIn(min = 40.dp).testTag("transfer_duration_value"),
                                style = TextStyle(fontFamily = dataFont, fontWeight = FontWeight.Medium,
                                    color = colors[RasvenSemanticColors.textPrimary], fontSize = 12.sp,
                                    lineHeight = 18.sp, textAlign = TextAlign.Left, textDirection = TextDirection.Ltr))
                            BasicText(stringResource(R.string.transfer_duration_label),
                                Modifier.widthIn(min = 76.dp).testTag("transfer_duration_label"),
                                style = TextStyle(fontFamily = pageFont, fontWeight = FontWeight.Normal,
                                    color = colors[RasvenSemanticColors.textSecondary], fontSize = 12.sp,
                                    lineHeight = 18.sp, textAlign = TextAlign.Left))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            } else {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RasvenBadge(
                        label = stringResource(if (primaryStillActive) R.string.active_call_connected else R.string.incoming_call_ended),
                        type = if (primaryStillActive) RasvenBadgeType.Success else RasvenBadgeType.Neutral,
                        style = RasvenBadgeStyle.Soft,
                        size = RasvenBadgeSize.Medium,
                        language = RasvenBadgeLanguage.Fa,
                        fontFamily = pageFont,
                        modifier = Modifier.testTag("transfer_status_badge"),
                    )
                }
                Spacer(Modifier.height(16.dp))
                call?.let {
                    BasicText(
                        it.displayIdentity,
                        Modifier.fillMaxWidth(),
                        style = TextStyle(
                            color = colors[RasvenSemanticColors.textPrimary],
                            fontFamily = pageFont,
                            fontSize = 20.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    val subtitle = when {
                        state is RasvenTransferUiState.SelectingDestination && state.mode == RasvenTransferMode.Attended -> stringResource(R.string.transfer_attended)
                        state is RasvenTransferUiState.EnteringDestination && state.mode == RasvenTransferMode.Attended -> stringResource(R.string.transfer_attended)
                        state is RasvenTransferUiState.SelectingDestination && state.mode == RasvenTransferMode.Blind -> stringResource(R.string.transfer_blind)
                        state is RasvenTransferUiState.EnteringDestination && state.mode == RasvenTransferMode.Blind -> stringResource(R.string.transfer_blind)
                        else -> stringResource(R.string.transfer_header)
                    }
                    BasicText(
                        subtitle,
                        Modifier.fillMaxWidth(),
                        style = TextStyle(
                            color = colors[RasvenSemanticColors.textSecondary],
                            fontFamily = pageFont,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BasicText(
                            stringResource(R.string.transfer_duration_label),
                            style = TextStyle(
                                fontFamily = pageFont,
                                fontWeight = FontWeight.Normal,
                                color = colors[RasvenSemanticColors.textSecondary],
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Right,
                            ),
                        )
                        BasicText(
                            formatActiveCallDuration(it.connectedAtElapsedMillis, System.nanoTime() / 1_000_000L, language),
                            style = TextStyle(
                                fontFamily = dataFont,
                                fontWeight = FontWeight.Normal,
                                color = colors[RasvenSemanticColors.textSecondary],
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Left,
                                textDirection = TextDirection.Ltr,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            when (state) {
                is RasvenTransferUiState.ChoosingMode -> {
                    if (english) {
                        Column(Modifier.fillMaxWidth().widthIn(max = 364.dp).padding(horizontal = 4.dp).testTag("transfer_choose_explanation"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TransferTitle(stringResource(R.string.transfer_choose_type), pageFont)
                            TransferText(stringResource(R.string.transfer_choose_explanation), pageFont, 12, 18,
                                FontWeight.Normal, colors[RasvenSemanticColors.textSecondary], Modifier.fillMaxWidth())
                        }
                    } else TransferTitle(stringResource(R.string.transfer_choose_type), pageFont)
                    Spacer(Modifier.height(if (english) 16.dp else 18.dp))
                    TransferAction(stringResource(R.string.transfer_blind), stringResource(R.string.transfer_blind_description), pageFont, Modifier.fillMaxWidth().widthIn(max = 364.dp).then(if (english) Modifier.heightIn(min = 96.dp) else Modifier), "transfer_choose_blind") { onChooseMode(RasvenTransferMode.Blind) }
                    Spacer(Modifier.height(if (english) 16.dp else 12.dp))
                    TransferAction(stringResource(R.string.transfer_attended), stringResource(R.string.transfer_attended_description), pageFont, Modifier.fillMaxWidth().widthIn(max = 364.dp).then(if (english) Modifier.heightIn(min = 96.dp) else Modifier), "transfer_choose_attended") { onChooseMode(RasvenTransferMode.Attended) }
                    Spacer(Modifier.height(24.dp))
                    if (english) Spacer(Modifier.weight(1f))
                    TransferAction(stringResource(R.string.transfer_return_primary), null, pageFont, Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_return_call", secondary = true, onClick = onReturnToCall)
                }
                is RasvenTransferUiState.SelectingDestination -> {
                    val defaultPresets = remember {
                        listOf(
                            RasvenTransferDestination("پشتیبانی فنی", "305"),
                            RasvenTransferDestination("فروش", "210"),
                            RasvenTransferDestination("مدیریت", "101"),
                        )
                    }
                    val effectiveDestinations = if (configuredDestinations.isNotEmpty()) configuredDestinations else defaultPresets
                    val destinationInputField = @Composable {
                        CompositionLocalProvider(LocalLayoutDirection provides productUiDirection()) {
                            RasvenTextField(
                                value = state.value,
                                onValueChange = onManualChanged,
                                label = if (english) stringResource(R.string.transfer_destination) else stringResource(R.string.transfer_field_destination_label),
                                required = !english,
                                leadingIcon = if (!english) {
                                    {
                                        Image(
                                            painter = painterResource(R.drawable.rasven_backspace_outline),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(colors[RasvenSemanticColors.iconPrimary]),
                                            modifier = Modifier.size(20.dp).clickable { onManualChanged("") },
                                        )
                                    }
                                } else null,
                                helper = if (state.invalid) stringResource(R.string.transfer_invalid_destination) else null,
                                isError = state.invalid,
                                appearance = RasvenTextFieldAppearance.Glass,
                                size = RasvenTextFieldSize.Medium,
                                layoutDirection = productUiDirection(),
                                fontFamily = pageFont,
                                valueFontFamily = dataFont,
                                valueTextDirection = TextDirection.Ltr,
                                valueTextAlign = productTextAlignment(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                                inputModifier = Modifier.testTag("transfer_manual_input"),
                                modifier = Modifier.fillMaxWidth().widthIn(max = 364.dp),
                            )
                        }
                    }
                    if (english) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                            TransferTitle(stringResource(R.string.transfer_choose_destination), pageFont)
                            Spacer(Modifier.height(18.dp))
                            effectiveDestinations.forEachIndexed { index, destination ->
                                TransferAction(transferDestinationLabel(destination.label), stringResource(R.string.transfer_extension_format, destination.address), pageFont,
                                    Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_preset_$index") { onSelectDestination(destination.label) }
                                Spacer(Modifier.height(10.dp))
                            }
                            TransferText(stringResource(R.string.transfer_manual_description), pageFont, 13, 20,
                                FontWeight.Normal, colors[RasvenSemanticColors.textSecondary], Modifier.fillMaxWidth().widthIn(max = 364.dp)
                                    .padding(horizontal = 4.dp).testTag("transfer_manual_description"))
                            Spacer(Modifier.height(12.dp))
                            destinationInputField()
                            Spacer(Modifier.height(12.dp))
                        }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            TransferAction(stringResource(R.string.transfer_submit), null, pageFont,
                                Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_submit_manual", onClick = onSubmitManual)
                            Spacer(Modifier.height(10.dp))
                            TransferAction(stringResource(R.string.transfer_back), null, pageFont,
                                Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_back_mode", secondary = true, onClick = onBackToMode)
                        }
                    } else {
                        val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
                        if (backDispatcher != null) {
                            BackHandler(onBack = onBackToMode)
                        }
                        Column(
                            Modifier.fillMaxWidth().widthIn(max = 364.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            destinationInputField()
                            Spacer(Modifier.height(16.dp))
                            effectiveDestinations.forEachIndexed { index, destination ->
                                val isSelected = state.value == destination.address
                                TransferPresetCard(
                                    title = transferDestinationLabel(destination.label),
                                    extension = destination.address,
                                    font = pageFont,
                                    selected = isSelected,
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 364.dp),
                                    tag = "transfer_preset_$index",
                                    onClick = {
                                        onManualChanged(destination.address)
                                        onSelectDestination(destination.label)
                                    },
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            if (state.mode == RasvenTransferMode.Attended) {
                                Spacer(Modifier.height(4.dp))
                                val noteShape = RoundedCornerShape(16.dp)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 364.dp)
                                        .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), noteShape)
                                        .clip(noteShape)
                                        .background(colors[RasvenSemanticColors.surfaceGlass])
                                        .border(1.dp, colors[RasvenSemanticColors.borderGlass], noteShape)
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .testTag("transfer_note_card"),
                                    horizontalAlignment = Alignment.End,
                                ) {
                                    BasicText(
                                        text = stringResource(R.string.transfer_call_destination),
                                        style = TextStyle(
                                            color = colors[RasvenSemanticColors.textPrimary],
                                            fontFamily = pageFont,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = productTextAlignment(),
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    BasicText(
                                        text = stringResource(R.string.transfer_attended_note_body),
                                        style = TextStyle(
                                            color = colors[RasvenSemanticColors.textSecondary],
                                            fontFamily = pageFont,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.Normal,
                                            textAlign = productTextAlignment(),
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            RasvenButton(
                                label = if (state.mode == RasvenTransferMode.Blind) stringResource(R.string.transfer_submit) else stringResource(R.string.transfer_call_destination),
                                fontFamily = pageFont,
                                modifier = Modifier.fillMaxWidth().widthIn(max = 364.dp).testTag("transfer_submit_manual"),
                                size = RasvenButtonSize.Large,
                                expandToFullWidth = true,
                                onClick = onSubmitManual,
                            )
                            Spacer(Modifier.height(10.dp))
                            RasvenOutlineButton(
                                label = stringResource(R.string.transfer_back),
                                fontFamily = pageFont,
                                modifier = Modifier.fillMaxWidth().widthIn(max = 364.dp).testTag("transfer_back_mode"),
                                size = RasvenButtonSize.Large,
                                expandToFullWidth = true,
                                onClick = onBackToMode,
                            )
                        }
                    }
                }
                is RasvenTransferUiState.EnteringDestination -> {
                    if (english) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TransferTitle(if (state.mode == RasvenTransferMode.Blind) stringResource(R.string.transfer_blind) else stringResource(R.string.transfer_call_destination), pageFont)
                            Spacer(Modifier.height(8.dp))
                            TransferText(stringResource(R.string.transfer_manual_instruction), pageFont, 13, 20, FontWeight.Normal,
                                colors[RasvenSemanticColors.textSecondary], Modifier.fillMaxWidth().widthIn(max = 364.dp))
                            Spacer(Modifier.height(16.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides productUiDirection()) {
                                RasvenTextField(
                                    value = state.value,
                                    onValueChange = onManualChanged,
                                    label = stringResource(R.string.transfer_destination),
                                    helper = if (state.invalid) stringResource(R.string.transfer_invalid_destination) else null,
                                    isError = state.invalid,
                                    appearance = RasvenTextFieldAppearance.Glass,
                                    size = RasvenTextFieldSize.Medium,
                                    layoutDirection = productUiDirection(),
                                    fontFamily = pageFont,
                                    valueFontFamily = dataFont,
                                    valueTextDirection = TextDirection.Ltr,
                                    valueTextAlign = productTextAlignment(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    inputModifier = Modifier.testTag("transfer_manual_input"),
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 364.dp),
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            // Keep the primary transfer CTA at the Figma bottom anchor.
                            TransferAction(stringResource(R.string.transfer_back), null, pageFont, Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_return_call", secondary = true, onClick = onReturnToCall)
                            Spacer(Modifier.height(12.dp))
                            TransferAction(if (state.mode == RasvenTransferMode.Blind) stringResource(R.string.transfer_submit) else stringResource(R.string.transfer_call_destination), null,
                                pageFont, Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_submit_manual", onClick = onSubmitManual)
                        }
                    } else {
                        TransferTitle(if (state.mode == RasvenTransferMode.Blind) stringResource(R.string.transfer_blind) else stringResource(R.string.transfer_call_destination), pageFont)
                        Spacer(Modifier.height(8.dp))
                        TransferText(stringResource(R.string.transfer_manual_instruction), pageFont, 13, 20, FontWeight.Normal,
                            colors[RasvenSemanticColors.textSecondary], Modifier.fillMaxWidth().widthIn(max = 364.dp))
                        Spacer(Modifier.height(16.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides productUiDirection()) {
                            RasvenTextField(
                                value = state.value,
                                onValueChange = onManualChanged,
                                label = stringResource(R.string.transfer_destination),
                                helper = if (state.invalid) stringResource(R.string.transfer_invalid_destination) else null,
                                isError = state.invalid,
                                appearance = RasvenTextFieldAppearance.Glass,
                                size = RasvenTextFieldSize.Medium,
                                layoutDirection = productUiDirection(),
                                fontFamily = pageFont,
                                valueFontFamily = dataFont,
                                valueTextDirection = TextDirection.Ltr,
                                valueTextAlign = productTextAlignment(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                                inputModifier = Modifier.testTag("transfer_manual_input"),
                                modifier = Modifier.fillMaxWidth().widthIn(max = 364.dp),
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        TransferAction(if (state.mode == RasvenTransferMode.Blind) stringResource(R.string.transfer_submit) else stringResource(R.string.transfer_call_destination), null,
                            pageFont, Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_submit_manual", onClick = onSubmitManual)
                        Spacer(Modifier.height(12.dp))
                        TransferAction(stringResource(R.string.transfer_back), null, pageFont, Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_return_call", secondary = true, onClick = onReturnToCall)
                    }
                }
                is RasvenTransferUiState.BlindAwaitingPbx -> StatusSurface(stringResource(R.string.transfer_in_progress), stringResource(R.string.transfer_waiting_pbx), state.destination, pageFont, "transfer_blind_waiting", onReturnToCall,
                    extraAction = stringResource(R.string.transfer_primary_still_active), onExtraAction = onReportBlindStillActive)
                is RasvenTransferUiState.BlindOutcomeUnknown -> ResultSurface(stringResource(R.string.transfer_unknown), stringResource(R.string.transfer_blind_unknown_body), state.destination, pageFont, false, onDismiss, resultTag = "transfer_result_unknown")
                is RasvenTransferUiState.BlindFailed -> ResultSurface(stringResource(R.string.transfer_failed), stringResource(R.string.transfer_blind_failed_body), state.destination, pageFont, false,
                    if (state.canReturnToCall) onReturnToCall else onDismiss)
                is RasvenTransferUiState.AttendedAwaitingDestinationTone -> StatusSurface(
                    title = stringResource(R.string.transfer_in_progress),
                    message = stringResource(R.string.transfer_waiting_transfer_tone) + noCancelHint,
                    destination = state.destination,
                    font = pageFont,
                    tag = "transfer_attended_dial_tone",
                    action = onReturnToCall,
                    extraAction = stringResource(R.string.transfer_call_destination),
                    onExtraAction = onContinueAttendedDestination,
                    onCancel = onCancelAttended.takeIf { canCancelAttended },
                    showReturnAction = !canCancelAttended,
                    returnActionLabel = controlsLabel,
                )
                is RasvenTransferUiState.AttendedAwaitingConsultation -> {
                    StatusSurface(stringResource(R.string.transfer_waiting_destination), stringResource(R.string.transfer_consultation_confirmation_hint) + noCancelHint, state.destination, pageFont, "transfer_attended_waiting", onReturnToCall,
                        extraAction = stringResource(R.string.transfer_consultation_connected), onExtraAction = onConfirmConsultation, onCancel = onCancelAttended.takeIf { canCancelAttended },
                        secondaryAction = stringResource(R.string.transfer_destination_busy), onSecondaryAction = onReportBusy,
                        tertiaryAction = stringResource(R.string.transfer_no_answer), onTertiaryAction = onReportNoAnswer,
                        returnActionLabel = controlsLabel)
                }
                is RasvenTransferUiState.AttendedReadyToComplete -> {
                    StatusSurface(stringResource(R.string.transfer_consultation_title), stringResource(R.string.transfer_ready_hint) + noCancelHint, state.destination, pageFont, "transfer_attended_ready", onReturnToCall,
                        extraAction = stringResource(R.string.transfer_complete), onExtraAction = onRequestComplete, onCancel = onCancelAttended.takeIf { canCancelAttended },
                        returnActionLabel = controlsLabel)
                }
                is RasvenTransferUiState.ConfirmingCompletion -> {
                    TransferTitle(stringResource(R.string.transfer_confirm_title), pageFont)
                    Spacer(Modifier.height(14.dp))
                    TransferText(stringResource(R.string.transfer_confirm_body), pageFont, 14, 22, FontWeight.Normal,
                        colors[RasvenSemanticColors.textSecondary], Modifier.fillMaxWidth().widthIn(max = 364.dp))
                    if (!canCancelAttended) TransferText(stringResource(R.string.transfer_cancel_unavailable_hint), pageFont, 13, 21, FontWeight.Normal,
                        colors[RasvenSemanticColors.textSecondary], Modifier.fillMaxWidth().widthIn(max = 364.dp))
                    Spacer(Modifier.height(24.dp))
                    if (english) Spacer(Modifier.weight(1f))
                    TransferAction(stringResource(R.string.transfer_confirm_action), null, pageFont, Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_confirm_complete", onClick = onConfirmComplete)
                    Spacer(Modifier.height(12.dp))
                    if (canCancelAttended) TransferAction(stringResource(R.string.transfer_cancel), null, pageFont, Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_cancel_attended", secondary = true, onClick = onCancelAttended)
                    else TransferAction(stringResource(R.string.transfer_back), null, pageFont, Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_back_to_consultation", secondary = true, onClick = onBackFromCompletion)
                }
                is RasvenTransferUiState.CompletionArmed -> StatusSurface(stringResource(R.string.transfer_completing), stringResource(R.string.transfer_waiting_end), state.destination, pageFont, "transfer_completion_pending", onReturnToCall)
                is RasvenTransferUiState.AttendedCancelAwaitingRecovery -> StatusSurface(
                    stringResource(R.string.transfer_returning), stringResource(R.string.transfer_recovery_hint),
                    state.destination, pageFont, "transfer_cancel_pending", onReturnToCall,
                    returnActionLabel = stringResource(R.string.transfer_return_call),
                )
                is RasvenTransferUiState.AttendedCancelledReturned -> ResultSurface(stringResource(R.string.transfer_primary_restored), stringResource(R.string.transfer_restored_body), state.destination, pageFont, true, onDismiss, stringResource(R.string.transfer_return_call))
                is RasvenTransferUiState.AttendedOutcomeUnknown -> ResultSurface(stringResource(R.string.transfer_unknown), stringResource(R.string.transfer_attended_unknown_body), state.destination, pageFont, false, onDismiss, resultTag = "transfer_result_unknown")
                is RasvenTransferUiState.AttendedFailed -> if (state.reason == RasvenAttendedTransferFailure.DestinationBusy ||
                    state.reason == RasvenAttendedTransferFailure.NoAnswer) {
                    StatusSurface(
                        title = stringResource(R.string.transfer_not_completed),
                        message = (if (state.reason == RasvenAttendedTransferFailure.DestinationBusy) stringResource(R.string.transfer_busy_reason) else stringResource(R.string.transfer_no_answer_reason)) + noCancelHint,
                        destination = state.destination ?: RasvenTransferDestination(stringResource(R.string.transfer_destination), "—"),
                        font = pageFont,
                        tag = "transfer_attended_failed",
                        action = onReturnToCall,
                        onCancel = onCancelAttended.takeIf { canCancelAttended },
                        showReturnAction = state.canReturnToCall || !canCancelAttended,
                        returnActionLabel = controlsLabel,
                    )
                } else ResultSurface(stringResource(R.string.transfer_not_completed), when (state.reason) {
                    RasvenAttendedTransferFailure.ManualCallHold -> stringResource(R.string.transfer_manual_hold_reason)
                    else -> stringResource(R.string.transfer_check_primary)
                } + if (!canCancelAttended && state.reason != RasvenAttendedTransferFailure.CallEnded) noCancelHint else "", state.destination, pageFont, false,
                    if (state.canReturnToCall || !canCancelAttended && state.reason != RasvenAttendedTransferFailure.CallEnded) onReturnToCall else onDismiss,
                    actionLabel = if (!canCancelAttended && state.reason != RasvenAttendedTransferFailure.CallEnded) controlsLabel else null)
                RasvenTransferUiState.Idle -> Unit
            }
            Spacer(Modifier.height(if (pinEnglishActions) 8.dp else 30.dp))
        }
    }
}

@Composable
private fun ColumnScope.StatusSurface(
    title: String,
    message: String,
    destination: RasvenTransferDestination,
    font: FontFamily,
    tag: String,
    action: () -> Unit,
    extraAction: String? = null,
    onExtraAction: () -> Unit = {},
    onCancel: (() -> Unit)? = null,
    secondaryAction: String? = null,
    onSecondaryAction: () -> Unit = {},
    tertiaryAction: String? = null,
    onTertiaryAction: () -> Unit = {},
    showReturnAction: Boolean = true,
    returnActionLabel: String? = null,
) {
    TransferTitle(title, font, tag)
    Spacer(Modifier.height(14.dp))
    TransferCard(Modifier.fillMaxWidth().widthIn(max = 364.dp), testTag = "${tag}_details") {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            TransferText(transferDestinationLabel(destination.label), font, 15, 24, FontWeight.SemiBold, Color(0xFF102B42), Modifier.fillMaxWidth())
            TransferText(stringResource(R.string.transfer_extension_format, destination.address), font, 13, 20, FontWeight.Normal, Color(0xFF557184), Modifier.fillMaxWidth())
            TransferText(message, font, 13, 21, FontWeight.Normal, Color(0xFF557184), Modifier.fillMaxWidth())
        }
    }
    Spacer(Modifier.height(20.dp))
    if (productIsEnglish()) Spacer(Modifier.weight(1f))
    extraAction?.let {
        TransferAction(it, null, font, Modifier.fillMaxWidth().widthIn(max = 364.dp), "${tag}_extra", onClick = onExtraAction)
        Spacer(Modifier.height(10.dp))
    }
    onCancel?.let {
        TransferAction(stringResource(R.string.transfer_cancel_return), null, font, Modifier.fillMaxWidth().widthIn(max = 364.dp), "${tag}_cancel", secondary = true, onClick = it)
        Spacer(Modifier.height(10.dp))
    }
    secondaryAction?.let {
        TransferAction(it, null, font, Modifier.fillMaxWidth().widthIn(max = 364.dp), "${tag}_secondary", secondary = true, onClick = onSecondaryAction)
        Spacer(Modifier.height(10.dp))
    }
    tertiaryAction?.let {
        TransferAction(it, null, font, Modifier.fillMaxWidth().widthIn(max = 364.dp), "${tag}_tertiary", secondary = true, onClick = onTertiaryAction)
        Spacer(Modifier.height(10.dp))
    }
    if (showReturnAction) TransferAction(returnActionLabel ?: stringResource(R.string.transfer_return_primary), null, font, Modifier.fillMaxWidth().widthIn(max = 364.dp), "${tag}_return", secondary = extraAction != null, onClick = action)
}

@Composable
private fun ColumnScope.ResultSurface(title: String, message: String, destination: RasvenTransferDestination?, font: FontFamily,
    success: Boolean, onAction: () -> Unit, actionLabel: String? = null, resultTag: String? = null) {
    TransferTitle(title, font, resultTag ?: if (success) "transfer_result_success" else "transfer_result_failure")
    Spacer(Modifier.height(16.dp))
    destination?.let {
        TransferCard(Modifier.fillMaxWidth().widthIn(max = 364.dp), testTag = "transfer_result_details") {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransferText(transferDestinationLabel(it.label), font, 15, 24, FontWeight.SemiBold, Color(0xFF102B42), Modifier.fillMaxWidth())
                TransferText(stringResource(R.string.transfer_extension_format, it.address), font, 13, 20, FontWeight.Normal, Color(0xFF557184), Modifier.fillMaxWidth())
                TransferText(message, font, 13, 21, FontWeight.Normal, Color(0xFF557184), Modifier.fillMaxWidth())
            }
        }
    }
    if (productIsEnglish()) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(20.dp))
    TransferAction(actionLabel ?: stringResource(R.string.transfer_back), null, font, Modifier.fillMaxWidth().widthIn(max = 364.dp), "transfer_result_action", onClick = onAction,
        success = success)
}

@Composable
private fun TransferTitle(value: String, font: FontFamily, tag: String = "transfer_title") {
    TransferText(value, font, if (productIsEnglish()) 14 else 18, if (productIsEnglish()) 24 else 28,
        FontWeight.SemiBold, Color(0xFF102B42), Modifier.fillMaxWidth().testTag(tag).semantics { heading() })
}

@Composable
private fun TransferAction(
    title: String,
    subtitle: String?,
    font: FontFamily,
    modifier: Modifier,
    tag: String,
    secondary: Boolean = false,
    success: Boolean = false,
    onClick: () -> Unit,
) {
    val english = productIsEnglish()
    if (english && subtitle == null) {
        if (secondary) RasvenOutlineButton(title, fontFamily = font, modifier = modifier.testTag(tag),
            size = RasvenButtonSize.Large, expandToFullWidth = true, onClick = onClick)
        else RasvenButton(title, fontFamily = font, modifier = modifier.testTag(tag),
            size = RasvenButtonSize.Large, expandToFullWidth = true, onClick = onClick)
        return
    }
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(if (english) 16.dp else 20.dp)
    val color = if (english) colors[RasvenSemanticColors.surfaceGlass] else when {
        success -> Color(0xFF15856C)
        secondary -> Color.White.copy(alpha = .74f)
        else -> Color(0xFF087DAA)
    }
    Column(
        modifier = modifier
            .then(if (english) Modifier.heightIn(min = 64.dp).rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape) else Modifier)
            .clip(shape).background(color).border(1.dp, if (english) colors[RasvenSemanticColors.borderGlass] else if (secondary) Color(0xFFB5DCE4) else color, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (english) 16.dp else 18.dp, vertical = if (english) 16.dp else 14.dp).testTag(tag),
        horizontalAlignment = Alignment.End,
    ) {
        TransferText(title, font, if (english) 14 else 15, 24, FontWeight.SemiBold, if (english) colors[RasvenSemanticColors.textPrimary] else if (secondary) Color(0xFF087DAA) else Color.White, Modifier.fillMaxWidth())
        subtitle?.let {
            Spacer(Modifier.height(if (english) 8.dp else 4.dp))
            TransferText(it, font, 12, if (english) 18 else 19, FontWeight.Normal, if (english) colors[RasvenSemanticColors.textSecondary] else if (secondary) Color(0xFF557184) else Color.White.copy(alpha = .9f), Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TransferCard(modifier: Modifier, testTag: String, content: @Composable () -> Unit) {
    val english = productIsEnglish()
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(if (english) 16.dp else 20.dp)
    Box(modifier
        .then(if (english) Modifier.rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape) else Modifier)
        .clip(shape).background(if (english) colors[RasvenSemanticColors.surfaceGlass] else Color.White.copy(alpha = .78f))
        .border(1.dp, if (english) colors[RasvenSemanticColors.borderGlass] else Color.White, shape).testTag(testTag)) { content() }
}

@Composable
private fun TransferText(value: String, font: FontFamily, size: Int, lineHeight: Int, weight: FontWeight,
    color: Color, modifier: Modifier, ltr: Boolean = false) {
    BasicText(value, modifier = modifier, style = TextStyle(color = color, fontFamily = font,
        fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight, textAlign = productTextAlignment(),
        textDirection = if (ltr) TextDirection.Ltr else productTextDirection()))
}
/** Translate only known presentation labels. Selection keeps the original configured key. */
@Composable
private fun transferDestinationLabel(label: String): String {
    if (!productIsEnglish()) return label
    return when (label) {
        "پشتیبانی فنی" -> stringResource(R.string.transfer_support)
        "فروش" -> stringResource(R.string.transfer_sales)
        "مدیریت" -> stringResource(R.string.transfer_management)
        else -> label
    }
}

@Composable
private fun TransferPresetCard(
    title: String,
    extension: String,
    font: FontFamily,
    selected: Boolean,
    modifier: Modifier,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val english = productIsEnglish()
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape)
            .clip(shape)
            .background(colors[RasvenSemanticColors.surfaceGlass])
            .border(1.dp, colors[RasvenSemanticColors.borderGlass], shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        horizontalAlignment = if (english) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = title,
            style = TextStyle(
                color = if (selected) Color(0xFF0369A1) else colors[RasvenSemanticColors.textPrimary],
                fontFamily = font,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = productTextAlignment(),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = stringResource(R.string.transfer_extension_format, extension),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textSecondary],
                fontFamily = font,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                textAlign = productTextAlignment(),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
