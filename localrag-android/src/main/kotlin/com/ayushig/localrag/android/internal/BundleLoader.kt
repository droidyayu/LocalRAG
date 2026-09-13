package com.ayushig.localrag.android.internal

import android.content.Context
import com.ayushig.localrag.core.bundle.Bundle
import com.ayushig.localrag.core.bundle.BundleReader
import java.io.File

/**
 * Decides which bundle to load and swaps in a downloaded one atomically.
 *
 * The download side is deliberately not built. What is built is the seam: a version comparison, a
 * signature hook and an atomic rename, so shipping content over the air later is an addition
 * rather than a redesign of how the runtime finds its index.
 */
internal class BundleLoader(
    private val context: Context,
    private val assetPath: String,
    private val signatureVerifier: SignatureVerifier,
) {

    fun load(): LoadedBundle {
        val assetBundle = context.assets.open(assetPath).use { BundleReader().read(it) }

        val downloaded = downloadedFile()
        if (!downloaded.isFile) return LoadedBundle(assetBundle, Origin.ASSET)

        val candidate = runCatching {
            downloaded.inputStream().use { BundleReader().read(it) }
        }.getOrNull() ?: return LoadedBundle(assetBundle, Origin.ASSET)

        // Only a strictly newer bundle wins, so a rollback in the app binary takes precedence
        // over a stale download.
        if (candidate.manifest.contentVersion <= assetBundle.manifest.contentVersion) {
            return LoadedBundle(assetBundle, Origin.ASSET)
        }
        if (!signatureVerifier.verify(downloaded)) {
            return LoadedBundle(assetBundle, Origin.ASSET_SIGNATURE_REJECTED)
        }
        return LoadedBundle(candidate, Origin.DOWNLOADED)
    }

    /**
     * Publishes a downloaded bundle by renaming it into place, so a process death mid-write can
     * never leave a half-written index where a whole one is expected.
     */
    fun install(incoming: File): Boolean {
        if (!signatureVerifier.verify(incoming)) return false
        val target = downloadedFile()
        target.parentFile?.mkdirs()
        val staging = File(target.parentFile, target.name + ".staging")
        incoming.copyTo(staging, overwrite = true)
        return staging.renameTo(target)
    }

    private fun downloadedFile(): File =
        File(File(context.filesDir, DOWNLOAD_DIR), assetPath.substringAfterLast(Char(47)))

    internal enum class Origin { ASSET, DOWNLOADED, ASSET_SIGNATURE_REJECTED }

    internal data class LoadedBundle(val bundle: Bundle, val origin: Origin)

    private companion object {
        const val DOWNLOAD_DIR = "localrag"
    }
}

/**
 * Gate for bundles that did not ship inside the APK.
 *
 * The default refuses everything. A host app that turns on content delivery supplies a real
 * implementation; refusing by default means forgetting to do so cannot silently accept an
 * unsigned index.
 */
fun interface SignatureVerifier {
    fun verify(file: File): Boolean

    companion object {
        val RejectAll = SignatureVerifier { false }
    }
}
