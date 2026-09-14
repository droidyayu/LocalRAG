package com.ayushig.localrag.demo.ui.chat

import androidx.compose.runtime.Immutable
import com.ayushig.localrag.android.LocalRagState
import com.ayushig.localrag.demo.domain.model.ChatMessage

@Immutable
data class ChatUiState(
    val engineState: LocalRagState = LocalRagState.Idle,
    val modelPresent: Boolean = true,
    val expectedModelPath: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    /** What the running turn is doing right now; null when nothing runs. */
    val activeActivity: String? = null,
    val loadingElapsedSeconds: Int = 0,
    val libraryVersion: String = "",
) {
    /**
     * Sending needs a ready engine: the agent is the only answer path, so a question asked
     * while loading could only be met with the fixed fallback.
     */
    val canSend: Boolean
        get() = input.isNotBlank() && !isGenerating && engineState is LocalRagState.Ready
}
