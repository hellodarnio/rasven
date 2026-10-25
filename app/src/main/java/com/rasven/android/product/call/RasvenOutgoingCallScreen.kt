package com.rasven.android.product.call

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import com.rasven.android.designsystem.components.RasvenBadge
import com.rasven.android.designsystem.components.RasvenBadgeSize
import com.rasven.android.designsystem.components.RasvenBadgeStyle
import com.rasven.android.designsystem.components.RasvenBadgeType
import com.rasven.android.designsystem.components.RasvenButton
import com.rasven.android.designsystem.components.RasvenButtonSize
import com.rasven.android.designsystem.components.RasvenEndCallAction
import com.rasven.android.designsystem.components.RasvenOutlineButton
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.LocalRasvenProductLanguage
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.productBadgeLanguage
import com.rasven.android.designsystem.foundation.productIsEnglish
import com.rasven.android.designsystem.foundation.productPageFont
import com.rasven.android.designsystem.foundation.productTextAlignment
import com.rasven.android.designsystem.foundation.productUiDirection
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens

internal fun formatOutgoingDestinationForDisplay(destination: String, language: RasvenLanguage = RasvenLanguage.Persian): String {
    val compact = destination.replace(" ", "")
    val asciiGrouped = if (compact.length == 11 && compact.startsWith("0") && compact.all(Char::isDigit)) {
        "${compact.substring(0, 4)} ${compact.substring(4, 7)} ${compact.substring(7)}"
    } else {
        destination
    }
    if (language == RasvenLanguage.English) return asciiGrouped
    return buildString(asciiGrouped.length) {
        asciiGrouped.forEach { ch ->
            append(if (ch in '0'..'9') ('۰'.code + (ch - '0')).toChar() else ch)
        }
    }
}

private data class FailurePresentation(
    val titleRes: Int,
    val cardTitleRes: Int,
    val guideRes: Int,
    val badgeType: RasvenBadgeType,
)

