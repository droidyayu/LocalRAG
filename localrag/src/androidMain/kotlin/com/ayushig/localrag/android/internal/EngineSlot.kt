package com.ayushig.localrag.android.internal

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holds the process-wide slot for the generation engine.
 *
 * One Engine per process: a second live instance means a native out-of-memory kill rather than a
 * catchable error. Creation therefore goes through [acquire], and a refused caller runs without
 * a generator exactly as it would on any other engine failure.
 */
internal object EngineSlot {
    private val held = AtomicBoolean(false)

    /** True when no engine is live in this process; the caller now owns the slot until [release]. */
    fun acquire(): Boolean = held.compareAndSet(false, true)

    fun release() {
        held.set(false)
    }
}
