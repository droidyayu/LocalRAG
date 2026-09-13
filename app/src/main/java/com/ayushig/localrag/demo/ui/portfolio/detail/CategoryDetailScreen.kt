package com.ayushig.localrag.demo.ui.portfolio.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayushig.localrag.demo.R
import com.ayushig.localrag.demo.core.Formatters
import com.ayushig.localrag.demo.domain.model.portfolio.CategorySummary
import com.ayushig.localrag.demo.ui.portfolio.components.HoldingListSkeleton
import com.ayushig.localrag.demo.ui.portfolio.components.SignedAmountAndPercent

@Composable
fun CategoryDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    uiState: CategoryDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.category.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            is CategoryDetailUiState.Loading ->
                HoldingListSkeleton(modifier = Modifier.padding(innerPadding))

            is CategoryDetailUiState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(uiState.message, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onRetry) { Text("Try again") }
                }
            }

            is CategoryDetailUiState.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SummaryHeader(uiState.summary) }
                when (val holdings = uiState.holdings) {
                    is CategoryHoldings.Leveraged ->
                        items(holdings.positions, key = { it.symbol }) { LeveragedRow(it) }
                    is CategoryHoldings.Metals ->
                        items(holdings.holdings, key = { it.metal.name }) { MetalRow(it) }
                    is CategoryHoldings.Wealth ->
                        items(holdings.investments, key = { it.id }) { WealthRow(it) }
                    is CategoryHoldings.Stocks ->
                        items(holdings.holdings, key = { it.symbol }) { StockRow(it) }
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(summary: CategorySummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Current value",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Formatters.currency(summary.currentValue),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            SignedAmountAndPercent(
                amount = summary.totalPnl,
                percent = summary.pnlPct,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = if (summary.holdingCount == 1) "1 holding" else "${summary.holdingCount} holdings",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
