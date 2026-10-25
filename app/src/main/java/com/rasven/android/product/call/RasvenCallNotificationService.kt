package com.rasven.android.product.call

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.rasven.android.diagnostics.RasvenDiagnostics

/** CallStyle is owned by a real phoneCall FGS, independently of microphone capture. */
internal class RasvenCallNotificationService : Service() {
    private var ownedId: Int? = null
    private lateinit var ringer: RasvenIncomingRinger
    override fun onCreate() { super.onCreate(); instance = this; ringer = RasvenIncomingRinger(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra("notificationId", -1) ?: -1
        @Suppress("DEPRECATION")
        val notification = intent?.getParcelableExtra<Notification>("notification")
        if (id < 0 || notification == null) { stopSelf(startId); return START_NOT_STICKY }
        val generation = intent.getLongExtra("generation", -1)
        if (pending[id] != generation) {
            if (ownedId == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            if (ownedId != null && ownedId != id) stopForeground(STOP_FOREGROUND_REMOVE)
            if (Build.VERSION.SDK_INT >= 29) startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            else startForeground(id, notification)
            ownedId = id
            RasvenDiagnostics.debug("RasvenCall", "CALL_FOREGROUND_POSTED ringing=${intent.getBooleanExtra("ringing", false)}")
            if (intent.getBooleanExtra("ringing", false) && silenced[id] != generation) ringer.start(id)
            else ringer.stopAll()
        } catch (failure: RuntimeException) {
            RasvenDiagnostics.error("RasvenCall", "CALL_FOREGROUND_FAILURE type=${failure.javaClass.simpleName}")
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (instance === this) instance = null
        ringer.stopAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        @Volatile private var instance: RasvenCallNotificationService? = null
        private val counter = java.util.concurrent.atomic.AtomicLong()
        private val pending = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        private val silenced = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        fun post(context: Context, id: Int, notification: Notification, ringing: Boolean = false): Boolean {
            val generation = counter.incrementAndGet()
            pending[id] = generation
            return try {
                context.startForegroundService(Intent(context, RasvenCallNotificationService::class.java)
                    .putExtra("notificationId", id).putExtra("notification", notification)
                    .putExtra("ringing", ringing)
                    .putExtra("generation", generation))
                true
            } catch (failure: RuntimeException) {
                RasvenDiagnostics.error("RasvenCall", "CALL_FOREGROUND_START_FAILURE type=${failure.javaClass.simpleName}")
                pending.remove(id, generation)
                false
            }
        }
        fun clear(id: Int) {
            pending.remove(id)
            silenced.remove(id)
            instance?.let { service -> if (service.ownedId == id) {
                service.ringer.stop(id)
                service.stopForeground(STOP_FOREGROUND_REMOVE)
                service.ownedId = null
                service.stopSelf()
            } }
        }
        fun stopRinging(id: Int) {
            pending[id]?.let { silenced[id] = it }
            instance?.ringer?.stop(id)
        }
    }
}
