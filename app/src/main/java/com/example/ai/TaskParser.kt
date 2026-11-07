package com.example.ai

import com.example.data.local.entities.ContactEntity
import com.example.domain.model.ActionType
import com.example.domain.model.AiTaskProposal
import com.example.domain.model.ExecutionMode
import java.util.Calendar
import java.util.regex.Pattern

object TaskParser {

    fun parseCommand(input: String, contacts: List<ContactEntity>): AiTaskProposal {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 1. Detect Action Type
        val actionType = detectActionType(lower)

        // 2. Identify Contact
        val matchedContact = findMatchingContact(lower, contacts)

        // 3. Extract Time
        val (scheduledTime, detectedTimeStr) = parseTime(lower)

        // 4. Extract Message Body (if SMS or WhatsApp)
        val messageBody = extractMessageBody(trimmed, actionType)

        // 5. Construct Clean Title
        val title = generateTitle(actionType, matchedContact?.name ?: extractUnknownName(lower), messageBody, detectedTimeStr)

        // 6. Check for Missing Information
        val missingFields = mutableListOf<String>()
        if (actionType != ActionType.REMINDER && matchedContact == null) {
            missingFields.add("contact")
        }

        val clarification = when {
            actionType != ActionType.REMINDER && matchedContact == null ->
                "Who would you like to ${actionType.name.lowercase()}? (Please specify a contact or phone number)"
            else -> null
        }

        return AiTaskProposal(
            intent = "${actionType.name}_TASK",
            contactName = matchedContact?.name ?: matchedContact?.nickname,
            contactPhone = matchedContact?.phoneNumber,
            actionType = actionType,
            title = title,
            messageBody = messageBody,
            scheduledTimeMillis = scheduledTime,
            executionMode = if (actionType == ActionType.REMINDER) ExecutionMode.REMINDER else ExecutionMode.ASSISTED,
            missingFields = missingFields,
            clarificationQuestion = clarification,
            requiresConfirmation = true,
            rawPrompt = input
        )
    }

    private fun detectActionType(lower: String): ActionType {
        return when {
            lower.contains("whatsapp") || lower.contains("wa ") || lower.contains("হোয়াটসঅ্যাপ") || lower.contains("হোয়াটসঅ্যাপ") ->
                ActionType.WHATSAPP
            lower.contains("sms") || lower.contains("text ") || lower.contains("message") || lower.contains("মেসেজ") || lower.contains("টেক্সট") || lower.contains("msg") ->
                ActionType.SMS
            lower.contains("call") || lower.contains("dial") || lower.contains("ফোন") || lower.contains("কল") || lower.contains("ring") || lower.contains("phone") ->
                ActionType.CALL
            lower.contains("remind") || lower.contains("রিমাইন্ডার") || lower.contains("মনে করিয়ে") || lower.contains("মনে করিয়ে") ->
                ActionType.REMINDER
            else -> ActionType.REMINDER
        }
    }

    private fun findMatchingContact(lower: String, contacts: List<ContactEntity>): ContactEntity? {
        // Direct name match or nickname match
        for (contact in contacts) {
            val nameLower = contact.name.lowercase()
            val nickLower = contact.nickname?.lowercase()

            if (lower.contains(nameLower)) return contact
            if (nickLower != null && nickLower.isNotBlank() && lower.contains(nickLower)) return contact

            // Check first name or token match
            val firstName = nameLower.split(" ").firstOrNull()
            if (firstName != null && firstName.length >= 3 && lower.contains(firstName)) {
                return contact
            }
        }

        // Check common Bengali/Banglish relations
        for (contact in contacts) {
            val nick = contact.nickname?.lowercase() ?: ""
            if (lower.contains("ammu") || lower.contains("আম্মু") || lower.contains("mom") || lower.contains("মা")) {
                if (nick.contains("ammu") || nick.contains("mom") || contact.name.lowercase().contains("ammu")) return contact
            }
            if (lower.contains("abbu") || lower.contains("আব্বু") || lower.contains("dad") || lower.contains("বাবা")) {
                if (nick.contains("abbu") || nick.contains("dad") || contact.name.lowercase().contains("abbu")) return contact
            }
            if (lower.contains("boss") || lower.contains("বস")) {
                if (nick.contains("boss") || contact.name.lowercase().contains("boss")) return contact
            }
            if (lower.contains("bhai") || lower.contains("ভাই")) {
                if (nick.contains("bhai") || contact.name.lowercase().contains("bhai")) return contact
            }
        }

        return null
    }

    private fun extractUnknownName(lower: String): String? {
        // Regex patterns like "call <name>", "sms to <name>", "<name> k call"
        val regexPatterns = listOf(
            Regex("""(?:call|text|sms|whatsapp)\s+([A-Za-z]+)"""),
            Regex("""([A-Za-z]+)\s+(?:k|ke)\s+(?:call|sms|message)"""),
            Regex("""([A-Za-z]+)কে\s+(?:কল|মেসেজ)""")
        )
        for (pattern in regexPatterns) {
            val match = pattern.find(lower)
            if (match != null && match.groupValues.size > 1) {
                val candidate = match.groupValues[1]
                val stopWords = listOf("tomorrow", "today", "now", "later", "kal", "aj", "me", "him", "her")
                if (!stopWords.contains(candidate)) {
                    return candidate.replaceFirstChar { it.uppercase() }
                }
            }
        }
        return null
    }

