package com.rasven.android.iax2

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

internal object RasvenIax2 {
    const val DEFAULT_PORT = 4569
    const val VERSION = 2
    const val MAX_CALL_NUMBER = 0x7fff
    const val MAX_UDP_PAYLOAD = 65507

    object FrameType {
        const val DTMF = 0x01
        const val VOICE = 0x02
        const val CONTROL = 0x04
        const val IAX = 0x06
    }

    object MediaFormat {
        const val PCMU = 0x00000004
        const val PCMA = 0x00000008
        const val IMPLEMENTED_CAPABILITY = PCMU or PCMA
    }

    object IaxSubclass {
        const val NEW = 0x01
        const val PING = 0x02
        const val PONG = 0x03
        const val ACK = 0x04
        const val HANGUP = 0x05
        const val REJECT = 0x06
        const val ACCEPT = 0x07
        const val AUTHREQ = 0x08
        const val AUTHREP = 0x09
        const val INVAL = 0x0a
        const val LAGRQ = 0x0b
        const val LAGRP = 0x0c
        const val REGREQ = 0x0d
        const val REGAUTH = 0x0e
        const val REGACK = 0x0f
        const val REGREJ = 0x10
        const val REGREL = 0x11
        const val VNAK = 0x12
        const val POKE = 0x1e
    }

    object ControlSubclass {
        const val HANGUP = 0x01
        const val RINGING = 0x03
        const val ANSWER = 0x04
        const val BUSY = 0x05
        const val CONGESTION = 0x08
        const val PROGRESS = 0x0e
        const val PROCEEDING = 0x0f
        const val HOLD = 0x10
        const val UNHOLD = 0x11
    }

    object IeType {
        const val CALLED_NUMBER = 0x01
        const val CALLING_NUMBER = 0x02
        const val CALLING_NAME = 0x04
        const val USERNAME = 0x06
        const val CAPABILITY = 0x08
        const val FORMAT = 0x09
        const val VERSION = 0x0b
        const val AUTHMETHODS = 0x0e
        const val CHALLENGE = 0x0f
        const val MD5_RESULT = 0x10
        const val REFRESH = 0x13
        const val CAUSE = 0x16
        const val CALLINGPRES = 0x26
        const val CALLINGTON = 0x27
        const val CALLINGTNS = 0x28
        const val CAUSECODE = 0x2a
        const val CODEC_PREFS = 0x2d
    }

    object AuthMethod {
        const val MD5 = 0x0002
        const val RSA = 0x0004
    }

    object CauseCode {
        const val NORMAL_CLEARING = 16
        const val USER_BUSY = 17
        const val NO_USER_RESPONSE = 18
        const val NO_ANSWER = 19
        const val CALL_REJECTED = 21
        const val NETWORK_OUT_OF_ORDER = 38
        const val TEMPORARY_FAILURE = 41
        const val SWITCH_CONGESTION = 42
    }
}

internal data class RasvenIax2InformationElement(
    val type: Int,
    val data: ByteArray,
) {
    init {
        require(type in 0..0xff)
        require(data.size <= 0xff)
    }

    override fun equals(other: Any?): Boolean =
        other is RasvenIax2InformationElement && type == other.type && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * type + data.contentHashCode()
}

internal data class RasvenIax2FullFrame(
    val sourceCallNumber: Int,
    val destinationCallNumber: Int,
    val retransmission: Boolean,
    val timestamp: Long,
    val oSeqNo: Int,
    val iSeqNo: Int,
    val frameType: Int,
    val subclass: Int,
    val compressedSubclass: Boolean = false,
    val informationElements: List<RasvenIax2InformationElement> = emptyList(),
    val payload: ByteArray = ByteArray(0),
) {
    init {
        require(sourceCallNumber in 0..RasvenIax2.MAX_CALL_NUMBER)
        require(destinationCallNumber in 0..RasvenIax2.MAX_CALL_NUMBER)
        require(timestamp in 0..0xffff_ffffL)
        require(oSeqNo in 0..0xff)
        require(iSeqNo in 0..0xff)
        require(frameType in 0..0xff)
        if (frameType == RasvenIax2.FrameType.CONTROL && subclass == -1) {
            require(compressedSubclass)
        } else if (compressedSubclass) require(subclass > 0 && subclass.countOneBits() == 1)
        else require(subclass in 0..0x7f)
        require(informationElements.isEmpty() || payload.isEmpty())
    }

    fun ie(type: Int): RasvenIax2InformationElement? = informationElements.firstOrNull { it.type == type }
    fun ieString(type: Int): String? = ie(type)?.data?.toString(StandardCharsets.UTF_8)
    fun ieU8(type: Int): Int? = ie(type)?.data?.takeIf { it.size == 1 }?.get(0)?.toInt()?.and(0xff)
    fun ieU16(type: Int): Int? = ie(type)?.data?.takeIf { it.size == 2 }?.let {
        ((it[0].toInt() and 0xff) shl 8) or (it[1].toInt() and 0xff)
    }
    fun ieU32(type: Int): Long? = ie(type)?.data?.takeIf { it.size == 4 }?.let {
        ((it[0].toLong() and 0xff) shl 24) or
            ((it[1].toLong() and 0xff) shl 16) or
            ((it[2].toLong() and 0xff) shl 8) or
            (it[3].toLong() and 0xff)
    }

    override fun equals(other: Any?): Boolean = other is RasvenIax2FullFrame &&
        sourceCallNumber == other.sourceCallNumber && destinationCallNumber == other.destinationCallNumber &&
        retransmission == other.retransmission && timestamp == other.timestamp && oSeqNo == other.oSeqNo &&
        iSeqNo == other.iSeqNo && frameType == other.frameType && subclass == other.subclass &&
        compressedSubclass == other.compressedSubclass && informationElements == other.informationElements &&
        payload.contentEquals(other.payload)

    override fun hashCode(): Int = 31 * informationElements.hashCode() + payload.contentHashCode()
}

