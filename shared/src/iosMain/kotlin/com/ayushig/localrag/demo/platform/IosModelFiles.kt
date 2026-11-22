package com.ayushig.localrag.demo.platform

import com.ayushig.localrag.demo.domain.model.ModelOption
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSNumber

/**
 * Resolves where .litertlm model files are expected on device. Paths are never hardcoded:
 * they derive from the app's Documents directory, which maps to
 * <Application_Home>/Documents and is writable without extra entitlements.
 */
@OptIn(ExperimentalForeignApi::class)
class IosModelFiles(
    private val directory: String = defaultDocumentsDirectory(),
) : ModelFiles {
    override fun pathFor(option: ModelOption): String = "$directory/${option.fileName}"

    /** A model is only usable if it exists and is not a zero-byte or truncated download. */
    override fun isPresent(option: ModelOption): Boolean {
        val manager = NSFileManager.defaultManager
        val path = pathFor(option)
        if (!manager.fileExistsAtPath(path)) return false
        val attributes = manager.attributesOfItemAtPath(path, error = null) ?: return false
        if (attributes[NSFileType] == NSFileTypeDirectory) return false
        val size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: return false
        return size > 0L
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun defaultDocumentsDirectory(): String =
    NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).first() as String
