package com.ayushig.localrag.demo.assistant

import com.ayushig.localrag.demo.core.Formatters
import com.ayushig.localrag.demo.data.assistant.HoldingVocabulary
import com.ayushig.localrag.demo.data.assistant.KeywordIntentResolver
import com.ayushig.localrag.demo.data.assistant.TemplatePortfolioAnswerRenderer
import com.ayushig.localrag.demo.data.portfolio.DummyPortfolioData
import com.ayushig.localrag.demo.data.repository.FakePortfolioRepository
import com.ayushig.localrag.demo.domain.model.assistant.AnswerResult
import com.ayushig.localrag.demo.domain.model.assistant.PortfolioIntent
import com.ayushig.localrag.demo.domain.model.portfolio.AssetCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exact-string tests. There is no model in this path, so every answer is deterministic and is
 * asserted character for character.
 */
class PortfolioAnswerRendererTest {

    private val repository = FakePortfolioRepository()
    private val renderer = TemplatePortfolioAnswerRenderer(repository)
    private val resolver = KeywordIntentResolver(HoldingVocabulary())

    /** The snapshot time renders in the machine's zone, so build the suffix the same way. */
    private val asOfLine = "\nAs of ${Formatters.asOf(DummyPortfolioData.AS_OF)}."

    private suspend fun textOf(intent: PortfolioIntent): String {
        val result = renderer.render(intent)
        assertTrue("expected an answer, got $result", result is AnswerResult.Answer)
        return (result as AnswerResult.Answer).text
    }

    @Test
    fun `total value`() = runTest {
        assertEquals(
            "Your portfolio is worth \$169,363.92. That's +\$8,693.25 (+5.41%) overall.$asOfLine",
            textOf(PortfolioIntent.TotalValue),
        )
    }

    @Test
    fun `total profit and loss`() = runTest {
        assertEquals(
            "You're up +\$8,693.25 (+5.41%) overall. " +
                "Your portfolio is worth \$169,363.92.$asOfLine",
            textOf(PortfolioIntent.TotalPnl),
        )
    }

    @Test
    fun `available balance`() = runTest {
        assertEquals(
            "You have \$12,480.35 available in USD.$asOfLine",
            textOf(PortfolioIntent.AvailableBalance),
        )
    }

    @Test
    fun `margin status`() = runTest {
        assertEquals(
            "You're using \$21,511.17 of margin. Free margin is \$160,333.10, " +
                "and your margin level is 845.35%.$asOfLine",
            textOf(PortfolioIntent.MarginStatus),
        )
    }

    @Test
    fun `metals profit and loss`() = runTest {
        assertEquals(
            "Your gold and silver are up +\$1,580.00 (+7.50%). " +
                "Current value is \$22,660.00.$asOfLine",
            textOf(PortfolioIntent.CategoryPnl(AssetCategory.METALS)),
        )
    }

    @Test
    fun `stocks value`() = runTest {
        assertEquals(
            "Your stocks are worth \$67,710.75 across 5 holdings.$asOfLine",
            textOf(PortfolioIntent.CategoryValue(AssetCategory.STOCKS)),
        )
    }

    @Test
    fun `worst performer`() = runTest {
        assertEquals(
            "Leveraged Trading is your weakest right now, up +\$52.00 (+0.24%).$asOfLine",
            textOf(PortfolioIntent.WorstPerformer),
        )
    }

    @Test
    fun `best performer`() = runTest {
        assertEquals(
            "Physical Gold/Silver is doing best right now, up +\$1,580.00 (+7.50%).$asOfLine",
            textOf(PortfolioIntent.BestPerformer),
        )
    }

    @Test
    fun `list holdings`() = runTest {
        assertEquals(
            "You hold \$169,363.92 across four categories.\n" +
                "Leveraged Trading: 4 holdings, \$21,563.17 (+0.24%)\n" +
                "Physical Gold/Silver: 2 holdings, \$22,660.00 (+7.50%)\n" +
                "Wealth Investment: 3 holdings, \$57,430.00 (+4.42%)\n" +
                "Stocks: 5 holdings, \$67,710.75 (+7.34%)$asOfLine",
            textOf(PortfolioIntent.ListHoldings),
        )
    }

    @Test
    fun `a winning stock`() = runTest {
        assertEquals(
            "Apple Inc.: 120 shares at an average of \$178.40. Now \$213.55, " +
                "so you're up +\$4,218.00 (+19.70%).$asOfLine",
            textOf(PortfolioIntent.HoldingDetail("how's Apple doing")),
        )
    }

    @Test
    fun `a losing stock keeps the minus sign`() = runTest {
        assertEquals(
            "NVIDIA Corporation: 60 shares at an average of \$118.75. Now \$104.30, " +
                "so you're down -\$867.00 (-12.17%).$asOfLine",
            textOf(PortfolioIntent.HoldingDetail("nvda")),
        )
    }

    @Test
    fun `a leveraged position`() = runTest {
        assertEquals(
            "British Pound / US Dollar: a SELL position of 60,000 units at 1:20. " +
                "You're down -\$990.00 on it, with \$3,822.00 of margin tied up.$asOfLine",
            textOf(PortfolioIntent.HoldingDetail("gbpusd")),
        )
    }

    @Test
    fun `a managed fund`() = runTest {
        assertEquals(
            "Emerging Tech Opportunities: \$12,000.00 invested, now worth \$10,940.00, " +
                "so you're down -\$1,060.00 (-8.83%).$asOfLine",
            textOf(PortfolioIntent.HoldingDetail("emerging")),
        )
    }

    @Test
    fun `advice is refused`() = runTest {
        assertEquals(
            AnswerResult.Refusal(
                "I can show you what's in your account, but I can't advise on buying or selling."
            ),
            renderer.render(PortfolioIntent.AdviceRequest),
        )
    }

    @Test
    fun `advice is still refused when a holding is named`() = runTest {
        val intent = resolver.resolve("should i sell my apple shares")
        assertEquals(PortfolioIntent.AdviceRequest, intent)
        assertTrue(renderer.render(intent) is AnswerResult.Refusal)
    }

    @Test
    fun `an unknown query falls back to the model`() = runTest {
        assertEquals(AnswerResult.NoMatch, renderer.render(PortfolioIntent.Unknown))
    }

    @Test
    fun `a holding that is not held is not answered`() = runTest {
        assertEquals(
            AnswerResult.NoMatch,
            renderer.render(PortfolioIntent.HoldingDetail("how is my palladium doing")),
        )
    }

    @Test
    fun `a failing repository never yields a partial answer`() = runTest {
        val failing = TemplatePortfolioAnswerRenderer(ThrowingPortfolioRepository())
        assertEquals(
            AnswerResult.Failure("I couldn't load your portfolio just now."),
            failing.render(PortfolioIntent.TotalValue),
        )
    }

    @Test
    fun `every answer carries the snapshot time`() = runTest {
        listOf(
            PortfolioIntent.TotalValue,
            PortfolioIntent.TotalPnl,
            PortfolioIntent.MarginStatus,
            PortfolioIntent.ListHoldings,
            PortfolioIntent.CategoryValue(AssetCategory.WEALTH),
        ).forEach { intent ->
            assertTrue("missing as-of line for $intent", textOf(intent).endsWith(asOfLine))
        }
    }
}
