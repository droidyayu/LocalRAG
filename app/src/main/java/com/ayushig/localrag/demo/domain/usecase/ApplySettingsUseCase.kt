package com.ayushig.localrag.demo.domain.usecase

import com.ayushig.localrag.demo.domain.model.GenerationSettings
import com.ayushig.localrag.demo.domain.repository.LlmRepository
import javax.inject.Inject

class ApplySettingsUseCase @Inject constructor(
    private val repository: LlmRepository,
) {
    suspend operator fun invoke(settings: GenerationSettings) = repository.applySettings(settings)
}
