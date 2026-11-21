package com.ayushig.localrag.demo.platform

import android.util.Log

actual object AppLog {
    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    actual fun w(tag: String, message: String) {
        Log.w(tag, message)
    }
}
