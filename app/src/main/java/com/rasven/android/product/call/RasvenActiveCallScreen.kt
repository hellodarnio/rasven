package com.rasven.android.product.call

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.components.RasvenAvatarIcon
import com.rasven.android.designsystem.components.RasvenBadge
import com.rasven.android.designsystem.components.RasvenBadgeSize
import com.rasven.android.designsystem.components.RasvenBadgeStyle
import com.rasven.android.designsystem.components.RasvenBadgeType
import com.rasven.android.designsystem.components.RasvenCallControl
import com.rasven.android.designsystem.components.RasvenCallControlAction
import com.rasven.android.designsystem.components.RasvenCallControlSize
import com.rasven.android.designsystem.components.RasvenCallControlsGroup
import com.rasven.android.designsystem.components.RasvenDialGlyph
import com.rasven.android.designsystem.components.RasvenDialKey
import com.rasven.android.designsystem.components.RasvenDialKeySpec
import com.rasven.android.designsystem.components.RasvenEndCallAction
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenInverseCallSurface
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.LocalRasvenProductLanguage
import com.rasven.android.designsystem.foundation.RasvenDailyPresentationContract
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.productBadgeLanguage
import com.rasven.android.designsystem.foundation.productDataFont
import com.rasven.android.designsystem.foundation.productPageFont
import com.rasven.android.designsystem.foundation.productIsEnglish
import com.rasven.android.designsystem.foundation.productTextAlignment
import com.rasven.android.designsystem.foundation.productUiDirection
import java.util.Locale
import kotlinx.coroutines.delay

private val inCallKeys = listOf(
    RasvenDialKeySpec(RasvenDialGlyph.Standard, "1"),
    RasvenDialKeySpec(RasvenDialGlyph.Standard, "2", "ABC"),
    RasvenDialKeySpec(RasvenDialGlyph.Standard, "3", "DEF"),
    RasvenDialKeySpec(RasvenDialGlyph.Standard, "4", "GHI"),
    RasvenDialKeySpec(RasvenDialGlyph.Standard, "5", "JKL"),
    RasvenDialKeySpec(RasvenDialGlyph.Standard, "6", "MNO"),
    RasvenDialKeySpec(RasvenDialGlyph.Standard, "7", "PQRS"),
    RasvenDialKeySpec(RasvenDialGlyph.Standard, "8", "TUV"),
    RasvenDialKeySpec(RasvenDialGlyph.Standard, "9", "WXYZ"),
    RasvenDialKeySpec(RasvenDialGlyph.Asterisk, "*"),
    RasvenDialKeySpec(RasvenDialGlyph.Standard, "0", "+"),
    RasvenDialKeySpec(RasvenDialGlyph.Hash, "#"),
)

