package com.ayushig.localrag.demo.data.assistant

import com.ayushig.localrag.android.ToolDefinition
import com.ayushig.localrag.android.ToolResult
import com.ayushig.localrag.demo.core.Formatters
import com.ayushig.localrag.demo.data.portfolio.PortfolioCalculations
import com.ayushig.localrag.demo.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.demo.domain.model.portfolio.HoldingSearchResult
import com.ayushig.localrag.demo.domain.repository.PortfolioRepository

/**
 * The host app's function definitions for the SDK agent loop.
 *
 * Every observation is rendered figures, never prose: the model writes the sentences, the data
 * writes the numbers, and the SDK output gate later checks that no other digits appear. Names,
 * descriptions, and arg specs are app-owned prompt copy; the SDK only plans and judges.
 */
class AssistantToolDefinitions constructor(
    private val repository: PortfolioRepository,
) {

    fun list(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = PORTFOLIO_SUMMARY,
            description = "Whole-account totals: value, profit and loss, available balance, currency.",
            argSpec = "(no arguments)",
            execute = { summary() },
        ),
        ToolDefinition(
            name = CATEGORY_SUMMARY,
            description = "One asset category: value, profit and loss, holding count.",
            argSpec = "category=METALS, STOCKS, WEALTH or LEVERAGED",
            execute = { args -> category(args) },
        ),
        ToolDefinition(
            name = MARGIN_STATUS,
            description = "Margin usage: used, free, level and equity.",
            argSpec = "(no arguments)",
            execute = { margin() },
        ),
        ToolDefinition(
            name = FIND_HOLDING,
            description = "One holding's figures by name or symbol.",
            argSpec = "query=<name or symbol>",
            execute = { args -> holding(args) },
        ),
    )

    private suspend fun summary(): ToolResult? = runCatching {
        val summary = repository.getPortfolioSummary()
        ToolResult(
            tool = PORTFOLIO_SUMMARY,
            text = lines(
                "total value" to Formatters.currency(summary.totalValue),
                "total profit and loss" to Formatters.signedCurrency(summary.totalPnl),
                "total profit and loss percent" to Formatters.signedPercent(summary.totalPnlPct),
                "available balance" to Formatters.currency(summary.availableBalance),
                "currency" to summary.currency,
                "as of" to Formatters.asOf(summary.asOf),
            ),
        )
    }.getOrNull()

    private suspend fun category(args: Map<String, String>): ToolResult? = runCatching {
        val category = args.parseCategory() ?: return null
        val summary = repository.getCategorySummary(category)
        ToolResult(
            tool = CATEGORY_SUMMARY,
            text = lines(
                "category" to category.displayName,
                "current value" to Formatters.currency(summary.currentValue),
                "profit and loss" to Formatters.signedCurrency(summary.totalPnl),
                "profit and loss percent" to Formatters.signedPercent(summary.pnlPct),
                "holdings" to holdingCount(summary.holdingCount),
                "as of" to Formatters.asOf(repository.getPortfolioSummary().asOf),
            ),
        )
    }.getOrNull()

    private suspend fun margin(): ToolResult? = runCatching {
        val margin = repository.getMarginStatus()
        ToolResult(
            tool = MARGIN_STATUS,
            text = lines(
                "margin used" to Formatters.currency(margin.marginUsed),
                "free margin" to Formatters.currency(margin.freeMargin),
                "margin level" to Formatters.percent(margin.marginLevelPct),
                "equity" to Formatters.currency(margin.equity),
                "as of" to Formatters.asOf(repository.getPortfolioSummary().asOf),
            ),
        )
    }.getOrNull()

    private suspend fun holding(args: Map<String, String>): ToolResult? = runCatching {
        val query = args["query"]?.trim().orEmpty()
        if (query.isEmpty()) return null
        val match = repository.findHoldingBySymbol(query) ?: return ToolResult(
            tool = FIND_HOLDING,
            text = "no holding matches \"$query\"",
        )
        ToolResult(tool = FIND_HOLDING, text = describe(match))
    }.getOrNull()

    private fun describe(match: HoldingSearchResult): String = when (match) {
        is HoldingSearchResult.Stock -> {
            val holding = match.holding
            val value = PortfolioCalculations.stockValue(holding)
            val cost = PortfolioCalculations.stockCost(holding)
            val pnl = value - cost
            lines(
                "company" to holding.companyName,
                "symbol" to holding.symbol,
                "quantity" to Formatters.shares(holding.quantity),
                "average cost" to Formatters.currency(holding.avgCost),
                "last price" to Formatters.currency(holding.lastPrice),
                "current value" to Formatters.currency(value),
                "profit and loss" to Formatters.signedCurrency(pnl),
                "profit and loss percent" to Formatters.signedPercent(
                    PortfolioCalculations.pnlPct(pnl, cost),
                ),
            )
        }

        is HoldingSearchResult.MetalHoldingMatch -> {
            val holding = match.holding
            val value = PortfolioCalculations.metalValue(holding)
            val cost = PortfolioCalculations.metalCost(holding)
            val pnl = value - cost
            lines(
                "metal" to holding.metal.displayName,
                "weight" to Formatters.grams(holding.grams),
                "average buy price per gram" to Formatters.currency(holding.avgBuyPricePerGram),
                "current price per gram" to Formatters.currency(holding.currentPricePerGram),
                "current value" to Formatters.currency(value),
                "profit and loss" to Formatters.signedCurrency(pnl),
                "profit and loss percent" to Formatters.signedPercent(
                    PortfolioCalculations.pnlPct(pnl, cost),
                ),
            )
        }

        is HoldingSearchResult.Leveraged -> {
            val position = match.position
            lines(
                "position" to position.displayName,
                "direction" to position.direction.name,
                "units" to Formatters.quantity(position.units),
                "leverage" to Formatters.leverage(position.leverage),
                "unrealised profit and loss" to Formatters.signedCurrency(position.unrealisedPnl),
                "margin used" to Formatters.currency(position.marginUsed),
            )
        }

        is HoldingSearchResult.Wealth -> {
            val investment = match.investment
            val pnl = investment.currentValue - investment.amountInvested
            lines(
                "investment" to investment.name,
                "amount invested" to Formatters.currency(investment.amountInvested),
                "current value" to Formatters.currency(investment.currentValue),
                "profit and loss" to Formatters.signedCurrency(pnl),
                "profit and loss percent" to Formatters.signedPercent(
                    PortfolioCalculations.pnlPct(pnl, investment.amountInvested),
                ),
            )
        }
    }

    private fun lines(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("\n") { (label, value) -> "$label: $value" }

    private fun holdingCount(count: Int): String =
        if (count == 1) "1 holding" else "$count holdings"

    private fun Map<String, String>.parseCategory(): AssetCategory? = try {
        AssetCategory.valueOf((get("category") ?: return null).trim().uppercase())
    } catch (failure: IllegalArgumentException) {
        null
    }

    companion object {
        const val PORTFOLIO_SUMMARY = "get_portfolio_summary"
        const val CATEGORY_SUMMARY = "get_category_summary"
        const val MARGIN_STATUS = "get_margin_status"
        const val FIND_HOLDING = "find_holding"
    }
}
