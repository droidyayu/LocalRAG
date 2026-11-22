package com.ayushig.localrag.core

import com.ayushig.localrag.core.text.Tokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenizerTest {

    @Test
    fun `lowercases and splits on whitespace`() {
        assertEquals(listOf("gtt", "order", "basics"), Tokenizer.tokenize("GTT Order Basics"))
    }

    @Test
    fun `strips punctuation but keeps ampersand and percent`() {
        assertEquals(
            listOf("profit", "loss", "5%", "p&l"),
            Tokenizer.tokenize("Profit/loss: 5%, (P&L)!"),
        )
    }

    @Test
    fun `drops stopwords`() {
        assertEquals(listOf("order", "pending"), Tokenizer.tokenize("is the order pending"))
    }

    @Test
    fun `handles empty and punctuation-only input`() {
        assertTrue(Tokenizer.tokenize("").isEmpty())
        assertTrue(Tokenizer.tokenize("--- ... ,,,").isEmpty())
    }

    @Test
    fun `keeps digits attached to their units`() {
        assertEquals(listOf("4", "2", "version"), Tokenizer.tokenize("4.2 version"))
    }
}
