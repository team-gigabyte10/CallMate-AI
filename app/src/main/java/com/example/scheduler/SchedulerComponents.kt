package com.example.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

    fun scheduleTask(
        context: Context,
        taskId: String,
        title: String,
        actionType: ActionType,
        phoneNumber: String?,
        messageBody: String?,
        scheduledAt: Long
    ) {
        if (scheduledAt <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_TASK
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_ACTION_TYPE, actionType.name)
            putExtra(EXTRA_PHONE, phoneNumber)
            putExtra(EXTRA_MESSAGE, messageBody)
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
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledAt, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledAt, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, scheduledAt, pendingIntent)
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

        val actionType = try {
            ActionType.valueOf(actionTypeName)
        } catch (_: Exception) {
            ActionType.REMINDER
        }

        // Show rich notification
        ReminderNotification.showTaskNotification(
            context = context,
            taskId = taskId,
            title = title,
            actionType = actionType,
            phoneNumber = phone,
            messageBody = message
        )

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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(taskId.hashCode(), builder.build())
    }
}
