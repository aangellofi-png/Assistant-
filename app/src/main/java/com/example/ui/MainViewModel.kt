package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiService
import com.example.data.models.CommandLog
import com.example.data.models.Note
import com.example.data.models.Reminder
import com.example.data.repository.JarvisRepository
import com.example.utils.JarvisVoiceManager
import com.example.utils.JarvisVoiceService
import com.example.utils.PhoneAutomationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(private val repository: JarvisRepository) : ViewModel() {

    private val TAG = "MainViewModel"

    // --- Local DB flows ---
    val notes: StateFlow<List<Note>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<Reminder>> = repository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<CommandLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Voice and Speech states ---
    private val _speechState = MutableStateFlow(JarvisVoiceManager.SpeechState.IDLE)
    val speechState: StateFlow<JarvisVoiceManager.SpeechState> = _speechState

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel

    // --- Custom feedback config ---
    val isTtsEnabled = MutableStateFlow(true)

    // --- Persistent Custom Settings ---
    private val _wakeWord = MutableStateFlow("Hey Jarvis")
    val wakeWord: StateFlow<String> = _wakeWord

    private val _voiceSensitivity = MutableStateFlow(0.5f)
    val voiceSensitivity: StateFlow<Float> = _voiceSensitivity

    private val _ttsPitch = MutableStateFlow(1.0f)
    val ttsPitch: StateFlow<Float> = _ttsPitch

    private val _ttsSpeechRate = MutableStateFlow(1.0f)
    val ttsSpeechRate: StateFlow<Float> = _ttsSpeechRate

    private val _selectedThemeColor = MutableStateFlow("Cyan")
    val selectedThemeColor: StateFlow<String> = _selectedThemeColor

    private val _language = MutableStateFlow("Hinglish")
    val language: StateFlow<String> = _language

    // --- Hands-free Wake Word Detection States ---
    private val _isWakeWordDetectionEnabled = MutableStateFlow(true)
    val isWakeWordDetectionEnabled: StateFlow<Boolean> = _isWakeWordDetectionEnabled

    private val _isAwake = MutableStateFlow(false)
    val isAwake: StateFlow<Boolean> = _isAwake

    private val _lastInteractionTime = MutableStateFlow(System.currentTimeMillis())
    val lastInteractionTime: StateFlow<Long> = _lastInteractionTime

    // --- Service Connection States ---
    private var jarvisService: JarvisVoiceService? = null
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound
    private var appContext: Context? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
            val binder = service as JarvisVoiceService.JarvisBinder
            val s = binder.getService()
            jarvisService = s
            _isServiceBound.value = true
            
            // Collect/sync all StateFlows from Service to ViewModel
            viewModelScope.launch {
                s.speechState.collect { _speechState.value = it }
            }
            viewModelScope.launch {
                s.soundLevel.collect { _soundLevel.value = it }
            }
            viewModelScope.launch {
                s.currentPrompt.collect { _currentPrompt.value = it }
            }
            viewModelScope.launch {
                s.currentResponse.collect { _currentResponse.value = it }
            }
            viewModelScope.launch {
                s.isProcessing.collect { _isProcessing.value = it }
            }
            viewModelScope.launch {
                s.isAwake.collect { _isAwake.value = it }
            }
            viewModelScope.launch {
                s.lastInteractionTime.collect { _lastInteractionTime.value = it }
            }
            viewModelScope.launch {
                s.wakeWord.collect { _wakeWord.value = it }
            }
            viewModelScope.launch {
                s.isWakeWordDetectionEnabled.collect { _isWakeWordDetectionEnabled.value = it }
            }
            viewModelScope.launch {
                s.batteryLevel.collect { _batteryLevel.value = it }
            }
            viewModelScope.launch {
                s.isCharging.collect { _isCharging.value = it }
            }
            viewModelScope.launch {
                s.lowBatteryWarningThreshold.collect { _lowBatteryWarningThreshold.value = it }
            }
            viewModelScope.launch {
                s.isTtsEnabled.collect { isTtsEnabled.value = it }
            }
            viewModelScope.launch {
                s.ttsPitch.collect { _ttsPitch.value = it }
            }
            viewModelScope.launch {
                s.ttsSpeechRate.collect { _ttsSpeechRate.value = it }
            }
            viewModelScope.launch {
                s.voiceSensitivity.collect { _voiceSensitivity.value = it }
            }
            viewModelScope.launch {
                s.language.collect { _language.value = it }
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            jarvisService = null
            _isServiceBound.value = false
        }
    }

    fun setWakeWordDetectionEnabled(value: Boolean, context: Context) {
        _isWakeWordDetectionEnabled.value = value
        saveSetting(context, "wake_word_detection_enabled", value)
        jarvisService?.setWakeWordDetectionEnabled(value)
    }

    fun setAwake(value: Boolean) {
        _isAwake.value = value
        jarvisService?.setAwake(value)
    }

    // --- Battery/Power Monitoring state ---
    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging

    private val _lowBatteryWarningThreshold = MutableStateFlow(20)
    val lowBatteryWarningThreshold: StateFlow<Int> = _lowBatteryWarningThreshold

    fun setLowBatteryWarningThreshold(value: Int, context: Context) {
        _lowBatteryWarningThreshold.value = value
        saveSetting(context, "low_battery_warning_threshold", value)
        jarvisService?.setLowBatteryWarningThreshold(value)
    }

    fun setWakeWord(value: String, context: Context) {
        _wakeWord.value = value
        saveSetting(context, "wake_word", value)
        jarvisService?.setWakeWord(value)
    }

    fun setVoiceSensitivity(value: Float, context: Context) {
        _voiceSensitivity.value = value
        saveSetting(context, "voice_sensitivity", value)
        jarvisService?.setVoiceSensitivity(value)
    }

    fun setTtsPitch(value: Float, context: Context) {
        _ttsPitch.value = value
        saveSetting(context, "tts_pitch", value)
        jarvisService?.setTtsParams(value, _ttsSpeechRate.value)
    }

    fun setTtsSpeechRate(value: Float, context: Context) {
        _ttsSpeechRate.value = value
        saveSetting(context, "tts_speech_rate", value)
        jarvisService?.setTtsParams(_ttsPitch.value, value)
    }

    fun setSelectedThemeColor(value: String, context: Context) {
        _selectedThemeColor.value = value
        saveSetting(context, "selected_theme_color", value)
    }

    fun setLanguage(value: String, context: Context) {
        _language.value = value
        saveSetting(context, "language", value)
        jarvisService?.setLanguage(value)
    }

    fun toggleTtsEnabled(context: Context) {
        val newVal = !isTtsEnabled.value
        isTtsEnabled.value = newVal
        saveSetting(context, "tts_enabled", newVal)
        jarvisService?.setTtsEnabled(newVal)
    }

    private fun saveSetting(context: Context, key: String, value: Any) {
        val prefs = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
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

    fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
        _wakeWord.value = prefs.getString("wake_word", "Hey Jarvis") ?: "Hey Jarvis"
        _voiceSensitivity.value = prefs.getFloat("voice_sensitivity", 0.5f)
        _ttsPitch.value = prefs.getFloat("tts_pitch", 1.0f)
        _ttsSpeechRate.value = prefs.getFloat("tts_speech_rate", 1.0f)
        _selectedThemeColor.value = prefs.getString("selected_theme_color", "Cyan") ?: "Cyan"
        isTtsEnabled.value = prefs.getBoolean("tts_enabled", true)
        _lowBatteryWarningThreshold.value = prefs.getInt("low_battery_warning_threshold", 20)
        _isWakeWordDetectionEnabled.value = prefs.getBoolean("wake_word_detection_enabled", true)
        _language.value = prefs.getString("language", "Hinglish") ?: "Hinglish"
    }

    // --- Conversation state ---
    private val _currentPrompt = MutableStateFlow("")
    val currentPrompt: StateFlow<String> = _currentPrompt

    private val _currentResponse = MutableStateFlow("System loaded. Welcome back, Boss. How can I assist you today?")
    val currentResponse: StateFlow<String> = _currentResponse

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    // --- Simulator Deck states (Calls, WhatsApp, SMS) ---
    private val _simulatedCaller = MutableStateFlow<String?>(null)
    val simulatedCaller: StateFlow<String?> = _simulatedCaller

    private val _simulatedMessage = MutableStateFlow<SimulatedMsg?>(null)
    val simulatedMessage: StateFlow<SimulatedMsg?> = _simulatedMessage

    private val _aiDraftedReply = MutableStateFlow<String?>(null)
    val aiDraftedReply: StateFlow<String?> = _aiDraftedReply

    data class SimulatedMsg(val sender: String, val text: String, val type: String) // WhatsApp or SMS

    fun setSpeechState(state: JarvisVoiceManager.SpeechState) {
        _speechState.value = state
    }

    fun initVoiceManager(context: Context) {
        val app = context.applicationContext
        appContext = app
        loadSettings(app)
        val intent = Intent(app, JarvisVoiceService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun checkBatteryStatus(context: Context, forceSpeak: Boolean = false) {
        jarvisService?.checkBatteryStatus(forceSpeak)
    }

    fun triggerVoiceListening() {
        jarvisService?.startListening()
    }

    fun stopVoiceListening() {
        jarvisService?.stopListening()
    }

    fun checkAndRestartListening() {
        jarvisService?.checkAndRestartListening()
    }

    fun speakAloud(text: String) {
        jarvisService?.speakAloud(text)
    }

    fun stopSpeaking() {
        jarvisService?.stopSpeaking()
    }

    // --- Command processing core ---
    fun processCommand(prompt: String, context: Context, isVoice: Boolean) {
        val service = jarvisService
        if (service != null) {
            service.processCommand(prompt, isVoice)
        } else {
            Log.w(TAG, "Service not bound yet, skipping command: $prompt")
        }
    }

    // --- Notes and Reminders standard CRUD methods ---
    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            repository.insertNote(Note(title = title, content = content))
        }
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            repository.deleteNoteById(id)
        }
    }

    fun addReminder(title: String, dateTime: String) {
        viewModelScope.launch {
            repository.insertReminder(Reminder(title = title, dateTime = dateTime))
        }
    }

    fun toggleReminder(id: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateReminderCompletion(id, isCompleted)
        }
    }

    fun deleteReminder(id: Int) {
        viewModelScope.launch {
            repository.deleteReminderById(id)
        }
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    // --- Simulator Deck triggers ---
    fun simulateIncomingCall(callerName: String) {
        _simulatedCaller.value = callerName
        speakAloud("Boss, you have an incoming holographic call from $callerName. Do you wish to accept?")
    }

    fun endSimulatedCall(wasAccepted: Boolean) {
        val caller = _simulatedCaller.value
        _simulatedCaller.value = null
        stopSpeaking()
        if (wasAccepted) {
            _currentResponse.value = "Call connected with $caller. Interface active, Sir."
            speakAloud("Connecting channel with $caller now.")
        } else {
            _currentResponse.value = "Call rejected. Logged to archives, Sir."
            speakAloud("Call rejected, Boss.")
        }
    }

    fun simulateIncomingMessage(sender: String, messageText: String, type: String) {
        val msg = SimulatedMsg(sender, messageText, type)
        _simulatedMessage.value = msg
        _aiDraftedReply.value = null // reset prior
        
        speakAloud("Sir, you received a $type from $sender. Let me parse an intelligent AI reply.")
        
        // Use Gemini to auto-draft a reply!
        viewModelScope.launch {
            val draftPrompt = "Draft a very short, polite, or witty reply to this message from $sender: \"$messageText\". Respond as if drafted by Jarvis on behalf of Tony Stark."
            val replyDraft = GeminiService.getJarvisResponse(draftPrompt)
            _aiDraftedReply.value = replyDraft
        }
    }

    fun sendAIParsedReply() {
        val msg = _simulatedMessage.value
        val draft = _aiDraftedReply.value
        if (msg != null && draft != null) {
            _simulatedMessage.value = null
            _aiDraftedReply.value = null
            _currentResponse.value = "Draft transmitted to ${msg.sender} via AI: \"$draft\""
            speakAloud("Draft reply successfully transmitted to ${msg.sender}, Boss.")
        }
    }

    fun cancelSimulatedMessage() {
        _simulatedMessage.value = null
        _aiDraftedReply.value = null
        stopSpeaking()
    }

    override fun onCleared() {
        super.onCleared()
        appContext?.let {
            if (_isServiceBound.value) {
                try {
                    it.unbindService(serviceConnection)
                } catch (e: Exception) {
                    Log.e(TAG, "Unbind failed: ${e.message}")
                }
            }
        }
    }
}

// --- ViewModel Factory ---
class MainViewModelFactory(private val repository: JarvisRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
