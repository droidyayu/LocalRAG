package com.ayushig.localrag.core.bundle

import com.ayushig.localrag.core.index.Bm25Index
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import kotlin.random.Random

class BundleReader(private val json: Json = BundleJson.format) {

    fun read(path: Path, fileSystem: FileSystem = FileSystem.SYSTEM): Bundle {
        val zip = try {
            fileSystem.openZip(path)
        } catch (e: IOException) {
            throw BundleFormatException("bundle has no ${BundleEntries.MANIFEST}")
        }
        try {
            val manifestJson = readText(zip, BundleEntries.MANIFEST)
                ?: throw BundleFormatException("bundle has no ${BundleEntries.MANIFEST}")
            val manifest = json.decodeFromString<BundleManifest>(manifestJson)
            if (manifest.bundleFormatVersion > BundleManifest.CURRENT_FORMAT_VERSION) {
                throw BundleFormatException(
                    "bundle format version ${manifest.bundleFormatVersion} is newer than the " +
                        "supported ${BundleManifest.CURRENT_FORMAT_VERSION}",
                )
            }

            val chunksJson = readText(zip, BundleEntries.CHUNKS)
                ?: throw BundleFormatException("bundle has no ${BundleEntries.CHUNKS}")
            val chunks = json.decodeFromString<List<StoredChunk>>(chunksJson)
            val bm25Json = readText(zip, BundleEntries.BM25)
                ?: throw BundleFormatException("bundle has no ${BundleEntries.BM25}")
            val snapshot = json.decodeFromString<Bm25Index.Snapshot>(bm25Json)
            val clustersJson = readText(zip, BundleEntries.CLUSTERS)
            val clusters =
                clustersJson?.let { body -> json.decodeFromString<List<Cluster>>(body) } ?: emptyList()

            val vectorBytes = readEntry(zip, BundleEntries.VECTORS)
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
        } finally {
            zip.close()
        }
    }

    fun read(bytes: ByteArray): Bundle {
        val fileSystem = FileSystem.SYSTEM
        val path = writeTempCopy(bytes, fileSystem)
        try {
            return read(path, fileSystem)
        } finally {
            if (fileSystem.exists(path)) fileSystem.delete(path)
        }
    }

    private fun writeTempCopy(bytes: ByteArray, fileSystem: FileSystem): Path {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
        var failures = 0
        while (true) {
            val path = directory / "localrag-bundle-${Random.nextLong().toULong().toString(16)}.zip"
            try {
                fileSystem.write(path, mustCreate = true) { write(bytes) }
                return path
            } catch (e: IOException) {
                if (++failures >= MAX_TEMP_NAME_ATTEMPTS) throw e
            }
        }
    }

    private fun readText(zip: FileSystem, entry: String): String? {
        return readEntry(zip, entry)?.decodeToString()
    }

    private fun readEntry(zip: FileSystem, entry: String): ByteArray? {
        val path = "/$entry".toPath()
        return try {
            if (!zip.exists(path)) return null
            val source = zip.source(path).buffer()
            try {
                source.readByteArray()
            } finally {
                source.close()
            }
        } catch (e: IOException) {
            // An entry that exists but cannot be read leaves the bundle unusable, exactly like a
            // missing one, so it surfaces as the same BundleFormatException below.
            null
        }
    }

    private fun ByteArray.toFloatArrayLittleEndian(): FloatArray {
        val count = size / Float.SIZE_BYTES
        return FloatArray(count) { index ->
            val offset = index * Float.SIZE_BYTES
            val bits = (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8) or
                ((this[offset + 2].toInt() and 0xFF) shl 16) or
                (this[offset + 3].toInt() shl 24)
            Float.fromBits(bits)
        }
    }

    private companion object {
        const val MAX_TEMP_NAME_ATTEMPTS = 10
    }
}

class BundleFormatException(message: String) : IllegalStateException(message)
