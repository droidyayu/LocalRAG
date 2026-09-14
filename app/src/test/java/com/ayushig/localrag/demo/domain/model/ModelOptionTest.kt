package com.ayushig.localrag.demo.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The picker always has a selection, even with nothing on device. */
class ModelOptionTest {

    @Test
    fun `preferred option wins when present`() {
        assertEquals(
            ModelOption.GEMMA_270M,
            ModelOption.resolve(
                setOf(ModelOption.E2B, ModelOption.GEMMA_270M),
                ModelOption.GEMMA_270M,
            ),
        )
    }

    @Test
    fun `missing preference falls back to what is present`() {
        assertEquals(
            ModelOption.GEMMA_270M,
            ModelOption.resolve(setOf(ModelOption.GEMMA_270M), ModelOption.E2B),
        )
    }

    @Test
    fun `no preference picks the first present option`() {
        assertEquals(
            ModelOption.E2B,
            ModelOption.resolve(setOf(ModelOption.E2B, ModelOption.GEMMA_270M), null),
        )
    }

    @Test
    fun `nothing present still selects E2B for the banner path`() {
        assertEquals(ModelOption.E2B, ModelOption.resolve(emptySet(), null))
        assertEquals(
            ModelOption.E2B,
            ModelOption.resolve(emptySet(), ModelOption.GEMMA_270M),
        )
    }
}
