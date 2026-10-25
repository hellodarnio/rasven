package com.rasven.android.product.call

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rasven.android.R

/** Original270×270 transparent Figma asset, translated65/-85 and viewport-clipped.
 * Decoration only; no inset, control, session or lifecycle ownership.
 */
@Composable
internal fun BoxScope.RasvenEnglishInverseCallAmbient() {
    Box(Modifier.matchParentSize().clipToBounds().testTag("english_inverse_ambient")) {
        Image(
            painterResource(R.drawable.rasven_en_in_call_ambient), null,
            Modifier.align(AbsoluteAlignment.TopRight).offset(x = 65.dp, y = (-85).dp).size(270.dp),
        )
    }
}
