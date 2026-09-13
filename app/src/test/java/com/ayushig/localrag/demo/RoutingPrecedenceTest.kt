package com.ayushig.localrag.demo

import com.ayushig.localrag.demo.data.assistant.HoldingVocabulary
import com.ayushig.localrag.demo.data.assistant.KeywordIntentResolver
import com.ayushig.localrag.demo.domain.model.assistant.PortfolioIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The precedence that decides whether a question is about this account or about the product.
 *
 * The words margin, gold and stocks appear in both the portfolio vocabulary and the help corpus,
 * so without an explicit rule every question containing one of them would be answered with a
 * balance and the documentation would be unreachable.
 */
class RoutingPrecedenceTest {

    private val resolver = KeywordIntentResolver(HoldingVocabulary())

    private fun assertDocs(vararg queries: String) = queries.forEach { query ->
        assertEquals("expected docs: $query", PortfolioIntent.Unknown, resolver.resolve(query))
    }

    private fun assertAccount(vararg queries: String) = queries.forEach { query ->
        val intent = resolver.resolve(query)
        assertTrue(
            "expected an account answer for: $query, got $intent",
            intent != PortfolioIntent.Unknown && intent != PortfolioIntent.AdviceRequest,
        )
    }

    @Test
    fun `definitional questions go to the documentation`() {
        assertDocs(
            "what is margin",
            "what is a gtt order",
            "how do i close a position",
            "what are the charges on gold",
            "how does overnight financing work",
            "which documents do i need for kyc",
            "how long does a withdrawal take",
            "explain margin level",
            "where is my gold stored",
        )
    }

    @Test
    fun `first person questions stay with the account`() {
        assertAccount(
            "how much margin am i using",
            "what is my portfolio worth",
            "what are my stocks worth",
            "how much am i down on gold",
            "how is my apple doing",
            "what do i hold",
        )
    }

    @Test
    fun `advice is refused before either route`() {
        listOf("should i sell my gold", "should i buy more stocks", "is now a good time to buy")
            .forEach { assertEquals(it, PortfolioIntent.AdviceRequest, resolver.resolve(it)) }
    }
}
