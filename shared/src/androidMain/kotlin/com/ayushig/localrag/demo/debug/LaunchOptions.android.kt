package com.ayushig.localrag.demo.debug

/**
 * Android launch-argument binding. [MainActivity] sets [engineMode] from the launch intent extra
 * "localrag.engine" before composition starts; the Koin graph reads it lazily at resolution.
 */
object LaunchOptions {
    var engineMode: String? = null
}

actual fun launchEngineMode(): String? = LaunchOptions.engineMode
