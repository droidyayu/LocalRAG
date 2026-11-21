package com.ayushig.localrag.internal

import com.ayushig.localrag.LocalRagPlatform
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

/**
 * The iOS host resources behind [LocalRagPlatform]: the app bundle for the bundled bytes, the
 * Application Support directory for a downloaded replacement bundle, and the memory-warning
 * notification for pressure callbacks.
 */
internal class IosPlatform : LocalRagPlatform {
    override fun readBundledBytes(path: String): ByteArray {
        val base = NSBundle.mainBundle.resourcePath
            ?: throw IllegalStateException("app bundle has no resource path")
        val source = FileSystem.SYSTEM.source("$base/$path".toPath()).buffer()
        try {
            return source.readByteArray()
        } finally {
            source.close()
        }
    }

    override val downloadsDirectory: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true,
        ).first() as String
        val dir = "$base/localrag"
        FileSystem.SYSTEM.createDirectories(dir.toPath())
        dir
    }

    override fun onMemoryPressure(handler: () -> Unit): () -> Unit {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            UIApplicationDidReceiveMemoryWarningNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { _ -> handler() },
        )
        return { center.removeObserver(observer) }
    }
}
