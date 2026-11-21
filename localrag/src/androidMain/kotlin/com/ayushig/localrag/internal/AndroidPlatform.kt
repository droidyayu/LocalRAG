package com.ayushig.localrag.internal

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Android host resources behind [com.ayushig.localrag.LocalRagPlatform]: APK assets for the
 * bundled bytes, the app-private files directory for a downloaded replacement bundle, and a
 * [ComponentCallbacks2] on the application context for memory warnings.
 */
internal class AndroidPlatform(context: Context) : com.ayushig.localrag.LocalRagPlatform {
    private val appContext = context.applicationContext

    override fun readBundledBytes(path: String): ByteArray =
        appContext.assets.open(path).use { it.readBytes() }

    override val downloadsDirectory: String
        get() = File(appContext.filesDir, DOWNLOAD_DIR).path

    override fun onMemoryPressure(handler: () -> Unit): () -> Unit {
        val callback = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) handler()
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Required by ComponentCallbacks2 but never used")
            override fun onLowMemory() = handler()
        }
        appContext.registerComponentCallbacks(callback)
        val stopped = AtomicBoolean(false)
        return {
            if (stopped.compareAndSet(false, true)) {
                runCatching { appContext.unregisterComponentCallbacks(callback) }
            }
        }
    }

    private companion object {
        const val DOWNLOAD_DIR = "localrag"
    }
}
