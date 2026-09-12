package com.ayushig.localrag.ui.portfolio.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayushig.localrag.domain.usecase.portfolio.GetMarginStatusUseCase
import com.ayushig.localrag.domain.usecase.portfolio.GetPortfolioSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PortfolioHomeViewModel @Inject constructor(
    private val getPortfolioSummary: GetPortfolioSummaryUseCase,
    private val getMarginStatus: GetMarginStatusUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PortfolioHomeUiState>(PortfolioHomeUiState.Loading)
    val uiState: StateFlow<PortfolioHomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        val current = _uiState.value
        // Keep the content on screen while refreshing; only a cold load shows skeletons.
        if (current is PortfolioHomeUiState.Content) {
            _uiState.value = current.copy(isRefreshing = true)
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching {
                // Independent reads, so pay 300 ms once rather than twice.
                coroutineScope {
                    val summary = async { getPortfolioSummary() }
                    val margin = async { getMarginStatus() }
                    summary.await() to margin.await()
                }
            }
                .onSuccess { (summary, margin) ->
                    _uiState.value = PortfolioHomeUiState.Content(summary, margin)
                }
                .onFailure { throwable ->
                    _uiState.value = PortfolioHomeUiState.Error(
                        throwable.message ?: "Could not load your portfolio."
                    )
                }
        }
    }
}
