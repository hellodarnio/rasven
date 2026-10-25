package com.rasven.android.product.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Product-level terminal category remains sanitized; raw protocol details never reach Product UI. */
internal enum class RasvenRegistrationErrorCategory {
    Unknown,
}

internal sealed interface RasvenRegistrationState {
    data object Idle : RasvenRegistrationState
    data object Registering : RasvenRegistrationState
    data object Registered : RasvenRegistrationState
    data object Unregistered : RasvenRegistrationState
    data class Failed(val category: RasvenRegistrationErrorCategory) : RasvenRegistrationState
}

internal sealed interface RasvenRegistrationResult {
    data object Registered : RasvenRegistrationResult
    data class Failed(val category: RasvenRegistrationErrorCategory) : RasvenRegistrationResult
}

/**
 * Small registration boundary for the Setup state family. Implementations own protocol/network
 * execution and must never log account secrets. Terminal callbacks are expected on the main thread
 * until an approved coroutine-backed registration engine is introduced.
 */
internal fun interface RasvenRegistrationGateway {
    fun start(account: RasvenValidatedAccount, onResult: (RasvenRegistrationResult) -> Unit)

    /** Account/server changes invalidate any transport that still owns the previous credential. */
    fun invalidate() = Unit
    /** Production owns classified transient recovery; default fixtures retain existing teardown. */
    val supportsAutomaticRecovery: Boolean get() = false
    fun onRegistrationFailed() = invalidate()

    /** Explicit disconnect may perform a protocol release before invalidating local transport. */
    fun disconnect(onComplete: (Boolean) -> Unit) {
        invalidate()
        onComplete(false)
    }

    /** UI lifetime ends independently from explicitly enabled incoming-call standby. */
    fun detachObserver() = Unit
}

/** Fail-closed fallback used only when no runtime registration gateway is available. */
internal object RasvenUnavailableRegistrationGateway : RasvenRegistrationGateway {
    override fun start(account: RasvenValidatedAccount, onResult: (RasvenRegistrationResult) -> Unit) {
        onResult(RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown))
    }
}

/**
 * Lifecycle-retained owner for explicit registration attempts. Rendering never starts registration.
 * Attempt identity protects a newer retry from stale terminal callbacks belonging to an older try.
 */
