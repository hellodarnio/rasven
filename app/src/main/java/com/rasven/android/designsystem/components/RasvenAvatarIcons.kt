package com.rasven.android.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.RasvenDesignTheme
import com.rasven.android.designsystem.foundation.RasvenSemanticColors

/** Glyph set used in caller and abstract contact avatar boxes across FA/EN call flows. */
internal enum class RasvenAvatarIcon {
    Caller,
    Call,
    CallOutgoing,
    Pause,
    User,
    CallUser,
    Calling,
}

internal fun avatarIconResource(icon: RasvenAvatarIcon): Int = when (icon) {
    RasvenAvatarIcon.Caller -> R.drawable.rasven_avatar_user_circle_bulk
    RasvenAvatarIcon.Call -> R.drawable.rasven_call
    RasvenAvatarIcon.CallOutgoing -> R.drawable.rasven_history_call_out
    RasvenAvatarIcon.Pause -> R.drawable.rasven_avatar_pause
    RasvenAvatarIcon.User -> R.drawable.rasven_avatar_user_outline
    RasvenAvatarIcon.CallUser -> R.drawable.rasven_avatar_call_user_outline
    RasvenAvatarIcon.Calling -> R.drawable.rasven_avatar_calling_outline
}

/** Caller avatar glyph is white on its brand surface; abstract avatar glyphs use icon/primary. */
@Composable
internal fun RasvenAvatarIcon(
    icon: RasvenAvatarIcon,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = RasvenDesignTheme.colors
    val tintColor = colors[
        if (icon == RasvenAvatarIcon.Caller) RasvenSemanticColors.iconOnBrand
        else RasvenSemanticColors.iconPrimary
    ]
    Image(
        painter = painterResource(avatarIconResource(icon)),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tintColor),
        modifier = modifier.size(size),
    )
}
