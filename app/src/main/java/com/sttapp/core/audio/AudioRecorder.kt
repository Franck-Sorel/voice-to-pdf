package com.sttapp.core.audio

import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class RecordingResult(
    val audioFile: File,
    val durationMs: Long,
)

interface AudioRecorder {

    val isRecording: StateFlow<Boolean>

    /** Starts recording microphone input into [outputFile]. */
    fun start(outputFile: File)

    /** Stops recording and returns the resulting audio file. */
    fun stop(): RecordingResult
}
