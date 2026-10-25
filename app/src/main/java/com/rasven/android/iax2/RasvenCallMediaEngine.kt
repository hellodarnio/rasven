package com.rasven.android.iax2

import java.util.TreeMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal interface RasvenAudioInput : AutoCloseable {
    fun start(): Boolean
    fun read(buffer: ShortArray, offset: Int, sampleCount: Int): Int
    fun stop()
    override fun close()
}

internal interface RasvenAudioOutput : AutoCloseable {
    fun start(): Boolean
    fun write(buffer: ShortArray, offset: Int, sampleCount: Int): Int
    fun stop()
    override fun close()
}

internal interface RasvenAudioSessionEnvironment : AutoCloseable {
    fun activate(onFocusLost: () -> Unit): Boolean
    override fun close()
}

internal interface RasvenAudioIoFactory {
    fun createInput(format: RasvenAudioMediaFormat): RasvenAudioInput?
    fun createOutput(format: RasvenAudioMediaFormat): RasvenAudioOutput?
    fun createEnvironment(): RasvenAudioSessionEnvironment
}

internal enum class RasvenMediaFailure {
    UnsupportedCodec,
    AudioInputInitFailed,
    AudioOutputInitFailed,
    AudioFocusDenied,
    MediaTransportFailure,
    AudioIoFailure,
}

internal sealed interface RasvenMediaStartResult {
    data object Started : RasvenMediaStartResult
    data class Failed(val reason: RasvenMediaFailure) : RasvenMediaStartResult
}

internal data class RasvenJitterMetrics(
    val receivedFrames: Long,
    val lateFrames: Long,
    val duplicateFrames: Long,
    val droppedFrames: Long,
    val missingFrames: Long,
    val playoutUnderruns: Long,
)

internal data class RasvenJitterFrame(
    val timestampMs: Long,
    val codec: RasvenAudioCodec,
    val payload: ByteArray,
)

/** Small bounded reorder buffer. It never stores audio outside the active in-memory media session. */
internal class RasvenBoundedJitterBuffer(
    private val frameDurationMs: Int,
    private val capacity: Int = 12,
    private val targetDepth: Int = 3,
) {
    init {
        require(frameDurationMs > 0)
        require(capacity >= targetDepth && targetDepth >= 1)
    }

    private val frames = TreeMap<Long, RasvenJitterFrame>()
    private var expectedTimestamp: Long? = null
    private var lastCodec: RasvenAudioCodec? = null
    private var received = 0L
    private var late = 0L
    private var duplicates = 0L
    private var dropped = 0L
    private var missing = 0L
    private var underruns = 0L
    private val lagReplies = ArrayDeque<Pair<Long, () -> Unit>>()

    /** Called on the playout worker after prior queued packets have entered this buffer. */
    fun deferLagReply(reply: () -> Unit) {
        val empty = synchronized(this) {
            if (frames.isEmpty()) true else {
                if (lagReplies.size >= 8) return
                lagReplies.addLast(frames.lastKey() to reply)
                // A lag marker is a flush boundary, including a partially filled prebuffer.
                if (expectedTimestamp == null) expectedTimestamp = frames.firstKey()
                false
            }
        }
        if (empty) reply()
    }

    fun afterPlayout(timestampMs: Long) {
        val ready = synchronized(this) {
            val callbacks = mutableListOf<() -> Unit>()
            while (lagReplies.isNotEmpty() && lagReplies.first().first <= timestampMs)
                callbacks.add(lagReplies.removeFirst().second)
            callbacks
        }
        ready.forEach { it() }
    }

    @Synchronized
    fun offer(frame: RasvenJitterFrame): Boolean {
        received += 1
        val expected = expectedTimestamp
        if (expected != null && frame.timestampMs < expected) {
            late += 1
            return false
        }
        if (frames.containsKey(frame.timestampMs)) {
            duplicates += 1
            return false
        }
        if (frames.size >= capacity) {
            val newest = frames.lastKey()
            if (frame.timestampMs >= newest) {
                dropped += 1
                return false
            }
            frames.pollLastEntry()
            dropped += 1
        }
        frames[frame.timestampMs] = frame.copy(payload = frame.payload.copyOf())
        lastCodec = frame.codec
        if (expectedTimestamp == null && frames.size >= targetDepth) {
            expectedTimestamp = frames.firstKey()
        }
        return true
    }

    /** Returns null during prebuffer/underrun. Missing packets advance the clock and are counted. */
    @Synchronized
    fun poll(): RasvenJitterFrame? {
        var expectedCandidate = expectedTimestamp
        if (expectedCandidate == null) {
            if (frames.size < targetDepth) return null
            expectedCandidate = frames.firstKey()
            expectedTimestamp = expectedCandidate
        }
        val expected = expectedCandidate ?: return null
        val exact = frames.remove(expected)
        if (exact != null) {
            expectedTimestamp = expected + frameDurationMs
            return exact
        }
        val next = frames.firstEntry()
        if (next == null) {
            val codec = lastCodec ?: return null
            underruns += 1
            missing += 1
            expectedTimestamp = expected + frameDurationMs
            return RasvenJitterFrame(expected, codec, ByteArray(0))
        }
        if (next.key > expected) {
            missing += 1
            expectedTimestamp = expected + frameDurationMs
            return RasvenJitterFrame(expected, next.value.codec, ByteArray(0))
        }
        return null
    }

    @Synchronized
    fun clear() {
        frames.clear()
        lagReplies.clear()
        expectedTimestamp = null
        lastCodec = null
    }

    @Synchronized fun size(): Int = frames.size

    @Synchronized
    fun recordExternalDrop() { dropped += 1 }

    @Synchronized
    fun metrics(): RasvenJitterMetrics = RasvenJitterMetrics(received, late, duplicates, dropped, missing, underruns)
}

