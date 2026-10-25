package com.rasven.android.product

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.designsystem.foundation.RasvenTheme
import com.rasven.android.designsystem.typography.RasvenAndroidFontFamilies
import com.rasven.android.product.call.*
import com.rasven.android.product.contacts.*
import com.rasven.android.product.history.*
import com.rasven.android.product.settings.*
import com.rasven.android.product.setup.*
import java.io.File
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Offline coverage inventory, not a blanket fidelity verdict. Every fixture is synthetic. */
class RasvenAllScreensTextAuditTest {
    @get:Rule val composeRule = createComposeRule()
    private val fa = RasvenAndroidFontFamilies.Persian
    private val en = FontFamily.Default
    private val now = 1_790_000_000_000L
    private val call = RasvenTransferCallContext(88, "علی رضایی", System.nanoTime() / 1_000_000 - 272_000, true)
    private val destination = RasvenTransferDestination("پشتیبانی فنی", "305")
    private val incoming = RasvenIncomingCall(90, "qa-only", "201", "علی رضایی", "204", now)
    private val entries = listOf(
        RasvenHistoryEntry("qa-in", RasvenHistoryCallClassification.Incoming, "علی رضایی", "201", now - 90_000, 272),
        RasvenHistoryEntry("qa-out", RasvenHistoryCallClassification.Outgoing, "شرکت آریا", "305", now - 120_000, 91),
        RasvenHistoryEntry("qa-missed", RasvenHistoryCallClassification.Missed, "رضا محمدی", "306", now - 180_000),
    )

    @Test fun captureSetupTextWithProductionFontAt320And360() {
        val cases = listOf<Pair<String, @Composable () -> Unit>>(
            "welcome" to { RasvenSetupWelcomeScreen(fa, en, {}) },
            "account" to { RasvenAccountInformationScreen(fa, en, {}) },
            "call-settings" to { RasvenCallSettingsScreen(fa, en, {}) },
            "advanced-setup" to { RasvenAdvancedSettingsScreen(RasvenAdvancedSettingsCurrentAccount("qa.invalid", 4569, "204"), fa, en, {}) },
            "permissions-missing" to { RasvenPermissionsEducationScreen(fa, false, {}, {}) },
            "permissions-ready" to { RasvenPermissionsEducationScreen(fa, true, {}, {}) },
            "connecting" to { RasvenConnectingScreen(fa) },
            "success" to { RasvenRegistrationSuccessScreen(fa, {}) },
            "failure" to { RasvenRegistrationFailureScreen(fa, {}, {}) },
        )
        captureCases(cases)
    }

    @Test fun captureDailyUseAndInCallTextWithProductionFontAt320And360() {
        val cases = mutableListOf<Pair<String, @Composable () -> Unit>>()
        cases += "contacts" to { RasvenContactsScreen(true, listOf(RasvenContact("علی رضایی", "201"), RasvenContact("شرکت آریا", "09121234567")), "", fa, en, {}, {}, "9", {}, {}) }
        cases += "contacts-permission" to { RasvenContactsScreen(false, emptyList(), "", fa, en, {}, {}, "9", {}, {}) }
        RasvenHistoryFilter.entries.forEach { filter ->
            cases += "history-${filter.name}" to { RasvenHistoryScreen(RasvenHistoryUiState(filter, filterHistoryEntries(entries, filter)), now, ZoneId.of("Asia/Tehran"), fa, en, {}, {}) }
        }
        RasvenSettingsDestination.entries.forEach { d -> cases += "settings-${d.name}" to { Settings(d) } }
        cases += "incoming-open" to { Incoming(RasvenIncomingCallState.Ringing(incoming), false) }
        cases += "incoming-locked" to { Incoming(RasvenIncomingCallState.Ringing(incoming), true) }
        cases += "incoming-permission" to { Incoming(RasvenIncomingCallState.AwaitingMicrophone(incoming), false) }
        cases += "incoming-missed" to { Incoming(RasvenIncomingCallState.Missed(entries.last()), false) }
        cases += "call-end" to { RasvenCallEndSummaryScreen(RasvenCallEndSummary("علی رضایی", "201", 1_000, 273_000, now), fa, {}) }
        listOf(
            "active" to RasvenActiveCallControls(), "mute" to RasvenActiveCallControls(muted = true),
            "speaker" to RasvenActiveCallControls(speaker = true), "hold" to RasvenActiveCallControls(held = true),
            "keypad" to RasvenActiveCallControls(keypadVisible = true),
            "keypad-dtmf" to RasvenActiveCallControls(keypadVisible = true, lastDtmfAccepted = '5'),
        ).forEach { (name, controls) ->
            cases += name to { RasvenActiveCallScreen("علی رضایی", call.connectedAtElapsedMillis, controls, fa, {}, {}, true, {}, {}, {}, {}, lineIdentity = "204") }
        }
        val transferStates = listOf(
            "transfer-mode" to RasvenTransferUiState.ChoosingMode(call),
            "blind-destinations" to RasvenTransferUiState.SelectingDestination(call, RasvenTransferMode.Blind),
            "attended-destinations" to RasvenTransferUiState.SelectingDestination(call, RasvenTransferMode.Attended),
            "blind-manual" to RasvenTransferUiState.EnteringDestination(call, RasvenTransferMode.Blind, "305"),
            "attended-manual" to RasvenTransferUiState.EnteringDestination(call, RasvenTransferMode.Attended, "305"),
            "transfer-invalid" to RasvenTransferUiState.EnteringDestination(call, RasvenTransferMode.Blind, "", true),
            "blind-waiting" to RasvenTransferUiState.BlindAwaitingPbx(call, destination),
            "blind-unknown" to RasvenTransferUiState.BlindOutcomeUnknown(call, destination),
            "blind-failure" to RasvenTransferUiState.BlindFailed(call, destination, true),
            "attended-waiting" to RasvenTransferUiState.AttendedAwaitingConsultation(call, destination),
            "attended-ready" to RasvenTransferUiState.AttendedReadyToComplete(call, destination),
            "attended-confirm" to RasvenTransferUiState.ConfirmingCompletion(call, destination),
            "attended-completing" to RasvenTransferUiState.CompletionArmed(call, destination),
            "attended-cancelling" to RasvenTransferUiState.AttendedCancelAwaitingRecovery(call, destination, true),
            "attended-returned" to RasvenTransferUiState.AttendedCancelledReturned(call, destination),
            "attended-unknown" to RasvenTransferUiState.AttendedOutcomeUnknown(call, destination),
            "attended-failed" to RasvenTransferUiState.AttendedFailed(call, destination, true),
        )
        transferStates.forEach { (name, state) -> cases += name to { Transfer(state) } }
        captureCases(cases)
    }

