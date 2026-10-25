package com.rasven.android.product.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import com.rasven.android.designsystem.foundation.rasvenString
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import com.rasven.android.diagnostics.RasvenDiagnostics
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.rasven.android.R
import com.rasven.android.app.BootstrapActivity

internal class RasvenCallNotification(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(INCOMING_CHANNEL, app.rasvenString(R.string.system_incoming_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        manager.createNotificationChannel(NotificationChannel(ONGOING_CHANNEL, app.rasvenString(R.string.system_ongoing_channel), NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
        })
        manager.createNotificationChannel(NotificationChannel(PRESENTATION_CHANNEL, app.rasvenString(R.string.system_presentation_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
    }

    fun showIncoming(callId: Long, caller: String): Boolean {
        val answer = PendingIntent.getActivity(
            app, requestCode(callId, 1),
            Intent(app, BootstrapActivity::class.java).apply {
                action = ACTION_ANSWER
                putExtra(EXTRA_CALL_ID, callId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                RasvenIncomingIntentAuthorization.authorize(app, this)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val reject = actionIntent(callId, RasvenCallNotificationReceiver.ACTION_REJECT, 2)
        val activity = PendingIntent.getActivity(
            app, requestCode(callId, 3),
            Intent(app, BootstrapActivity::class.java).apply {
                action = ACTION_OPEN_INCOMING
                putExtra(EXTRA_CALL_ID, callId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                RasvenIncomingIntentAuthorization.authorize(app, this)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(app, PRESENTATION_CHANNEL)
            .setSmallIcon(R.drawable.rasven_call)
            .setContentTitle(app.rasvenString(R.string.incoming_call_title))
            .setContentText(caller.ifBlank { app.rasvenString(R.string.incoming_call_unknown) })
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(activity)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setStyle(Notification.CallStyle.forIncomingCall(
                Person.Builder().setName(caller.ifBlank { app.rasvenString(R.string.incoming_call_unknown) }).build(), reject, answer))
        } else {
            builder.addAction(Notification.Action.Builder(null, app.rasvenString(R.string.incoming_call_decline), reject).build())
            builder.addAction(Notification.Action.Builder(null, app.rasvenString(R.string.incoming_call_answer), answer).build())
        }
        val fullScreenAllowed = if (Build.VERSION.SDK_INT >= 34) manager.canUseFullScreenIntent() else true
        RasvenDiagnostics.debug("RasvenCall", "INCOMING_DISPLAY_ACCESS fullScreen=$fullScreenAllowed notifications=${manager.areNotificationsEnabled()} channelImportance=${manager.getNotificationChannel(PRESENTATION_CHANNEL)?.importance}")
        if (fullScreenAllowed) builder.setFullScreenIntent(activity, true)
        return try {
            val notification = builder.build()
            RasvenCallNotificationService.post(app, notificationId(callId), notification, ringing = true)
        } catch (failure: RuntimeException) {
            false
        }
    }

    fun showOutgoing(callId: Long, remote: String): Boolean = showOngoing(callId, remote, incoming = false)

    fun showOngoing(callId: Long, remote: String, incoming: Boolean): Boolean {
        val end = actionIntent(callId, RasvenCallNotificationReceiver.ACTION_END, 4)
        val open = Intent(app, BootstrapActivity::class.java)
            .setAction(ACTION_OPEN_ONGOING)
            .putExtra(EXTRA_CALL_ID, callId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        RasvenIncomingIntentAuthorization.authorize(app, open)
        val content = PendingIntent.getActivity(app, requestCode(callId, 5), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(app, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.rasven_call)
            .setContentTitle(if (incoming) app.rasvenString(R.string.incoming_call_title) else app.rasvenString(R.string.system_outgoing_title))
            .setContentText(remote)
            .setContentIntent(content)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setStyle(Notification.CallStyle.forOngoingCall(
                Person.Builder().setName(remote).build(), end))
        } else {
            builder.addAction(Notification.Action.Builder(null, app.rasvenString(R.string.incoming_call_end), end).build())
        }
        return try {
            RasvenCallNotificationService.post(app, notificationId(callId), builder.build())
        } catch (failure: RuntimeException) {
            false
        }
    }

    fun clear(callId: Long) {
        RasvenCallNotificationService.clear(notificationId(callId))
        manager.cancel(notificationId(callId))
    }

    fun stopRinging(callId: Long) { RasvenCallNotificationService.stopRinging(notificationId(callId)) }

    private fun actionIntent(callId: Long, actionName: String, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            app, requestCode(callId, slot),
            Intent(app, RasvenCallNotificationReceiver::class.java).apply {
                action = actionName
                putExtra(EXTRA_CALL_ID, callId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationId(callId: Long): Int = 20_000 + (callId and 0x3fff).toInt()
    private fun requestCode(callId: Long, slot: Int): Int = ((callId and 0x1fff).toInt() shl 3) or slot

    companion object {
        const val EXTRA_CALL_ID = "com.rasven.android.call.ID"
        const val ACTION_OPEN_INCOMING = "com.rasven.android.call.OPEN_INCOMING"
        const val ACTION_OPEN_ONGOING = "com.rasven.android.call.OPEN_ONGOING"
        const val ACTION_ANSWER = "com.rasven.android.call.ANSWER_FROM_NOTIFICATION"
        internal const val INCOMING_CHANNEL = "rasven_incoming_calls_v2"
        internal const val PRESENTATION_CHANNEL = "rasven_incoming_presentation_v3"
        internal const val ONGOING_CHANNEL = "rasven_ongoing_calls_v2"
    }
}

internal class RasvenCallNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getLongExtra(RasvenCallNotification.EXTRA_CALL_ID, -1)
        if (callId < 0) return
        // A notification action is a Product command, never a direct IAX2 or Telecom mutation.
        RasvenCallDomain.initialize(context.applicationContext)
        when (intent.action) {
            ACTION_ANSWER -> RasvenCallDomain.requestAnswer(callId)
            ACTION_REJECT -> RasvenCallDomain.requestReject(callId)
            ACTION_END -> RasvenCallDomain.requestEnd(callId)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.rasven.android.call.ANSWER"
        const val ACTION_REJECT = "com.rasven.android.call.REJECT"
        const val ACTION_END = "com.rasven.android.call.END"
    }
}
