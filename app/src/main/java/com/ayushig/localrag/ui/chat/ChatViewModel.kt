package com.ayushig.localrag.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayushig.localrag.BuildConfig
import com.ayushig.localrag.domain.model.ChatMessage
import com.ayushig.localrag.domain.model.MessageSource
import com.ayushig.localrag.domain.model.assistant.AnswerResult
import com.ayushig.localrag.domain.model.EngineState
import com.ayushig.localrag.domain.model.GenerationChunk
import com.ayushig.localrag.domain.model.GenerationSettings
import com.ayushig.localrag.domain.model.Role
import com.ayushig.localrag.domain.repository.LlmRepository
import com.ayushig.localrag.domain.usecase.ApplySettingsUseCase
import com.ayushig.localrag.domain.usecase.assistant.AnswerPortfolioQueryUseCase
import com.ayushig.localrag.domain.usecase.GenerateReplyUseCase
import com.ayushig.localrag.domain.usecase.InitializeEngineUseCase
import com.ayushig.localrag.domain.usecase.ResetConversationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: LlmRepository,
    private val initializeEngine: InitializeEngineUseCase,
    private val generateReply: GenerateReplyUseCase,
    private val resetConversation: ResetConversationUseCase,
    private val applySettings: ApplySettingsUseCase,
    private val answerPortfolioQuery: AnswerPortfolioQueryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(libraryVersion = BuildConfig.LITERTLM_VERSION))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Only one generation may be in flight; a new send cancels the previous one. */
    private var generationJob: Job? = null
    private var loadTimerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.engineState.collect { state ->
                _uiState.value = _uiState.value.copy(engineState = state)
                if (state !is EngineState.Loading) loadTimerJob?.cancel()
            }
        }
        loadEngine()
    }

    /** Lazy engine load, triggered by entering the chat screen rather than by Application. */
    fun loadEngine() {
        val present = repository.isModelPresent()
        _uiState.value = _uiState.value.copy(
            modelPresent = present,
            expectedModelPath = repository.expectedModelPath,
        )
        if (!present) return
        if (_uiState.value.engineState is EngineState.Loading) return

        startLoadTimer()
        viewModelScope.launch { initializeEngine() }
    }

    private fun startLoadTimer() {
        loadTimerJob?.cancel()
        _uiState.value = _uiState.value.copy(loadingElapsedSeconds = 0)
        loadTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _uiState.value =
                    _uiState.value.copy(loadingElapsedSeconds = _uiState.value.loadingElapsedSeconds + 1)
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.value = _uiState.value.copy(input = value)
    }

    fun onSend() {
        val prompt = _uiState.value.input.trim()
        if (prompt.isEmpty()) return

        generationJob?.cancel()

        val userMessage = ChatMessage(id = UUID.randomUUID().toString(), role = Role.USER, text = prompt)
        val replyId = UUID.randomUUID().toString()
        val placeholder =
            ChatMessage(id = replyId, role = Role.MODEL, text = "", isStreaming = true)

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage + placeholder,
            input = "",
            isGenerating = true,
            errorMessage = null,
        )

        generationJob = viewModelScope.launch {
            // Account questions are answered from repository data, never by the model. Only
            // what the rules cannot route falls through to generation.
            when (val answer = answerPortfolioQuery(prompt)) {
                is AnswerResult.Answer -> streamPortfolioText(replyId, answer.text)
                is AnswerResult.Refusal -> streamPortfolioText(replyId, answer.text)
                is AnswerResult.Failure -> streamPortfolioText(replyId, answer.text)
                AnswerResult.NoMatch -> generateWithModel(replyId, prompt)
            }
        }
    }

    private suspend fun generateWithModel(replyId: String, prompt: String) {
        // Collect off the main thread; only the resulting state hops back to Main.
        generateReply(prompt)
            .flowOn(Dispatchers.IO)
            .onCompletion { finishStreaming(replyId) }
            .collect { chunk -> applyChunk(replyId, chunk) }
    }

    /**
     * Templated answers are streamed a word at a time so they read like the model's replies.
     * The text is already complete before the first word appears; the delay is presentation only.
     */
    private suspend fun streamPortfolioText(replyId: String, text: String) {
        updateMessage(replyId) { it.copy(source = MessageSource.PORTFOLIO_DATA) }
        text.split(" ").forEachIndexed { index, word ->
            updateMessage(replyId) { it.copy(text = if (index == 0) word else it.text + " " + word) }
            delay(PORTFOLIO_WORD_DELAY_MS)
        }
        finishStreaming(replyId)
    }

    private fun applyChunk(replyId: String, chunk: GenerationChunk) {
        when (chunk) {
            is GenerationChunk.Token -> updateMessage(replyId) { it.copy(text = it.text + chunk.text) }
            is GenerationChunk.Done ->
                updateMessage(replyId) { it.copy(isStreaming = false, metrics = chunk.metrics) }
            is GenerationChunk.Error -> {
                updateMessage(replyId) { it.copy(isStreaming = false) }
                _uiState.value = _uiState.value.copy(errorMessage = chunk.message)
            }
        }
    }

    private fun finishStreaming(replyId: String) {
        updateMessage(replyId) { it.copy(isStreaming = false) }
        _uiState.value = _uiState.value.copy(isGenerating = false)
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { if (it.id == id) transform(it) else it },
        )
    }

    fun onStop() {
        generationJob?.cancel()
        generationJob = null
        _uiState.value = _uiState.value.copy(isGenerating = false)
    }

    fun onClearChat() {
        generationJob?.cancel()
        viewModelScope.launch {
            resetConversation()
            _uiState.value = _uiState.value.copy(messages = emptyList(), isGenerating = false)
        }
    }

    fun onReloadEngine() {
        generationJob?.cancel()
        repository.release()
        loadEngine()
    }

    fun onToggleDebugPanel() {
        _uiState.value = _uiState.value.copy(showDebugPanel = !_uiState.value.showDebugPanel)
    }

    fun onSettingsChange(settings: GenerationSettings) {
        _uiState.value = _uiState.value.copy(settings = settings)
        viewModelScope.launch { applySettings(settings) }
    }

    private companion object {
        const val PORTFOLIO_WORD_DELAY_MS = 35L
    }

    override fun onCleared() {
        generationJob?.cancel()
        repository.release()
        super.onCleared()
    }
}
