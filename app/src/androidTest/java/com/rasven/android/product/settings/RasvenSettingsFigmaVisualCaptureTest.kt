package com.rasven.android.product.settings

import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.RasvenProductLanguageScope
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import com.rasven.android.product.setup.RasvenAdvancedSettingsErrors
import com.rasven.android.product.setup.RasvenRegistrationState
import org.junit.Rule
import org.junit.Test

/** Offline Settings reference captures; this fixture cannot register or disconnect an account. */
class RasvenSettingsFigmaVisualCaptureTest {
    @get:Rule val composeRule = createComposeRule()

    private fun captureFixture(width: Int, language: RasvenLanguage) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val physicalWidthPx = target.resources.displayMetrics.widthPixels.toFloat()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(physicalWidthPx / width, 1f)) {
                RasvenTheme {
                    RasvenProductLanguageScope(language) {
                        RasvenSettingsScreen(
                            state = RasvenSettingsUiState(),
                            currentContactPrefix = "9",
                            notificationsEnabled = true,
                            advancedAvailable = true,
                            connectionState = RasvenRegistrationState.Registered,
                            advancedErrors = RasvenAdvancedSettingsErrors(),
                            persianFontFamily = RasvenAndroidFontFamilies.Persian,
                            englishFontFamily = RasvenAndroidFontFamilies.English,
                            onOpenContactPrefix = {}, onContactPrefixChanged = {}, onSaveContactPrefix = {},
                            onOpenCallNotifications = {}, onOpenSystemCallSettings = {},
                            onOpenAdvanced = {},
                            onDisconnect = { error("A visual fixture must never disconnect a real account") },
                            onAdvancedServerChanged = {}, onAdvancedPortChanged = {},
                            onAdvancedUsernameChanged = {}, onAdvancedPasswordChanged = {},
                            onSaveAdvanced = {}, onBackToMain = {}, onNavigationDestination = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val suffix = if (language == RasvenLanguage.Persian) "fa" else "en"
        fun capture(page: String) {
            val imageName = "rasven-settings-v4-$suffix-$width-$page.png"
            val uri = requireNotNull(target.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, imageName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/RasvenSettingsFigmaV2")
                },
            ))
            requireNotNull(target.contentResolver.openOutputStream(uri)).use {
                check(composeRule.onRoot().captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        capture("main")
        composeRule.onNodeWithTag("settings_row_disconnect").performClick()
        composeRule.waitForIdle()
        capture("confirm")
        composeRule.onNodeWithTag("settings_disconnect_cancel_action").performClick()
    }

    @Test fun capturePersian320() = captureFixture(320, RasvenLanguage.Persian)
    @Test fun capturePersian360() = captureFixture(360, RasvenLanguage.Persian)
    @Test fun capturePersian412() = captureFixture(412, RasvenLanguage.Persian)
    @Test fun captureEnglish320() = captureFixture(320, RasvenLanguage.English)
    @Test fun captureEnglish360() = captureFixture(360, RasvenLanguage.English)
    @Test fun captureEnglish412() = captureFixture(412, RasvenLanguage.English)
}
