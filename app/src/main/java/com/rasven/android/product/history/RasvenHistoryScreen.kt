package com.rasven.android.product.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.*
import com.rasven.android.designsystem.components.RasvenBadgeLanguage
import com.rasven.android.designsystem.components.RasvenBottomNavigation
import com.rasven.android.designsystem.components.RasvenFilterChip
import com.rasven.android.designsystem.components.RasvenFilterChipIcon
import com.rasven.android.designsystem.components.RasvenFilterChipPalette
import com.rasven.android.designsystem.components.RasvenFilterChipSize
import com.rasven.android.designsystem.components.RasvenHistoryIcon
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.designsystem.components.historyIconResource
import com.rasven.android.designsystem.components.rasvenGradientFill
import com.rasven.android.designsystem.components.rasvenShadow
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenEffects
import com.rasven.android.designsystem.foundation.RasvenGradients
import com.rasven.android.designsystem.foundation.RasvenProductLayout
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens
import java.time.ZoneId

/**
 * Stage5B4 source ledger:
 * Product behavior 145:2557; Android handoff 198:4151; DS 550:8744 / 680:5205.
 * Final visual states: 900:9458 / 900:9535 / 900:9612 / 900:9689.
 * They are one state-driven History destination, not four Android routes.
 */
@Composable
internal fun RasvenHistoryScreen(
    state: RasvenHistoryUiState,
    nowEpochMillis: Long,
    zoneId: ZoneId,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
    onFilterSelected: (RasvenHistoryFilter) -> Unit,
    onNavigationDestination: (RasvenNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageFont = productPageFont(persianFontFamily)
    val dataFont = productDataFont(englishFontFamily)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .rasvenGradientFill(RasvenGradients.pageCanvas, 0.dp)
            .testTag("history_screen"),
    ) {
        val pageMargin = if (maxWidth < RasvenProductLayout.viewportReferenceBase.dp) {
            RasvenProductLayout.pageMarginCompact.dp
        } else {
            RasvenProductLayout.pageMarginBase.dp
        }
        val rowWidth = (maxWidth - pageMargin * 2).coerceIn(
            RasvenProductLayout.contentMinWidth.dp,
            RasvenProductLayout.destinationInputMaxWidth.dp,
        )

        RasvenDailyAmbient()

        CompositionLocalProvider(LocalLayoutDirection provides productUiDirection()) {
            RasvenDailyBottomLayout(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                HistoryHeader(pageFont)
                HistoryFilterBar(
                    selectedFilter = state.selectedFilter,
                    persianFontFamily = pageFont,
                    onFilterSelected = onFilterSelected,
                )
                HistoryList(
                    entries = state.visibleEntries,
                    width = rowWidth,
                    height = dailyRemainingContentHeight(205.dp),
                    nowEpochMillis = nowEpochMillis,
                    zoneId = zoneId,
                    persianFontFamily = pageFont,
                    englishFontFamily = dataFont,
                )
                RasvenBottomNavigation(
                    language = productBadgeLanguage(),
                    selectedDestination = RasvenNavDestination.History,
                    fontFamily = pageFont,
                    onDestinationClick = onNavigationDestination,
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .padding(bottom = dailyBottomLift())
                        .testTag("history_bottom_navigation"),
                )
            }
        }
    }
}

