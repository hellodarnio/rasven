package com.rasven.android.product.call

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before

class RasvenCallNotificationPlatformTest {
    // These fixtures post notifications without adding a real Telecom call. They cannot rely on
    // the self-managed-call exemption; fresh Gradle installs must establish this precondition.
    @Before fun grantPositiveFixtureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
            assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    @Test fun incomingUsesRingtoneChannelAndAnswerUpdatesToSilentOngoingCall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(android.content.Intent(context, com.rasven.android.app.BootstrapActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = RasvenCallNotification(context)
        val id = 7124L
        fun posted() = manager.activeNotifications.firstOrNull { it.id == 27124 }?.notification
        fun awaitChannel(channel: String) {
            val deadline = System.currentTimeMillis() + 4000
            while (posted()?.channelId != channel && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertEquals(channel, posted()?.channelId)
        }
        try {
            assertTrue(notifications.showIncoming(id, "fixture-peer"))
            awaitChannel(RasvenCallNotification.PRESENTATION_CHANNEL)
            val channel = manager.getNotificationChannel(RasvenCallNotification.INCOMING_CHANNEL)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
            assertEquals(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE, channel.audioAttributes.usage)
            assertTrue(channel.shouldVibrate())
            @Suppress("DEPRECATION")
            assertEquals(0, posted()!!.flags and android.app.Notification.FLAG_INSISTENT)
            val presentation = manager.getNotificationChannel(RasvenCallNotification.PRESENTATION_CHANNEL)
            assertNull(presentation.sound)
            assertFalse(presentation.shouldVibrate())
            assertTrue(notifications.showOngoing(id, "fixture-peer", incoming = true))
            awaitChannel(RasvenCallNotification.ONGOING_CHANNEL)
            assertNull(manager.getNotificationChannel(RasvenCallNotification.ONGOING_CHANNEL).sound)
            @Suppress("DEPRECATION")
            assertEquals(0, posted()!!.flags and android.app.Notification.FLAG_INSISTENT)
        } finally { notifications.clear(id) }
    }

    @Test fun outgoingCallStyleCanBePostedWithoutFullScreenIntent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(android.content.Intent(context, com.rasven.android.app.BootstrapActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val notifications = RasvenCallNotification(context)
        val callId = 7123L
        try {
            assertTrue("Real Android must accept the outgoing CallStyle", notifications.showOutgoing(callId, "fixture-peer"))
            val manager = context.getSystemService(NotificationManager::class.java)
            val deadline = System.currentTimeMillis() + 4000
            while (manager.activeNotifications.none { it.id == 20000 + 7123 } && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertTrue(manager.activeNotifications.any { it.id == 20000 + 7123 })
        } finally {
            notifications.clear(callId)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 4000
        while (manager.activeNotifications.any { it.id == 20000 + 7123 } && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertFalse("Ended call must not leave a foreground notification", manager.activeNotifications.any { it.id == 20000 + 7123 })
    }
}
