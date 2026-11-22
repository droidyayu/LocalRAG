package com.ayushig.localrag.core.bundle

import com.ayushig.localrag.core.text.Tokenizer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reads the golden bundle bytes on every platform.
 *
 * The bundle was written once by the JVM BundleWriter; these tests prove the common BundleReader
 * (okio zip, explicit little-endian vector decode) reads those exact bytes back, so the same test
 * passes unchanged on iOS.
 */
class BundleReaderGoldenTest {

    private fun readGolden(): Bundle = BundleReader().read(GoldenBundleFixture.bytes())

    @Test
    fun goldenManifestAndChunks() {
        val bundle = readGolden()

        assertEquals(GoldenBundleFixture.CONTENT_VERSION, bundle.manifest.contentVersion)
        assertEquals(GoldenBundleFixture.BUILT_AT, bundle.manifest.builtAt)
        assertEquals(GoldenBundleFixture.CHUNK_COUNT, bundle.manifest.chunkCount)
        assertEquals(GoldenBundleFixture.DIMENSIONS, bundle.manifest.embedding?.dimensions)
        assertEquals(GoldenBundleFixture.CHUNK_COUNT, bundle.chunks.size)
        assertEquals(GoldenBundleFixture.CHUNK_IDS, bundle.chunks.map { it.chunkId })
    }

    @Test
    fun goldenVectorsDecodeLittleEndian() {
        val vectors = assertNotNull(readGolden().vectors)

        assertEquals(
            GoldenBundleFixture.CHUNK_COUNT * GoldenBundleFixture.DIMENSIONS,
            vectors.size,
        )
        assertContentEquals(GoldenBundleFixture.VECTORS, vectors)

        // Spot checks: a negative, a fraction, and a value whose little-endian low byte is
        // non-zero, proving the byte order rather than a lucky zero pattern.
        assertEquals(-2.5f, vectors[0])
        assertEquals(0.125f, vectors[1])
        assertEquals(0.1f, vectors[2])
        assertTrue(vectors[2].toBits() and 0xFF != 0, "0.1f must keep its non-zero low byte")
    }

    @Test
    fun goldenRetrievalMatchesSdk() {
        // The same call the SDK makes: tokenize, BM25 search, map back to chunk ids.
        val bundle = readGolden()
        fun topIds(query: String): List<String> =
            bundle.bm25.search(Tokenizer.tokenize(query), 5)
                .map { bundle.chunks[it.chunkIndex].chunkId }

        assertEquals(GoldenBundleFixture.QUERY_1_TOP_IDS, topIds(GoldenBundleFixture.QUERY_1))
        assertEquals(GoldenBundleFixture.QUERY_2_TOP_IDS, topIds(GoldenBundleFixture.QUERY_2))
    }

    @Test
    fun bytesThatAreNotAZipThrow() {
        val failure = assertFailsWith<BundleFormatException> {
            BundleReader().read("hello".encodeToByteArray())
        }
        assertEquals(GoldenBundleFixture.NO_MANIFEST_MESSAGE, failure.message)
    }

    @Test
    fun zipWithoutManifestThrows() {
        val failure = assertFailsWith<BundleFormatException> {
            BundleReader().read(GoldenBundleFixture.noManifestBytes())
        }
        assertEquals(GoldenBundleFixture.NO_MANIFEST_MESSAGE, failure.message)
    }

    @Test
    fun vectorsOfTheWrongLengthThrow() {
        val failure = assertFailsWith<BundleFormatException> {
            BundleReader().read(GoldenBundleFixture.wrongVectorsBytes())
        }
        assertEquals(GoldenBundleFixture.WRONG_VECTORS_MESSAGE, failure.message)
    }
}
