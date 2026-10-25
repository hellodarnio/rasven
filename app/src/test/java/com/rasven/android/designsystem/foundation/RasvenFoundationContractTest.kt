package com.rasven.android.designsystem.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenFoundationContractTest {
    @Test
    fun semanticColorRegistry_isCompleteAndUnique() {
        val all = RasvenSemanticColors.all
        assertEquals(116, all.size)
        assertEquals(116, all.map { it.sourceName }.toSet().size)
    }

    @Test
    fun responsiveReferences_match320_360_412Contract() {
        assertEquals(RasvenWidthClass.Compact, RasvenResponsive.widthClass(320))
        assertEquals(RasvenWidthClass.Regular, RasvenResponsive.widthClass(360))
        assertEquals(RasvenWidthClass.Regular, RasvenResponsive.widthClass(412))

        assertEquals(16, RasvenResponsive.pageHorizontalMargin(320))
        assertEquals(24, RasvenResponsive.pageHorizontalMargin(360))
        assertEquals(24, RasvenResponsive.pageHorizontalMargin(412))

        assertEquals(320, RasvenResponsive.bottomNavigationWidth(320))
        assertEquals(344, RasvenResponsive.bottomNavigationWidth(360))
        assertEquals(376, RasvenResponsive.bottomNavigationWidth(412))

        assertEquals(288, RasvenResponsive.destinationInputWidth(320))
        assertEquals(312, RasvenResponsive.destinationInputWidth(360))
        assertEquals(364, RasvenResponsive.destinationInputWidth(412))

        assertEquals(288, RasvenResponsive.callControlsGroupWidth(320))
        assertEquals(328, RasvenResponsive.callControlsGroupWidth(360))
        assertEquals(380, RasvenResponsive.callControlsGroupWidth(412))
    }

    @Test
    fun responsiveBreakpoint_preservesFractionalWidthsAround360() {
        assertEquals(RasvenWidthClass.Compact, RasvenResponsive.widthClass(359.1f))
        assertEquals(RasvenWidthClass.Compact, RasvenResponsive.widthClass(359.9f))
        assertEquals(RasvenWidthClass.Regular, RasvenResponsive.widthClass(360.0f))
        assertEquals(RasvenWidthClass.Regular, RasvenResponsive.widthClass(360.1f))
        assertEquals(16, RasvenResponsive.pageHorizontalMargin(359.9f))
        assertEquals(24, RasvenResponsive.pageHorizontalMargin(360.0f))
    }

    @Test
    fun unsupportedWidth_failsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            RasvenResponsive.widthClass(319)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RasvenResponsive.widthClass(319.99f)
        }
    }

    @Test
    fun directionContract_preservesRtlLtrAndDataIslands() {
        assertEquals(RasvenLayoutDirection.Rtl, RasvenDirectionContract.screenDirection(RasvenLanguage.Persian))
        assertEquals(RasvenLayoutDirection.Ltr, RasvenDirectionContract.screenDirection(RasvenLanguage.English))
        assertEquals(RasvenSemanticEdge.End, RasvenDirectionContract.setupBrandEdge)
        RasvenDataIslandKind.entries.forEach {
            assertEquals(RasvenLayoutDirection.Ltr, RasvenDirectionContract.dataIslandDirection(it))
        }
    }

    @Test
    fun currentDialKeyTypography_usesLiveComponentValues() {
        assertEquals(25, RasvenDialKeyTypography.primary.fontSizeSp)
        assertEquals(22, RasvenDialKeyTypography.primary.lineHeightSp)
        assertEquals(11, RasvenDialKeyTypography.secondary.fontSizeSp)
        assertEquals(16, RasvenDialKeyTypography.secondary.lineHeightSp)
        assertEquals(500, RasvenDialKeyTypography.secondary.fontWeight)
    }

    @Test
    fun effects_preserveRawFigmaValues() {
        assertEquals(24f, RasvenEffects.bottomNavigation.backdropBlur?.radiusPx)
        assertEquals(-10f, RasvenEffects.bottomNavigation.shadows.single().spreadPx)
        assertEquals(18f, RasvenEffects.glassInput.backdropBlur?.radiusPx)
        assertEquals(3f, RasvenEffects.focusHalo.shadows.single().spreadPx)
        assertEquals(14f, RasvenEffects.dialKey.backdropBlur?.radiusPx)
    }

    @Test
    fun gradients_preserveRawTransformAndMatchLiveFigmaAnglesAtConsumerSizes() {
        assertEquals(0.8600000143f, RasvenGradients.pageCanvas.transform.m00)
        assertEquals(0.0799999982f, RasvenGradients.pageCanvas.transform.m12)
        assertEquals(0.7799999714f, RasvenGradients.primaryDefault.transform.m00)
        assertEquals(0.1599999964f, RasvenGradients.primaryPressed.transform.m12)
        assertTrue(RasvenGradients.primaryHover.stops.size == 2)

        assertEquals(94.189765f, RasvenGradients.pageCanvas.transform.figmaCssAngleDegrees(360f, 800f), 0.0001f)
        assertEquals(152.57759f, RasvenGradients.primaryDefault.transform.figmaCssAngleDegrees(328f, 48f), 0.0001f)
        assertEquals(109.71972f, RasvenGradients.primaryDefault.transform.figmaCssAngleDegrees(61f, 48f), 0.0001f)
        assertEquals(114.86159f, RasvenGradients.primaryDefault.transform.figmaCssAngleDegrees(92f, 56f), 0.0001f)
    }

    @Test
    fun gradientLine_usesCssEquivalentMagicCornerGeometry() {
        val identity = RasvenGradientTransform(1f, 0f, 0f, 0f, 1f, 0f)
        val horizontal = identity.linearGradientLine(200f, 100f)
        assertEquals(0f, horizontal.startX, 0.0001f)
        assertEquals(50f, horizontal.startY, 0.0001f)
        assertEquals(200f, horizontal.endX, 0.0001f)
        assertEquals(50f, horizontal.endY, 0.0001f)

        val topToBottom = RasvenGradientTransform(0f, 1f, 0f, -1f, 0f, 1f)
        val vertical = topToBottom.linearGradientLine(200f, 100f)
        assertEquals(100f, vertical.startX, 0.0001f)
        assertEquals(0f, vertical.startY, 0.0001f)
        assertEquals(100f, vertical.endX, 0.0001f)
        assertEquals(100f, vertical.endY, 0.0001f)
    }
    @Test
    fun fontContract_separatesFigmaAndAndroidPersianFamilies() {
        assertEquals("Vazirmatn", RasvenFontContract.figmaPersianFamilyName)
        assertEquals("IRANSansX Pro", RasvenFontContract.androidPersianFamilyName)
        assertEquals("Inter", RasvenFontContract.englishFamilyName)
    }

    @Test
    fun fontScalePolicy_neverShrinksAndCapsGrowthAtTwentyFivePercent() {
        assertEquals(1.00f, RasvenFontScalePolicy.resolve(0.75f), 0.0001f)
        assertEquals(1.00f, RasvenFontScalePolicy.resolve(1.00f), 0.0001f)
        assertEquals(1.10f, RasvenFontScalePolicy.resolve(1.10f), 0.0001f)
        assertEquals(1.25f, RasvenFontScalePolicy.resolve(1.25f), 0.0001f)
        assertEquals(1.25f, RasvenFontScalePolicy.resolve(1.50f), 0.0001f)
        assertEquals(1.25f, RasvenFontScalePolicy.resolve(2.00f), 0.0001f)
    }

}