/** One exact call's two-way media owner. No Android or IAX packet handling is delegated to Compose. */
internal class RasvenCallMediaEngine(
    private val ioFactory: RasvenAudioIoFactory,
) : AutoCloseable {
    private val lock = Any()
    // Serializes a mute command with the frame currently entering the transport.
    private val transmitLock = Any()
    private val running = AtomicBoolean(false)
    private val startedOnce = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val failureDelivered = AtomicBoolean(false)
    private val txTimestamp = AtomicLong(0)
    private val captureExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "rasven-audio-capture").apply { isDaemon = true } }
    private val playoutExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "rasven-audio-playout").apply { isDaemon = true } }
    // Focus callbacks arrive on the main looper. Use a dedicated control worker so a blocking
    // AudioRecord.read() can never delay teardown after focus loss.
    private val controlExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "rasven-audio-control").apply { isDaemon = true } }
    private sealed interface PlayoutEvent {
        data class Voice(val frame: RasvenIax2InboundVoice) : PlayoutEvent
        data class Lag(val reply: () -> Unit) : PlayoutEvent
    }
    private val rxQueue = ArrayBlockingQueue<PlayoutEvent>(32)

    private fun enqueuePlayout(event: PlayoutEvent) {
        if (rxQueue.offer(event)) return
        val discard = rxQueue.firstOrNull { it is PlayoutEvent.Voice }
        if (discard != null && rxQueue.remove(discard)) {
            jitter?.recordExternalDrop()
            rxQueue.offer(event)
        }
    }

    private var mediaSessionId: Long? = null
    private var muted = false
    private var held = false
    private var channel: RasvenIax2MediaChannel? = null
    private var input: RasvenAudioInput? = null
    private var output: RasvenAudioOutput? = null
    private var environment: RasvenAudioSessionEnvironment? = null
    private var jitter: RasvenBoundedJitterBuffer? = null
    private var format: RasvenAudioMediaFormat? = null
    private var failureCallback: ((RasvenMediaFailure) -> Unit)? = null

    fun start(
        mediaSessionId: Long,
        channel: RasvenIax2MediaChannel,
        format: RasvenAudioMediaFormat,
        onFailure: (RasvenMediaFailure) -> Unit,
    ): RasvenMediaStartResult = synchronized(lock) {
        if (closed.get() || !startedOnce.compareAndSet(false, true)) {
            return RasvenMediaStartResult.Failed(RasvenMediaFailure.AudioIoFailure)
        }
        if (channel.sessionId != mediaSessionId || channel.codec != format.codec) {
            return RasvenMediaStartResult.Failed(RasvenMediaFailure.UnsupportedCodec)
        }
        val env = ioFactory.createEnvironment()
        if (!env.activate {
                // Android dispatches focus changes on the supplied main-looper Handler. Media cleanup may block,
                // so hand teardown to a dedicated control worker instead of blocking Main or waiting
                // behind the long-running capture loop.
                try { controlExecutor.execute { fail(RasvenMediaFailure.AudioFocusDenied) } } catch (_: Exception) { }
            }) {
            env.close()
            return RasvenMediaStartResult.Failed(RasvenMediaFailure.AudioFocusDenied)
        }
        val inDevice = ioFactory.createInput(format)
        if (inDevice == null) {
            env.close()
            return RasvenMediaStartResult.Failed(RasvenMediaFailure.AudioInputInitFailed)
        }
        val outDevice = ioFactory.createOutput(format)
        if (outDevice == null) {
            inDevice.close(); env.close()
            return RasvenMediaStartResult.Failed(RasvenMediaFailure.AudioOutputInitFailed)
        }
        if (!outDevice.start()) {
            outDevice.close(); inDevice.close(); env.close()
            return RasvenMediaStartResult.Failed(RasvenMediaFailure.AudioOutputInitFailed)
        }
        if (!inDevice.start()) {
            outDevice.stop(); outDevice.close(); inDevice.close(); env.close()
            return RasvenMediaStartResult.Failed(RasvenMediaFailure.AudioInputInitFailed)
        }

        this.mediaSessionId = mediaSessionId
        muted = false
        held = false
        this.channel = channel
        this.input = inDevice
        this.output = outDevice
        this.environment = env
        this.jitter = RasvenBoundedJitterBuffer(format.frameDurationMs)
        this.format = format
        this.failureCallback = onFailure
        failureDelivered.set(false)
        txTimestamp.set(channel.initialTimestampMs and 0xffff_ffffL)
        rxQueue.clear()
        running.set(true)
        channel.setReceiver { inbound ->
            if (running.get() && inbound.sessionId == this.mediaSessionId) {
                enqueuePlayout(PlayoutEvent.Voice(inbound))
            }
        }
        channel.setLagReceiver { reply ->
            if (running.get()) enqueuePlayout(PlayoutEvent.Lag(reply))
        }
        captureExecutor.execute(::captureLoop)
        playoutExecutor.execute(::playoutLoop)
        RasvenMediaStartResult.Started
    }

    /** Applies mute only to this live media session. A successful return is the TX state truth. */
    fun setMuted(sessionId: Long, enabled: Boolean): Boolean = synchronized(transmitLock) {
        synchronized(lock) {
            if (!running.get() || mediaSessionId != sessionId) return false
            muted = enabled
            true
        }
    }

    fun isMuted(sessionId: Long): Boolean = synchronized(lock) {
        running.get() && mediaSessionId == sessionId && muted
    }

    fun setHeld(sessionId: Long, enabled: Boolean): Boolean = synchronized(transmitLock) {
        synchronized(lock) {
            if (!running.get() || mediaSessionId != sessionId) return false
            held = enabled
            true
        }
    }

    private fun captureLoop() {
        val currentFormat = synchronized(lock) { format } ?: return
        val pcm = ShortArray(currentFormat.samplesPerFrame)
        val encoded = ByteArray(currentFormat.encodedBytesPerFrame)
        var collected = 0
        var fullEpoch = -1L
        while (running.get()) {
            val count = try { input?.read(pcm, collected, pcm.size - collected) ?: -1 } catch (_: Exception) { -1 }
            if (count < 0) {
                if (running.get()) fail(RasvenMediaFailure.AudioIoFailure)
                break
            }
            if (count == 0) continue
            collected += count
            if (collected < currentFormat.samplesPerFrame) continue
            collected = 0
            val timestamp = txTimestamp.getAndAdd(currentFormat.frameDurationMs.toLong()) and 0xffff_ffffL
            // RFC 5456 mini timestamps are the low 16 bits and wrap every 65.536 seconds.
            // The first packet is Full Voice, then a Full Voice frame is sent again after each 0x10000 wrap.
            val sent = synchronized(transmitLock) {
                val snapshot = synchronized(lock) { Triple(channel, muted || held, mediaSessionId) }
                val active = snapshot.first
                if (!running.get() || snapshot.third == null || active == null) {
                    false
                } else {
                    // Silence keeps packet timing and RX alive while no microphone sample leaves the device.
                    if (snapshot.second) java.util.Arrays.fill(pcm, 0)
                    RasvenG711.encodeInto(currentFormat.codec, pcm, currentFormat.samplesPerFrame, encoded)
                    val epoch = timestamp ushr 16
                    if (fullEpoch != epoch) {
                        fullEpoch = epoch
                        active.sendFullVoice(timestamp, encoded)
                    } else {
                        active.sendMiniVoice(timestamp, encoded)
                    }
                }
            }
            if (!sent) {
                fail(RasvenMediaFailure.MediaTransportFailure)
                break
            }
        }
    }

    private fun playoutLoop() {
        val currentFormat = synchronized(lock) { format } ?: return
        val decoded = ShortArray(currentFormat.samplesPerFrame)
        var nextTick = System.nanoTime()
        while (running.get()) {
            var incoming = try {
                rxQueue.poll(20, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                if (!running.get()) break
                continue
            }
            while (incoming != null) {
                when (val event = incoming) {
                    is PlayoutEvent.Voice -> jitter?.offer(RasvenJitterFrame(event.frame.timestampMs, event.frame.codec, event.frame.payload))
                    is PlayoutEvent.Lag -> jitter?.deferLagReply(event.reply)
                }
                incoming = rxQueue.poll()
            }
            val frame = jitter?.poll()
            if (frame != null) {
                if (frame.payload.isEmpty()) {
                    java.util.Arrays.fill(decoded, 0)
                } else if (frame.payload.size == currentFormat.encodedBytesPerFrame) {
                    RasvenG711.decodeInto(frame.codec, frame.payload, frame.payload.size, decoded)
                } else {
                    continue
                }
                var writtenTotal = 0
                var zeroWrites = 0
                while (writtenTotal < decoded.size && running.get()) {
                    val written = try {
                        output?.write(decoded, writtenTotal, decoded.size - writtenTotal) ?: -1
                    } catch (_: Exception) {
                        -1
                    }
                    if (written < 0) {
                        if (running.get()) fail(RasvenMediaFailure.AudioIoFailure)
                        break
                    }
                    if (written == 0) {
                        zeroWrites += 1
                        if (zeroWrites >= 3) {
                            if (running.get()) fail(RasvenMediaFailure.AudioIoFailure)
                            break
                        }
                        Thread.yield()
                    } else {
                        writtenTotal += written
                        zeroWrites = 0
                    }
                }
                if (!running.get() || writtenTotal < decoded.size) break
                jitter?.afterPlayout(frame.timestampMs)
            }
            nextTick += TimeUnit.MILLISECONDS.toNanos(currentFormat.frameDurationMs.toLong())
            val remaining = nextTick - System.nanoTime()
            if (remaining > 0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(remaining)
                } catch (_: InterruptedException) {
                    if (!running.get()) break
                }
            } else if (remaining < -TimeUnit.MILLISECONDS.toNanos(200)) {
                nextTick = System.nanoTime()
            }
        }
    }

    private fun fail(reason: RasvenMediaFailure) {
        if (!failureDelivered.compareAndSet(false, true)) return
        val callback = synchronized(lock) { failureCallback }
        stop()
        callback?.invoke(reason)
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        synchronized(lock) {
            channel?.setReceiver(null)
            channel?.setLagReceiver(null)
            channel = null
            try { input?.stop() } catch (_: Exception) { }
            try { output?.stop() } catch (_: Exception) { }
            try { input?.close() } catch (_: Exception) { }
            try { output?.close() } catch (_: Exception) { }
            try { environment?.close() } catch (_: Exception) { }
            input = null
            output = null
            environment = null
            jitter?.clear()
            jitter = null
            format = null
            mediaSessionId = null
            muted = false
            held = false
            failureCallback = null
            rxQueue.clear()
        }
    }

    internal fun jitterMetricsForTest(): RasvenJitterMetrics? = synchronized(lock) { jitter?.metrics() }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stop()
        captureExecutor.shutdownNow()
        playoutExecutor.shutdownNow()
        controlExecutor.shutdownNow()
    }
}
