package com.sttapp.core.recognition

import com.sttapp.core.model.SupportedLanguage
import com.sttapp.core.model.WhisperModel
import java.io.File

data class TranscriptionProgress(
    val processedMs: Long,
    val totalMs: Long,
) {
    val fraction: Float
        get() = if (totalMs <= 0L) 0f else (processedMs.toFloat() / totalMs).coerceIn(0f, 1f)
}

data class TranscriptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class TranscriptionResult(
    val text: String,
    val segments: List<TranscriptionSegment>,
    val language: SupportedLanguage,
)

interface Transcriber {

    /**
     * Transcribes [audioFile] using the given [model] and [language] on-device.
     *
     * Runs as a blocking CPU-heavy call; implementations must hop to a
     * background dispatcher. Progress is reported via [onProgress].
     */
    suspend fun transcribe(
        audioFile: File,
        model: WhisperModel,
        language: SupportedLanguage,
        onProgress: (TranscriptionProgress) -> Unit,
    ): TranscriptionResult

    fun cancel()
}
