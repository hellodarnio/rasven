package com.rasven.android.product.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** App-private offline History. Stable IDs make repeated terminal callbacks idempotent. */
internal class RasvenPersistentHistoryRepository(context: Context) : RasvenHistoryRepository {
    private val preferences = context.applicationContext.getSharedPreferences("rasven_history_v1", Context.MODE_PRIVATE)
    private val lock = Any()

    override fun loadEntries(): List<RasvenHistoryEntry> = synchronized(lock) {
        val serialized = preferences.getString(KEY, null) ?: return emptyList()
        try {
            val array = JSONArray(serialized)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val classification = runCatching {
                        RasvenHistoryCallClassification.valueOf(item.optString("classification"))
                    }.getOrNull() ?: continue
                    if (id.isBlank()) continue
                    add(RasvenHistoryEntry(
                        id = id,
                        classification = classification,
                        displayIdentity = item.optString("displayIdentity").ifBlank { null },
                        callableDestination = item.optString("callableDestination"),
                        timestampEpochMillis = item.optLong("timestampEpochMillis"),
                        durationSeconds = item.optLong("durationSeconds", -1L).takeIf { it >= 0L },
                    ))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun record(entry: RasvenHistoryEntry) = synchronized(lock) {
        val entries = loadEntries().filterNot { it.id == entry.id }
        val array = JSONArray()
        (listOf(entry) + entries).take(MAX_ENTRIES).forEach { value ->
            array.put(JSONObject().apply {
                put("id", value.id)
                put("classification", value.classification.name)
                put("displayIdentity", value.displayIdentity.orEmpty())
                put("callableDestination", value.callableDestination)
                put("timestampEpochMillis", value.timestampEpochMillis)
                value.durationSeconds?.let { put("durationSeconds", it) }
            })
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "entries"
        private const val MAX_ENTRIES = 500
    }
}

internal object RasvenHistoryRepositoryProvider {
    @Volatile private var production: RasvenHistoryRepository? = null
    fun initialize(context: Context) {
        if (production == null) synchronized(this) {
            if (production == null) production = RasvenPersistentHistoryRepository(context)
        }
    }
    fun current(): RasvenHistoryRepository = production ?: InMemoryRasvenHistoryRepository()
}
