package com.ayushig.localrag.domain.usecase.portfolio


import com.ayushig.localrag.domain.repository.PortfolioRepository
import javax.inject.Inject

class FindHoldingBySymbolUseCase @Inject constructor(
    private val repository: PortfolioRepository,
) {
    suspend operator fun invoke(query: String) = repository.findHoldingBySymbol(query)
}
