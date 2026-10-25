package com.rasven.android.product.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.designsystem.foundation.RasvenTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RasvenHistoryScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-04T10:42:00Z").toEpochMilli()
    private val fixtures = listOf(
        RasvenHistoryEntry(
            id = "incoming",
            classification = RasvenHistoryCallClassification.Incoming,
            displayIdentity = "علی رضایی",
            callableDestination = "+989121234567",
            timestampEpochMillis = Instant.parse("2026-10-04T10:42:00Z").toEpochMilli(),
        ),
        RasvenHistoryEntry(
            id = "outgoing",
            classification = RasvenHistoryCallClassification.Outgoing,
            displayIdentity = "پشتیبانی فروش",
            callableDestination = "305",
            timestampEpochMillis = Instant.parse("2026-10-04T09:15:00Z").toEpochMilli(),
        ),
        RasvenHistoryEntry(
            id = "missed",
            classification = RasvenHistoryCallClassification.Missed,
            displayIdentity = null,
            callableDestination = "+989121111111",
            timestampEpochMillis = Instant.parse("2026-10-03T18:00:00Z").toEpochMilli(),
        ),
    )

    private fun render(
        initialFilter: RasvenHistoryFilter = RasvenHistoryFilter.All,
        entries: List<RasvenHistoryEntry> = fixtures,
        onNavigation: (RasvenNavDestination) -> Unit = {},
    ) {
        composeRule.setContent {
            RasvenTheme {
                var filter by remember { mutableStateOf(initialFilter) }
                RasvenHistoryScreen(
                    state = RasvenHistoryUiState(filter, filterHistoryEntries(entries, filter)),
                    nowEpochMillis = now,
                    zoneId = zone,
                    persianFontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    onFilterSelected = { filter = it },
                    onNavigationDestination = onNavigation,
                )
            }
        }
    }

    @Test fun allFrameShowsApprovedShellAllThreeClassificationsAndHistoryCurrent() {
        render()
        composeRule.onNodeWithText("سوابق تماس").assertIsDisplayed()
        composeRule.onNodeWithText("تماس‌های انجام‌شده در این دستگاه").assertIsDisplayed()
        composeRule.onNodeWithTag("history_filter_all").assertIsSelected()
        composeRule.onNodeWithTag("history_row_incoming").assertIsDisplayed()
        composeRule.onNodeWithTag("history_row_outgoing").assertIsDisplayed()
        composeRule.onNodeWithTag("history_row_missed").assertIsDisplayed()
        composeRule.onNodeWithTag("history_bottom_navigation").assertIsDisplayed()
        composeRule.onNodeWithText("سوابق").assertIsDisplayed()
    }

    @Test fun incomingFrameSelectsIncomingAndHidesOtherClassifications() {
        render(initialFilter = RasvenHistoryFilter.Incoming)
        composeRule.onNodeWithTag("history_filter_incoming").assertIsSelected()
        composeRule.onNodeWithTag("history_row_incoming").assertIsDisplayed()
        composeRule.onNodeWithTag("history_row_outgoing").assertDoesNotExist()
        composeRule.onNodeWithTag("history_row_missed").assertDoesNotExist()
        composeRule.onNodeWithText("+98 912 123 4567", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun outgoingFrameSelectsOutgoingAndPreservesTechnical305() {
        render(initialFilter = RasvenHistoryFilter.Outgoing)
        composeRule.onNodeWithTag("history_filter_outgoing").assertIsSelected()
        composeRule.onNodeWithTag("history_row_outgoing").assertIsDisplayed()
        composeRule.onNodeWithTag("history_row_incoming").assertDoesNotExist()
        composeRule.onNodeWithTag("history_row_missed").assertDoesNotExist()
        composeRule.onNodeWithTag("history_destination_outgoing", useUnmergedTree = true)
            .assertIsDisplayed().assertTextContains("305", substring = true)
    }

    @Test fun missedFrameSelectsMissedAndUsesApprovedUnknownAndMissedCopy() {
        render(initialFilter = RasvenHistoryFilter.Missed)
        composeRule.onNodeWithTag("history_filter_missed").assertIsSelected()
        composeRule.onNodeWithTag("history_row_missed").assertIsDisplayed()
        composeRule.onNodeWithTag("history_row_incoming").assertDoesNotExist()
        composeRule.onNodeWithTag("history_row_outgoing").assertDoesNotExist()
        composeRule.onNodeWithText("شماره ناشناس", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("تماس بی‌پاسخ", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun chipSwitchingIsOneScreenAndRestoresAllRecords() {
        render()
        composeRule.onNodeWithTag("history_filter_incoming").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history_row_incoming").assertIsDisplayed()
        composeRule.onNodeWithTag("history_row_outgoing").assertDoesNotExist()

        composeRule.onNodeWithTag("history_filter_missed").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history_row_missed").assertIsDisplayed()
        composeRule.onNodeWithTag("history_row_incoming").assertDoesNotExist()

        composeRule.onNodeWithTag("history_filter_all").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history_row_incoming").assertIsDisplayed()
        composeRule.onNodeWithTag("history_row_outgoing").assertIsDisplayed()
        composeRule.onNodeWithTag("history_row_missed").assertIsDisplayed()
    }

    @Test fun historyNavigationCanReachHomeContactsAndSettings() {
        var selected: RasvenNavDestination? = null
        render(onNavigation = { selected = it })
        composeRule.onNodeWithText("خانه").performClick()
        composeRule.runOnIdle { assertEquals(RasvenNavDestination.Home, selected) }
        composeRule.onNodeWithText("مخاطبین").performClick()
        composeRule.runOnIdle { assertEquals(RasvenNavDestination.Contacts, selected) }
        composeRule.onNodeWithText("تنظیمات").performClick()
        composeRule.runOnIdle { assertEquals(RasvenNavDestination.Settings, selected) }
    }

    @Test fun emptyHistoryKeepsHeaderFiltersAndNavigationWithoutInventedEmptyState() {
        render(entries = emptyList())
        composeRule.onNodeWithText("سوابق تماس").assertIsDisplayed()
        composeRule.onNodeWithTag("history_filter_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("history_list").assertIsDisplayed()
        composeRule.onNodeWithTag("history_bottom_navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("history_row_incoming").assertDoesNotExist()
    }
}
