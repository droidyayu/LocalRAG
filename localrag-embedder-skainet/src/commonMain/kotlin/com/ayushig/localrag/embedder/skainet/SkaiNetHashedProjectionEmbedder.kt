package com.ayushig.localrag.embedder.skainet

import kotlin.math.ln
import kotlin.math.sqrt
import sk.ainet.context.DirectCpuExecutionContext
import sk.ainet.lang.tensor.dsl.tensor
import sk.ainet.lang.types.FP32

/**
 * A deterministic embedder over SKaiNET tensors: signed term frequencies projected into a dense
 * space through per-token pseudo-random rows (the hashing trick + random projection).
 *
 * Unlike the hash sidecar's pseudo-vectors, cosine similarity in this space tracks token overlap,
 * so retrieval built on it is testable for quality, not just for plumbing.
 *
 * Everything here is `commonMain`: the Gradle sidecar embeds the corpus with the same compiled
 * code a phone runs at query time — JVM today, iosArm64 next — which is what makes the bundle
 * manifest's parity block true by construction rather than by pinned twin toolchains.
 *
 * Determinism holds across platforms: token rows come from integer hashes (FNV-1a, splitmix64),
 * never from a platform random source, and the projection runs in FP32 end to end.
 */
class SkaiNetHashedProjectionEmbedder(
    val dimensions: Int = DEFAULT_DIMENSIONS,
) : AutoCloseable {

    private val context = DirectCpuExecutionContext.create()

    /** Zero vector for text with no tokens; never null — build-time embedding must not skip chunks. */
    fun embed(text: String): FloatArray {
        val counts = termFrequencies(tokenize(text))
        if (counts.isEmpty()) return FloatArray(dimensions)

        val termCount = counts.size
        val weights = FloatArray(termCount)
        val rows = FloatArray(termCount * dimensions)
        var index = 0
        for ((token, frequency) in counts) {
            val seed = fnv1a64(token)
            val sign = if (seed and 1L == 0L) 1f else -1f
            weights[index] = sign * (1f + ln(frequency.toFloat()))
            fillProjectionRow(rows, index * dimensions, seed)
            index++
        }

        val termWeights = tensor<FP32, Float>(context, FP32::class) {
            tensor { shape(1, termCount) { fromArray(weights) } }
        }
        val projection = tensor<FP32, Float>(context, FP32::class) {
            tensor { shape(termCount, dimensions) { fromArray(rows) } }
        }
        val projected = termWeights.ops.matmul(termWeights, projection)

        return normalize(projected.data.copyToFloatArray())
    }

    override fun close() {
        // DirectCpuExecutionContext holds no native resources today; the seam stays AutoCloseable
        // so a model-backed engine can take this class's place without an API change.
    }

    /** ±1/sqrt(D) entries drawn from a splitmix64 bit stream seeded by the token hash. */
    private fun fillProjectionRow(rows: FloatArray, offset: Int, seed: Long) {
        val magnitude = 1f / sqrt(dimensions.toFloat())
        var state = seed
        var bits = 0L
        var bitsLeft = 0
        for (dim in 0 until dimensions) {
            if (bitsLeft == 0) {
                state += -0x61c8864680b583ebL // splitmix64 golden gamma
                var z = state
                z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
                z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
                bits = z xor (z ushr 31)
                bitsLeft = 64
            }
            rows[offset + dim] = if (bits and 1L == 0L) magnitude else -magnitude
            bits = bits ushr 1
            bitsLeft--
        }
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sumOfSquares = 0.0
        for (value in vector) sumOfSquares += value.toDouble() * value
        val norm = sqrt(sumOfSquares)
        if (norm == 0.0) return vector
        for (i in vector.indices) vector[i] = (vector[i] / norm).toFloat()
        return vector
    }

    companion object {
        const val DEFAULT_DIMENSIONS = 256

        /** Written into the bundle manifest; the runtime refuses vectors from any other id. */
        const val MODEL_ID = "skainet-hrp-v1"

        internal fun tokenize(text: String): List<String> {
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            for (char in text.lowercase()) {
                if (char.isLetterOrDigit()) current.append(char)
                else if (current.isNotEmpty()) { tokens += current.toString(); current.clear() }
            }
            if (current.isNotEmpty()) tokens += current.toString()
            return tokens
        }

        private fun termFrequencies(tokens: List<String>): Map<String, Int> {
            val counts = LinkedHashMap<String, Int>()
            for (token in tokens) counts[token] = (counts[token] ?: 0) + 1
            return counts
        }

        private fun fnv1a64(token: String): Long {
            var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
            for (byte in token.encodeToByteArray()) {
                hash = hash xor (byte.toLong() and 0xff)
                hash *= 0x100000001b3L
            }
            return hash
        }
    }
}
