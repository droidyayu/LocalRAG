package com.ayushig.localrag.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * A new call cancels the previous one and then WAITS for it to finish. Cancelling alone is not
 * enough: the previous call may still be using the native engine for a moment after it is
 * cancelled, and a second concurrent use of one native conversation is a crash.
 */
class SingleFlightJoinTest {

    @Test
    fun theSecondCallStartsOnlyAfterTheFirstHasEnded() = runTest {
        val flight = SingleFlight()
        val events = mutableListOf<String>()

        val first = launch {
            flight.run {
                events += "start1"
                try {
                    awaitCancellation()
                } finally {
                    events += "end1"
                }
            }
        }
        advanceUntilIdle()
        val second = launch { flight.run { events += "start2" } }
        advanceUntilIdle()

        assertEquals(listOf("start1", "end1", "start2"), events)
        assertTrue(first.isCancelled)
        assertTrue(second.isCompleted)
    }

    @Test
    fun aFirstCallThatCleansUpSlowlyStillFinishesBeforeTheSecondStarts() = runTest {
        val flight = SingleFlight()
        val events = mutableListOf<String>()

        launch {
            flight.run {
                events += "start1"
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        delay(500)
                        events += "end1"
                    }
                }
            }
        }
        advanceUntilIdle()
        launch { flight.run { events += "start2" } }
        advanceUntilIdle()

        assertEquals(listOf("start1", "end1", "start2"), events)
    }

    @Test
    fun sequentialCallsNeverBlockEachOther() = runTest {
        val flight = SingleFlight()
        val events = mutableListOf<String>()

        flight.run { events += "one" }
        flight.run { events += "two" }

        assertEquals(listOf("one", "two"), events)
    }
}
