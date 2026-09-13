package com.ayushig.localrag.demo.domain.usecase.portfolio


import com.ayushig.localrag.demo.domain.repository.PortfolioRepository
import javax.inject.Inject

class GetMetalHoldingsUseCase @Inject constructor(
    private val repository: PortfolioRepository,
) {
    suspend operator fun invoke() = repository.getMetalHoldings()
}
