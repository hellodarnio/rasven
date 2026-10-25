package com.rasven.android.iax2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class RasvenIax2SignalingEngineTest {
    @Test fun registrationRejectIsPermanentButDeadlineAndTransportAreRetryable() {
        val rejected = harness()
        rejected.engine.register { }
        val req = rejected.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last()
        rejected.transport.inject(peerIax(300, req.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.REGREJ))
        assertFalse(rejected.engine.lastRegistrationFailureRetryable)
        rejected.engine.close()
        val expired = harness()
        expired.engine.register { }
        assertTrue(expired.scheduler.runFirst(30_000))
        assertTrue(expired.engine.lastRegistrationFailureRetryable)
        expired.engine.close()
        val transport = harness()
        transport.engine.register { }
        transport.transport.fail()
        assertTrue(transport.engine.lastRegistrationFailureRetryable)
        transport.engine.close()
    }
    @Test fun invalidationTerminatesAnsweredIncomingExactlyOnce() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2IncomingEvent>()
        h.engine.setIncomingListener(events::add)
        h.transport.inject(incomingNew())
        val ringing = events.single() as RasvenIax2IncomingEvent.Ringing
        assertTrue(h.engine.answerIncoming(ringing.sessionId))
        h.engine.close(); h.engine.close()
        assertEquals(listOf(ringing, RasvenIax2IncomingEvent.RemoteCancel(ringing.sessionId, true)), events)
        assertEquals(0, h.engine.activeSessionCountForTest())
    }
    @Test fun invalidationTerminatesUnansweredIncomingWithoutFabricatingAnswer() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2IncomingEvent>()
        h.engine.setIncomingListener(events::add)
        h.transport.inject(incomingNew())
        val ringing = events.single() as RasvenIax2IncomingEvent.Ringing
        h.engine.close()
        assertEquals(RasvenIax2IncomingEvent.RemoteCancel(ringing.sessionId, false), events.last())
    }
    @Test fun invalidationTerminatesOutgoingAndDoesNotRepeatAnAlreadyTerminalEvent() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(91, "305", media, events::add)
        h.engine.close(); h.engine.close()
        assertEquals(listOf(RasvenIax2OutgoingEvent.NetworkFailure), events)
        val next = harness(); register(next)
        val alreadyEnded = mutableListOf<RasvenIax2OutgoingEvent>()
        next.engine.startOutgoing(92, "305", media, alreadyEnded::add)
        val setup = lastNew(next)
        next.transport.inject(peerControl(401, setup.sourceCallNumber, 0, 1, RasvenIax2.ControlSubclass.BUSY))
        next.engine.close()
        assertEquals(listOf(RasvenIax2OutgoingEvent.Busy), alreadyEnded)
    }
    @Test fun replacementEngineDoesNotReuseAnIncomingProductSessionIdentity() {
        val policy = com.rasven.android.product.call.RasvenIncomingAdmissionPolicy()
        val identities = mutableListOf<Long>()
        repeat(2) {
            val h = harness()
            try {
                register(h)
                h.engine.setIncomingListener { event ->
                    if (event is RasvenIax2IncomingEvent.Ringing) {
                        identities += event.sessionId
                        assertEquals("A fresh engine's real call must not be classified as a duplicate",
                            com.rasven.android.product.call.RasvenIncomingAdmissionPolicy.Outcome.Present,
                            policy.decide(event.sessionId))
                    }
                }
                h.transport.inject(incomingNew())
            } finally { h.engine.close() }
        }
        assertEquals(2, identities.size)
        assertNotEquals(identities[0], identities[1])
    }

    @Test fun staleIncomingAnswerCannotAnswerTheReplacementEnginesCall() {
        val first = harness()
        val next = harness()
        try {
            register(first)
            var firstId = -1L
            first.engine.setIncomingListener { if (it is RasvenIax2IncomingEvent.Ringing) firstId = it.sessionId }
            first.transport.inject(incomingNew())
            first.engine.close()
            register(next)
            var nextId = -1L
            next.engine.setIncomingListener { if (it is RasvenIax2IncomingEvent.Ringing) nextId = it.sessionId }
            next.transport.inject(incomingNew())
            assertFalse("An old notification/Telecom action must not answer a different call",
                next.engine.answerIncoming(firstId))
            assertTrue(next.engine.answerIncoming(nextId))
        } finally { first.engine.close(); next.engine.close() }
    }

    @Test fun unansweredQualificationDoesNotFailRegistrationAndMonitoringIsBounded() {
        val h = harness()
        register(h)
        repeat(9) { index -> h.transport.inject(RasvenIax2FullFrame(700 + index, 0, false,
            12345L + index, 0, 0, RasvenIax2.FrameType.IAX, 30)) }
        assertEquals(8, h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).count { it.subclass == 3 })
        repeat(8) { assertTrue(h.scheduler.runFirst(500)) }
        for (delay in listOf(1000L, 2000L, 4000L, 8000L)) repeat(8) { assertTrue(h.scheduler.runFirst(delay)) }
        assertTrue(h.engine.isRegistered)
        assertFalse(h.scheduler.hasActive(500))
        assertFalse(h.scheduler.hasActive(8000))
        h.engine.close()
        assertFalse(h.scheduler.hasActive(48000))
    }
    @Test fun peerQualificationPokeReceivesReliableTimestampEchoAndAckRetiresIt() {
        val h = harness()
        val poke = RasvenIax2FullFrame(700, 0, false, 12345, 0, 0, RasvenIax2.FrameType.IAX, 30)
        h.transport.inject(poke)
        val pong = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).lastOrNull { it.subclass == 3 }
        assertTrue("RFC 5456 POKE must receive PONG", pong != null)
        pong!!
        assertEquals(700, pong.destinationCallNumber)
        assertEquals(12345L, pong.timestamp)
        assertEquals(0, pong.oSeqNo)
        assertEquals(1, pong.iSeqNo)
        h.transport.inject(poke.copy(retransmission = true))
        val repeated = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last()
        assertEquals(pong.sourceCallNumber, repeated.sourceCallNumber)
        assertTrue(repeated.retransmission)
        h.transport.inject(RasvenIax2FullFrame(700, pong.sourceCallNumber, false, 12345, 1, 1,
            RasvenIax2.FrameType.IAX, RasvenIax2.IaxSubclass.ACK))
        assertFalse(h.scheduler.hasActive(500))
        h.engine.close()
    }
    private class FakeTransport(private val autoReady: Boolean = true) : RasvenIax2PacketTransport {
        private var packetCallback: ((ByteArray) -> Unit)? = null
        private var failureCallback: (() -> Unit)? = null
        private var readyCallback: (() -> Unit)? = null
        private val closed = AtomicBoolean(false)
        val sent = mutableListOf<ByteArray>()
        override var isReady: Boolean = false
            private set

        override fun start(onReady: () -> Unit, onPacket: (ByteArray) -> Unit, onFailure: () -> Unit) {
            packetCallback = onPacket
            failureCallback = onFailure
            readyCallback = onReady
            if (autoReady) ready()
        }

        fun ready() {
            isReady = true
            readyCallback?.invoke()
        }

        override fun send(packet: ByteArray): Boolean {
            if (!isReady || closed.get()) return false
            sent += packet.copyOf()
            return true
        }

        fun inject(frame: RasvenIax2FullFrame) {
            packetCallback?.invoke(RasvenIax2FrameCodec.encode(frame))
        }

        fun fail() {
            isReady = false
            failureCallback?.invoke()
        }

        override fun close() {
            closed.set(true)
            isReady = false
        }
    }

    private class FakeScheduler : RasvenIax2Scheduler {
        private data class Task(val delay: Long, var cancelled: Boolean, val block: () -> Unit)
        private val tasks = mutableListOf<Task>()

        override fun schedule(delayMs: Long, task: () -> Unit): RasvenIax2Cancellation {
            val scheduled = Task(delayMs, false, task)
            tasks += scheduled
            return RasvenIax2Cancellation { scheduled.cancelled = true }
        }

        fun runFirst(delay: Long): Boolean {
            val task = tasks.firstOrNull { !it.cancelled && it.delay == delay } ?: return false
            task.cancelled = true
            task.block()
            return true
        }

        fun hasActive(delay: Long): Boolean = tasks.any { !it.cancelled && it.delay == delay }
        override fun close() { tasks.forEach { it.cancelled = true } }
    }

    private class FakeClock(var now: Long = 1000L) : RasvenIax2Clock {
        override fun nowMillis(): Long = now
    }

    private data class Harness(
        val transport: FakeTransport,
        val scheduler: FakeScheduler,
        val clock: FakeClock,
        val engine: RasvenIax2SignalingEngine,
    )

    private val account = RasvenIax2Account("127.0.0.1", 4569, "fixture-user", "fixture-secret")
    private val media = RasvenIax2MediaOffer(format = 4, capability = 4, codecPreferences = "ulaw")

    private fun harness(callTimeoutMs: Long = 45_000, autoReady: Boolean = true): Harness {
        val transport = FakeTransport(autoReady)
        val scheduler = FakeScheduler()
        val clock = FakeClock()
        return Harness(
            transport,
            scheduler,
            clock,
            RasvenIax2SignalingEngine(account, transport, scheduler, clock, callTimeoutMs = callTimeoutMs),
        )
    }

    private fun register(h: Harness) {
        var result: RasvenIax2RegistrationEvent? = null
        h.engine.register { result = it }
        val initial = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.REGREQ }
        assertEquals("fixture-user", initial.ieString(RasvenIax2.IeType.USERNAME))
        assertEquals(60, initial.ieU16(RasvenIax2.IeType.REFRESH))

        h.transport.inject(
            peerIax(
                remote = 300,
                local = initial.sourceCallNumber,
                oSeq = 0,
                iSeq = 1,
                subclass = RasvenIax2.IaxSubclass.REGAUTH,
                ies = listOf(
                    RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.AUTHMETHODS, RasvenIax2.AuthMethod.MD5),
                    RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.CHALLENGE, "abc123"),
                ),
            ),
        )
        val authenticated = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.REGREQ }
        val expectedDigest = MessageDigest.getInstance("MD5")
            .digest("abc123fixture-secret".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(expectedDigest, authenticated.ieString(RasvenIax2.IeType.MD5_RESULT))

        h.transport.inject(
            peerIax(
                remote = 300,
                local = initial.sourceCallNumber,
                oSeq = 1,
                iSeq = 2,
                subclass = RasvenIax2.IaxSubclass.REGACK,
                ies = listOf(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH, 60)),
            ),
        )
        assertEquals(RasvenIax2RegistrationEvent.Registered, result)
        assertTrue(h.engine.isRegistered)
    }

    @Test fun shortRegistrationLeaseRefreshesBeforeActualExpiry() {
        val h = harness()
        val events = mutableListOf<RasvenIax2RegistrationEvent>()
        h.engine.register(events::add)
        val request = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last()
        h.transport.inject(peerIax(301, request.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.REGACK,
            listOf(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH, 1))))
        assertEquals(listOf(RasvenIax2RegistrationEvent.Registered), events)
        assertTrue("One-second lease must refresh before expiry", h.scheduler.runFirst(800))
        val refresh = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last()
        assertEquals(RasvenIax2.IaxSubclass.REGREQ, refresh.subclass)
        assertNotEquals(request.sourceCallNumber, refresh.sourceCallNumber)
        h.engine.close()
    }

    @Test fun zeroRegistrationLeaseFailsInsteadOfClaimingRegistered() {
        val h = harness()
        val events = mutableListOf<RasvenIax2RegistrationEvent>()
        h.engine.register(events::add)
        val request = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last()
        h.transport.inject(peerIax(301, request.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.REGACK,
            listOf(RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.REFRESH, 0))))
        assertEquals(listOf(RasvenIax2RegistrationEvent.Failed), events)
        assertFalse(h.engine.isRegistered)
        assertFalse(h.scheduler.hasActive(4000))
        h.engine.close()
    }

    @Test fun closingFromRegisteredCallbackCannotScheduleGhostRefresh() {
        val h = harness()
        h.engine.register { if (it == RasvenIax2RegistrationEvent.Registered) h.engine.close() }
        val request = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last()
        h.transport.inject(peerIax(301, request.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.REGACK))
        assertFalse(h.engine.isRegistered)
        assertFalse(h.scheduler.hasActive(48000))
    }

    @Test fun refreshTimeoutTerminatesRegistrationOnceAndRetiresTimers() {
        val h = harness()
        val events = mutableListOf<RasvenIax2RegistrationEvent>()
        h.engine.register(events::add)
        val request = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last()
        h.transport.inject(peerIax(301, request.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.REGACK))
        assertTrue(h.scheduler.runFirst(48000))
        assertTrue(h.scheduler.runFirst(30000))
        assertEquals(listOf(RasvenIax2RegistrationEvent.Registered, RasvenIax2RegistrationEvent.Failed), events)
        assertFalse(h.engine.isRegistered)
        assertFalse(h.scheduler.hasActive(500))
        assertFalse(h.scheduler.runFirst(30000))
        h.engine.close()
    }

    @Test fun allocatorProvidesUniqueNumbersAndReleasesThem() {
        val allocator = RasvenIax2CallNumberAllocator()
        val a = allocator.allocate()
        val b = allocator.allocate()
        assertNotEquals(a, b)
        assertTrue(allocator.isAllocated(a))
        allocator.release(a)
        assertFalse(allocator.isAllocated(a))
    }

    @Test fun registrationUsesPacketExchangeAndMd5ChallengeWithoutPlaintextFallback() {
        val h = harness()
        register(h)
        assertEquals(0, h.engine.activeSessionCountForTest())
        h.engine.close()
    }

    @Test fun explicitDisconnectReleasesRegistrarWithAuthenticatedRegrelAndBlocksNewCalls() {
        val h = harness()
        register(h)
        var released: Boolean? = null

        h.engine.releaseRegistration { released = it }

        val initialRelease = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.subclass == RasvenIax2.IaxSubclass.REGREL }
        assertEquals("fixture-user", initialRelease.ieString(RasvenIax2.IeType.USERNAME))
        assertEquals(null, initialRelease.ieString(RasvenIax2.IeType.MD5_RESULT))
        assertEquals(null, initialRelease.ieU16(RasvenIax2.IeType.REFRESH))
        assertFalse(h.engine.isRegistered)
        assertEquals(RasvenIax2StartResult.NotRegistered,
            h.engine.startOutgoing(99, "200", media) {})
        assertEquals(null, released)

        h.transport.inject(peerIax(
            remote = 300,
            local = initialRelease.sourceCallNumber,
            oSeq = 0,
            iSeq = 1,
            subclass = RasvenIax2.IaxSubclass.REGAUTH,
            ies = listOf(
                RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.AUTHMETHODS, RasvenIax2.AuthMethod.MD5),
                RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.CHALLENGE, "releaseChallenge"),
            ),
        ))
        val authenticatedRelease = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.subclass == RasvenIax2.IaxSubclass.REGREL }
        val expectedDigest = MessageDigest.getInstance("MD5")
            .digest("releaseChallengefixture-secret".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("fixture-user", authenticatedRelease.ieString(RasvenIax2.IeType.USERNAME))
        assertEquals(expectedDigest, authenticatedRelease.ieString(RasvenIax2.IeType.MD5_RESULT))
        assertEquals(null, authenticatedRelease.ieU16(RasvenIax2.IeType.REFRESH))

        h.transport.inject(peerIax(
            remote = 300,
            local = initialRelease.sourceCallNumber,
            oSeq = 1,
            iSeq = 2,
            subclass = RasvenIax2.IaxSubclass.REGACK,
        ))
        assertEquals(true, released)
        assertFalse(h.engine.isRegistered)
        assertFalse(h.scheduler.hasActive(10_000L))
        h.engine.close()
    }

    @Test fun releaseRejectionAndTimeoutReportUnknownServerReleaseAndRetireLocalSession() {
        val rejected = harness()
        register(rejected)
        var rejection: Boolean? = null
        rejected.engine.releaseRegistration { rejection = it }
        val rejectRequest = rejected.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.subclass == RasvenIax2.IaxSubclass.REGREL }
        rejected.transport.inject(peerIax(300, rejectRequest.sourceCallNumber, 0, 1,
            RasvenIax2.IaxSubclass.REGREJ))
        assertEquals(false, rejection)
        assertFalse(rejected.engine.isRegistered)
        assertFalse(rejected.scheduler.hasActive(10_000L))
        rejected.engine.close()

        val timedOut = harness()
        register(timedOut)
        var timeout: Boolean? = null
        timedOut.engine.releaseRegistration { timeout = it }
        assertTrue(timedOut.scheduler.runFirst(10_000L))
        assertEquals(false, timeout)
        assertFalse(timedOut.engine.isRegistered)
        assertEquals(0, timedOut.engine.activeSessionCountForTest())
        assertFalse(timedOut.scheduler.hasActive(10_000L))
        timedOut.engine.close()
    }

    @Test fun transportFailureBeforeProtocolSessionTerminatesRegistration() {
        val h = harness(autoReady = false)
        val results = mutableListOf<RasvenIax2RegistrationEvent>()
        h.engine.register(results::add)
        h.transport.fail()
        assertEquals(listOf(RasvenIax2RegistrationEvent.Failed), results)
        h.transport.ready() // Late DNS/socket completion cannot restart a failed attempt.
        assertTrue(h.transport.sent.isEmpty())
        h.engine.close()
    }

    @Test fun transportFailureBeforeRegisterIsReportedImmediately() {
        val h = harness(autoReady = false)
        h.transport.fail()
        val results = mutableListOf<RasvenIax2RegistrationEvent>()
        h.engine.register(results::add)
        assertEquals(listOf(RasvenIax2RegistrationEvent.Failed), results)
        h.engine.close()
    }

    @Test fun unresolvedTransportDeadlineTerminatesRegistrationOnce() {
        val h = harness(autoReady = false)
        val results = mutableListOf<RasvenIax2RegistrationEvent>()
        h.engine.register(results::add)
        assertTrue(h.scheduler.runFirst(30_000L))
        assertEquals(listOf(RasvenIax2RegistrationEvent.Failed), results)
        h.transport.ready()
        assertTrue(h.transport.sent.isEmpty())
        h.engine.close()
    }

    @Test fun unansweredRegistrationDeadlineTerminatesProtocolSession() {
        val h = harness()
        val results = mutableListOf<RasvenIax2RegistrationEvent>()
        h.engine.register(results::add)
        assertEquals(1, h.transport.sent.size)
        assertTrue(h.scheduler.runFirst(30_000L))
        assertEquals(listOf(RasvenIax2RegistrationEvent.Failed), results)
        assertFalse(h.engine.isRegistered)
        h.engine.close()
    }

    @Test fun ackWithoutAuthOrFinalReplyCannotLeaveRegistrationPendingForever() {
        val h = harness()
        val results = mutableListOf<RasvenIax2RegistrationEvent>()
        h.engine.register(results::add)
        val request = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).single()
        h.transport.inject(peerIax(remote = 300, local = request.sourceCallNumber,
            oSeq = 0, iSeq = 1, subclass = RasvenIax2.IaxSubclass.ACK))
        assertTrue(results.isEmpty())
        assertFalse(h.scheduler.hasActive(500L)) // The peer ACK has retired retransmission.
        assertTrue(h.scheduler.runFirst(30_000L))
        assertEquals(listOf(RasvenIax2RegistrationEvent.Failed), results)
        h.engine.close()
    }

    @Test fun registrationRejectionTerminatesWithoutWaitingForDeadline() {
        val h = harness()
        val results = mutableListOf<RasvenIax2RegistrationEvent>()
        h.engine.register(results::add)
        val request = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).single()
        h.transport.inject(peerIax(remote = 300, local = request.sourceCallNumber,
            oSeq = 0, iSeq = 1, subclass = RasvenIax2.IaxSubclass.REGREJ))
        assertEquals(listOf(RasvenIax2RegistrationEvent.Failed), results)
        assertFalse(h.scheduler.hasActive(30_000L))
        h.engine.close()
    }

    private fun incomingNew(remote: Int = 510): RasvenIax2FullFrame = RasvenIax2FullFrame(
        sourceCallNumber = remote,
        destinationCallNumber = 0,
        retransmission = false,
        timestamp = 1,
        oSeqNo = 0,
        iSeqNo = 0,
        frameType = RasvenIax2.FrameType.IAX,
        subclass = RasvenIax2.IaxSubclass.NEW,
        informationElements = listOf(
            RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.VERSION, 2),
            RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.CALLING_NUMBER, "305"),
            RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.CALLED_NUMBER, "100"),
            RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.CAPABILITY, 4),
            RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4),
        ),
    )

    @Test fun incomingNewCreatesOneSessionAndDuplicateCannotCreateAnother() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2IncomingEvent>()
        h.engine.setIncomingListener(events::add)
        val incoming = incomingNew()
        h.transport.inject(incoming)
        h.transport.inject(incoming.copy(retransmission = true))
        val ringing = events.single() as RasvenIax2IncomingEvent.Ringing
        assertEquals("305", ringing.callingNumber)
        assertEquals("100", ringing.calledNumber)
        val responses = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
        assertEquals(1, responses.count { it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.ACCEPT })
        assertEquals(1, responses.count { it.frameType == 4 && it.subclass == RasvenIax2.ControlSubclass.RINGING })
        assertFalse(h.engine.answerIncoming(ringing.sessionId + 1))
        assertTrue(h.engine.answerIncoming(ringing.sessionId))
        assertFalse(h.engine.answerIncoming(ringing.sessionId))
        assertEquals(1, h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .count { it.frameType == 4 && it.subclass == RasvenIax2.ControlSubclass.ANSWER })
        val accept = responses.last { it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.ACCEPT }
        h.transport.inject(peerIax(510, accept.sourceCallNumber, 1, 3, RasvenIax2.IaxSubclass.HANGUP))
        assertEquals(RasvenIax2IncomingEvent.RemoteCancel(ringing.sessionId, true), events.last())
        assertFalse(h.engine.answerIncoming(ringing.sessionId))
        h.engine.close()
    }

    @Test fun answeredIncomingHoldUsesSameSessionAndStopsAtTermination() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2IncomingEvent>()
        h.engine.setIncomingListener(events::add)
        h.transport.inject(incomingNew(513))
        val call = events.single() as RasvenIax2IncomingEvent.Ringing
        assertFalse(h.engine.setHeld(call.sessionId, true))
        assertTrue(h.engine.answerIncoming(call.sessionId))
        assertTrue(h.engine.setHeld(call.sessionId, true))
        assertTrue(h.engine.setHeld(call.sessionId, true))
        assertTrue(h.engine.setHeld(call.sessionId, false))
        val controls = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .filter { it.frameType == RasvenIax2.FrameType.CONTROL &&
                it.subclass in listOf(RasvenIax2.ControlSubclass.HOLD, RasvenIax2.ControlSubclass.UNHOLD) }
        assertEquals(listOf(RasvenIax2.ControlSubclass.HOLD, RasvenIax2.ControlSubclass.UNHOLD), controls.map { it.subclass })
        assertTrue(controls.all { it.destinationCallNumber == 513 })
        assertTrue(h.engine.rejectIncoming(call.sessionId))
        assertFalse(h.engine.setHeld(call.sessionId, true))
        h.engine.close()
    }

    @Test fun incomingRemoteCancelBeforeAnswerRejectsLatePermissionCompletion() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2IncomingEvent>()
        h.engine.setIncomingListener(events::add)
        h.transport.inject(incomingNew(511))
        val ringing = events.single() as RasvenIax2IncomingEvent.Ringing
        val accept = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.ACCEPT }
        h.transport.inject(peerIax(511, accept.sourceCallNumber, 1, 2, RasvenIax2.IaxSubclass.HANGUP))
        assertEquals(RasvenIax2IncomingEvent.RemoteCancel(ringing.sessionId, false), events.last())
        assertFalse(h.engine.answerIncoming(ringing.sessionId))
        assertEquals(0, h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .count { it.frameType == 4 && it.subclass == RasvenIax2.ControlSubclass.ANSWER })
        h.engine.close()
    }

    @Test fun dndBusyRejectSuppressesRetransmittedNewAfterAcknowledgedHangup() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2IncomingEvent>()
        h.engine.setIncomingListener(events::add)
        val incoming = incomingNew(512)
        h.transport.inject(incoming)
        val ring = events.single() as RasvenIax2IncomingEvent.Ringing
        assertTrue(h.engine.rejectIncoming(ring.sessionId, busy = true))
        val hangup = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.frameType == RasvenIax2.FrameType.IAX && it.subclass == RasvenIax2.IaxSubclass.HANGUP }
        assertEquals(RasvenIax2.CauseCode.USER_BUSY, hangup.ieU8(RasvenIax2.IeType.CAUSECODE))
        h.transport.inject(peerIax(512, hangup.sourceCallNumber, 1, hangup.oSeqNo + 1,
            RasvenIax2.IaxSubclass.ACK))
        h.transport.inject(incoming.copy(retransmission = true))
        assertEquals(1, events.count { it is RasvenIax2IncomingEvent.Ringing })
        assertEquals(1, h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .count { it.frameType == RasvenIax2.FrameType.IAX && it.subclass == RasvenIax2.IaxSubclass.HANGUP })
        h.engine.close()
    }

    @Test fun unsupportedRegistrationAuthFailsInsteadOfUsingInsecureFallback() {
        val h = harness()
        var result: RasvenIax2RegistrationEvent? = null
        h.engine.register { result = it }
        val request = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).last()
        h.transport.inject(
            peerIax(
                300, request.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.REGAUTH,
                ies = listOf(
                    RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.AUTHMETHODS, RasvenIax2.AuthMethod.RSA),
                    RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.CHALLENGE, "challenge"),
                ),
            ),
        )
        assertEquals(RasvenIax2RegistrationEvent.Failed, result)
        assertFalse(h.engine.isRegistered)
    }

    @Test fun productionStyleNoMediaOfferRefusesCallWithoutSendingNew() {
        val h = harness()
        register(h)
        val before = h.transport.sent.size
        val result = h.engine.startOutgoing(1, "305", null) { }
        assertEquals(RasvenIax2StartResult.MediaCapabilityUnavailable, result)
        assertEquals(before, h.transport.sent.size)
    }

    @Test fun outgoingNewCarriesRequiredSignalingAndBusyIsPacketDrivenOnce() {
        val h = harness()
        register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        assertEquals(RasvenIax2StartResult.Accepted, h.engine.startOutgoing(11, "305", media, events::add))
        val new = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.NEW }
        assertEquals(2, new.ieU16(RasvenIax2.IeType.VERSION))
        assertEquals("305", new.ieString(RasvenIax2.IeType.CALLED_NUMBER))
        assertEquals(4L, new.ieU32(RasvenIax2.IeType.FORMAT))
        assertEquals("ulaw", new.ieString(RasvenIax2.IeType.CODEC_PREFS))

        h.transport.inject(peerIax(400, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT,
            ies = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))))
        h.transport.inject(peerControl(400, new.sourceCallNumber, 1, 1, RasvenIax2.ControlSubclass.BUSY))
        h.transport.inject(peerControl(400, new.sourceCallNumber, 1, 1, RasvenIax2.ControlSubclass.BUSY, retransmission = true))
        assertEquals(listOf(RasvenIax2OutgoingEvent.Busy), events)
        assertEquals(0, h.engine.activeSessionCountForTest())
    }

    @Test fun rejectCauseCodeMapsConservativelyAndExactly() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(12, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(peerIax(401, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.REJECT,
            ies = listOf(RasvenIax2FrameCodec.u8Ie(RasvenIax2.IeType.CAUSECODE, RasvenIax2.CauseCode.CALL_REJECTED))))
        assertEquals(listOf(RasvenIax2OutgoingEvent.Rejected), events)
    }


    @Test fun rejectWithoutCallRejectedCauseDoesNotMasqueradeAsDestinationRejected() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(13, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(peerIax(403, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.REJECT))
        assertEquals(listOf(RasvenIax2OutgoingEvent.OtherFailure), events)
    }

    @Test fun remoteHangupBusyNoAnswerAndNetworkCausesRemainDistinct() {
        fun result(cause: Int): RasvenIax2OutgoingEvent {
            val h = harness(); register(h)
            val events = mutableListOf<RasvenIax2OutgoingEvent>()
            h.engine.startOutgoing(100L + cause, "305", media, events::add)
            val new = lastNew(h)
            h.transport.inject(peerIax(402, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.HANGUP,
                ies = listOf(RasvenIax2FrameCodec.u8Ie(RasvenIax2.IeType.CAUSECODE, cause))))
            return events.single()
        }
        assertEquals(RasvenIax2OutgoingEvent.Busy, result(RasvenIax2.CauseCode.USER_BUSY))
        assertEquals(RasvenIax2OutgoingEvent.NoAnswer, result(RasvenIax2.CauseCode.NO_ANSWER))
        assertEquals(RasvenIax2OutgoingEvent.NetworkFailure, result(RasvenIax2.CauseCode.NETWORK_OUT_OF_ORDER))
    }

    @Test fun answerEmitsConnectedExactlyOnceAndKeepsLiveSession() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(20, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(peerIax(410, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT,
            ies = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))))
        h.transport.inject(peerControl(410, new.sourceCallNumber, 1, 1, RasvenIax2.ControlSubclass.ANSWER))
        h.transport.inject(peerControl(410, new.sourceCallNumber, 1, 1, RasvenIax2.ControlSubclass.ANSWER, retransmission = true))
        assertEquals(1, events.size)
        assertTrue(events.single() is RasvenIax2OutgoingEvent.Connected)
        assertEquals(1, h.engine.activeSessionCountForTest())
    }

    @Test fun ringingIsDeliveredOnceWithoutEndingTheOutgoingLeg() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(21, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(peerIax(411, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT,
            ies = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))))
        h.transport.inject(peerControl(411, new.sourceCallNumber, 1, 1, RasvenIax2.ControlSubclass.PROCEEDING))
        h.transport.inject(peerControl(411, new.sourceCallNumber, 2, 1, RasvenIax2.ControlSubclass.RINGING))
        h.transport.inject(peerControl(411, new.sourceCallNumber, 2, 1, RasvenIax2.ControlSubclass.RINGING,
            retransmission = true))
        assertEquals(listOf(RasvenIax2OutgoingEvent.Ringing), events)
        assertEquals(1, h.engine.activeSessionCountForTest())
        h.transport.inject(peerControl(411, new.sourceCallNumber, 3, 1, RasvenIax2.ControlSubclass.ANSWER))
        assertEquals(2, events.size)
        assertTrue(events.last() is RasvenIax2OutgoingEvent.Connected)
    }

    @Test fun noAnswerComesFromAttemptBoundSessionTimeoutAndCleansSession() {
        val h = harness(callTimeoutMs = 45_000); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(30, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(peerIax(415, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT,
            ies = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))))
        assertTrue(h.scheduler.runFirst(45_000))
        assertEquals(listOf(RasvenIax2OutgoingEvent.NoAnswer), events)
        assertEquals(0, h.engine.activeSessionCountForTest())
        assertEquals(1, h.engine.protocolSessionCountForTest())
        val hangup = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.HANGUP }
        h.transport.inject(peerIax(415, new.sourceCallNumber, 1, (hangup.oSeqNo + 1) and 0xff, RasvenIax2.IaxSubclass.ACK))
        assertEquals(0, h.engine.protocolSessionCountForTest())
    }

    @Test fun answerCancelsNoAnswerTimeout() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(31, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(peerIax(420, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT,
            ies = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))))
        h.transport.inject(peerControl(420, new.sourceCallNumber, 1, 1, RasvenIax2.ControlSubclass.ANSWER))
        assertFalse(h.scheduler.runFirst(45_000))
        assertEquals(1, events.size)
    }

    @Test fun stopIndicationDoesNotBlockSubsequentRemoteHangup() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(32, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(peerIax(421, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT,
            ies = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))))
        h.transport.inject(peerControl(421, new.sourceCallNumber, 1, 1, RasvenIax2.ControlSubclass.ANSWER))
        val stop = peerControl(421, new.sourceCallNumber, 2, 1, 0).copy(subclass = -1, compressedSubclass = true)
        h.transport.inject(stop)
        h.transport.inject(stop)
        h.transport.inject(peerIax(421, new.sourceCallNumber, 3, 1, RasvenIax2.IaxSubclass.HANGUP))
        assertEquals(2, events.size)
        assertTrue(events.first() is RasvenIax2OutgoingEvent.Connected)
        assertEquals(RasvenIax2OutgoingEvent.RemoteHangup, events.last())
        assertEquals(0, h.engine.activeSessionCountForTest())
        assertFalse(h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).any {
            it.frameType == RasvenIax2.FrameType.IAX && it.subclass == RasvenIax2.IaxSubclass.VNAK
        })
        h.engine.close()
    }

    @Test fun lagReplyEchoesTimestampAfterMediaBarrierAndRejectsLateOwner() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(32, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(peerIax(421, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT,
            ies = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))))
        h.transport.inject(peerControl(421, new.sourceCallNumber, 1, 1, RasvenIax2.ControlSubclass.ANSWER))
        val id = (events.single() as RasvenIax2OutgoingEvent.Connected).sessionId
        val deferred = mutableListOf<() -> Unit>()
        h.engine.openMediaChannel(id)!!.setLagReceiver(deferred::add)
        val request = peerIax(421, new.sourceCallNumber, 2, 1, RasvenIax2.IaxSubclass.LAGRQ).copy(timestamp = 0x1234L)
        h.transport.inject(request)
        h.transport.inject(request.copy(retransmission = true))
        fun replies() = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode).filter {
            it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.LAGRP
        }
        assertEquals(1, deferred.size)
        assertTrue(replies().isEmpty())
        deferred.removeAt(0).invoke()
        assertEquals(request.timestamp, replies().single().timestamp)
        h.transport.inject(request.copy(oSeqNo = 3))
        assertEquals(1, deferred.size)
        h.engine.cancelOutgoing(32)
        deferred.single().invoke()
        assertEquals(1, replies().size)
        h.engine.close()
    }

    @Test fun connectedDtmfSendsOrderedProtocolDigitsAndStopsAtTermination() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(32, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(peerIax(421, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT,
            ies = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))))
        h.transport.inject(peerControl(421, new.sourceCallNumber, 1, 1, RasvenIax2.ControlSubclass.ANSWER))
        val sessionId = (events.single() as RasvenIax2OutgoingEvent.Connected).sessionId
        assertFalse(h.engine.sendDtmf(sessionId, 'A'))
        assertFalse(h.engine.sendDtmf(sessionId + 1, '3'))
        "305*#".forEach { assertTrue(h.engine.sendDtmf(sessionId, it)) }
        val frames = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .filter { it.frameType == RasvenIax2.FrameType.DTMF }
        assertEquals("305*#", frames.map { it.subclass.toChar() }.joinToString(""))
        assertTrue(frames.all { it.destinationCallNumber == 421 })
        assertTrue(h.engine.cancelOutgoing(32))
        assertFalse(h.engine.sendDtmf(sessionId, '9'))
        h.engine.close()
    }

    @Test fun connectedHoldAndResumeUseReliableControlFramesAndRejectStaleSession() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(33, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(peerIax(422, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT,
            ies = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))))
        h.transport.inject(peerControl(422, new.sourceCallNumber, 1, 1, RasvenIax2.ControlSubclass.ANSWER))
        val sessionId = (events.single() as RasvenIax2OutgoingEvent.Connected).sessionId
        assertTrue(h.engine.setHeld(sessionId, true))
        assertTrue(h.engine.setHeld(sessionId, true))
        assertFalse(h.engine.setHeld(sessionId + 1, true))
        assertTrue(h.engine.setHeld(sessionId, false))
        val controls = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .filter { it.frameType == RasvenIax2.FrameType.CONTROL }
        assertEquals(listOf(RasvenIax2.ControlSubclass.HOLD, RasvenIax2.ControlSubclass.UNHOLD),
            controls.map { it.subclass })
        assertTrue(controls.all { it.destinationCallNumber == 422 })
        h.engine.close()
    }

    @Test fun localCancelSendsHangupAtMostOnceAndReleasesSession() {
        val h = harness(); register(h)
        h.engine.startOutgoing(40, "305", media) { }
        val new = lastNew(h)
        h.transport.inject(peerIax(430, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT,
            ies = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))))
        assertTrue(h.engine.cancelOutgoing(40))
        assertFalse(h.engine.cancelOutgoing(40))
        val hangup = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.HANGUP }
        assertEquals(1, h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .count { it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.HANGUP })
        assertEquals(0, h.engine.activeSessionCountForTest())
        assertEquals(1, h.engine.protocolSessionCountForTest())
        h.transport.inject(peerIax(430, new.sourceCallNumber, 1, (hangup.oSeqNo + 1) and 0xff, RasvenIax2.IaxSubclass.ACK))
        assertEquals(0, h.engine.protocolSessionCountForTest())
    }

    @Test fun latePacketForOldCallCannotAttachToFreshRetrySession() {
        val h = harness(); register(h)
        val firstEvents = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(50, "111", media, firstEvents::add)
        val firstNew = lastNew(h)
        h.transport.inject(peerIax(440, firstNew.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.REJECT,
            ies = listOf(RasvenIax2FrameCodec.u8Ie(RasvenIax2.IeType.CAUSECODE, 21))))
        val secondEvents = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(51, "111", media, secondEvents::add)
        val secondNew = lastNew(h)
        assertNotEquals(firstNew.sourceCallNumber, secondNew.sourceCallNumber)
        h.transport.inject(peerControl(440, firstNew.sourceCallNumber, 1, 1, RasvenIax2.ControlSubclass.BUSY, retransmission = true))
        assertTrue(secondEvents.isEmpty())
        assertEquals(1, h.engine.activeSessionCountForTest())
    }

    @Test fun cancelBeforeRemoteCallNumberRetiresSessionInsteadOfImmediateReuse() {
        val h = harness(); register(h)
        h.engine.startOutgoing(55, "305", media) { }
        assertTrue(h.engine.cancelOutgoing(55))
        assertEquals(0, h.engine.activeSessionCountForTest())
        assertEquals(1, h.engine.protocolSessionCountForTest())
        assertTrue(h.scheduler.runFirst(16_000))
        assertEquals(0, h.engine.protocolSessionCountForTest())
    }

    @Test fun acceptWithoutRequiredFormatFailsConservatively() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(56, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(peerIax(445, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT))
        assertEquals(listOf(RasvenIax2OutgoingEvent.OtherFailure), events)
    }

    @Test fun outOfOrderTimestampIsIgnoredAndRequestsResynchronization() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(57, "305", media, events::add)
        val new = lastNew(h)
        h.transport.inject(
            RasvenIax2FullFrame(
                sourceCallNumber = 446, destinationCallNumber = new.sourceCallNumber, retransmission = false,
                timestamp = 100, oSeqNo = 0, iSeqNo = 1, frameType = RasvenIax2.FrameType.IAX,
                subclass = RasvenIax2.IaxSubclass.ACCEPT,
                informationElements = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4)),
            ),
        )
        val before = h.transport.sent.size
        h.transport.inject(
            RasvenIax2FullFrame(
                sourceCallNumber = 446, destinationCallNumber = new.sourceCallNumber, retransmission = false,
                timestamp = 50, oSeqNo = 1, iSeqNo = 1, frameType = RasvenIax2.FrameType.CONTROL,
                subclass = RasvenIax2.ControlSubclass.BUSY,
            ),
        )
        assertTrue(events.isEmpty())
        val additional = h.transport.sent.drop(before).mapNotNull(RasvenIax2FrameCodec::decode)
        assertTrue(additional.any { it.frameType == RasvenIax2.FrameType.IAX && it.subclass == RasvenIax2.IaxSubclass.VNAK })
        h.transport.inject(
            RasvenIax2FullFrame(
                sourceCallNumber = 446, destinationCallNumber = new.sourceCallNumber, retransmission = false,
                timestamp = 101, oSeqNo = 1, iSeqNo = 1, frameType = RasvenIax2.FrameType.CONTROL,
                subclass = RasvenIax2.ControlSubclass.BUSY,
            ),
        )
        assertEquals(listOf(RasvenIax2OutgoingEvent.Busy), events)
    }

    @Test fun reliableNewRetransmitsBoundedlyAndAckProgressCancelsFurtherRetry() {
        val h = harness(); register(h)
        h.engine.startOutgoing(60, "305", media) { }
        val new = lastNew(h)
        assertTrue(h.scheduler.runFirst(500))
        val retrans = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.NEW }
        assertTrue(retrans.retransmission)
        h.transport.inject(peerIax(450, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.ACCEPT,
            ies = listOf(RasvenIax2FrameCodec.u32Ie(RasvenIax2.IeType.FORMAT, 4))))
        assertFalse(h.scheduler.hasActive(1000))
    }


    @Test fun incomingVnakRetransmitsOutstandingHigherSequenceWithoutNewStateTransition() {
        val h = harness(); register(h)
        h.engine.startOutgoing(62, "305", media) { }
        val new = lastNew(h)
        h.transport.inject(
            peerIax(
                451, new.sourceCallNumber, 0, 1, RasvenIax2.IaxSubclass.AUTHREQ,
                ies = listOf(
                    RasvenIax2FrameCodec.u16Ie(RasvenIax2.IeType.AUTHMETHODS, RasvenIax2.AuthMethod.MD5),
                    RasvenIax2FrameCodec.stringIe(RasvenIax2.IeType.CHALLENGE, "call-challenge"),
                ),
            ),
        )
        val authRep = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
            .last { it.subclass == RasvenIax2.IaxSubclass.AUTHREP }
        assertEquals(1, authRep.oSeqNo)
        val before = h.transport.sent.size
        h.transport.inject(peerIax(451, new.sourceCallNumber, 1, 0, RasvenIax2.IaxSubclass.VNAK))
        val additional = h.transport.sent.drop(before).mapNotNull(RasvenIax2FrameCodec::decode)
        assertTrue(additional.any { it.subclass == RasvenIax2.IaxSubclass.AUTHREP && it.retransmission })
    }

    @Test fun retransmissionExhaustionBecomesNetworkFailureAndCleansResources() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(61, "305", media, events::add)
        assertTrue(h.scheduler.runFirst(500))
        assertTrue(h.scheduler.runFirst(1000))
        assertTrue(h.scheduler.runFirst(2000))
        assertTrue(h.scheduler.runFirst(4000))
        assertTrue(h.scheduler.runFirst(8000))
        assertEquals(listOf(RasvenIax2OutgoingEvent.NetworkFailure), events)
        assertEquals(0, h.engine.activeSessionCountForTest())
    }

    @Test fun transportFailureMapsToNetworkFailureWithoutRawExceptionSurface() {
        val h = harness(); register(h)
        val events = mutableListOf<RasvenIax2OutgoingEvent>()
        h.engine.startOutgoing(70, "305", media, events::add)
        h.transport.fail()
        assertEquals(listOf(RasvenIax2OutgoingEvent.NetworkFailure), events)
        assertFalse(h.engine.isRegistered)
    }

    private fun lastNew(h: Harness): RasvenIax2FullFrame = h.transport.sent.mapNotNull(RasvenIax2FrameCodec::decode)
        .last { it.frameType == 6 && it.subclass == RasvenIax2.IaxSubclass.NEW }

    private fun peerIax(
        remote: Int,
        local: Int,
        oSeq: Int,
        iSeq: Int,
        subclass: Int,
        ies: List<RasvenIax2InformationElement> = emptyList(),
        retransmission: Boolean = false,
    ) = RasvenIax2FullFrame(
        sourceCallNumber = remote,
        destinationCallNumber = local,
        retransmission = retransmission,
        timestamp = oSeq.toLong() + 1,
        oSeqNo = oSeq,
        iSeqNo = iSeq,
        frameType = RasvenIax2.FrameType.IAX,
        subclass = subclass,
        informationElements = ies,
    )

    private fun peerControl(
        remote: Int,
        local: Int,
        oSeq: Int,
        iSeq: Int,
        subclass: Int,
        retransmission: Boolean = false,
    ) = RasvenIax2FullFrame(
        sourceCallNumber = remote,
        destinationCallNumber = local,
        retransmission = retransmission,
        timestamp = oSeq.toLong() + 1,
        oSeqNo = oSeq,
        iSeqNo = iSeq,
        frameType = RasvenIax2.FrameType.CONTROL,
        subclass = subclass,
    )
}
