package com.example.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.api.GeminiService
import com.example.data.database.JarvisDatabase
import com.example.data.models.CommandLog
import com.example.data.models.Note
import com.example.data.models.Reminder
import com.example.data.repository.JarvisRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class JarvisVoiceService : Service() {

    private val TAG = "JarvisVoiceService"
    private val NOTIFICATION_ID = 1001

    private lateinit var serviceScope: CoroutineScope
    private lateinit var repository: JarvisRepository
    private var voiceManager: JarvisVoiceManager? = null

    // Binder given to clients
    private val binder = JarvisBinder()

    // --- Service State Flows (observed by ViewModel) ---
    private val _speechState = MutableStateFlow(JarvisVoiceManager.SpeechState.IDLE)
    val speechState: StateFlow<JarvisVoiceManager.SpeechState> = _speechState

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel

    private val _currentPrompt = MutableStateFlow("")
    val currentPrompt: StateFlow<String> = _currentPrompt

    private val _currentResponse = MutableStateFlow("System loaded. Welcome back, Boss. How can I assist you today?")
    val currentResponse: StateFlow<String> = _currentResponse

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _isAwake = MutableStateFlow(false)
    val isAwake: StateFlow<Boolean> = _isAwake

    private val _lastInteractionTime = MutableStateFlow(System.currentTimeMillis())
    val lastInteractionTime: StateFlow<Long> = _lastInteractionTime

    private val _wakeWord = MutableStateFlow("Hey Jarvis")
    val wakeWord: StateFlow<String> = _wakeWord

    private val _isWakeWordDetectionEnabled = MutableStateFlow(true)
    val isWakeWordDetectionEnabled: StateFlow<Boolean> = _isWakeWordDetectionEnabled

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging

    private val _lowBatteryWarningThreshold = MutableStateFlow(20)
    val lowBatteryWarningThreshold: StateFlow<Int> = _lowBatteryWarningThreshold

    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled

    private val _ttsPitch = MutableStateFlow(1.0f)
    val ttsPitch: StateFlow<Float> = _ttsPitch

    private val _ttsSpeechRate = MutableStateFlow(1.0f)
    val ttsSpeechRate: StateFlow<Float> = _ttsSpeechRate

    private val _voiceSensitivity = MutableStateFlow(0.5f)
    val voiceSensitivity: StateFlow<Float> = _voiceSensitivity

    private val _language = MutableStateFlow("Hinglish")
    val language: StateFlow<String> = _language

    inner class JarvisBinder : Binder() {
        fun getService(): JarvisVoiceService = this@JarvisVoiceService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Creating JarvisVoiceService")
        serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        // Load database & repository
        val database = JarvisDatabase.getDatabase(applicationContext)
        repository = JarvisRepository(
            noteDao = database.noteDao(),
            reminderDao = database.reminderDao(),
            commandLogDao = database.commandLogDao()
        )

        loadSettings()
        showNotification()
        initializeVoiceSystems()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Started service")
        // Always-on foreground behavior
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind: Bound by client")
        return binder
    }

    private fun showNotification() {
        val channelId = "jarvis_voice_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Jarvis Voice Core"
            val descriptionText = "Monitors wake word and processes voice commands hands-free"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis Core Active")
            .setContentText("Hands-free wake word detection is active...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initializeVoiceSystems() {
        val manager = JarvisVoiceManager(
            context = applicationContext,
            onSpeechResults = { text ->
                handleSpeechResults(text)
            },
            onSpeechStateChanged = { state ->
                _speechState.value = state
                if (state == JarvisVoiceManager.SpeechState.IDLE || state == JarvisVoiceManager.SpeechState.ERROR) {
                    checkAndRestartListening()
                }
            }
        )
        manager.setLanguage(_language.value)
        voiceManager = manager

        serviceScope.launch {
            manager.soundLevel.collect {
                _soundLevel.value = it
            }
        }

        // Inactivity timeout checking (60-second limit)
        // Monitors the microphone input stream activity and triggers sleep / stops the listener
        serviceScope.launch {
            while (true) {
                delay(2000) // Poll every 2 seconds for high responsiveness
                val isMicActive = _isWakeWordDetectionEnabled.value || _speechState.value == JarvisVoiceManager.SpeechState.LISTENING
                if (isMicActive) {
                    val elapsed = System.currentTimeMillis() - _lastInteractionTime.value
                    if (elapsed >= 60000) { // 60 seconds of inactivity
                        Log.d(TAG, "Inactivity timeout of 60 seconds reached. Entering deep sleep and disabling microphone listener.")
                        _isAwake.value = false
                        
                        // Stop the listener completely to release microphone stream
                        setWakeWordDetectionEnabled(false)
                        
                        val sleepMsg = "Deep sleep mode activated due to inactivity, Sir. Microphone has been taken offline to conserve core power."
                        _currentResponse.value = sleepMsg
                        speakAloud(sleepMsg)
                    }
                }
            }
        }

        // Periodic battery/power tracking
        serviceScope.launch {
            while (true) {
                checkBatteryStatus(false)
                delay(15000)
            }
        }

        checkAndRestartListening()
    }

    private fun handleSpeechResults(text: String) {
        val lowerText = text.lowercase(Locale.getDefault())
        val wakeWordLower = _wakeWord.value.lowercase(Locale.getDefault())

        if (!_isAwake.value) {
            if (lowerText.contains(wakeWordLower)) {
                _isAwake.value = true
                _lastInteractionTime.value = System.currentTimeMillis()

                val index = lowerText.indexOf(wakeWordLower)
                val afterWake = text.substring(index + _wakeWord.value.length).trim()
                val cleanAfterWake = if (afterWake.startsWith(",") || afterWake.startsWith(".") || afterWake.startsWith("-")) {
                    afterWake.substring(1).trim()
                } else {
                    afterWake
                }

                if (cleanAfterWake.isNotEmpty()) {
                    _currentPrompt.value = text
                    processCommand(cleanAfterWake, isVoice = true)
                } else {
                    _currentPrompt.value = text
                    val replyText = "Yes, Boss?"
                    _currentResponse.value = replyText
                    speakAloud(replyText)
                }
            } else {
                _currentPrompt.value = "Unrecognized signal: \"$text\""
                if (_isWakeWordDetectionEnabled.value) {
                    checkAndRestartListening()
                }
            }
        } else {
            _lastInteractionTime.value = System.currentTimeMillis()
            _currentPrompt.value = text
            processCommand(text, isVoice = true)
        }
    }

    fun processCommand(prompt: String, isVoice: Boolean) {
        if (prompt.trim().isEmpty()) return

        _lastInteractionTime.value = System.currentTimeMillis()
        _currentPrompt.value = prompt
        _isProcessing.value = true
        _speechState.value = JarvisVoiceManager.SpeechState.PROCESSING

        serviceScope.launch {
            val currentWake = _wakeWord.value.lowercase(Locale.getDefault())
            var cleanPrompt = prompt.trim()
            var cleanLower = cleanPrompt.lowercase(Locale.getDefault())

            if (cleanLower.startsWith(currentWake)) {
                cleanPrompt = cleanPrompt.substring(currentWake.length).trim()
                if (cleanPrompt.startsWith(",") || cleanPrompt.startsWith(".") || cleanPrompt.startsWith("-")) {
                    cleanPrompt = cleanPrompt.substring(1).trim()
                }
                cleanLower = cleanPrompt.lowercase(Locale.getDefault())
            }

            val actualPrompt = cleanPrompt
            val lower = cleanLower

            var executedResponse = ""
            var handledByAutomation = true

            try {
                when {
                    // 0. Local Keyword Fallback / Critical Commands (Always processed offline and immediately)
                    lower == "stop" || lower == "cancel" || lower == "quiet" || lower == "silent" || lower == "silence" ||
                    lower == "standby" || lower == "sleep" || lower == "dismiss" ||
                    lower.contains("stop speaking") || lower.contains("stop listening") ||
                    lower.contains("shant ho jao") || lower.contains("chup") || lower.contains("bas karo") ||
                    lower.contains("shut up") || lower.contains("bolna band") || lower.contains("shant ho") ||
                    lower.contains("ruk jao") || lower.contains("ruko") -> {
                        stopSpeaking()
                        _isAwake.value = false
                        executedResponse = "Standing down, Sir."
                    }

                    // 1. App Opening Intent
                    lower.startsWith("open ") || lower.contains("launch ") || lower.contains("kholo ") -> {
                        val appName = actualPrompt
                            .replace("open ", "", ignoreCase = true)
                            .replace("launch ", "", ignoreCase = true)
                            .replace("kholo ", "", ignoreCase = true)
                            .trim()
                        executedResponse = PhoneAutomationHelper.launchApp(applicationContext, appName)
                    }

                    // 2. Flashlight Controller
                    lower.contains("flashlight") || lower.contains("torch") || lower.contains("torch light") || lower.contains("flash light") -> {
                        val turnOn = lower.contains("on") || lower.contains("chalu") || lower.contains("jalao") || lower.contains("start")
                        val success = PhoneAutomationHelper.toggleFlashlight(applicationContext, turnOn)
                        executedResponse = if (success) {
                            if (turnOn) "Core flashlight initialized, Sir." else "Core flashlight deactivated, Boss."
                        } else {
                            "Unable to establish hardware contact for flashlight beam, Sir."
                        }
                    }

                    // 3. Alarm triggers
                    lower.contains("alarm") || lower.contains("remind me to") -> {
                        var hour = 7
                        var min = 0
                        val message = "Jarvis wake up directive"

                        val timeRegex = "(\\d{1,2}):(\\d{2})".toRegex()
                        val match = timeRegex.find(lower)
                        if (match != null) {
                            hour = match.groupValues[1].toInt()
                            min = match.groupValues[2].toInt()
                        }

                        executedResponse = PhoneAutomationHelper.setAlarm(applicationContext, hour, min, message)

                        repository.insertReminder(
                            Reminder(
                                title = if (lower.contains("remind me to")) actualPrompt.substringAfter("remind me to") else "Voice alarm scheduled",
                                dateTime = "%02d:%02d".format(hour, min)
                            )
                        )
                    }

                    // 4. Note Creation
                    lower.startsWith("note ") || lower.startsWith("make a note") || lower.startsWith("write a note") || lower.startsWith("likho ") || lower.startsWith("save note") -> {
                        val noteContent = actualPrompt
                            .replace("note ", "", ignoreCase = true)
                            .replace("make a note", "", ignoreCase = true)
                            .replace("write a note", "", ignoreCase = true)
                            .replace("likho ", "", ignoreCase = true)
                            .replace("save note", "", ignoreCase = true)
                            .trim()

                        if (noteContent.isNotEmpty()) {
                            repository.insertNote(Note(title = "Jarvis Voice Memo", content = noteContent))
                            executedResponse = "Saved to notes vault, Sir: \"$noteContent\""
                        } else {
                            executedResponse = "Note contents are empty, Sir. What should I write?"
                        }
                    }

                    // 5. Phone calls / Dialer
                    lower.contains("call ") || lower.contains("dial ") || lower.contains("phone milao") -> {
                        val number = actualPrompt.filter { it.isDigit() }.ifEmpty { "+1234567890" }
                        executedResponse = PhoneAutomationHelper.initiateCall(applicationContext, number)
                    }

                    // 6. Emails
                    lower.contains("email") || lower.contains("mail bhejo") -> {
                        executedResponse = PhoneAutomationHelper.sendEmail(
                            applicationContext,
                            "sir.tony.stark@starkindustries.com",
                            "Jarvis Core Operational Status",
                            "All systems nominal. AI neural paths are highly responsive."
                        )
                    }

                    // 7. Automation toggles (Wi-Fi, Bluetooth, Brightness)
                    lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("bluetooth") || lower.contains("brightness") -> {
                        if (lower.contains("wifi") || lower.contains("wi-fi")) {
                            PhoneAutomationHelper.toggleWifiOrBluetooth(applicationContext, isWifi = true)
                            executedResponse = "Opening Wi-Fi control center panel, Sir."
                        } else if (lower.contains("bluetooth")) {
                            PhoneAutomationHelper.toggleWifiOrBluetooth(applicationContext, isWifi = false)
                            executedResponse = "Opening Bluetooth link manager, Boss."
                        } else if (lower.contains("brightness")) {
                            PhoneAutomationHelper.changeBrightness(applicationContext, 0.8f)
                            executedResponse = "Opening display luminance adjustor, Sir."
                        }
                    }

                    // 8. Battery & Power Monitoring
                    lower.contains("battery") || lower.contains("power level") || lower.contains("charge") || lower.contains("power saving") || lower.contains("battery saver") -> {
                        if (lower.contains("saving") || lower.contains("saver") || lower.contains("toggle") || lower.contains("enable") || lower.contains("disable") || lower.contains("open") || lower.contains("activate")) {
                            val openResult = PhoneAutomationHelper.openBatterySaverSettings(applicationContext)
                            executedResponse = "$openResult You can toggle power-saving modes there to conserve system energy, Boss."
                        } else {
                            val batteryStatus: Intent? = applicationContext.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                            val level: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                            val scale: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                            val isChargingState = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING

                            if (level != -1 && scale != -1) {
                                val pct = ((level.toFloat() / scale.toFloat()) * 100).toInt()
                                _batteryLevel.value = pct
                                _isCharging.value = isChargingState

                                executedResponse = if (isChargingState) {
                                    "Auxiliary power connection active. Internal energy levels are currently at $pct% and charging, Sir."
                                } else {
                                    var response = "Core device battery level is currently at $pct%."
                                    if (pct <= _lowBatteryWarningThreshold.value) {
                                        response += " Power levels are critically low! I highly recommend opening battery settings to enable power-saving mode, Boss."
                                    } else {
                                        response += " Operating within normal power consumption parameters, Sir."
                                    }
                                    response
                                }
                            } else {
                                executedResponse = "My energy telemetry pathways are unresponsive, but device power reserves appear stable, Boss."
                            }
                        }
                    }

                    // Default path: Chat with Gemini
                    else -> {
                        handledByAutomation = false
                        executedResponse = GeminiService.getJarvisResponse(actualPrompt)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Command execution failure: ${e.message}", e)
                executedResponse = "My core processors encountered an anomaly executing that action, Boss."
            }

            _currentResponse.value = executedResponse
            _isProcessing.value = false
            _speechState.value = JarvisVoiceManager.SpeechState.IDLE

            repository.insertLog(
                CommandLog(
                    prompt = actualPrompt,
                    response = executedResponse,
                    isVoice = isVoice,
                    wasSuccessful = true
                )
            )

            if (isVoice || _isTtsEnabled.value) {
                speakAloud(executedResponse)
            }
        }
    }

    private var _batteryCriticalAnnounced = false

    fun checkBatteryStatus(forceSpeak: Boolean = false) {
        try {
            val batteryStatus: Intent? = applicationContext.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val isChargingState = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING

            if (level != -1 && scale != -1) {
                val pct = ((level.toFloat() / scale.toFloat()) * 100).toInt()
                _batteryLevel.value = pct
                _isCharging.value = isChargingState

                if (pct <= _lowBatteryWarningThreshold.value && !isChargingState) {
                    if (!_batteryCriticalAnnounced) {
                        _batteryCriticalAnnounced = true
                        speakAloud("Warning, Sir. Battery level is critically low at $pct%. I highly recommend enabling power-saving settings to prolong core systems.")
                    }
                } else if (pct > _lowBatteryWarningThreshold.value + 5 || isChargingState) {
                    _batteryCriticalAnnounced = false
                }

                if (forceSpeak) {
                    val statusText = if (isChargingState) {
                        "Power source detected. Core battery level is currently at $pct% and charging, Sir."
                    } else {
                        var response = "Core battery level is currently at $pct%."
                        if (pct <= _lowBatteryWarningThreshold.value) {
                            response += " This is critically low! I recommend opening battery configurations to toggle power-saving settings, Boss."
                        } else {
                            response += " All sub-systems operating within optimal power parameters, Sir."
                        }
                        response
                    }
                    speakAloud(statusText)
                    _currentResponse.value = statusText
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery: ${e.message}")
            if (forceSpeak) {
                speakAloud("Apologies, Sir. I was unable to access the device power cells.")
            }
        }
    }

    private var isRestartingListening = false

    fun checkAndRestartListening() {
        if (!_isWakeWordDetectionEnabled.value) return
        val currentS = _speechState.value
        if (currentS == JarvisVoiceManager.SpeechState.SPEAKING ||
            currentS == JarvisVoiceManager.SpeechState.PROCESSING ||
            currentS == JarvisVoiceManager.SpeechState.LISTENING) {
            return
        }
        if (isRestartingListening) return
        isRestartingListening = true
        serviceScope.launch {
            delay(300)
            if (_isWakeWordDetectionEnabled.value &&
                _speechState.value != JarvisVoiceManager.SpeechState.SPEAKING &&
                _speechState.value != JarvisVoiceManager.SpeechState.PROCESSING &&
                _speechState.value != JarvisVoiceManager.SpeechState.LISTENING) {
                voiceManager?.startListening()
            }
            isRestartingListening = false
        }
    }

    fun startListening() {
        voiceManager?.startListening()
    }

    fun stopListening() {
        voiceManager?.stopListening()
    }

    fun speakAloud(text: String) {
        if (_isTtsEnabled.value) {
            voiceManager?.setTtsParams(_ttsPitch.value, _ttsSpeechRate.value)
            voiceManager?.speak(text)
        }
    }

    fun stopSpeaking() {
        voiceManager?.stopSpeaking()
    }

    fun setWakeWord(value: String) {
        _wakeWord.value = value
        saveSetting("wake_word", value)
    }

    fun setLowBatteryWarningThreshold(value: Int) {
        _lowBatteryWarningThreshold.value = value
        saveSetting("low_battery_warning_threshold", value)
    }

    fun setWakeWordDetectionEnabled(value: Boolean) {
        _isWakeWordDetectionEnabled.value = value
        saveSetting("wake_word_detection_enabled", value)
        if (value) {
            checkAndRestartListening()
        } else {
            stopListening()
        }
    }

    fun setTtsEnabled(value: Boolean) {
        _isTtsEnabled.value = value
        saveSetting("tts_enabled", value)
    }

    fun setTtsParams(pitch: Float, rate: Float) {
        _ttsPitch.value = pitch
        _ttsSpeechRate.value = rate
        saveSetting("tts_pitch", pitch)
        saveSetting("tts_speech_rate", rate)
    }

    fun setVoiceSensitivity(value: Float) {
        _voiceSensitivity.value = value
        saveSetting("voice_sensitivity", value)
    }

    fun setLanguage(value: String) {
        _language.value = value
        saveSetting("language", value)
        voiceManager?.setLanguage(value)
    }

    fun setAwake(value: Boolean) {
        _isAwake.value = value
        if (value) {
            _lastInteractionTime.value = System.currentTimeMillis()
        }
    }

    private fun saveSetting(key: String, value: Any) {
        val prefs = getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            when (value) {
                is String -> putString(key, value)
                is Float -> putFloat(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
            }
            apply()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
        _wakeWord.value = prefs.getString("wake_word", "Hey Jarvis") ?: "Hey Jarvis"
        _voiceSensitivity.value = prefs.getFloat("voice_sensitivity", 0.5f)
        _ttsPitch.value = prefs.getFloat("tts_pitch", 1.0f)
        _ttsSpeechRate.value = prefs.getFloat("tts_speech_rate", 1.0f)
        _isTtsEnabled.value = prefs.getBoolean("tts_enabled", true)
        _lowBatteryWarningThreshold.value = prefs.getInt("low_battery_warning_threshold", 20)
        _isWakeWordDetectionEnabled.value = prefs.getBoolean("wake_word_detection_enabled", true)
        _language.value = prefs.getString("language", "Hinglish") ?: "Hinglish"
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Destroying service")
        voiceManager?.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }
}
