package com.ayushig.localrag.internal

import com.ayushig.localrag.LocalRagPlatform
import com.ayushig.localrag.core.bundle.BundleJson
import com.ayushig.localrag.core.bundle.BundleManifest
import com.ayushig.localrag.testing.SdkTestBundle
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip

/**
 * The two bundle-swap edges with no existing coverage: a downloaded candidate that is not
 * strictly newer than the asset is ignored, and a strictly newer candidate that fails the
 * signature check falls back to the bundled asset.
 */
class BundleLoaderTest {

    private class TestPlatform(
        private val bundle: ByteArray = SdkTestBundle.bytes(),
        override val downloadsDirectory: String,
    ) : LocalRagPlatform {
        override fun readBundledBytes(path: String): ByteArray = bundle

        override fun onMemoryPressure(handler: () -> Unit): () -> Unit = {}
    }

    @Test
    fun anOlderOrEqualDownloadedCandidateIsIgnored() {
        val dir = tempDir()
        try {
            val platform = TestPlatform(downloadsDirectory = dir.toString())
            val loader = BundleLoader(platform, ASSET_PATH, SignatureVerifier.RejectAll)

            FileSystem.SYSTEM.write(dir / FILE_NAME) { write(SdkTestBundle.bytes()) }
            val equal = loader.load()
            assertEquals(BundleLoader.Origin.ASSET, equal.origin)
            assertEquals(47, equal.bundle.manifest.contentVersion)

            FileSystem.SYSTEM.write(dir / FILE_NAME) {
                write(rebundledWithVersion(SdkTestBundle.bytes(), 46))
            }
            val older = loader.load()
            assertEquals(BundleLoader.Origin.ASSET, older.origin)
            assertEquals(47, older.bundle.manifest.contentVersion)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }

    @Test
    fun aRejectedSignatureFallsBackToTheBundledAsset() {
        val dir = tempDir()
        try {
            val newer = rebundledWithVersion(SdkTestBundle.bytes(), 99)

            val rejected = BundleLoader(
                TestPlatform(downloadsDirectory = dir.toString()),
                ASSET_PATH,
                SignatureVerifier.RejectAll,
            )
            FileSystem.SYSTEM.write(dir / FILE_NAME) { write(newer) }
            val fallback = rejected.load()
            assertEquals(BundleLoader.Origin.ASSET_SIGNATURE_REJECTED, fallback.origin)
            assertEquals(47, fallback.bundle.manifest.contentVersion)

            val accepted = BundleLoader(
                TestPlatform(downloadsDirectory = dir.toString()),
                ASSET_PATH,
                SignatureVerifier { true },
            )
            val downloaded = accepted.load()
            assertEquals(BundleLoader.Origin.DOWNLOADED, downloaded.origin)
            assertEquals(99, downloaded.bundle.manifest.contentVersion)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }

    private fun tempDir(): Path {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
            "localrag-bundle-loader-test-${Random.nextLong().toULong().toString(16)}"
        FileSystem.SYSTEM.createDirectories(dir)
        return dir
    }

    /**
     * Rewrites a bundle with a different content version: every entry is copied byte for byte
     * except the manifest, which is re-encoded with the new version. Entries are written
     * uncompressed, which any zip reader accepts.
     */
    private fun rebundledWithVersion(bytes: ByteArray, contentVersion: Int): ByteArray {
        val fileSystem = FileSystem.SYSTEM
        val src = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
            "localrag-bundle-loader-src-${Random.nextLong().toULong().toString(16)}.zip"
        fileSystem.write(src) { write(bytes) }
        try {
            val zip = fileSystem.openZip(src)
            try {
                val entries = zip.list("/".toPath()).associate { path ->
                    val source = zip.source(path).buffer()
                    try {
                        path.name to source.readByteArray()
                    } finally {
                        source.close()
                    }
                }.toMutableMap()
                val manifest = BundleJson.format.decodeFromString<BundleManifest>(
                    entries.getValue("manifest.json").decodeToString(),
                )
                entries["manifest.json"] = BundleJson.format.encodeToString(
                    manifest.copy(contentVersion = contentVersion),
                ).encodeToByteArray()
                return storedZip(entries)
            } finally {
                zip.close()
            }
        } finally {
            fileSystem.delete(src)
        }
    }

    private fun storedZip(entries: Map<String, ByteArray>): ByteArray {
        val out = Buffer()
        val central = Buffer()
        var offset = 0L
        for ((name, data) in entries) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val size = data.size.toLong()
            out.writeIntLe(LOCAL_HEADER)
            out.writeShortLe(20)
            out.writeShortLe(0)
            out.writeShortLe(0)
            out.writeShortLe(0)
            out.writeShortLe(0)
            out.writeIntLe(crc.toInt())
            out.writeIntLe(size.toInt())
            out.writeIntLe(size.toInt())
            out.writeShortLe(nameBytes.size)
            out.writeShortLe(0)
            out.write(nameBytes)
            out.write(data)
            central.writeIntLe(CENTRAL_HEADER)
            central.writeShortLe(20)
            central.writeShortLe(20)
            central.writeShortLe(0)
            central.writeShortLe(0)
            central.writeShortLe(0)
            central.writeShortLe(0)
            central.writeIntLe(crc.toInt())
            central.writeIntLe(size.toInt())
            central.writeIntLe(size.toInt())
            central.writeShortLe(nameBytes.size)
            central.writeShortLe(0)
            central.writeShortLe(0)
            central.writeShortLe(0)
            central.writeShortLe(0)
            central.writeIntLe(0)
            central.writeIntLe(offset.toInt())
            central.write(nameBytes)
            offset += 30L + nameBytes.size + size
        }
        val centralSize = central.size
        out.write(central.readByteArray())
        out.writeIntLe(END_HEADER)
        out.writeShortLe(0)
        out.writeShortLe(0)
        out.writeShortLe(entries.size)
        out.writeShortLe(entries.size)
        out.writeIntLe(centralSize.toInt())
        out.writeIntLe(offset.toInt())
        out.writeShortLe(0)
        return out.readByteArray()
    }

    private fun crc32(bytes: ByteArray): Long {
        var crc = -1
        for (b in bytes) {
            crc = CRC_TABLE[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
        }
        return (crc xor -1).toUInt().toLong()
    }

    private companion object {
        const val ASSET_PATH = "localrag/docs.localrag"
        const val FILE_NAME = "docs.localrag"
        const val LOCAL_HEADER = 0x04034b50
        const val CENTRAL_HEADER = 0x02014b50
        const val END_HEADER = 0x06054b50
        const val CRC_POLY = -306674912

        val CRC_TABLE: IntArray = IntArray(256) { index ->
            var c = index
            repeat(8) {
                c = if (c and 1 != 0) (c ushr 1) xor CRC_POLY else c ushr 1
            }
            c
        }
    }
}
