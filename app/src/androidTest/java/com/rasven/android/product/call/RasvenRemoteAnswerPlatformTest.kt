package com.rasven.android.product.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.telecom.DisconnectCause
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.R
import com.rasven.android.app.BootstrapActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Real Android remote-answer transaction, with no PBX account, UDP, microphone, or audible ring. */
class RasvenRemoteAnswerPlatformTest {
    @Test fun headsetAnswerWhileScreenOffCompletesAndDoesNotTearDownTheCall() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("Run this fixture with -Prasven.applicationId=com.rasven.android.qa",
            "com.rasven.android.qa.debug", context.packageName)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        context.startActivity(Intent(context, BootstrapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "rasven_offline_answer_fixture"
        manager.createNotificationChannel(NotificationChannel(channel, "آزمون آفلاین پاسخ", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        })
        val notificationId = 31011
        val callId = 91011L
        val added = CountDownLatch(1)
        val answered = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val answerCount = AtomicInteger()
        var addedSuccessfully = false
        var gateway: RasvenCoreTelecomGateway? = null
        fun shell(command: String): String {
            val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
            return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        }
        val wasInteractive = context.getSystemService(PowerManager::class.java).isInteractive
        val actions = object : RasvenCoreTelecomGateway.Actions {
            override suspend fun answer(callId: Long): Boolean {
                answerCount.incrementAndGet()
                answered.countDown()
                return true
            }
            override suspend fun disconnect(callId: Long) = true
            override suspend fun setActive(callId: Long) = true
            override suspend fun setInactive(callId: Long) = true
            override fun added(callId: Long, success: Boolean) {
                addedSuccessfully = success
                if (success) {
                    fun pending(slot: Int) = PendingIntent.getBroadcast(context, slot,
                        Intent("com.rasven.android.OFFLINE_FIXTURE").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
                    val notification = Notification.Builder(context, channel)
                        .setSmallIcon(R.drawable.rasven_call)
                        .setContentTitle("آزمون آفلاین پاسخ")
                        .setCategory(Notification.CATEGORY_CALL)
                        .setOngoing(true)
                        .setStyle(Notification.CallStyle.forIncomingCall(
                            Person.Builder().setName("Offline fixture").build(), pending(1), pending(2)))
                        .build()
                    RasvenCallNotificationService.post(context, notificationId, notification)
                }
                added.countDown()
            }
            override fun ended(callId: Long) { ended.countDown() }
        }
        try {
            instrumentation.runOnMainSync {
                gateway = RasvenCoreTelecomGateway(context)
                assertTrue(gateway.add(callId, true, "Offline fixture", "offline-fixture", actions))
            }
            assertTrue("fixture must be added before answering", added.await(4, TimeUnit.SECONDS))
            assertTrue("Android must admit the offline call", addedSuccessfully)
            shell("input keyevent 223")
            // addCall completes before Telecom asynchronously activates its headset MediaSession.
            // Without this barrier a key can be routed to the last music/video player instead.
            shell("cmd telecom wait-on-handlers")
            val sessionDeadline = android.os.SystemClock.elapsedRealtime() + 4000L
            var headsetSessionReady = false
            while (android.os.SystemClock.elapsedRealtime() < sessionDeadline) {
                val mediaState = shell("dumpsys media_session")
                val prioritySession = mediaState.substringAfter("Global priority session is", "")
                    .substringBefore("User Records:")
                headsetSessionReady = prioritySession.contains("com.android.server.telecom/HeadsetMediaButton") &&
                    prioritySession.contains("active=true")
                if (headsetSessionReady) break
                Thread.sleep(40)
            }
            assertTrue("Telecom must own headset routing before injecting the answer key", headsetSessionReady)
            shell("input keyevent 79")
            assertTrue("system headset action must invoke onAnswer", answered.await(4, TimeUnit.SECONDS))
            assertEquals(1, answerCount.get())
            assertFalse("completed answer must survive the platform's 5-second transaction deadline",
                ended.await(6, TimeUnit.SECONDS))
            assertTrue(gateway?.isAdded(callId) == true)
        } finally {
            gateway?.let { owner ->
                instrumentation.runOnMainSync { owner.disconnect(callId, DisconnectCause.LOCAL) }
                ended.await(4, TimeUnit.SECONDS)
                instrumentation.runOnMainSync { owner.close() }
            }
            instrumentation.runOnMainSync { RasvenCallNotificationService.clear(notificationId) }
            manager.cancel(notificationId)
            if (wasInteractive) shell("input keyevent 224")
        }
    }
}
