package com.rasven.android.product.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.rasven.android.designsystem.foundation.RasvenProductLayout
import com.rasven.android.product.setup.RasvenAdvancedSettingsCurrentAccount
import com.rasven.android.product.setup.RasvenAdvancedSettingsErrors
import com.rasven.android.product.setup.RasvenAdvancedSettingsUpdate
import com.rasven.android.product.setup.RasvenAdvancedSettingsValidation
import com.rasven.android.product.call.RasvenPbxTransferProfile
import com.rasven.android.product.call.RasvenPbxTransferProfileErrors

/** Stage5B5 is one Bottom Navigation destination with three internal sub-pages. */
internal enum class RasvenSettingsDestination {
    Main,
    ContactPrefix,
    CallNotifications,
    Advanced,
}


internal data class RasvenSettingsResponsiveGeometry(
    val pageMarginDp: Int,
    val contentWidthDp: Int,
)

/** Final Product responsive contract: 320→16/288, 360→24/312, 412→24/364. */
internal fun settingsResponsiveGeometry(viewportWidthDp: Int): RasvenSettingsResponsiveGeometry {
    val pageMargin = if (viewportWidthDp < RasvenProductLayout.responsiveBaseThreshold) {
        RasvenProductLayout.pageMarginCompact
    } else {
        RasvenProductLayout.pageMarginBase
    }
    val contentWidth = (viewportWidthDp - pageMargin * 2).coerceIn(
        RasvenProductLayout.contentMinWidth,
        RasvenProductLayout.destinationInputMaxWidth,
    )
    return RasvenSettingsResponsiveGeometry(pageMargin, contentWidth)
}

internal data class RasvenAdvancedSettingsDraft(
    val server: String = "",
    val port: String = "",
    val username: String = "",
    val passwordReplacement: String = "",
    val attemptedSave: Boolean = false,
    val transferProfile: RasvenPbxTransferProfile = RasvenPbxTransferProfile.issabelAsteriskDefaults,
) {
    override fun toString(): String =
        "RasvenAdvancedSettingsDraft(server=[redacted], port=$port, username=[redacted], passwordReplacement=[redacted], attemptedSave=$attemptedSave)"
}

internal data class RasvenSettingsUiState(
    val destination: RasvenSettingsDestination = RasvenSettingsDestination.Main,
    val contactPrefixDraft: String = "",
    val advancedDraft: RasvenAdvancedSettingsDraft = RasvenAdvancedSettingsDraft(),
)

/**
 * Lifecycle-retained UI state owner for the complete Settings family.
 * Contacts prefix and account data remain authoritative in their existing domain owners; this owner
 * holds only edit drafts and internal Settings navigation.
 */
internal class RasvenSettingsViewModel : ViewModel() {
    var uiState: RasvenSettingsUiState by mutableStateOf(RasvenSettingsUiState())
        private set

    fun openMain() {
        uiState = uiState.copy(
            destination = RasvenSettingsDestination.Main,
            advancedDraft = uiState.advancedDraft.copy(passwordReplacement = "", attemptedSave = false),
        )
    }

    fun openContactPrefix(currentPrefix: String) {
        uiState = uiState.copy(
            destination = RasvenSettingsDestination.ContactPrefix,
            contactPrefixDraft = currentPrefix,
        )
    }

    fun updateContactPrefixDraft(value: String) {
        if (uiState.destination != RasvenSettingsDestination.ContactPrefix) return
        // Existing Stage5A3 contract preserves exactly what the user enters. No new sanitization.
        uiState = uiState.copy(contactPrefixDraft = value)
    }

    /** Moves to Main before invoking the boundary so repeated queued clicks cannot double-commit. */
    fun saveContactPrefix(onCommit: (String) -> Unit): Boolean {
        if (uiState.destination != RasvenSettingsDestination.ContactPrefix) return false
        val value = uiState.contactPrefixDraft
        uiState = uiState.copy(destination = RasvenSettingsDestination.Main)
        onCommit(value)
        return true
    }

    fun openCallNotifications() {
        uiState = uiState.copy(destination = RasvenSettingsDestination.CallNotifications)
    }

    fun openAdvanced(
        currentAccount: RasvenAdvancedSettingsCurrentAccount,
        transferProfile: RasvenPbxTransferProfile = RasvenPbxTransferProfile.issabelAsteriskDefaults,
    ) {
        uiState = uiState.copy(
            destination = RasvenSettingsDestination.Advanced,
            advancedDraft = RasvenAdvancedSettingsDraft(
                server = currentAccount.server,
                port = currentAccount.port.toString(),
                username = currentAccount.username,
                passwordReplacement = "",
                transferProfile = transferProfile,
            ),
        )
    }

    fun updateAdvancedServer(value: String) = updateAdvanced { copy(server = value) }
    fun updateAdvancedPort(value: String) = updateAdvanced { copy(port = value) }
    fun updateAdvancedUsername(value: String) = updateAdvanced { copy(username = value) }
    fun updateAdvancedPasswordReplacement(value: String) = updateAdvanced { copy(passwordReplacement = value) }
    fun updateTransferProfile(profile: RasvenPbxTransferProfile) = updateAdvanced { copy(transferProfile = profile.withCode()) }
    fun restoreTransferProfileDefaults() = updateTransferProfile(RasvenPbxTransferProfile.issabelAsteriskDefaults)
    fun transferProfileErrors(): RasvenPbxTransferProfileErrors = uiState.advancedDraft.transferProfile.validate()
    fun validatedTransferProfile(): RasvenPbxTransferProfile? = uiState.advancedDraft.transferProfile.takeIf { it.isValid() }

    fun advancedErrors(): RasvenAdvancedSettingsErrors {
        val draft = uiState.advancedDraft
        return if (draft.attemptedSave) {
            RasvenAdvancedSettingsValidation.validate(draft.server, draft.port, draft.username)
        } else {
            RasvenAdvancedSettingsErrors()
        }
    }

    /** Returns an existing validator-owned update; invalid drafts stay on the Advanced page. */
    fun validatedAdvancedUpdate(): RasvenAdvancedSettingsUpdate? {
        if (uiState.destination != RasvenSettingsDestination.Advanced) return null
        val draft = uiState.advancedDraft.copy(attemptedSave = true)
        uiState = uiState.copy(advancedDraft = draft)
        return RasvenAdvancedSettingsValidation.validatedUpdate(
            server = draft.server,
            port = draft.port,
            username = draft.username,
            passwordReplacement = draft.passwordReplacement,
        )
    }

    fun completeAdvancedSave() {
        if (uiState.destination == RasvenSettingsDestination.Advanced) {
            uiState = uiState.copy(
                destination = RasvenSettingsDestination.Main,
                advancedDraft = uiState.advancedDraft.copy(passwordReplacement = "", attemptedSave = false),
            )
        }
    }

    fun backToMain(): Boolean {
        if (uiState.destination == RasvenSettingsDestination.Main) return false
        openMain()
        return true
    }

    private inline fun updateAdvanced(transform: RasvenAdvancedSettingsDraft.() -> RasvenAdvancedSettingsDraft) {
        if (uiState.destination != RasvenSettingsDestination.Advanced) return
        uiState = uiState.copy(advancedDraft = uiState.advancedDraft.transform())
    }
}
