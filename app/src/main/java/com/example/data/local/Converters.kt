package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.model.ActionType
import com.example.domain.model.ExecutionMode
import com.example.domain.model.TaskStatus

class Converters {
    @TypeConverter
    fun fromActionType(value: ActionType): String = value.name

    @TypeConverter
    fun toActionType(value: String): ActionType = try {
        ActionType.valueOf(value)
    } catch (_: Exception) {
        ActionType.REMINDER
    }

    @TypeConverter
    fun fromExecutionMode(value: ExecutionMode): String = value.name

    @TypeConverter
    fun toExecutionMode(value: String): ExecutionMode = try {
        ExecutionMode.valueOf(value)
    } catch (_: Exception) {
        ExecutionMode.ASSISTED
    }

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String = value.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = try {
        TaskStatus.valueOf(value)
    } catch (_: Exception) {
        TaskStatus.SCHEDULED
    }
}
