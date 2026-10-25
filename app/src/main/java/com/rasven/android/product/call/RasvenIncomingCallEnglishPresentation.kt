package com.rasven.android.product.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.components.RasvenAvatarIcon
import com.rasven.android.designsystem.components.RasvenBadge
import com.rasven.android.designsystem.components.RasvenBadgeLanguage
import com.rasven.android.designsystem.components.RasvenBadgeSize
import com.rasven.android.designsystem.components.RasvenBadgeStyle
import com.rasven.android.designsystem.components.RasvenBadgeType
import com.rasven.android.designsystem.components.RasvenButton
import com.rasven.android.designsystem.components.RasvenButtonSize
import com.rasven.android.designsystem.components.RasvenEndCallAction
import com.rasven.android.designsystem.components.RasvenIncomingAnswerAction
import com.rasven.android.designsystem.components.RasvenOutlineButton
import com.rasven.android.designsystem.components.avatarIconResource
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.productPageFont

/** Presentation-only EN branch. All state/session ownership stays in the shared incoming flow. */
@Composable
internal fun RasvenIncomingCallEnglishPresentation(
    state: RasvenIncomingCallState,
    locked: Boolean,
    persianFontFamily: FontFamily,
    onAnswer: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    onRequestMicrophone: (Long) -> Unit,
    onClose: () -> Unit,
    onCallback: (String) -> Unit,
) {
    val colors = RasvenDesignTheme.colors
    val font = productPageFont(persianFontFamily)
    // Keep the same locked-state contract as the existing incoming screen.
    val dark = locked && state is RasvenIncomingCallState.Ringing
    val ink = if (dark) Color.White else colors[RasvenSemanticColors.textPrimary]
    val secondary = if (dark) Color.White else colors[RasvenSemanticColors.textSecondary]
    val call = when (state) {
        is RasvenIncomingCallState.Ringing -> state.call
        is RasvenIncomingCallState.AwaitingMicrophone -> state.call
        is RasvenIncomingCallState.Answering -> state.call
        is RasvenIncomingCallState.Active -> state.call
        else -> null
    }
    val title = when (state) {
        is RasvenIncomingCallState.Ringing -> stringResource(
            if (locked) R.string.incoming_call_locked_title else R.string.incoming_call_title)
        is RasvenIncomingCallState.AwaitingMicrophone -> stringResource(R.string.incoming_call_microphone_title)
        is RasvenIncomingCallState.Answering -> stringResource(R.string.incoming_call_answering)
        is RasvenIncomingCallState.Active -> stringResource(R.string.incoming_call_active)
        is RasvenIncomingCallState.Missed -> stringResource(R.string.incoming_call_missed)
        is RasvenIncomingCallState.Ended -> stringResource(R.string.incoming_call_ended)
        RasvenIncomingCallState.Idle -> ""
    }
    Box(Modifier.fillMaxSize().testTag("incoming_call_screen")
        .then(if (dark) Modifier.background(Brush.linearGradient(listOf(
            colors[RasvenSemanticColors.backgroundInverse], colors[RasvenSemanticColors.backgroundInverseEnd],
        ))) else Modifier.rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp))) {
        if (dark) RasvenEnglishInverseCallAmbient() else Image(
            painterResource(R.drawable.rasven_setup_ambient_en), null,
            Modifier.align(Alignment.TopStart).size(210.dp, 200.dp),
        )
        if (!dark) Image(painterResource(R.drawable.rasven_brand_symbol_v01_candidate), null,
            Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 40.dp).size(64.dp))
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val permission = state is RasvenIncomingCallState.AwaitingMicrophone
            val compact = maxHeight < 600.dp
            Column(
                Modifier.fillMaxSize().then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(if (dark) 28.dp else if (permission) 14.dp else 22.dp))
                IncomingEnglishText(title, font, if (permission) 16 else 20, if (permission) 26 else 32,
                    FontWeight.SemiBold, ink,
                    Modifier.fillMaxWidth().absolutePadding(left = 16.dp, right = if (dark) 16.dp else 96.dp)
                        .semantics { heading() }, if (dark) TextAlign.Center else TextAlign.Left)
                if (permission && call != null) {
                    Spacer(Modifier.height(8.dp))
                    IncomingEnglishText(call.name ?: call.number, font, 12, 18, FontWeight.Normal, secondary,
                        Modifier.fillMaxWidth().absolutePadding(left = 16.dp, right = 96.dp), TextAlign.Left)
                    Spacer(Modifier.height(32.dp))
                    IncomingEnglishText(stringResource(R.string.incoming_call_microphone_instruction),
                        font, 12, 18, FontWeight.Normal, secondary, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                    Spacer(Modifier.height(40.dp))
                    Box(Modifier.size(88.dp)
                        .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), CircleShape)
                        .background(colors[RasvenSemanticColors.statusErrorBackground], CircleShape)
                        .border(1.dp, colors[RasvenSemanticColors.statusErrorIndicator], CircleShape),
                        contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.rasven_incoming_permission_warning), null,
                            Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(28.dp))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 104.dp)
                        .background(colors[RasvenSemanticColors.surfaceGlass], RoundedCornerShape(16.dp))
                        .border(1.dp, colors[RasvenSemanticColors.borderGlass], RoundedCornerShape(16.dp))
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IncomingEnglishText(stringResource(R.string.incoming_call_microphone_guide_title),
                            font, 14, 24, FontWeight.SemiBold, ink, Modifier.fillMaxWidth(), TextAlign.Left)
                        IncomingEnglishText(stringResource(R.string.incoming_call_microphone_guide_body),
                            font, 12, 18, FontWeight.Normal, secondary, Modifier.fillMaxWidth(), TextAlign.Left)
                    }
                } else if (call != null) {
                    Spacer(Modifier.height(if (dark) 66.dp else 72.dp))
                    Box(Modifier.size(88.dp)
                        .background(if (dark) colors[RasvenSemanticColors.backgroundInverseEnd]
                            else colors[RasvenSemanticColors.surfaceGlass], CircleShape)
                        .border(1.dp, colors[RasvenSemanticColors.borderGlass], CircleShape),
                        contentAlignment = Alignment.Center) {
                        Image(painterResource(avatarIconResource(RasvenAvatarIcon.Caller)), null,
                            colorFilter = ColorFilter.tint(if (dark) Color.White else colors[RasvenSemanticColors.textBrand]),
                            modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    IncomingEnglishText(call.name ?: stringResource(R.string.incoming_call_unknown),
                        font, 16, 26, FontWeight.SemiBold, ink, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    Spacer(Modifier.height(12.dp))
                    IncomingEnglishText(call.number, font, 14, 24, FontWeight.Normal, secondary,
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp), technical = true)
                    Spacer(Modifier.height(12.dp))
                    RasvenBadge(stringResource(R.string.incoming_call_business_line),
                        type = RasvenBadgeType.Info, style = RasvenBadgeStyle.Soft, size = RasvenBadgeSize.Small,
                        language = RasvenBadgeLanguage.En, fontFamily = font)
                } else if (state is RasvenIncomingCallState.Missed) {
                    Spacer(Modifier.height(96.dp))
                    IncomingEnglishText(state.entry.displayIdentity ?: state.entry.callableDestination,
                        font, 16, 26, FontWeight.SemiBold, ink, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                    Spacer(Modifier.height(12.dp))
                    IncomingEnglishText(state.entry.callableDestination, font, 14, 24, FontWeight.Normal,
                        secondary, Modifier.fillMaxWidth().padding(horizontal = 24.dp), technical = true)
                    Spacer(Modifier.height(24.dp))
                    IncomingEnglishText(stringResource(R.string.incoming_call_missed_hint), font, 12, 18,
                        FontWeight.Normal, secondary, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                }
                if (compact) Spacer(Modifier.height(32.dp)) else Spacer(Modifier.weight(1f))
                when (state) {
                    is RasvenIncomingCallState.Ringing -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            IncomingEnglishAction(stringResource(R.string.incoming_call_decline), font, ink) {
                                RasvenEndCallAction(stringResource(R.string.incoming_call_decline),
                                    { onReject(state.call.sessionId) }, Modifier.size(128.dp, 58.dp))
                            }
                            IncomingEnglishAction(stringResource(R.string.incoming_call_answer), font, ink) {
                                RasvenIncomingAnswerAction(stringResource(R.string.incoming_call_answer),
                                    { onAnswer(state.call.sessionId) }, Modifier.size(128.dp, 58.dp))
                            }
                        }
                        Spacer(Modifier.height(if (compact) 24.dp else 155.dp))
                    }
                    is RasvenIncomingCallState.AwaitingMicrophone -> {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            RasvenButton(stringResource(R.string.incoming_call_allow_microphone),
                                RasvenButtonSize.Large, font, { onRequestMicrophone(state.call.sessionId) },
                                Modifier.fillMaxWidth(), expandToFullWidth = true)
                            RasvenOutlineButton(stringResource(R.string.incoming_call_close),
                                RasvenButtonSize.Large, font, { onReject(state.call.sessionId) },
                                Modifier.fillMaxWidth(), expandToFullWidth = true)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    is RasvenIncomingCallState.Active -> {
                        RasvenEndCallAction(stringResource(R.string.incoming_call_end),
                            { onEnd(state.call.sessionId) }, Modifier.size(128.dp, 58.dp))
                        Spacer(Modifier.height(16.dp))
                    }
                    is RasvenIncomingCallState.Missed -> {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RasvenButton(stringResource(R.string.incoming_call_callback), RasvenButtonSize.Large,
                                font, { onCallback(state.entry.callableDestination) }, Modifier.fillMaxWidth(), expandToFullWidth = true)
                            RasvenOutlineButton(stringResource(R.string.incoming_call_close), RasvenButtonSize.Large,
                                font, onClose, Modifier.fillMaxWidth(), expandToFullWidth = true)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    else -> Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun IncomingEnglishAction(label: String, font: FontFamily, ink: Color, action: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        action()
        IncomingEnglishText(label, font, 14, 24, FontWeight.Normal, ink)
    }
}

@Composable
private fun IncomingEnglishText(
    value: String, font: FontFamily, size: Int, lineHeight: Int, weight: FontWeight, ink: Color,
    modifier: Modifier = Modifier, alignment: TextAlign = TextAlign.Center, technical: Boolean = false,
) {
    BasicText(value, modifier, style = TextStyle(color = ink, fontFamily = font, fontSize = size.sp,
        lineHeight = lineHeight.sp, fontWeight = weight, textAlign = alignment,
        textDirection = if (technical) TextDirection.Ltr else TextDirection.ContentOrLtr))
}
