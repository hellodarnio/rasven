package com.rasven.android.designsystem.components

import com.rasven.android.designsystem.foundation.RasvenSemanticColors
import com.rasven.android.designsystem.foundation.RasvenShadowSpec

/** Effects defined on Stage 4 components, separate from the frozen Stage 3 foundation contract. */
internal object RasvenComponentEffects {
    val callControlsGroup = RasvenShadowSpec(
        offsetXPx = 0f,
        offsetYPx = 12f,
        radiusPx = 28f,
        spreadPx = -10f,
        color = RasvenSemanticColors.shadowDialog,
        showShadowBehindNode = true,
    )
}
