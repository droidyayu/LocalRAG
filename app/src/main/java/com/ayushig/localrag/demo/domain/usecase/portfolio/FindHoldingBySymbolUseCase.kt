package com.ayushig.localrag.demo.domain.usecase.portfolio


import com.ayushig.localrag.demo.domain.repository.PortfolioRepository
import javax.inject.Inject

class FindHoldingBySymbolUseCase @Inject constructor(
    private val repository: PortfolioRepository,
) {
    suspend operator fun invoke(query: String) = repository.findHoldingBySymbol(query)
}
