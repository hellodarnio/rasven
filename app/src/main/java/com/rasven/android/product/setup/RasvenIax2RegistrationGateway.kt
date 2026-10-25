package com.rasven.android.product.setup

import android.os.Handler
import android.os.Looper
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.rasven.android.product.settings.RasvenRequiredCallAccess
import com.rasven.android.iax2.RasvenIax2Account
import com.rasven.android.iax2.RasvenIax2RegistrationEvent
import com.rasven.android.iax2.RasvenIax2Runtime

/** Production registration adapter. Secrets cross only the protocol boundary and never return to UI. */
internal object RasvenIax2RegistrationGateway : RasvenRegistrationGateway {
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var configuredAccount: RasvenValidatedAccount? = null
    private var observer: ((RasvenRegistrationResult) -> Unit)? = null
    private var latestResult: RasvenRegistrationResult? = null
    private var context: Context? = null
    private val recovery = RasvenRegistrationRecoveryPolicy()
    private var pendingRetry: Runnable? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var knownNetwork: Network? = null
    override val supportsAutomaticRecovery: Boolean = true

    fun initialize(appContext: Context) { context = appContext.applicationContext }
    // Failure handling is process-owned even when no Activity observer is attached.
    override fun onRegistrationFailed() = Unit

    override fun start(
        account: RasvenValidatedAccount,
        onResult: (RasvenRegistrationResult) -> Unit,
    ) {
        observer = onResult
        // A detached UI can miss a refresh/transport failure. A surviving engine object is not
        // evidence of a usable registration; reattaching after failure must allow a fresh attempt.
        if (configuredAccount?.sameRegistrationAs(account) == true && latestResult !is RasvenRegistrationResult.Failed &&
            RasvenIax2Runtime.isRegistrationAlive()) {
            latestResult?.let { result -> main.post { if (observer === onResult) onResult(result) } }
            return
        }
        configuredAccount = account
        cancelRetry()
        recovery.explicitStart()
        observeNetwork()
        beginConnection(account)
    }

    private fun beginConnection(account: RasvenValidatedAccount) {
        latestResult = null
        val owner = ++generation
        RasvenIax2Runtime.register(
            account = RasvenIax2Account(
                server = account.server,
                port = account.port,
                username = account.username,
                password = account.password,
            ),
        ) { event ->
            // Capture while the matching engine still exists, before Main can invalidate it.
            val retryable = event is RasvenIax2RegistrationEvent.Failed && RasvenIax2Runtime.registrationFailureRetryable()
            main.post {
                if (generation != owner) return@post
                val result = when (event) {
                        RasvenIax2RegistrationEvent.Registered -> RasvenRegistrationResult.Registered
                        RasvenIax2RegistrationEvent.Failed ->
                            RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown)
                    }
                latestResult = result
                if (result is RasvenRegistrationResult.Registered) {
                    recovery.registered()
                    cancelRetry()
                } else {
                    val delay = recovery.failed(retryable)
                    RasvenIax2Runtime.invalidate(keepStandby = recovery.keepStandby && canRecover())
                    if (delay != null) scheduleRetry(delay)
                    else if (recovery.keepStandby && canRecover()) context?.let {
                        com.rasven.android.iax2.RasvenRegistrationForegroundService.start(it, awaitingNetwork = true)
                    }
                }
                observer?.invoke(result)
            }
        }
    }

    override fun invalidate() {
        stopRecovery()
        generation++
        observer = null
        configuredAccount = null
        latestResult = null
        RasvenIax2Runtime.invalidate()
    }

    override fun disconnect(onComplete: (Boolean) -> Unit) {
        stopRecovery()
        generation++
        observer = null
        configuredAccount = null
        latestResult = null
        RasvenIax2Runtime.releaseRegistrationAndInvalidate { released ->
            main.post { onComplete(released) }
        }
    }

    override fun detachObserver() { observer = null }

    private fun canRecover(): Boolean {
        val app = context ?: return false
        return !app.getSharedPreferences("rasven_user_connection_v1", Context.MODE_PRIVATE).getBoolean("paused", false) &&
            RasvenRequiredCallAccess.status(app).canConnect
    }

    private fun scheduleRetry(delay: Long) {
        cancelRetry()
        val owner = generation
        val account = configuredAccount ?: return
        val task = Runnable {
            pendingRetry = null
            if (generation != owner || configuredAccount !== account || !recovery.keepStandby) return@Runnable
            if (!canRecover()) {
                stopRecovery()
                RasvenIax2Runtime.invalidate()
                return@Runnable
            }
            beginConnection(account)
        }
        pendingRetry = task
        main.postDelayed(task, delay)
    }

    private fun cancelRetry() { pendingRetry?.let(main::removeCallbacks); pendingRetry = null }
    private fun stopRecovery() {
        cancelRetry()
        recovery.stop()
        val manager = context?.getSystemService(ConnectivityManager::class.java)
        networkCallback?.let { callback -> runCatching { manager?.unregisterNetworkCallback(callback) } }
        networkCallback = null
        knownNetwork = null
    }

    private fun observeNetwork() {
        if (networkCallback != null) return
        val manager = context?.getSystemService(ConnectivityManager::class.java) ?: return
        knownNetwork = manager.activeNetwork
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val owner = this
                main.post {
                    if (networkCallback !== owner || network == knownNetwork) return@post
                    knownNetwork = network
                    recoverOnNetworkChange()
                }
            }
            override fun onLost(network: Network) {
                val owner = this
                main.post {
                    if (networkCallback !== owner || network != knownNetwork) return@post
                    knownNetwork = null
                    recoverOnNetworkChange()
                }
            }
        }
        networkCallback = callback
        try { manager.registerDefaultNetworkCallback(callback) }
        catch (_: RuntimeException) { networkCallback = null }
    }

    private fun recoverOnNetworkChange() {
        val delay = recovery.networkChanged() ?: return
        if (!canRecover()) { stopRecovery(); RasvenIax2Runtime.invalidate(); return }
        cancelRetry()
        generation++ // Late replies from the obsolete network owner cannot republish Registered.
        RasvenIax2Runtime.invalidate(keepStandby = true)
        val lost = RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown)
        latestResult = lost
        observer?.invoke(lost)
        scheduleRetry(delay)
    }

    fun stopFromNotification() {
        val callback = observer
        disconnect {
            main.post { callback?.invoke(RasvenRegistrationResult.Failed(RasvenRegistrationErrorCategory.Unknown)) }
        }
    }
}
