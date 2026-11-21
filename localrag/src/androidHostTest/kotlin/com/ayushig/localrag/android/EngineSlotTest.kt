package com.ayushig.localrag.android

import com.ayushig.localrag.android.internal.EngineSlot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The process-wide engine policy, without the native engine.
 *
 * The slot object is the whole policy: Generator only calls acquire and release, so asserting the
 * slot here asserts the refusal a second LocalRag instance meets on a real device.
 */
class EngineSlotTest {

    @Test
    fun `a second acquire fails while the slot is held`() {
        assertTrue(EngineSlot.acquire())
        try {
            assertFalse(EngineSlot.acquire())
        } finally {
            EngineSlot.release()
        }
    }

    @Test
    fun `the slot is usable again after release`() {
        assertTrue(EngineSlot.acquire())
        EngineSlot.release()
        assertTrue(EngineSlot.acquire())
        EngineSlot.release()
    }

    @Test
    fun `a spare release does not break the next acquire`() {
        EngineSlot.release()
        assertTrue(EngineSlot.acquire())
        EngineSlot.release()
    }
}
