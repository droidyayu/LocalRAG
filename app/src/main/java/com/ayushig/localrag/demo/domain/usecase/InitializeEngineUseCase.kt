package com.ayushig.localrag.demo.domain.usecase

import com.ayushig.localrag.demo.domain.repository.LlmRepository
import javax.inject.Inject

class InitializeEngineUseCase @Inject constructor(
    private val repository: LlmRepository,
) {
    suspend operator fun invoke() = repository.initialize()
}
