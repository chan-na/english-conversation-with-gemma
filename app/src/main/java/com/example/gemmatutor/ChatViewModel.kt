package com.example.gemmatutor

import android.content.Context
import android.speech.tts.TextToSpeech
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

    private val _textInputEnabled: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    val isTextInputEnabled: StateFlow<Boolean> =
        _textInputEnabled.asStateFlow()

    private val textToSpeechOnInitListener = TextToSpeech.OnInitListener { status ->
        if (status != TextToSpeech.ERROR) {
            textToSpeech.setLanguage(Locale.ENGLISH)
            setInputEnabled(true)
        } else {
            assert(false) {
                "TTS initialization fail"
            }
        }
    }

    private val textToSpeech: TextToSpeech = TextToSpeech(context, textToSpeechOnInitListener)


    fun sendMessage(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.addMessage(userMessage, USER_PREFIX)
            var currentMessageId: String? = _uiState.value.createLoadingMessage()
            setInputEnabled(false)
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
                                setInputEnabled(true)
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.value.addMessage(e.localizedMessage ?: "Unknown Error", MODEL_PREFIX)
                setInputEnabled(true)
            }
        }
    }

    private fun setInputEnabled(isEnabled: Boolean) {
        _textInputEnabled.value = isEnabled
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
