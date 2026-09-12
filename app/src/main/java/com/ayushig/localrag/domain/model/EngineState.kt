package com.ayushig.localrag.domain.model

/** Lifecycle of the single process-wide inference engine. */
sealed interface EngineState {
    data object Idle : EngineState

    data object Loading : EngineState

    data class Ready(val loadTimeMs: Long) : EngineState

    data class Failed(val reason: String) : EngineState
}
