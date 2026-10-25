package com.rasven.android.iax2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.rasven.android.R
import com.rasven.android.designsystem.foundation.rasvenString

/** Minimum generic foreground ownership for a user-initiated outgoing microphone media session. */
internal class RasvenCallMediaForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle(rasvenString(R.string.app_name))
            .setContentText(rasvenString(R.string.active_call_notification_text))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            activeForDiagnostics = true
            instance = this
        } catch (_: SecurityException) {
            // RECORD_AUDIO can be revoked between the user action and Service creation. Fail closed without crashing.
            activeForDiagnostics = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Even a canceled pending start must first run onCreate/startForeground above. Calling
        // stopService before that promotion leaves Android's FGS start deadline unfulfilled.
        if (!requested) stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        activeForDiagnostics = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, rasvenString(R.string.active_call_notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = rasvenString(R.string.active_call_notification_text)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "rasven_active_call_media"
        private const val NOTIFICATION_ID = 5602
        @Volatile private var activeForDiagnostics = false
        @Volatile private var requested = false
        @Volatile private var instance: RasvenCallMediaForegroundService? = null

        internal fun isActiveForDiagnostics(): Boolean = activeForDiagnostics

        fun start(context: Context): Boolean = try {
            requested = true
            val intent = Intent(context, RasvenCallMediaForegroundService::class.java)
            if (activeForDiagnostics) context.startService(intent) else context.startForegroundService(intent)
            true
        } catch (_: Exception) {
            requested = false
            false
        }

        fun stop(@Suppress("UNUSED_PARAMETER") context: Context) {
            requested = false
            // A queued start still needs to be promoted; onStartCommand retires it afterward.
            instance?.stopSelf()
        }
    }
}
