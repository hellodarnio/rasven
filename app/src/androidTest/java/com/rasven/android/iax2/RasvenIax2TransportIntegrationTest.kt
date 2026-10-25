package com.rasven.android.iax2

import com.rasven.android.product.call.RasvenOutgoingCallAttempt
import com.rasven.android.product.call.RasvenOutgoingCallGateway
import com.rasven.android.product.call.RasvenOutgoingCallOrigin
import com.rasven.android.product.call.RasvenOutgoingCallState
import com.rasven.android.product.call.RasvenOutgoingCallViewModel
import com.rasven.android.product.call.RasvenOutgoingConnectedSession
import com.rasven.android.product.call.RasvenOutgoingStartDisposition
import com.rasven.android.product.call.RasvenOutgoingTransportEvent
import com.rasven.android.product.setup.RasvenRegistrationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Packet-level Android proof: Product coordinator -> real IAX2 engine -> deterministic peer -> Product state. */
class RasvenIax2TransportIntegrationTest {
    private class PacketTransport : RasvenIax2PacketTransport {
        private var receiver: ((ByteArray) -> Unit)? = null
        val sent = mutableListOf<ByteArray>()
        override var isReady: Boolean = false
            private set

        override fun start(onReady: () -> Unit, onPacket: (ByteArray) -> Unit, onFailure: () -> Unit) {
            receiver = onPacket
            isReady = true
            onReady()
        }

        override fun send(packet: ByteArray): Boolean {
            if (!isReady) return false
            sent += packet.copyOf()
            return true
        }

        fun inject(frame: RasvenIax2FullFrame) {
            receiver?.invoke(RasvenIax2FrameCodec.encode(frame))
        }

        override fun close() { isReady = false }
    }

    private class NoopScheduler : RasvenIax2Scheduler {
        override fun schedule(delayMs: Long, task: () -> Unit): RasvenIax2Cancellation = RasvenIax2Cancellation { }
        override fun close() = Unit
    }

    @Test fun coordinatorReceivesBusyFromRealPacketDrivenIax2Session() {
        val packetTransport = PacketTransport()
        val engine = RasvenIax2SignalingEngine(
            account = RasvenIax2Account("127.0.0.1", 4569, "fixture-user", "fixture-secret"),
            transport = packetTransport,
            scheduler = NoopScheduler(),
            clock = RasvenIax2Clock { 1000L },
        )

        var registration: RasvenIax2RegistrationEvent? = null
        engine.register { registration = it }
        val regReq = packetTransport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.subclass == RasvenIax2.IaxSubclass.REGREQ }
        packetTransport.inject(
            RasvenIax2FullFrame(
                sourceCallNumber = 300,
                destinationCallNumber = regReq.sourceCallNumber,
                retransmission = false,
                timestamp = 1,
                oSeqNo = 0,
                iSeqNo = 1,
                frameType = RasvenIax2.FrameType.IAX,
                subclass = RasvenIax2.IaxSubclass.REGACK,
                informationElements = listOf(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH, 60)),
            ),
        )
        assertEquals(RasvenIax2RegistrationEvent.Registered, registration)

        val gateway = object : RasvenOutgoingCallGateway {
            override fun start(
                attempt: RasvenOutgoingCallAttempt,
                onEvent: (RasvenOutgoingTransportEvent) -> Unit,
            ): RasvenOutgoingStartDisposition {
                val result = engine.startOutgoing(
                    attemptId = attempt.id,
                    destination = attempt.destination,
                    mediaOffer = RasvenIax2MediaOffer(4, 4, "ulaw"),
                ) { event ->
                    onEvent(
                        when (event) {
                            RasvenIax2OutgoingEvent.Ringing -> RasvenOutgoingTransportEvent.Ringing
                            RasvenIax2OutgoingEvent.Busy -> RasvenOutgoingTransportEvent.Busy
                            RasvenIax2OutgoingEvent.Rejected -> RasvenOutgoingTransportEvent.Rejected
                            RasvenIax2OutgoingEvent.NoAnswer -> RasvenOutgoingTransportEvent.NoAnswer
                            RasvenIax2OutgoingEvent.NetworkFailure -> RasvenOutgoingTransportEvent.NetworkFailure
                            is RasvenIax2OutgoingEvent.Connected ->
                                RasvenOutgoingTransportEvent.Connected(RasvenOutgoingConnectedSession(event.sessionId))
                            is RasvenIax2OutgoingEvent.PeerHoldChanged -> if (event.held) RasvenOutgoingTransportEvent.PeerHeld else RasvenOutgoingTransportEvent.PeerResumed
                            RasvenIax2OutgoingEvent.RemoteHangup,
                            RasvenIax2OutgoingEvent.OtherFailure -> RasvenOutgoingTransportEvent.OtherFailure
                        },
                    )
                }
                return if (result == RasvenIax2StartResult.Accepted) {
                    RasvenOutgoingStartDisposition.Accepted
                } else {
                    RasvenOutgoingStartDisposition.TransportUnavailable
                }
            }

            override fun cancel(attemptId: Long) {
                engine.cancelOutgoing(attemptId)
            }
        }

        val coordinator = RasvenOutgoingCallViewModel(gateway)
        assertTrue(
            coordinator.requestStart(
                destination = "305",
                origin = RasvenOutgoingCallOrigin.HomeDirectDial,
                microphoneGranted = true,
                registrationState = RasvenRegistrationState.Registered,
            ),
        )
        val new = packetTransport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.subclass == RasvenIax2.IaxSubclass.NEW }

        packetTransport.inject(
            RasvenIax2FullFrame(
                sourceCallNumber = 400,
                destinationCallNumber = new.sourceCallNumber,
                retransmission = false,
                timestamp = 1,
                oSeqNo = 0,
                iSeqNo = 1,
                frameType = RasvenIax2.FrameType.IAX,
                subclass = RasvenIax2.IaxSubclass.ACCEPT,
                informationElements = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4)),
            ),
        )
        packetTransport.inject(
            RasvenIax2FullFrame(
                sourceCallNumber = 400,
                destinationCallNumber = new.sourceCallNumber,
                retransmission = false,
                timestamp = 2,
                oSeqNo = 1,
                iSeqNo = 1,
                frameType = RasvenIax2.FrameType.CONTROL,
                subclass = RasvenIax2.ControlSubclass.BUSY,
            ),
        )

        assertTrue(coordinator.state is RasvenOutgoingCallState.Busy)
        assertEquals("305", (coordinator.state as RasvenOutgoingCallState.Busy).attempt.destination)
        engine.close()
    }
}
