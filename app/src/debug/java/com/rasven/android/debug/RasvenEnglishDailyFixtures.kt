package com.rasven.android.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rasven.android.designsystem.components.RasvenNavDestination
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import com.rasven.android.product.contacts.*
import com.rasven.android.product.history.*
import com.rasven.android.product.home.*
import com.rasven.android.product.settings.*
import com.rasven.android.product.setup.RasvenAdvancedSettingsErrors
import com.rasven.android.product.setup.RasvenRegistrationState
import java.time.Instant
import java.time.ZoneId

/** Synthetic values only. No view models/repositories/runtime, OS settings or real credentials. */
internal val englishDailyFixtureStages = listOf(
    "home", "home-dnd", "home-number", "home-dnd-number", "home-disconnected", "home-reconnecting",
    "contacts", "contacts-permission", "history-all", "history-incoming", "history-outgoing", "history-missed",
    "settings", "settings-prefix", "settings-notifications", "settings-advanced",
)

@Composable internal fun RasvenEnglishDailyFixture(
    stage: String,
    onSelectLanguage: ((RasvenLanguage) -> Boolean)? = null,
    onNavigate: (String) -> Unit = {},
) {
    val fa = RasvenAndroidFontFamilies.Persian
    val en = RasvenAndroidFontFamilies.English
    val nav: (RasvenNavDestination) -> Unit = { onNavigate(when (it) {
        RasvenNavDestination.Home -> "home"
        RasvenNavDestination.Contacts -> "contacts"
        RasvenNavDestination.History -> "history-all"
        RasvenNavDestination.Settings -> "settings"
    }) }
    when {
        stage.startsWith("home") -> {
            var number by remember(stage) { mutableStateOf(if (stage.contains("number")) "305" else "") }
            var dnd by remember(stage) { mutableStateOf(stage.contains("dnd")) }
            val connection = when (stage) {
                "home-disconnected" -> RasvenRegistrationState.Unregistered
                "home-reconnecting" -> RasvenRegistrationState.Registering
                else -> RasvenRegistrationState.Registered
            }
            RasvenHomeDialerScreen(RasvenHomeDialerUiState(connection, "204", dnd, number), fa, en,
                onDestinationChange = { number = it }, onDialKey = { number = appendDialCharacter(number, it) },
                onDelete = { number = deleteLastDialCharacter(number) }, onDoNotDisturbChanged = { dnd = it },
                onStartOutgoingCall = {}, onReconnect = { onNavigate("home") }, onNavigationDestination = nav)
        }
        stage.startsWith("contacts") -> {
            var granted by remember(stage) { mutableStateOf(stage == "contacts") }
            var query by remember(stage) { mutableStateOf("") }
            val contacts = listOf(RasvenContact("Alex Taylor", "+989121234567"), RasvenContact("Support", "305"))
            RasvenContactsScreen(granted, filterContacts(contacts, query), query, fa, en,
                onQueryChange = { query = it }, onRequestPermission = { granted = true }, contactPrefix = "9",
                onStartOutgoingCall = {}, onNavigationDestination = nav)
        }
        stage.startsWith("history") -> {
            var filter by remember(stage) { mutableStateOf(when (stage) {
                "history-incoming" -> RasvenHistoryFilter.Incoming
                "history-outgoing" -> RasvenHistoryFilter.Outgoing
                "history-missed" -> RasvenHistoryFilter.Missed
                else -> RasvenHistoryFilter.All
            }) }
            val now = Instant.parse("2026-10-11T10:00:00Z").toEpochMilli()
            val entries = listOf(
                RasvenHistoryEntry("incoming", RasvenHistoryCallClassification.Incoming, "Alex Taylor", "+989121234567", now - 60_000),
                RasvenHistoryEntry("outgoing", RasvenHistoryCallClassification.Outgoing, "Support", "305", now - 120_000),
                RasvenHistoryEntry("missed", RasvenHistoryCallClassification.Missed, null, "306", now - 86_400_000),
                RasvenHistoryEntry("older", RasvenHistoryCallClassification.Missed, "Earlier call", "307", now - 172_800_000),
            )
            RasvenHistoryScreen(RasvenHistoryUiState(filter, filterHistoryEntries(entries, filter)), now, ZoneId.of("UTC"),
                fa, en, { filter = it }, nav)
        }
        stage.startsWith("settings") -> {
            var state by remember(stage) { mutableStateOf(RasvenSettingsUiState(
                destination = when (stage) {
                    "settings-prefix" -> RasvenSettingsDestination.ContactPrefix
                    "settings-notifications" -> RasvenSettingsDestination.CallNotifications
                    "settings-advanced" -> RasvenSettingsDestination.Advanced
                    else -> RasvenSettingsDestination.Main
                }, contactPrefixDraft = "9",
                advancedDraft = RasvenAdvancedSettingsDraft(server = "qa.invalid", port = "4569", username = "204"),
            )) }
            fun draft(change: RasvenAdvancedSettingsDraft.() -> RasvenAdvancedSettingsDraft) {
                state = state.copy(advancedDraft = state.advancedDraft.change())
            }
            RasvenSettingsScreen(state, "9", notificationsEnabled = false, advancedAvailable = true,
                connectionState = RasvenRegistrationState.Registered, advancedErrors = RasvenAdvancedSettingsErrors(),
                persianFontFamily = fa, englishFontFamily = en,
                onOpenContactPrefix = { onNavigate("settings-prefix") },
                onContactPrefixChanged = { state = state.copy(contactPrefixDraft = it) },
                onSaveContactPrefix = { onNavigate("settings") },
                onOpenCallNotifications = { onNavigate("settings-notifications") }, onOpenSystemCallSettings = {},
                onOpenAdvanced = { onNavigate("settings-advanced") }, onDisconnect = {},
                onAdvancedServerChanged = { draft { copy(server = it) } },
                onAdvancedPortChanged = { draft { copy(port = it) } },
                onAdvancedUsernameChanged = { draft { copy(username = it) } },
                onAdvancedPasswordChanged = { draft { copy(passwordReplacement = it) } },
                onTransferProfileChanged = { draft { copy(transferProfile = it) } },
                onSaveAdvanced = { onNavigate("settings") }, onBackToMain = { onNavigate("settings") },
                onNavigationDestination = nav, onSelectLanguage = onSelectLanguage)
        }
        else -> error("Unknown offline daily fixture: $stage")
    }
}
