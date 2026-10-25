package com.rasven.android.iax2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenIax2MediaSignalingTest {
    private class FakeTransport : RasvenIax2PacketTransport {
        private var packetCallback: ((ByteArray) -> Unit)? = null
        private var readyCallback: (() -> Unit)? = null
        val sent = mutableListOf<ByteArray>()
        override var isReady: Boolean = false
            private set

        override fun start(onReady: () -> Unit, onPacket: (ByteArray) -> Unit, onFailure: () -> Unit) {
            packetCallback = onPacket
            readyCallback = onReady
            isReady = true
            onReady()
        }
        override fun send(packet: ByteArray): Boolean {
            if (!isReady) return false
            sent += packet.copyOf()
            return true
        }
        fun injectFull(frame: RasvenIax2FullFrame) { packetCallback?.invoke(RasvenIax2FrameCodec.encode(frame)) }
        fun injectMini(frame: RasvenIax2MiniFrame) { packetCallback?.invoke(RasvenIax2MiniFrameCodec.encode(frame)) }
        override fun close() { isReady = false }
    }

    private class NoopScheduler : RasvenIax2Scheduler {
        override fun schedule(delayMs: Long, task: () -> Unit): RasvenIax2Cancellation = RasvenIax2Cancellation { }
        override fun close() = Unit
    }

    private fun registeredEngine(transport: FakeTransport): RasvenIax2SignalingEngine {
        val engine = RasvenIax2SignalingEngine(
            RasvenIax2Account("127.0.0.1", 4569, "fixture-user", "fixture-secret"),
            transport,
            NoopScheduler(),
            RasvenIax2Clock { 1_000L },
        )
        engine.register { }
        val reg = transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last { it.subclass == RasvenIax2.IaxSubclass.REGREQ }
        transport.injectFull(
            RasvenIax2FullFrame(
                300, reg.sourceCallNumber, false, 0, 0, 1,
                RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.REGACK,
                informationElements = listOf(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH, 60)),
            ),
        )
        assertTrue(engine.isRegistered)
        return engine
    }

    @Test fun acceptMaySelectEitherActuallyAdvertisedG711Codec() {
        val transport = FakeTransport()
        val engine = registeredEngine(transport)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        val offer = RasvenIax2MediaOffer(
            format = RasvenIax2.MediaFormat.PCMU,
            capability = RasvenIax2.MediaFormat.PCMU or RasvenIax2.MediaFormat.PCMA,
        )
        assertEquals(RasvenIax2StartResult.Accepted, engine.startOutgoing(99, "305", offer, events::add))
        val new = transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last { it.subclass == RasvenIax2.IaxSubclass.NEW }
        assertEquals(12L, new.ieU32(RasvenIax2.IeType.CAPABILITY))
        assertEquals(4L, new.ieU32(RasvenIax2.IeType.FORMAT))
        assertNull(new.ie(RasvenIax2.IeType.CODEC_PREFS))

        transport.injectFull(
            RasvenIax2FullFrame(
                400, new.sourceCallNumber, false, 10, 0, 1,
                RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.ACCEPT,
                informationElements = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, RasvenIax2.MediaFormat.PCMA.toLong())),
            ),
        )
        transport.injectFull(
            RasvenIax2FullFrame(
                400, new.sourceCallNumber, false, 20, 1, 1,
                RasvenIax2.FrameType.CONTROL, RasvenIax2.ControlSubclass.ANSWER,
            ),
        )
        val connected = events.single() as RasvenIax2OutgoingEvent.Connected
        val channel = engine.openMediaChannel(connected.sessionId)
        assertNotNull(channel)
        assertEquals(RasvenAudioCodec.PCMA, channel!!.codec)
        engine.close()
    }

    @Test fun mediaChannelSendsRealVoiceFramesAndReceivesFullThenMiniMedia() {
        val transport = FakeTransport()
        val engine = registeredEngine(transport)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        val offer = RasvenIax2MediaOffer(4, 12)
        engine.startOutgoing(100, "305", offer, events::add)
        val new = transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last { it.subclass == RasvenIax2.IaxSubclass.NEW }
        transport.injectFull(
            RasvenIax2FullFrame(
                401, new.sourceCallNumber, false, 0, 0, 1,
                RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.ACCEPT,
                informationElements = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4)),
            ),
        )
        transport.injectFull(
            RasvenIax2FullFrame(
                401, new.sourceCallNumber, false, 20, 1, 1,
                RasvenIax2.FrameType.CONTROL, RasvenIax2.ControlSubclass.ANSWER,
            ),
        )
        val sessionId = (events.single() as RasvenIax2OutgoingEvent.Connected).sessionId
        val channel = engine.openMediaChannel(sessionId)!!
        val payload = ByteArray(160) { it.toByte() }
        assertTrue(channel.sendFullVoice(0, payload))
        val sentFull = transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last { it.frameType == RasvenIax2.FrameType.VOICE }
        assertEquals(4, sentFull.subclass)
        assertArrayEquals(payload, sentFull.payload)
        assertTrue(channel.sendMiniVoice(20, payload))
        val sentMini = transport.sent.mapNotNull(RasvenIax2MiniFrameCodec::decode).last()
        assertEquals(new.sourceCallNumber, sentMini.sourceCallNumber)
        assertEquals(20, sentMini.timestamp16)

        val received = mutableListOf<RasvenIax2InboundVoice>()
        channel.setReceiver(received::add)
        transport.injectFull(
            RasvenIax2FullFrame(
                401, new.sourceCallNumber, false, 40, 2, 2,
                RasvenIax2.FrameType.VOICE, 4, compressedSubclass = true, payload = payload,
            ),
        )
        transport.injectMini(RasvenIax2MiniFrame(401, 60, payload))
        assertEquals(2, received.size)
        assertTrue(received[0].fullFrame)
        assertTrue(!received[1].fullFrame)
        assertEquals(60L, received[1].timestampMs)
        engine.close()
    }

    @Test fun lateMiniFromRetiredCallDoesNotReachNewSession() {
        val transport = FakeTransport()
        val engine = registeredEngine(transport)
        val offer = RasvenIax2MediaOffer(4, 12)
        val eventsA = mutableListOf<RasvenIax2OutgoingEvent>()
        engine.startOutgoing(101, "111", offer, eventsA::add)
        val newA = transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last { it.subclass == RasvenIax2.IaxSubclass.NEW }
        transport.injectFull(RasvenIax2FullFrame(402, newA.sourceCallNumber, false, 0, 0, 1, 6, 7,
            informationElements = listOf(RasvenIax2FrameCodec.u32Ie(9, 4))))
        transport.injectFull(RasvenIax2FullFrame(402, newA.sourceCallNumber, false, 20, 1, 1, 4, 4))
        val chA = engine.openMediaChannel((eventsA.single() as RasvenIax2OutgoingEvent.Connected).sessionId)!!
        var staleDelivered = 0
        chA.setReceiver { staleDelivered += 1 }
        engine.cancelOutgoing(101)
        val hangup = transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last { it.subclass == RasvenIax2.IaxSubclass.HANGUP }
        transport.injectFull(RasvenIax2FullFrame(402, newA.sourceCallNumber, false, 30, 2, (hangup.oSeqNo + 1) and 0xff, 6, 4))

        val eventsB = mutableListOf<RasvenIax2OutgoingEvent>()
        engine.startOutgoing(102, "222", offer, eventsB::add)
        val newB = transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last { it.subclass == RasvenIax2.IaxSubclass.NEW }
        transport.injectMini(RasvenIax2MiniFrame(402, 40, ByteArray(160)))
        assertEquals(0, staleDelivered)
        assertTrue(eventsB.isEmpty())
        assertTrue(newA.sourceCallNumber != newB.sourceCallNumber)
        engine.close()
    }
    @Test fun miniIsRejectedUntilFullVoiceEstablishesCodecAndFullCanChangeRxCodec() {
        val transport = FakeTransport()
        val engine = registeredEngine(transport)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        val offer = RasvenIax2MediaOffer(RasvenIax2.MediaFormat.PCMU, RasvenIax2.MediaFormat.IMPLEMENTED_CAPABILITY)
        engine.startOutgoing(103, "305", offer, events::add)
        val new = transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last { it.subclass == RasvenIax2.IaxSubclass.NEW }
        transport.injectFull(
            RasvenIax2FullFrame(
                403, new.sourceCallNumber, false, 10, 0, 1,
                RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.ACCEPT,
                informationElements = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, RasvenIax2.MediaFormat.PCMU.toLong())),
            ),
        )
        transport.injectFull(
            RasvenIax2FullFrame(
                403, new.sourceCallNumber, false, 20, 1, 1,
                RasvenIax2.FrameType.CONTROL, RasvenIax2.ControlSubclass.ANSWER,
            ),
        )
        val channel = engine.openMediaChannel((events.single() as RasvenIax2OutgoingEvent.Connected).sessionId)!!
        val received = mutableListOf<RasvenIax2InboundVoice>()
        channel.setReceiver(received::add)
        val payload = ByteArray(160) { (it * 3).toByte() }

        transport.injectMini(RasvenIax2MiniFrame(403, 65_500, payload))
        assertTrue(received.isEmpty())

        transport.injectFull(
            RasvenIax2FullFrame(
                403, new.sourceCallNumber, false, 65_520, 2, 2,
                RasvenIax2.FrameType.VOICE, RasvenIax2.MediaFormat.PCMA,
                compressedSubclass = true,
                payload = payload,
            ),
        )
        transport.injectMini(RasvenIax2MiniFrame(403, 4, payload))
        assertEquals(2, received.size)
        assertEquals(RasvenAudioCodec.PCMA, received[0].codec)
        assertEquals(65_520L, received[0].timestampMs)
        assertEquals(RasvenAudioCodec.PCMA, received[1].codec)
        assertEquals(65_540L, received[1].timestampMs)
        engine.close()
    }

}