@Composable
internal fun RasvenActiveCallScreen(
    destination: String,
    connectedAtElapsedMillis: Long,
    controls: RasvenActiveCallControls,
    persianFontFamily: FontFamily,
    onMute: (Boolean) -> Unit,
    onSpeaker: (Boolean) -> Unit,
    canHold: Boolean,
    onHold: (Boolean) -> Unit,
    onKeypad: () -> Unit,
    onDtmf: (Char) -> Unit,
    onEnd: () -> Unit,
    onTransfer: () -> Unit = {},
    lineIdentity: String? = null,
) {
    BackHandler(enabled = true) { if (controls.keypadVisible) onKeypad() }
    var now by remember { mutableLongStateOf(System.nanoTime() / 1_000_000L) }
    LaunchedEffect(connectedAtElapsedMillis) {
        while (true) {
            now = System.nanoTime() / 1_000_000L
            delay(1_000)
        }
    }
    val language = LocalRasvenProductLanguage.current
    val pageFont = productPageFont(persianFontFamily)
    val keypadFont = productDataFont(FontFamily.Default)
    val endLabel = stringResource(R.string.active_call_end)
    val duration = formatActiveCallDuration(connectedAtElapsedMillis, now, language)
    val colors = RasvenDesignTheme.colors
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
        colors[RasvenSemanticColors.backgroundInverse],
        colors[RasvenSemanticColors.backgroundInverseEnd],
    )))) {
        // EN uses the original transparent270px SVG; FA retains its previous raster.
        if (productIsEnglish()) RasvenEnglishInverseCallAmbient() else Image(
            painterResource(R.drawable.rasven_in_call_ambient),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).size(205.dp, 185.dp),
        )
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val keypad = controls.keypadVisible
            val compactHeight = maxHeight < if (keypad) 800.dp else 716.dp
            val scrollable = activeCallControlsScrollable(maxHeight, keypad)
            val englishFigmaCallLayout = productIsEnglish() && !keypad && !compactHeight && !scrollable
            val controlSize = if (maxWidth < 360.dp) RasvenCallControlSize.Compact else RasvenCallControlSize.Regular
            Column(
                Modifier.fillMaxSize()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(start = 16.dp, end = 16.dp,
                        top = if (englishFigmaCallLayout) 21.dp else 14.dp,
                        bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (keypad) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CallDurationPill(duration, pageFont, compact = true)
                            CompositionLocalProvider(LocalLayoutDirection provides productUiDirection()) {
                                RasvenBadge(
                                    label = stringResource(R.string.active_call_connected), type = RasvenBadgeType.Success,
                                    style = RasvenBadgeStyle.Soft, size = RasvenBadgeSize.Medium,
                                    language = productBadgeLanguage(), fontFamily = pageFont,
                                    modifier = Modifier.testTag("active_call_status_badge"),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(if (compactHeight) 2.dp else 4.dp))
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (productIsEnglish()) Alignment.TopCenter else Alignment.CenterEnd) {
                        RasvenBadge(
                            label = stringResource(if (controls.held) R.string.active_call_held_badge else R.string.active_call_connected),
                            type = if (controls.held) RasvenBadgeType.Info else RasvenBadgeType.Success,
                            style = RasvenBadgeStyle.Soft, size = RasvenBadgeSize.Medium,
                            language = productBadgeLanguage(), fontFamily = pageFont,
                            modifier = Modifier.testTag("active_call_status_badge"),
                        )
                    }
                    Spacer(Modifier.height(when {
                        englishFigmaCallLayout -> 39.dp
                        compactHeight -> 28.dp
                        else -> 42.dp
                    }))
                }
                val avatarSize = if (compactHeight && keypad) 72.dp else 88.dp
                Box(
                    Modifier.size(avatarSize).clip(CircleShape)
                        .background(colors[RasvenSemanticColors.backgroundInverseEnd])
                        .border(1.dp, Color.White.copy(alpha = 0.78f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    RasvenAvatarIcon(RasvenAvatarIcon.Caller, if (avatarSize == 72.dp) 34.dp else 40.dp)
                }
                Spacer(Modifier.height(when {
                    keypad -> 16.dp
                    englishFigmaCallLayout -> 30.dp
                    else -> 20.dp
                }))
                CallText(destination, 20, 32, FontWeight.SemiBold, pageFont)
                Spacer(Modifier.height(if (keypad) 6.dp else 8.dp))
                val subtitle = if (controls.held && !keypad) {
                    stringResource(R.string.active_call_primary_held)
                } else if (keypad) {
                    controls.lastDtmfAccepted?.let {
                        stringResource(R.string.active_call_dtmf_sent,
                            RasvenDailyPresentationContract.displayDigits(it.toString(), language))
                    } ?: stringResource(R.string.active_call_keypad_title)
                } else {
                    lineIdentity?.takeIf { it.isNotBlank() }?.let {
                        stringResource(
                            if (it.length <= 6 && it.all(Char::isDigit)) R.string.active_call_business_extension
                            else R.string.active_call_business_identity,
                            RasvenDailyPresentationContract.displayDigits(it, language),
                        )
                    } ?: stringResource(R.string.active_call_connected_title)
                }
                CallText(subtitle, 12, 18, FontWeight.Normal, pageFont)
                if (keypad) {
                    // The Figma keypad frame places the 3×4 grid lower than the compact
                    // active-call header. Keep that breathing room even on short layouts;
                    // scrolling below still protects End Call from the navigation inset.
                    Spacer(Modifier.height(if (compactHeight) 46.dp else 50.dp))
                    RasvenInverseCallSurface {
                        // The official Figma dial pad uses 20 px between all four rows.
                        // Keep this invariant on compact screens too; squeezing it to 8 dp
                        // pulled the selected keypad control noticeably upward.
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            inCallKeys.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    row.forEach { key ->
                                        val keyLabel = stringResource(R.string.active_call_dtmf_accessibility, key.primary)
                                        RasvenDialKey(
                                            spec = key,
                                            fontFamily = keypadFont,
                                            onClick = { onDtmf(key.primary.single()) },
                                            modifier = Modifier.semantics {
                                                contentDescription = keyLabel
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    RasvenInverseCallSurface {
                        RasvenCallControl(
                            action = RasvenCallControlAction.Keypad,
                            language = productBadgeLanguage(),
                            size = RasvenCallControlSize.Compact,
                            fontFamily = pageFont,
                            selected = true,
                            onClick = onKeypad,
                            modifier = Modifier.testTag("active_call_keypad_toggle"),
                        )
                    }
                } else {
                    Spacer(Modifier.height(if (englishFigmaCallLayout) 7.dp else 10.dp))
                    CallDurationPill(duration, pageFont, compact = false)
                    if (controls.held) {
                        Spacer(Modifier.height(if (englishFigmaCallLayout) 9.dp else 10.dp))
                        HeldCallMessage(pageFont)
                    }
                    when {
                        englishFigmaCallLayout && controls.held -> Spacer(Modifier.height(16.dp))
                        englishFigmaCallLayout -> Spacer(Modifier.height(27.dp))
                        scrollable -> Spacer(Modifier.height(if (controls.held) 16.dp else 36.dp))
                        else -> Spacer(Modifier.weight(1f))
                    }
                    RasvenInverseCallSurface { RasvenCallControlsGroup(
                        language = productBadgeLanguage(),
                        size = controlSize,
                        fontFamily = pageFont,
                        selected = buildSet {
                            if (controls.muted) add(RasvenCallControlAction.Mute)
                            if (controls.speaker) add(RasvenCallControlAction.Speaker)
                            if (controls.held) add(RasvenCallControlAction.Hold)
                        },
                        enabled = buildSet {
                            add(RasvenCallControlAction.Mute)
                            add(RasvenCallControlAction.Speaker)
                            if (canHold) add(RasvenCallControlAction.Hold)
                            add(RasvenCallControlAction.Keypad)
                            add(RasvenCallControlAction.Transfer)
                        },
                        onAction = { action -> when (action) {
                            RasvenCallControlAction.Mute -> onMute(!controls.muted)
                            RasvenCallControlAction.Speaker -> onSpeaker(!controls.speaker)
                            RasvenCallControlAction.Hold -> onHold(!controls.held)
                            RasvenCallControlAction.Keypad -> onKeypad()
                            RasvenCallControlAction.Transfer -> onTransfer()
                        } },
                        modifier = Modifier.testTag("active_call_controls"),
                    ) }
                }
                if (keypad) {
                    if (scrollable) Spacer(Modifier.height(16.dp)) else Spacer(Modifier.weight(1f))
                } else {
                    Spacer(Modifier.height(when {
                        englishFigmaCallLayout && controls.held -> 32.dp
                        englishFigmaCallLayout -> 38.dp
                        else -> 18.dp
                    }))
                }
                RasvenEndCallAction(
                    contentLabel = endLabel,
                    onClick = onEnd,
                    modifier = Modifier.size(128.dp, 58.dp),
                )
                if (englishFigmaCallLayout && !controls.held) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeldCallMessage(fontFamily: FontFamily) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(108.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.66f))
            .border(1.dp, Color.White.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (productIsEnglish()) Alignment.Start else Alignment.End,
    ) {
        BasicText(
            stringResource(R.string.active_call_held_title),
            style = TextStyle(color = Color(0xFF102B42), fontFamily = fontFamily,
                fontSize = 14.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
                textAlign = productTextAlignment()),
        )
        BasicText(
            stringResource(R.string.active_call_held_body),
            style = TextStyle(color = Color(0xFF102B42), fontFamily = fontFamily,
                fontSize = 12.sp, lineHeight = 18.sp, textAlign = productTextAlignment()),
        )
    }
}

@Composable
private fun CallDurationPill(duration: String, fontFamily: FontFamily, compact: Boolean) {
    val width = if (compact) 104.dp else 120.dp
    val height = if (compact) 28.dp else 32.dp
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.size(width, height)
                .testTag(if (compact) "active_call_duration_compact" else "active_call_duration")
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(16.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallText(duration, if (compact) 13 else 17, if (compact) 20 else 24, FontWeight.SemiBold, fontFamily)
            Spacer(Modifier.size(7.dp))
            Image(
                painterResource(if (productIsEnglish()) {
                    if (compact) R.drawable.rasven_en_in_call_clock else R.drawable.rasven_en_in_call_clock_regular
                } else R.drawable.rasven_in_call_time_icon),
                contentDescription = null,
                modifier = Modifier.size(if (productIsEnglish() && compact) 14.dp else 16.dp),
            )
        }
    }
}

@Composable
private fun CallText(value: String, sizeSp: Int, lineHeightSp: Int, weight: FontWeight, family: FontFamily) {
    BasicText(
        text = value,
        style = TextStyle(
            color = Color.White,
            fontSize = sizeSp.sp,
            lineHeight = lineHeightSp.sp,
            fontWeight = weight,
            fontFamily = family,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun activeCallControlsScrollable(
    viewportHeight: androidx.compose.ui.unit.Dp,
    keypadVisible: Boolean,
): Boolean = viewportHeight < if (keypadVisible) 760.dp else 640.dp

internal fun formatActiveCallDuration(
    connectedAtElapsedMillis: Long,
    nowElapsedMillis: Long,
    language: RasvenLanguage = RasvenLanguage.Persian,
): String {
    val elapsedSeconds = ((nowElapsedMillis - connectedAtElapsedMillis).coerceAtLeast(0L) / 1_000L)
    val clock = if (language == RasvenLanguage.English) {
        String.format(Locale.ROOT, "%02d:%02d", elapsedSeconds / 60L, elapsedSeconds % 60L)
    } else {
        "%02d:%02d".format(elapsedSeconds / 60L, elapsedSeconds % 60L)
    }
    return RasvenDailyPresentationContract.displayDigits(clock, language)
}
