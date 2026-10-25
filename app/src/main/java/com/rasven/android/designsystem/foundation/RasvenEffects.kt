package com.rasven.android.designsystem.foundation

internal data class RasvenBackdropBlurSpec(
    val radiusPx: Float,
)

internal data class RasvenShadowSpec(
    val offsetXPx: Float,
    val offsetYPx: Float,
    val radiusPx: Float,
    val spreadPx: Float,
    val color: RasvenNamedColorToken,
    val showShadowBehindNode: Boolean,
)

internal data class RasvenEffectSpec(
    val backdropBlur: RasvenBackdropBlurSpec? = null,
    val shadows: List<RasvenShadowSpec> = emptyList(),
)

/**
 * Raw Figma effects. `BACKGROUND_BLUR` is preserved as a backdrop-blur contract; it must not be
 * silently replaced by Compose content blur, which is a different visual operation.
 */
internal object RasvenEffects {
    val subtleCard = RasvenEffectSpec(
        shadows = listOf(
            RasvenShadowSpec(
                offsetXPx = 0f,
                offsetYPx = 2f,
                radiusPx = 8f,
                spreadPx = 0f,
                color = RasvenSemanticColors.shadowSubtle,
                showShadowBehindNode = false,
            ),
        ),
    )

    val bottomNavigation = RasvenEffectSpec(
        backdropBlur = RasvenBackdropBlurSpec(radiusPx = 24f),
        shadows = listOf(
            RasvenShadowSpec(
                offsetXPx = 0f,
                offsetYPx = 14f,
                radiusPx = 18f,
                spreadPx = -10f,
                color = RasvenSemanticColors.shadowOverlay,
                showShadowBehindNode = true,
            ),
        ),
    )

    val glassInput = RasvenEffectSpec(
        backdropBlur = RasvenBackdropBlurSpec(radiusPx = 18f),
        shadows = listOf(
            RasvenShadowSpec(
                offsetXPx = 0f,
                offsetYPx = 12f,
                radiusPx = 28f,
                spreadPx = -10f,
                color = RasvenSemanticColors.shadowInputGlass,
                showShadowBehindNode = true,
            ),
        ),
    )

    val primaryCall = RasvenEffectSpec(
        shadows = listOf(
            RasvenShadowSpec(
                offsetXPx = 0f,
                offsetYPx = 11f,
                radiusPx = 24f,
                spreadPx = -7f,
                color = RasvenSemanticColors.shadowPrimaryCall,
                showShadowBehindNode = true,
            ),
        ),
    )

    val endCall = RasvenEffectSpec(
        shadows = listOf(
            RasvenShadowSpec(
                offsetXPx = 0f,
                offsetYPx = 11f,
                radiusPx = 24f,
                spreadPx = -7f,
                color = RasvenSemanticColors.shadowEndCall,
                showShadowBehindNode = true,
            ),
        ),
    )

    val selectedCallControl = RasvenEffectSpec(
        shadows = listOf(
            RasvenShadowSpec(
                offsetXPx = 0f,
                offsetYPx = 8f,
                radiusPx = 18f,
                spreadPx = -7f,
                color = RasvenSemanticColors.shadowCallControlSelected,
                showShadowBehindNode = true,
            ),
        ),
    )

    val dialKey = RasvenEffectSpec(
        backdropBlur = RasvenBackdropBlurSpec(radiusPx = 14f),
        shadows = listOf(
            RasvenShadowSpec(
                offsetXPx = 0f,
                offsetYPx = 9f,
                radiusPx = 22f,
                spreadPx = -9f,
                color = RasvenSemanticColors.shadowDialKey,
                showShadowBehindNode = true,
            ),
        ),
    )

    val focusHalo = RasvenEffectSpec(
        shadows = listOf(
            RasvenShadowSpec(
                offsetXPx = 0f,
                offsetYPx = 0f,
                radiusPx = 0f,
                spreadPx = 3f,
                color = RasvenSemanticColors.focusHalo,
                showShadowBehindNode = true,
            ),
        ),
    )
}
