package com.rasven.android.debug

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.RasvenProductLanguageScope
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import com.rasven.android.product.setup.*

/** Synthetic/offline English setup/daily/call host. No repositories, permissions, Telecom or PBX gateway.
 * Account/prefix callbacks exercise UI navigation only; connected/failure fixtures are explicit.
 * This Activity is neither exported nor present in release. Only QA preview resources enable it.
 */
class RasvenEnglishSetupQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageName != "com.rasven.android.qa.debug" || !resources.getBoolean(R.bool.rasven_english_setup_resources_ready)) {
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val initial = intent.getStringExtra("stage").takeIf { it in STAGES } ?: "welcome"
        setContent {
            var stage by rememberSaveable { mutableStateOf(initial) }
            var ready by rememberSaveable { mutableStateOf(false) }
            RasvenTheme {
                RasvenSetupLanguageScope(RasvenLanguage.English) {
                    val fa = RasvenAndroidFontFamilies.Persian
                    val en = RasvenAndroidFontFamilies.English
                    when (stage) {
                        "welcome" -> RasvenSetupWelcomeScreen(fa, en, { stage = "account" })
                        "account" -> RasvenAccountInformationScreen(fa, en, { stage = "call-settings" })
                        "call-settings" -> RasvenCallSettingsScreen(fa, en, { stage = "permissions" })
                        "advanced" -> RasvenAdvancedSettingsScreen(RasvenAdvancedSettingsCurrentAccount("qa.invalid", 4569, "204"), fa, en, { stage = "permissions" })
                        "permissions" -> RasvenPermissionsEducationScreen(fa, ready, {}, { ready = true })
                        "connecting" -> RasvenConnectingScreen(fa)
                        "success" -> RasvenRegistrationSuccessScreen(fa, { stage = "welcome" })
                        "failure" -> RasvenRegistrationFailureScreen(fa, { stage = "connecting" }, { stage = "account" })
                        "language" -> {
                            var selectedLanguage by rememberSaveable { mutableStateOf(RasvenLanguage.English) }
                            RasvenProductLanguageScope(selectedLanguage) {
                                RasvenEnglishDailyFixture("settings", onSelectLanguage = {
                                    selectedLanguage = it
                                    true // synthetic preview only, never the real language/account store
                                })
                            }
                        }
                        else -> if (stage in englishCallFixtureStages) RasvenEnglishCallFixture(stage) { stage = it }
                            else RasvenEnglishDailyFixture(stage) { stage = it }
                    }
                }
            }
        }
    }

    private companion object {
        val STAGES = setOf("welcome", "account", "call-settings", "advanced", "permissions", "connecting", "success", "failure", "language") +
            englishDailyFixtureStages + englishCallFixtureStages
    }
}
