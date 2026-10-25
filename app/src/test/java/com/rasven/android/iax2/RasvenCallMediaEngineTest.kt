package com.rasven.android.iax2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RasvenCallMediaEngineTest {
    @Test fun lagReplyWaitsForActualPriorVoicePlayoutAndClearDiscardsStaleReply() {
        val buffer = RasvenBoundedJitterBuffer(20)
        var replies = 0
        buffer.offer(RasvenJitterFrame(100, RasvenAudioCodec.PCMU, byteArrayOf(1)))
        buffer.offer(RasvenJitterFrame(120, RasvenAudioCodec.PCMU, byteArrayOf(2)))
        buffer.deferLagReply { replies++ }
        assertEquals(0, replies)
        val first = buffer.poll()!!
        buffer.afterPlayout(first.timestampMs)
        assertEquals(0, replies)
        val second = buffer.poll()!!
        assertEquals(0, replies) // Removing a packet is not proof that AudioTrack wrote it.
        buffer.afterPlayout(second.timestampMs)
        assertEquals(1, replies)
        buffer.offer(RasvenJitterFrame(140, RasvenAudioCodec.PCMU, byteArrayOf(3)))
        buffer.deferLagReply { replies++ }
        buffer.clear()
        buffer.afterPlayout(140)
        assertEquals(1, replies)
        buffer.deferLagReply { replies++ }
        assertEquals(2, replies) // No jitter/audio pending, no invented delay.
    }

    private class FakeInput(private val format: RasvenAudioMediaFormat) : RasvenAudioInput {
        private val active = AtomicBoolean(false)
        val stopCount = AtomicInteger(0)
        override fun start(): Boolean { active.set(true); return true }
        override fun read(buffer: ShortArray, offset: Int, sampleCount: Int): Int {
            if (!active.get()) return -1
            repeat(sampleCount) { i -> buffer[offset + i] = (((i * 37) % 4000) - 2000).toShort() }
            Thread.sleep(format.frameDurationMs.toLong())
            return sampleCount
        }
        override fun stop() { if (active.compareAndSet(true, false)) stopCount.incrementAndGet() }
        override fun close() { stop() }
    }

    private class FakeOutput(private val latch: CountDownLatch) : RasvenAudioOutput {
        private val active = AtomicBoolean(false)
        val writes = AtomicInteger(0)
        override fun start(): Boolean { active.set(true); return true }
        override fun write(buffer: ShortArray, offset: Int, sampleCount: Int): Int {
            if (!active.get()) return -1
            writes.incrementAndGet()
            latch.countDown()
            return sampleCount
        }
        override fun stop() { active.set(false) }
        override fun close() { stop() }
    }

    private class FakeEnvironment(private val grants: Boolean) : RasvenAudioSessionEnvironment {
        val closes = AtomicInteger(0)
        @Volatile private var focusLost: (() -> Unit)? = null
        override fun activate(onFocusLost: () -> Unit): Boolean {
            if (grants) focusLost = onFocusLost
            return grants
        }
        fun loseFocus() { focusLost?.invoke() }
        override fun close() { closes.incrementAndGet(); focusLost = null }
    }

    private class LoopbackMediaChannel(
        override val sessionId: Long,
        override val codec: RasvenAudioCodec,
        private val localCall: Int = 7,
    ) : RasvenIax2MediaChannel {
        override val initialTimestampMs: Long = 65_520L
        @Volatile private var receiver: ((RasvenIax2InboundVoice) -> Unit)? = null
        private var sequence = 0
        val fullCount = AtomicInteger(0)
        val miniCount = AtomicInteger(0)
        val transmittedPayloads = CopyOnWriteArrayList<ByteArray>()

        override fun sendFullVoice(timestampMs: Long, payload: ByteArray): Boolean {
            val packet = RasvenIax2FrameCodec.encode(
                RasvenIax2FullFrame(
                    sourceCallNumber = localCall,
                    destinationCallNumber = 9,
                    retransmission = false,
                    timestamp = timestampMs,
                    oSeqNo = sequence++ and 0xff,
                    iSeqNo = 0,
                    frameType = RasvenIax2.FrameType.VOICE,
                    subclass = codec.iaxFormat,
                    compressedSubclass = true,
                    payload = payload.copyOf(),
                ),
            )
            val decoded = RasvenIax2FrameCodec.decode(packet) ?: return false
            fullCount.incrementAndGet()
            transmittedPayloads.add(decoded.payload.copyOf())
            receiver?.invoke(RasvenIax2InboundVoice(sessionId, codec, decoded.timestamp, decoded.payload, true))
            return true
        }

        override fun sendMiniVoice(timestampMs: Long, payload: ByteArray): Boolean {
            val packet = RasvenIax2MiniFrameCodec.encode(
                RasvenIax2MiniFrame(localCall, (timestampMs and 0xffff).toInt(), payload.copyOf()),
            )
            val decoded = RasvenIax2MiniFrameCodec.decode(packet) ?: return false
            miniCount.incrementAndGet()
            transmittedPayloads.add(decoded.payload.copyOf())
            receiver?.invoke(RasvenIax2InboundVoice(sessionId, codec, timestampMs, decoded.payload, false))
            return true
        }

        override fun setReceiver(receiver: ((RasvenIax2InboundVoice) -> Unit)?) { this.receiver = receiver }
    }

    @Test fun syntheticPcmTraversesCodecFramesJitterAndPlaybackWithoutUnboundedQueue() {
        val format = RasvenAudioMediaFormat(RasvenAudioCodec.PCMU)
        val input = FakeInput(format)
        val playback = CountDownLatch(4)
        val output = FakeOutput(playback)
        val environment = FakeEnvironment(true)
        val factory = object : RasvenAudioIoFactory {
            override fun createInput(format: RasvenAudioMediaFormat): RasvenAudioInput = input
            override fun createOutput(format: RasvenAudioMediaFormat): RasvenAudioOutput = output
            override fun createEnvironment(): RasvenAudioSessionEnvironment = environment
        }
        val channel = LoopbackMediaChannel(88, RasvenAudioCodec.PCMU)
        val engine = RasvenCallMediaEngine(factory)
        val failures = mutableListOf<RasvenMediaFailure>()
        assertEquals(RasvenMediaStartResult.Started, engine.start(88, channel, format, failures::add))
        assertTrue(playback.await(2, TimeUnit.SECONDS))
        // First media packet is Full Voice; crossing the 16-bit mini timestamp wrap emits another Full Voice.
        assertTrue(channel.fullCount.get() >= 2)
        assertTrue(channel.miniCount.get() >= 1)
        assertTrue(output.writes.get() >= 4)
        assertTrue((engine.jitterMetricsForTest()?.receivedFrames ?: 0) >= 4)
        engine.stop()
        engine.stop()
        assertEquals(1, input.stopCount.get())
        assertEquals(1, environment.closes.get())
        assertTrue(failures.isEmpty())
        engine.close()
    }

    @Test fun focusFailureFailsClosedBeforeCreatingMediaIo() {
        val created = AtomicInteger(0)
        val factory = object : RasvenAudioIoFactory {
            override fun createInput(format: RasvenAudioMediaFormat): RasvenAudioInput? { created.incrementAndGet(); return null }
            override fun createOutput(format: RasvenAudioMediaFormat): RasvenAudioOutput? { created.incrementAndGet(); return null }
            override fun createEnvironment(): RasvenAudioSessionEnvironment = FakeEnvironment(false)
        }
        val engine = RasvenCallMediaEngine(factory)
        val result = engine.start(
            mediaSessionId = 1,
            channel = LoopbackMediaChannel(1, RasvenAudioCodec.PCMU),
            format = RasvenAudioMediaFormat(RasvenAudioCodec.PCMU),
        ) { }
        assertEquals(RasvenMediaStartResult.Failed(RasvenMediaFailure.AudioFocusDenied), result)
        assertEquals(0, created.get())
        engine.close()
    }

    @Test fun muteTransmitsSilenceAndResumeRestoresOnlyTheCurrentSession() {
        val format = RasvenAudioMediaFormat(RasvenAudioCodec.PCMU)
        val input = FakeInput(format)
        val output = FakeOutput(CountDownLatch(1))
        val factory = object : RasvenAudioIoFactory {
            override fun createInput(format: RasvenAudioMediaFormat): RasvenAudioInput = input
            override fun createOutput(format: RasvenAudioMediaFormat): RasvenAudioOutput = output
            override fun createEnvironment(): RasvenAudioSessionEnvironment = FakeEnvironment(true)
        }
        val channel = LoopbackMediaChannel(104, RasvenAudioCodec.PCMU)
        val engine = RasvenCallMediaEngine(factory)
        assertEquals(RasvenMediaStartResult.Started, engine.start(104, channel, format) { })
        assertTrue(awaitPayloadCount(channel, 2))
        assertFalse(engine.setMuted(103, true))
        assertFalse(engine.isMuted(104))
        assertTrue(engine.setMuted(104, true))
        assertTrue(engine.isMuted(104))
        val beforeMute = channel.transmittedPayloads.size
        assertTrue(awaitPayloadCount(channel, beforeMute + 3))
        val silent = RasvenG711.encodePcm16(RasvenAudioCodec.PCMU, ShortArray(format.samplesPerFrame))
        channel.transmittedPayloads.drop(beforeMute).forEach { assertTrue(it.contentEquals(silent)) }
        assertTrue(engine.setMuted(104, false))
        val beforeResume = channel.transmittedPayloads.size
        assertTrue(awaitPayloadCount(channel, beforeResume + 2))
        assertTrue(channel.transmittedPayloads.drop(beforeResume).any { !it.contentEquals(silent) })
        engine.stop()
        assertFalse(engine.setMuted(104, true))
        assertFalse(engine.isMuted(104))
        engine.close()
    }

    @Test fun holdSilencesTransmitWithoutClearingUserMuteAndResumeRestoresPriorMute() {
        val format = RasvenAudioMediaFormat(RasvenAudioCodec.PCMU)
        val input = FakeInput(format)
        val output = FakeOutput(CountDownLatch(1))
        val channel = LoopbackMediaChannel(105, RasvenAudioCodec.PCMU)
        val engine = RasvenCallMediaEngine(object : RasvenAudioIoFactory {
            override fun createInput(format: RasvenAudioMediaFormat): RasvenAudioInput = input
            override fun createOutput(format: RasvenAudioMediaFormat): RasvenAudioOutput = output
            override fun createEnvironment(): RasvenAudioSessionEnvironment = FakeEnvironment(true)
        })
        assertEquals(RasvenMediaStartResult.Started, engine.start(105, channel, format) { })
        assertTrue(awaitPayloadCount(channel, 2))
        assertTrue(engine.setMuted(105, true))
        assertTrue(engine.setHeld(105, true))
        assertTrue(engine.setMuted(105, false))
        val silent = RasvenG711.encodePcm16(RasvenAudioCodec.PCMU, ShortArray(format.samplesPerFrame))
        val beforeHeld = channel.transmittedPayloads.size
        assertTrue(awaitPayloadCount(channel, beforeHeld + 3))
        assertTrue(channel.transmittedPayloads.drop(beforeHeld).all { it.contentEquals(silent) })
        assertTrue(engine.setHeld(105, false))
        val beforeResume = channel.transmittedPayloads.size
        assertTrue(awaitPayloadCount(channel, beforeResume + 2))
        assertTrue(channel.transmittedPayloads.drop(beforeResume).any { !it.contentEquals(silent) })
        engine.close()
    }

    private fun awaitPayloadCount(channel: LoopbackMediaChannel, count: Int): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (channel.transmittedPayloads.size < count && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        return channel.transmittedPayloads.size >= count
    }

    @Test fun staleSessionChannelIsRejectedBeforeAudioStarts() {
        val factory = object : RasvenAudioIoFactory {
            override fun createInput(format: RasvenAudioMediaFormat): RasvenAudioInput? = null
            override fun createOutput(format: RasvenAudioMediaFormat): RasvenAudioOutput? = null
            override fun createEnvironment(): RasvenAudioSessionEnvironment = FakeEnvironment(true)
        }
        val engine = RasvenCallMediaEngine(factory)
        val result = engine.start(
            mediaSessionId = 2,
            channel = LoopbackMediaChannel(1, RasvenAudioCodec.PCMU),
            format = RasvenAudioMediaFormat(RasvenAudioCodec.PCMU),
        ) { }
        assertEquals(RasvenMediaStartResult.Failed(RasvenMediaFailure.UnsupportedCodec), result)
        engine.close()
    }
    @Test fun focusLossStopsBlockingCaptureThroughDedicatedControlPath() {
        val format = RasvenAudioMediaFormat(RasvenAudioCodec.PCMU)
        val input = FakeInput(format)
        val output = FakeOutput(CountDownLatch(1))
        val environment = FakeEnvironment(true)
        val factory = object : RasvenAudioIoFactory {
            override fun createInput(format: RasvenAudioMediaFormat): RasvenAudioInput = input
            override fun createOutput(format: RasvenAudioMediaFormat): RasvenAudioOutput = output
            override fun createEnvironment(): RasvenAudioSessionEnvironment = environment
        }
        val engine = RasvenCallMediaEngine(factory)
        val failure = CountDownLatch(1)
        assertEquals(
            RasvenMediaStartResult.Started,
            engine.start(44, LoopbackMediaChannel(44, RasvenAudioCodec.PCMU), format) {
                assertEquals(RasvenMediaFailure.AudioFocusDenied, it)
                failure.countDown()
            },
        )
        environment.loseFocus()
        assertTrue(failure.await(1, TimeUnit.SECONDS))
        assertEquals(1, input.stopCount.get())
        engine.close()
    }

    @Test fun doubleStartIsRejectedAndClosedEngineCannotRestart() {
        val format = RasvenAudioMediaFormat(RasvenAudioCodec.PCMU)
        val input = FakeInput(format)
        val output = FakeOutput(CountDownLatch(1))
        val environment = FakeEnvironment(true)
        val factory = object : RasvenAudioIoFactory {
            override fun createInput(format: RasvenAudioMediaFormat): RasvenAudioInput = input
            override fun createOutput(format: RasvenAudioMediaFormat): RasvenAudioOutput = output
            override fun createEnvironment(): RasvenAudioSessionEnvironment = environment
        }
        val engine = RasvenCallMediaEngine(factory)
        val channel = LoopbackMediaChannel(91, RasvenAudioCodec.PCMU)
        assertEquals(RasvenMediaStartResult.Started, engine.start(91, channel, format) { })
        assertEquals(
            RasvenMediaStartResult.Failed(RasvenMediaFailure.AudioIoFailure),
            engine.start(91, channel, format) { },
        )
        engine.close()
        assertEquals(
            RasvenMediaStartResult.Failed(RasvenMediaFailure.AudioIoFailure),
            engine.start(91, channel, format) { },
        )
    }

    @Test fun inputAndOutputInitializationFailuresFailClosedAndReleaseEnvironment() {
        val format = RasvenAudioMediaFormat(RasvenAudioCodec.PCMU)
        val inputEnvironment = FakeEnvironment(true)
        val inputFailureFactory = object : RasvenAudioIoFactory {
            override fun createInput(format: RasvenAudioMediaFormat): RasvenAudioInput? = null
            override fun createOutput(format: RasvenAudioMediaFormat): RasvenAudioOutput? = error("output must not be created")
            override fun createEnvironment(): RasvenAudioSessionEnvironment = inputEnvironment
        }
        val inputEngine = RasvenCallMediaEngine(inputFailureFactory)
        assertEquals(
            RasvenMediaStartResult.Failed(RasvenMediaFailure.AudioInputInitFailed),
            inputEngine.start(92, LoopbackMediaChannel(92, RasvenAudioCodec.PCMU), format) { },
        )
        assertEquals(1, inputEnvironment.closes.get())
        inputEngine.close()

        val outputEnvironment = FakeEnvironment(true)
        val input = FakeInput(format)
        val outputFailureFactory = object : RasvenAudioIoFactory {
            override fun createInput(format: RasvenAudioMediaFormat): RasvenAudioInput = input
            override fun createOutput(format: RasvenAudioMediaFormat): RasvenAudioOutput? = null
            override fun createEnvironment(): RasvenAudioSessionEnvironment = outputEnvironment
        }
        val outputEngine = RasvenCallMediaEngine(outputFailureFactory)
        assertEquals(
            RasvenMediaStartResult.Failed(RasvenMediaFailure.AudioOutputInitFailed),
            outputEngine.start(93, LoopbackMediaChannel(93, RasvenAudioCodec.PCMU), format) { },
        )
        assertEquals(1, outputEnvironment.closes.get())
        outputEngine.close()
    }

}
