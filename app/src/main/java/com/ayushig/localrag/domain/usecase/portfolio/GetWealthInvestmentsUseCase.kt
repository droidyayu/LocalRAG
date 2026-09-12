package com.ayushig.localrag.domain.usecase.portfolio


import com.ayushig.localrag.domain.repository.PortfolioRepository
import javax.inject.Inject

class GetWealthInvestmentsUseCase @Inject constructor(
    private val repository: PortfolioRepository,
) {
    suspend operator fun invoke() = repository.getWealthInvestments()
}
