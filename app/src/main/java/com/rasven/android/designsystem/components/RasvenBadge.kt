package com.rasven.android.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenNamedColorToken
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenSemanticLayout
import com.rasven.android.designsystem.foundation.RasvenTypographyTokens
import com.rasven.android.R

internal enum class RasvenBadgeType { Neutral, Info, Success, Warning, Error }
internal enum class RasvenBadgeStyle { Soft, Outline }
internal enum class RasvenBadgeSize { Small, Medium }
internal enum class RasvenBadgeLanguage { Fa, En }

internal data class RasvenBadgePalette(
    val background: RasvenNamedColorToken,
    val border: RasvenNamedColorToken,
    val text: RasvenNamedColorToken,
)

/** Status colors are shared by the FA and EN component sets. */
internal fun badgePalette(type: RasvenBadgeType): RasvenBadgePalette = when (type) {
    RasvenBadgeType.Neutral -> RasvenBadgePalette(
        RasvenSemanticColors.statusNeutralBackground,
        RasvenSemanticColors.statusNeutralBorder,
        RasvenSemanticColors.statusNeutralText,
    )
    RasvenBadgeType.Info -> RasvenBadgePalette(
        RasvenSemanticColors.statusInfoBackground,
        RasvenSemanticColors.statusInfoBorder,
        RasvenSemanticColors.statusInfoText,
    )
    RasvenBadgeType.Success -> RasvenBadgePalette(
        RasvenSemanticColors.statusSuccessBackground,
        RasvenSemanticColors.statusSuccessBorder,
        RasvenSemanticColors.statusSuccessText,
    )
    RasvenBadgeType.Warning -> RasvenBadgePalette(
        RasvenSemanticColors.statusWarningBackground,
        RasvenSemanticColors.statusWarningBorder,
        RasvenSemanticColors.statusWarningText,
    )
    RasvenBadgeType.Error -> RasvenBadgePalette(
        RasvenSemanticColors.statusErrorBackground,
        RasvenSemanticColors.statusErrorBorder,
        RasvenSemanticColors.statusErrorText,
    )
}

/**
 * Non-interactive DS Badge. FA has a visible official status icon; EN is text-only.
 * The caller supplies the approved font. Official FA icon exports are bundled locally.
 */
@Composable
internal fun RasvenBadge(
    label: String,
    type: RasvenBadgeType,
    style: RasvenBadgeStyle,
    size: RasvenBadgeSize,
    language: RasvenBadgeLanguage,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    val colors = RasvenDesignTheme.colors
    val palette = badgePalette(type)
    val height = if (size == RasvenBadgeSize.Small) 24.dp else 28.dp
    val metrics = if (size == RasvenBadgeSize.Small) {
        RasvenTypographyTokens.labelS
    } else {
        RasvenTypographyTokens.labelM
    }
    val shape = RoundedCornerShape(RasvenSemanticLayout.radiusFull.dp)

    Box(
        modifier = modifier
            .sizeIn(maxWidth = 200.dp)
            .height(height)
            .clip(shape)
            .background(if (style == RasvenBadgeStyle.Soft) colors[palette.background] else Color.Transparent)
            .border(RasvenSemanticLayout.borderWidthDefault.dp, colors[palette.border], shape)
            .padding(horizontal = RasvenSemanticLayout.spacingInlineMd.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(RasvenSemanticLayout.spacingInlineSm.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (language == RasvenBadgeLanguage.Fa) {
                val iconSize = if (size == RasvenBadgeSize.Small) 16 else 20
                val icon = when (type) {
                    RasvenBadgeType.Neutral -> if (iconSize == 16) R.drawable.rasven_status_neutral_16 else R.drawable.rasven_status_neutral_20
                    RasvenBadgeType.Info -> if (iconSize == 16) R.drawable.rasven_status_info_16 else R.drawable.rasven_status_info_20
                    RasvenBadgeType.Success -> if (iconSize == 16) R.drawable.rasven_status_success_16 else R.drawable.rasven_status_success_20
                    RasvenBadgeType.Warning -> if (iconSize == 16) R.drawable.rasven_status_warning_16 else R.drawable.rasven_status_warning_20
                    RasvenBadgeType.Error -> if (iconSize == 16) R.drawable.rasven_status_error_16 else R.drawable.rasven_status_error_20
                }
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize.dp))
            }
            BasicText(
                text = label,
                style = TextStyle(
                    color = colors[palette.text],
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = metrics.fontSizeSp.sp,
                    lineHeight = metrics.lineHeightSp.sp,
                    letterSpacing = metrics.letterSpacingSp.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
