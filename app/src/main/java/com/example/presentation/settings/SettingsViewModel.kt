package com.example.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CallMateDatabase
import com.example.data.local.entities.ExecutionEntity
import com.example.data.repository.CallMateRepository
import com.example.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CallMateDatabase.getDatabase(application)
    val repository = CallMateRepository(database)
    val preferences = PreferencesRepository(application)

    val language = preferences.language
    val themeMode = preferences.themeMode
    val voiceFeedback = preferences.voiceFeedback
    val defaultExecutionMode = preferences.defaultExecutionMode
    val aiModel = preferences.aiModel
    val autonomousCallEnabled = preferences.autonomousCallEnabled
    val autonomousCallGreeting = preferences.autonomousCallGreeting

    val executions: StateFlow<List<ExecutionEntity>> = repository.allExecutions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setLanguage(lang: String) {
        preferences.setLanguage(lang)
    }

    fun setTheme(theme: String) {
        preferences.setThemeMode(theme)
    }

    fun setVoiceFeedback(enabled: Boolean) {
        preferences.setVoiceFeedback(enabled)
    }

    fun setDefaultExecutionMode(mode: String) {
        preferences.setDefaultExecutionMode(mode)
    }

    fun setAiModel(model: String) {
        preferences.setAiModel(model)
    }

    fun setAutonomousCallEnabled(enabled: Boolean) {
        preferences.setAutonomousCallEnabled(enabled)
    }

    fun setAutonomousCallGreeting(greeting: String) {
        preferences.setAutonomousCallGreeting(greeting)
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearConversations()
            _message.value = "AI chat history cleared"
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _message.value = "All tasks, contacts, and chat data cleared"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
