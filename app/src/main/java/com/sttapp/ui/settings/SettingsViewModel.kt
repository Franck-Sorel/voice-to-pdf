package com.sttapp.ui.settings

import androidx.lifecycle.ViewModel
import com.sttapp.core.model.SupportedLanguage
import com.sttapp.core.model.WhisperModel
import com.sttapp.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val model: StateFlow<WhisperModel> = settingsRepository.model
    val language: StateFlow<SupportedLanguage> = settingsRepository.language

    fun setModel(model: WhisperModel) = settingsRepository.setModel(model)

    fun setLanguage(language: SupportedLanguage) = settingsRepository.setLanguage(language)
}
