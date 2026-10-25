package com.rasven.android.iax2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenIax2MediaFrameTest {
    @Test fun miniVoiceFrameRoundTripsWithFourByteHeader() {
        val payload = ByteArray(160) { it.toByte() }
        val frame = RasvenIax2MiniFrame(0x1234, 0xfff0, payload)
        val encoded = RasvenIax2MiniFrameCodec.encode(frame)
        assertEquals(164, encoded.size)
        assertEquals(0x12, encoded[0].toInt() and 0xff)
        assertEquals(0x34, encoded[1].toInt() and 0xff)
        val decoded = RasvenIax2MiniFrameCodec.decode(encoded)!!
        assertEquals(0x1234, decoded.sourceCallNumber)
        assertEquals(0xfff0, decoded.timestamp16)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test fun miniDecoderRejectsFullFrameAndTruncation() {
        assertNull(RasvenIax2MiniFrameCodec.decode(byteArrayOf()))
        assertNull(RasvenIax2MiniFrameCodec.decode(byteArrayOf(0, 1, 2, 3)))
        assertNull(RasvenIax2MiniFrameCodec.decode(byteArrayOf(0x80.toByte(), 1, 0, 1, 2)))
    }

    @Test fun fullVoiceFrameCarriesRawPayloadAndCompressedCodecSubclass() {
        val payload = ByteArray(160) { (255 - it).toByte() }
        val frame = RasvenIax2FullFrame(
            sourceCallNumber = 7,
            destinationCallNumber = 9,
            retransmission = false,
            timestamp = 32_780,
            oSeqNo = 3,
            iSeqNo = 4,
            frameType = RasvenIax2.FrameType.VOICE,
            subclass = RasvenIax2.MediaFormat.PCMU,
            compressedSubclass = true,
            payload = payload,
        )
        val decoded = RasvenIax2FrameCodec.decode(RasvenIax2FrameCodec.encode(frame))!!
        assertEquals(RasvenIax2.FrameType.VOICE, decoded.frameType)
        assertEquals(RasvenIax2.MediaFormat.PCMU, decoded.subclass)
        assertTrue(decoded.compressedSubclass)
        assertArrayEquals(payload, decoded.payload)
        assertTrue(decoded.informationElements.isEmpty())
    }

    @Test fun miniTimestampExtenderHandlesWrapAndOutOfOrderAroundBoundary() {
        val extender = RasvenIax2MiniTimestampExtender()
        assertEquals(65_520L, extender.extend(65_520))
        assertEquals(65_540L, extender.extend(4))
        assertEquals(65_530L, extender.extend(65_530))
        assertEquals(65_560L, extender.extend(24))
    }
}
