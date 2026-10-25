package com.rasven.android.iax2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor

internal class RasvenAndroidAudioIoFactory(
    private val context: Context,
    private val telecomOwnsAudio: Boolean = false,
) : RasvenAudioIoFactory {
    private val appContext = context.applicationContext

    override fun createInput(format: RasvenAudioMediaFormat): RasvenAudioInput? {
        // Recheck at capture construction, including revocation after the UI permission gate.
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return null
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val min = AudioRecord.getMinBufferSize(format.sampleRateHz, channelConfig, encoding)
        if (min <= 0) return null
        val bufferBytes = maxOf(min, format.pcmBytesPerFrame * 4)
        return try {
            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(format.sampleRateHz)
                        .setEncoding(encoding)
                        .setChannelMask(channelConfig)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                null
            } else {
                RasvenAndroidAudioInput(record)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun createOutput(format: RasvenAudioMediaFormat): RasvenAudioOutput? {
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val min = AudioTrack.getMinBufferSize(format.sampleRateHz, channelConfig, encoding)
        if (min <= 0) return null
        val bufferBytes = maxOf(min, format.pcmBytesPerFrame * 4)
        return try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(format.sampleRateHz)
                        .setEncoding(encoding)
                        .setChannelMask(channelConfig)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                null
            } else {
                RasvenAndroidAudioOutput(track)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun createEnvironment(): RasvenAudioSessionEnvironment = if (telecomOwnsAudio) {
        // Core-Telecom owns focus/mode/endpoints; a second focus request competes with our call.
        object : RasvenAudioSessionEnvironment {
            override fun activate(onFocusLost: () -> Unit): Boolean = true
            override fun close() = Unit
        }
    } else RasvenAndroidAudioEnvironment(appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
}

private class RasvenAndroidAudioInput(
    private val record: AudioRecord,
) : RasvenAudioInput {
    private var started = false
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    override fun start(): Boolean = try {
        if (started) return true
        // Platform effects are optional: an unavailable/broken effect must never block real media startup.
        aec = createOptionalEffect(
            available = AcousticEchoCanceler.isAvailable(),
            create = { AcousticEchoCanceler.create(record.audioSessionId) },
        )
        ns = createOptionalEffect(
            available = NoiseSuppressor.isAvailable(),
            create = { NoiseSuppressor.create(record.audioSessionId) },
        )
        agc = createOptionalEffect(
            available = AutomaticGainControl.isAvailable(),
            create = { AutomaticGainControl.create(record.audioSessionId) },
        )
        record.startRecording()
        started = record.recordingState == AudioRecord.RECORDSTATE_RECORDING
        started
    } catch (_: Exception) {
        false
    }

    private fun <T : android.media.audiofx.AudioEffect> createOptionalEffect(
        available: Boolean,
        create: () -> T?,
    ): T? {
        if (!available) return null
        return try { create()?.also { it.enabled = true } } catch (_: Exception) { null }
    }

    override fun read(buffer: ShortArray, offset: Int, sampleCount: Int): Int =
        record.read(buffer, offset, sampleCount, AudioRecord.READ_BLOCKING)

    override fun stop() {
        if (!started) return
        try { record.stop() } catch (_: Exception) { }
        started = false
    }

    override fun close() {
        stop()
        try { aec?.release() } catch (_: Exception) { }
        try { ns?.release() } catch (_: Exception) { }
        try { agc?.release() } catch (_: Exception) { }
        aec = null; ns = null; agc = null
        record.release()
    }
}

private class RasvenAndroidAudioOutput(
    private val track: AudioTrack,
) : RasvenAudioOutput {
    private var started = false

    override fun start(): Boolean = try {
        if (started) return true
        track.play()
        started = track.playState == AudioTrack.PLAYSTATE_PLAYING
        started
    } catch (_: Exception) {
        false
    }

    override fun write(buffer: ShortArray, offset: Int, sampleCount: Int): Int {
        var writtenTotal = 0
        while (writtenTotal < sampleCount) {
            val written = track.write(buffer, offset + writtenTotal, sampleCount - writtenTotal, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) return if (writtenTotal > 0) writtenTotal else written
            writtenTotal += written
        }
        return writtenTotal
    }

    override fun stop() {
        if (!started) return
        try { track.pause() } catch (_: Exception) { }
        try { track.flush() } catch (_: Exception) { }
        try { track.stop() } catch (_: Exception) { }
        started = false
    }

    override fun close() {
        stop()
        track.release()
    }
}

private class RasvenAndroidAudioEnvironment(
    private val audioManager: AudioManager,
) : RasvenAudioSessionEnvironment {
    private var previousMode: Int? = null
    private var focusRequest: AudioFocusRequest? = null
    private var active = false

    override fun activate(onFocusLost: () -> Unit): Boolean {
        if (active) return true
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                onFocusLost()
            }
        }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        previousMode = audioManager.mode
        return try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            focusRequest = request
            active = true
            true
        } catch (_: Exception) {
            audioManager.abandonAudioFocusRequest(request)
            previousMode = null
            false
        }
    }

    override fun close() {
        val request = focusRequest
        if (request != null) {
            try { audioManager.abandonAudioFocusRequest(request) } catch (_: Exception) { }
        }
        val old = previousMode
        if (Build.VERSION.SDK_INT >= 31) {
            try { audioManager.clearCommunicationDevice() } catch (_: Exception) { }
        } else {
            @Suppress("DEPRECATION")
            try { audioManager.isSpeakerphoneOn = false } catch (_: Exception) { }
        }
        if (old != null) {
            try { audioManager.mode = old } catch (_: Exception) { }
        }
        focusRequest = null
        previousMode = null
        active = false
    }
}
