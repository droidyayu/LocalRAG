package com.ayushig.localrag.ui.portfolio.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ayushig.localrag.core.Formatters
import com.ayushig.localrag.data.portfolio.PortfolioCalculations
import com.ayushig.localrag.domain.model.portfolio.Direction
import com.ayushig.localrag.domain.model.portfolio.LeveragedPosition
import com.ayushig.localrag.domain.model.portfolio.MetalHolding
import com.ayushig.localrag.domain.model.portfolio.RiskLevel
import com.ayushig.localrag.domain.model.portfolio.StockHolding
import com.ayushig.localrag.domain.model.portfolio.WealthInvestment
import com.ayushig.localrag.ui.portfolio.components.SignedAmount
import com.ayushig.localrag.ui.portfolio.components.SignedPercent
import com.ayushig.localrag.ui.theme.LocalFinanceColors

@Composable
fun LeveragedRow(position: LeveragedPosition, modifier: Modifier = Modifier) {
    HoldingCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(position.symbol, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    DirectionBadge(position.direction, modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    text = position.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SignedAmount(
                amount = position.unrealisedPnl,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        DetailGrid(
            "Units" to Formatters.quantity(position.units),
            "Leverage" to Formatters.leverage(position.leverage),
            "Entry" to Formatters.price(position.entryPrice, decimals = priceDecimals(position)),
            "Current" to Formatters.price(position.currentPrice, decimals = priceDecimals(position)),
            "Margin used" to Formatters.currency(position.marginUsed),
            "Value" to Formatters.currency(PortfolioCalculations.positionValue(position)),
        )
    }
}

/** FX quotes need four decimals; index and commodity prices read better with two. */
private fun priceDecimals(position: LeveragedPosition): Int =
    if (position.entryPrice < 10.0) 4 else 2

@Composable
fun MetalRow(holding: MetalHolding, modifier: Modifier = Modifier) {
    val value = PortfolioCalculations.metalValue(holding)
    val cost = PortfolioCalculations.metalCost(holding)
    HoldingCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(holding.metal.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    text = "${Formatters.grams(holding.grams)} · ${holding.purity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SignedAmount(amount = value - cost, style = MaterialTheme.typography.titleSmall)
        }
        DetailGrid(
            "Avg buy / g" to Formatters.currency(holding.avgBuyPricePerGram),
            "Current / g" to Formatters.currency(holding.currentPricePerGram),
            "Value" to Formatters.currency(value),
            "Storage" to holding.storageLocation,
        )
    }
}

@Composable
fun WealthRow(investment: WealthInvestment, modifier: Modifier = Modifier) {
    val pnl = investment.currentValue - investment.amountInvested
    HoldingCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(investment.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                RiskBadge(investment.riskLevel, modifier = Modifier.padding(top = 4.dp))
            }
            SignedAmount(amount = pnl, style = MaterialTheme.typography.titleSmall)
        }
        DetailGrid(
            "Invested" to Formatters.currency(investment.amountInvested),
            "Current value" to Formatters.currency(investment.currentValue),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                text = "Annualised return  ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SignedPercent(
                value = investment.annualisedReturnPct,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun StockRow(holding: StockHolding, modifier: Modifier = Modifier) {
    val value = PortfolioCalculations.stockValue(holding)
    val cost = PortfolioCalculations.stockCost(holding)
    val pnl = value - cost
    HoldingCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(holding.symbol, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    text = holding.companyName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                SignedAmount(amount = pnl, style = MaterialTheme.typography.titleSmall)
                Row {
                    Text(
                        text = "today ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SignedPercent(
                        value = holding.dayChangePct,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        DetailGrid(
            "Quantity" to Formatters.shares(holding.quantity),
            "Avg cost" to Formatters.currency(holding.avgCost),
            "Last price" to Formatters.currency(holding.lastPrice),
            "Value" to Formatters.currency(value),
        )
    }
}

@Composable
private fun HoldingCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

/** Label-and-value pairs in two columns, so rows stay readable at phone width. */
@Composable
private fun DetailGrid(vararg pairs: Pair<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        pairs.toList().chunked(2).forEach { rowPairs ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowPairs.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = value, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (rowPairs.size == 1) Column(modifier = Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun DirectionBadge(direction: Direction, modifier: Modifier = Modifier) {
    val finance = LocalFinanceColors.current
    val container = when (direction) {
        Direction.BUY -> finance.positiveContainer
        Direction.SELL -> finance.negativeContainer
    }
    val content = when (direction) {
        Direction.BUY -> finance.positive
        Direction.SELL -> finance.negative
    }
    Badge(text = direction.name, container = container, content = content, modifier = modifier)
}

@Composable
private fun RiskBadge(risk: RiskLevel, modifier: Modifier = Modifier) {
    Badge(
        text = "${risk.displayName} risk",
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    )
}

@Composable
private fun Badge(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
