package com.ayushig.localrag.domain.usecase.portfolio


import com.ayushig.localrag.domain.repository.PortfolioRepository
import javax.inject.Inject

class GetMetalHoldingsUseCase @Inject constructor(
    private val repository: PortfolioRepository,
) {
    suspend operator fun invoke() = repository.getMetalHoldings()
}
