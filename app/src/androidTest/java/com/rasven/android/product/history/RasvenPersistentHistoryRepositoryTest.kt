package com.rasven.android.product.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasvenPersistentHistoryRepositoryTest {
    @Test fun answeredCallDurationPersistsWhileOldEntriesWithoutDurationRemainReadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("rasven_history_v1", 0).edit().clear().commit()
        try {
            val repository = RasvenPersistentHistoryRepository(context)
            repository.record(RasvenHistoryEntry("old", RasvenHistoryCallClassification.Missed, null, "305", 10L))
            repository.record(RasvenHistoryEntry("answered", RasvenHistoryCallClassification.Incoming, "Ali", "306", 20L, 12L))
            val entries = RasvenPersistentHistoryRepository(context).loadEntries()
            assertEquals(12L, entries.first { it.id == "answered" }.durationSeconds)
            assertEquals(null, entries.first { it.id == "old" }.durationSeconds)
        } finally {
            context.getSharedPreferences("rasven_history_v1", 0).edit().clear().commit()
        }
    }

    @Test fun missedCallSurvivesRepositoryRecreationAndDuplicateIdReplacesRatherThanAdds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("rasven_history_v1", 0).edit().clear().commit()
        try {
            val first = RasvenPersistentHistoryRepository(context)
            val record = RasvenHistoryEntry(
                "stable-incoming-id", RasvenHistoryCallClassification.Missed,
                "آزمایشی", "305", 100L,
            )
            first.record(record)

            val recreated = RasvenPersistentHistoryRepository(context)
            assertEquals(listOf(record), recreated.loadEntries())
            recreated.record(record.copy(timestampEpochMillis = 200L))
            val records = RasvenPersistentHistoryRepository(context).loadEntries()
            assertEquals(1, records.size)
            assertEquals(RasvenHistoryCallClassification.Missed, records.single().classification)
            assertEquals(200L, records.single().timestampEpochMillis)
        } finally {
            context.getSharedPreferences("rasven_history_v1", 0).edit().clear().commit()
        }
    }
}
