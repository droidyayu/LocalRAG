package com.ayushig.localrag.demo.ui.chat

import androidx.compose.runtime.Immutable
import com.ayushig.localrag.demo.domain.model.ChatMessage
import com.ayushig.localrag.demo.domain.model.EngineState
import com.ayushig.localrag.demo.domain.model.GenerationSettings

@Immutable
data class ChatUiState(
    val engineState: EngineState = EngineState.Idle,
    val modelPresent: Boolean = true,
    val expectedModelPath: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    val loadingElapsedSeconds: Int = 0,
    val settings: GenerationSettings = GenerationSettings(),
    val showDebugPanel: Boolean = false,
    val libraryVersion: String = "",
    val errorMessage: String? = null,
) {
    /**
     * Account questions are answered from repository data, so sending does not wait for the
     * model. Only general chat needs a loaded engine.
     */
    val canSend: Boolean
        get() = input.isNotBlank() && !isGenerating
}
