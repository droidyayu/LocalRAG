package com.ayushig.localrag.domain.usecase.portfolio

import com.ayushig.localrag.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.domain.repository.PortfolioRepository
import javax.inject.Inject

class GetCategorySummaryUseCase @Inject constructor(
    private val repository: PortfolioRepository,
) {
    suspend operator fun invoke(category: AssetCategory) = repository.getCategorySummary(category)
}
