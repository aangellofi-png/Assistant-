package com.example.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class JarvisVoiceManager(
    private val context: Context,
    private val onSpeechResults: (String) -> Unit,
    private val onSpeechStateChanged: (SpeechState) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private var speechLocale: Locale = Locale("hi", "IN")
    private var ttsLocale: Locale = Locale("en", "IN") // Hinglish default uses Indian accent English for Roman script reading

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel

    init {
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }

    fun setLanguage(language: String) {
        when (language.lowercase()) {
            "english" -> {
                speechLocale = Locale("en", "US")
                ttsLocale = Locale.US
            }
            "hindi" -> {
                speechLocale = Locale("hi", "IN")
                ttsLocale = Locale("hi", "IN")
            }
            else -> { // Hinglish
                speechLocale = Locale("hi", "IN")
                ttsLocale = Locale("en", "IN")
            }
        }
        if (isTtsInitialized && textToSpeech != null) {
            val result = textToSpeech?.setLanguage(ttsLocale)
            Log.d("JarvisVoiceManager", "Updated TTS locale to $ttsLocale, result code: $result")
        }
    }

    enum class SpeechState {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING,
        ERROR
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@JarvisVoiceManager)
            }
        } else {
            Log.w("JarvisVoiceManager", "Speech recognizer not available on this device.")
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context, this)
    }

    fun startListening() {
        if (speechRecognizer == null) {
            onSpeechStateChanged(SpeechState.ERROR)
            onSpeechResults("Speech Recognition is not supported on this emulator/device.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            onSpeechStateChanged(SpeechState.LISTENING)
        } catch (e: Exception) {
            Log.e("JarvisVoiceManager", "Failed to start speech recognizer: ${e.message}")
            onSpeechStateChanged(SpeechState.ERROR)
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        onSpeechStateChanged(SpeechState.IDLE)
        _soundLevel.value = 0f
    }

    fun speak(text: String) {
        if (isTtsInitialized && textToSpeech != null) {
            onSpeechStateChanged(SpeechState.SPEAKING)
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JarvisSpeechId")
        } else {
            Log.e("JarvisVoiceManager", "TTS is not initialized yet.")
        }
    }

    fun setTtsParams(pitch: Float, rate: Float) {
        textToSpeech?.setPitch(pitch)
        textToSpeech?.setSpeechRate(rate)
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        onSpeechStateChanged(SpeechState.IDLE)
    }

    fun destroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    // --- Speech RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d("Speech", "Ready for speech")
        onSpeechStateChanged(SpeechState.LISTENING)
    }

    override fun onBeginningOfSpeech() {
        Log.d("Speech", "Speech beginning")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Map rmsdB (typically -2 to 10+) to normalized 0f to 1f for the custom waves animation
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        _soundLevel.value = normalized
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        onSpeechStateChanged(SpeechState.PROCESSING)
        _soundLevel.value = 0f
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions missing"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "System recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server-side error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            else -> "Speech recognizer error"
        }
        Log.e("Speech", "Error: $errorMessage ($error)")
        onSpeechStateChanged(SpeechState.ERROR)
        _soundLevel.value = 0f
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val bestResult = matches?.firstOrNull()
        if (bestResult != null) {
            onSpeechResults(bestResult)
        } else {
            onSpeechStateChanged(SpeechState.IDLE)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partial = matches?.firstOrNull()
        if (partial != null) {
            // Can be used to show real-time transcription if desired
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    // --- TextToSpeech.OnInitListener Callbacks ---

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(ttsLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("JarvisVoiceManager", "TTS Language $ttsLocale not supported.")
            } else {
                isTtsInitialized = true
                Log.d("JarvisVoiceManager", "TTS engine successfully initialized with language $ttsLocale.")
                // Set custom utterance progress listener to reset state back to idle after speaking completes
                textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onSpeechStateChanged(SpeechState.SPEAKING)
                    }

                    override fun onDone(utteranceId: String?) {
                        onSpeechStateChanged(SpeechState.IDLE)
                    }

                    override fun onError(utteranceId: String?) {
                        onSpeechStateChanged(SpeechState.ERROR)
                    }
                })
            }
        } else {
            Log.e("JarvisVoiceManager", "TTS initialization failed.")
        }
    }
}
