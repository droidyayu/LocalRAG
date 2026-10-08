package com.ayushig.localrag.embedder.skainet

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * These tests run on every target the module compiles for. The iosSimulatorArm64 run is the
 * point, not a bonus: it proves the exact code that embedded the corpus at build time produces
 * the same vectors on a phone-class target.
 */
class SkaiNetHashedProjectionEmbedderTest {

    @Test
    fun embeddingHasConfiguredDimensionsAndUnitNorm() {
        SkaiNetHashedProjectionEmbedder(dimensions = 64).use { embedder ->
            val vector = embedder.embed("How do I deposit funds into my account?")
            assertEquals(64, vector.size)
            val norm = sqrt(vector.fold(0.0) { acc, v -> acc + v.toDouble() * v })
            assertTrue(abs(norm - 1.0) < 1e-4, "expected unit norm, got $norm")
        }
    }

    @Test
    fun embeddingIsDeterministic() {
        SkaiNetHashedProjectionEmbedder().use { embedder ->
            val first = embedder.embed("What are the brokerage charges?")
            val second = embedder.embed("What are the brokerage charges?")
            assertTrue(first.contentEquals(second))
        }
    }

    @Test
    fun cosineTracksTokenOverlap() {
        SkaiNetHashedProjectionEmbedder().use { embedder ->
            val query = embedder.embed("how do I deposit funds")
            val related = embedder.embed("deposit funds into your account")
            val unrelated = embedder.embed("margin interest overnight charges")
            val relatedScore = cosine(query, related)
            val unrelatedScore = cosine(query, unrelated)
            assertTrue(
                relatedScore > unrelatedScore + 0.1,
                "related=$relatedScore should clearly beat unrelated=$unrelatedScore",
            )
        }
    }

    @Test
    fun emptyTextEmbedsToZeroVectorInsteadOfFailing() {
        SkaiNetHashedProjectionEmbedder(dimensions = 16).use { embedder ->
            val vector = embedder.embed("---")
            assertEquals(16, vector.size)
            assertTrue(vector.all { it == 0f })
        }
    }

    /**
     * Golden values pinned on the JVM. A platform whose vector drifts past float tolerance is
     * retrieving from a different space, which is exactly the drift the manifest parity block
     * exists to refuse — here it fails a unit test instead of degrading retrieval in the field.
     */
    @Test
    fun crossPlatformParityGolden() {
        SkaiNetHashedProjectionEmbedder(dimensions = 8).use { embedder ->
            val vector = embedder.embed("kyc verification pending")
            val golden = floatArrayOf(
                -0.20412415f, 0.61237246f, 0.20412415f, -0.20412415f,
                0.20412415f, 0.61237246f, -0.20412415f, 0.20412415f,
            )
            assertEquals(golden.size, vector.size)
            for (i in golden.indices) {
                assertTrue(
                    abs(golden[i] - vector[i]) < 1e-4,
                    "dimension $i drifted: expected ${golden[i]}, got ${vector[i]}",
                )
            }
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        for (i in a.indices) dot += a[i].toDouble() * b[i]
        return dot
    }
}
