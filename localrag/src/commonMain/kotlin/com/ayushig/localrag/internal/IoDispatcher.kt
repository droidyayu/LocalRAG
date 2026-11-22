package com.ayushig.localrag.internal

import kotlinx.coroutines.CoroutineDispatcher

/** The shared background dispatcher: IO on Android, Default on iOS. */
internal expect val defaultIoDispatcher: CoroutineDispatcher
