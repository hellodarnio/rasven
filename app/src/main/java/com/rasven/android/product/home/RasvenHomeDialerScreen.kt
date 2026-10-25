package com.rasven.android.product.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.*
import com.rasven.android.designsystem.components.RasvenBadge
import com.rasven.android.designsystem.components.RasvenBadgeLanguage
import com.rasven.android.designsystem.components.RasvenBadgeSize
import com.rasven.android.designsystem.components.RasvenBadgeStyle
import com.rasven.android.designsystem.components.RasvenBadgeType
import com.rasven.android.designsystem.components.RasvenBottomNavigation
import com.rasven.android.designsystem.components.RasvenButton
import com.rasven.android.designsystem.components.RasvenButtonSize
import com.rasven.android.designsystem.components.RasvenDialGlyph
import com.rasven.android.designsystem.components.RasvenDialKey
import com.rasven.android.designsystem.components.RasvenDialKeySpec
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.designsystem.components.RasvenPrimaryCallAction
import com.rasven.android.designsystem.components.RasvenSwitch
import com.rasven.android.designsystem.components.RasvenTextField
import com.rasven.android.designsystem.components.RasvenTextFieldAppearance
import com.rasven.android.designsystem.components.RasvenTextFieldSize
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenProductLayout
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens
import com.rasven.android.product.setup.RasvenRegistrationState

/**
 * One state-driven Home/Dialer surface.
 * Connected: 843:5466 / 5539 / 5612 / 5682.
 * Disconnected: 843:5754. Home reconnecting: 843:5883.
 */
