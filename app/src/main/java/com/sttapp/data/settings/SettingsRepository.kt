package com.sttapp.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.sttapp.core.model.SupportedLanguage
import com.sttapp.core.model.WhisperModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User preferences (model size, transcript language). Backed by
 * SharedPreferences; exposed as [StateFlow]s so the UI stays reactive.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _model = MutableStateFlow(loadModel())
    val model: StateFlow<WhisperModel> = _model.asStateFlow()

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<SupportedLanguage> = _language.asStateFlow()

    fun setModel(model: WhisperModel) {
        prefs.edit().putString(KEY_MODEL, model.name).apply()
        _model.value = model
    }

    fun setLanguage(language: SupportedLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
        _language.value = language
    }

    private fun loadModel(): WhisperModel =
        prefs.getString(KEY_MODEL, null)?.let { runCatching { WhisperModel.valueOf(it) }.getOrNull() }
            ?: WhisperModel.BASE

    private fun loadLanguage(): SupportedLanguage =
        prefs.getString(KEY_LANGUAGE, null)?.let { runCatching { SupportedLanguage.valueOf(it) }.getOrNull() }
            ?: SupportedLanguage.ENGLISH

    private companion object {
        const val PREFS_NAME = "voicetopdf_settings"
        const val KEY_MODEL = "whisper_model"
        const val KEY_LANGUAGE = "language"
    }
}
