package com.example.ai

import com.example.data.local.entities.ContactEntity
import com.example.domain.model.ActionType
import com.example.domain.model.AiTaskProposal
import com.example.domain.model.ExecutionMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TaskValidator {

    fun validateProposal(
        rawProposal: AiTaskProposal,
        contacts: List<ContactEntity>,
        scheduledDateTimeStr: String? = null
    ): AiTaskProposal {
        val missingFields = rawProposal.missingFields.toMutableList()

        // 1. Contact Validation
        var matchedContact: ContactEntity? = null
        if (!rawProposal.contactName.isNullOrBlank()) {
            val query = rawProposal.contactName.lowercase().trim()
            matchedContact = contacts.find { contact ->
                contact.name.lowercase().contains(query) ||
                (contact.nickname != null && contact.nickname.lowercase().contains(query)) ||
                query.contains(contact.name.lowercase()) ||
                (contact.nickname != null && query.contains(contact.nickname.lowercase()))
            }
        }

        val resolvedName = matchedContact?.name ?: rawProposal.contactName
        val resolvedPhone = matchedContact?.phoneNumber ?: rawProposal.contactPhone

        if (rawProposal.actionType != ActionType.REMINDER) {
            if (resolvedName.isNullOrBlank()) {
                if (!missingFields.contains("contact")) missingFields.add("contact")
            }
        }

        // 2. Schedule Validation
        val now = System.currentTimeMillis()
        var scheduledTimeMillis = rawProposal.scheduledTimeMillis

        if (!scheduledDateTimeStr.isNullOrBlank()) {
            try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone(rawProposal.timezone.ifBlank { TimeZone.getDefault().id })
                }
                val parsedDate = format.parse(scheduledDateTimeStr)
                if (parsedDate != null) {
                    scheduledTimeMillis = parsedDate.time
                }
            } catch (_: Exception) {
                // Keep default or fallback
            }
        }

        // Ensure time is in the future
        if (scheduledTimeMillis <= now) {
            // If in the past or same minute, push to 1 hour from now
            scheduledTimeMillis = now + 3600000L
        }

        // 3. Execution Mode Safety
        val safeExecutionMode = when (rawProposal.actionType) {
            ActionType.CALL -> ExecutionMode.REMINDER
            ActionType.SMS -> ExecutionMode.ASSISTED
            ActionType.WHATSAPP -> ExecutionMode.ASSISTED
            ActionType.REMINDER -> ExecutionMode.REMINDER
        }

        // 4. Clarification Question Generation if needed
        val clarification = when {
            rawProposal.clarificationQuestion != null -> rawProposal.clarificationQuestion
            missingFields.contains("contact") ->
                "Who would you like to ${rawProposal.actionType.name.lowercase()}? Please specify a contact or phone number."
            missingFields.contains("time") ->
                "When would you like to schedule this? (e.g., today at 5 PM, tomorrow 2 PM)"
            else -> null
        }

        val cleanTitle = if (rawProposal.title.isNotBlank()) {
            rawProposal.title
        } else {
            when (rawProposal.actionType) {
                ActionType.CALL -> "Call ${resolvedName ?: "Contact"}"
                ActionType.SMS -> "SMS ${resolvedName ?: "Contact"}"
                ActionType.WHATSAPP -> "WhatsApp ${resolvedName ?: "Contact"}"
                ActionType.REMINDER -> "Reminder"
            }
        }

        return rawProposal.copy(
            contactName = resolvedName,
            contactPhone = resolvedPhone,
            title = cleanTitle,
            scheduledTimeMillis = scheduledTimeMillis,
            executionMode = safeExecutionMode,
            missingFields = missingFields,
            clarificationQuestion = clarification,
            requiresConfirmation = true
        )
    }
}

