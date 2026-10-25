package com.rasven.android.iax2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import com.rasven.android.designsystem.foundation.rasvenString
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.rasven.android.diagnostics.RasvenDiagnostics
import com.rasven.android.R
import com.rasven.android.app.BootstrapActivity
import com.rasven.android.product.call.RasvenCallDomain
import com.rasven.android.product.setup.RasvenIax2RegistrationGateway
import com.rasven.android.product.setup.RasvenSecureSetupRepository

/** User-visible standby for a legacy PBX that delivers calls directly over IAX UDP, without push. */
internal class RasvenRegistrationForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        RasvenDiagnostics.debug("RasvenStandby", "SERVICE_CREATED")
        instance = this
        // Satisfy every initial startForegroundService before a fast REGREJ/cancel can retire it.
        publish(ready = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        RasvenDiagnostics.debug("RasvenStandby", "SERVICE_START requested=$requested restore=${intent == null} ready=${intent?.getBooleanExtra(READY, false) == true}")
        if (intent?.action == STOP) {
            getSharedPreferences("rasven_user_connection_v1", MODE_PRIVATE)
                .edit().putBoolean("paused", true).apply()
            RasvenIax2RegistrationGateway.stopFromNotification()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null && !requested) restoreAfterSystemRestart()
        publish(ready = intent?.getBooleanExtra(READY, false) == true,
            awaitingNetwork = intent?.getBooleanExtra(AWAITING_NETWORK, false) == true)
        if (!requested) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Android may recreate an already user-enabled standby after process death. This does
        // not start at boot or after a force-stop, and explicit Disconnect/Stop stays paused.
        return START_STICKY
    }

    private fun restoreAfterSystemRestart() {
        val paused = getSharedPreferences("rasven_user_connection_v1", MODE_PRIVATE).getBoolean("paused", false)
        if (paused) return
        val account = RasvenSecureSetupRepository(this).load()?.account
        if (!shouldRestoreStandbyConnection(paused, account != null,
                com.rasven.android.product.settings.RasvenRequiredCallAccess.status(this).canConnect)) return
        RasvenCallDomain.initialize(applicationContext)
        RasvenIax2RegistrationGateway.start(checkNotNull(account)) {}
    }

    private fun publish(ready: Boolean, awaitingNetwork: Boolean = false) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, rasvenString(R.string.system_standby_channel), NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
        })
        val open = PendingIntent.getActivity(this, 8101,
            Intent(this, BootstrapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 8102, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.rasven_call)
            .setContentTitle(rasvenString(R.string.system_standby_title))
            .setContentText(when {
                awaitingNetwork -> rasvenString(R.string.system_standby_waiting_network)
                !ready -> rasvenString(R.string.system_standby_connecting)
                RasvenCallDomain.isDoNotDisturbEnabled() -> rasvenString(R.string.system_standby_dnd)
                else -> rasvenString(R.string.system_standby_ready)
            })
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, rasvenString(R.string.system_standby_disconnect), stop).build())
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, notification)
            active = true
        } catch (failure: RuntimeException) {
            RasvenDiagnostics.error("RasvenStandby", "FOREGROUND_FAILURE type=${failure.javaClass.simpleName}")
            active = false
            RasvenIax2RegistrationGateway.stopFromNotification()
            stopSelf()
        }
    }

    override fun onDestroy() {
        RasvenDiagnostics.debug("RasvenStandby", "SERVICE_DESTROYED requested=$requested")
        if (instance === this) instance = null
        active = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        RasvenDiagnostics.debug("RasvenStandby", "UI_TASK_REMOVED requested=$requested active=$active")
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val CHANNEL = "rasven_registration_standby"
        private const val ID = 8100
        private const val STOP = "com.rasven.android.STOP_REGISTRATION"
        private const val READY = "ready"
        private const val AWAITING_NETWORK = "awaiting_network"
        @Volatile private var active = false
        @Volatile private var requested = false
        @Volatile private var instance: RasvenRegistrationForegroundService? = null
        internal fun isActiveForDiagnostics() = active
        fun start(context: Context, ready: Boolean = false, awaitingNetwork: Boolean = false): Boolean = try {
            requested = true
            val intent = Intent(context, RasvenRegistrationForegroundService::class.java)
                .putExtra(READY, ready).putExtra(AWAITING_NETWORK, awaitingNetwork)
            // An already-foreground notification update needs no new foreground-start deadline.
            if (active) context.startService(intent) else context.startForegroundService(intent)
            true
        } catch (_: RuntimeException) { requested = false; false }
        fun stop(context: Context) {
            requested = false
            // Do not cancel a queued foreground start before it fulfills Android's contract.
            // onStartCommand promotes and immediately retires a canceled pending start.
            instance?.stopSelf()
        }
        fun refreshPolicy(context: Context) {
            if (active && requested) start(context, ready = true)
        }
    }
}
