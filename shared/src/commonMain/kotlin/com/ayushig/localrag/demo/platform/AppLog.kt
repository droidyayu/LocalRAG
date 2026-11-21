package com.ayushig.localrag.demo.platform

/** Logger seam: Android writes to logcat, iOS prints to stdout. */
expect object AppLog {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
}
