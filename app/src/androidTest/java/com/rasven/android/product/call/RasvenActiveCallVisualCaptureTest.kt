package com.rasven.android.product.call

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test

/** Offline-only screenshots; no account, network registration, Telecom call or audio owner. */
class RasvenActiveCallVisualCaptureTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun captureApprovedInCallStatesForPixelReview() {
        var controls by mutableStateOf(RasvenActiveCallControls())
        composeRule.setContent {
            RasvenTheme {
                RasvenActiveCallScreen(
                    destination = "علی رضایی",
                    lineIdentity = "202",
                    connectedAtElapsedMillis = System.nanoTime() / 1_000_000L - 272_000L,
                    controls = controls,
                    persianFontFamily = RasvenAndroidFontFamilies.Persian,
                    onMute = {}, onSpeaker = {}, canHold = true, onHold = {},
                    onKeypad = {}, onDtmf = {}, onEnd = {},
                )
            }
        }
        val output = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        val states = listOf(
            // Capture the user-reported keypad position first for reliable offline device review.
            "keypad" to RasvenActiveCallControls(keypadVisible = true),
            "active" to RasvenActiveCallControls(),
            "mute" to RasvenActiveCallControls(muted = true),
            "speaker" to RasvenActiveCallControls(speaker = true),
            "hold" to RasvenActiveCallControls(held = true),
            "dtmf" to RasvenActiveCallControls(keypadVisible = true, lastDtmfAccepted = '5'),
        )
        states.forEach { (name, state) ->
            composeRule.runOnIdle { controls = state }
            composeRule.waitForIdle()
            FileOutputStream(File(output, "rasven-call-$name.png")).use { stream ->
                check(composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
    }
}
