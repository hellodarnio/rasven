package com.rasven.android.iax2

internal data class RasvenIax2MiniFrame(
    val sourceCallNumber: Int,
    val timestamp16: Int,
    val payload: ByteArray,
) {
    init {
        require(sourceCallNumber in 1..RasvenIax2.MAX_CALL_NUMBER)
        require(timestamp16 in 0..0xffff)
        require(payload.isNotEmpty())
        require(payload.size <= RasvenIax2.MAX_UDP_PAYLOAD - 4)
    }

    override fun equals(other: Any?): Boolean = other is RasvenIax2MiniFrame &&
        sourceCallNumber == other.sourceCallNumber && timestamp16 == other.timestamp16 && payload.contentEquals(other.payload)
    override fun hashCode(): Int = 31 * (31 * sourceCallNumber + timestamp16) + payload.contentHashCode()
}

internal object RasvenIax2MiniFrameCodec {
    fun encode(frame: RasvenIax2MiniFrame): ByteArray {
        val out = ByteArray(4 + frame.payload.size)
        out[0] = ((frame.sourceCallNumber ushr 8) and 0x7f).toByte()
        out[1] = frame.sourceCallNumber.toByte()
        out[2] = (frame.timestamp16 ushr 8).toByte()
        out[3] = frame.timestamp16.toByte()
        frame.payload.copyInto(out, 4)
        return out
    }

    fun decode(packet: ByteArray): RasvenIax2MiniFrame? {
        if (packet.size <= 4 || packet.size > RasvenIax2.MAX_UDP_PAYLOAD) return null
        val sourceWord = ((packet[0].toInt() and 0xff) shl 8) or (packet[1].toInt() and 0xff)
        if (sourceWord and 0x8000 != 0) return null
        val source = sourceWord and 0x7fff
        if (source == 0) return null
        val timestamp = ((packet[2].toInt() and 0xff) shl 8) or (packet[3].toInt() and 0xff)
        return RasvenIax2MiniFrame(source, timestamp, packet.copyOfRange(4, packet.size))
    }
}

internal data class RasvenIax2InboundVoice(
    val sessionId: Long,
    val codec: RasvenAudioCodec,
    val timestampMs: Long,
    val payload: ByteArray,
    val fullFrame: Boolean,
)

internal interface RasvenIax2MediaChannel {
    val sessionId: Long
    val codec: RasvenAudioCodec
    /** Current call-relative IAX timestamp when media ownership is handed over. */
    val initialTimestampMs: Long
    fun sendFullVoice(timestampMs: Long, payload: ByteArray): Boolean
    fun sendMiniVoice(timestampMs: Long, payload: ByteArray): Boolean
    fun setReceiver(receiver: ((RasvenIax2InboundVoice) -> Unit)?)
    /** Enqueue a lag reply behind already received voice; production owns the playout barrier. */
    fun setLagReceiver(receiver: (((() -> Unit) -> Unit))?) {}
}

/** Extends a wrapped 16-bit mini-frame timestamp into a monotonic-ish 32-bit call-relative value. */
internal class RasvenIax2MiniTimestampExtender {
    private var initialized = false
    private var highestExtended = 0L

    /** Full voice frames re-synchronize the 32-bit media clock around mini-frame wrap. */
    @Synchronized
    fun syncFullTimestamp(timestamp32: Long) {
        require(timestamp32 in 0..0xffff_ffffL)
        initialized = true
        highestExtended = timestamp32
    }

    @Synchronized
    fun extend(timestamp16: Int): Long {
        require(timestamp16 in 0..0xffff)
        if (!initialized) {
            initialized = true
            highestExtended = timestamp16.toLong()
            return highestExtended
        }
        val base = highestExtended and 0xffff_0000L
        val candidates = longArrayOf(
            (base or timestamp16.toLong()) and 0xffff_ffffL,
            ((base + 0x1_0000L) or timestamp16.toLong()) and 0xffff_ffffL,
            ((base - 0x1_0000L) or timestamp16.toLong()) and 0xffff_ffffL,
        )
        val best = candidates.minBy { unsignedDistance(highestExtended, it) }
        if (isAtOrAfter(best, highestExtended)) highestExtended = best
        return best
    }

    private fun unsignedDistance(a: Long, b: Long): Long {
        val forward = (b - a) and 0xffff_ffffL
        val backward = (a - b) and 0xffff_ffffL
        return minOf(forward, backward)
    }

    private fun isAtOrAfter(candidate: Long, previous: Long): Boolean {
        val distance = (candidate - previous) and 0xffff_ffffL
        return distance == 0L || distance < 0x8000_0000L
    }
}
