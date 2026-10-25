package com.rasven.android.iax2

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** Call-attempt-owned local ringback. A stale queued start cannot resurrect a finished call. */
internal class RasvenOutgoingRingback {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var owner: Long? = null
    private var tone: ToneGenerator? = null
    private var toneOwner: Long? = null

    fun start(attemptId: Long) {
        synchronized(lock) { owner = attemptId }
        main.post {
            synchronized(lock) {
                if (owner != attemptId) return@post
                if (toneOwner != attemptId) releaseTone()
                if (tone != null) return@post
                var candidate: ToneGenerator? = null
                try {
                    candidate = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
                    if (candidate.startTone(ToneGenerator.TONE_SUP_RINGTONE)) {
                        tone = candidate
                        toneOwner = attemptId
                    }
                    else candidate.release()
                } catch (_: RuntimeException) {
                    candidate?.release()
                    // A tone failure must not invent a connected call or crash signaling.
                }
            }
        }
    }

    fun stop(attemptId: Long) {
        synchronized(lock) { if (owner == attemptId) owner = null }
        main.post { releaseIfStopped() }
    }

    fun stopAll() {
        synchronized(lock) { owner = null }
        main.post { releaseIfStopped() }
    }

    private fun releaseIfStopped() {
        synchronized(lock) {
            if (owner == toneOwner && owner != null) return
            releaseTone()
        }
    }

    private fun releaseTone() {
        tone?.let {
            try { it.stopTone() } catch (_: RuntimeException) { }
            try { it.release() } catch (_: RuntimeException) { }
        }
        tone = null
        toneOwner = null
    }
}
