package com.example.presentation.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.communication.ExecutionPolicy
import com.example.data.local.CallMateDatabase
import com.example.data.local.entities.TaskEntity
import com.example.data.repository.CallMateRepository
import com.example.domain.model.TaskStatus
import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class CalendarViewMode {
    DAY,
    WEEK
}

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CallMateDatabase.getDatabase(application)
    val repository = CallMateRepository(database)
    val executionPolicy = ExecutionPolicy(repository)

    private val _selectedDate = MutableStateFlow(Calendar.getInstance())
    val selectedDate: StateFlow<Calendar> = _selectedDate.asStateFlow()

    private val _viewMode = MutableStateFlow(CalendarViewMode.DAY)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    val dayTasks: StateFlow<List<TaskEntity>> = combine(
        repository.allTasks,
        _selectedDate
    ) { tasks, date ->
        val cal = Calendar.getInstance().apply {
            timeInMillis = date.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = cal.timeInMillis

        tasks.filter { it.scheduledAt in startOfDay until endOfDay }
            .sortedBy { it.scheduledAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyTasks: StateFlow<List<TaskEntity>> = combine(
        repository.allTasks,
        _selectedDate
    ) { tasks, date ->
        val cal = Calendar.getInstance().apply {
            timeInMillis = date.timeInMillis
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfWeek = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 7)
        val endOfWeek = cal.timeInMillis

        tasks.filter { it.scheduledAt in startOfWeek until endOfWeek }
            .sortedBy { it.scheduledAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedDate(calendar: Calendar) {
        val newCal = Calendar.getInstance().apply { timeInMillis = calendar.timeInMillis }
        _selectedDate.value = newCal
    }

    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    fun changeDay(offset: Int) {
        val newCal = Calendar.getInstance().apply {
            timeInMillis = _selectedDate.value.timeInMillis
            add(Calendar.DAY_OF_YEAR, offset)
        }
        _selectedDate.value = newCal
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
}
