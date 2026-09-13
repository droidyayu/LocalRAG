package com.ayushig.localrag.data.assistant

import com.ayushig.localrag.core.Formatters
import com.ayushig.localrag.data.portfolio.PortfolioCalculations
import com.ayushig.localrag.domain.assistant.PortfolioAnswerRenderer
import com.ayushig.localrag.domain.model.assistant.AnswerResult
import com.ayushig.localrag.domain.model.assistant.PortfolioIntent
import com.ayushig.localrag.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.domain.model.portfolio.CategorySummary
import com.ayushig.localrag.domain.model.portfolio.HoldingSearchResult
import com.ayushig.localrag.domain.model.portfolio.PortfolioSummary
import com.ayushig.localrag.domain.repository.PortfolioRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Builds answers from templates. The model never sees these numbers and never produces one:
 * every figure comes from [PortfolioRepository] and is formatted by [Formatters], the same code
 * path the portfolio screens use, so a spoken figure and a rendered row cannot disagree.
 */
@Singleton
class TemplatePortfolioAnswerRenderer @Inject constructor(
    private val repository: PortfolioRepository,
) : PortfolioAnswerRenderer {

    override suspend fun render(intent: PortfolioIntent): AnswerResult {
        if (intent is PortfolioIntent.AdviceRequest) return AnswerResult.Refusal(ADVICE_REFUSAL)
        if (intent is PortfolioIntent.Unknown) return AnswerResult.NoMatch

        // A failed read is never allowed to become a partial answer.
        return runCatching { renderFromRepository(intent) }
            .getOrElse { AnswerResult.Failure(LOAD_FAILURE) }
    }

    /**
     * Every answer needs the snapshot time, so the summary read starts immediately and the
     * intent-specific read runs alongside it rather than after it.
     */
    private suspend fun renderFromRepository(
        intent: PortfolioIntent,
    ): AnswerResult = coroutineScope {
        val summaryAsync = async { repository.getPortfolioSummary() }

        when (intent) {
            PortfolioIntent.TotalValue -> summaryAsync.await().let { summary ->
                answer(
                    "Your portfolio is worth ${money(summary.totalValue)}. " +
                        "That's ${signedMoney(summary.totalPnl)} (${pct(summary.totalPnlPct)}) overall.",
                    summary,
                )
            }

            PortfolioIntent.TotalPnl -> summaryAsync.await().let { summary ->
                answer(
                    "You're ${direction(summary.totalPnl)} ${signedMoney(summary.totalPnl)} " +
                        "(${pct(summary.totalPnlPct)}) overall. " +
                        "Your portfolio is worth ${money(summary.totalValue)}.",
                    summary,
                )
            }

            PortfolioIntent.AvailableBalance -> summaryAsync.await().let { summary ->
                answer(
                    "You have ${money(summary.availableBalance)} available in " +
                        "${summary.currency}.",
                    summary,
                )
            }

            PortfolioIntent.MarginStatus -> {
                val margin = async { repository.getMarginStatus() }.await()
                answer(
                    "You're using ${money(margin.marginUsed)} of margin. " +
                        "Free margin is ${money(margin.freeMargin)}, and your margin level is " +
                        "${Formatters.percent(margin.marginLevelPct)}.",
                    summaryAsync.await(),
                )
            }

            is PortfolioIntent.CategoryValue -> {
                val summary = async { repository.getCategorySummary(intent.category) }.await()
                answer(
                    "${categoryPhrase(intent.category)} are worth " +
                        "${money(summary.currentValue)} across " +
                        "${holdingCount(summary.holdingCount)}.",
                    summaryAsync.await(),
                )
            }

            is PortfolioIntent.CategoryPnl -> {
                val summary = async { repository.getCategorySummary(intent.category) }.await()
                answer(
                    "${categoryPhrase(intent.category)} are " +
                        "${direction(summary.totalPnl)} ${signedMoney(summary.totalPnl)} " +
                        "(${pct(summary.pnlPct)}). " +
                        "Current value is ${money(summary.currentValue)}.",
                    summaryAsync.await(),
                )
            }

            PortfolioIntent.BestPerformer -> performer(summaryAsync.await(), best = true)

            PortfolioIntent.WorstPerformer -> performer(summaryAsync.await(), best = false)

            PortfolioIntent.ListHoldings -> summaryAsync.await().let { summary ->
                val lines = summary.categories.joinToString("\n") { category ->
                    "${category.category.displayName}: " +
                        "${holdingCount(category.holdingCount)}, " +
                        "${money(category.currentValue)} (${pct(category.pnlPct)})"
                }
                answer(
                    "You hold ${money(summary.totalValue)} across four categories.\n$lines",
                    summary,
                )
            }

            is PortfolioIntent.HoldingDetail -> holdingDetail(intent.query, summaryAsync.await())

            PortfolioIntent.AdviceRequest, PortfolioIntent.Unknown -> AnswerResult.NoMatch
        }
    }

    /**
     * A query can offer several candidate words, so they are looked up together rather than one
     * after another; the first match in candidate order still wins.
     */
    private suspend fun holdingDetail(query: String, summary: PortfolioSummary): AnswerResult =
        coroutineScope {
            val candidates = KeywordIntentResolver.holdingCandidates(
                KeywordIntentResolver.normalise(query)
            )
            val match = candidates
                .map { candidate -> async { repository.findHoldingBySymbol(candidate) } }
                .awaitAll()
                .firstNotNullOfOrNull { it }
            if (match == null) AnswerResult.NoMatch else answer(describe(match), summary)
        }

    private fun describe(match: HoldingSearchResult): String = when (match) {
        is HoldingSearchResult.Stock -> {
            val holding = match.holding
            val value = PortfolioCalculations.stockValue(holding)
            val cost = PortfolioCalculations.stockCost(holding)
            val pnl = value - cost
            "${holding.companyName}: ${Formatters.shares(holding.quantity)} at an average of " +
                "${money(holding.avgCost)}. Now ${money(holding.lastPrice)}, so you're " +
                "${direction(pnl)} ${signedMoney(pnl)} (${pct(PortfolioCalculations.pnlPct(pnl, cost))})."
        }

        is HoldingSearchResult.MetalHoldingMatch -> {
            val holding = match.holding
            val value = PortfolioCalculations.metalValue(holding)
            val cost = PortfolioCalculations.metalCost(holding)
            val pnl = value - cost
            "${holding.metal.displayName}: ${Formatters.grams(holding.grams)} at an average of " +
                "${money(holding.avgBuyPricePerGram)} per gram. Now " +
                "${money(holding.currentPricePerGram)} per gram, so you're ${direction(pnl)} " +
                "${signedMoney(pnl)} (${pct(PortfolioCalculations.pnlPct(pnl, cost))})."
        }

        is HoldingSearchResult.Leveraged -> {
            val position = match.position
            "${position.displayName}: a ${position.direction.name} position of " +
                "${Formatters.quantity(position.units)} units at " +
                "${Formatters.leverage(position.leverage)}. You're " +
                "${direction(position.unrealisedPnl)} ${signedMoney(position.unrealisedPnl)} on it, " +
                "with ${money(position.marginUsed)} of margin tied up."
        }

        is HoldingSearchResult.Wealth -> {
            val investment = match.investment
            val pnl = investment.currentValue - investment.amountInvested
            "${investment.name}: ${money(investment.amountInvested)} invested, now worth " +
                "${money(investment.currentValue)}, so you're ${direction(pnl)} ${signedMoney(pnl)} " +
                "(${pct(PortfolioCalculations.pnlPct(pnl, investment.amountInvested))})."
        }
    }

    private fun performer(summary: PortfolioSummary, best: Boolean): AnswerResult {
        val ranked = summary.categories.sortedBy { it.pnlPct }
        val pick: CategorySummary = if (best) ranked.last() else ranked.first()
        val text = if (best) {
            "${pick.category.displayName} is doing best right now, " +
                "${direction(pick.totalPnl)} ${signedMoney(pick.totalPnl)} (${pct(pick.pnlPct)})."
        } else {
            "${pick.category.displayName} is your weakest right now, " +
                "${direction(pick.totalPnl)} ${signedMoney(pick.totalPnl)} (${pct(pick.pnlPct)})."
        }
        return answer(text, summary)
    }

    /** Every answer carries the snapshot time, so a stale figure is never presented as live. */
    private fun answer(text: String, summary: PortfolioSummary): AnswerResult.Answer =
        AnswerResult.Answer("$text\nAs of ${Formatters.asOf(summary.asOf)}.")

    private fun money(amount: Double) = Formatters.currency(amount)

    /** Profit and loss always carries its sign, so the wording never has to imply one. */
    private fun signedMoney(amount: Double) = Formatters.signedCurrency(amount)

    private fun pct(value: Double) = Formatters.signedPercent(value)

    /** Reads the sign off the actual figure rather than guessing at "profit" or "loss". */
    private fun direction(amount: Double): String = when {
        amount > 0.0 -> "up"
        amount < 0.0 -> "down"
        else -> "flat at"
    }

    private fun holdingCount(count: Int) = if (count == 1) "1 holding" else "$count holdings"

    private fun categoryPhrase(category: AssetCategory): String = when (category) {
        AssetCategory.METALS -> "Your gold and silver"
        AssetCategory.STOCKS -> "Your stocks"
        AssetCategory.WEALTH -> "Your managed funds"
        AssetCategory.LEVERAGED -> "Your leveraged positions"
    }

    private companion object {
        const val ADVICE_REFUSAL =
            "I can show you what's in your account, but I can't advise on buying or selling."
        const val LOAD_FAILURE = "I couldn't load your portfolio just now."
    }
}
