package com.ayushig.localrag.core.bundle

import com.ayushig.localrag.core.index.Bm25Index
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json

class BundleReader(private val json: Json = BundleJson.format) {

    fun read(input: InputStream): Bundle {
        var manifestJson: String? = null
        var chunksJson: String? = null
        var bm25Json: String? = null
        var clustersJson: String? = null
        var vectorBytes: ByteArray? = null

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val bytes = zip.readBytes()
                when (entry.name) {
                    BundleEntries.MANIFEST -> manifestJson = bytes.decodeToString()
                    BundleEntries.CHUNKS -> chunksJson = bytes.decodeToString()
                    BundleEntries.BM25 -> bm25Json = bytes.decodeToString()
                    BundleEntries.CLUSTERS -> clustersJson = bytes.decodeToString()
                    BundleEntries.VECTORS -> vectorBytes = bytes
                }
                zip.closeEntry()
            }
        }

        val manifest = manifestJson?.let { json.decodeFromString<BundleManifest>(it) }
            ?: throw BundleFormatException("bundle has no ${BundleEntries.MANIFEST}")
        if (manifest.bundleFormatVersion > BundleManifest.CURRENT_FORMAT_VERSION) {
            throw BundleFormatException(
                "bundle format version ${manifest.bundleFormatVersion} is newer than the " +
                    "supported ${BundleManifest.CURRENT_FORMAT_VERSION}",
            )
        }

        val chunks = chunksJson?.let { json.decodeFromString<List<StoredChunk>>(it) }
            ?: throw BundleFormatException("bundle has no ${BundleEntries.CHUNKS}")
        val snapshot = bm25Json?.let { json.decodeFromString<Bm25Index.Snapshot>(it) }
            ?: throw BundleFormatException("bundle has no ${BundleEntries.BM25}")
        val clusters = clustersJson?.let { json.decodeFromString<List<Cluster>>(it) } ?: emptyList()

        val vectors = vectorBytes?.let { bytes ->
            val dimensions = manifest.embedding?.dimensions
                ?: throw BundleFormatException(
                    "bundle carries ${BundleEntries.VECTORS} but no embedding block to describe it",
                )
            val expected = manifest.chunkCount * dimensions * Float.SIZE_BYTES
            if (bytes.size != expected) {
                throw BundleFormatException(
                    "${BundleEntries.VECTORS} is ${bytes.size} bytes, expected $expected for " +
                        "${manifest.chunkCount} chunks at $dimensions dimensions",
                )
            }
            bytes.toFloatArrayLittleEndian()
        }

        return Bundle(
            manifest = manifest,
            chunks = chunks,
            bm25 = Bm25Index.from(snapshot),
            vectors = vectors,
            clusters = clusters,
        )
    }

    private fun ByteArray.toFloatArrayLittleEndian(): FloatArray {
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(size / Float.SIZE_BYTES) { buffer.getFloat() }
    }
}

class BundleFormatException(message: String) : IllegalStateException(message)
