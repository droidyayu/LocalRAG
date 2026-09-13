package com.ayushig.localrag.data.assistant

import com.ayushig.localrag.data.portfolio.DummyPortfolioData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The words that name something in the account: every symbol, plus the significant words of each
 * display name, company name, metal and fund.
 *
 * The resolver needs this synchronously to tell "how's Apple doing" from "hello" without paying
 * for a repository lookup on every unrecognised question.
 */
@Singleton
class HoldingVocabulary @Inject constructor() {

    private val terms: Set<String> by lazy {
        buildSet {
            DummyPortfolioData.leveragedPositions.forEach { position ->
                add(position.symbol.lowercase())
                addAll(significantWords(position.displayName))
            }
            DummyPortfolioData.stockHoldings.forEach { holding ->
                add(holding.symbol.lowercase())
                addAll(significantWords(holding.companyName))
            }
            DummyPortfolioData.metalHoldings.forEach { holding ->
                add(holding.metal.displayName.lowercase())
            }
            DummyPortfolioData.wealthInvestments.forEach { investment ->
                addAll(significantWords(investment.name))
            }
        }
    }

    operator fun contains(term: String): Boolean = term in terms

    /** Drops connectives and legal suffixes that would match far too much. */
    private fun significantWords(name: String): List<String> = name
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 && it !in NOISE }

    private companion object {
        /**
         * Legal suffixes, connectives, and the generic account words that appear inside fund
         * names. "Balanced Growth Portfolio" must not make "portfolio" name a holding, or
         * "what is my portfolio worth" would answer about one fund instead of the whole account.
         */
        val NOISE = setOf(
            "inc", "the", "and", "corporation", "company", "ltd", "plc", "spot",
            "portfolio", "fund", "funds", "account", "investment", "investments",
        )
    }
}
