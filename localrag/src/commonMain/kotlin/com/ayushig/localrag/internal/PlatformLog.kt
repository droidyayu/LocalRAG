package com.ayushig.localrag.internal

internal enum class LogLevel { DEBUG, INFO, WARN }

/** The platform logger: android.util.Log on Android, a print with a level prefix on iOS. */
internal expect object PlatformLog {
    fun log(level: LogLevel, tag: String, message: String)
}
