package com.ayushig.localrag.domain.usecase.portfolio


import com.ayushig.localrag.domain.repository.PortfolioRepository
import javax.inject.Inject

class GetLeveragedPositionsUseCase @Inject constructor(
    private val repository: PortfolioRepository,
) {
    suspend operator fun invoke() = repository.getLeveragedPositions()
}
