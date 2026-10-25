package com.rasven.android.iax2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RasvenG711Test {
    @Test fun pcmuGoldenVectorsMatchReferenceValues() {
        val samples = shortArrayOf(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE)
        val encoded = RasvenG711.encodePcm16(RasvenAudioCodec.PCMU, samples)
        assertArrayEquals(
            byteArrayOf(0xff.toByte(), 0xce.toByte(), 0x4e, 0x80.toByte(), 0x00),
            encoded,
        )
    }

    @Test fun pcmaGoldenVectorsMatchReferenceValues() {
        val samples = shortArrayOf(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE)
        val encoded = RasvenG711.encodePcm16(RasvenAudioCodec.PCMA, samples)
        assertArrayEquals(
            byteArrayOf(0xd5.toByte(), 0xfa.toByte(), 0x7a, 0xaa.toByte(), 0x2a),
            encoded,
        )
    }

    @Test fun pcmuDecodeGoldenVectorsMatchReferenceValues() {
        val encoded = byteArrayOf(0xff.toByte(), 0xce.toByte(), 0x4e, 0x80.toByte(), 0x00)
        assertArrayEquals(shortArrayOf(0, 988, -988, 32124, -32124), RasvenG711.decodeToPcm16(RasvenAudioCodec.PCMU, encoded))
    }

    @Test fun pcmaDecodeGoldenVectorsMatchReferenceValues() {
        val encoded = byteArrayOf(0xd5.toByte(), 0xfa.toByte(), 0x7a, 0xaa.toByte(), 0x2a)
        assertArrayEquals(shortArrayOf(8, 1008, -1008, 32256, -32256), RasvenG711.decodeToPcm16(RasvenAudioCodec.PCMA, encoded))
    }


    @Test fun bothCodecsAreDeterministicAndDoNotMutateInput() {
        val source = ShortArray(160) { i -> ((i - 80) * 321).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
        val original = source.copyOf()
        for (codec in RasvenAudioCodec.entries) {
            val a = RasvenG711.encodePcm16(codec, source)
            val b = RasvenG711.encodePcm16(codec, source)
            assertArrayEquals(a, b)
            assertEquals(160, a.size)
            val decoded = RasvenG711.decodeToPcm16(codec, a)
            assertEquals(160, decoded.size)
            assertTrue(decoded.zip(source.toList()).all { (actual, expected) -> abs(actual.toInt() - expected.toInt()) <= 1024 })
        }
        assertArrayEquals(original, source)
    }
    @Test fun pcmuSegmentBoundaryVectorsMatchReferenceImplementation() {
        val samples = shortArrayOf(-31612, -30588, -100, -10, -1, 0, 1, 100, 1926, 16358)
        val encoded = RasvenG711.encodePcm16(RasvenAudioCodec.PCMU, samples)
        assertArrayEquals(
            byteArrayOf(0x00, 0x01, 0x72, 0x7d, 0x7e, 0xff.toByte(), 0xff.toByte(), 0xf2.toByte(), 0xbf.toByte(), 0x8f.toByte()),
            encoded,
        )
    }

}
