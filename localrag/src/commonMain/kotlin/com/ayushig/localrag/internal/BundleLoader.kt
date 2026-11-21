package com.ayushig.localrag.internal

import com.ayushig.localrag.LocalRagPlatform
import com.ayushig.localrag.core.bundle.Bundle
import com.ayushig.localrag.core.bundle.BundleReader
import kotlinx.coroutines.CancellationException
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Decides which bundle to load and swaps in a downloaded one atomically.
 *
 * The download side is deliberately not built. What is built is the seam: a version comparison, a
 * signature hook and an atomic rename, so shipping content over the air later is an addition
 * rather than a redesign of how the runtime finds its index.
 */
internal class BundleLoader(
    private val platform: LocalRagPlatform,
    private val assetPath: String,
    private val signatureVerifier: SignatureVerifier,
) {

    fun load(): LoadedBundle {
        val assetBundle = BundleReader().read(platform.readBundledBytes(assetPath))

        val fileSystem = FileSystem.SYSTEM
        val target = downloadedFile()
        if (!fileSystem.exists(target)) return LoadedBundle(assetBundle, Origin.ASSET)

        val candidate = try {
            BundleReader().read(target, fileSystem)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return LoadedBundle(assetBundle, Origin.ASSET)
        }

        // Only a strictly newer bundle wins, so a rollback in the app binary takes precedence
        // over a stale download.
        if (candidate.manifest.contentVersion <= assetBundle.manifest.contentVersion) {
            return LoadedBundle(assetBundle, Origin.ASSET)
        }
        if (!signatureVerifier.verify(target.toString())) {
            return LoadedBundle(assetBundle, Origin.ASSET_SIGNATURE_REJECTED)
        }
        return LoadedBundle(candidate, Origin.DOWNLOADED)
    }

    /**
     * Publishes a downloaded bundle by writing it to a staging file and renaming it into place,
     * so a process death mid-write can never leave a half-written index where a whole one is
     * expected.
     */
    fun install(incoming: String): Boolean {
        if (!signatureVerifier.verify(incoming)) return false
        val fileSystem = FileSystem.SYSTEM
        return try {
            val target = downloadedFile()
            val parent = target.parent ?: return false
            fileSystem.createDirectories(parent)
            val staging = parent / (target.name + STAGING_SUFFIX)
            if (fileSystem.exists(staging)) fileSystem.delete(staging)
            fileSystem.copy(incoming.toPath(), staging)
            fileSystem.atomicMove(staging, target)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            false
        }
    }

    private fun downloadedFile(): Path =
        platform.downloadsDirectory.toPath() / assetPath.substringAfterLast('/')

    internal enum class Origin { ASSET, DOWNLOADED, ASSET_SIGNATURE_REJECTED }

    internal data class LoadedBundle(val bundle: Bundle, val origin: Origin)

    private companion object {
        const val STAGING_SUFFIX = ".staging"
    }
}

/**
 * Gate for bundles that did not ship inside the app package.
 *
 * The default refuses everything. A host app that turns on content delivery supplies a real
 * implementation; refusing by default means forgetting to do so cannot silently accept an
 * unsigned index.
 */
fun interface SignatureVerifier {
    fun verify(path: String): Boolean

    companion object {
        val RejectAll = SignatureVerifier { false }
    }
}
