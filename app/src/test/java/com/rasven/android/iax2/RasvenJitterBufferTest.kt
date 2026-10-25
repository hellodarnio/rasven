package com.rasven.android.iax2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasvenJitterBufferTest {
    private fun frame(ts: Long, value: Int = ts.toInt()) =
        RasvenJitterFrame(ts, RasvenAudioCodec.PCMU, ByteArray(160) { value.toByte() })

    @Test fun reordersOutOfOrderFramesAndRejectsDuplicates() {
        val buffer = RasvenBoundedJitterBuffer(frameDurationMs = 20, capacity = 8, targetDepth = 3)
        assertTrue(buffer.offer(frame(40)))
        assertTrue(buffer.offer(frame(0)))
        assertTrue(buffer.offer(frame(20)))
        assertEquals(0L, buffer.poll()!!.timestampMs)
        assertEquals(20L, buffer.poll()!!.timestampMs)
        assertEquals(40L, buffer.poll()!!.timestampMs)
        val underrun = buffer.poll()!!
        assertEquals(60L, underrun.timestampMs)
        assertEquals(0, underrun.payload.size)
        assertTrue(!buffer.offer(frame(60)))
        assertEquals(1L, buffer.metrics().lateFrames)
    }

    @Test fun timestampWrapUsesExtendedOrderWithoutSignedComparison() {
        val extender = RasvenIax2MiniTimestampExtender()
        extender.syncFullTimestamp(65_520)
        val before = extender.extend(65_530)
        val after = extender.extend(4)
        val later = extender.extend(24)
        assertEquals(65_530L, before)
        assertEquals(65_540L, after)
        assertEquals(65_560L, later)
    }

    @Test fun missingFrameProducesReplaceableSilenceMarkerAndBufferStaysBounded() {
        val buffer = RasvenBoundedJitterBuffer(frameDurationMs = 20, capacity = 4, targetDepth = 2)
        buffer.offer(frame(0)); buffer.offer(frame(40))
        assertEquals(0L, buffer.poll()!!.timestampMs)
        val missing = buffer.poll()!!
        assertEquals(20L, missing.timestampMs)
        assertEquals(0, missing.payload.size)
        assertEquals(1L, buffer.metrics().missingFrames)
        repeat(20) { buffer.offer(frame(100L + it * 20L, it)) }
        assertTrue(buffer.size() <= 4)
        assertTrue(buffer.metrics().droppedFrames > 0)
        buffer.clear()
        assertEquals(0, buffer.size())
    }


    @Test fun extendedTimestampsRemainOrderedAcrossMiniWrap() {
        val buffer = RasvenBoundedJitterBuffer(frameDurationMs = 20, capacity = 8, targetDepth = 3)
        buffer.offer(frame(65_520))
        buffer.offer(frame(65_560))
        buffer.offer(frame(65_540))
        assertEquals(65_520L, buffer.poll()!!.timestampMs)
        assertEquals(65_540L, buffer.poll()!!.timestampMs)
        assertEquals(65_560L, buffer.poll()!!.timestampMs)
    }

    @Test fun severalMinutesOfSyntheticFramesRemainBounded() {
        val buffer = RasvenBoundedJitterBuffer(frameDurationMs = 20, capacity = 12, targetDepth = 3)
        repeat(9_000) { index ->
            buffer.offer(frame(index * 20L, index))
            if (index % 2 == 0) buffer.poll()
            assertTrue(buffer.size() <= 12)
        }
    }

}
