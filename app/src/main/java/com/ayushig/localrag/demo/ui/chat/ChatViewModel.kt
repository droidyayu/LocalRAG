package com.ayushig.localrag.demo.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayushig.localrag.demo.BuildConfig
import com.ayushig.localrag.demo.domain.model.ChatMessage
import com.ayushig.localrag.demo.domain.model.MessageSource
import com.ayushig.localrag.demo.domain.model.Role
import com.ayushig.localrag.demo.domain.model.ToolCallRecord
import com.ayushig.localrag.android.AgentConfig
import com.ayushig.localrag.android.AgentEvent
import com.ayushig.localrag.android.AgentMessage
import com.ayushig.localrag.android.AgentOutcome
import com.ayushig.localrag.android.AgentRole
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
        sendPrompt(_uiState.value.input.trim())
    }

    /** Suggestion chips send directly without touching the input field. */
    fun onSuggestion(question: String) {
        sendPrompt(question)
    }

    private fun sendPrompt(prompt: String) {
        if (prompt.isEmpty()) return

        generationJob?.cancel()

        // The turn about to run sees everything before it: history is per-call data the
        // ViewModel assembles, never engine memory.
        val history = _uiState.value.messages
            .filter { !it.isStreaming && it.text.isNotBlank() }
            .takeLast(MAX_HISTORY_TURNS)
            .map {
                AgentMessage(
                    role = if (it.role == Role.USER) AgentRole.USER else AgentRole.MODEL,
                    text = it.text,
                )
            }

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
                answerWithAgent(prompt, replyId, history)
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
    private suspend fun answerWithAgent(
        prompt: String,
        replyId: String,
        history: List<AgentMessage>,
    ) {
        Log.d(TAG, "send \"$prompt\" via agent")
        // The calls accumulate here as the turn runs, so the placeholder bubble can show them
        // live and the finished message keeps them for its expandable details.
        val records = mutableListOf<ToolCallRecord>()
        val onEvent: (AgentEvent) -> Unit = { event -> handleAgentEvent(replyId, event, records) }
        when (val answer = localRag.runAgent(prompt, agentConfig, history, onEvent)) {
            is AgentOutcome.Final -> {
                val source = when {
                    answer.sources.isNotEmpty() -> MessageSource.DOCUMENTATION
                    answer.usedTools.any { it != AssistantToolDefinitions.SEARCH_DOCUMENTATION } ->
                        MessageSource.PORTFOLIO_DATA
                    else -> MessageSource.MODEL
                }
                updateMessage(replyId) {
                    it.copy(source = source, sources = answer.sources, tools = records.toList())
                }
                streamWords(replyId, answer.text)
            }

            AgentOutcome.Refused ->
                streamWords(replyId, NoInformationFallback.ADVICE_REFUSAL)

            AgentOutcome.Unresolved -> answerNoInformation(replyId)
        }
    }

    /**
     * Runs on whatever thread the engine calls back on; StateFlow updates are thread-safe and
     * the records list is touched only by this one turn.
     */
    private fun handleAgentEvent(
        replyId: String,
        event: AgentEvent,
        records: MutableList<ToolCallRecord>,
    ) {
        when (event) {
            is AgentEvent.CallingTool -> {
                records += ToolCallRecord(name = event.name, args = event.args)
                updateMessage(replyId) { it.copy(tools = records.toList()) }
            }

            is AgentEvent.ToolFinished -> {
                val index = records.indexOfLast { it.name == event.name && !it.finished }
                if (index >= 0) {
                    records[index] = records[index].copy(
                        resultChars = event.observationChars,
                        sources = event.sourceTitles,
                        finished = true,
                    )
                    updateMessage(replyId) { it.copy(tools = records.toList()) }
                }
            }

            AgentEvent.Thinking, AgentEvent.JudgingAnswer -> Unit
        }
        _uiState.value = _uiState.value.copy(activeActivity = event.statusText())
    }

    private suspend fun answerNoInformation(replyId: String) {
        updateMessage(replyId) { it.copy(source = MessageSource.NO_INFORMATION) }
        streamWords(replyId, NoInformationFallback.TEXT)
    }

    private suspend fun streamWords(replyId: String, text: String) {
        // The working card hands off to the bubble: no status lingers under arriving text.
        _uiState.value = _uiState.value.copy(activeActivity = null)
        text.split(" ").forEachIndexed { index, word ->
            updateMessage(replyId) { it.copy(text = if (index == 0) word else it.text + " " + word) }
            delay(PORTFOLIO_WORD_DELAY_MS)
        }
        finishStreaming(replyId)
    }

    private fun finishStreaming(replyId: String) {
        updateMessage(replyId) { it.copy(isStreaming = false) }
        _uiState.value = _uiState.value.copy(isGenerating = false, activeActivity = null)
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { if (it.id == id) transform(it) else it },
        )
    }

    fun onStop() {
        generationJob?.cancel()
        generationJob = null
        _uiState.value = _uiState.value.copy(isGenerating = false, activeActivity = null)
    }

    fun onClearChat() {
        generationJob?.cancel()
        // Stateless engine: clearing the transcript is all there is to do. No conversation
        // survives a turn, so nothing can leak into the next answer.
        _uiState.value =
            _uiState.value.copy(messages = emptyList(), isGenerating = false, activeActivity = null)
    }

    fun onReloadEngine() {
        generationJob?.cancel()
        localRag.release()
        loadEngine()
    }

    private companion object {
        const val TAG = "LocalRagChat"
        const val PORTFOLIO_WORD_DELAY_MS = 35L
        /** Recent turns per call; the SDK's char budget drops older ones first anyway. */
        const val MAX_HISTORY_TURNS = 6
    }

    override fun onCleared() {
        generationJob?.cancel()
        localRag.release()
        super.onCleared()
    }
}
