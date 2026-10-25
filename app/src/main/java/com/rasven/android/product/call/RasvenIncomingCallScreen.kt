package com.rasven.android.product.call

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.productIsEnglish

@Composable
internal fun RasvenIncomingCallScreen(
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
    val dark = locked && state is RasvenIncomingCallState.Ringing
    val ink = if (dark) Color.White else Color(0xFF152D38)
    val call = when (state) {
        is RasvenIncomingCallState.Ringing -> state.call
        is RasvenIncomingCallState.AwaitingMicrophone -> state.call
        is RasvenIncomingCallState.Answering -> state.call
        is RasvenIncomingCallState.Active -> state.call
        else -> null
    }
    BackHandler(state !is RasvenIncomingCallState.Idle) {
        when (state) {
            is RasvenIncomingCallState.Missed -> onClose()
            is RasvenIncomingCallState.Active -> onEnd(state.call.sessionId)
            is RasvenIncomingCallState.AwaitingMicrophone -> onReject(state.call.sessionId)
            else -> Unit
        }
    }
    if (productIsEnglish()) {
        RasvenIncomingCallEnglishPresentation(
            state, locked, persianFontFamily, onAnswer, onReject, onEnd, onRequestMicrophone, onClose, onCallback,
        )
        return
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(if (dark) listOf(Color(0xFF123B4C), Color(0xFF071F2C))
            else listOf(Color(0xFFF7FCFC), Color(0xFFE3F3F5)))
        ).statusBarsPadding().navigationBarsPadding().testTag("incoming_call_screen"),
    ) {
        Image(
            painterResource(R.drawable.rasven_brand_symbol_v01_candidate),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 20.dp).size(52.dp),
        )
        val title = when (state) {
            is RasvenIncomingCallState.Ringing -> stringResource(R.string.incoming_call_title)
            is RasvenIncomingCallState.AwaitingMicrophone -> stringResource(R.string.incoming_call_microphone_title)
            is RasvenIncomingCallState.Answering -> stringResource(R.string.incoming_call_answering)
            is RasvenIncomingCallState.Active -> stringResource(R.string.incoming_call_active)
            is RasvenIncomingCallState.Missed -> stringResource(R.string.incoming_call_missed)
            is RasvenIncomingCallState.Ended -> stringResource(R.string.incoming_call_ended)
            RasvenIncomingCallState.Idle -> ""
        }
        Text(title, modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 38.dp),
            fontFamily = persianFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ink)
        if (call != null) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (state is RasvenIncomingCallState.AwaitingMicrophone) {
                    Image(painterResource(R.drawable.rasven_incoming_permission_warning), null,
                        Modifier.size(88.dp).background(Color(0xFFFFE8E8), CircleShape).padding(26.dp))
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.incoming_call_microphone_instruction), color = ink,
                        fontFamily = persianFontFamily, fontSize = 18.sp, textAlign = TextAlign.Center)
                } else {
                    Box(Modifier.size(88.dp).background(Color(0xFFBDE4E8), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text((call.name ?: call.number).take(1), color = Color(0xFF176174), fontSize = 35.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(call.name ?: stringResource(R.string.incoming_call_unknown), color = ink, fontFamily = persianFontFamily,
                        fontWeight = FontWeight.Bold, fontSize = 26.sp, textAlign = TextAlign.Center)
                    Text(call.number, color = ink.copy(alpha = .75f), fontSize = 17.sp,
                        textAlign = TextAlign.Center)
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                when (state) {
                    is RasvenIncomingCallState.Ringing -> {
                        Action(stringResource(R.string.incoming_call_decline), Color(0xFFDC5258), Modifier.weight(1f)) { onReject(call.sessionId) }
                        Action(stringResource(R.string.incoming_call_answer), Color(0xFF138D73), Modifier.weight(1f)) { onAnswer(call.sessionId) }
                    }
                    is RasvenIncomingCallState.AwaitingMicrophone -> {
                        Action(stringResource(R.string.incoming_call_close), Color(0xFF708993), Modifier.weight(1f)) { onReject(call.sessionId) }
                        Action(stringResource(R.string.incoming_call_allow_microphone), Color(0xFF137D94), Modifier.weight(2f)) {
                            onRequestMicrophone(call.sessionId)
                        }
                    }
                    is RasvenIncomingCallState.Active -> Action(stringResource(R.string.incoming_call_end), Color(0xFFDC5258), Modifier.fillMaxWidth()) {
                        onEnd(call.sessionId)
                    }
                    else -> Unit
                }
            }
        }
        if (state is RasvenIncomingCallState.Missed) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.entry.displayIdentity ?: state.entry.callableDestination,
                    color = ink, fontFamily = persianFontFamily, fontSize = 23.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(state.entry.callableDestination, color = ink, fontSize = 17.sp)
                Spacer(Modifier.height(36.dp))
                Action(stringResource(R.string.incoming_call_callback), Color(0xFF138D73), Modifier.fillMaxWidth()) {
                    onCallback(state.entry.callableDestination)
                }
                Spacer(Modifier.height(12.dp))
                Action(stringResource(R.string.incoming_call_close), Color(0xFF708993), Modifier.fillMaxWidth(), onClose)
            }
        }
    }
}

@Composable
private fun Action(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(58.dp).background(color, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    textAlign: TextAlign = TextAlign.Start,
) {
    BasicText(text, modifier, style = TextStyle(color = color, fontFamily = fontFamily,
        fontWeight = fontWeight, fontSize = fontSize, textAlign = textAlign))
}