@Composable
internal fun RasvenOutgoingCallScreen(
    state: RasvenOutgoingCallState,
    persianFontFamily: FontFamily,
    onPermissionRequest: () -> Unit,
    onClosePermission: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onCloseFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageFont = productPageFont(persianFontFamily)
    val english = productIsEnglish()
    val backEnabled = state is RasvenOutgoingCallState.AwaitingMicrophonePermission ||
        state is RasvenOutgoingCallState.Dialing || state is RasvenOutgoingCallState.Ringing || state.isFailureSurface()
    BackHandler(enabled = backEnabled) {
        when (state) {
            is RasvenOutgoingCallState.AwaitingMicrophonePermission -> onClosePermission()
            is RasvenOutgoingCallState.Dialing -> onCancel()
            is RasvenOutgoingCallState.Ringing -> onCancel()
            else -> if (state.isFailureSurface()) onCloseFailure()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
            .rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp)
            .testTag("outgoing_call_screen"),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            if (english) {
                Image(
                    painterResource(R.drawable.rasven_setup_ambient_en),
                    contentDescription = null,
                    modifier = Modifier.align(AbsoluteAlignment.TopLeft).size(210.dp, 200.dp),
                )
            } else Box(
                modifier = Modifier.align(AbsoluteAlignment.TopRight)
                    .offset(x = 90.dp, y = (-100).dp)
                    .size(300.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x5C75D6E7), Color.Transparent),
                        ),
                        shape = CircleShape,
                    ),
            )
            Image(
                painter = painterResource(R.drawable.rasven_brand_symbol_v01_candidate),
                contentDescription = null,
                modifier = Modifier.align(if (english) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft)
                    .offset(x = if (english) (-16).dp else 16.dp, y = 40.dp).size(64.dp).testTag("outgoing_brand"),
            )
        }

        CompositionLocalProvider(LocalLayoutDirection provides productUiDirection()) {
            Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            ) {
                when (state) {
                    is RasvenOutgoingCallState.AwaitingMicrophonePermission -> PermissionPage(
                        destination = state.destination,
                        persianFontFamily = pageFont,
                        onPermissionRequest = onPermissionRequest,
                        onClose = onClosePermission,
                    )
                    is RasvenOutgoingCallState.Dialing -> {
                        val attempt = state.attempt
                        DialingPage(
                            destination = attempt.destination,
                            persianFontFamily = pageFont,
                            onCancel = onCancel,
                        )
                    }
                    is RasvenOutgoingCallState.Ringing -> DialingPage(
                        destination = state.attempt.destination,
                        persianFontFamily = pageFont,
                        onCancel = onCancel,
                        ringing = true,
                    )
                    is RasvenOutgoingCallState.Busy -> FailurePage(
                        destination = state.attempt.destination,
                        presentation = FailurePresentation(
                            R.string.outgoing_busy_title,
                            R.string.outgoing_busy_card_title,
                            R.string.outgoing_busy_guide,
                            RasvenBadgeType.Warning,
                        ),
                        persianFontFamily = pageFont,
                        onRetry = onRetry,
                        onClose = onCloseFailure,
                    )
                    is RasvenOutgoingCallState.Rejected -> FailurePage(
                        destination = state.attempt.destination,
                        presentation = FailurePresentation(
                            R.string.outgoing_rejected_title,
                            R.string.outgoing_rejected_card_title,
                            R.string.outgoing_rejected_guide,
                            RasvenBadgeType.Error,
                        ),
                        persianFontFamily = pageFont,
                        onRetry = onRetry,
                        onClose = onCloseFailure,
                    )
                    is RasvenOutgoingCallState.NoAnswer -> FailurePage(
                        destination = state.attempt.destination,
                        presentation = FailurePresentation(
                            R.string.outgoing_no_answer_title,
                            R.string.outgoing_no_answer_card_title,
                            R.string.outgoing_no_answer_guide,
                            RasvenBadgeType.Warning,
                        ),
                        persianFontFamily = pageFont,
                        onRetry = onRetry,
                        onClose = onCloseFailure,
                    )
                    is RasvenOutgoingCallState.NetworkError -> FailurePage(
                        destination = state.attempt.destination,
                        presentation = FailurePresentation(
                            R.string.outgoing_network_title,
                            R.string.outgoing_network_card_title,
                            R.string.outgoing_network_guide,
                            RasvenBadgeType.Error,
                        ),
                        persianFontFamily = pageFont,
                        onRetry = onRetry,
                        onClose = onCloseFailure,
                    )
                    RasvenOutgoingCallState.Idle,
                    is RasvenOutgoingCallState.PreparingAttempt,
                    is RasvenOutgoingCallState.Cancelled,
                    is RasvenOutgoingCallState.ConnectedBoundary,
                    is RasvenOutgoingCallState.Ended -> Unit
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PermissionPage(
    destination: String,
    persianFontFamily: FontFamily,
    onPermissionRequest: () -> Unit,
    onClose: () -> Unit,
) {
    OutgoingHeader(
        title = stringResource(R.string.outgoing_microphone_title),
        destination = destination,
        titleSize = 16,
        titleLineHeight = 26,
        persianFontFamily = persianFontFamily,
        testTag = "outgoing_permission_heading",
    )
    val colors = RasvenDesignTheme.colors
    val body = RasvenTypographyTokens.bodyS
    BasicText(
        text = stringResource(R.string.outgoing_microphone_blocked),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .offset(y = 118.dp).testTag("outgoing_permission_explanation"),
        style = TextStyle(
            color = colors[RasvenSemanticColors.textSecondary],
            fontFamily = persianFontFamily,
            fontWeight = FontWeight(body.fontWeight),
            fontSize = body.fontSizeSp.sp,
            lineHeight = body.lineHeightSp.sp,
            textAlign = TextAlign.Center,
        ),
    )
    StatusSymbol(type = RasvenBadgeType.Error, top = 176)
    GuideCard(
        top = 292,
        title = stringResource(R.string.outgoing_microphone_guide_title),
        body = stringResource(R.string.outgoing_microphone_guide_body),
        persianFontFamily = persianFontFamily,
        testTag = "outgoing_permission_guide",
    )
    BottomActions(
        primary = stringResource(R.string.outgoing_microphone_cta),
        secondary = stringResource(R.string.outgoing_close),
        persianFontFamily = persianFontFamily,
        onPrimary = onPermissionRequest,
        onSecondary = onClose,
        primaryTag = "outgoing_permission_cta",
        secondaryTag = "outgoing_permission_close",
    )
}

@Composable
private fun BoxScope.DialingPage(
    destination: String,
    persianFontFamily: FontFamily,
    onCancel: () -> Unit,
    ringing: Boolean = false,
) {
    OutgoingHeader(
        title = stringResource(if (ringing) R.string.outgoing_ringing_title else R.string.outgoing_dialing_title),
        destination = destination,
        titleSize = 20,
        titleLineHeight = 32,
        persianFontFamily = persianFontFamily,
        testTag = "outgoing_dialing_heading",
    )
    val colors = RasvenDesignTheme.colors
    Box(
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 220.dp).size(88.dp)
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), CircleShape)
            .clip(CircleShape)
            .background(colors[RasvenSemanticColors.statusInfoBackground])
            .border(1.dp, colors[RasvenSemanticColors.statusInfoBorder], CircleShape)
            .testTag("outgoing_dialing_status_symbol"),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.rasven_connecting_loading_official),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors[RasvenSemanticColors.statusInfoIcon]),
            modifier = Modifier.size(32.dp),
        )
    }
    GuideCard(
        top = 344,
        title = stringResource(if (ringing) R.string.outgoing_ringing_waiting else R.string.outgoing_dialing_waiting),
        body = null,
        persianFontFamily = persianFontFamily,
        testTag = "outgoing_dialing_guide",
    )
    Column(
        // Figma's cancellation group ends 70dp above the physical viewport bottom;
        // this content is already inside the system navigation safe area.
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (productIsEnglish()) 46.dp else 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RasvenEndCallAction(
            contentLabel = stringResource(R.string.outgoing_cancel),
            onClick = onCancel,
            modifier = Modifier.testTag("outgoing_cancel_action"),
        )
        val body = RasvenTypographyTokens.bodyM
        BasicText(
            text = stringResource(R.string.outgoing_cancel),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textPrimary],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight(body.fontWeight),
                fontSize = body.fontSizeSp.sp,
                lineHeight = body.lineHeightSp.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun BoxScope.FailurePage(
    destination: String,
    presentation: FailurePresentation,
    persianFontFamily: FontFamily,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    OutgoingHeader(
        title = stringResource(presentation.titleRes),
        destination = destination,
        titleSize = 16,
        titleLineHeight = 26,
        persianFontFamily = persianFontFamily,
        testTag = "outgoing_failure_heading",
    )
    RasvenBadge(
        label = stringResource(R.string.outgoing_failed_badge),
        type = presentation.badgeType,
        style = RasvenBadgeStyle.Soft,
        size = RasvenBadgeSize.Small,
        language = productBadgeLanguage(),
        fontFamily = persianFontFamily,
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 112.dp)
            .testTag("outgoing_failure_badge"),
    )
    StatusSymbol(type = presentation.badgeType, top = 176)
    GuideCard(
        top = 292,
        title = stringResource(presentation.cardTitleRes),
        body = stringResource(presentation.guideRes),
        persianFontFamily = persianFontFamily,
        testTag = "outgoing_failure_guide",
    )
    BottomActions(
        primary = stringResource(R.string.outgoing_retry),
        secondary = stringResource(R.string.outgoing_close),
        persianFontFamily = persianFontFamily,
        onPrimary = onRetry,
        onSecondary = onClose,
        primaryTag = "outgoing_retry",
        secondaryTag = "outgoing_close",
    )
}

