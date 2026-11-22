package com.ayushig.localrag.demo.platform

actual object AppLog {
    actual fun d(tag: String, message: String) {
        println("$tag: $message")
    }

    actual fun w(tag: String, message: String) {
        println("$tag: $message")
    }
}
