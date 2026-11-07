package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("callmate_preferences", Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(prefs.getString("pref_language", "EN") ?: "EN")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString("pref_theme", "SYSTEM") ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _voiceFeedback = MutableStateFlow(prefs.getBoolean("pref_voice_feedback", true))
    val voiceFeedback: StateFlow<Boolean> = _voiceFeedback.asStateFlow()

    private val _defaultExecutionMode = MutableStateFlow(
        prefs.getString("pref_default_mode", "ASSISTED") ?: "ASSISTED"
    )
    val defaultExecutionMode: StateFlow<String> = _defaultExecutionMode.asStateFlow()

    private val _aiModel = MutableStateFlow(prefs.getString("pref_ai_model", "Gemini 2.5 Flash") ?: "Gemini 2.5 Flash")
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _autonomousCallEnabled = MutableStateFlow(prefs.getBoolean("pref_auto_call_enabled", true))
    val autonomousCallEnabled: StateFlow<Boolean> = _autonomousCallEnabled.asStateFlow()

    private val _autonomousCallGreeting = MutableStateFlow(
        prefs.getString(
            "pref_auto_call_greeting",
            "Hello, this is CallMate AI calling on behalf of the user who is currently in a meeting."
        ) ?: "Hello, this is CallMate AI calling on behalf of the user who is currently in a meeting."
    )
    val autonomousCallGreeting: StateFlow<String> = _autonomousCallGreeting.asStateFlow()

    fun setLanguage(lang: String) {
        prefs.edit().putString("pref_language", lang).apply()
        _language.value = lang
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("pref_theme", mode).apply()
        _themeMode.value = mode
    }

    fun setVoiceFeedback(enabled: Boolean) {
        prefs.edit().putBoolean("pref_voice_feedback", enabled).apply()
        _voiceFeedback.value = enabled
    }

    fun setDefaultExecutionMode(mode: String) {
        prefs.edit().putString("pref_default_mode", mode).apply()
        _defaultExecutionMode.value = mode
    }

    fun setAiModel(model: String) {
        prefs.edit().putString("pref_ai_model", model).apply()
        _aiModel.value = model
    }

    fun setAutonomousCallEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pref_auto_call_enabled", enabled).apply()
        _autonomousCallEnabled.value = enabled
    }

    fun setAutonomousCallGreeting(greeting: String) {
        prefs.edit().putString("pref_auto_call_greeting", greeting).apply()
        _autonomousCallGreeting.value = greeting
    }
}
