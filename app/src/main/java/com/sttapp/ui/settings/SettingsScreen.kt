package com.sttapp.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sttapp.core.model.SupportedLanguage
import com.sttapp.core.model.WhisperModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val model by viewModel.model.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Settings", style = MaterialTheme.typography.titleLarge)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Whisper model",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            WhisperModel.entries.forEach { candidate ->
                RadioRow(
                    label = candidateLabel(candidate),
                    selected = candidate == model,
                    onClick = { viewModel.setModel(candidate) },
                )
            }

            Text(
                text = "Transcript language",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            SupportedLanguage.entries.forEach { candidate ->
                RadioRow(
                    label = candidate.displayName,
                    selected = candidate == language,
                    onClick = { viewModel.setLanguage(candidate) },
                )
            }

            Text(
                text = "All processing happens on this device. No internet required.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun candidateLabel(model: WhisperModel): String = when (model) {
    WhisperModel.TINY -> "Tiny — fastest, ~39 MB, less accurate"
    WhisperModel.BASE -> "Base — balanced (default), ~74 MB"
    WhisperModel.SMALL -> "Small — best accuracy, ~244 MB, slow"
}
