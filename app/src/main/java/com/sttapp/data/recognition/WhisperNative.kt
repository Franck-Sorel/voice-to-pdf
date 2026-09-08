package com.sttapp.data.recognition

/**
 * @deprecated JNI bridge to whisper.cpp — superseded by sherpa-onnx (ADR-009).
 *
 * sherpa-onnx is ~50× faster than whisper.cpp for the same Whisper model on
 * Android, so the real STT implementation uses the sherpa-onnx Android AAR
 * (`OfflineRecognizer` etc.) instead of this custom JNI bridge. See
 * `ml/README.md`. Kept only as a placeholder so the scaffold's `Transcriber`
 * wiring compiles; remove when `SherpaOnnxTranscriber` lands (ROADMAP M1).
 *
 * JNI symbol names must match these functions exactly, e.g.
 * `Java_com_sttapp_data_recognition_WhisperNative_whisperInit`.
 */
object WhisperNative {

    @Volatile
    private var loaded = false

    fun load() {
        if (!loaded) {
            System.loadLibrary("whisper")
            loaded = true
        }
    }

    /** Loads a model file and returns an opaque context handle. */
    external fun whisperInit(modelPath: String, language: String, threads: Int): Long

    /** Runs inference over 16 kHz mono PCM; returns joined transcript text. */
    external fun whisperTranscribe(context: Long, pcm16kHzMono: ShortArray, sampleRateHz: Int): String

    external fun whisperRelease(context: Long)
}
