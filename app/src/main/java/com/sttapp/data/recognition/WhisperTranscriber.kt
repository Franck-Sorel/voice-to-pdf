package com.sttapp.data.recognition

import android.content.Context
import com.sttapp.core.model.SupportedLanguage
import com.sttapp.core.model.WhisperModel
import com.sttapp.core.recognition.Transcriber
import com.sttapp.core.recognition.TranscriptionProgress
import com.sttapp.core.recognition.TranscriptionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * On-device transcription via whisper.cpp over JNI (ADR-015). This is the
 * concrete `Transcriber` implementation; it can also be extracted into a
 * reusable `:recognition` module for other projects without touching the
 * interface (see ml/README.md).
 *
 * Pipeline: audio file -> PCM (16 kHz mono) -> whisper.cpp (GGML model) -> text.
 * Runs on [Dispatchers.Default] (CPU-heavy, no I/O blocking needed).
 */
@Singleton
class WhisperTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pcmDecoder: PcmDecoder,
) : Transcriber {

    override suspend fun transcribe(
        audioFile: File,
        model: WhisperModel,
        language: SupportedLanguage,
        onProgress: (TranscriptionProgress) -> Unit,
    ): TranscriptionResult = withContext(Dispatchers.Default) {
        WhisperNative.load()

        val pcm = pcmDecoder.decodeToPcm16Mono(audioFile, WHISPER_SAMPLE_RATE)

        val modelFile = resolveModelFile(model)
        val contextHandle = WhisperNative.whisperInit(modelPath = modelFile.absolutePath)

        try {
            if (contextHandle == 0L) {
                return@withContext TranscriptionResult(
                    text = "",
                    segments = emptyList(),
                    language = language,
                )
            }
            val durationMs = audioDurationMs(audioFile)
            onProgress(TranscriptionProgress(processedMs = 0L, totalMs = durationMs))
            val text = WhisperNative.whisperTranscribe(
                context = contextHandle,
                pcm16kHzMono = pcm,
                sampleRateHz = WHISPER_SAMPLE_RATE,
                threads = availableThreads(),
                language = language.isoCode,
            )
            onProgress(TranscriptionProgress(processedMs = durationMs, totalMs = durationMs))
            TranscriptionResult(text = text, segments = emptyList(), language = language)
        } finally {
            WhisperNative.whisperRelease(contextHandle)
        }
    }

    override fun cancel() {
        // TODO: expose a cancel flag to native code via whisper_abort.
    }

    /**
     * Returns the GGML model file, copying it from bundled `assets` to
     * `filesDir/models/` on first use (so transcription works 100% offline).
     * Bundle via `ml/download-models.sh`; see `ml/README.md`.
     */
    private fun resolveModelFile(model: WhisperModel): File {
        val dir = File(context.filesDir, "models")
        val target = File(dir, ggmlFileName(model))
        if (target.exists()) return target

        val asset = ggmlFileName(model)
        context.assets.open(asset).use { input ->
            dir.mkdirs()
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun ggmlFileName(model: WhisperModel): String = when (model) {
        WhisperModel.TINY -> "ggml-tiny-q8_0.bin"
        WhisperModel.BASE -> "ggml-base-q8_0.bin"
        WhisperModel.SMALL -> "ggml-small-q8_0.bin"
    }

    private fun availableThreads(): Int = min(Runtime.getRuntime().availableProcessors(), 4)

    private fun audioDurationMs(audioFile: File): Long {
        // TODO: derive from container metadata via MediaMetadataRetriever.
        return 0L
    }

    companion object {
        const val WHISPER_SAMPLE_RATE = 16_000
    }
}
