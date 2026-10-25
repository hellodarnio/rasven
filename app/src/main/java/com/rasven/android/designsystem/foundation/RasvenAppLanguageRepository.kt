package com.rasven.android.designsystem.foundation

import android.content.Context
import android.content.res.Configuration
import com.rasven.android.R
import java.util.Locale

/** UI/resource-local locale ownership. No Activity recreation, global locale API,
 * credential access, re-registration or transport mutation is performed here.
 */
internal object RasvenAppLanguageRepository {
    private const val PREFERENCES = "rasven_product_language_v1"
    private const val CHOICE = "explicit_language"

    fun englishComplete(context: Context): Boolean =
        context.resources.getBoolean(R.bool.rasven_product_locales_complete) &&
            context.resources.getBoolean(R.bool.rasven_english_setup_resources_ready)

    fun current(context: Context): RasvenLanguage {
        // A completed product defaults to English while preserving an explicit user choice.
        if (!englishComplete(context)) return RasvenLanguage.Persian
        return selection(context).current()
    }

    fun select(context: Context, language: RasvenLanguage): Boolean = selection(context).select(language)

    private fun selection(context: Context): RasvenLanguageSelection {
        val app = context.applicationContext
        return RasvenLanguageSelection(object : RasvenLanguageChoiceStore {
            override fun readTag(): String? = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(CHOICE, null)
            override fun writeTag(tag: String): Boolean = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(CHOICE, tag).commit()
        }, { englishComplete(app) })
    }

    fun resourceContext(context: Context): Context {
        val app = context.applicationContext
        val locale = Locale.forLanguageTag(RasvenAppLanguagePolicy.storageTag(current(app)))
        val locales = app.resources.configuration.locales
        if (!locales.isEmpty && locales[0].language == locale.language) return app
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(locale) })
    }
}

/** Uses saved app-language ownership for app-supplied OS text, not the phone's locale. */
internal fun Context.rasvenString(id: Int, vararg arguments: Any): String =
    RasvenAppLanguageRepository.resourceContext(this).getString(id, *arguments)
