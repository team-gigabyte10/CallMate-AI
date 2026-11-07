package com.example.communication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat

object AiVoiceCallBridge {

    /**
     * Checks whether all necessary on-device permissions for Architecture C
     * (Direct SIM cellular calling + Audio management + Mic recognition) are granted.
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        val hasCallPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val hasRecordAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        return hasCallPhone && hasRecordAudio && hasPhoneState
    }

    /**
     * Dispatches an autonomous AI voice call using on-device SIM + Acoustic Speakerphone Bridge.
     * If permissions are missing, falls back to assisted native dialer.
     */
    fun dispatchAiCall(
        context: Context,
        taskId: String,
        phoneNumber: String,
        contactName: String? = null,
        customMessage: String? = null
    ): Boolean {
        if (!hasRequiredPermissions(context)) {
            Toast.makeText(
                context,
                "Permissions required for AI Calling (Phone & Microphone). Opening dialer instead.",
                Toast.LENGTH_LONG
            ).show()
            return CallManager.launchDialer(context, phoneNumber)
        }

        return try {
            AiVoiceCallService.start(
                context = context,
                taskId = taskId,
                phoneNumber = phoneNumber,
                contactName = contactName,
                customMessage = customMessage
            )
            Toast.makeText(
                context,
                "🤖 CallMate AI is placing cellular call on your behalf...",
                Toast.LENGTH_SHORT
            ).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not start AI Voice Call: ${e.message}", Toast.LENGTH_SHORT).show()
            CallManager.launchDialer(context, phoneNumber)
        }
    }
}
