package com.sttapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sttapp.core.model.Session
import com.sttapp.core.model.SessionStatus
import com.sttapp.core.model.SupportedLanguage
import com.sttapp.core.model.WhisperModel

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAtEpochMs: Long,
    val status: String,
    val transcript: String? = null,
    val audioPath: String? = null,
    val durationMs: Long = 0L,
    val language: String,
    val model: String,
)

fun SessionEntity.toDomain(): Session = Session(
    id = id,
    title = title,
    createdAtEpochMs = createdAtEpochMs,
    status = SessionStatus.valueOf(status),
    transcript = transcript,
    audioPath = audioPath,
    durationMs = durationMs,
    language = SupportedLanguage.valueOf(language),
    model = WhisperModel.valueOf(model),
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    title = title,
    createdAtEpochMs = createdAtEpochMs,
    status = status.name,
    transcript = transcript,
    audioPath = audioPath,
    durationMs = durationMs,
    language = language.name,
    model = model.name,
)
