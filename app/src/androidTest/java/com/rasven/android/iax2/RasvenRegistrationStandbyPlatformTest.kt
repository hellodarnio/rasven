package com.rasven.android.iax2

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.rasven.android.app.BootstrapActivity
import com.rasven.android.product.setup.RasvenIax2RegistrationGateway
import com.rasven.android.product.setup.RasvenRegistrationResult
import com.rasven.android.product.setup.RasvenValidatedAccount
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RasvenRegistrationStandbyPlatformTest {
    @Before fun requireIsolatedQaPackage() {
        assertEquals("Run these registration fixtures with the separate QA application ID",
            "com.rasven.android.qa.debug", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
    }

    @Test fun sameAccountCanRetryAfterFailureWhileNoUiObserverWasAttached() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, BootstrapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 4000
            val account = RasvenValidatedAccount("127.0.0.1", server.localPort, "fixture", "fixture-not-owner")
            val registered = CountDownLatch(1)
            try {
                instrumentation.runOnMainSync {
                    RasvenIax2Runtime.initialize(context)
                    RasvenIax2RegistrationGateway.start(account) {}
                    RasvenIax2RegistrationGateway.detachObserver()
                }
                val packet = DatagramPacket(ByteArray(4096), 4096)
                server.receive(packet)
                assertTrue("standby should start before rejection", awaitStandbyActive(true))
                val first = RasvenIax2FrameCodec.decode(packet.data.copyOfRange(0, packet.length))!!
                val rejection = RasvenIax2FrameCodec.encode(RasvenIax2FullFrame(42, first.sourceCallNumber,
                    false, 1, 0, 1, RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.REGREJ))
                server.send(DatagramPacket(rejection, rejection.size, packet.address, packet.port))
                server.receive(packet)
                assertEquals(RasvenIax2.IaxSubclass.ACK,
                    RasvenIax2FrameCodec.decode(packet.data.copyOfRange(0, packet.length))!!.subclass)
                // The ACK is sent on the UDP thread before its failure callback is posted to UI.
                // Wait for the resulting service shutdown, then drain UI before retrying.
                assertTrue("rejection should stop standby", awaitStandbyActive(false))
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    RasvenIax2RegistrationGateway.start(account) {
                        if (it == RasvenRegistrationResult.Registered) registered.countDown()
                    }
                }
                server.receive(packet)
                val retry = RasvenIax2FrameCodec.decode(packet.data.copyOfRange(0, packet.length))!!
                assertEquals(RasvenIax2.IaxSubclass.REGREQ, retry.subclass)
                val ack = RasvenIax2FrameCodec.encode(RasvenIax2FullFrame(43, retry.sourceCallNumber,
                    false, 1, 0, 1, RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.REGACK,
                    informationElements = listOf(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH, 60))))
                server.send(DatagramPacket(ack, ack.size, packet.address, packet.port))
                assertTrue(registered.await(4, TimeUnit.SECONDS))
            } finally {
                instrumentation.runOnMainSync { RasvenIax2RegistrationGateway.invalidate() }
            }
        }
    }

    @Test fun realStandbyServiceAndUdpRegistrationSurviveUiObserverReplacementWithoutDuplicateRegreq() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, BootstrapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 4000
            val account = RasvenValidatedAccount("127.0.0.1", server.localPort, "fixture", "fixture-not-owner")
            val first = CountDownLatch(1)
            val second = CountDownLatch(1)
            try {
                instrumentation.runOnMainSync {
                    RasvenIax2Runtime.initialize(context)
                    RasvenIax2RegistrationGateway.start(account) { if (it == RasvenRegistrationResult.Registered) first.countDown() }
                }
                val packet = DatagramPacket(ByteArray(4096), 4096)
                server.receive(packet)
                val request = RasvenIax2FrameCodec.decode(packet.data.copyOfRange(0, packet.length))!!
                assertEquals(RasvenIax2.IaxSubclass.REGREQ, request.subclass)
                val ack = RasvenIax2FrameCodec.encode(RasvenIax2FullFrame(42, request.sourceCallNumber, false,
                    1, 0, 1, RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.REGACK,
                    informationElements = listOf(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH, 60))))
                server.send(DatagramPacket(ack, ack.size, packet.address, packet.port))
                assertTrue(first.await(4, TimeUnit.SECONDS))
                server.receive(packet)
                assertEquals(RasvenIax2.IaxSubclass.ACK, RasvenIax2FrameCodec.decode(packet.data.copyOfRange(0, packet.length))!!.subclass)
                instrumentation.runOnMainSync {
                    RasvenIax2RegistrationGateway.detachObserver()
                    assertTrue(RasvenIax2Runtime.isRegistrationAlive())
                    // A newly created Activity decrypts a new account object with identical fields.
                    val restoredAccount = RasvenValidatedAccount(account.server, account.port, account.username, account.password)
                    RasvenIax2RegistrationGateway.start(restoredAccount) {
                        if (it == RasvenRegistrationResult.Registered) second.countDown()
                    }
                }
                assertTrue(second.await(4, TimeUnit.SECONDS))
                assertTrue(RasvenRegistrationForegroundService.isActiveForDiagnostics())
                server.soTimeout = 300
                try {
                    server.receive(packet)
                    fail("Reattaching UI must not create another protocol request")
                } catch (_: SocketTimeoutException) { }
            } finally {
                instrumentation.runOnMainSync { RasvenIax2RegistrationGateway.invalidate() }
            }
            val deadline = System.currentTimeMillis() + 4000
            while (RasvenRegistrationForegroundService.isActiveForDiagnostics() && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertFalse(RasvenRegistrationForegroundService.isActiveForDiagnostics())
        }
    }

    private fun awaitStandbyActive(expected: Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 4_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (RasvenRegistrationForegroundService.isActiveForDiagnostics() == expected) return true
            Thread.sleep(20)
        }
        return RasvenRegistrationForegroundService.isActiveForDiagnostics() == expected
    }
}
