package com.ayushig.localrag.core

import com.ayushig.localrag.core.answer.ClusterMatcher
import com.ayushig.localrag.core.bundle.Cluster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClusterMatcherTest {

    private val clusters = listOf(
        Cluster(
            id = "withdrawal-timing",
            questions = listOf(
                "how long does a withdrawal take",
                "when will my withdrawal arrive",
                "withdrawal settlement time",
            ),
            answer = "Most withdrawals settle within two working days.",
            chunkIds = listOf("withdraw-funds#how-long-it-takes"),
        ),
        Cluster(
            id = "kyc-timing",
            questions = listOf("how long does kyc review take", "when will my kyc be approved"),
            answer = "A submitted document is usually reviewed within two working days.",
            chunkIds = listOf("kyc-status#in-review"),
        ),
    )

    private val matcher = ClusterMatcher(clusters)

    @Test
    fun `matches a paraphrase of a clustered question`() {
        assertEquals("withdrawal-timing", matcher.match("when will my withdrawal arrive")?.id)
        assertEquals("kyc-timing", matcher.match("how long does kyc review take")?.id)
    }

    @Test
    fun `an unrelated question matches nothing`() {
        assertNull(matcher.match("where is my gold stored"))
    }

    @Test
    fun `a weak match is rejected rather than guessed`() {
        // A single common word must not be enough to serve a canned answer.
        assertNull(matcher.match("how"))
    }

    @Test
    fun `an empty cluster list never matches`() {
        assertNull(ClusterMatcher(emptyList()).match("how long does a withdrawal take"))
    }
}
