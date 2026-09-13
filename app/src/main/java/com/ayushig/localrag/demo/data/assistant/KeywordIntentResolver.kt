package com.ayushig.localrag.demo.data.assistant

import com.ayushig.localrag.demo.domain.assistant.IntentResolver
import com.ayushig.localrag.demo.domain.model.assistant.PortfolioIntent
import com.ayushig.localrag.demo.domain.model.portfolio.AssetCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rule-based routing. No model is involved, so the same question always routes the same way.
 *
 * The keyword tables below are the whole implementation — extend those rather than the logic.
 * Order matters: advice is caught first, then the specific intents, then the general ones, and a
 * holding lookup is the last resort before giving up to the model.
 */
@Singleton
class KeywordIntentResolver @Inject constructor(
    private val holdingVocabulary: HoldingVocabulary,
) : IntentResolver {

    override fun resolve(query: String): PortfolioIntent {
        val text = normalise(query)
        if (text.isEmpty()) return PortfolioIntent.Unknown

        // Split once: the keyword tables hold roughly sixty single words between them.
        val words = WORD_SPLIT.split(text).toSet()

        // Advice is refused even when the question also names a holding, so it runs first.
        if (text.matches(words, ADVICE)) return PortfolioIntent.AdviceRequest

        if (text.matches(words, MARGIN_STATUS)) return PortfolioIntent.MarginStatus
        if (text.matches(words, AVAILABLE_BALANCE)) return PortfolioIntent.AvailableBalance
        if (text.matches(words, BEST_PERFORMER)) return PortfolioIntent.BestPerformer
        if (text.matches(words, WORST_PERFORMER)) return PortfolioIntent.WorstPerformer
        if (text.matches(words, LIST_HOLDINGS)) return PortfolioIntent.ListHoldings

        val category = CATEGORY_WORDS.entries
            .firstOrNull { (_, keywords) -> text.matches(words, keywords) }?.key
        val asksPnl = text.matches(words, PNL_WORDS)
        val asksValue = text.matches(words, VALUE_WORDS)

        if (category != null) {
            return if (asksPnl) {
                PortfolioIntent.CategoryPnl(category)
            } else {
                PortfolioIntent.CategoryValue(category)
            }
        }

        // A named holding beats the whole-account reading: "how's Apple doing" asks about Apple,
        // even though "doing" is a profit-and-loss word.
        if (holdingCandidates(text).any { it in holdingVocabulary }) {
            return PortfolioIntent.HoldingDetail(query)
        }

        if (asksPnl) return PortfolioIntent.TotalPnl
        if (asksValue) return PortfolioIntent.TotalValue

        return PortfolioIntent.Unknown
    }

    /**
     * Multi-word phrases match as substrings of [this]; single words match against [words] so
     * "up" does not fire inside "supported".
     */
    private fun String.matches(words: Set<String>, needles: List<String>): Boolean =
        needles.any { needle ->
            if (needle.contains(' ')) contains(needle) else needle in words
        }

    companion object {
        val ADVICE = listOf(
            "should i", "should we", "shall i", "worth buying", "worth selling",
            "good time to", "right time to", "recommend", "recommendation",
            "will it go up", "will it go down", "will they go up", "hold or sell",
            "buy or sell", "sell or hold", "what do you think", "is it a good buy",
            "is it a good time", "any advice", "your advice", "advise me",
            "invest in", "double down", "cut my losses",
        )

        val MARGIN_STATUS = listOf(
            "margin level", "free margin", "margin used", "using margin", "margin am i using",
            "margin are we using", "how much margin", "margin status", "margin call",
            "margin usage", "margin left",
        )

        val AVAILABLE_BALANCE = listOf(
            "available balance", "available cash", "cash balance", "buying power",
            "how much cash", "spare cash", "free cash", "uninvested",
        )

        val BEST_PERFORMER = listOf(
            "best performer", "best performing", "top performer", "doing best", "biggest winner",
            "best holding", "biggest gain", "biggest gainer", "my best",
        )

        val WORST_PERFORMER = listOf(
            "worst performer", "worst performing", "doing worst", "biggest loser",
            "biggest loss", "worst holding", "losing the most", "losing most", "my worst",
        )

        val LIST_HOLDINGS = listOf(
            "what do i hold", "what do i own", "list my holdings", "my holdings",
            "what am i holding", "whats in my account", "what is in my account",
            "show me everything", "breakdown", "break down", "everything i own",
            "all my holdings", "what have i got",
        )

        /**
         * "portfolio" deliberately does not map to WEALTH: "what is my portfolio worth" is a
         * whole-account question, and mapping it to the managed funds would answer the wrong one.
         */
        val CATEGORY_WORDS: Map<AssetCategory, List<String>> = mapOf(
            AssetCategory.METALS to listOf(
                "gold", "silver", "metal", "metals", "bullion", "xau", "xag",
            ),
            AssetCategory.WEALTH to listOf(
                "wealth", "fund", "funds", "managed", "managed portfolio", "wealth portfolio",
            ),
            AssetCategory.LEVERAGED to listOf(
                "leverage", "leveraged", "cfd", "cfds", "forex", "fx", "position", "positions",
                "lot", "lots", "margin", "currency pair", "trades", "open trades",
            ),
            AssetCategory.STOCKS to listOf(
                "stock", "stocks", "share", "shares", "equity", "equities", "ticker",
            ),
        )

        val PNL_WORDS = listOf(
            "profit", "profits", "loss", "losses", "pnl", "up", "down", "gain", "gains",
            "made", "lost", "losing", "making", "return", "returns", "performance",
            "performing", "doing", "ahead", "behind",
        )

        val VALUE_WORDS = listOf(
            "worth", "value", "valued", "total", "how much", "how big", "size", "balance",
        )

        /** Words that never identify a holding, so they are not worth a repository lookup. */
        private val STOP_WORDS = setOf(
            "how", "hows", "how's", "is", "are", "am", "my", "me", "i", "the", "a", "an", "of",
            "on", "in", "at", "to", "for", "with", "and", "or", "what", "whats", "what's",
            "much", "many", "doing", "do", "does", "did", "it", "its", "that", "this", "there",
            "here", "now", "today", "please", "tell", "show", "give", "about", "any", "all",
            "you", "your", "we", "us", "can", "could", "would", "will", "get", "got", "have",
            "has", "had", "was", "were", "be", "been", "s", "t",
        )

        private val WORD_SPLIT = Regex("\\s+")

        /** Tokens worth trying against the holdings index, longest first. */
        fun holdingCandidates(normalisedText: String): List<String> =
            WORD_SPLIT.split(normalisedText)
                .filter { it.length >= 2 && it !in STOP_WORDS }
                .distinct()
                .sortedByDescending { it.length }

        /**
         * Lowercase, drop apostrophes so "what's" reads as "whats", fold "p&l" to "pnl", strip
         * the remaining punctuation and collapse whitespace.
         */
        fun normalise(query: String): String = query
            .lowercase()
            .replace("'", "")
            .replace("\u2019", "")
            .replace("p&l", "pnl")
            .replace("p & l", "pnl")
            .replace("p and l", "pnl")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(WORD_SPLIT, " ")
            .trim()
    }
}
