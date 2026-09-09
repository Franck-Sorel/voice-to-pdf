package com.sttapp.data.recognition

/**
 * JNI bridge to the bundled whisper.cpp native library — the on-device STT
 * runtime (ADR-015). whisper.cpp + GGML `base` q8_0 (~78 MB) fits a ~100 MB
 * APK while keeping base-level accuracy, which is why it beats sherpa-onnx's
 * ONNX exports (base int8 ~152 MB) for this project's release-size + broad-
 * device fit (see docs/DECISIONS.md).
 *
 * `libwhisper.so` is NOT part of this scaffold — it must be built per
 * `ml/README.md` (CMake/NDK) and the model bundled via `ml/download-models.sh`.
 * [load] is called lazily from the transcriber so the app still launches even
 * when the library is missing.
 *
 * JNI symbol names must match the C++ side in `app/src/main/cpp/jni.cpp`.
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

    /** Loads a GGML model file; returns an opaque context handle (0 on failure). */
    external fun whisperInit(modelPath: String): Long

    /**
     * Runs inference over 16 kHz mono PCM and returns the joined transcript.
     * Blocking CPU call — must run off the main thread.
     */
    external fun whisperTranscribe(
        context: Long,
        pcm16kHzMono: ShortArray,
        sampleRateHz: Int,
        threads: Int,
        language: String,
    ): String

    external fun whisperRelease(context: Long)
}