internal object RasvenIax2FrameCodec {
    private const val HEADER_SIZE = 12

    fun encode(frame: RasvenIax2FullFrame): ByteArray {
        val tailSize = if (frame.payload.isNotEmpty()) frame.payload.size else frame.informationElements.sumOf { it.data.size + 2 }
        val data = ByteArrayOutputStream(HEADER_SIZE + tailSize)
        writeU16(data, 0x8000 or frame.sourceCallNumber)
        writeU16(data, (if (frame.retransmission) 0x8000 else 0) or frame.destinationCallNumber)
        writeU32(data, frame.timestamp)
        data.write(frame.oSeqNo)
        data.write(frame.iSeqNo)
        data.write(frame.frameType)
        data.write(encodeSubclass(frame.subclass, frame.compressedSubclass))
        if (frame.payload.isNotEmpty()) {
            data.write(frame.payload)
        } else {
            frame.informationElements.forEach { ie ->
                data.write(ie.type)
                data.write(ie.data.size)
                data.write(ie.data)
            }
        }
        return data.toByteArray()
    }

    fun decode(packet: ByteArray): RasvenIax2FullFrame? {
        if (packet.size < HEADER_SIZE || packet.size > RasvenIax2.MAX_UDP_PAYLOAD) return null
        val sourceWord = readU16(packet, 0)
        if (sourceWord and 0x8000 == 0) return null
        val destinationWord = readU16(packet, 2)
        val timestamp = readU32(packet, 4)
        val oSeq = packet[8].toInt() and 0xff
        val iSeq = packet[9].toInt() and 0xff
        val frameType = packet[10].toInt() and 0xff
        val encodedSubclass = packet[11].toInt() and 0xff
        val compressed = encodedSubclass and 0x80 != 0
        // Asterisk uses the reserved 0xff representation for CONTROL -1
        // (stop an indication). It is not a codec exponent or a hangup.
        val subclass = if (frameType == RasvenIax2.FrameType.CONTROL && encodedSubclass == 0xff) {
            -1
        } else if (compressed) {
            val power = encodedSubclass and 0x7f
            if (power > 31) return null
            1 shl power
        } else {
            encodedSubclass and 0x7f
        }

        if (frameType == RasvenIax2.FrameType.VOICE) {
            val payload = packet.copyOfRange(HEADER_SIZE, packet.size)
            if (payload.isEmpty()) return null
            return RasvenIax2FullFrame(
                sourceCallNumber = sourceWord and 0x7fff,
                destinationCallNumber = destinationWord and 0x7fff,
                retransmission = destinationWord and 0x8000 != 0,
                timestamp = timestamp,
                oSeqNo = oSeq,
                iSeqNo = iSeq,
                frameType = frameType,
                subclass = subclass,
                compressedSubclass = compressed,
                payload = payload,
            )
        }

        val ies = ArrayList<RasvenIax2InformationElement>()
        var offset = HEADER_SIZE
        while (offset < packet.size) {
            if (packet.size - offset < 2) return null
            val type = packet[offset].toInt() and 0xff
            val length = packet[offset + 1].toInt() and 0xff
            offset += 2
            if (length > packet.size - offset) return null
            ies += RasvenIax2InformationElement(type, packet.copyOfRange(offset, offset + length))
            offset += length
        }

        return RasvenIax2FullFrame(
            sourceCallNumber = sourceWord and 0x7fff,
            destinationCallNumber = destinationWord and 0x7fff,
            retransmission = destinationWord and 0x8000 != 0,
            timestamp = timestamp,
            oSeqNo = oSeq,
            iSeqNo = iSeq,
            frameType = frameType,
            subclass = subclass,
            compressedSubclass = compressed,
            informationElements = ies,
        )
    }

    fun stringIe(type: Int, value: String): RasvenIax2InformationElement =
        RasvenIax2InformationElement(type, value.toByteArray(StandardCharsets.UTF_8))

    fun u8Ie(type: Int, value: Int): RasvenIax2InformationElement =
        RasvenIax2InformationElement(type, byteArrayOf((value and 0xff).toByte()))

    fun u16Ie(type: Int, value: Int): RasvenIax2InformationElement =
        RasvenIax2InformationElement(type, byteArrayOf((value ushr 8).toByte(), value.toByte()))

    fun u32Ie(type: Int, value: Long): RasvenIax2InformationElement =
        RasvenIax2InformationElement(
            type,
            byteArrayOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte(),
            ),
        )

    private fun encodeSubclass(subclass: Int, compressed: Boolean): Int {
        if (compressed && subclass == -1) return 0xff
        if (!compressed) return subclass and 0x7f
        val power = Integer.numberOfTrailingZeros(subclass)
        require(power in 0..31)
        return 0x80 or power
    }

    private fun writeU16(output: ByteArrayOutputStream, value: Int) {
        output.write((value ushr 8) and 0xff)
        output.write(value and 0xff)
    }

    private fun writeU32(output: ByteArrayOutputStream, value: Long) {
        output.write(((value ushr 24) and 0xff).toInt())
        output.write(((value ushr 16) and 0xff).toInt())
        output.write(((value ushr 8) and 0xff).toInt())
        output.write((value and 0xff).toInt())
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)
}
