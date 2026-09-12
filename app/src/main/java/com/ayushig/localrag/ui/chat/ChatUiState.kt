package com.ayushig.localrag.ui.chat

import androidx.compose.runtime.Immutable
import com.ayushig.localrag.domain.model.ChatMessage
import com.ayushig.localrag.domain.model.EngineState
import com.ayushig.localrag.domain.model.GenerationSettings

@Immutable
data class ChatUiState(
    val engineState: EngineState = EngineState.Idle,
    val modelPresent: Boolean = true,
    val expectedModelPath: String = "",
    val pushCommand: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    val loadingElapsedSeconds: Int = 0,
    val settings: GenerationSettings = GenerationSettings(),
    val showDebugPanel: Boolean = false,
    val libraryVersion: String = "",
    val errorMessage: String? = null,
) {
    val canSend: Boolean
        get() = input.isNotBlank() && !isGenerating && engineState is EngineState.Ready
}
