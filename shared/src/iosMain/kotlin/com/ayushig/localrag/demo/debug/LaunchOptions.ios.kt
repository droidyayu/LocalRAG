package com.ayushig.localrag.demo.debug

import platform.Foundation.NSUserDefaults

/**
 * iOS launch-argument binding. A launch argument "-localrag.engine scripted" lands in the
 * standard defaults, so the Koin graph can read it with no Swift changes.
 */
actual fun launchEngineMode(): String? =
    NSUserDefaults.standardUserDefaults.stringForKey("localrag.engine")
