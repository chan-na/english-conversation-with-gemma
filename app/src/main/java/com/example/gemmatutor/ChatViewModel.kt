package com.example.gemmatutor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.launch
import java.util.Locale

class ChatViewModel(
    context: Context,
    private val inferenceModel: InferenceModel
) : ViewModel() {

    // `GemmaUiState()` is optimized for the Gemma model.
    // Replace `GemmaUiState` with `ChatUiState()` if you're using a different model
    private val _uiState: MutableStateFlow<GemmaUiState> = MutableStateFlow(GemmaUiState())
    val uiState: StateFlow<UiState> =
        _uiState.asStateFlow()

    private val _isSpeechRecognitionEnabled: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val isSpeechRecognitionEnabled: StateFlow<Boolean> =
        _isSpeechRecognitionEnabled.asStateFlow()

    private val textToSpeechOnInitListener = TextToSpeech.OnInitListener { status ->
        if (status != TextToSpeech.ERROR) {
            textToSpeech.setLanguage(Locale.ENGLISH)
            setSpeechRecognitionEnabled(true)
        } else {
            assert(false) {
                "TTS initialization fail"
            }
        }
    }

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("RecognitionListener", "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d("RecognitionListener", "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // 너무 많이 호출되어 주석처리해 놓음
            // Log.d("RecognitionListener", "onRmsChanged(rmsdB=$rmsdB)")
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            Log.d("RecognitionListener", "onBufferReceived")
        }

        override fun onEndOfSpeech() {
            Log.d("RecognitionListener", "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            setSpeechRecognitionEnabled(true)

            val errorStr = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "ERROR_CANNOT_CHECK_SUPPORT" // API 33
                SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> "ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS" // API 34
                SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED" // API 31
                SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED"   // API 31
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"   // API 31
                else -> "unknown error: $error"
            }
            Log.d("RecognitionListener", "onError(error=$errorStr)")
        }

        override fun onResults(results: Bundle?) {
            if (results == null) {
                setSpeechRecognitionEnabled(true)
                return
            }

            val recognitionResults =
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)!!
            val confidenceScores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)!!

            val maxConfidenceIndex = confidenceScores.withIndex().maxBy { it.value }
            val maxConfidenceResult = recognitionResults[maxConfidenceIndex.index]

            sendMessage(maxConfidenceResult)

            Log.d("RecognitionListener", "recognition results: $recognitionResults")
            Log.d(
                "RecognitionListener",
                "confidence scores: ${confidenceScores.map { it.toString() }}"
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (partialResults == null) {
                return
            }

            val partialSpeeches =
                partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            Log.d("RecognitionListener", "onPartialResults: $partialSpeeches")
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d("RecognitionListener", "onEvent")
        }
    }

    private val textToSpeech: TextToSpeech = TextToSpeech(context, textToSpeechOnInitListener)
    private val speechRecognizer: SpeechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(context)

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer.setRecognitionListener(recognitionListener)
        } else {
            error("Speech recognizer is not available")
        }
    }

    fun sendMessage(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.addMessage(userMessage, USER_PREFIX)
            var currentMessageId: String? = _uiState.value.createLoadingMessage()
            try {
                val fullPrompt = _uiState.value.fullPrompt
                inferenceModel.generateResponseAsync(fullPrompt)
                inferenceModel.partialResults
                    .collectIndexed { index, (partialResult, done) ->
                        currentMessageId?.let { id ->
                            if (index == 0) {
                                _uiState.value.appendFirstMessage(id, partialResult)
                            } else {
                                _uiState.value.appendMessage(id, partialResult, done)
                            }
                            if (done) {
                                val message = _uiState.value.getMessage(id)

                                textToSpeech.speak(
                                    message,
                                    TextToSpeech.QUEUE_ADD,
                                    null,
                                    "utteranceId"
                                )

                                currentMessageId = null
                                // Re-enable text input
                                setSpeechRecognitionEnabled(true)
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.value.addMessage(e.localizedMessage ?: "Unknown Error", MODEL_PREFIX)
                setSpeechRecognitionEnabled(true)
            }
        }
    }

    fun startListening() {
        setSpeechRecognitionEnabled(false)
        speechRecognizer.startListening(recognizerIntent)
    }

    private fun setSpeechRecognitionEnabled(isEnabled: Boolean) {
        _isSpeechRecognitionEnabled.value = isEnabled
    }

    override fun onCleared() {
        super.onCleared()

        textToSpeech.shutdown()
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val inferenceModel = InferenceModel.getInstance(context)

                return ChatViewModel(
                    context = context,
                    inferenceModel = inferenceModel
                ) as T
            }
        }
    }
}
