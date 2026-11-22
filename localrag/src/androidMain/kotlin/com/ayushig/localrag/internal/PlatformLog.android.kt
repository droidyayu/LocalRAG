package com.ayushig.localrag.internal

import android.util.Log

internal actual object PlatformLog {
    actual fun log(level: LogLevel, tag: String, message: String) {
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
        }
    }
}
