package com.ayushig.localrag.demo.platform

import com.ayushig.localrag.demo.domain.model.ModelOption

/**
 * Where the generation model files live on this device. Paths are never hardcoded: Android
 * resolves them under the app's external files directory (adb-writable); iOS under the app's
 * Documents directory.
 */
interface ModelFiles {
    /** Absolute path where [option] is expected, whether or not the file exists yet. */
    fun pathFor(option: ModelOption): String

    /** A model is only usable if it exists and is not a zero-byte or truncated download. */
    fun isPresent(option: ModelOption): Boolean

    fun present(): Set<ModelOption> =
        ModelOption.entries.filterTo(mutableSetOf(), ::isPresent)
}

/** Which generation model the assistant runs; survives restarts. */
interface ModelSelection {
    fun selected(): ModelOption?

    fun select(option: ModelOption)
}

/** Facts about the running app that the shared code shows or passes on. */
data class AppInfo(
    /** Shown in the chat top bar so a screenshot of a run always names the exact runtime. */
    val litertlmVersion: String,
    val appVersion: String,
    /** Writable directory for engine caches. */
    val cacheDir: String,
)
