package com.ayushig.localrag.demo.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayushig.localrag.demo.BuildConfig
import com.ayushig.localrag.demo.domain.model.ChatMessage
import com.ayushig.localrag.demo.domain.model.MessageSource
import com.ayushig.localrag.demo.domain.model.Role
import com.ayushig.localrag.android.AgentConfig
import com.ayushig.localrag.android.AgentOutcome
import com.ayushig.localrag.android.LocalRag
import com.ayushig.localrag.android.LocalRagState
import com.ayushig.localrag.demo.data.ModelFileLocator
import com.ayushig.localrag.demo.data.assistant.AssistantToolDefinitions
import com.ayushig.localrag.demo.domain.assistant.NoInformationFallback
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val modelFileLocator: ModelFileLocator,
    private val agentConfig: AgentConfig,
    private val localRag: LocalRag,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            libraryVersion = BuildConfig.LITERTLM_VERSION,
            modelPresent = modelFileLocator.isPresent(),
            expectedModelPath = modelFileLocator.absolutePath,
        ),
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Only one generation may be in flight; a new send cancels the previous one. */
    private var generationJob: Job? = null
    private var loadTimerJob: Job? = null

    init {
        // The engine is the only answer path, so it has to be loading from the moment the
        // screen exists.
        viewModelScope.launch { localRag.initialize() }
        viewModelScope.launch {
            localRag.state.collect { state ->
                _uiState.value = _uiState.value.copy(engineState = state)
                if (state !is LocalRagState.Loading) loadTimerJob?.cancel()
            }
        }
        loadEngine()
    }

    /** Lazy engine load, triggered by entering the chat screen rather than by Application. */
    fun loadEngine() {
        val present = modelFileLocator.isPresent()
        _uiState.value = _uiState.value.copy(
            modelPresent = present,
            expectedModelPath = modelFileLocator.absolutePath,
        )
        if (!present) return
        if (_uiState.value.engineState is LocalRagState.Loading) return

        startLoadTimer()
        viewModelScope.launch { localRag.initialize() }
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
        )

        generationJob = viewModelScope.launch {
            if (modelCanGenerate()) {
                answerWithAgent(prompt, replyId)
            } else {
                // No engine, no answers: the rules tier is gone, so say exactly that.
                answerNoInformation(replyId)
            }
        }
    }

    private fun modelCanGenerate(): Boolean =
        (localRag.state.value as? LocalRagState.Ready)?.canGenerate == true

    /**
     * The full assistant: the SDK plans tool calls against the app's functions, executes them,
     * and gates the final text against the observations. Anything the loop cannot resolve
     * honestly becomes the fixed fallback, never a guess.
     */
    private suspend fun answerWithAgent(prompt: String, replyId: String) {
        Log.d(TAG, "send \"$prompt\" via agent")
        when (val answer = localRag.runAgent(prompt, agentConfig)) {
            is AgentOutcome.Final -> {
                val source = when {
                    answer.sources.isNotEmpty() -> MessageSource.DOCUMENTATION
                    answer.usedTools.any { it != AssistantToolDefinitions.SEARCH_DOCUMENTATION } ->
                        MessageSource.PORTFOLIO_DATA
                    else -> MessageSource.MODEL
                }
                updateMessage(replyId) { it.copy(source = source, sources = answer.sources) }
                streamWords(replyId, answer.text)
            }

            AgentOutcome.Refused ->
                streamWords(replyId, NoInformationFallback.ADVICE_REFUSAL)

            AgentOutcome.Unresolved -> answerNoInformation(replyId)
        }
    }

    private suspend fun answerNoInformation(replyId: String) {
        updateMessage(replyId) { it.copy(source = MessageSource.NO_INFORMATION) }
        streamWords(replyId, NoInformationFallback.TEXT)
    }

    private suspend fun streamWords(replyId: String, text: String) {
        text.split(" ").forEachIndexed { index, word ->
            updateMessage(replyId) { it.copy(text = if (index == 0) word else it.text + " " + word) }
            delay(PORTFOLIO_WORD_DELAY_MS)
        }
        finishStreaming(replyId)
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
        // Stateless engine: clearing the transcript is all there is to do. No conversation
        // survives a turn, so nothing can leak into the next answer.
        _uiState.value = _uiState.value.copy(messages = emptyList(), isGenerating = false)
    }

    fun onReloadEngine() {
        generationJob?.cancel()
        localRag.release()
        loadEngine()
    }

    private companion object {
        const val TAG = "LocalRagChat"
        const val PORTFOLIO_WORD_DELAY_MS = 35L
    }

    override fun onCleared() {
        generationJob?.cancel()
        localRag.release()
        super.onCleared()
    }
}
