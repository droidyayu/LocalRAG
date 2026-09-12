package com.ayushig.localrag.ui.portfolio.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayushig.localrag.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.ui.portfolio.components.AllocationBar
import com.ayushig.localrag.ui.portfolio.components.BalanceStrip
import com.ayushig.localrag.ui.portfolio.components.CategoryCard
import com.ayushig.localrag.ui.portfolio.components.PortfolioHomeSkeleton
import com.ayushig.localrag.ui.portfolio.components.ValueHeader

@Composable
fun PortfolioHomeRoute(
    onCategoryClick: (AssetCategory) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortfolioHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PortfolioHomeScreen(
        uiState = uiState,
        onCategoryClick = onCategoryClick,
        onRefresh = viewModel::refresh,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioHomeScreen(
    uiState: PortfolioHomeUiState,
    onCategoryClick: (AssetCategory) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = uiState is PortfolioHomeUiState.Content && uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
    ) {
        when (uiState) {
            PortfolioHomeUiState.Loading -> PortfolioHomeSkeleton()

            is PortfolioHomeUiState.Error -> ErrorState(uiState.message, onRefresh)

            is PortfolioHomeUiState.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ValueHeader(
                        totalValue = uiState.summary.totalValue,
                        totalPnl = uiState.summary.totalPnl,
                        totalPnlPct = uiState.summary.totalPnlPct,
                        asOf = uiState.summary.asOf,
                    )
                }
                item {
                    BalanceStrip(
                        availableBalance = uiState.summary.availableBalance,
                        equity = uiState.margin.equity,
                        freeMargin = uiState.margin.freeMargin,
                    )
                }
                item { AllocationBar(categories = uiState.summary.categories) }
                items(uiState.summary.categories, key = { it.category.name }) { summary ->
                    CategoryCard(
                        summary = summary,
                        onClick = { onCategoryClick(summary.category) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}
