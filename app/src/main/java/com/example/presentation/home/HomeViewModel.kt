package com.example.presentation.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.TaskParser
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

class HomeViewModel(application: Application) : AndroidViewModel(application) {

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

        viewModelScope.launch {
            // Save user message to chat
            val userMsg = ConversationEntity(
                sender = "USER",
                message = text
            )
            repository.insertConversation(userMsg)

            // Parse command using multilingual AI TaskParser
            val contactsSnapshot = repository.getAllContactsSnapshot()
            val proposal = TaskParser.parseCommand(text, contactsSnapshot)

            val replyText = if (proposal.missingFields.isNotEmpty() && proposal.clarificationQuestion != null) {
                proposal.clarificationQuestion
            } else {
                "I understood your request: ${proposal.title} at ${com.example.core.util.TimeUtils.formatDateTime(proposal.scheduledTimeMillis)}. Please review the proposed details below."
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
        }
    }

    fun confirmProposal(proposal: AiTaskProposal) {
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
            }
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            repository.updateTaskStatus(taskId, TaskStatus.COMPLETED)
            AlarmScheduler.cancelTask(getApplication(), taskId)
        }
    }

    fun snoozeTask(taskId: String) {
        viewModelScope.launch {
            repository.snoozeTask(taskId, 10)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
            AlarmScheduler.cancelTask(getApplication(), taskId = task.id)
        }
    }

    fun createQuickTask(type: ActionType) {
        val now = System.currentTimeMillis() + 3600000L
        val proposal = AiTaskProposal(
            intent = "QUICK_TASK",
            actionType = type,
            title = when (type) {
                ActionType.CALL -> "Quick Call"
                ActionType.SMS -> "Quick SMS"
                ActionType.WHATSAPP -> "Quick WhatsApp"
                ActionType.REMINDER -> "Quick Reminder"
            },
            scheduledTimeMillis = now
        )
        _aiProposal.value = proposal
    }

    override fun onCleared() {
        super.onCleared()
        voiceController?.release()
    }
}
