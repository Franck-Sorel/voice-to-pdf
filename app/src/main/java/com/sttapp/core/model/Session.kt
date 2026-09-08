package com.sttapp.core.model

/**
 * A single capture session: one recording (optional, user opt-in) plus its
 * transcript. Stored locally in Room.
 */
data class Session(
    val id: Long = 0L,
    val title: String,
    val createdAtEpochMs: Long,
    val status: SessionStatus,
    val transcript: String? = null,
    val audioPath: String? = null,
    val durationMs: Long = 0L,
    val language: SupportedLanguage = SupportedLanguage.ENGLISH,
    val model: WhisperModel = WhisperModel.BASE,
)
