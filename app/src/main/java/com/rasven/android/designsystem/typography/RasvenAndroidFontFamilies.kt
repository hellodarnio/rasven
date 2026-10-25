package com.rasven.android.designsystem.typography

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.rasven.android.R

/** Android runtime typography assets. Figma remains authored with Vazirmatn. */
internal object RasvenAndroidFontFamilies {
    // Inter 4.1, original unmodified TTFs; bundled OFL notice in assets/licenses.
    val English = FontFamily(
        Font(R.font.inter_regular, weight = FontWeight.Normal),
        Font(R.font.inter_medium, weight = FontWeight.Medium),
        Font(R.font.inter_semibold, weight = FontWeight.SemiBold),
    )
    val Persian = FontFamily(
        Font(R.font.iransansx_regular, weight = FontWeight.Normal),
        Font(R.font.iransansx_medium, weight = FontWeight.Medium),
        Font(R.font.iransansx_demibold, weight = FontWeight.SemiBold),
    )
}
