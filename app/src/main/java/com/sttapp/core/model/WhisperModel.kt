package com.sttapp.core.model

enum class SessionStatus {
    TRANSCRIBING,
    READY,
    ERROR,
}

/**
 * Whisper model sizes exposed to the user.
 *
 * @property approxSizeMb approximate on-device model size (whisper.cpp GGML files)
 * @property nominalRealtimeFactor processing minutes needed per audio minute (rough guide)
 */
enum class WhisperModel(
    val id: String,
    val approxSizeMb: Int,
    val nominalRealtimeFactor: Float,
) {
    TINY("tiny", 39, 0.8f),
    BASE("base", 74, 1.5f),
    SMALL("small", 244, 4.0f),
}
