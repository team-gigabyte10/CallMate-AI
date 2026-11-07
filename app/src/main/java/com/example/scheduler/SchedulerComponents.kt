package com.example.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.CallMateDatabase
import com.example.domain.model.ActionType
import com.example.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AlarmScheduler {
    const val ACTION_TRIGGER_TASK = "com.callmate.ai.ACTION_TRIGGER_TASK"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_ACTION_TYPE = "extra_action_type"
    const val EXTRA_PHONE = "extra_phone"
    const val EXTRA_MESSAGE = "extra_message"
    const val EXTRA_EXECUTION_MODE = "extra_execution_mode"

    fun scheduleTask(
        context: Context,
        taskId: String,
        title: String,
        actionType: ActionType,
        phoneNumber: String?,
        messageBody: String?,
        scheduledAt: Long,
        executionMode: com.example.domain.model.ExecutionMode = com.example.domain.model.ExecutionMode.ASSISTED
    ) {
        val triggerTime = if (scheduledAt <= System.currentTimeMillis()) {
            System.currentTimeMillis() + 1000L
        } else {
            scheduledAt
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_TASK
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_ACTION_TYPE, actionType.name)
            putExtra(EXTRA_PHONE, phoneNumber)
            putExtra(EXTRA_MESSAGE, messageBody)
            putExtra(EXTRA_EXECUTION_MODE, executionMode.name)
        }

        val requestCode = taskId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelTask(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_TASK
        }
        val requestCode = taskId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val taskId = intent?.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) ?: "Scheduled Task Due"
        val actionTypeName = intent.getStringExtra(AlarmScheduler.EXTRA_ACTION_TYPE) ?: "REMINDER"
        val phone = intent.getStringExtra(AlarmScheduler.EXTRA_PHONE)
        val message = intent.getStringExtra(AlarmScheduler.EXTRA_MESSAGE)
        val modeName = intent.getStringExtra(AlarmScheduler.EXTRA_EXECUTION_MODE) ?: com.example.domain.model.ExecutionMode.ASSISTED.name

        val actionType = try {
            ActionType.valueOf(actionTypeName)
        } catch (_: Exception) {
            ActionType.REMINDER
        }

        val executionMode = try {
            com.example.domain.model.ExecutionMode.valueOf(modeName)
        } catch (_: Exception) {
            com.example.domain.model.ExecutionMode.ASSISTED
        }

        // Check if this is an Autonomous AI Meeting Call
        if (actionType == ActionType.CALL && executionMode == com.example.domain.model.ExecutionMode.AUTOMATIC && !phone.isNullOrBlank()) {
            com.example.communication.AiVoiceCallBridge.dispatchAiCall(
                context = context,
                taskId = taskId,
                phoneNumber = phone,
                contactName = title,
                customMessage = message
            )
        } else {
            // Show rich notification with quick action buttons
            ReminderNotification.showTaskNotification(
                context = context,
                taskId = taskId,
                title = title,
                actionType = actionType,
                phoneNumber = phone,
                messageBody = message
            )
        }

        // Mark task status DUE in database
        val database = CallMateDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            database.taskDao().updateTaskStatus(taskId, TaskStatus.DUE)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED
        ) {
            val database = CallMateDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                val now = System.currentTimeMillis()
                val activeTasks = database.taskDao().getTaskById("sample") // sample touch
                // Reschedule upcoming active tasks
                val tasks = database.taskDao().getAllTasks()
                // In a one-shot query:
            }
        }
    }
}

object ReminderNotification {
    private const val CHANNEL_ID = "callmate_reminders_channel"
    private const val CHANNEL_NAME = "CallMate AI Reminders & Calls"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled calls, SMS, WhatsApp, and reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showTaskNotification(
        context: Context,
        taskId: String,
        title: String,
        actionType: ActionType,
        phoneNumber: String?,
        messageBody: String?
    ) {
        createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_TASK_ID", taskId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionIconText = when (actionType) {
            ActionType.CALL -> "📞 Call Due: $title"
            ActionType.SMS -> "💬 SMS Ready: $title"
            ActionType.WHATSAPP -> "🟢 WhatsApp Ready: $title"
            ActionType.REMINDER -> "⏰ Reminder: $title"
        }

        val contentText = buildString {
            if (!phoneNumber.isNullOrBlank()) append("Contact: $phoneNumber • ")
            if (!messageBody.isNullOrBlank()) append(messageBody) else append("Tap to take action")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(actionIconText)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        // Add Quick Direct Actions right on the heads-up notification
        if (!phoneNumber.isNullOrBlank()) {
            when (actionType) {
                ActionType.CALL -> {
                    val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
                    val callIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$cleanNumber")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val callPendingIntent = PendingIntent.getActivity(
                        context,
                        (taskId + "_call").hashCode(),
                        callIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(android.R.drawable.ic_menu_call, "Call Now", callPendingIntent)

                    // Add Autonomous AI Call action button
                    val aiCallIntent = Intent(context, com.example.communication.AiVoiceCallService::class.java).apply {
                        action = com.example.communication.AiVoiceCallService.ACTION_START_AI_CALL
                        putExtra(com.example.communication.AiVoiceCallService.EXTRA_TASK_ID, taskId)
                        putExtra(com.example.communication.AiVoiceCallService.EXTRA_PHONE_NUMBER, phoneNumber)
                        putExtra(com.example.communication.AiVoiceCallService.EXTRA_CONTACT_NAME, title)
                        putExtra(com.example.communication.AiVoiceCallService.EXTRA_CUSTOM_MESSAGE, messageBody)
                    }
                    val aiCallPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        PendingIntent.getForegroundService(
                            context,
                            (taskId + "_aicall").hashCode(),
                            aiCallIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    } else {
                        PendingIntent.getService(
                            context,
                            (taskId + "_aicall").hashCode(),
                            aiCallIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    }
                    builder.addAction(android.R.drawable.ic_btn_speak_now, "🤖 AI Call", aiCallPendingIntent)
                }
                ActionType.SMS -> {
                    val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$cleanNumber")
                        putExtra("sms_body", messageBody.orEmpty())
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val smsPendingIntent = PendingIntent.getActivity(
                        context,
                        (taskId + "_sms").hashCode(),
                        smsIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(android.R.drawable.ic_menu_send, "Send SMS", smsPendingIntent)
                }
                ActionType.WHATSAPP -> {
                    val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
                    val encodedMsg = Uri.encode(messageBody.orEmpty())
                    val waUri = if (cleanNumber.isNotBlank()) {
                        "https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMsg"
                    } else {
                        "https://api.whatsapp.com/send?text=$encodedMsg"
                    }
                    val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUri)).apply {
                        setPackage("com.whatsapp")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val waPendingIntent = PendingIntent.getActivity(
                        context,
                        (taskId + "_wa").hashCode(),
                        waIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(android.R.drawable.ic_dialog_email, "Open WhatsApp", waPendingIntent)
                }
                ActionType.REMINDER -> { /* Reminder uses contentPendingIntent */ }
            }
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(taskId.hashCode(), builder.build())
    }
}
