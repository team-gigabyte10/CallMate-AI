package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.ActionType
import com.example.domain.model.ExecutionMode
import com.example.domain.model.TaskStatus
import java.util.UUID

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val contactId: String? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val title: String,
    val actionType: ActionType,
    val messageBody: String? = null,
    val scheduledAt: Long,
    val timezone: String = java.util.TimeZone.getDefault().id,
    val executionMode: ExecutionMode = ExecutionMode.ASSISTED,
    val status: TaskStatus = TaskStatus.SCHEDULED,
    val recurrenceRule: String? = null,
    val requiresConfirmation: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val nickname: String? = null,
    val isFavorite: Boolean = false,
    val avatarColor: Long = 0xFF6366F1,
    val notes: String? = null,
    val lastContacted: Long? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    val recipientPhone: String,
    val recipientName: String? = null,
    val body: String,
    val actionType: ActionType,
    val scheduledAt: Long,
    val sentAt: Long? = null,
    val deliveryStatus: String = "PENDING"
)

@Entity(tableName = "recurrences")
data class RecurrenceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val frequency: String, // DAILY, WEEKLY, MONTHLY
    val interval: Int = 1,
    val daysOfWeek: String? = null,
    val nextOccurrence: Long = 0L
)

@Entity(tableName = "executions")
data class ExecutionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: ActionType,
    val executionMode: ExecutionMode,
    val resultOutcome: String,
    val details: String? = null
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "USER" or "AI"
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val proposedTaskId: String? = null,
    val languageDetected: String? = "EN"
)