    private fun captureCases(cases: List<Pair<String, @Composable () -> Unit>>) {
        var selection by mutableStateOf(0)
        var width by mutableStateOf(360)
        composeRule.setContent {
            // Keep only the synthetic capture host awake; do not change the device timeout.
            val hostView = LocalView.current
            DisposableEffect(hostView) {
                val previous = hostView.keepScreenOn
                hostView.keepScreenOn = true
                onDispose { hostView.keepScreenOn = previous }
            }
            RasvenTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.width(width.dp).fillMaxHeight()) {
                        key(selection, width) { cases[selection].second() }
                    }
                }
            }
        }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-text-audit").apply { mkdirs() }
        for (w in listOf(320, 360)) for (index in cases.indices) {
            composeRule.runOnIdle { selection = index; width = w }
            composeRule.waitForIdle()
            val name = "${cases[index].first}-$w"
            val nodes = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
            val metrics = JSONArray()
            composeRule.runOnIdle {
                nodes.forEach { node ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                    layouts.forEach { text ->
                        val b = node.boundsInRoot
                        metrics.put(JSONObject().put("text", text.layoutInput.text.text)
                            .put("tag", node.config.getOrNull(SemanticsProperties.TestTag) ?: "")
                            .put("lines", text.lineCount).put("overflow", text.hasVisualOverflow)
                            // Some legitimate spans inherit an unspecified (NaN) TextUnit.
                            // JSON cannot represent NaN; retain inheritance as null, not a fake zero.
                            .put("fontSp", text.layoutInput.style.fontSize.value.takeIf { it.isFinite() } ?: JSONObject.NULL)
                            .put("lineSp", text.layoutInput.style.lineHeight.value.takeIf { it.isFinite() } ?: JSONObject.NULL)
                            .put("overflowWidth", text.didOverflowWidth).put("overflowHeight", text.didOverflowHeight)
                            .put("layoutWidthPx", text.size.width).put("layoutHeightPx", text.size.height)
                            .put("ellipsized", (0 until text.lineCount).any { text.isLineEllipsized(it) })
                            .put("align", text.layoutInput.style.textAlign.toString())
                            .put("xPx", b.left).put("yPx", b.top).put("wPx", b.width).put("hPx", b.height))
                    }
                }
            }
            assertTrue("No text semantics in $name", metrics.length() > 0)
            File(output, "$name.json").writeText(metrics.toString(2))
            File(output, "$name.png").outputStream().use {
                check(composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    @Composable private fun Incoming(state: RasvenIncomingCallState, locked: Boolean) {
        RasvenIncomingCallScreen(state, locked, fa, {}, {}, {}, {}, {}, {})
    }

    @Composable private fun Transfer(state: RasvenTransferUiState) {
        RasvenTransferScreen(
            state = state,
            fontFamily = fa,
            englishFontFamily = en,
            configuredDestinations = listOf(destination, RasvenTransferDestination("مدیریت", "306")),
            onChooseMode = {}, onEnterManual = {}, onManualChanged = {}, onSubmitManual = {},
            onSelectDestination = {}, onContinueAttendedDestination = {}, onConfirmConsultation = {},
            onRequestComplete = {}, onConfirmComplete = {}, onCancelAttended = {}, onReportBusy = {},
            onReportNoAnswer = {}, onReportBlindStillActive = {}, onReturnToCall = {}, onDismiss = {},
        )
    }

    @Composable private fun Settings(destination: RasvenSettingsDestination) {
        RasvenSettingsScreen(
            state = RasvenSettingsUiState(destination, "9", RasvenAdvancedSettingsDraft("qa.invalid", "4569", "204")),
            currentContactPrefix = "9", notificationsEnabled = true, advancedAvailable = true,
            connectionState = RasvenRegistrationState.Registered, advancedErrors = RasvenAdvancedSettingsErrors(),
            persianFontFamily = fa, englishFontFamily = en, onOpenContactPrefix = {}, onContactPrefixChanged = {},
            onSaveContactPrefix = {}, onOpenCallNotifications = {}, onOpenSystemCallSettings = {}, onOpenAdvanced = {},
            onAdvancedServerChanged = {}, onAdvancedPortChanged = {}, onAdvancedUsernameChanged = {}, onAdvancedPasswordChanged = {},
            onSaveAdvanced = {}, onBackToMain = {}, onNavigationDestination = {},
        )
    }
}
