package com.example.communication

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.local.CallMateDatabase
import com.example.data.repository.PreferencesRepository
import com.example.domain.model.ActionType
import com.example.domain.model.ExecutionMode
import com.example.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class AiVoiceCallService : Service() {

    companion object {
        private const val TAG = "AiVoiceCallService"
        const val CHANNEL_ID = "callmate_ai_voice_call_channel"
        private const val NOTIFICATION_ID = 2002
        private const val SUMMARY_NOTIFICATION_ID = 2003

        const val ACTION_START_AI_CALL = "com.callmate.ai.ACTION_START_AI_CALL"
        const val ACTION_STOP_AI_CALL = "com.callmate.ai.ACTION_STOP_AI_CALL"
        const val ACTION_SPEAK_NOW = "com.callmate.ai.ACTION_SPEAK_NOW"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_CUSTOM_MESSAGE = "extra_custom_message"

        fun start(
            context: Context,
            taskId: String,
            phoneNumber: String,
            contactName: String? = null,
            customMessage: String? = null
        ) {
            val intent = Intent(context, AiVoiceCallService::class.java).apply {
                action = ACTION_START_AI_CALL
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CONTACT_NAME, contactName)
                putExtra(EXTRA_CUSTOM_MESSAGE, customMessage)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AiVoiceCallService::class.java).apply {
                action = ACTION_STOP_AI_CALL
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var telephonyManager: TelephonyManager? = null
    private var audioManager: AudioManager? = null
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var taskId: String = ""
    private var phoneNumber: String = ""
    private var contactName: String = ""
    private var customMessage: String = ""

    private var isCallConnected = false
    private var hasSpokenIntro = false
    private var recordedReply: String? = null
    private var isTtsReady = false
    private var hasCompletedSession = false

    private var telephonyCallbackApi31: Any? = null
    private var phoneStateListenerPre31: PhoneStateListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        createNotificationChannel()
        initTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_AI_CALL) {
            Log.d(TAG, "Stop action requested by user")
            endCallAndCleanup(userCancelled = true)
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_SPEAK_NOW) {
            Log.d(TAG, "Manual Speak Now triggered")
            activateSpeakerphoneBridge()
            speakAiIntroMessage()
            return START_NOT_STICKY
        }

        taskId = intent?.getStringExtra(EXTRA_TASK_ID).orEmpty()
        phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
        contactName = intent?.getStringExtra(EXTRA_CONTACT_NAME) ?: phoneNumber
        customMessage = intent?.getStringExtra(EXTRA_CUSTOM_MESSAGE).orEmpty()

        if (phoneNumber.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification("Dialing $contactName on SIM... Tap 'Speak Now' when answered"))
        registerCallStateListener()
        placeCellularSimCall()

        return START_NOT_STICKY
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                val preferences = PreferencesRepository(this)
                val lang = if (preferences.language.value == "BN") Locale.forLanguageTag("bn-BD") else Locale.US
                textToSpeech?.language = lang

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                    .build()
                textToSpeech?.setAudioAttributes(audioAttributes)

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS Utterance started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS Utterance completed: $utteranceId")
                        when (utteranceId) {
                            "utterance_intro" -> {
                                // Intro message finished. Listen for recipient response!
                                mainHandler.post {
                                    startListeningForRecipientReply()
                                }
                            }
                            "utterance_goodbye" -> {
                                // Goodbye finished. Terminate call gracefully.
                                mainHandler.postDelayed({
                                    endCallAndCleanup(userCancelled = false)
                                }, 1200L)
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS Utterance error on: $utteranceId")
                        mainHandler.post {
                            endCallAndCleanup(userCancelled = false)
                        }
                    }
                })
            }
        }
    }

    private fun placeCellularSimCall() {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$cleanNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(callIntent)
            updateNotification("Dialing $contactName on SIM...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to place cellular call: ${e.message}")
            stopSelf()
        }
    }

    private fun registerCallStateListener() {
        val telephony = telephonyManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = @RequiresApi(Build.VERSION_CODES.S) object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state)
                }
            }
            telephonyCallbackApi31 = callback
            telephony.registerTelephonyCallback(mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                    handleCallState(state)
                }
            }
            phoneStateListenerPre31 = listener
            @Suppress("DEPRECATION")
            telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun unregisterCallStateListener() {
        val telephony = telephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallbackApi31 as? TelephonyCallback)?.let {
                telephony.unregisterTelephonyCallback(it)
            }
        } else {
            phoneStateListenerPre31?.let {
                @Suppress("DEPRECATION")
                telephony.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    private fun handleCallState(state: Int) {
        Log.d(TAG, "Call State changed: $state")
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call is dialing or connected
                if (!isCallConnected) {
                    isCallConnected = true
                    activateSpeakerphoneBridge()
                    updateNotification("Call connected on SIM. Speaking in 9s (or tap 'Speak Now')...")
                    // Wait 9 seconds for carrier connection, ringing, and recipient to pick up & say Hello
                    serviceScope.launch {
                        delay(9000L)
                        if (isCallConnected && !hasSpokenIntro) {
                            speakAiIntroMessage()
                        }
                    }
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call disconnected (either by recipient, carrier, or user)
                if (isCallConnected) {
                    Log.d(TAG, "Call disconnected by remote party")
                    endCallAndCleanup(userCancelled = false)
                }
            }
        }
    }

    private fun activateSpeakerphoneBridge() {
        try {
            audioManager?.let { am ->
                // Ensure audio mode is in-call so telephony routing stays connected
                am.mode = AudioManager.MODE_IN_CALL

                // Ensure microphone is unmuted
                am.isMicrophoneMute = false

                // Maximize in-call voice call and music volume
                val maxVoiceVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoiceVol, 0)
                val maxMusicVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speaker = am.availableCommunicationDevices.find {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        am.setCommunicationDevice(speaker)
                    }
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                Log.d(TAG, "Acoustic Speakerphone bridge engaged (MODE_IN_CALL, max vol, mic unmuted)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not switch speakerphone: ${e.message}")
        }
    }

    private fun deactivateSpeakerphoneBridge() {
        try {
            audioManager?.let { am ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                am.mode = AudioManager.MODE_NORMAL
                Log.d(TAG, "Acoustic Speakerphone bridge restored to normal")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring audio settings: ${e.message}")
        }
    }

    private fun speakAiIntroMessage() {
        if (hasSpokenIntro) return
        hasSpokenIntro = true
        activateSpeakerphoneBridge()
        updateNotification("Speaking to $contactName on your behalf...")

        val preferences = PreferencesRepository(this)
        val isBengali = preferences.language.value == "BN"

        val spokenScript = if (isBengali) {
            buildString {
                append("হ্যালো? ... হ্যালো! আমি কলমেট এআই সহকারী। ")
                append("আপনার সাথে যিনি যোগাযোগ করতে চেয়েছিলেন তিনি বর্তমানে একটি গুরুত্বপূর্ণ মিটিংয়ে আছেন। ")
                if (customMessage.isNotBlank()) {
                    append("তার বার্তাটি হলো: $customMessage। আমি আবার বলছি: $customMessage। ")
                }
                append("অনুগ্রহ করে আপনার কোনো উত্তর থাকলে এখন বলুন, আমি তা সংরক্ষণ করে তাকে পৌঁছে দেব।")
            }
        } else {
            buildString {
                append("Hello? ... Hello! This is CallMate AI assistant. ")
                append("The person calling you is currently occupied in an important meeting. ")
                if (customMessage.isNotBlank()) {
                    append("Their message for you is: $customMessage. I repeat: $customMessage. ")
                }
                append("Please speak your response now and I will record it.")
            }
        }

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utterance_intro")
        }
        textToSpeech?.speak(spokenScript, TextToSpeech.QUEUE_FLUSH, params, "utterance_intro")
    }

    private fun startListeningForRecipientReply() {
        updateNotification("Listening for $contactName's response...")

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            recordedReply = "Speech recognition unavailable on device"
            speakGoodbye()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "SpeechRecognizer ready for recipient speech")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.w(TAG, "SpeechRecognizer error: $error")
                    if (recordedReply == null) {
                        recordedReply = "No clear reply detected (code: $error)"
                    }
                    speakGoodbye()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    recordedReply = if (!text.isNullOrBlank()) text else "No audible response"
                    Log.d(TAG, "Captured recipient reply: $recordedReply")
                    speakGoodbye()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val preferences = PreferencesRepository(this@AiVoiceCallService)
            val lang = if (preferences.language.value == "BN") "bn-BD" else "en-US"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(recognizerIntent)

        // Safety fallback timer: if no speech after 8 seconds, wrap up
        serviceScope.launch {
            delay(8000L)
            if (recordedReply == null) {
                recordedReply = "Recipient did not provide a verbal response."
                speakGoodbye()
            }
        }
    }

    private fun speakGoodbye() {
        speechRecognizer?.destroy()
        speechRecognizer = null

        updateNotification("Wrapping up call with $contactName...")

        val preferences = PreferencesRepository(this)
        val isBengali = preferences.language.value == "BN"

        val goodbyeScript = if (isBengali) {
            "ধন্যবাদ! আপনার উত্তর রেকর্ড করা হয়েছে। আমি তাকে জানিয়ে দেব। বিদায়।"
        } else {
            "Thank you. Your response has been recorded. I will deliver it to them immediately. Goodbye."
        }

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utterance_goodbye")
        }
        textToSpeech?.speak(goodbyeScript, TextToSpeech.QUEUE_FLUSH, params, "utterance_goodbye")
    }

    private fun endCallAndCleanup(userCancelled: Boolean) {
        if (hasCompletedSession) return
        hasCompletedSession = true
        isCallConnected = false

        // Disconnect cellular call if possible
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    telecomManager?.endCall()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not programmatically end call: ${e.message}")
                }
            }
        }

        deactivateSpeakerphoneBridge()
        unregisterCallStateListener()

        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        val replyOutcome = recordedReply ?: if (userCancelled) "Cancelled by user" else "Call ended"

        // Persist execution and task completion in database
        if (taskId.isNotBlank()) {
            val database = CallMateDatabase.getDatabase(this)
            serviceScope.launch(Dispatchers.IO) {
                database.taskDao().updateTaskStatus(taskId, TaskStatus.COMPLETED)
                database.executionDao().insertExecution(
                    com.example.data.local.entities.ExecutionEntity(
                        taskId = taskId,
                        actionType = ActionType.CALL,
                        executionMode = ExecutionMode.AUTOMATIC,
                        resultOutcome = if (userCancelled) "AI_CALL_CANCELLED" else "AI_CALL_COMPLETED",
                        details = "Target: $contactName ($phoneNumber) | Recipient reply: '$replyOutcome'"
                    )
                )
            }
        }

        showPostCallSummaryNotification(contactName, phoneNumber, replyOutcome)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CallMate AI Voice Call Assistant",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active voice call handling on behalf of the user"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val stopIntent = Intent(this, AiVoiceCallService::class.java).apply {
            action = ACTION_STOP_AI_CALL
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speakIntent = Intent(this, AiVoiceCallService::class.java).apply {
            action = ACTION_SPEAK_NOW
        }
        val speakPendingIntent = PendingIntent.getService(
            this,
            103,
            speakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIntent = Intent(this, MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            this,
            102,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("🤖 CallMate AI Voice Assistant")
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "▶ Speak Now", speakPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Call", stopPendingIntent)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val notification = buildForegroundNotification(statusText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showPostCallSummaryNotification(name: String, phone: String, reply: String) {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            this,
            SUMMARY_NOTIFICATION_ID,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🤖 AI Call Completed: $name")
            .setContentText("Recipient said: \"$reply\"")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("CallMate AI successfully spoke to $name ($phone) on your behalf.\n\n📝 Recipient's response:\n\"$reply\"")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        endCallAndCleanup(userCancelled = true)
        super.onDestroy()
    }
}

