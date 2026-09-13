package com.ayushig.localrag.core.index

/**
 * Brute-force nearest neighbour over L2-normalized vectors, so cosine similarity is a plain dot
 * product.
 *
 * At a few thousand chunks and a few hundred dimensions this is well under ten milliseconds. A
 * vector database would add a dependency, a file format and a failure mode to save time nobody is
 * currently losing. Revisit when a measurement says otherwise.
 */
class VectorIndex(private val vectors: FloatArray, val dimensions: Int) {

    val chunkCount: Int = if (dimensions == 0) 0 else vectors.size / dimensions

    init {
        require(dimensions >= 0) { "dimensions must not be negative" }
        require(dimensions == 0 || vectors.size % dimensions == 0) {
            "vector data of ${vectors.size} floats does not divide into $dimensions dimensions"
        }
    }

    fun search(query: FloatArray, topK: Int): List<ScoredChunk> {
        if (topK <= 0 || chunkCount == 0) return emptyList()
        require(query.size == dimensions) {
            "query has ${query.size} dimensions, index has $dimensions"
        }

        val scored = ArrayList<ScoredChunk>(chunkCount)
        for (chunkIndex in 0 until chunkCount) {
            val offset = chunkIndex * dimensions
            var dot = 0f
            for (dimension in 0 until dimensions) {
                dot += vectors[offset + dimension] * query[dimension]
            }
            scored.add(ScoredChunk(chunkIndex, dot))
        }

        return scored
            .sortedWith(compareByDescending<ScoredChunk> { it.score }.thenBy { it.chunkIndex })
            .take(topK)
    }

    companion object {
        /** Build-time normalization keeps the runtime a dot product. */
        fun normalize(vector: FloatArray): FloatArray {
            var sum = 0.0
            for (value in vector) sum += value.toDouble() * value
            val magnitude = kotlin.math.sqrt(sum).toFloat()
            if (magnitude == 0f) return vector.copyOf()
            return FloatArray(vector.size) { vector[it] / magnitude }
        }
    }
}