@Composable
internal fun RasvenHomeDialerScreen(
    state: RasvenHomeDialerUiState,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onDestinationChange: (String) -> Unit,
    onDialKey: (String) -> Unit,
    onDelete: () -> Unit,
    onDoNotDisturbChanged: (Boolean) -> Unit,
    onStartOutgoingCall: (String) -> Unit,
    onReconnect: () -> Unit,
    onNavigationDestination: (RasvenNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageFont = productPageFont(persianFontFamily)
    val dataFont = productDataFont(englishFontFamily)
    val presentation = homeConnectionPresentation(state.connectionState)
    val connected = presentation == RasvenHomeConnectionPresentation.Connected
    val disconnected = presentation == RasvenHomeConnectionPresentation.Disconnected
    val destinationNonEmpty = state.destination.isNotEmpty()

    val rootStateDescription = when (presentation) {
        RasvenHomeConnectionPresentation.Connected -> stringResource(R.string.home_connected)
        RasvenHomeConnectionPresentation.Disconnected -> stringResource(R.string.home_disconnected_badge)
        RasvenHomeConnectionPresentation.Reconnecting -> stringResource(R.string.home_reconnecting_badge)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp)
            .testTag("home_dialer_screen")
            .semantics { stateDescription = rootStateDescription },
    ) {
        val destinationWidth = (maxWidth - 48.dp).coerceIn(
            RasvenProductLayout.destinationInputMinWidth.dp,
            RasvenProductLayout.destinationInputMaxWidth.dp,
        )

        // Same clipped Figma ambient export is shared by all approved Home states.
        RasvenDailyAmbient()

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            RasvenDailyBottomLayout(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            ) {
                HomeHeader(
                    presentation = presentation,
                    extension = state.extension,
                    persianFontFamily = pageFont,
                )

                if (connected) {
                    DoNotDisturbControl(
                        checked = state.doNotDisturb,
                        onCheckedChange = onDoNotDisturbChanged,
                        persianFontFamily = pageFont,
                        modifier = Modifier.absoluteOffset(x = 16.dp, y = 80.dp),
                    )
                }

                DestinationInput(
                    destination = state.destination,
                    destinationWidth = destinationWidth,
                    persianFontFamily = pageFont,
                    englishFontFamily = dataFont,
                    enabled = connected,
                    helper = when (presentation) {
                        RasvenHomeConnectionPresentation.Connected -> stringResource(
                            if (destinationNonEmpty) R.string.home_destination_helper_ready
                            else R.string.home_destination_helper_empty,
                        )
                        RasvenHomeConnectionPresentation.Disconnected -> stringResource(R.string.home_disconnected_helper)
                        RasvenHomeConnectionPresentation.Reconnecting -> stringResource(R.string.home_reconnecting_helper)
                    },
                    onDestinationChange = onDestinationChange,
                    onDelete = onDelete,
                    modifier = Modifier.fillMaxWidth().absoluteOffset(
                        y = if (connected) 140.dp else 94.dp,
                    ),
                )

                HomeDialPad(
                    englishFontFamily = dataFont,
                    enabled = connected,
                    onDialKey = onDialKey,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = (if (connected) 156.dp else 202.dp) + dailyBottomLift())
                        .testTag("home_dial_pad"),
                )

                if (connected) {
                    RasvenPrimaryCallAction(
                        contentLabel = stringResource(R.string.home_start_call_accessibility),
                        onClick = { invokeOutgoingCallIfEnabled(state.destination, onStartOutgoingCall) },
                        enabled = destinationNonEmpty,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 88.dp + dailyBottomLift())
                            .testTag("home_call_action"),
                    )
                }

                if (disconnected) {
                    RasvenButton(
                        label = stringResource(R.string.home_reconnect),
                        size = RasvenButtonSize.Large,
                        fontFamily = pageFont,
                        onClick = onReconnect,
                        expandToFullWidth = true,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 116.dp + dailyBottomLift())
                            .width(destinationWidth)
                            .testTag("home_reconnect"),
                    )
                }

                RasvenBottomNavigation(
                    language = productBadgeLanguage(),
                    selectedDestination = RasvenNavDestination.Home,
                    fontFamily = pageFont,
                    onDestinationClick = onNavigationDestination,
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .padding(bottom = dailyBottomLift())
                        .testTag("home_bottom_navigation"),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    presentation: RasvenHomeConnectionPresentation,
    extension: String,
    persianFontFamily: FontFamily,
) {
    val colors = RasvenDesignTheme.colors
    val heading = RasvenTypographyTokens.headingL
    val body = RasvenTypographyTokens.bodyS
    val subtitle = when (presentation) {
        RasvenHomeConnectionPresentation.Connected -> stringResource(
            R.string.home_extension_format,
            RasvenDailyPresentationContract.displayDigits(extension, LocalRasvenProductLanguage.current),
        )
        RasvenHomeConnectionPresentation.Disconnected -> stringResource(R.string.home_disconnected_subtitle)
        RasvenHomeConnectionPresentation.Reconnecting -> stringResource(R.string.home_reconnecting_subtitle)
    }
    val badgeLabel = when (presentation) {
        RasvenHomeConnectionPresentation.Connected -> stringResource(R.string.home_connected)
        RasvenHomeConnectionPresentation.Disconnected -> stringResource(R.string.home_disconnected_badge)
        RasvenHomeConnectionPresentation.Reconnecting -> stringResource(R.string.home_reconnecting_badge)
    }
    val badgeType = when (presentation) {
        RasvenHomeConnectionPresentation.Connected -> RasvenBadgeType.Success
        RasvenHomeConnectionPresentation.Disconnected -> RasvenBadgeType.Neutral
        RasvenHomeConnectionPresentation.Reconnecting -> RasvenBadgeType.Info
    }
    val badgeTag = when (presentation) {
        RasvenHomeConnectionPresentation.Connected -> "home_connected_badge"
        RasvenHomeConnectionPresentation.Disconnected -> "home_disconnected_badge"
        RasvenHomeConnectionPresentation.Reconnecting -> "home_reconnecting_badge"
    }

    Box(Modifier.fillMaxWidth().height(90.dp)) {
        Column(
            modifier = if (productIsEnglish()) Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 18.dp).width(168.dp)
                else Modifier.align(Alignment.TopEnd).padding(end = 28.dp, top = 18.dp).width(120.dp),
            horizontalAlignment = if (productIsEnglish()) Alignment.Start else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(
                text = stringResource(R.string.home_line_title),
                modifier = Modifier.semantics { heading() }.testTag("home_line_title"),
                style = TextStyle(
                    color = colors[RasvenSemanticColors.textPrimary], fontFamily = persianFontFamily,
                    fontSize = heading.fontSizeSp.sp, lineHeight = heading.lineHeightSp.sp,
                    fontWeight = FontWeight(heading.fontWeight), textAlign = productTextAlignment(), textDirection = productTextDirection(),
                ),
            )
            BasicText(
                text = subtitle,
                modifier = Modifier.testTag("home_line_subtitle"),
                style = TextStyle(
                    color = colors[RasvenSemanticColors.textSecondary], fontFamily = persianFontFamily,
                    fontSize = body.fontSizeSp.sp, lineHeight = body.lineHeightSp.sp,
                    fontWeight = FontWeight(body.fontWeight), textAlign = productTextAlignment(), textDirection = productTextDirection(),
                ),
            )
        }
        RasvenBadge(
            label = badgeLabel,
            type = badgeType,
            style = RasvenBadgeStyle.Soft,
            size = RasvenBadgeSize.Small,
            language = productBadgeLanguage(),
            fontFamily = persianFontFamily,
            modifier = (if (productIsEnglish()) Modifier.align(Alignment.TopEnd).absoluteOffset(x = (-84).dp, y = 26.dp)
                else Modifier.absoluteOffset(x = 84.dp, y = 26.dp)).testTag(badgeTag),
        )
        Image(
            painter = painterResource(R.drawable.rasven_brand_symbol_v01_candidate),
            contentDescription = null,
            modifier = (if (productIsEnglish()) Modifier.align(Alignment.TopEnd).absoluteOffset(x = (-16).dp, y = 14.dp)
                else Modifier.absoluteOffset(x = 16.dp, y = 14.dp)).size(48.dp),
        )
    }
}

