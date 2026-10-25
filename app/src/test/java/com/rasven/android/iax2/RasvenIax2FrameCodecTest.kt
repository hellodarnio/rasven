package com.rasven.android.iax2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RasvenIax2FrameCodecTest {
    @Test fun asteriskStopIndicationControlRoundTripsWithoutAcceptingInvalidVoiceFormat() {
        val packet = byteArrayOf(0x80.toByte(), 1, 0, 2, 0, 0, 0, 9, 4, 3, 4, 0xff.toByte())
        val frame = RasvenIax2FrameCodec.decode(packet)!!
        assertEquals(-1, frame.subclass)
        assertArrayEquals(packet, RasvenIax2FrameCodec.encode(frame))
        packet[10] = RasvenIax2.FrameType.VOICE.toByte()
        assertNull(RasvenIax2FrameCodec.decode(packet + byteArrayOf(0x7f)))
    }

    @Test fun fullFrameByteFixtureEncodesAndDecodesExactly() {
        val frame = RasvenIax2FullFrame(
            sourceCallNumber = 1,
            destinationCallNumber = 2,
            retransmission = false,
            timestamp = 0x01020304,
            oSeqNo = 5,
            iSeqNo = 6,
            frameType = RasvenIax2.FrameType.IAX,
            subclass = RasvenIax2.IaxSubclass.NEW,
            informationElements = listOf(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.VERSION, 2)),
        )
        val expected = byteArrayOf(
            0x80.toByte(), 0x01, 0x00, 0x02,
            0x01, 0x02, 0x03, 0x04,
            0x05, 0x06, 0x06, 0x01,
            0x0b, 0x02, 0x00, 0x02,
        )
        val encoded = RasvenIax2FrameCodec.encode(frame)
        assertArrayEquals(expected, encoded)
        assertEquals(frame, RasvenIax2FrameCodec.decode(encoded))
    }

    @Test fun retransmissionBitRoundTripsWithoutCorruptingDestination() {
        val frame = RasvenIax2FullFrame(42, 77, true, 9, 1, 2, 6, 13)
        val decoded = RasvenIax2FrameCodec.decode(RasvenIax2FrameCodec.encode(frame))!!
        assertTrue(decoded.retransmission)
        assertEquals(77, decoded.destinationCallNumber)
    }

    @Test fun typedInformationElementsRoundTrip() {
        val frame = RasvenIax2FullFrame(
            10, 0, false, 0, 0, 0, 6, 13,
            informationElements = listOf(
                RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.USERNAME, "test-user"),
                RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH, 60),
                RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4),
            ),
        )
        val decoded = RasvenIax2FrameCodec.decode(RasvenIax2FrameCodec.encode(frame))!!
        assertEquals("test-user", decoded.ieString(RasvenIax2.IeType.USERNAME))
        assertEquals(60, decoded.ieU16(RasvenIax2.IeType.REFRESH))
        assertEquals(4L, decoded.ieU32(RasvenIax2.IeType.FORMAT))
    }

    @Test fun parserRejectsEmptyOneByteAndTruncatedHeaders() {
        assertNull(RasvenIax2FrameCodec.decode(byteArrayOf()))
        assertNull(RasvenIax2FrameCodec.decode(byteArrayOf(0x80.toByte())))
        for (size in 2..11) assertNull(RasvenIax2FrameCodec.decode(ByteArray(size)))
    }

    @Test fun parserRejectsMiniFrameAndIeLengthBeyondRemainingPayload() {
        assertNull(RasvenIax2FrameCodec.decode(ByteArray(12)))
        val hostile = byteArrayOf(
            0x80.toByte(), 0x01, 0, 0,
            0, 0, 0, 0,
            0, 0, 6, 1,
            0x06, 0x20, 0x01,
        )
        assertNull(RasvenIax2FrameCodec.decode(hostile))
    }

    @Test fun boundedRandomShortPacketsNeverCrashAndOnlyReturnValidatedFrames() {
        val random = Random(5456)
        repeat(300) {
            val bytes = ByteArray(random.nextInt(0, 80)) { random.nextInt(0, 256).toByte() }
            val decoded = RasvenIax2FrameCodec.decode(bytes)
            if (decoded != null) {
                assertTrue(bytes.size >= 12)
                assertTrue(decoded.sourceCallNumber in 0..RasvenIax2.MAX_CALL_NUMBER)
                assertTrue(decoded.destinationCallNumber in 0..RasvenIax2.MAX_CALL_NUMBER)
            }
        }
    }

    @Test fun maximumAllowedPacketWithInvalidIeMetadataFailsCleanly() {
        val bytes = ByteArray(RasvenIax2.MAX_UDP_PAYLOAD)
        bytes[0] = 0x80.toByte()
        bytes[10] = 0x06
        bytes[11] = 0x01
        // Fill with valid zero-length IEs except leave a final type octet without a length.
        var i = 12
        while (i + 1 < bytes.size) {
            bytes[i] = 1
            bytes[i + 1] = 0
            i += 2
        }
        if (i < bytes.size) bytes[i] = 1
        assertNull(RasvenIax2FrameCodec.decode(bytes))
    }

    @Test fun compressedSubclassPowerEncodingRoundTrips() {
        // Voice Full Frames are only valid when they carry media payload. Keep this
        // fixture protocol-valid while testing compressed subclass power encoding.
        val frame = RasvenIax2FullFrame(
            sourceCallNumber = 1,
            destinationCallNumber = 2,
            retransmission = false,
            timestamp = 0,
            oSeqNo = 0,
            iSeqNo = 0,
            frameType = RasvenIax2.FrameType.VOICE,
            subclass = 1 shl 10,
            compressedSubclass = true,
            payload = byteArrayOf(0x7f),
        )
        val decoded = RasvenIax2FrameCodec.decode(RasvenIax2FrameCodec.encode(frame))!!
        assertTrue(decoded.compressedSubclass)
        assertEquals(1 shl 10, decoded.subclass)
        assertFalse(decoded.retransmission)
        assertArrayEquals(frame.payload, decoded.payload)
    }
}
