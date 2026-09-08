package com.sttapp.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(
    onOpenSessions: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importAudio(uri)
    }
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.toggleRecording()
    }
    val hasMicPermission = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
    val startRecording: () -> Unit = {
        if (hasMicPermission) {
            viewModel.toggleRecording()
        } else {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenSessions) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Sessions")
                }
                Text(text = "Voice to PDF", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                state.isTranscribing -> {
                    Text(
                        text = "Transcribing…",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "This can take as long as the recording itself. " +
                            "Keep the app open.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }

                state.isRecording -> {
                    Text(
                        text = "Recording…",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = viewModel::toggleRecording,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text("Stop & transcribe")
                    }
                }

                else -> {
                    Button(
                        onClick = startRecording,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Text("Start recording")
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("audio/mpeg", "audio/wav", "audio/ogg", "audio/mp4"),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Import audio file")
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "100% offline. Runs on your device.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun checkSelfPermission(permission: String): Boolean {
    val context = LocalContext.current
    return ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED
}
