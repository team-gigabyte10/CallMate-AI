package com.example.presentation.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.communication.ExecutionPolicy
import com.example.data.local.CallMateDatabase
import com.example.data.local.entities.TaskEntity
import com.example.data.repository.CallMateRepository
import com.example.domain.model.ActionType
import com.example.domain.model.ExecutionMode
import com.example.domain.model.TaskStatus
import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class ScheduleTab {
    ALL,
    UPCOMING,
    COMPLETED,
    MISSED,
    RECURRING
}

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CallMateDatabase.getDatabase(application)
    val repository = CallMateRepository(database)
    val executionPolicy = ExecutionPolicy(repository)

    private val _selectedTab = MutableStateFlow(ScheduleTab.ALL)
    val selectedTab: StateFlow<ScheduleTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedActionType = MutableStateFlow<ActionType?>(null)
    val selectedActionType: StateFlow<ActionType?> = _selectedActionType.asStateFlow()

    val filteredTasks: StateFlow<List<TaskEntity>> = combine(
        repository.allTasks,
        _selectedTab,
        _searchQuery,
        _selectedActionType
    ) { tasks, tab, query, actionFilter ->
        tasks.filter { task ->
            // Tab filter
            val tabMatch = when (tab) {
                ScheduleTab.ALL -> true
                ScheduleTab.UPCOMING -> task.status == TaskStatus.SCHEDULED || task.status == TaskStatus.DUE || task.status == TaskStatus.SNOOZED
                ScheduleTab.COMPLETED -> task.status == TaskStatus.COMPLETED
                ScheduleTab.MISSED -> task.status == TaskStatus.MISSED
                ScheduleTab.RECURRING -> task.recurrenceRule != null
            }

            // Search query filter
            val queryMatch = if (query.isBlank()) true else {
                task.title.contains(query, ignoreCase = true) ||
                        (task.contactName?.contains(query, ignoreCase = true) == true) ||
                        (task.messageBody?.contains(query, ignoreCase = true) == true)
            }

            // Action type filter
            val actionMatch = actionFilter == null || task.actionType == actionFilter

            tabMatch && queryMatch && actionMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: ScheduleTab) {
        _selectedTab.value = tab
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun selectActionTypeFilter(type: ActionType?) {
        _selectedActionType.value = type
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
            AlarmScheduler.cancelTask(getApplication(), task.id)
        }
    }

    fun createTask(
        title: String,
        actionType: ActionType,
        contactName: String?,
        contactPhone: String?,
        messageBody: String?,
        scheduledAt: Long,
        recurrenceRule: String? = null
    ) {
        if (actionType != ActionType.REMINDER && contactPhone.isNullOrBlank()) {
            return
        }

        viewModelScope.launch {
            val task = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                actionType = actionType,
                contactName = contactName?.ifBlank { null },
                contactPhone = contactPhone?.ifBlank { null },
                messageBody = messageBody?.ifBlank { null },
                scheduledAt = scheduledAt,
                executionMode = ExecutionMode.ASSISTED,
                status = TaskStatus.SCHEDULED,
                recurrenceRule = recurrenceRule
            )
            repository.insertTask(task)
            AlarmScheduler.scheduleTask(
                context = getApplication(),
                taskId = task.id,
                title = task.title,
                actionType = task.actionType,
                phoneNumber = task.contactPhone,
                messageBody = task.messageBody,
                scheduledAt = task.scheduledAt
            )
        }
    }
}
