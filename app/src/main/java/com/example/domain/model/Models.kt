package com.example.domain.model

enum class ActionType {
    CALL,
    SMS,
    WHATSAPP,
    REMINDER
}

enum class ExecutionMode {
    REMINDER,   // Notify user to take action
    ASSISTED,   // Launch native dialer or messaging app pre-filled for user review
    AUTOMATIC   // Direct send (when authorized)
}

enum class TaskStatus {
    DRAFT,
    SCHEDULED,
    DUE,
    COMPLETED,
    SNOOZED,
    CANCELLED,
    MISSED,
    FAILED
}

data class AiTaskProposal(
    val intent: String,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val actionType: ActionType = ActionType.REMINDER,
    val title: String = "",
    val messageBody: String? = null,
    val scheduledTimeMillis: Long = System.currentTimeMillis() + 3600000,
    val timezone: String = java.util.TimeZone.getDefault().id,
    val recurrence: String? = null,
    val executionMode: ExecutionMode = ExecutionMode.ASSISTED,
    val missingFields: List<String> = emptyList(),
    val clarificationQuestion: String? = null,
    val requiresConfirmation: Boolean = true,
    val rawPrompt: String = ""
)
