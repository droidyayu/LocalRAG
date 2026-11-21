package com.ayushig.localrag.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val defaultIoDispatcher: CoroutineDispatcher = Dispatchers.Default