@Composable
private fun DoNotDisturbControl(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    persianFontFamily: FontFamily,
    modifier: Modifier,
) {
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusOverlay.dp)
    val dndLabel = stringResource(R.string.home_dnd)
    CompositionLocalProvider(LocalLayoutDirection provides productValueRowDirection()) {
        Row(
            modifier = modifier
                .size(width = RasvenDailyPresentationContract.dndWidth(LocalRasvenProductLanguage.current).dp, height = 48.dp)
                .rasvenShadow(RasvenEffects.subtleCard.shadows.single(), shape)
                .clip(shape)
                .background(colors[RasvenSemanticColors.surfaceGlass])
                .border(1.dp, colors[RasvenSemanticColors.borderGlass], shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RasvenSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag("home_dnd_switch").semantics {
                    contentDescription = dndLabel
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable(role = Role.Button) { onCheckedChange(!checked) }
                    .testTag("home_dnd_label"),
            ) {
                BasicText(
                    text = stringResource(R.string.home_dnd),
                    style = TextStyle(
                        color = colors[RasvenSemanticColors.textPrimary], fontFamily = persianFontFamily,
                        fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
                        textAlign = productTextAlignment(), textDirection = productTextDirection(),
                    ),
                )
                Image(
                    painter = painterResource(R.drawable.rasven_dnd_outline), contentDescription = null,
                    colorFilter = ColorFilter.tint(colors[RasvenSemanticColors.iconPrimary]), modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun DestinationInput(
    destination: String,
    destinationWidth: androidx.compose.ui.unit.Dp,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    enabled: Boolean,
    helper: String,
    onDestinationChange: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    val colors = RasvenDesignTheme.colors
    val body = RasvenTypographyTokens.bodyS
    val valueToken = homeDestinationValueColorToken(enabled, destination)
    val valueTransformation = if (valueToken != null) {
        HomeDestinationColorTransformation(colors[valueToken])
    } else {
        VisualTransformation.None
    }

    val deleteOne = stringResource(R.string.home_delete_one)
    val deleteAll = stringResource(R.string.home_delete_all)
    Box(modifier.height(108.dp)) {
        BasicText(
            text = stringResource(R.string.home_destination_label),
            modifier = Modifier.align(Alignment.TopCenter).width(destinationWidth).testTag("home_destination_label"),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textPrimary],
                fontFamily = persianFontFamily,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = productTextAlignment(), textDirection = productTextDirection(),
            ),
        )
        RasvenTextField(
            value = destination,
            onValueChange = { onDestinationChange(sanitizeDirectDialInput(it)) },
            fontFamily = persianFontFamily,
            valueFontFamily = englishFontFamily,
            valueFontSize = 24.sp,
            // This field has no internal Persian label/helper. Its whole value viewport
            // must be LTR too: TextAlign.Left alone leaves the tight inner text at RTL start.
            layoutDirection = LayoutDirection.Ltr,
            valueTextDirection = TextDirection.Ltr,
            // Owner override: technical digits begin at the physical left, not RTL start.
            valueTextAlign = TextAlign.Left,
            visualTransformation = valueTransformation,
            appearance = RasvenTextFieldAppearance.Glass,
            size = RasvenTextFieldSize.XLarge,
            enabled = enabled,
            readOnly = true,
            accessibilityLabel = stringResource(R.string.home_destination_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp).width(destinationWidth)
                .testTag("home_destination_control")
                .then(if (!enabled) Modifier.semantics { disabled() } else Modifier),
            inputModifier = Modifier
                .absolutePadding(right = if (enabled && destination.isNotEmpty()) 60.dp else 0.dp)
                .testTag("home_destination_input"),
        )
        BasicText(
            text = helper,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp).width(destinationWidth)
                .testTag("home_destination_helper"),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textSecondary], fontFamily = persianFontFamily,
                fontSize = body.fontSizeSp.sp, lineHeight = body.lineHeightSp.sp, textAlign = productTextAlignment(), textDirection = productTextDirection(),
            ),
        )
        if (enabled && destination.isNotEmpty()) {
            Box(
                modifier = Modifier.align(Alignment.TopCenter)
                    .absoluteOffset(x = destinationWidth / 2 - 32.dp, y = 30.dp).size(48.dp)
                    .testTag("home_delete")
                    .semantics { contentDescription = deleteOne }
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors[RasvenSemanticColors.surfaceGlassSelected])
                    .border(1.dp, colors[RasvenSemanticColors.borderGlassSelected], RoundedCornerShape(12.dp))
                    .combinedClickable(
                        role = Role.Button,
                        onClick = onDelete,
                        onLongClickLabel = deleteAll,
                        onLongClick = { onDestinationChange("") },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.rasven_backspace_outline),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors[RasvenSemanticColors.iconPrimary]),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

internal fun homeDestinationValueColorToken(enabled: Boolean, destination: String) =
    if (enabled && destination.isNotEmpty()) RasvenSemanticColors.textTertiary else null

private class HomeDestinationColorTransformation(private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            withStyle(SpanStyle(color = color)) { append(text.text) }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private val homeDialSpecs = listOf(
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
private fun HomeDialPad(
    englishFontFamily: FontFamily,
    enabled: Boolean,
    onDialKey: (String) -> Unit,
    modifier: Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier.width(276.dp).height(332.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            homeDialSpecs.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { spec ->
                        val accessible = when (spec.primary) {
                            "*" -> stringResource(R.string.home_dial_star)
                            "#" -> stringResource(R.string.home_dial_hash)
                            else -> spec.primary
                        }
                        RasvenDialKey(
                            spec = spec,
                            fontFamily = englishFontFamily,
                            onClick = { onDialKey(spec.primary) },
                            enabled = enabled,
                            modifier = Modifier
                                .testTag("home_key_${spec.primary}")
                                .semantics { contentDescription = accessible },
                        )
                    }
                }
            }
        }
    }
}
