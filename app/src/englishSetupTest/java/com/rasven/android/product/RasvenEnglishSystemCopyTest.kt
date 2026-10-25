package com.rasven.android.product

import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.R
import com.rasven.android.app.BootstrapActivity
import com.rasven.android.designsystem.foundation.RasvenAppLanguageRepository
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.rasvenString
import java.util.Locale
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Prepared only: isolated resource/presentation checks, no service, permission, PBX or audio start. */
class RasvenEnglishSystemCopyTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun requireIsolatedPreview() {
        assertEquals("com.rasven.android.qa.debug", context.packageName)
        assertTrue(context.resources.getBoolean(R.bool.rasven_english_setup_resources_ready))
    }

    @Test fun englishSystemCopyHasNoPersianFallbackAndPreservesAppMeaning() {
        val en = context.createConfigurationContext(Configuration(context.resources.configuration)
            .apply { setLocale(Locale.forLanguageTag("en")) })
        for (id in listOf(R.string.system_incoming_channel, R.string.system_ongoing_channel,
            R.string.system_presentation_channel, R.string.system_outgoing_title,
            R.string.system_standby_channel, R.string.system_standby_title,
            R.string.system_standby_waiting_network, R.string.system_standby_connecting,
            R.string.system_standby_dnd, R.string.system_standby_ready,
            R.string.system_standby_disconnect, R.string.system_account_save_failed,
            R.string.system_settings_save_failed, R.string.system_disconnect_unconfirmed,
            R.string.system_dnd_on, R.string.system_dnd_off)) {
            val text = en.getString(id)
            assertTrue(text.isNotBlank())
            assertFalse("Persian fallback in EN system copy: $text", text.any { it in '\u0600'..'\u06ff' })
        }
        assertEquals("Disconnect", en.getString(R.string.system_standby_disconnect))
        assertTrue(en.getString(R.string.system_standby_dnd).contains("will not ring"))
    }

    @Test fun completedProductDefaultsToEnglishAndPersianChoiceSurvivesRepositoryRead() {
        assertTrue(context.resources.getBoolean(R.bool.rasven_product_locales_complete))
        assertTrue(RasvenAppLanguageRepository.englishComplete(context))
        val preferences = context.getSharedPreferences("rasven_product_language_v1", android.content.Context.MODE_PRIVATE)
        val hadChoice = preferences.contains("explicit_language")
        val previousChoice = preferences.getString("explicit_language", null)
        try {
            assertTrue(preferences.edit().remove("explicit_language").commit())
            assertEquals(RasvenLanguage.English, RasvenAppLanguageRepository.current(context))
            assertEquals("Unknown number", context.rasvenString(R.string.incoming_call_unknown))
            assertEquals("Disconnect", context.rasvenString(R.string.system_standby_disconnect))

            assertTrue(RasvenAppLanguageRepository.select(context, RasvenLanguage.Persian))
            assertEquals("fa", preferences.getString("explicit_language", null))
            assertEquals(RasvenLanguage.Persian, RasvenAppLanguageRepository.current(context))
            assertEquals("شماره ناشناس", context.rasvenString(R.string.incoming_call_unknown))
            assertEquals("قطع اتصال", context.rasvenString(R.string.system_standby_disconnect))
        } finally {
            val restore = preferences.edit()
            if (hadChoice) restore.putString("explicit_language", previousChoice)
            else restore.remove("explicit_language")
            assertTrue("Restore isolated QA preference", restore.commit())
        }
    }

    @Test fun realBootstrapHostLoadsExplicitPersianWithoutLosingActivityResultOwner() {
        val preferences = context.getSharedPreferences("rasven_product_language_v1", android.content.Context.MODE_PRIVATE)
        val hadChoice = preferences.contains("explicit_language")
        val previousChoice = preferences.getString("explicit_language", null)
        var scenario: ActivityScenario<BootstrapActivity>? = null
        try {
            assertTrue(preferences.edit().putString("explicit_language", "fa").commit())
            scenario = ActivityScenario.launch(BootstrapActivity::class.java)
            scenario.onActivity { activity ->
                assertEquals(RasvenLanguage.Persian, RasvenAppLanguageRepository.current(activity))
                assertEquals("یک‌بار حساب سازمانی را تنظیم کنید.", activity.rasvenString(R.string.setup_subtitle))
            }
        } finally {
            scenario?.close()
            val restore = preferences.edit()
            if (hadChoice) restore.putString("explicit_language", previousChoice)
            else restore.remove("explicit_language")
            assertTrue("Restore isolated QA preference", restore.commit())
        }
    }
}
