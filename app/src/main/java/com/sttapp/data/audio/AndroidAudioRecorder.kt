package com.sttapp.data.audio

import android.media.MediaRecorder
import android.os.SystemClock
import com.sttapp.core.audio.AudioRecorder
import com.sttapp.core.audio.RecordingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Microphone recorder built on [MediaRecorder]. Output is AAC-in-MP4, which
 * MediaCodec can decode to PCM for Whisper.
 */
@Singleton
class AndroidAudioRecorder @Inject constructor() : AudioRecorder {

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtElapsedMs: Long = 0L

    override fun start(outputFile: File) {
        stopInternal()

        @Suppress("DEPRECATION")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        this.outputFile = outputFile
        startedAtElapsedMs = SystemClock.elapsedRealtime()
        _isRecording.value = true
    }

    override fun stop(): RecordingResult {
        val durationMs = SystemClock.elapsedRealtime() - startedAtElapsedMs
        val file = outputFile
        stopInternal()
        return RecordingResult(
            audioFile = file ?: error("stop() called before start()"),
            durationMs = durationMs,
        )
    }

    private fun stopInternal() {
        val current = recorder
        if (current == null) return

        runCatching { current.stop() }
        current.release()
        recorder = null
        _isRecording.value = false
    }
}
