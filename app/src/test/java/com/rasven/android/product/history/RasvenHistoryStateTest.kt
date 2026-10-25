package com.rasven.android.product.history

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenHistoryStateTest {
    private val entries = listOf(
        RasvenHistoryEntry("in", RasvenHistoryCallClassification.Incoming, "ورودی", "+989121234567", 1L),
        RasvenHistoryEntry("out", RasvenHistoryCallClassification.Outgoing, "خروجی", "305", 2L),
        RasvenHistoryEntry("miss", RasvenHistoryCallClassification.Missed, null, "+989121111111", 3L),
    )

    @Test fun allReturnsEveryRecordWithoutMutatingSource() {
        val original = entries.toList()
        val result = filterHistoryEntries(entries, RasvenHistoryFilter.All)
        assertEquals(entries, result)
        assertEquals(original, entries)
        assertNotSame(entries, result)
    }

    @Test fun incomingReturnsIncomingOnly() {
        assertEquals(listOf("in"), filterHistoryEntries(entries, RasvenHistoryFilter.Incoming).map { it.id })
    }

    @Test fun outgoingReturnsOutgoingOnly() {
        assertEquals(listOf("out"), filterHistoryEntries(entries, RasvenHistoryFilter.Outgoing).map { it.id })
    }

    @Test fun missedReturnsMissedOnly() {
        assertEquals(listOf("miss"), filterHistoryEntries(entries, RasvenHistoryFilter.Missed).map { it.id })
    }

    @Test fun filterSwitchingIsMutuallyExclusiveAndRestoresSourceRecords() {
        val repository = FakeHistoryRepository(entries)
        val viewModel = RasvenHistoryViewModel(repository)
        assertEquals(RasvenHistoryFilter.All, viewModel.uiState.selectedFilter)
        assertEquals(3, viewModel.uiState.visibleEntries.size)

        viewModel.selectFilter(RasvenHistoryFilter.Incoming)
        assertEquals(listOf("in"), viewModel.uiState.visibleEntries.map { it.id })
        viewModel.selectFilter(RasvenHistoryFilter.Missed)
        assertEquals(listOf("miss"), viewModel.uiState.visibleEntries.map { it.id })
        viewModel.selectFilter(RasvenHistoryFilter.All)

        assertEquals(entries, viewModel.uiState.visibleEntries)
        assertEquals(entries, repository.loadEntries())
    }

    @Test fun selectedFilterSurvivesRepositoryRefresh() {
        val repository = FakeHistoryRepository(entries)
        val viewModel = RasvenHistoryViewModel(repository)
        viewModel.selectFilter(RasvenHistoryFilter.Outgoing)
        repository.record(
            RasvenHistoryEntry("out2", RasvenHistoryCallClassification.Outgoing, null, "306", 4L)
        )
        viewModel.refresh()
        assertEquals(RasvenHistoryFilter.Outgoing, viewModel.uiState.selectedFilter)
        assertEquals(listOf("out2", "out"), viewModel.uiState.visibleEntries.map { it.id })
    }

    @Test fun emptyRepositoryIsSafeForEveryFilter() {
        val viewModel = RasvenHistoryViewModel(FakeHistoryRepository(emptyList()))
        RasvenHistoryFilter.entries.forEach { filter ->
            viewModel.selectFilter(filter)
            assertTrue(viewModel.uiState.visibleEntries.isEmpty())
        }
    }

    @Test fun technicalDestination305IsPreservedExactlyWithoutContactsPrefix() {
        val entry = entries.single { it.id == "out" }
        assertEquals("305", historyCallableDestination(entry))
        assertEquals("305", formatHistoryTechnicalDestination(historyCallableDestination(entry)))
    }

    @Test fun plus98PresentationFormattingDoesNotMutateCallableDestination() {
        val entry = entries.single { it.id == "in" }
        assertEquals("+989121234567", historyCallableDestination(entry))
        assertEquals("+98 912 123 4567", formatHistoryTechnicalDestination(historyCallableDestination(entry)))
        assertEquals("+989121234567", historyCallableDestination(entry))
    }

    @Test fun timeAndYesterdayFormattingAreDeterministicWithInjectedClockAndZone() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-10-04T10:42:00Z").toEpochMilli()
        val sameDay = Instant.parse("2026-10-04T09:15:00Z").toEpochMilli()
        val yesterday = Instant.parse("2026-10-03T23:59:00Z").toEpochMilli()
        val older = Instant.parse("2026-09-30T08:00:00Z").toEpochMilli()

        assertEquals("۰۹:۱۵", formatHistoryMoment(sameDay, now, zone))
        assertEquals("دیروز", formatHistoryMoment(yesterday, now, zone))
        assertEquals("۲۰۲۶/۰۹/۳۰", formatHistoryMoment(older, now, zone))
    }

    private class FakeHistoryRepository(initial: List<RasvenHistoryEntry>) : RasvenHistoryRepository {
        private val data = initial.toMutableList()
        override fun loadEntries(): List<RasvenHistoryEntry> = data.toList()
        override fun record(entry: RasvenHistoryEntry) {
            data.removeAll { it.id == entry.id }
            data.add(0, entry)
        }
    }
}
