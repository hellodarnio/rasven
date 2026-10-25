package com.rasven.android.iax2

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.LinkedHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal interface RasvenIax2PacketTransport : AutoCloseable {
    fun start(
        onReady: () -> Unit,
        onPacket: (ByteArray) -> Unit,
        onFailure: () -> Unit,
    )

    /** Returns true only when the packet was accepted by the owned sender queue. */
    fun send(packet: ByteArray): Boolean
    val isReady: Boolean
}

/**
 * One UDP association per active account. DNS/socket setup, receive and send all stay off Main.
 * The sender queue is deliberately bounded so network failure cannot create unbounded memory use.
 */
internal class RasvenIax2UdpTransport(
    private val host: String,
    private val port: Int,
    private val diagnostic: ((String) -> Unit)? = null,
) : RasvenIax2PacketTransport {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private val failureNotified = AtomicBoolean(false)
    private val receiveExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "rasven-iax2-recv").apply { isDaemon = true }
    }
    private val sendExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(64),
        { runnable -> Thread(runnable, "rasven-iax2-send").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var failureCallback: (() -> Unit)? = null

    private val endpointLock = Any()
    private val peerByRemoteCall = LinkedHashMap<Int, InetSocketAddress>()
    private val peerByLocalCall = LinkedHashMap<Int, InetSocketAddress>()
    @Volatile private var allowedPeerAddresses: Set<InetAddress> = emptySet()
    @Volatile private var defaultPeer: InetSocketAddress? = null

    override val isReady: Boolean get() = ready.get() && !closed.get()

    override fun start(onReady: () -> Unit, onPacket: (ByteArray) -> Unit, onFailure: () -> Unit) {
        if (!started.compareAndSet(false, true)) return
        failureCallback = onFailure
        receiveExecutor.execute {
            try {
                val addresses = InetAddress.getAllByName(host).toSet()
                val address = addresses.firstOrNull() ?: throw IllegalStateException("No server address resolved")
                allowedPeerAddresses = addresses
                defaultPeer = InetSocketAddress(address, port)
                val ownedSocket = DatagramSocket(null).apply {
                    reuseAddress = false
                    soTimeout = 1000
                    bind(InetSocketAddress(0))
                }
                if (closed.get()) {
                    ownedSocket.close()
                    return@execute
                }
                socket = ownedSocket
                ready.set(true)
                diagnostic?.invoke("UDP_READY")
                onReady()
                val buffer = ByteArray(RasvenIax2.MAX_UDP_PAYLOAD)
                while (!closed.get()) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        ownedSocket.receive(packet)
                        if (packet.length > 0) {
                            if (packet.address !in allowedPeerAddresses) {
                                diagnostic?.invoke("UDP_DROP_UNTRUSTED_SOURCE")
                                continue
                            }
                            val source = InetSocketAddress(packet.address, packet.port)
                            val bytes = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                            rememberPeerEndpoints(bytes, source)
                            if (source.port != port) diagnostic?.invoke("UDP_RX_ALTERNATE_SOURCE_PORT")
                            traceHeader("RX", bytes)
                            onPacket(bytes)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Periodic close check; no busy polling.
                    }
                }
            } catch (failure: Exception) {
                diagnostic?.invoke("UDP_FAILURE type=${failure.javaClass.simpleName}")
                failure.stackTrace.take(8).forEach { diagnostic?.invoke("UDP_FAILURE_AT $it") }
                if (!closed.get()) notifyFailure()
            } finally {
                ready.set(false)
                // A final ACK can be queued by onPacket immediately before its release callback
                // closes this owner. Drain only already-accepted teardown frames off Main.
                if (closed.get()) {
                    try {
                        if (!sendExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) sendExecutor.shutdownNow()
                    } catch (_: InterruptedException) {
                        sendExecutor.shutdownNow()
                        Thread.currentThread().interrupt()
                    }
                }
                socket?.close()
                socket = null
                synchronized(endpointLock) {
                    peerByRemoteCall.clear()
                    peerByLocalCall.clear()
                }
            }
        }
    }

    override fun send(packet: ByteArray): Boolean {
        if (packet.isEmpty() || packet.size > RasvenIax2.MAX_UDP_PAYLOAD || !isReady) return false
        val copy = packet.copyOf()
        return try {
            sendExecutor.execute {
                try {
                    if (closed.get() && !isFinalControlPacket(copy)) return@execute
                    val owned = socket ?: return@execute
                    val target = targetFor(copy) ?: return@execute
                    owned.send(DatagramPacket(copy, copy.size, target))
                    traceHeader("TX", copy)
                } catch (failure: Exception) {
                    diagnostic?.invoke("UDP_SEND_FAILURE type=${failure.javaClass.simpleName}")
                    if (!closed.get()) notifyFailure()
                }
            }
            true
        } catch (_: RejectedExecutionException) {
            false
        }
    }

    private fun isFinalControlPacket(packet: ByteArray): Boolean =
        packet.size >= 12 && packet[0].toInt() and 0x80 != 0 &&
            packet[10].toInt() and 0xff == RasvenIax2.FrameType.IAX &&
            packet[11].toInt() and 0xff in setOf(
                RasvenIax2.IaxSubclass.ACK, RasvenIax2.IaxSubclass.HANGUP, RasvenIax2.IaxSubclass.REJECT,
            )

    private fun notifyFailure() {
        ready.set(false)
        socket?.close()
        if (failureNotified.compareAndSet(false, true)) failureCallback?.invoke()
    }

    /**
     * IAX servers may send call legs from a source port different from the registration port
     * (for example, when a PBX port is translated by NAT). Keep the configured server address
     * as the trust boundary, but remember the observed endpoint per IAX call leg so replies use
     * the exact address/port that sent that leg.
     */
    private fun rememberPeerEndpoints(packet: ByteArray, source: InetSocketAddress) {
        if (packet.size < 2) return
        val first = packet[0].toInt() and 0xff
        val second = packet[1].toInt() and 0xff
        val callNumber = ((first and 0x7f) shl 8) or second
        if (callNumber == 0) return
        synchronized(endpointLock) {
            if (first and 0x80 != 0 && packet.size >= 4) {
                val destination = (((packet[2].toInt() and 0x7f) shl 8) or (packet[3].toInt() and 0xff))
                peerByRemoteCall[callNumber] = source
                if (destination != 0) peerByLocalCall[destination] = source
            } else if (first and 0x80 == 0) {
                peerByRemoteCall[callNumber] = source
            }
            trimEndpoints(peerByRemoteCall)
            trimEndpoints(peerByLocalCall)
        }
    }

    private fun targetFor(packet: ByteArray): InetSocketAddress? {
        if (packet.size < 2) return defaultPeer
        val first = packet[0].toInt() and 0xff
        val second = packet[1].toInt() and 0xff
        val targetCall = if (first and 0x80 != 0 && packet.size >= 4) {
            ((packet[2].toInt() and 0x7f) shl 8) or (packet[3].toInt() and 0xff)
        } else if (first and 0x80 == 0) {
            ((first and 0x7f) shl 8) or second
        } else {
            0
        }
        if (targetCall == 0) return defaultPeer
        return synchronized(endpointLock) {
            if (first and 0x80 != 0) peerByRemoteCall[targetCall] ?: defaultPeer
            else peerByLocalCall[targetCall] ?: defaultPeer
        }
    }

    private fun trimEndpoints(endpoints: LinkedHashMap<Int, InetSocketAddress>) {
        while (endpoints.size > MAX_REMEMBERED_PEERS) {
            endpoints.remove(endpoints.keys.first())
        }
    }

    /** Header metadata only: never inspect or log credentials, auth IEs, DTMF or audio. */
    private fun traceHeader(direction: String, packet: ByteArray) {
        if (diagnostic == null || packet.size < 12) return
        val byte = { index: Int -> packet[index].toInt() and 255 }
        if (byte(0) and 128 == 0 || byte(10) !in setOf(RasvenIax2.FrameType.IAX, RasvenIax2.FrameType.CONTROL)) return
        val source = ((byte(0) and 127) shl 8) or byte(1)
        val destination = ((byte(2) and 127) shl 8) or byte(3)
        diagnostic.invoke("$direction type=${byte(10)} subclass=${byte(11)} source=$source destination=$destination oseq=${byte(8)} iseq=${byte(9)} retransmit=${byte(2) and 128 != 0}")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ready.set(false)
        // Receiver exits after its bounded receive timeout; its finalizer closes the socket only
        // after queued final ACK/HANGUP/REJECT frames finish. Queued audio and setup are discarded.
        sendExecutor.shutdown()
        receiveExecutor.shutdown()
    }

    private companion object {
        const val MAX_REMEMBERED_PEERS = 256
    }
}
