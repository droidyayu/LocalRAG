package com.ayushig.localrag.internal

internal actual object PlatformLog {
    actual fun log(level: LogLevel, tag: String, message: String) {
        println("${level.name} $tag: $message")
    }
}
