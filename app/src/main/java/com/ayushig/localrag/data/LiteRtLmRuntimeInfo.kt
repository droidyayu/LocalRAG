package com.ayushig.localrag.data

import com.google.ai.edge.litertlm.Backend
import com.ayushig.localrag.domain.model.LlmBackend

/**
 * Maps the domain backend choice onto the LiteRT-LM type. Kept here so no LiteRT-LM type ever
 * leaves the data layer, and so Phase 1 already proves the AAR's Kotlin metadata compiles
 * against this project's compiler.
 */
internal fun LlmBackend.toLiteRtBackend(): Backend = when (this) {
    LlmBackend.CPU -> Backend.CPU()
    LlmBackend.GPU -> Backend.GPU()
}
