package com.rasven.android.product.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.rasven.android.designsystem.foundation.RasvenLanguage

internal enum class RasvenHistoryCallClassification {
    Incoming,
    Outgoing,
    Missed,
}

internal data class RasvenHistoryEntry(
    val id: String,
    val classification: RasvenHistoryCallClassification,
    val displayIdentity: String?,
    val callableDestination: String,
    val timestampEpochMillis: Long,
    val durationSeconds: Long? = null,
)

internal enum class RasvenHistoryFilter {
    All,
    Incoming,
    Outgoing,
    Missed,
}

internal data class RasvenHistoryUiState(
    val selectedFilter: RasvenHistoryFilter,
    val visibleEntries: List<RasvenHistoryEntry>,
)

/** App-owned Rasven history boundary. It deliberately does not query Android's system Call Log. */
internal interface RasvenHistoryRepository {
    fun loadEntries(): List<RasvenHistoryEntry>
    fun record(entry: RasvenHistoryEntry)
}

/**
 * Smallest honest Production boundary for Stage5B4. It starts empty and can receive future Rasven
 * call events without introducing system Call Log permission or fake Product records.
 */
internal class InMemoryRasvenHistoryRepository : RasvenHistoryRepository {
    private val entries = mutableListOf<RasvenHistoryEntry>()

    override fun loadEntries(): List<RasvenHistoryEntry> = entries.toList()

    override fun record(entry: RasvenHistoryEntry) {
        entries.removeAll { it.id == entry.id }
        entries.add(0, entry)
    }
}

internal fun filterHistoryEntries(
    source: List<RasvenHistoryEntry>,
    filter: RasvenHistoryFilter,
): List<RasvenHistoryEntry> = when (filter) {
    RasvenHistoryFilter.All -> source.toList()
    RasvenHistoryFilter.Incoming -> source.filter { it.classification == RasvenHistoryCallClassification.Incoming }
    RasvenHistoryFilter.Outgoing -> source.filter { it.classification == RasvenHistoryCallClassification.Outgoing }
    RasvenHistoryFilter.Missed -> source.filter { it.classification == RasvenHistoryCallClassification.Missed }
}

/** History destinations are preserved exactly; Contacts-only prefix normalization never participates. */
internal fun historyCallableDestination(entry: RasvenHistoryEntry): String = entry.callableDestination

internal fun toPersianHistoryDigits(value: String): String = buildString(value.length) {
    value.forEach { ch -> append(if (ch in '0'..'9') ('۰'.code + (ch - '0')).toChar() else ch) }
}

internal fun formatHistoryMoment(
    timestampEpochMillis: Long,
    nowEpochMillis: Long,
    zoneId: ZoneId,
    language: RasvenLanguage = RasvenLanguage.Persian,
): String {
    val timestamp = Instant.ofEpochMilli(timestampEpochMillis).atZone(zoneId)
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId)
    fun digits(value: String) = if (language == RasvenLanguage.English) value else toPersianHistoryDigits(value)
    return when (timestamp.toLocalDate()) {
        now.toLocalDate() -> digits(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).format(timestamp))
        now.toLocalDate().minusDays(1) -> if (language == RasvenLanguage.English) "Yesterday" else "دیروز"
        else -> digits(DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT).format(timestamp))
    }
}

internal fun formatHistoryTechnicalDestination(destination: String): String {
    val compact = destination.replace(" ", "")
    return if (compact.startsWith("+98") && compact.length == 13 && compact.drop(1).all(Char::isDigit)) {
        "${compact.take(3)} ${compact.substring(3, 6)} ${compact.substring(6, 9)} ${compact.substring(9)}"
    } else destination
}

/** Live EN Figma: HH:mm slot56dp, Yesterday slot64dp; older EN date uses64dp minimum.
 * Its full date can grow intrinsically rather than clipping or shrinking the font. FA stays56dp. */
internal fun historyMomentWidthDp(moment: String, language: RasvenLanguage): Int =
    if (language == RasvenLanguage.English && moment.length > 5) 64 else 56

internal class RasvenHistoryViewModel(
    private val repository: RasvenHistoryRepository = RasvenHistoryRepositoryProvider.current(),
) : ViewModel() {
    private var entries: List<RasvenHistoryEntry> by mutableStateOf(repository.loadEntries())

    var selectedFilter: RasvenHistoryFilter by mutableStateOf(RasvenHistoryFilter.All)
        private set

    val uiState: RasvenHistoryUiState
        get() = RasvenHistoryUiState(
            selectedFilter = selectedFilter,
            visibleEntries = filterHistoryEntries(entries, selectedFilter),
        )

    fun selectFilter(filter: RasvenHistoryFilter) {
        selectedFilter = filter
    }

    fun refresh() {
        entries = repository.loadEntries()
    }

    internal fun record(entry: RasvenHistoryEntry) {
        repository.record(entry)
        refresh()
    }
}
