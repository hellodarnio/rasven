package com.rasven.android.designsystem.foundation

/** Dedicated presentation preference, never a credential/registration/call-state store. */
internal interface RasvenLanguageChoiceStore {
    fun readTag(): String?
    fun writeTag(tag: String): Boolean
}

internal class RasvenLanguageSelection(
    private val store: RasvenLanguageChoiceStore,
    private val englishComplete: () -> Boolean,
) {
    fun current(): RasvenLanguage = RasvenAppLanguagePolicy.resolve(store.readTag(), englishComplete())

    /** Reject unfinished English without erasing an existing saved explicit choice. */
    fun select(language: RasvenLanguage): Boolean {
        if (language == RasvenLanguage.English && !englishComplete()) return false
        return store.writeTag(RasvenAppLanguagePolicy.storageTag(language))
    }
}