internal class RasvenRegistrationViewModel private constructor(
    private val gatewayProvider: () -> RasvenRegistrationGateway,
) : ViewModel() {
    constructor() : this({ RasvenRuntimeRegistrationGateway.current() })

    private enum class AttemptOrigin { Setup, HomeRecovery }

    private data class AttemptLaunch(
        val id: Long,
        val account: RasvenValidatedAccount,
    )

    private var activeAccount: RasvenValidatedAccount? = null
    private var setupRepository: RasvenSecureSetupRepository? = null
    private var restoreChecked = false
    private var attemptSequence: Long = 0
    private var currentAttemptOrigin: AttemptOrigin? = null

    var state: RasvenRegistrationState by mutableStateOf(RasvenRegistrationState.Idle)
        private set

    internal var startCount: Int = 0
        private set

    internal var currentAttemptId: Long? = null
        private set

    internal constructor(gateway: RasvenRegistrationGateway) : this({ gateway })

    fun attachSetupRepository(repository: RasvenSecureSetupRepository) {
        synchronized(this) { setupRepository = repository }
    }

    /** Called only from Activity startup; a retained owner cannot reconnect twice on recreation. */
    fun restoreSavedAccountOnce(account: RasvenValidatedAccount?, autoConnect: Boolean = true) {
        val shouldStart = synchronized(this) {
            if (restoreChecked) false else {
                restoreChecked = true
                if (account != null && !autoConnect && state is RasvenRegistrationState.Idle) {
                    activeAccount = account
                    state = RasvenRegistrationState.Unregistered
                }
                account != null && autoConnect && state is RasvenRegistrationState.Idle
            }
        }
        if (shouldStart) startOnce(account ?: return)
    }

    /** Explicit user disconnect keeps the encrypted account for the Home reconnect action. */
    fun disconnectFromSettings(onReleaseResult: (Boolean) -> Unit = {}): Boolean {
        val shouldInvalidate = synchronized(this) {
            if (state !is RasvenRegistrationState.Registered) return false
            state = RasvenRegistrationState.Unregistered
            currentAttemptId = null
            currentAttemptOrigin = null
            true
        }
        if (shouldInvalidate) gatewayProvider().disconnect(onReleaseResult)
        return shouldInvalidate
    }

    /** Non-secret account identity for the registered Home header; password never leaves this owner. */
    fun currentUsernameForDisplay(): String? = synchronized(this) { activeAccount?.username }

    /** Stage5B5 non-secret Settings projection. The stored password never leaves this owner. */
    fun currentSettingsSnapshot(): RasvenAdvancedSettingsCurrentAccount? = synchronized(this) {
        activeAccount?.let { account ->
            RasvenAdvancedSettingsCurrentAccount(
                server = account.server,
                port = account.port,
                username = account.username,
            )
        }
    }

    /**
     * Stage5B5 secure Settings update. Saving account endpoint/identity invalidates the current
     * registration relationship, so the existing Home Unregistered -> explicit Reconnect contract
     * becomes authoritative. No success is fabricated and no registration attempt starts on save.
     */
    fun applySettingsUpdate(update: RasvenAdvancedSettingsUpdate): Boolean = synchronized(this) {
        val current = activeAccount ?: return false
        val updated = current.applyAdvancedSettings(update)
        if (updated.server == current.server && updated.port == current.port && updated.username == current.username &&
            updated.password == current.password) return true
        if (setupRepository?.saveAccount(updated) == false) return false
        gatewayProvider().invalidate()
        activeAccount = updated
        currentAttemptId = null
        currentAttemptOrigin = null
        if (
            state is RasvenRegistrationState.Registered ||
            state is RasvenRegistrationState.Registering
        ) {
            state = RasvenRegistrationState.Unregistered
        }
        true
    }

    /** Stage5A5 explicit connect action. Duplicate calls while non-Idle are ignored. */
    fun startOnce(account: RasvenValidatedAccount) {
        val launch = synchronized(this) {
            if (state !is RasvenRegistrationState.Idle) null else prepareAttemptLocked(account, AttemptOrigin.Setup)
        } ?: return
        launchAttempt(launch)
    }

    /** Stage5A8 user-controlled Retry. One Failed -> Registering transition creates one new attempt. */
    fun retryFailedAttempt() {
        val launch = synchronized(this) {
            if (state !is RasvenRegistrationState.Failed) {
                null
            } else {
                activeAccount?.let { prepareAttemptLocked(it, AttemptOrigin.Setup) }
            }
        } ?: return
        launchAttempt(launch)
    }


    /** Home-only connection-loss boundary. It preserves the volatile account for explicit recovery. */
    fun markConnectionLost() {
        val shouldInvalidate = synchronized(this) {
            if (state !is RasvenRegistrationState.Registered) return
            state = RasvenRegistrationState.Unregistered
            true
        }
        if (shouldInvalidate) gatewayProvider().invalidate()
    }

    /** Stage5B2 explicit Home reconnect. Repeated calls while non-Unregistered are ignored. */
    fun reconnectFromHome() {
        val launch = synchronized(this) {
            if (state !is RasvenRegistrationState.Unregistered) {
                null
            } else {
                activeAccount?.let { prepareAttemptLocked(it, AttemptOrigin.HomeRecovery) }
            }
        } ?: return
        launchAttempt(launch)
    }

    /**
     * Stage5A8 Open Settings returns to authoritative Stage5A2 without starting registration.
     * Old account data is cleared from this owner so no stored password is recovered for display.
     */
    fun prepareForAccountEditAfterFailure() {
        synchronized(this) {
            if (state !is RasvenRegistrationState.Failed) return
            gatewayProvider().invalidate()
            state = RasvenRegistrationState.Idle
            activeAccount = null
            currentAttemptId = null
            currentAttemptOrigin = null
        }
    }

    private fun prepareAttemptLocked(account: RasvenValidatedAccount, origin: AttemptOrigin): AttemptLaunch {
        attemptSequence += 1
        val id = attemptSequence
        activeAccount = account
        currentAttemptId = id
        currentAttemptOrigin = origin
        state = RasvenRegistrationState.Registering
        startCount += 1
        return AttemptLaunch(id = id, account = account)
    }

    private fun launchAttempt(launch: AttemptLaunch) {
        gatewayProvider().start(launch.account) { result ->
            acceptTerminalResult(launch.id, result)
        }
    }

    private fun acceptTerminalResult(attemptId: Long, result: RasvenRegistrationResult) {
        synchronized(this) {
            if (currentAttemptId != attemptId) return
            if (state is RasvenRegistrationState.Unregistered && result is RasvenRegistrationResult.Registered &&
                gatewayProvider().supportsAutomaticRecovery) {
                state = RasvenRegistrationState.Registered
                return
            }
            if (state is RasvenRegistrationState.Registered && result is RasvenRegistrationResult.Failed) {
                state = RasvenRegistrationState.Unregistered
                gatewayProvider().onRegistrationFailed()
                return
            }
            if (state !is RasvenRegistrationState.Registering) return
            state = when (result) {
                RasvenRegistrationResult.Registered -> RasvenRegistrationState.Registered
                is RasvenRegistrationResult.Failed -> {
                    gatewayProvider().onRegistrationFailed()
                    if (currentAttemptOrigin == AttemptOrigin.HomeRecovery) RasvenRegistrationState.Unregistered
                    else RasvenRegistrationState.Failed(result.category)
                }
            }
        }
    }

    override fun onCleared() {
        gatewayProvider().detachObserver()
        activeAccount = null
        currentAttemptId = null
        currentAttemptOrigin = null
        super.onCleared()
    }
}

/**
 * Applies Stage5A4 changes without ever round-tripping the existing password through UI state.
 * Empty replacement means preserve the already-held validated credential in volatile memory.
 */
internal fun RasvenValidatedAccount.applyAdvancedSettings(
    update: RasvenAdvancedSettingsUpdate,
): RasvenValidatedAccount = RasvenValidatedAccount(
    server = update.server,
    port = update.port,
    username = update.username,
    password = update.passwordReplacement ?: password,
)
