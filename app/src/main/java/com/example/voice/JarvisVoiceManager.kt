package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.JarvisLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "JarvisVoiceManager"

class JarvisVoiceManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var currentLanguage: JarvisLanguage = JarvisLanguage.ENGLISH
    private var speechRate: Float = 1.0f
    private var speechPitch: Float = 1.0f

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _isSpeechAvailable = MutableStateFlow(
        SpeechRecognizer.isRecognitionAvailable(context)
    )
    val isSpeechAvailable: StateFlow<Boolean> = _isSpeechAvailable.asStateFlow()

    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _ttsLanguageNote = MutableStateFlow<String?>(null)
    val ttsLanguageNote: StateFlow<String?> = _ttsLanguageNote.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private var onFinalTranscript: ((String) -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    _isTtsReady.value = true
                    applyTtsConfig()
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

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                        }
                    })
                } else {
                    _isTtsReady.value = false
                    _ttsLanguageNote.value = "Text-to-speech engine unavailable on this device."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech", e)
            _isTtsReady.value = false
        }
    }

    fun updateSettings(language: JarvisLanguage, rate: Float, pitch: Float) {
        currentLanguage = language
        speechRate = rate.coerceIn(0.5f, 2.0f)
        speechPitch = pitch.coerceIn(0.5f, 2.0f)
        if (_isTtsReady.value) {
            applyTtsConfig()
        }
    }

    private fun applyTtsConfig() {
        val engine = tts ?: return
        engine.setSpeechRate(speechRate)
        engine.setPitch(speechPitch)
        val locale = Locale.forLanguageTag(currentLanguage.localeTag)
        val res = engine.setLanguage(locale)
        _ttsLanguageNote.value = when (res) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                "Device TTS voice data for ${currentLanguage.englishLabel} may not be installed."
            }
            else -> null
        }
    }

    fun speak(text: String) {
        val clean = text
            .replace(Regex("[*_`#>]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isEmpty()) return
        val engine = tts
        if (engine == null || !_isTtsReady.value) {
            _voiceError.value = "Voice synthesis engine is not ready yet."
            return
        }
        applyTtsConfig()
        engine.speak(clean.take(3900), TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun buildRecognizerIntent(language: JarvisLanguage): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Jarvis (${language.englishLabel})")
        }

    fun canLaunchVoicePrompt(language: JarvisLanguage = currentLanguage): Boolean =
        buildRecognizerIntent(language).resolveActivity(context.packageManager) != null

    fun startListening(language: JarvisLanguage, onResult: (String) -> Unit) {
        _voiceError.value = null
        if (!hasMicrophonePermission()) {
            _voiceError.value = "Microphone permission is required for voice-to-text."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _isSpeechAvailable.value = false
            _voiceError.value = "Speech recognition service is not installed on this device."
            return
        }
        _isSpeechAvailable.value = true
        stopSpeaking()
        onFinalTranscript = onResult
        _partialTranscript.value = ""

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    _rmsLevel.value = 0.15f
                }

                override fun onBeginningOfSpeech() {
                    _isListening.value = true
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                    _rmsLevel.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    _rmsLevel.value = 0.1f
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    _voiceError.value = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio recording error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech recognition requires an active network connection."
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please try speaking again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out before speech was detected."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "${language.englishLabel} speech recognition is unavailable on this device."
                        SpeechRecognizer.ERROR_CLIENT ->
                            "Speech recognizer client error. Please try again."
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
                            "Too many speech requests. Please wait a moment and try again."
                        else -> "Voice recognition failed (code $error)."
                    }
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    _partialTranscript.value = ""
                    if (best.isNotEmpty()) {
                        onFinalTranscript?.invoke(best)
                    } else {
                        _voiceError.value = "Could not transcribe audio. Please try again."
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

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            recognizer.startListening(buildRecognizerIntent(language))
            _isListening.value = true
        } catch (e: Exception) {
            Log.e(TAG, "SpeechRecognizer error", e)
            _isListening.value = false
            _voiceError.value = e.localizedMessage ?: "Failed to start voice input."
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        _isListening.value = false
        _rmsLevel.value = 0f
    }

    fun cancelListening() {
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        _isListening.value = false
        _rmsLevel.value = 0f
        _partialTranscript.value = ""
    }

    fun clearVoiceError() {
        _voiceError.value = null
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {
        }
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }
}
