package com.ayushig.localrag.demo.domain.usecase.portfolio


import com.ayushig.localrag.demo.domain.repository.PortfolioRepository

class GetWealthInvestmentsUseCase constructor(
    private val repository: PortfolioRepository,
) {
    suspend operator fun invoke() = repository.getWealthInvestments()
}
