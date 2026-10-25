package com.rasven.android.product.call

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.rasven.android.app.BootstrapActivity
import com.rasven.android.iax2.*
import com.rasven.android.product.setup.RasvenIax2RegistrationGateway
import com.rasven.android.product.setup.RasvenRegistrationResult
import com.rasven.android.product.setup.RasvenSecureSetupRepository
import com.rasven.android.product.setup.RasvenValidatedAccount
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

/** Actual product UDP -> Telecom -> full-screen notification -> answer/media, entirely on loopback. */
class RasvenLockedIncomingPlatformTest {
    @Test fun incomingAfterTaskRemovalWhileLockedOpensFullScreenAndAnswerRemainsActive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("Run with the isolated QA application ID", "com.rasven.android.qa.debug", context.packageName)
        val savedSetup = RasvenSecureSetupRepository(context)
        assertTrue(savedSetup.clear())
        context.getSharedPreferences("rasven_user_connection_v1", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("paused", false).commit()
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                .use { it.readBytes() }
        }
        fun await(timeout: Long = 6000L, condition: () -> Boolean): Boolean {
            val deadline = android.os.SystemClock.elapsedRealtime() + timeout
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if (condition()) return true
                Thread.sleep(40)
            }
            return condition()
        }
        val power = context.getSystemService(PowerManager::class.java)
        val wasInteractive = power.isInteractive
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        shell("cmd appops set ${context.packageName} USE_FULL_SCREEN_INTENT allow")
        assertTrue(notifications.canUseFullScreenIntent())
        val open = Intent(context, BootstrapActivity::class.java)
            .setAction(RasvenCallNotification.ACTION_OPEN_INCOMING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        RasvenIncomingIntentAuthorization.authorize(context, open)
        context.startActivity(open)
        instrumentation.waitForIdleSync()
        assertTrue("initial Activity must finish startup before the fixture account is saved", await {
            var resumed = false
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { it is BootstrapActivity }
            }
            resumed
        })
        val answeredPeers = ConcurrentHashMap.newKeySet<Int>()
        val clientLegs = ConcurrentHashMap<Int, Int>()
        val nextClientSequence = ConcurrentHashMap<Int, Int>()
        val stopPeer = AtomicBoolean(false)
        var peerThread: Thread? = null
        var productCallId: Long? = null
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 4000
            try {
                val account = RasvenValidatedAccount("127.0.0.1", server.localPort, "offline-fixture", "offline-only")
                assertTrue(savedSetup.save(account, ""))
                val registered = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    RasvenCallDomain.initialize(context)
                    RasvenCallDomain.setDoNotDisturb(false)
                    RasvenIax2RegistrationGateway.start(
                        account,
                    ) { if (it == RasvenRegistrationResult.Registered) registered.countDown() }
                }
                val packet = DatagramPacket(ByteArray(4096), 4096)
                server.receive(packet)
                val request = RasvenIax2FrameCodec.decode(packet.data.copyOfRange(0, packet.length))!!
                val client = packet.socketAddress
                fun send(frame: RasvenIax2FullFrame) {
                    val bytes = RasvenIax2FrameCodec.encode(frame)
                    server.send(DatagramPacket(bytes, bytes.size, client))
                }
                send(RasvenIax2FullFrame(42, request.sourceCallNumber, false, 1, 0, 1,
                    RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.REGACK,
                    informationElements = listOf(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH, 60))))
                assertTrue(registered.await(4, TimeUnit.SECONDS))
                server.soTimeout = 250
                peerThread = Thread({
                    while (!stopPeer.get()) {
                        try {
                            val inbound = DatagramPacket(ByteArray(4096), 4096)
                            server.receive(inbound)
                            val frame = RasvenIax2FrameCodec.decode(inbound.data.copyOfRange(0, inbound.length)) ?: continue
                            if (frame.destinationCallNumber !in 501..505) continue
                            clientLegs[frame.destinationCallNumber] = frame.sourceCallNumber
                            nextClientSequence[frame.destinationCallNumber] = (frame.oSeqNo + 1) and 255
                            if (frame.frameType == RasvenIax2.FrameType.CONTROL && frame.subclass == RasvenIax2.ControlSubclass.ANSWER) {
                                answeredPeers.add(frame.destinationCallNumber)
                            }
                            if (frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass == RasvenIax2.IaxSubclass.ACK) continue
                            send(RasvenIax2FullFrame(frame.destinationCallNumber, frame.sourceCallNumber, false, frame.timestamp, 1,
                                (frame.oSeqNo + 1) and 255, RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.ACK))
                        } catch (_: SocketTimeoutException) { }
                    }
                }, "offline-incoming-peer").apply { start() }
                repeat(5) { attempt ->
                val peerCall = 501 + attempt
                instrumentation.runOnMainSync {
                    context.getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
                }
                shell("input keyevent 223")
                assertTrue("the fixture must start with the actual screen off", await { !power.isInteractive })
                assertTrue("the device must be locked before incoming setup", await(8000L) { keyguard.isKeyguardLocked })
                send(RasvenIax2FullFrame(peerCall, 0, false, 1, 0, 0, RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.NEW,
                    informationElements = listOf(
                        RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.VERSION, RasvenIax2.VERSION),
                        RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.CALLING_NUMBER, "offline-fixture"),
                        RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.CAPABILITY, RasvenIax2.MediaFormat.PCMU.toLong()),
                        RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, RasvenIax2.MediaFormat.PCMU.toLong()),
                    )))
                assertTrue("local UDP setup must reach the real Product ringing owner", await {
                    RasvenCallDomain.incomingState is RasvenIncomingCallState.Ringing
                })
                productCallId = (RasvenCallDomain.incomingState as RasvenIncomingCallState.Ringing).call.sessionId
                val fullScreen = await {
                    var resumedIncoming = false
                    instrumentation.runOnMainSync {
                        resumedIncoming = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<BootstrapActivity>().any {
                                it.intent.action == RasvenCallNotification.ACTION_OPEN_INCOMING && it.hasWindowFocus()
                            }
                    }
                    power.isInteractive && resumedIncoming
                }
                shell("screencap -p /data/local/tmp/rasven-offline-incoming-locked.png")
                assertTrue("incoming must wake the display and resume the full-screen activity over the lock screen", fullScreen)
                assertTrue("fixture must remain locked while the incoming activity is visible", keyguard.isKeyguardLocked)
                val notification = notifications.activeNotifications.single { it.notification.category == android.app.Notification.CATEGORY_CALL }
                    .notification
                val answer = notification.actions.firstOrNull {
                    it.title.toString().contains("Answer", ignoreCase = true) || it.title.toString().contains("پاسخ")
                } ?: error("CallStyle must expose an answer action")
                answer.actionIntent.send()
                assertTrue("answer ${attempt + 1} must reach the same UDP call leg", await {
                    peerCall in answeredPeers
                })
                assertTrue("media startup must reach Product Active", await(8000L) {
                    RasvenCallDomain.incomingState is RasvenIncomingCallState.Active
                })
                Thread.sleep(3000)
                assertTrue("answered call must remain Active instead of returning home",
                    RasvenCallDomain.incomingState is RasvenIncomingCallState.Active)
                send(RasvenIax2FullFrame(peerCall, clientLegs.getValue(peerCall), false, 5000, 1,
                    nextClientSequence.getValue(peerCall), RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.HANGUP))
                assertTrue("remote hangup must finish the actual Product call", await {
                    RasvenCallDomain.incomingState is RasvenIncomingCallState.Ended
                })
                // Deliberately do not dismiss Ended. A locked user must receive the next call
                // without opening the app, reconnecting, or clearing its completed summary.
                }
                assertEquals("All five consecutive locked/task-removed calls must be answered", 5, answeredPeers.size)
            } finally {
                instrumentation.runOnMainSync {
                    productCallId?.let { RasvenCallDomain.requestReject(it) || RasvenCallDomain.requestEnd(it) }
                    RasvenIax2RegistrationGateway.invalidate()
                    RasvenCallDomain.closeMissedSurface()
                    RasvenCallDomain.closeCallEndSummary()
                }
                stopPeer.set(true)
                peerThread?.join(1000)
                assertTrue(savedSetup.clear())
                if (wasInteractive) shell("input keyevent 224")
            }
        }
    }
}
