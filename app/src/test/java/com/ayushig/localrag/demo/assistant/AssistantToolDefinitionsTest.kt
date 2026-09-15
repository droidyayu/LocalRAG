package com.ayushig.localrag.demo.assistant

import com.ayushig.localrag.demo.data.assistant.AssistantToolDefinitions
import com.ayushig.localrag.demo.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.demo.domain.model.portfolio.CategorySummary
import com.ayushig.localrag.demo.domain.model.portfolio.HoldingSearchResult
import com.ayushig.localrag.demo.domain.model.portfolio.LeveragedPosition
import com.ayushig.localrag.demo.domain.model.portfolio.MarginStatus
import com.ayushig.localrag.demo.domain.model.portfolio.MetalHolding
import com.ayushig.localrag.demo.domain.model.portfolio.PortfolioSummary
import com.ayushig.localrag.demo.domain.model.portfolio.StockHolding
import com.ayushig.localrag.demo.domain.model.portfolio.WealthInvestment
import com.ayushig.localrag.demo.domain.repository.PortfolioRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tool execution against a stubbed repository.
 *
 * The contract under test: observations carry rendered figures and labels, never prose, and any
 * misuse or failed read yields null so the loop ends the turn instead of guessing.
 */
class AssistantToolDefinitionsTest {

    private val summary = PortfolioSummary(
        totalValue = 12340.0,
        totalPnl = 1240.0,
        totalPnlPct = 11.17,
        availableBalance = 2500.0,
        currency = "USD",
        categories = emptyList(),
        asOf = Instant.parse("2026-09-13T10:00:00Z"),
    )

    private open class StubRepo : PortfolioRepository {
        override suspend fun getPortfolioSummary(): PortfolioSummary =
            throw UnsupportedOperationException()

        override suspend fun getCategorySummary(category: AssetCategory): CategorySummary =
            throw UnsupportedOperationException()

        override suspend fun getLeveragedPositions(): List<LeveragedPosition> =
            throw UnsupportedOperationException()

        override suspend fun getMetalHoldings(): List<MetalHolding> =
            throw UnsupportedOperationException()

        override suspend fun getWealthInvestments(): List<WealthInvestment> =
            throw UnsupportedOperationException()

        override suspend fun getStockHoldings(): List<StockHolding> =
            throw UnsupportedOperationException()

        override suspend fun getMarginStatus(): MarginStatus =
            throw UnsupportedOperationException()

        override suspend fun findHoldingBySymbol(query: String): HoldingSearchResult? =
            throw UnsupportedOperationException()
    }

    private inner class FakeRepo(
        val summary: PortfolioSummary = this@AssistantToolDefinitionsTest.summary,
        val holding: HoldingSearchResult? = null,
    ) : StubRepo() {
        override suspend fun getPortfolioSummary(): PortfolioSummary = summary

        override suspend fun getCategorySummary(category: AssetCategory): CategorySummary =
            CategorySummary(category, 5000.0, 200.0, 4.17, 2)

        override suspend fun getMarginStatus(): MarginStatus =
            MarginStatus(equity = 15000.0, marginUsed = 3000.0, freeMargin = 12000.0, marginLevelPct = 500.0)

        override suspend fun findHoldingBySymbol(query: String): HoldingSearchResult? = holding
    }

    private fun tools(
        repo: PortfolioRepository = FakeRepo(),
    ) = AssistantToolDefinitions(repo)

    private suspend fun AssistantToolDefinitions.call(
        name: String,
        args: Map<String, String>,
    ) = list().single { it.name == name }.execute(args)

    @Test
    fun `summary observation carries figures and labels`() = runTest {
        val observation = tools().call("get_portfolio_summary", emptyMap())!!
        assertTrue(observation.text.contains("total value: $12,340.00"))
        assertTrue(observation.text.contains("available balance: $2,500.00"))
        assertTrue(observation.text.contains("as of:"))
        assertTrue(observation.sourceTitles.isEmpty())
    }

    @Test
    fun `category names are case-insensitive`() = runTest {
        val observation = tools().call("get_category_summary", mapOf("category" to "metals"))!!
        assertTrue(observation.text.contains("Physical Gold/Silver"))
        assertTrue(observation.text.contains("current value: $5,000.00"))
    }

    @Test
    fun `category tool rejects bad input`() = runTest {
        val tools = tools()
        assertNull(tools.call("get_category_summary", mapOf("category" to "crypto")))
        assertNull(tools.call("get_category_summary", emptyMap()))
    }

    @Test
    fun `margin observation carries figures`() = runTest {
        val observation = tools().call("get_margin_status", emptyMap())!!
        assertTrue(observation.text.contains("margin used: $3,000.00"))
        assertTrue(observation.text.contains("margin level: 500.00%"))
    }

    @Test
    fun `holding observation describes the match`() = runTest {
        val holding = HoldingSearchResult.Stock(
            StockHolding("AAPL", "Apple Inc.", 10, 150.0, 170.0, 1.2),
        )
        val observation = tools(FakeRepo(holding = holding))
            .call("find_holding", mapOf("query" to "aapl"))!!
        assertTrue(observation.text.contains("Apple Inc."))
        assertTrue(observation.text.contains("10 shares"))
        assertTrue(observation.text.contains("current value: $1,700.00"))
    }

    @Test
    fun `holding miss is an observation, empty query is a failure`() = runTest {
        val tools = tools(FakeRepo(holding = null))
        assertEquals(
            "no holding matches \"tsla\"",
            tools.call("find_holding", mapOf("query" to "tsla"))!!.text,
        )
        assertNull(tools.call("find_holding", mapOf("query" to "  ")))
        assertNull(tools.call("find_holding", emptyMap()))
    }

    @Test
    fun `unknown tool and failed reads yield null`() = runTest {
        val tools = tools()
        assertNull(tools.call("delete_portfolio", emptyMap()))
        assertNull(tools.call("search_documentation", mapOf("query" to "gtt")))

        val failing = object : StubRepo() {}
        assertNull(AssistantToolDefinitions(failing).call("get_portfolio_summary", emptyMap()))
    }
}
