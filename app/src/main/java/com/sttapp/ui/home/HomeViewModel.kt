package com.sttapp.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sttapp.core.audio.AudioRecorder
import com.sttapp.core.model.Session
import com.sttapp.core.model.SessionStatus
import com.sttapp.core.recognition.Transcriber
import com.sttapp.data.SessionRepository
import com.sttapp.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class HomeUiState(
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val progress: Float = 0f,
    val lastSessionId: Long? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val transcriber: Transcriber,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            audioRecorder.isRecording.collect { recording ->
                _uiState.update { it.copy(isRecording = recording) }
            }
        }
    }

    fun toggleRecording() {
        if (audioRecorder.isRecording.value) {
            stopAndTranscribe()
        } else {
            startRecording()
        }
    }

    fun importAudio(uri: Uri) {
        // MVP 1 F1 (import): copy the picked document into app storage, then
        // transcribe it like an in-app recording.
        viewModelScope.launch {
            val destination = File(context.cacheDir, "import_${System.currentTimeMillis()}.m4a")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: return@launch
            transcribe(destination)
        }
    }

    private fun startRecording() {
        val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        audioRecorder.start(file)
    }

    private fun stopAndTranscribe() {
        val result = audioRecorder.stop()
        viewModelScope.launch {
            transcribe(result.audioFile)
        }
    }

    private suspend fun transcribe(audioFile: File) {
        val model = settingsRepository.model.value
        val language = settingsRepository.language.value

        _uiState.update { it.copy(isTranscribing = true, progress = 0f) }
        val sessionId = sessionRepository.insert(
            Session(
                title = "Untitled lecture",
                createdAtEpochMs = System.currentTimeMillis(),
                status = SessionStatus.TRANSCRIBING,
                audioPath = audioFile.absolutePath,
                durationMs = 0L,
                language = language,
                model = model,
            ),
        )

        try {
            val result = transcriber.transcribe(audioFile, model, language) { progress ->
                _uiState.update { it.copy(progress = progress.fraction) }
            }
            val session = sessionRepository.observeSession(sessionId).first()
            if (session != null) {
                sessionRepository.update(
                    session.copy(
                        status = SessionStatus.READY,
                        transcript = result.text,
                        title = result.text.substringBefore('\n').take(64).ifBlank { "Untitled lecture" },
                    ),
                )
            }
            _uiState.update { it.copy(isTranscribing = false, progress = 1f, lastSessionId = sessionId) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            val session = sessionRepository.observeSession(sessionId).first()
            if (session != null) {
                sessionRepository.update(session.copy(status = SessionStatus.ERROR))
            }
            _uiState.update { it.copy(isTranscribing = false, progress = 0f) }
        }
    }

    override fun onCleared() {
        transcriber.cancel()
        super.onCleared()
    }
}
