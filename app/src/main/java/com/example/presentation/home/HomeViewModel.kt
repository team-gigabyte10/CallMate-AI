package com.example.presentation.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.DefaultGeminiRepository
import com.example.ai.GeminiRepository
import com.example.ai.VoiceSpeechController
import com.example.communication.ExecutionPolicy
import com.example.data.local.CallMateDatabase
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.ConversationEntity
import com.example.data.local.entities.TaskEntity
import com.example.data.repository.CallMateRepository
import com.example.data.repository.PreferencesRepository
import com.example.domain.model.ActionType
import com.example.domain.model.AiTaskProposal
import com.example.domain.model.ExecutionMode
import com.example.domain.model.TaskStatus
import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val geminiRepository: GeminiRepository = DefaultGeminiRepository()
) : AndroidViewModel(application) {

    private val database = CallMateDatabase.getDatabase(application)
    val repository = CallMateRepository(database)
    val preferencesRepository = PreferencesRepository(application)
    val executionPolicy = ExecutionPolicy(repository)

    val todayTasks: StateFlow<List<TaskEntity>> = repository.getTodayTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingTasks: StateFlow<List<TaskEntity>> = repository.getUpcomingTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = repository.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allContacts: StateFlow<List<ContactEntity>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isLoadingAi = MutableStateFlow(false)
    val isLoadingAi: StateFlow<Boolean> = _isLoadingAi.asStateFlow()

    private val _aiProposal = MutableStateFlow<AiTaskProposal?>(null)
    val aiProposal: StateFlow<AiTaskProposal?> = _aiProposal.asStateFlow()

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    var voiceController: VoiceSpeechController? = null

    init {
        initVoice()
    }

    private fun initVoice() {
        voiceController = VoiceSpeechController(
            context = getApplication(),
            onResult = { text ->
                _commandInput.value = text
                processCommand(text)
            },
            onListeningStateChanged = { listening ->
                _isListening.value = listening
            },
            onError = { err ->
                _statusMessage.value = err
            }
        )
    }

    fun onCommandInputChange(text: String) {
        _commandInput.value = text
    }

    fun toggleVoiceListening() {
        if (_isListening.value) {
            voiceController?.stopListening()
        } else {
            val lang = preferencesRepository.language.value
            voiceController?.startListening(lang)
        }
    }

    fun processCommand(input: String? = null) {
        val text = (input ?: _commandInput.value).trim()
        if (text.isBlank()) return

        _commandInput.value = ""
        _isLoadingAi.value = true

        viewModelScope.launch {
            try {
                // Save user message to chat history
                val userMsg = ConversationEntity(
                    sender = "USER",
                    message = text
                )
                repository.insertConversation(userMsg)

                // Parse command using Gemini AI Repository (with local TaskParser fallback)
                val contactsSnapshot = repository.getAllContactsSnapshot()
                val proposal = geminiRepository.parseCommand(text, contactsSnapshot)

                val replyText = if (proposal.missingFields.isNotEmpty() && proposal.clarificationQuestion != null) {
                    proposal.clarificationQuestion
                } else {
                    "I understood your request: ${proposal.title} at ${com.example.core.util.TimeUtils.formatDateTime(proposal.scheduledTimeMillis)}. Please review the proposal below."
                }

                val aiMsg = ConversationEntity(
                    sender = "AI",
                    message = replyText,
                    proposedTaskId = proposal.intent
                )
                repository.insertConversation(aiMsg)

                if (preferencesRepository.voiceFeedback.value) {
                    voiceController?.speak(replyText)
                }

                // Surface proposal for user confirmation
                _aiProposal.value = proposal
            } catch (e: Exception) {
                _statusMessage.value = "AI parsing error: ${e.message}"
            } finally {
                _isLoadingAi.value = false
            }
        }
    }

    fun confirmProposal(proposal: AiTaskProposal) {
        if (proposal.actionType != ActionType.REMINDER && proposal.contactPhone.isNullOrBlank()) {
            _statusMessage.value = "Phone number is required to schedule a ${proposal.actionType.name.lowercase()}."
            return
        }

        viewModelScope.launch {
            val task = TaskEntity(
                id = UUID.randomUUID().toString(),
                contactName = proposal.contactName,
                contactPhone = proposal.contactPhone,
                title = proposal.title,
                actionType = proposal.actionType,
                messageBody = proposal.messageBody,
                scheduledAt = proposal.scheduledTimeMillis,
                executionMode = proposal.executionMode,
                status = TaskStatus.SCHEDULED
            )

            // Save to Room
            repository.insertTask(task)

            // Schedule with AlarmManager
            AlarmScheduler.scheduleTask(
                context = getApplication(),
                taskId = task.id,
                title = task.title,
                actionType = task.actionType,
                phoneNumber = task.contactPhone,
                messageBody = task.messageBody,
                scheduledAt = task.scheduledAt
            )

            _aiProposal.value = null
            _statusMessage.value = "Scheduled successfully: ${task.title}"
        }
    }

    fun dismissProposal() {
        _aiProposal.value = null
    }

    fun executeTask(task: TaskEntity) {
        viewModelScope.launch {
            val success = executionPolicy.executeTaskAction(
                context = getApplication(),
                taskId = task.id,
                actionType = task.actionType,
                phoneNumber = task.contactPhone,
                messageBody = task.messageBody,
                mode = task.executionMode
            )
            if (success) {
                repository.updateTaskStatus(task.id, TaskStatus.COMPLETED)
            } else {
                _statusMessage.value = "Failed to launch action for ${task.title}"
            }
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            repository.updateTaskStatus(taskId, TaskStatus.COMPLETED)
            AlarmScheduler.cancelTask(getApplication(), taskId)
            _statusMessage.value = "Task marked as completed"
        }
    }

    fun snoozeTask(taskId: String) {
        viewModelScope.launch {
            val additionalMinutes = 10
            val newTime = System.currentTimeMillis() + (additionalMinutes * 60 * 1000L)
            repository.snoozeTask(taskId, additionalMinutes)
            val task = repository.getTaskById(taskId)
            task?.let {
                AlarmScheduler.scheduleTask(
                    context = getApplication(),
                    taskId = it.id,
                    title = it.title,
                    actionType = it.actionType,
                    phoneNumber = it.contactPhone,
                    messageBody = it.messageBody,
                    scheduledAt = newTime
                )
            }
            _statusMessage.value = "Snoozed for 10 minutes"
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancelTask(getApplication(), task.id)
            repository.deleteTask(task)
            _statusMessage.value = "Task deleted"
        }
    }

    fun createQuickTask(actionType: ActionType) {
        val proposal = AiTaskProposal(
            intent = "QUICK_CREATE_${actionType.name}",
            actionType = actionType,
            title = when (actionType) {
                ActionType.CALL -> "Call Contact"
                ActionType.SMS -> "Send SMS"
                ActionType.WHATSAPP -> "WhatsApp Message"
                ActionType.REMINDER -> "Personal Reminder"
            },
            executionMode = if (actionType == ActionType.REMINDER) ExecutionMode.REMINDER else ExecutionMode.ASSISTED,
            scheduledTimeMillis = System.currentTimeMillis() + 3600000L,
            requiresConfirmation = true
        )
        _aiProposal.value = proposal
    }

    override fun onCleared() {
        super.onCleared()
        voiceController?.release()
    }
}
