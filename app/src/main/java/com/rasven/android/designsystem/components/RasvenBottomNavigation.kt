package com.rasven.android.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenEffects

internal enum class RasvenNavDestination { Home, Contacts, History, Settings }

internal fun navLabel(destination: RasvenNavDestination, language: RasvenBadgeLanguage): String = when (destination) {
    RasvenNavDestination.Home -> if (language == RasvenBadgeLanguage.Fa) "خانه" else "Home"
    RasvenNavDestination.Contacts -> if (language == RasvenBadgeLanguage.Fa) "مخاطبین" else "Contacts"
    RasvenNavDestination.History -> if (language == RasvenBadgeLanguage.Fa) "سوابق" else "History"
    RasvenNavDestination.Settings -> if (language == RasvenBadgeLanguage.Fa) "تنظیمات" else "Settings"
}

internal fun navIcon(destination: RasvenNavDestination, selected: Boolean): Int = when (destination) {
    RasvenNavDestination.Home -> if (selected) R.drawable.rasven_nav_home_solid else R.drawable.rasven_nav_home_outline
    RasvenNavDestination.Contacts -> if (selected) R.drawable.rasven_nav_contacts_solid else R.drawable.rasven_nav_contacts_outline
    RasvenNavDestination.History -> if (selected) R.drawable.rasven_nav_history_solid else R.drawable.rasven_nav_history_outline
    RasvenNavDestination.Settings -> if (selected) R.drawable.rasven_nav_settings_solid else R.drawable.rasven_nav_settings_outline
}

/** Four equally growing destinations; language direction determines their physical order. */
@Composable
internal fun RasvenBottomNavigation(
    language: RasvenBadgeLanguage,
    selectedDestination: RasvenNavDestination,
    fontFamily: FontFamily,
    onDestinationClick: (RasvenNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    horizontalInset: Dp = 8.dp,
    highlightSelected: Boolean = false,
) {
    val colors = RasvenDesignTheme.colors
    val direction = if (language == RasvenBadgeLanguage.Fa) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val width = (maxWidth - horizontalInset * 2).coerceIn(
                if (highlightSelected) 272.dp else 320.dp, 376.dp,
            )
            val shape = RoundedCornerShape(28.dp)
            Row(
                modifier = Modifier
                    .selectableGroup()
                    .size(width, 72.dp)
                    .rasvenShadow(RasvenEffects.bottomNavigation.shadows.single(), shape)
                    .clip(shape)
                    .background(colors[RasvenSemanticColors.surfaceNavigationGlass])
                    .border(1.dp, colors[RasvenSemanticColors.borderNavigationGlass], shape)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RasvenNavDestination.entries.forEach { destination ->
                    val isSelected = destination == selectedDestination
                    val color = colors[if (isSelected) RasvenSemanticColors.iconBrand else RasvenSemanticColors.iconSecondary]
                    Column(
                        modifier = Modifier.weight(1f).height(64.dp)
                            .then(if (isSelected && highlightSelected) Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors[RasvenSemanticColors.selectionBackground]) else Modifier)
                            .selectable(selected = isSelected, role = Role.Tab) {
                                onDestinationClick(destination)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(
                            painter = painterResource(navIcon(destination, isSelected)),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(color),
                            modifier = Modifier.size(24.dp),
                        )
                        BasicText(
                            text = navLabel(destination, language),
                            style = TextStyle(
                                color = colors[if (isSelected) RasvenSemanticColors.textBrand else RasvenSemanticColors.textSecondary],
                                fontFamily = fontFamily, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
