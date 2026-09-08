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
 * Placeholder on-device transcription via the (superseded) whisper.cpp JNI
 * stub. Replaced in ROADMAP M1 by a sherpa-onnx implementation
 * (`SherpaOnnxTranscriber`) that implements the same [Transcriber] interface
 * — see `ml/README.md` and ADR-009.
 *
 * Pipeline: audio file -> PCM (16 kHz mono) -> STT runtime -> text.
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
        val contextHandle = WhisperNative.whisperInit(
            modelPath = modelFile.absolutePath,
            language = language.isoCode,
            threads = availableThreads(),
        )

        try {
            val durationMs = audioDurationMs(audioFile)
            onProgress(TranscriptionProgress(processedMs = 0L, totalMs = durationMs))
            val text = WhisperNative.whisperTranscribe(contextHandle, pcm, WHISPER_SAMPLE_RATE)
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
     * Model files live in `filesDir/models/<id>.onnx`. For MVP 1 they are
     * bundled into assets at build time and copied here on first launch;
     * see `ml/README.md` for the download-and-bundle step.
     */
    private fun resolveModelFile(model: WhisperModel): File {
        val dir = File(context.filesDir, "models")
        val target = File(dir, "${model.id}.onnx")
        return target.also {
            check(it.exists()) {
                "Model file not found: ${it.absolutePath}. " +
                    "Bundle it per ml/README.md before transcription."
            }
        }
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
