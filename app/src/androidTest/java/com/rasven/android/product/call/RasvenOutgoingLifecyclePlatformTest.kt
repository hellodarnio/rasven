package com.rasven.android.product.call

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.rasven.android.app.BootstrapActivity
import com.rasven.android.iax2.*
import com.rasven.android.product.settings.RasvenRequiredCallAccess
import com.rasven.android.product.setup.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

/** Screen-on, short loopback fixtures only. No live account, radio change, keyguard or Doze. */
class RasvenOutgoingLifecyclePlatformTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun actualRingingCallSurvivesNewUiAndInvalidationRetiresCallAndMedia() = fixture { peer, account ->
        main { RasvenIax2RegistrationGateway.start(account) {} }
        assertTrue(await { peer.registrations.get() >= 1 && RasvenRegistrationForegroundService.isActiveForDiagnostics() })
        // An observer receives the existing success without creating a second registration.
        val registered = AtomicBoolean(false)
        main { RasvenIax2RegistrationGateway.start(account) { registered.set(it is RasvenRegistrationResult.Registered) } }
        assertTrue(await { registered.get() })
        val owner = RasvenProcessOutgoingCall.current()
        main {
            assertTrue(owner.requestStart("305", RasvenOutgoingCallOrigin.HomeDirectDial, true, RasvenRegistrationState.Registered))
        }
        assertTrue("Actual runtime must forward Ringing to Product", await { owner.state is RasvenOutgoingCallState.Ringing })
        peer.answerOutgoing()
        assertTrue("Platform/audio startup must precede Connected", await(8000) { owner.state is RasvenOutgoingCallState.ConnectedBoundary })
        assertTrue(RasvenCallMediaForegroundService.isActiveForDiagnostics())
        val session = (owner.state as RasvenOutgoingCallState.ConnectedBoundary).session
        val oldActivity = resumedActivity()
        main { context.getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() } }
        assertTrue(await { resumedActivity() == null })
        val notification = context.getSystemService(NotificationManager::class.java).activeNotifications
            .single { it.notification.channelId == RasvenCallNotification.ONGOING_CHANNEL }.notification
        assertNotNull("Ongoing call notification must return to its call UI", notification.contentIntent)
        notification.contentIntent.send()
        assertTrue(await { resumedActivity()?.let { it !== oldActivity } == true })
        assertSame("Fresh Activity must reattach the exact process owner", owner, RasvenProcessOutgoingCall.current())
        assertEquals(session, (owner.state as RasvenOutgoingCallState.ConnectedBoundary).session)
        assertEquals("Reopening must not replay outgoing setup", 1, peer.outgoingSetups.get())
        assertTrue("Active controls must be visible, not Home", await { hasEndAction() })
        main { RasvenIax2RegistrationGateway.invalidate() }
        assertTrue("Invalidation must terminate Product call", await { owner.state is RasvenOutgoingCallState.Ended })
        assertTrue("Media service must be released", await { !RasvenCallMediaForegroundService.isActiveForDiagnostics() })
        assertTrue("Platform call notification must be retired", await {
            context.getSystemService(NotificationManager::class.java).activeNotifications.none {
                it.notification.category == android.app.Notification.CATEGORY_CALL
            }
        })
        peer.assertHealthy()
    }

    @Test fun transientRefreshFailureRecoversWithNoUiObserverAndDisconnectCancelsRecovery() = fixture { peer, account ->
        peer.failSecondRegistration.set(true)
        val registered = AtomicBoolean(false)
        main { RasvenIax2RegistrationGateway.start(account) { registered.set(it is RasvenRegistrationResult.Registered) } }
        assertTrue(await { registered.get() })
        main { RasvenIax2RegistrationGateway.detachObserver() }
        assertTrue("Retry must recover the third registration without a UI observer", await(8000) { peer.registrations.get() >= 3 })
        val reattached = AtomicBoolean(false)
        main { RasvenIax2RegistrationGateway.start(account) { reattached.set(it is RasvenRegistrationResult.Registered) } }
        assertTrue(await { reattached.get() })
        val released = AtomicBoolean(false)
        main { RasvenIax2RegistrationGateway.disconnect { released.set(it) } }
        assertTrue(await { released.get() })
        val before = peer.registrations.get()
        Thread.sleep(1600)
        assertEquals("User disconnect must not be undone by scheduled retry", before, peer.registrations.get())
        assertFalse(RasvenIax2Runtime.isRegistrationAlive())
        assertTrue(await { !RasvenRegistrationForegroundService.isActiveForDiagnostics() })
        peer.assertHealthy()
    }

    private fun fixture(body: (Peer, RasvenValidatedAccount) -> Unit) {
        assertEquals("Never use the real account package", "com.rasven.android.qa.debug", context.packageName)
        RasvenRequiredCallAccess.setTestOverride(null)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        shell("cmd appops set ${context.packageName} USE_FULL_SCREEN_INTENT allow")
        val repository = RasvenSecureSetupRepository(context)
        assertTrue(repository.clear())
        context.getSharedPreferences("rasven_user_connection_v1", Context.MODE_PRIVATE).edit().putBoolean("paused", false).commit()
        main {
            RasvenCallDomain.initialize(context)
            clearOutgoingPresentation()
            context.startActivity(Intent(context, BootstrapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        assertTrue(await { resumedActivity() != null })
        Peer().use { peer ->
            val account = RasvenValidatedAccount("127.0.0.1", peer.port, "offline-fixture", "offline-only")
            assertTrue(repository.save(account, ""))
            try { body(peer, account) }
            finally {
                main {
                    RasvenProcessOutgoingCall.current().cancel()
                    RasvenIax2RegistrationGateway.invalidate()
                    RasvenCallDomain.closeMissedSurface()
                    RasvenCallDomain.closeCallEndSummary()
                }
                instrumentation.waitForIdleSync()
                main {
                    clearOutgoingPresentation()
                    context.getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
                }
                assertTrue(repository.clear())
            }
        }
    }

    private fun clearOutgoingPresentation() {
        RasvenProcessOutgoingCall.current().apply { closeCancelled(); closeFailureSurface(); closeCallEndSummary() }
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun resumedActivity(): BootstrapActivity? {
        var result: BootstrapActivity? = null
        main {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<BootstrapActivity>().firstOrNull()
            result?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        return result
    }
    private fun await(timeout: Long = 5000, predicate: () -> Boolean): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeout
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return true
            Thread.sleep(40)
        }
        return predicate()
    }
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
    private fun hasEndAction(): Boolean {
        fun find(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
            val endLabels = setOf("پایان تماس", "End call")
            if (node.contentDescription?.toString() in endLabels || node.text?.toString() in endLabels) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let { if (find(it)) return true }
            return false
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::find) == true
    }

    private class Peer : AutoCloseable {
        private val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 100 }
        val port: Int get() = socket.localPort
        val registrations = AtomicInteger()
        val outgoingSetups = AtomicInteger()
        val failSecondRegistration = AtomicBoolean(false)
        private val stopped = AtomicBoolean(false)
        private val failure = AtomicReference<Throwable?>()
        @Volatile private var outgoingAddress: SocketAddress? = null
        @Volatile private var outgoingClientCall = 0
        private val thread = Thread({
            while (!stopped.get()) {
                try {
                    val packet = DatagramPacket(ByteArray(4096), 4096)
                    socket.receive(packet)
                    val frame = RasvenIax2FrameCodec.decode(packet.data.copyOfRange(0, packet.length)) ?: continue
                    when {
                        frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass == RasvenIax2.IaxSubclass.REGREQ -> {
                            val count = registrations.incrementAndGet()
                            val invalid = failSecondRegistration.get() && count == 2
                            send(RasvenIax2FullFrame(42, frame.sourceCallNumber, false, frame.timestamp, 0, (frame.oSeqNo + 1) and 255,
                                RasvenIax2.FrameType.IAX, if (invalid) RasvenIax2.IaxSubclass.INVAL else RasvenIax2.IaxSubclass.REGACK,
                                informationElements = if (invalid) emptyList() else listOf(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH,
                                    if (failSecondRegistration.get() && count == 1) 1 else 60))), packet.socketAddress)
                        }
                        frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass == RasvenIax2.IaxSubclass.REGREL ->
                            send(RasvenIax2FullFrame(42, frame.sourceCallNumber, false, frame.timestamp, 0, (frame.oSeqNo + 1) and 255,
                                RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.REGACK), packet.socketAddress)
                        frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass == RasvenIax2.IaxSubclass.NEW -> {
                            outgoingSetups.incrementAndGet(); outgoingAddress = packet.socketAddress; outgoingClientCall = frame.sourceCallNumber
                            send(RasvenIax2FullFrame(401, frame.sourceCallNumber, false, 1, 0, 1, RasvenIax2.FrameType.IAX,
                                RasvenIax2.IaxSubclass.ACCEPT, informationElements = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))), packet.socketAddress)
                            send(RasvenIax2FullFrame(401, frame.sourceCallNumber, false, 2, 1, 1, RasvenIax2.FrameType.CONTROL,
                                RasvenIax2.ControlSubclass.RINGING), packet.socketAddress)
                        }
                        frame.destinationCallNumber == 401 && !(frame.frameType == RasvenIax2.FrameType.IAX && frame.subclass == RasvenIax2.IaxSubclass.ACK) ->
                            send(RasvenIax2FullFrame(401, frame.sourceCallNumber, false, frame.timestamp, 3, (frame.oSeqNo + 1) and 255,
                                RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.ACK), packet.socketAddress)
                    }
                } catch (_: SocketTimeoutException) { }
                catch (error: Throwable) { if (!stopped.get()) failure.compareAndSet(null, error) }
            }
        }, "rasven-offline-outgoing-peer").apply { start() }
        private fun send(frame: RasvenIax2FullFrame, address: SocketAddress) {
            val bytes = RasvenIax2FrameCodec.encode(frame)
            socket.send(DatagramPacket(bytes, bytes.size, address))
        }
        fun answerOutgoing() = send(RasvenIax2FullFrame(401, outgoingClientCall, false, 3, 2, 1,
            RasvenIax2.FrameType.CONTROL, RasvenIax2.ControlSubclass.ANSWER), checkNotNull(outgoingAddress))
        fun assertHealthy() { assertNull("Offline peer error", failure.get()) }
        override fun close() { stopped.set(true); socket.close(); thread.join(1000) }
    }
}
