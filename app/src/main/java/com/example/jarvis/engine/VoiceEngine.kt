package com.example.jarvis.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

class VoiceEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCommandRecognized: (String, Boolean) -> Unit,
    private val onWakeWordTriggered: () -> Unit,
    private val onSpeechError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _waveformBars = MutableStateFlow(List(24) { 0.12f })
    val waveformBars: StateFlow<List<Float>> = _waveformBars.asStateFlow()

    private var speechRate: Float = 1.05f
    private var speechPitch: Float = 0.98f
    private var voiceEnabled: Boolean = true
    private var wakeWordLoopEnabled: Boolean = false
    private var wakeWordLoopDelayMs: Long = 1800L
    private var speakingWaveformJob: Job? = null

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val localeIn = Locale("en", "IN")
            val result = tts?.setLanguage(localeIn)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(speechPitch)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    startSpeakingWaveformAnimation()
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    stopSpeakingWaveformAnimation()
                    if (wakeWordLoopEnabled) {
                        scope.launch(Dispatchers.Main) {
                            delay(wakeWordLoopDelayMs)
                            if (wakeWordLoopEnabled && !_isListening.value && !_isSpeaking.value) {
                                startListening(isWakeWordStandby = true)
                            }
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    stopSpeakingWaveformAnimation()
                }
            })
            isTtsReady = true
        }
    }

    fun updateVoicePreferences(
        enabled: Boolean,
        rate: Float,
        pitch: Float,
        wakeWordActive: Boolean,
        wakeIntervalMs: Long
    ) {
        voiceEnabled = enabled
        speechRate = rate.coerceIn(0.6f, 1.8f)
        speechPitch = pitch.coerceIn(0.6f, 1.6f)
        wakeWordLoopEnabled = wakeWordActive
        wakeWordLoopDelayMs = wakeIntervalMs
        if (isTtsReady) {
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(speechPitch)
        }
    }

    fun startListening(isWakeWordStandby: Boolean = false) {
        scope.launch(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onSpeechError("Speech recognition service is not available on this device/emulator. You can type or tap any voice command below.")
                return@launch
            }
            stopSpeaking()
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createRecognitionListener(isWakeWordStandby))
                    }
                } else {
                    speechRecognizer?.setRecognitionListener(createRecognitionListener(isWakeWordStandby))
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                _partialTranscript.value = if (isWakeWordStandby) "Say \"Hey JARVIS\"..." else "Listening..."
                _isListening.value = true
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                _isListening.value = false
                onSpeechError("Microphone recognizer could not start: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun stopListening() {
        scope.launch(Dispatchers.Main) {
            runCatching { speechRecognizer?.stopListening() }
            _isListening.value = false
            resetWaveform()
        }
    }

    fun speak(text: String) {
        if (!voiceEnabled || text.isBlank()) return
        scope.launch(Dispatchers.Main) {
            if (!isTtsReady) return@launch
            // Switch TTS locale dynamically if Devanagari script is present
            val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
            if (hasDevanagari) {
                tts?.setLanguage(Locale("hi", "IN"))
            } else {
                tts?.setLanguage(Locale("en", "IN"))
            }
            val params = Bundle()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "jarvis_utt_${System.currentTimeMillis()}")
        }
    }

    fun stopSpeaking() {
        runCatching {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        }
        _isSpeaking.value = false
        stopSpeakingWaveformAnimation()
    }

    private fun createRecognitionListener(isWakeWordStandby: Boolean): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
                val updated = List(24) { idx ->
                    val wave = abs(sin((System.currentTimeMillis() / 90.0) + idx * 0.45)).toFloat()
                    (normalized * (0.35f + 0.65f * wave)).coerceIn(0.08f, 1.0f)
                }
                _waveformBars.value = updated
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                resetWaveform()
            }

            override fun onError(error: Int) {
                _isListening.value = false
                resetWaveform()
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try speaking again or tap a command."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice commands."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error in speech recognizer."
                    else -> "Voice recognition paused."
                }
                if (!isWakeWordStandby && error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    onSpeechError(msg)
                } else if (wakeWordLoopEnabled) {
                    scope.launch(Dispatchers.Main) {
                        delay(wakeWordLoopDelayMs)
                        if (wakeWordLoopEnabled && !_isListening.value && !_isSpeaking.value) {
                            startListening(isWakeWordStandby = true)
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                resetWaveform()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()?.trim() ?: ""
                _partialTranscript.value = spoken
                if (spoken.isNotBlank()) {
                    handleTranscriptResult(spoken, isWakeWordStandby)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!partial.isNullOrBlank()) {
                    _partialTranscript.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun handleTranscriptResult(rawText: String, isWakeWordStandby: Boolean) {
        val lower = rawText.lowercase()
        val wakePrefixes = listOf("hey jarvis", "hi jarvis", "hello jarvis", "ok jarvis", "jarvis", "हे जार्विस", "जार्विस")
        val matchedPrefix = wakePrefixes.firstOrNull { lower.startsWith(it) }

        if (matchedPrefix != null) {
            val remainder = rawText.substring(matchedPrefix.length).trim().removePrefix(",").trim()
            if (remainder.isBlank()) {
                onWakeWordTriggered()
                speak("Yes? Listening for your command.")
                scope.launch(Dispatchers.Main) {
                    delay(1100L)
                    startListening(isWakeWordStandby = false)
                }
            } else {
                onCommandRecognized(remainder, true)
            }
        } else if (!isWakeWordStandby) {
            onCommandRecognized(rawText, false)
        } else {
            // Even in standby, if user spoke a clear command while HUD is open, process it
            onCommandRecognized(rawText, false)
        }
    }

    private fun startSpeakingWaveformAnimation() {
        speakingWaveformJob?.cancel()
        speakingWaveformJob = scope.launch(Dispatchers.Main) {
            var tick = 0
            while (_isSpeaking.value) {
                _waveformBars.value = List(24) { i ->
                    val v = abs(sin((tick * 0.35) + (i * 0.5))).toFloat()
                    (0.18f + 0.72f * v).coerceIn(0.12f, 0.95f)
                }
                tick++
                delay(65L)
            }
            resetWaveform()
        }
    }

    private fun stopSpeakingWaveformAnimation() {
        speakingWaveformJob?.cancel()
        resetWaveform()
    }

    private fun resetWaveform() {
        _waveformBars.value = List(24) { 0.12f }
    }

    fun shutdown() {
        runCatching {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        }
    }
}
