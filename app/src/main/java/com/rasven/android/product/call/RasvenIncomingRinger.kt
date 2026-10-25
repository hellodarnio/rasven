package com.rasven.android.product.call

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

/** A call owns its feedback, not the transient notification shade/full-screen presentation. */
internal class RasvenIncomingRinger(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val vibrator = app.getSystemService(Vibrator::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var owner: Int? = null
    private var player: MediaPlayer? = null
    private var lastPolicy: RasvenRingerPolicy? = null
    internal val ownedNotificationId: Int? get() = owner
    internal val isSoundPlaying: Boolean get() = synchronized(this) {
        try { player?.isPlaying == true } catch (_: IllegalStateException) { false }
    }
    internal val expectsSound: Boolean get() = lastPolicy?.sound == true
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val poll = object : Runnable {
        override fun run() {
            if (owner == null) return
            updatePolicy()
            main.postDelayed(this, 250)
        }
    }

    @Synchronized fun start(id: Int) {
        if (owner == id) return
        stopAll()
        owner = id
        poll.run()
    }

    @Synchronized fun stop(id: Int) { if (owner == id) stopAll() }

    @Synchronized fun stopAll() {
        owner = null
        main.removeCallbacks(poll)
        releaseFeedback()
        lastPolicy = null
    }

    @Synchronized private fun updatePolicy() {
        // Preserve the existing user-editable channel's sound/vibration policy. The new
        // presentation channel is quiet so Android cannot race or duplicate this player.
        val channel = notifications.getNotificationChannel(RasvenCallNotification.INCOMING_CHANNEL)
        val presentation = notifications.getNotificationChannel(RasvenCallNotification.PRESENTATION_CHANNEL)
        val allowed = notifications.areNotificationsEnabled() && channel != null &&
            channel.importance != NotificationManager.IMPORTANCE_NONE &&
            presentation != null && presentation.importance != NotificationManager.IMPORTANCE_NONE &&
            notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        @Suppress("DEPRECATION")
        val policy = RasvenRingerPolicy.evaluate(allowed, audio.ringerMode,
            audio.getStreamVolume(AudioManager.STREAM_RING), channel?.sound != null,
            channel?.shouldVibrate() == true, vibrator.hasVibrator(),
            Settings.System.getInt(app.contentResolver, Settings.System.VIBRATE_WHEN_RINGING, 0) != 0)
        if (policy == lastPolicy) return
        releaseFeedback()
        lastPolicy = policy
        if (policy.sound) {
            val candidate = MediaPlayer()
            player = candidate
            try {
                candidate.setAudioAttributes(attributes)
                candidate.setDataSource(app, channel!!.sound)
                candidate.isLooping = true
                candidate.setOnPreparedListener {
                    synchronized(this) {
                        if (owner != null && player === it && lastPolicy?.sound == true) it.start()
                    }
                }
                candidate.setOnErrorListener { failed, _, _ ->
                    synchronized(this) {
                        if (player === failed) { player = null; failed.release() }
                    }
                    true
                }
                candidate.prepareAsync()
            } catch (_: Exception) { if (player === candidate) { player = null; candidate.release() } }
        }
        if (policy.vibrate) {
            try {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 700), 0), attributes)
            } catch (_: RuntimeException) { /* No permission/platform effect: never crash the call. */ }
        }
    }

    private fun releaseFeedback() {
        player?.let { it.setOnPreparedListener(null); it.setOnErrorListener(null); it.release() }
        player = null
        vibrator.cancel()
    }
}
