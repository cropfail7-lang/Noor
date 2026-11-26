package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class VoiceAssistantManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private var onFinalResultCallback: ((String) -> Unit)? = null

    private var speechRate = 1.0f
    private var speechPitch = 1.02f

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val urPk = Locale("ur", "PK")
                val urResult = tts?.setLanguage(urPk) ?: TextToSpeech.LANG_MISSING_DATA
                if (urResult == TextToSpeech.LANG_MISSING_DATA || urResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val urGeneric = tts?.setLanguage(Locale("ur")) ?: TextToSpeech.LANG_MISSING_DATA
                    if (urGeneric == TextToSpeech.LANG_MISSING_DATA || urGeneric == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale("en", "PK"))
                    }
                }
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(speechPitch)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
                ttsReady = true
            } else {
                Log.w(TAG, "TextToSpeech init failed with status=$status")
            }
        }
    }

    fun updateVoiceTuning(rate: Float, pitch: Float) {
        speechRate = rate.coerceIn(0.6f, 1.6f)
        speechPitch = pitch.coerceIn(0.7f, 1.4f)
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(speechPitch)
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        val cleanText = text
            .replace(Regex("\\[.*?\\]"), "")
            .replace("*", "")
            .replace("#", "")
            .trim()
        if (cleanText.isBlank()) return

        // Prefer Urdu voice if Urdu script is detected, else English/PK voice
        val containsUrduChars = cleanText.any { it in '\u0600'..'\u06FF' }
        val targetLocale = if (containsUrduChars) Locale("ur", "PK") else Locale("en", "PK")
        val check = tts?.isLanguageAvailable(targetLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (check >= TextToSpeech.LANG_AVAILABLE) {
            tts?.language = targetLocale
        }
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(speechPitch)
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun startListening(languageTag: String = "ur-PK", onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceError.value = "Speech recognition service is unavailable on this device."
            return
        }
        stopSpeaking()
        _voiceError.value = null
        _partialTranscript.value = ""
        onFinalResultCallback = onResult

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _rmsLevel.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_CLIENT) {
                        _voiceError.value = mapSpeechError(error)
                    }
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull()?.trim().orEmpty()
                    _partialTranscript.value = ""
                    if (transcript.isNotBlank()) {
                        onFinalResultCallback?.invoke(transcript)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            _isListening.value = false
            _voiceError.value = e.localizedMessage ?: "Voice input failed."
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        _isListening.value = false
        _rmsLevel.value = 0f
        _partialTranscript.value = ""
    }

    fun emergencyStop() {
        stopListening()
        stopSpeaking()
    }

    fun destroy() {
        emergencyStop()
        try {
            speechRecognizer?.destroy()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        tts = null
    }

    private fun mapSpeechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during voice recognition."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        else -> "Speech recognition error ($code)."
    }

    companion object {
        private const val TAG = "VoiceAssistantManager"
    }
}