@Composable
private fun OutgoingHeader(
    title: String,
    destination: String,
    titleSize: Int,
    titleLineHeight: Int,
    persianFontFamily: FontFamily,
    testTag: String,
) {
    val colors = RasvenDesignTheme.colors
    Column(
        // Explicit physical slots: FA left=96/right=16; EN left=16/right=96.
        // The parent adds the status bar once: Figma top=42 (dialing) / 38 (failure).
        modifier = Modifier.fillMaxWidth().absolutePadding(
            left = if (productIsEnglish()) 16.dp else 96.dp,
            right = if (productIsEnglish()) 96.dp else 16.dp,
            top = if (titleSize == 20) 18.dp else 14.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (titleSize == 20) 4.dp else 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        BasicText(
            text = title,
            modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag(testTag),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textPrimary],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = titleSize.sp,
                lineHeight = titleLineHeight.sp,
                textAlign = productTextAlignment(),
            ),
            maxLines = 2,
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BasicText(
                text = formatOutgoingDestinationForDisplay(destination, LocalRasvenProductLanguage.current),
                modifier = Modifier.fillMaxWidth().testTag("outgoing_destination"),
                style = TextStyle(
                    color = colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = productTextAlignment(),
                    textDirection = TextDirection.Ltr,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BoxScope.StatusSymbol(type: RasvenBadgeType, top: Int) {
    val colors = RasvenDesignTheme.colors
    val isWarning = type == RasvenBadgeType.Warning
    val background = colors[if (isWarning) RasvenSemanticColors.statusWarningBackground else RasvenSemanticColors.statusErrorBackground]
    val border = colors[if (isWarning) RasvenSemanticColors.statusWarningIndicator else RasvenSemanticColors.statusErrorIndicator]
    val icon = colors[if (isWarning) RasvenSemanticColors.statusWarningIcon else RasvenSemanticColors.statusErrorIcon]
    Box(
        modifier = Modifier.align(Alignment.TopCenter).offset(y = top.dp).size(88.dp)
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), CircleShape)
            .clip(CircleShape).background(background).border(1.dp, border, CircleShape)
            .testTag("outgoing_status_symbol"),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.rasven_registration_failure_official),
            contentDescription = null,
            colorFilter = ColorFilter.tint(icon),
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun BoxScope.GuideCard(
    top: Int,
    title: String,
    body: String?,
    persianFontFamily: FontFamily,
    testTag: String,
) {
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusOverlay.dp)
    val heading = RasvenTypographyTokens.headingS
    val bodyToken = RasvenTypographyTokens.bodyS
    Column(
        modifier = (if (body == null) Modifier.align(Alignment.TopCenter).widthIn(max = 340.dp).wrapContentWidth() else Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            .offset(y = top.dp)
            .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape)
            .clip(shape).background(colors[RasvenSemanticColors.surfaceGlass])
            .border(1.dp, colors[RasvenSemanticColors.borderGlass], shape)
            .padding(16.dp).testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(if (body == null) 0.dp else 8.dp),
        horizontalAlignment = if (body == null) Alignment.CenterHorizontally else Alignment.End,
    ) {
        BasicText(
            text = title,
            modifier = if (body == null) Modifier.wrapContentWidth() else Modifier.fillMaxWidth(),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textPrimary],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight(heading.fontWeight),
                fontSize = heading.fontSizeSp.sp,
                lineHeight = heading.lineHeightSp.sp,
                textAlign = if (body == null) TextAlign.Center else productTextAlignment(),
            ),
        )
        if (body != null) {
            BasicText(
                text = body,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(bodyToken.fontWeight),
                    fontSize = bodyToken.fontSizeSp.sp,
                    lineHeight = bodyToken.lineHeightSp.sp,
                    textAlign = productTextAlignment(),
                ),
            )
        }
    }
}

@Composable
private fun BoxScope.BottomActions(
    primary: String,
    secondary: String,
    persianFontFamily: FontFamily,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    primaryTag: String,
    secondaryTag: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RasvenButton(
            label = primary,
            size = RasvenButtonSize.Large,
            fontFamily = persianFontFamily,
            onClick = onPrimary,
            expandToFullWidth = true,
            modifier = Modifier.fillMaxWidth().testTag(primaryTag),
        )
        RasvenOutlineButton(
            label = secondary,
            size = RasvenButtonSize.Large,
            fontFamily = persianFontFamily,
            onClick = onSecondary,
            expandToFullWidth = true,
            modifier = Modifier.fillMaxWidth().testTag(secondaryTag),
        )
    }
}
