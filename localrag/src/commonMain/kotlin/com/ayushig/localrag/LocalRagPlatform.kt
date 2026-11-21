package com.ayushig.localrag

/**
 * Everything LocalRag needs from the host operating system, so the SDK itself stays common code.
 *
 * Android implements it over a Context (assets, files dir, ComponentCallbacks2); iOS over the app
 * bundle, Application Support and the memory-warning notification.
 */
internal interface LocalRagPlatform {

    /** The bytes of the docs bundle packed into the app (an Android asset, an iOS bundle resource). */
    fun readBundledBytes(path: String): ByteArray

    /** A writable, app-private directory where a downloaded replacement bundle may live. */
    val downloadsDirectory: String

    /**
     * Calls [handler] when the OS asks the app to free memory. Returns a function that stops
     * listening; calling it twice is harmless.
     */
    fun onMemoryPressure(handler: () -> Unit): () -> Unit
}