@Composable
private fun HistoryHeader(persianFontFamily: FontFamily) {
    val colors = RasvenDesignTheme.colors
    val heading = RasvenTypographyTokens.headingL
    val body = RasvenTypographyTokens.bodyS
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(Modifier.fillMaxWidth().height(96.dp)) {
        BasicText(
            text = stringResource(R.string.history_heading),
            modifier = Modifier.absoluteOffset(x = 24.dp, y = 16.dp)
                .fillMaxWidth().padding(end = 48.dp)
                .semantics { heading() }
                .testTag("history_heading"),
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
            text = stringResource(R.string.history_subtitle),
            modifier = Modifier.absoluteOffset(x = 24.dp, y = 54.dp)
                .fillMaxWidth().padding(end = 48.dp)
                .testTag("history_subtitle"),
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

private data class HistoryFilterVisual(
    val filter: RasvenHistoryFilter,
    val labelRes: Int,
    val icon: RasvenHistoryIcon,
)

private val historyFilterVisuals = listOf(
    // RTL Row places the first item at the right edge, matching the final Product composition.
    HistoryFilterVisual(RasvenHistoryFilter.All, R.string.history_filter_all, RasvenHistoryIcon.All),
    HistoryFilterVisual(RasvenHistoryFilter.Incoming, R.string.history_filter_incoming, RasvenHistoryIcon.Incoming),
    HistoryFilterVisual(RasvenHistoryFilter.Outgoing, R.string.history_filter_outgoing, RasvenHistoryIcon.Outgoing),
    HistoryFilterVisual(RasvenHistoryFilter.Missed, R.string.history_filter_missed, RasvenHistoryIcon.Missed),
)

@Composable
private fun BoxScope.HistoryFilterBar(
    selectedFilter: RasvenHistoryFilter,
    persianFontFamily: FontFamily,
    onFilterSelected: (RasvenHistoryFilter) -> Unit,
) {
    val colors = RasvenDesignTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier.absoluteOffset(y = 127.dp)
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(58.dp)
            .rasvenShadow(RasvenEffects.glassInput.shadows.single(), shape)
            .clip(shape)
            .background(colors[RasvenSemanticColors.surfaceInputGlass])
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag("history_filter_bar"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        historyFilterVisuals.forEach { visual ->
            val selected = selectedFilter == visual.filter
            val palette = historyFilterPalette(visual.filter, colors)
            val iconTint = historyFilterIconColor(visual.filter, colors)
            RasvenFilterChip(
                label = stringResource(visual.labelRes),
                selected = selected,
                size = RasvenFilterChipSize.Medium,
                fontFamily = persianFontFamily,
                onClick = { onFilterSelected(visual.filter) },
                palette = palette,
                // Final B4 Figma instances are restrained at 12/20 even though base DS label is 14/24.
                labelFontSizeSp = 12,
                labelLineHeightSp = 20,
                modifier = Modifier.testTag("history_filter_${visual.filter.name.lowercase()}"),
                leadingIcon = {
                    RasvenFilterChipIcon(
                        resourceId = historyIconResource(visual.icon),
                        size = RasvenFilterChipSize.Medium,
                        tintColor = iconTint,
                    )
                },
            )
        }
    }
}

@Composable
private fun historyFilterPalette(
    filter: RasvenHistoryFilter,
    colors: com.rasven.android.designsystem.foundation.RasvenColorResolver,
): RasvenFilterChipPalette = when (filter) {
    RasvenHistoryFilter.Incoming -> RasvenFilterChipPalette(
        unselectedFill = colors[RasvenSemanticColors.surfaceRaised],
        unselectedBorder = colors[RasvenSemanticColors.borderSubtle],
        selectedFill = colors[RasvenSemanticColors.statusSuccessBackground],
        selectedBorder = colors[RasvenSemanticColors.statusSuccessBorder],
        contentColor = colors[RasvenSemanticColors.statusSuccessText],
    )
    RasvenHistoryFilter.Outgoing -> RasvenFilterChipPalette(
        unselectedFill = colors[RasvenSemanticColors.surfaceRaised],
        unselectedBorder = colors[RasvenSemanticColors.borderSubtle],
        selectedFill = colors[RasvenSemanticColors.statusInfoBackground],
        selectedBorder = colors[RasvenSemanticColors.statusInfoBorder],
        contentColor = colors[RasvenSemanticColors.statusInfoText],
    )
    RasvenHistoryFilter.Missed -> RasvenFilterChipPalette(
        unselectedFill = colors[RasvenSemanticColors.surfaceRaised],
        unselectedBorder = colors[RasvenSemanticColors.borderSubtle],
        selectedFill = colors[RasvenSemanticColors.statusErrorBackground],
        selectedBorder = colors[RasvenSemanticColors.statusErrorBorder],
        contentColor = colors[RasvenSemanticColors.statusErrorText],
    )
    RasvenHistoryFilter.All -> RasvenFilterChipPalette(
        // Final Product frames bind no semantic token to these neutral instance overrides.
        unselectedFill = Color(0xFFF5F5F5),
        unselectedBorder = Color(0x736B7280),
        selectedFill = Color(0xFFE5E7EB),
        selectedBorder = Color(0xFF6B7280),
        contentColor = Color(0xFF374151),
    )
}

@Composable
private fun historyFilterIconColor(
    filter: RasvenHistoryFilter,
    colors: com.rasven.android.designsystem.foundation.RasvenColorResolver,
): Color = when (filter) {
    RasvenHistoryFilter.Incoming -> colors[RasvenSemanticColors.statusSuccessIcon]
    RasvenHistoryFilter.Outgoing -> colors[RasvenSemanticColors.statusInfoIcon]
    RasvenHistoryFilter.Missed -> colors[RasvenSemanticColors.statusErrorIcon]
    RasvenHistoryFilter.All -> Color(0xFF374151)
}

@Composable
private fun BoxScope.HistoryList(
    entries: List<RasvenHistoryEntry>,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    nowEpochMillis: Long,
    zoneId: ZoneId,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
) {
    LazyColumn(
        modifier = Modifier.absoluteOffset(y = 205.dp)
            .align(Alignment.TopCenter)
            .width(width)
            .height(height)
            .testTag("history_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = RasvenHistoryEntry::id) { entry ->
            HistoryRow(
                entry = entry,
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId,
                persianFontFamily = persianFontFamily,
                englishFontFamily = englishFontFamily,
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: RasvenHistoryEntry,
    nowEpochMillis: Long,
    zoneId: ZoneId,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
) {
    val colors = RasvenDesignTheme.colors
    val rowShape = RoundedCornerShape(16.dp)
    val bodyM = RasvenTypographyTokens.bodyM
    val bodyS = RasvenTypographyTokens.bodyS
    val identity = entry.displayIdentity ?: stringResource(R.string.history_unknown_number)
    val classificationLabel = when (entry.classification) {
        RasvenHistoryCallClassification.Incoming -> stringResource(R.string.history_classification_incoming)
        RasvenHistoryCallClassification.Outgoing -> stringResource(R.string.history_classification_outgoing)
        RasvenHistoryCallClassification.Missed -> stringResource(R.string.history_classification_missed)
    }
    val moment = formatHistoryMoment(entry.timestampEpochMillis, nowEpochMillis, zoneId, LocalRasvenProductLanguage.current)
    val semanticSecondary = when (entry.classification) {
        RasvenHistoryCallClassification.Incoming -> "\u2066${formatHistoryTechnicalDestination(historyCallableDestination(entry))}\u2069"
        RasvenHistoryCallClassification.Outgoing -> stringResource(
            R.string.history_internal_format,
            "\u2066${historyCallableDestination(entry)}\u2069",
        )
        RasvenHistoryCallClassification.Missed -> stringResource(R.string.history_missed_context)
    }
    val semanticDescription = listOf(classificationLabel, identity, semanticSecondary, moment).joinToString(productSemanticSeparator())

    CompositionLocalProvider(LocalLayoutDirection provides productValueRowDirection()) {
    Row(
        modifier = Modifier.fillMaxWidth().height(82.dp)
            .clip(rowShape)
            .background(colors[RasvenSemanticColors.surfaceGlass])
            .border(1.dp, colors[RasvenSemanticColors.borderGlass], rowShape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = semanticDescription }
            .testTag("history_row_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = moment,
            modifier = if (productIsEnglish() && moment != "Yesterday" && moment.length > 5)
                Modifier.widthIn(min = historyMomentWidthDp(moment, LocalRasvenProductLanguage.current).dp)
                else Modifier.width(historyMomentWidthDp(moment, LocalRasvenProductLanguage.current).dp),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textSecondary],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight(bodyS.fontWeight),
                fontSize = bodyS.fontSizeSp.sp,
                lineHeight = bodyS.lineHeightSp.sp,
                textAlign = if (productIsEnglish()) TextAlign.Right else TextAlign.Left,
                textDirection = productTextDirection(),
            ),
            maxLines = 1,
        )

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = identity,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = colors[RasvenSemanticColors.textPrimary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(bodyM.fontWeight),
                    fontSize = bodyM.fontSizeSp.sp,
                    lineHeight = bodyM.lineHeightSp.sp,
                    textAlign = productTextAlignment(),
                    textDirection = productTextDirection(),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HistorySecondaryText(entry, persianFontFamily, englishFontFamily)
        }

        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50))
                .background(colors[RasvenSemanticColors.surfaceDefault]),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when (entry.classification) {
                RasvenHistoryCallClassification.Incoming -> RasvenHistoryIcon.Incoming
                RasvenHistoryCallClassification.Outgoing -> RasvenHistoryIcon.Outgoing
                RasvenHistoryCallClassification.Missed -> RasvenHistoryIcon.Missed
            }
            val tint = when (entry.classification) {
                RasvenHistoryCallClassification.Incoming -> colors[RasvenSemanticColors.statusSuccessIcon]
                RasvenHistoryCallClassification.Outgoing -> colors[RasvenSemanticColors.statusInfoIcon]
                RasvenHistoryCallClassification.Missed -> colors[RasvenSemanticColors.statusErrorIcon]
            }
            Image(
                painter = painterResource(historyIconResource(icon)),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(20.dp),
            )
        }
    }
    }
}

@Composable
private fun HistorySecondaryText(
    entry: RasvenHistoryEntry,
    persianFontFamily: FontFamily,
    englishFontFamily: FontFamily,
) {
    val colors = RasvenDesignTheme.colors
    val bodyS = RasvenTypographyTokens.bodyS
    val baseModifier = Modifier.fillMaxWidth()
    when (entry.classification) {
        RasvenHistoryCallClassification.Missed -> BasicText(
            text = stringResource(R.string.history_missed_context),
            modifier = baseModifier,
            style = TextStyle(
                color = colors[RasvenSemanticColors.textBrand],
                fontFamily = persianFontFamily,
                fontWeight = FontWeight(bodyS.fontWeight),
                fontSize = bodyS.fontSizeSp.sp,
                lineHeight = bodyS.lineHeightSp.sp,
                textAlign = productTextAlignment(),
                textDirection = productTextDirection(),
            ),
            maxLines = 1,
        )
        RasvenHistoryCallClassification.Incoming -> BasicText(
            text = formatHistoryTechnicalDestination(historyCallableDestination(entry)),
            modifier = baseModifier.testTag("history_destination_${entry.id}"),
            style = TextStyle(
                color = colors[RasvenSemanticColors.textSecondary],
                fontFamily = englishFontFamily,
                fontWeight = FontWeight(bodyS.fontWeight),
                fontSize = bodyS.fontSizeSp.sp,
                lineHeight = bodyS.lineHeightSp.sp,
                textAlign = productTextAlignment(),
                textDirection = TextDirection.Ltr,
            ),
            maxLines = 1,
        )
        RasvenHistoryCallClassification.Outgoing -> {
            val technicalIsland = "\u2066${historyCallableDestination(entry)}\u2069"
            BasicText(
                text = stringResource(R.string.history_internal_format, technicalIsland),
                modifier = baseModifier.testTag("history_destination_${entry.id}"),
                style = TextStyle(
                    color = colors[RasvenSemanticColors.textSecondary],
                    fontFamily = persianFontFamily,
                    fontWeight = FontWeight(bodyS.fontWeight),
                    fontSize = bodyS.fontSizeSp.sp,
                    lineHeight = bodyS.lineHeightSp.sp,
                    textAlign = productTextAlignment(),
                    textDirection = productTextDirection(),
                ),
                maxLines = 1,
            )
        }
    }
}
