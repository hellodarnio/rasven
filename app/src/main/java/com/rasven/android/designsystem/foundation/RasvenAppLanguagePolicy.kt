package com.rasven.android.designsystem.foundation

/** Pure language-selection contract. English is enabled only when product resource gates are true.
 * The final product default is English by owner request, independent of the device language.
 * A saved explicit choice wins; English is the default once the product locale gate is complete.
 */
internal object RasvenAppLanguagePolicy {
    val completedProductDefault = RasvenLanguage.English

    fun storageTag(language: RasvenLanguage): String = when (language) {
        RasvenLanguage.Persian -> "fa"
        RasvenLanguage.English -> "en"
    }

    fun savedChoice(tag: String?): RasvenLanguage? = when (tag?.trim()?.lowercase(java.util.Locale.ROOT)) {
        "fa", "fa-ir" -> RasvenLanguage.Persian
        "en", "en-us", "en-gb" -> RasvenLanguage.English
        else -> null
    }

    fun resolve(savedTag: String?, englishComplete: Boolean): RasvenLanguage {
        val requested = savedChoice(savedTag) ?: completedProductDefault
        return if (requested == RasvenLanguage.English && !englishComplete) RasvenLanguage.Persian else requested
    }
}
