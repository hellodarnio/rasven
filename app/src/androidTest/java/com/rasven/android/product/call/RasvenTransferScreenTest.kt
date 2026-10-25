package com.rasven.android.product.call

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.designsystem.foundation.LocalRasvenProductLanguage
import com.rasven.android.designsystem.foundation.RasvenLanguage
import com.rasven.android.designsystem.foundation.RasvenTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RasvenTransferScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun captureEnglishTransferStatesForPlacementReview() {
        val call = RasvenTransferCallContext(99L, "Ali Rezaei", System.nanoTime() / 1_000_000L - 272_000L, true)
        val destination = RasvenTransferDestination("Support", "305")
        val states = listOf(
            "transfer-mode" to RasvenTransferUiState.ChoosingMode(call),
            "transfer-destinations" to RasvenTransferUiState.SelectingDestination(call, RasvenTransferMode.Blind),
            "transfer-manual" to RasvenTransferUiState.EnteringDestination(call, RasvenTransferMode.Blind, "305"),
            "transfer-blind-waiting" to RasvenTransferUiState.BlindAwaitingPbx(call, destination),
            "transfer-blind-failed" to RasvenTransferUiState.BlindFailed(call, destination, true),
            "transfer-attended-waiting" to RasvenTransferUiState.AttendedAwaitingConsultation(call, destination),
            "transfer-attended-ready" to RasvenTransferUiState.AttendedReadyToComplete(call, destination),
            "transfer-attended-confirm" to RasvenTransferUiState.ConfirmingCompletion(call, destination),
            "transfer-attended-failed" to RasvenTransferUiState.AttendedFailed(call, destination, true),
            "transfer-attended-unknown" to RasvenTransferUiState.AttendedOutcomeUnknown(call, destination),
        )
        var selected by mutableIntStateOf(0)
        composeRule.setContent {
            RasvenTheme {
                CompositionLocalProvider(
                    LocalRasvenProductLanguage provides RasvenLanguage.English,
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        key(selected) {
                            RasvenTransferScreen(
                                state = states[selected].second,
                                fontFamily = FontFamily.Default,
                                englishFontFamily = FontFamily.Default,
                                configuredDestinations = listOf(destination, RasvenTransferDestination("Sales", "306")),
                                onChooseMode = {}, onEnterManual = {}, onManualChanged = {}, onSubmitManual = {},
                                onSelectDestination = {}, onConfirmConsultation = {}, onRequestComplete = {},
                                onConfirmComplete = {}, onCancelAttended = {}, onReportBusy = {}, onReportNoAnswer = {},
                                onReportBlindStillActive = {}, onReturnToCall = {}, onDismiss = {},
                            )
                        }
                    }
                }
            }
        }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "transfer-ui-review").apply { mkdirs() }
        states.forEachIndexed { index, (name, _) ->
            composeRule.runOnIdle { selected = index }
            composeRule.waitForIdle()
            FileOutputStream(File(output, "$name-before.png")).use { stream ->
                check(composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
    }

    @Test fun transferModeActionsFitCompactAndRegularViewports() {
        var viewportWidth by mutableIntStateOf(320)
        composeRule.setContent {
            RasvenTheme {
                Box(Modifier.width(viewportWidth.dp).fillMaxHeight()) {
                    RasvenTransferScreen(
                        state = RasvenTransferUiState.ChoosingMode(
                            RasvenTransferCallContext(88L, "آزمون تماس", 1_000L, outgoing = true),
                        ),
                        fontFamily = FontFamily.Default,
                        englishFontFamily = FontFamily.Default,
                        configuredDestinations = emptyList(),
                        onChooseMode = {}, onEnterManual = {}, onManualChanged = {}, onSubmitManual = {},
                        onSelectDestination = {}, onConfirmConsultation = {}, onRequestComplete = {},
                        onConfirmComplete = {}, onCancelAttended = {}, onReportBusy = {}, onReportNoAnswer = {},
                        onReportBlindStillActive = {}, onReturnToCall = {}, onDismiss = {},
                    )
                }
            }
        }
        for (width in listOf(320, 360, 412)) {
            composeRule.runOnIdle { viewportWidth = width }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("transfer_choose_blind").assertIsDisplayed()
            composeRule.onNodeWithTag("transfer_choose_attended").assertIsDisplayed()
        }
    }

    @Test fun manualTransferDestinationInputUsesItsOwnSemanticTag() {
        composeRule.setContent {
            RasvenTheme {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    RasvenTransferScreen(
                        state = RasvenTransferUiState.EnteringDestination(
                            RasvenTransferCallContext(89L, "آزمون تماس", 1_000L, outgoing = false),
                            RasvenTransferMode.Blind,
                        ),
                        fontFamily = FontFamily.Default,
                        englishFontFamily = FontFamily.Default,
                        configuredDestinations = emptyList(),
                        onChooseMode = {}, onEnterManual = {}, onManualChanged = {}, onSubmitManual = {},
                        onSelectDestination = {}, onConfirmConsultation = {}, onRequestComplete = {},
                        onConfirmComplete = {}, onCancelAttended = {}, onReportBusy = {}, onReportNoAnswer = {},
                        onReportBlindStillActive = {}, onReturnToCall = {}, onDismiss = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("transfer_manual_input").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_submit_manual").assertIsDisplayed()
    }

    @Test fun callEndWithoutPbxConfirmationShowsUnknownNotSuccessfulTransfer() {
        composeRule.setContent {
            RasvenTheme {
                RasvenTransferScreen(
                    state = RasvenTransferUiState.BlindOutcomeUnknown(
                        RasvenTransferCallContext(90L, "آزمون تماس", 1_000L, outgoing = true),
                        RasvenTransferDestination("آزمون", "305"),
                    ),
                    fontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    configuredDestinations = emptyList(),
                    onChooseMode = {}, onEnterManual = {}, onManualChanged = {}, onSubmitManual = {},
                    onSelectDestination = {}, onConfirmConsultation = {}, onRequestComplete = {},
                    onConfirmComplete = {}, onCancelAttended = {}, onReportBusy = {}, onReportNoAnswer = {},
                    onReportBlindStillActive = {}, onReturnToCall = {}, onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("transfer_result_unknown").assertIsDisplayed()
        composeRule.onNodeWithText("وضعیت انتقال نامشخص").assertIsDisplayed()
    }

    @Test fun destinationPickerContainsManualEntryTransferAndBackActions() {
        composeRule.setContent {
            RasvenTheme {
                RasvenTransferScreen(
                    state = RasvenTransferUiState.SelectingDestination(
                        RasvenTransferCallContext(91L, "آزمون تماس", 1_000L, outgoing = false),
                        RasvenTransferMode.Blind,
                    ),
                    fontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    configuredDestinations = emptyList(),
                    onChooseMode = {}, onEnterManual = {}, onManualChanged = {}, onSubmitManual = {},
                    onSelectDestination = {}, onConfirmConsultation = {}, onRequestComplete = {},
                    onConfirmComplete = {}, onCancelAttended = {}, onReportBusy = {}, onReportNoAnswer = {},
                    onReportBlindStillActive = {}, onReturnToCall = {}, onDismiss = {}, onBackToMode = {},
                )
            }
        }
        composeRule.onNodeWithTag("transfer_manual_input").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_submit_manual").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer_back_mode").assertIsDisplayed()
    }

    @Test fun pendingRecoveryReturnButtonOpensCallControls() {
        var returnRequests = 0
        composeRule.setContent {
            RasvenTheme {
                RasvenTransferScreen(
                    state = RasvenTransferUiState.AttendedCancelAwaitingRecovery(
                        RasvenTransferCallContext(92L, "200", 1_000L, outgoing = true),
                        RasvenTransferDestination("205", "205"),
                        observedPeerHold = false,
                    ),
                    fontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    configuredDestinations = emptyList(),
                    onChooseMode = {}, onEnterManual = {}, onManualChanged = {}, onSubmitManual = {},
                    onSelectDestination = {}, onConfirmConsultation = {}, onRequestComplete = {},
                    onConfirmComplete = {}, onCancelAttended = {}, onReportBusy = {}, onReportNoAnswer = {},
                    onReportBlindStillActive = {}, onReturnToCall = { returnRequests++ }, onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("transfer_cancel_pending_return").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, returnRequests) }
    }

    @Test fun noCancelCodeHidesCancellationAndKeepsCallControlsReachable() {
        val call = RasvenTransferCallContext(93L, "200", 1_000L, outgoing = true)
        val destination = RasvenTransferDestination("205", "205")
        var current by mutableStateOf<RasvenTransferUiState>(RasvenTransferUiState.AttendedAwaitingDestinationTone(call, destination))
        var returnRequests = 0
        var backRequests = 0
        composeRule.setContent {
            RasvenTheme {
                RasvenTransferScreen(
                    state = current,
                    fontFamily = FontFamily.Default,
                    englishFontFamily = FontFamily.Default,
                    configuredDestinations = emptyList(),
                    onChooseMode = {}, onEnterManual = {}, onManualChanged = {}, onSubmitManual = {},
                    onSelectDestination = {}, onConfirmConsultation = {}, onRequestComplete = {},
                    onConfirmComplete = {}, onCancelAttended = {}, canCancelAttended = false,
                    onBackFromCompletion = { backRequests++ }, onReportBusy = {}, onReportNoAnswer = {},
                    onReportBlindStillActive = {}, onReturnToCall = { returnRequests++ }, onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("transfer_attended_dial_tone_cancel").assertDoesNotExist()
        composeRule.onNodeWithTag("transfer_attended_dial_tone_return").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, returnRequests); current = RasvenTransferUiState.ConfirmingCompletion(call, destination) }
        composeRule.onNodeWithTag("transfer_cancel_attended").assertDoesNotExist()
        composeRule.onNodeWithTag("transfer_back_to_consultation").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, backRequests) }
    }
}