    private fun parseTime(lower: String): Pair<Long, String> {
        val cal = Calendar.getInstance()
        var recognized = "in 1 hour"

        // 1. "in X minutes" or "X min por"
        val minutePattern = Pattern.compile("""in\s+(\d+)\s*(?:min|minute|minutes)|(\d+)\s*(?:min|minute|minutes|মিনিট)\s*por""")
        val minMatcher = minutePattern.matcher(lower)
        if (minMatcher.find()) {
            val mins = (minMatcher.group(1) ?: minMatcher.group(2))?.toIntOrNull() ?: 10
            cal.add(Calendar.MINUTE, mins)
            return Pair(cal.timeInMillis, "in $mins mins")
        }

        // 2. "in X hours"
        val hourPattern = Pattern.compile("""in\s+(\d+)\s*(?:hour|hours|ঘণ্টা)|(\d+)\s*(?:hour|hours|ঘন্টা)\s*por""")
        val hrMatcher = hourPattern.matcher(lower)
        if (hrMatcher.find()) {
            val hrs = (hrMatcher.group(1) ?: hrMatcher.group(2))?.toIntOrNull() ?: 1
            cal.add(Calendar.HOUR_OF_DAY, hrs)
            return Pair(cal.timeInMillis, "in $hrs hours")
        }

        // Check if tomorrow or kal or কাল
        val isTomorrow = lower.contains("tomorrow") || lower.contains("kal") || lower.contains("কাল")
        if (isTomorrow) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            recognized = "tomorrow"
        }

        // Time of day indicators
        val isEveningOrNight = lower.contains("pm") || lower.contains("rat") || lower.contains("রাত") ||
                lower.contains("shondha") || lower.contains("সন্ধ্যা") || lower.contains("night") || lower.contains("evening")
        val isNoon = lower.contains("dupur") || lower.contains("দুপুর") || lower.contains("noon")
        val isMorning = lower.contains("am") || lower.contains("shokal") || lower.contains("সকাল") || lower.contains("morning")

        // Specific hour e.g. "at 2 PM", "2:30 pm", "2 tay", "২টায়", "at 4", "10 am"
        val timeRegex = Pattern.compile("""(?:at\s+|@\s*|shokal\s+|dupur\s+|rat\s+|কাল\s+|রাত\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm|tay|টায়)?""")
        val timeMatcher = timeRegex.matcher(lower)

        var foundHour = false
        while (timeMatcher.find()) {
            val hourStr = timeMatcher.group(1)
            val minStr = timeMatcher.group(2)
            val amPm = timeMatcher.group(3)

            val parsedHour = hourStr?.toIntOrNull()
            if (parsedHour != null && parsedHour in 1..24) {
                var hour = parsedHour
                val minute = minStr?.toIntOrNull() ?: 0

                if (amPm == "pm" || isEveningOrNight || (isNoon && hour < 12)) {
                    if (hour < 12) hour += 12
                } else if (amPm == "am" || isMorning) {
                    if (hour == 12) hour = 0
                } else if (hour in 1..7 && !isMorning) {
                    // Default afternoon/evening assumption for low hour numbers unless specified AM
                    hour += 12
                }

                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)

                // If scheduled time in past today, move to tomorrow
                if (!isTomorrow && cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    recognized = "tomorrow at ${timeMatcher.group(0)}"
                } else {
                    recognized = "${if (isTomorrow) "Tomorrow at " else "Today at "}$hourStr:${if (minute < 10) "0$minute" else "$minute"}"
                }
                foundHour = true
                break
            }
        }

        if (!foundHour) {
            // Default 1 hour from now
            cal.timeInMillis = System.currentTimeMillis() + 3600000L
            recognized = "in 1 hour"
        }

        return Pair(cal.timeInMillis, recognized)
    }

    private fun extractMessageBody(input: String, actionType: ActionType): String? {
        if (actionType != ActionType.SMS && actionType != ActionType.WHATSAPP) return null

        val patterns = listOf(
            Regex("""(?:saying|with message|text|msg|bole|বলে)\s+["']?([^"']+)["']?""", RegexOption.IGNORE_CASE),
            Regex("""(?:that|eta|যে)\s+(.+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim()
            }
        }

        return "Hi, reaching out via CallMate AI."
    }

    private fun generateTitle(
        actionType: ActionType,
        contactName: String?,
        messageBody: String?,
        timeStr: String
    ): String {
        val target = contactName ?: "Contact"
        return when (actionType) {
            ActionType.CALL -> "Call with $target"
            ActionType.SMS -> "SMS to $target"
            ActionType.WHATSAPP -> "WhatsApp to $target"
            ActionType.REMINDER -> {
                if (!messageBody.isNullOrBlank() && messageBody != "Hi, reaching out via CallMate AI.") {
                    messageBody
                } else {
                    "Reminder: Follow up with $target"
                }
            }
        }
    }
}
