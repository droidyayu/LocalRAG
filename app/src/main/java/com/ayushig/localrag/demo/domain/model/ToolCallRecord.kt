package com.ayushig.localrag.demo.domain.model

/**
 * One function call from an assistant turn, as the model planned it and the app executed it.
 * Shown in the turn's expandable details so a grounded answer can be traced to the exact
 * calls behind it. A call with [finished] false never returned — the turn was cancelled or
 * the tool failed, and the reply says so instead of guessing.
 */
data class ToolCallRecord(
    val name: String,
    val args: Map<String, String> = emptyMap(),
    val resultChars: Int = 0,
    val sources: List<String> = emptyList(),
    val finished: Boolean = false,
)
