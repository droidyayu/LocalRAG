package com.ayushig.localrag.demo.assistant

import com.ayushig.localrag.demo.domain.assistant.NoInformationFallback
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The last-resort reply must never reflect the question back at the user.
 *
 * That was the visible symptom of the echo repository sitting behind the fallback path: a query
 * the docs could not answer came back as "Echo: ...". The fallback is fixed text with nothing
 * interpolated, and this test pins that property so a future rewiring cannot reintroduce it.
 */
class NoInformationFallbackTest {

    @Test
    fun `fallback is a complete answer on its own`() {
        assertTrue(NoInformationFallback.TEXT.isNotBlank())
    }

    @Test
    fun `fallback never repeats the query`() {
        listOf(
            "what is my portfolio worth",
            "should i sell my gold",
            "Echo: what is a gtt order",
            "my account balance is 50000",
        ).forEach { query ->
            assertFalse(
                "fallback must not echo the query: $query",
                NoInformationFallback.TEXT.contains(query),
            )
        }
    }
}
