package com.ayushig.localrag.demo.domain.usecase

import com.ayushig.localrag.demo.domain.model.GenerationChunk
import com.ayushig.localrag.demo.domain.repository.LlmRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GenerateReplyUseCase @Inject constructor(
    private val repository: LlmRepository,
) {
    operator fun invoke(prompt: String): Flow<GenerationChunk> = repository.generate(prompt)
}
