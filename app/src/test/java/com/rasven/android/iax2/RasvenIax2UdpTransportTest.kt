package com.rasven.android.iax2

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class RasvenIax2UdpTransportTest {
    @Test fun closingFromReceiveCallbackDrainsFinalControlAckButDropsQueuedVoice() {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { peer ->
            peer.soTimeout = 2000
            val ready = CountDownLatch(1)
            val senderHeld = CountDownLatch(1)
            val releaseSender = CountDownLatch(1)
            val closeRequested = CountDownLatch(1)
            val transport = RasvenIax2UdpTransport("127.0.0.1", peer.localPort) { message ->
                if (message.startsWith("TX ") && message.contains("subclass=${RasvenIax2.IaxSubclass.POKE} ")) {
                    senderHeld.countDown()
                    releaseSender.await(2, TimeUnit.SECONDS)
                }
            }
            fun frame(type: Int, subclass: Int, source: Int = 9, destination: Int = 501) =
                RasvenIax2FrameCodec.encode(RasvenIax2FullFrame(source, destination, false, 1, 0, 1,
                    type, subclass, payload = if (type == RasvenIax2.FrameType.VOICE) byteArrayOf(0) else byteArrayOf()))
            try {
                transport.start({ ready.countDown() }, {
                    assertTrue(transport.send(frame(RasvenIax2.FrameType.VOICE, RasvenIax2.MediaFormat.PCMU)))
                    assertTrue(transport.send(frame(RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.ACK)))
                    transport.close()
                    closeRequested.countDown()
                }, { ready.countDown() })
                assertTrue(ready.await(2, TimeUnit.SECONDS))
                assertTrue(transport.send(frame(RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.POKE, destination = 0)))
                val probe = DatagramPacket(ByteArray(128), 128)
                peer.receive(probe)
                assertTrue(senderHeld.await(2, TimeUnit.SECONDS))
                val reply = frame(RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.REGACK, source = 501, destination = 9)
                peer.send(DatagramPacket(reply, reply.size, probe.socketAddress))
                assertTrue(closeRequested.await(2, TimeUnit.SECONDS))
                releaseSender.countDown()
                val finalPacket = DatagramPacket(ByteArray(128), 128)
                peer.receive(finalPacket)
                val finalFrame = RasvenIax2FrameCodec.decode(finalPacket.data.copyOfRange(0, finalPacket.length))!!
                assertEquals("closing must not discard the final control ACK", RasvenIax2.FrameType.IAX, finalFrame.frameType)
                assertEquals(RasvenIax2.IaxSubclass.ACK, finalFrame.subclass)
                peer.soTimeout = 200
                try {
                    peer.receive(finalPacket)
                    fail("queued audio must not be sent after close is requested")
                } catch (_: java.net.SocketTimeoutException) { }
            } finally {
                releaseSender.countDown()
                transport.close()
            }
        }
    }

    @Test fun configuredRemotePortIsUsedByRealUdpSocket() {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { peer ->
            peer.soTimeout = 3000
            val ready = CountDownLatch(1)
            val received = CountDownLatch(1)
            var failure = false
            val payload = byteArrayOf(1, 2, 3, 4)
            RasvenIax2UdpTransport("127.0.0.1", peer.localPort).use { transport ->
                transport.start({ ready.countDown() }, {
                    assertArrayEquals(payload, it)
                    received.countDown()
                }, { failure = true; ready.countDown() })
                assertTrue(ready.await(3, TimeUnit.SECONDS))
                assertFalse("UDP setup must use the configured peer port, not socket.port=-1", failure)
                assertTrue(transport.isReady)
                assertTrue(transport.send(payload))
                val packet = DatagramPacket(ByteArray(64), 64)
                peer.receive(packet)
                assertArrayEquals(payload, packet.data.copyOf(packet.length))
                peer.send(DatagramPacket(payload, payload.size, packet.socketAddress))
                assertTrue(received.await(3, TimeUnit.SECONDS))
            }
        }
    }

    @Test fun acceptsAndRepliesToCallLegFromAlternatePortOnConfiguredServerAddress() {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { registrar ->
            DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { callPeer ->
                registrar.soTimeout = 3000
                callPeer.soTimeout = 3000
                val ready = CountDownLatch(1)
                val received = CountDownLatch(1)
                var receivedNew = false
                val incomingNew = RasvenIax2FrameCodec.encode(
                    RasvenIax2FullFrame(
                        sourceCallNumber = 431,
                        destinationCallNumber = 0,
                        retransmission = false,
                        timestamp = 1,
                        oSeqNo = 0,
                        iSeqNo = 0,
                        frameType = RasvenIax2.FrameType.IAX,
                        subclass = RasvenIax2.IaxSubclass.NEW,
                        informationElements = listOf(
                            RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.VERSION, RasvenIax2.VERSION),
                        ),
                    ),
                )
                RasvenIax2UdpTransport("127.0.0.1", registrar.localPort).use { transport ->
                    transport.start({ ready.countDown() }, {
                        receivedNew = RasvenIax2FrameCodec.decode(it)?.subclass == RasvenIax2.IaxSubclass.NEW
                        received.countDown()
                    }, { ready.countDown() })
                    assertTrue(ready.await(3, TimeUnit.SECONDS))
                    assertTrue(transport.isReady)

                    val probe = byteArrayOf(9)
                    assertTrue(transport.send(probe))
                    val addressProbe = DatagramPacket(ByteArray(8), 8)
                    registrar.receive(addressProbe)
                    val clientAddress = addressProbe.socketAddress

                    callPeer.send(DatagramPacket(incomingNew, incomingNew.size, clientAddress))
                    assertTrue("call-leg from a different source port must be received", received.await(3, TimeUnit.SECONDS))
                    assertTrue(receivedNew)

                    val reply = RasvenIax2FrameCodec.encode(
                        RasvenIax2FullFrame(
                            sourceCallNumber = 17,
                            destinationCallNumber = 431,
                            retransmission = false,
                            timestamp = 2,
                            oSeqNo = 0,
                            iSeqNo = 1,
                            frameType = RasvenIax2.FrameType.IAX,
                            subclass = RasvenIax2.IaxSubclass.ACK,
                        ),
                    )
                    assertTrue(transport.send(reply))
                    val response = DatagramPacket(ByteArray(64), 64)
                    callPeer.receive(response)
                    assertEquals(reply.size, response.length)
                    assertEquals(InetAddress.getByName("127.0.0.1"), response.address)
                }
            }
        }
    }
}
