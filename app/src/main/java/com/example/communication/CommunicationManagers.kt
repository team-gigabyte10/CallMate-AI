package com.example.communication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.repository.CallMateRepository
import com.example.domain.model.ActionType
import com.example.domain.model.ExecutionMode

object CallManager {
    fun launchDialer(context: Context, phoneNumber: String): Boolean {
        return try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch dialer: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

object SmsManager {
    fun launchSmsAssisted(context: Context, phoneNumber: String, message: String): Boolean {
        return try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$cleanNumber")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open messaging app: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

object WhatsAppManager {
    fun launchWhatsApp(context: Context, phoneNumber: String, message: String): Boolean {
        return try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
            val encodedMessage = Uri.encode(message)
            val uriString = if (cleanNumber.isNotBlank()) {
                "https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMessage"
            } else {
                "https://api.whatsapp.com/send?text=$encodedMessage"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(uriString)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                // Fallback to general browser or WhatsApp Web intent
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp not available: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

class ExecutionPolicy(private val repository: CallMateRepository) {
    suspend fun executeTaskAction(
        context: Context,
        taskId: String,
        actionType: ActionType,
        phoneNumber: String?,
        messageBody: String?,
        mode: ExecutionMode
    ): Boolean {
        var success = false
        var outcome = "UNKNOWN"

        when (actionType) {
            ActionType.CALL -> {
                if (phoneNumber.isNullOrBlank()) {
                    outcome = "FAILED_NO_PHONE_NUMBER"
                    Toast.makeText(context, "No phone number available for this call", Toast.LENGTH_SHORT).show()
                } else if (mode == ExecutionMode.AUTOMATIC) {
                    success = AiVoiceCallBridge.dispatchAiCall(
                        context = context,
                        taskId = taskId,
                        phoneNumber = phoneNumber,
                        customMessage = messageBody
                    )
                    outcome = if (success) "AI_CALL_DISPATCHED" else "AI_CALL_FAILED"
                } else {
                    success = CallManager.launchDialer(context, phoneNumber)
                    outcome = if (success) "DIALER_LAUNCHED" else "DIALER_FAILED"
                }
            }
            ActionType.SMS -> {
                if (phoneNumber.isNullOrBlank()) {
                    outcome = "FAILED_NO_PHONE_NUMBER"
                    Toast.makeText(context, "No recipient phone number for SMS", Toast.LENGTH_SHORT).show()
                } else {
                    success = SmsManager.launchSmsAssisted(context, phoneNumber, messageBody ?: "")
                    outcome = if (success) "SMS_APP_PREFILLED" else "SMS_LAUNCH_FAILED"
                }
            }
            ActionType.WHATSAPP -> {
                success = WhatsAppManager.launchWhatsApp(context, phoneNumber ?: "", messageBody ?: "")
                outcome = if (success) "WHATSAPP_LAUNCHED" else "WHATSAPP_LAUNCH_FAILED"
            }
            ActionType.REMINDER -> {
                success = true
                outcome = "REMINDER_ACKNOWLEDGED"
                Toast.makeText(context, "Reminder: $messageBody", Toast.LENGTH_LONG).show()
            }
        }

        repository.logExecution(
            taskId = taskId,
            actionType = actionType,
            executionMode = mode,
            outcome = outcome,
            details = "Target: $phoneNumber, Action: $actionType"
        )

        return success
    }
}
