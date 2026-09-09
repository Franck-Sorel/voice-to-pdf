package com.sttapp.ui.session

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sttapp.core.model.Session
import com.sttapp.core.pdf.PdfDestination
import com.sttapp.core.pdf.PdfExportRequest
import com.sttapp.core.pdf.PdfExporter
import com.sttapp.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val pdfExporter: PdfExporter,
) : ViewModel() {

    private val sessionId = MutableStateFlow<Long?>(null)

    val session: StateFlow<Session?> = sessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else sessionRepository.observeSession(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun setSessionId(id: Long) {
        sessionId.value = id
    }

    fun saveTranscript(text: String) {
        val current = session.value ?: return
        viewModelScope.launch {
            sessionRepository.update(current.copy(transcript = text))
        }
    }

    fun deleteSession(onDeleted: () -> Unit) {
        val current = session.value ?: return
        viewModelScope.launch {
            sessionRepository.delete(current.id)
            onDeleted()
        }
    }

    fun exportPdf(destination: Uri) {
        val current = session.value ?: return
        viewModelScope.launch {
            runCatching {
                val dateLabel = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(Date(current.createdAtEpochMs))
                pdfExporter.export(
                    PdfExportRequest(
                        title = current.title,
                        dateLabel = dateLabel,
                        bodyText = current.transcript.orEmpty(),
                    ),
                    PdfDestination.ContentUri(destination.toString()),
                )
            }
        }
    }
}
