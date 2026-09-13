package com.ayushig.localrag.core.bundle

import com.ayushig.localrag.core.document.Chunk
import com.ayushig.localrag.core.document.Chunker
import com.ayushig.localrag.core.index.Bm25Index
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Writes the bundle as a ZIP of JSON plus one binary blob.
 *
 * A ZIP rather than a custom container because it is inspectable with tools everyone already has,
 * and there is no hand-rolled parser to get subtly wrong. JSON everywhere except the vectors: at a
 * few thousand chunks it parses in tens of milliseconds, deflate reclaims the size, and being able
 * to read the index in a text editor is worth more than the bytes.
 */
class BundleWriter(private val json: Json = BundleJson.format) {

    fun write(
        output: OutputStream,
        chunks: List<Chunk>,
        vectors: FloatArray?,
        clusters: List<Cluster>,
        contentVersion: Int,
        builtAt: String,
        embedding: EmbeddingInfo?,
    ) {
        val index = Bm25Index.build(chunks)
        val snapshot = index.snapshot()

        require(vectors == null || embedding != null) {
            "vectors were supplied without an embedding block; the runtime could not verify parity"
        }
        if (vectors != null && embedding != null) {
            require(vectors.size == chunks.size * embedding.dimensions) {
                "expected ${chunks.size * embedding.dimensions} floats for ${chunks.size} chunks " +
                    "at ${embedding.dimensions} dimensions, got ${vectors.size}"
            }
        }

        val manifest = BundleManifest(
            contentVersion = contentVersion,
            builtAt = builtAt,
            chunkCount = chunks.size,
            chunkerVersion = Chunker.VERSION,
            bm25 = Bm25Params(snapshot.k1, snapshot.b, snapshot.averageChunkLength),
            embedding = embedding,
        )

        ZipOutputStream(output).use { zip ->
            zip.writeText(BundleEntries.MANIFEST, json.encodeToString(manifest))
            zip.writeText(BundleEntries.CHUNKS, json.encodeToString(chunks.map(StoredChunk::from)))
            zip.writeText(BundleEntries.BM25, json.encodeToString(snapshot))
            zip.writeText(BundleEntries.CLUSTERS, json.encodeToString(clusters))
            if (vectors != null) {
                zip.writeEntry(BundleEntries.VECTORS) { it.write(vectors.toLittleEndianBytes()) }
            }
        }
    }

    private fun ZipOutputStream.writeText(name: String, content: String) {
        writeEntry(name) { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    private fun ZipOutputStream.writeEntry(name: String, body: (OutputStream) -> Unit) {
        // A fixed timestamp keeps the bundle byte-comparable between builds of identical content.
        putNextEntry(ZipEntry(name).apply { time = FIXED_ENTRY_TIME })
        body(this)
        closeEntry()
    }

    private fun FloatArray.toLittleEndianBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (value in this) buffer.putFloat(value)
        return buffer.array()
    }

    private companion object {
        const val FIXED_ENTRY_TIME = 0L
    }
}

object BundleJson {
    val format: Json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
        // A BM25-only bundle must have no embedding block at all, not a null one: the runtime
        // treats the block as a parity contract, and an explicit null invites a reader to think
        // one was intended.
        explicitNulls = false
    }
}
