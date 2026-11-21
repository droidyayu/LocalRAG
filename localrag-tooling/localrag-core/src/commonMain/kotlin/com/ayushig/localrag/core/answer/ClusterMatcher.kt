package com.ayushig.localrag.core.answer

import com.ayushig.localrag.core.bundle.Cluster
import com.ayushig.localrag.core.index.Bm25Index
import com.ayushig.localrag.core.text.Tokenizer

/**
 * Matches a query against questions that were answered ahead of time.
 *
 * Matching is BM25 over the cluster questions, deliberately the same mechanism as document
 * retrieval, so precomputed answers work on a device with no models at all rather than only on
 * the tier that can embed.
 *
 * A precomputed answer is written by a human, so it beats anything generated when it fits: the
 * threshold exists to be sure it fits.
 */
class ClusterMatcher(
    private val clusters: List<Cluster>,
    /**
     * Share of the query content words a cluster must cover to be served.
     *
     * Coverage rather than a BM25 score threshold: BM25 magnitudes depend on how many clusters
     * exist, so any absolute cutoff would silently stop matching as the cluster list grew.
     */
    private val minimumCoverage: Float = 0.6f,
    /**
     * A one-word query covers a cluster trivially without expressing an intent, so a canned
     * answer needs more than that to be served.
     */
    private val minimumQueryTerms: Int = 2,
) {

    private val index: Bm25Index? = if (clusters.isEmpty()) {
        null
    } else {
        Bm25Index.build(clusters.map(::asChunk))
    }

    fun match(query: String): Cluster? {
        val active = index ?: return null
        val terms = Tokenizer.tokenize(query)
        if (terms.size < minimumQueryTerms) return null

        val best = active.search(terms, 1).firstOrNull() ?: return null
        val candidate = clusters[best.chunkIndex]

        val questionTerms = candidate.questions
            .flatMapTo(mutableSetOf()) { Tokenizer.tokenize(it) }
        val covered = terms.count { it in questionTerms }.toFloat() / terms.size
        return if (covered >= minimumCoverage) candidate else null
    }

    /**
     * Clusters are indexed through the ordinary chunk path so scoring stays identical: the
     * questions become the searchable text, and the answer is never matched against.
     */
    private fun asChunk(cluster: Cluster) = com.ayushig.localrag.core.document.Chunk(
        chunkId = cluster.id,
        docId = cluster.id,
        title = cluster.questions.firstOrNull().orEmpty(),
        heading = null,
        category = "",
        aliases = cluster.questions.drop(1),
        screen = null,
        appVersionMin = null,
        appVersionMax = null,
        embeddedText = cluster.questions.joinToString(" "),
        displayText = cluster.questions.joinToString(" "),
        tokenCount = 0,
    )
}
