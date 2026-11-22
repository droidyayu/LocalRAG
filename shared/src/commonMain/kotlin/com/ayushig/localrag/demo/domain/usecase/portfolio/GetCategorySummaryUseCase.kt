package com.ayushig.localrag.demo.domain.usecase.portfolio

import com.ayushig.localrag.demo.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.demo.domain.repository.PortfolioRepository

class GetCategorySummaryUseCase constructor(
    private val repository: PortfolioRepository,
) {
    suspend operator fun invoke(category: AssetCategory) = repository.getCategorySummary(category)
}
