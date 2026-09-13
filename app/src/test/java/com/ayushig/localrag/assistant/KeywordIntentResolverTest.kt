package com.ayushig.localrag.assistant

import com.ayushig.localrag.data.assistant.HoldingVocabulary
import com.ayushig.localrag.data.assistant.KeywordIntentResolver
import com.ayushig.localrag.domain.model.assistant.PortfolioIntent
import com.ayushig.localrag.domain.model.portfolio.AssetCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class KeywordIntentResolverTest {

    private val resolver = KeywordIntentResolver(HoldingVocabulary())

    private fun assertIntent(expected: PortfolioIntent, vararg queries: String) {
        queries.forEach { query ->
            assertEquals("query: \"$query\"", expected, resolver.resolve(query))
        }
    }

    @Test
    fun `advice questions are caught before anything else`() {
        assertIntent(
            PortfolioIntent.AdviceRequest,
            "should i buy more gold",
            "Should I sell my Apple shares?",
            "is AAPL worth buying right now",
            "is now a good time to buy silver",
            "what do you think about NVDA",
            "can you recommend a fund",
            "will it go up tomorrow",
            "should i hold or sell tesla",
            "any advice on my leveraged positions",
            "worth selling my gold?",
            "is it a good time to invest in stocks",
            "shall i double down on NVDA",
            "should we cut my losses on the euro trade",
        )
    }

    @Test
    fun `whole portfolio value`() {
        assertIntent(
            PortfolioIntent.TotalValue,
            "what's my total value",
            "how much is my portfolio worth",
            "what is my portfolio worth",
            "total value please",
            "whats my account worth",
            "how big is my portfolio",
        )
    }

    @Test
    fun `whole portfolio profit and loss`() {
        assertIntent(
            PortfolioIntent.TotalPnl,
            "what's my total pnl",
            "what is my p&l",
            "am i up or down",
            "how much have i made",
            "how much have i lost overall",
            "how is my portfolio performing",
            "overall performance",
        )
    }

    @Test
    fun `available balance`() {
        assertIntent(
            PortfolioIntent.AvailableBalance,
            "what's my available balance",
            "how much cash do i have",
            "available cash",
            "whats my buying power",
        )
    }

    @Test
    fun `margin status wins over the leveraged category`() {
        assertIntent(
            PortfolioIntent.MarginStatus,
            "how much margin am i using",
            "what's my margin level",
            "free margin",
            "margin status",
            "how much margin have i got left",
        )
    }

    @Test
    fun `category profit and loss`() {
        assertIntent(PortfolioIntent.CategoryPnl(AssetCategory.METALS),
            "how much am i down on gold",
            "how are my metals doing",
            "am i up on silver",
            "bullion performance",
        )
        assertIntent(PortfolioIntent.CategoryPnl(AssetCategory.STOCKS),
            "how are my stocks doing",
            "how much have i made on shares",
            "equity performance",
        )
        assertIntent(PortfolioIntent.CategoryPnl(AssetCategory.WEALTH),
            "how are my managed funds performing",
            "am i up on the wealth products",
        )
        assertIntent(PortfolioIntent.CategoryPnl(AssetCategory.LEVERAGED),
            "how are my cfds doing",
            "am i down on my forex positions",
        )
    }

    @Test
    fun `category value`() {
        assertIntent(PortfolioIntent.CategoryValue(AssetCategory.METALS),
            "how much gold do i have",
            "what are my metals worth",
        )
        assertIntent(PortfolioIntent.CategoryValue(AssetCategory.STOCKS),
            "what are my stocks worth",
            "total value of my shares",
        )
        assertIntent(PortfolioIntent.CategoryValue(AssetCategory.WEALTH),
            "what are my funds worth",
        )
        assertIntent(PortfolioIntent.CategoryValue(AssetCategory.LEVERAGED),
            "what are my open positions worth",
        )
    }

    @Test
    fun `best and worst performers`() {
        assertIntent(
            PortfolioIntent.BestPerformer,
            "what's my best performer",
            "which is doing best",
            "biggest gainer",
        )
        assertIntent(
            PortfolioIntent.WorstPerformer,
            "what's my worst performer",
            "what's losing the most",
            "biggest loser",
            "which one is doing worst",
        )
    }

    @Test
    fun `listing holdings`() {
        assertIntent(
            PortfolioIntent.ListHoldings,
            "what do i hold",
            "list my holdings",
            "what's in my account",
            "show me everything",
            "give me a breakdown",
        )
    }

    @Test
    fun `a bare holding name routes to a holding lookup`() {
        assertEquals(
            PortfolioIntent.HoldingDetail("how's Apple"),
            resolver.resolve("how's Apple"),
        )
        assertEquals(
            PortfolioIntent.HoldingDetail("tesla"),
            resolver.resolve("tesla"),
        )
    }

    @Test
    fun `a named holding beats the whole-account reading`() {
        // "doing", "up" and "down" are profit-and-loss words, but the holding is more specific.
        listOf(
            "hows Apple doing",
            "how's Apple doing",
            "am i up on tesla",
            "how much am i down on nvda",
            "microsoft performance",
        ).forEach { query ->
            assertEquals(
                "query: \"$query\"",
                PortfolioIntent.HoldingDetail(query),
                resolver.resolve(query),
            )
        }
    }

    @Test
    fun `casual phrasing and misspellings still route`() {
        assertIntent(PortfolioIntent.TotalPnl, "am i up", "how much am i down")
        assertIntent(PortfolioIntent.CategoryPnl(AssetCategory.METALS), "hows gold doing")
        assertIntent(PortfolioIntent.MarginStatus, "HOW MUCH MARGIN AM I USING???")
    }

    @Test
    fun `chit chat falls through to the model`() {
        assertIntent(
            PortfolioIntent.Unknown,
            "hello",
            "",
            "   ",
            "what is the weather",
        )
    }

    @Test
    fun `short words never fire inside longer ones`() {
        // "up" must not match inside "supported", "down" must not match inside "download".
        assertEquals(PortfolioIntent.Unknown, resolver.resolve("is that supported"))
    }
}
