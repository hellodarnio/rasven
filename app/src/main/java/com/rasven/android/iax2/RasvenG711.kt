package com.rasven.android.iax2

/** RFC 5456 media subclasses implemented end-to-end by Rasven. */
internal enum class RasvenAudioCodec(val iaxFormat: Int) {
    PCMU(0x00000004),
    PCMA(0x00000008),
    ;

    companion object {
        fun fromIaxFormat(value: Int): RasvenAudioCodec? = entries.firstOrNull { it.iaxFormat == value }
    }
}

internal data class RasvenAudioMediaFormat(
    val codec: RasvenAudioCodec,
    val sampleRateHz: Int = 8_000,
    val channels: Int = 1,
    val pcmBitsPerSample: Int = 16,
    val frameDurationMs: Int = 20,
) {
    val samplesPerFrame: Int = sampleRateHz * frameDurationMs / 1_000
    val encodedBytesPerFrame: Int = samplesPerFrame
    val pcmBytesPerFrame: Int = samplesPerFrame * 2

    init {
        require(sampleRateHz == 8_000)
        require(channels == 1)
        require(pcmBitsPerSample == 16)
        require(frameDurationMs > 0 && 1_000 % frameDurationMs == 0)
    }
}

/** Allocation-free-per-sample G.711 μ-law/A-law codec helpers. */
internal object RasvenG711 {
    private const val ULAW_BIAS = 0x84
    private const val ULAW_CLIP_SCALED = 8159
    private val ULAW_SEGMENT_END = intArrayOf(0x3F, 0x7F, 0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF, 0x1FFF)

    fun encodePcm16(codec: RasvenAudioCodec, pcm: ShortArray, sampleCount: Int = pcm.size): ByteArray {
        require(sampleCount in 0..pcm.size)
        val out = ByteArray(sampleCount)
        encodeInto(codec, pcm, sampleCount, out)
        return out
    }

    fun encodeInto(
        codec: RasvenAudioCodec,
        pcm: ShortArray,
        sampleCount: Int,
        output: ByteArray,
        outputOffset: Int = 0,
    ) {
        require(sampleCount in 0..pcm.size)
        require(outputOffset >= 0 && output.size - outputOffset >= sampleCount)
        for (i in 0 until sampleCount) {
            output[outputOffset + i] = when (codec) {
                RasvenAudioCodec.PCMU -> linearToMuLaw(pcm[i])
                RasvenAudioCodec.PCMA -> linearToALaw(pcm[i])
            }
        }
    }

    fun decodeToPcm16(codec: RasvenAudioCodec, encoded: ByteArray): ShortArray {
        val out = ShortArray(encoded.size)
        decodeInto(codec, encoded, encoded.size, out)
        return out
    }

    fun decodeInto(
        codec: RasvenAudioCodec,
        encoded: ByteArray,
        byteCount: Int,
        output: ShortArray,
        outputOffset: Int = 0,
    ) {
        require(byteCount in 0..encoded.size)
        require(outputOffset >= 0 && output.size - outputOffset >= byteCount)
        for (i in 0 until byteCount) {
            output[outputOffset + i] = when (codec) {
                RasvenAudioCodec.PCMU -> muLawToLinear(encoded[i])
                RasvenAudioCodec.PCMA -> aLawToLinear(encoded[i])
            }
        }
    }

    internal fun linearToMuLaw(sample: Short): Byte {
        // Reference 16-bit conversion from the CCITT/Sun G.711 implementation: scale to
        // 14-bit magnitude first, clip there, then add the scaled bias. Keeping this exact
        // ordering avoids off-by-one quantization errors near segment boundaries.
        var pcm = sample.toInt() shr 2
        val mask: Int
        if (pcm < 0) {
            pcm = -pcm
            mask = 0x7f
        } else {
            mask = 0xff
        }
        pcm = pcm.coerceAtMost(ULAW_CLIP_SCALED)
        pcm += ULAW_BIAS shr 2
        val segment = findSegment(pcm, ULAW_SEGMENT_END)
        val encoded = if (segment >= 8) {
            0x7f
        } else {
            (segment shl 4) or ((pcm ushr (segment + 1)) and 0x0f)
        }
        return (encoded xor mask).toByte()
    }

    private fun findSegment(value: Int, table: IntArray): Int {
        for (index in table.indices) if (value <= table[index]) return index
        return table.size
    }

    internal fun muLawToLinear(value: Byte): Short {
        val u = (value.toInt() and 0xff).inv() and 0xff
        var sample = ((u and 0x0f) shl 3) + ULAW_BIAS
        sample = sample shl ((u and 0x70) ushr 4)
        sample -= ULAW_BIAS
        return if (u and 0x80 != 0) (-sample).coerceAtLeast(Short.MIN_VALUE.toInt()).toShort()
        else sample.coerceAtMost(Short.MAX_VALUE.toInt()).toShort()
    }

    internal fun linearToALaw(sample: Short): Byte {
        var pcm = sample.toInt()
        val mask: Int
        if (pcm >= 0) {
            mask = 0xD5
        } else {
            mask = 0x55
            pcm = -pcm - 1
        }
        pcm = pcm.coerceAtMost(32767)
        val compressed = if (pcm < 256) {
            pcm ushr 4
        } else {
            var segment = 1
            var temp = pcm ushr 8
            while (temp > 1 && segment < 7) {
                segment += 1
                temp = temp ushr 1
            }
            (segment shl 4) or ((pcm ushr (segment + 3)) and 0x0f)
        }
        return (compressed xor mask).toByte()
    }

    internal fun aLawToLinear(value: Byte): Short {
        val a = (value.toInt() and 0xff) xor 0x55
        var sample = (a and 0x0f) shl 4
        val segment = (a and 0x70) ushr 4
        sample += when (segment) {
            0 -> 8
            1 -> 0x108
            else -> 0x108
        }
        if (segment >= 2) sample = sample shl (segment - 1)
        return if (a and 0x80 != 0) sample.coerceAtMost(Short.MAX_VALUE.toInt()).toShort()
        else (-sample).coerceAtLeast(Short.MIN_VALUE.toInt()).toShort()
    }
}
