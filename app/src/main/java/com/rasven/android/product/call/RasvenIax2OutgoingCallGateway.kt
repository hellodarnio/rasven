package com.rasven.android.product.call

import android.os.Handler
import android.os.Looper
import com.rasven.android.iax2.RasvenIax2OutgoingEvent
import com.rasven.android.iax2.RasvenIax2Runtime
import java.util.concurrent.atomic.AtomicBoolean

/** Product-to-IAX2 adapter. Raw protocol values never cross this boundary. */
internal object RasvenIax2OutgoingCallGateway : RasvenOutgoingCallGateway {
    private val main = Handler(Looper.getMainLooper())

    override fun start(
        attempt: RasvenOutgoingCallAttempt,
        onEvent: (RasvenOutgoingTransportEvent) -> Unit,
    ): RasvenOutgoingStartDisposition {
        val iaxStarted = AtomicBoolean(false)
        val added = RasvenCallDomain.addOutgoingPlatformCall(
            attempt.id,
            attempt.destination,
            attempt.displayIdentity,
            onReady = { ready ->
            if (!ready) {
                if (iaxStarted.compareAndSet(false, true)) main.post { onEvent(RasvenOutgoingTransportEvent.NetworkFailure) }
                return@addOutgoingPlatformCall
            }
            if (!iaxStarted.compareAndSet(false, true)) return@addOutgoingPlatformCall
            val result = RasvenIax2Runtime.startOutgoing(attempt.id, attempt.destination,
                preparePlatformAudio = { sessionId, ready -> main.post {
                    RasvenCallDomain.prepareOutgoingPlatformAudio(attempt.id, sessionId, ready)
                } }) { event ->
                main.post {
                    when (event) {
                        RasvenIax2OutgoingEvent.Ringing -> Unit
                        is RasvenIax2OutgoingEvent.Connected -> RasvenCallDomain.activateOutgoingPlatformCall(attempt.id, event.sessionId)
                        is RasvenIax2OutgoingEvent.PeerHoldChanged -> Unit
                        RasvenIax2OutgoingEvent.Busy,
                        RasvenIax2OutgoingEvent.Rejected,
                        RasvenIax2OutgoingEvent.NoAnswer,
                        RasvenIax2OutgoingEvent.NetworkFailure,
                        RasvenIax2OutgoingEvent.RemoteHangup,
                        RasvenIax2OutgoingEvent.OtherFailure -> RasvenCallDomain.finishOutgoingPlatformCall(attempt.id)
                    }
                    onEvent(
                        when (event) {
                            RasvenIax2OutgoingEvent.Ringing -> RasvenOutgoingTransportEvent.Ringing
                            RasvenIax2OutgoingEvent.Busy -> RasvenOutgoingTransportEvent.Busy
                            RasvenIax2OutgoingEvent.Rejected -> RasvenOutgoingTransportEvent.Rejected
                            RasvenIax2OutgoingEvent.NoAnswer -> RasvenOutgoingTransportEvent.NoAnswer
                            RasvenIax2OutgoingEvent.NetworkFailure -> RasvenOutgoingTransportEvent.NetworkFailure
                            is RasvenIax2OutgoingEvent.Connected ->
                                RasvenOutgoingTransportEvent.Connected(RasvenOutgoingConnectedSession(event.sessionId))
                            is RasvenIax2OutgoingEvent.PeerHoldChanged -> if (event.held) RasvenOutgoingTransportEvent.PeerHeld else RasvenOutgoingTransportEvent.PeerResumed
                            RasvenIax2OutgoingEvent.RemoteHangup -> RasvenOutgoingTransportEvent.RemoteHangup
                            RasvenIax2OutgoingEvent.OtherFailure -> RasvenOutgoingTransportEvent.OtherFailure
                        },
                    )
                }
            }
            if (result != com.rasven.android.iax2.RasvenIax2StartResult.Accepted) {
                RasvenCallDomain.finishOutgoingPlatformCall(attempt.id, android.telecom.DisconnectCause.ERROR)
                onEvent(RasvenOutgoingTransportEvent.NetworkFailure)
            }
            },
            onCancelled = { main.post { onEvent(RasvenOutgoingTransportEvent.OtherFailure) } },
        )
        return if (added) RasvenOutgoingStartDisposition.Accepted else RasvenOutgoingStartDisposition.TransportUnavailable
    }

    override fun cancel(attemptId: Long) {
        RasvenCallDomain.cancelOutgoingPlatformCall(attemptId)
    }
}
