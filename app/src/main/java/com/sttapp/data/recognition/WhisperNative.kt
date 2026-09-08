package com.sttapp.data.recognition

/**
 * JNI bridge to the bundled whisper.cpp native library.
 *
 * The native `libwhisper.so` is NOT part of this scaffold — it must be built
 * for each ABI (see `whisper/README.md`). [load] is called lazily from the
 * transcriber so the app launches fine even when the library is missing.
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
