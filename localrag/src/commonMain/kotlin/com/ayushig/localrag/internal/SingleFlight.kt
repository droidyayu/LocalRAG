package com.ayushig.localrag.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One in-flight generation per process, shared by every entry point that touches the engine.
 *
 * A new call cancels the previous one and then waits until it has fully ended (including slow
 * non-cancellable cleanup) before starting: cancelling alone is not enough, because the previous
 * call may still be using the native engine for a moment after it is cancelled, and a second
 * concurrent use of one native conversation is a crash. The slot clears itself when its owner
 * finishes, so sequential calls never block each other.
 */
internal class SingleFlight {
    private val mutex = Mutex()
    private var inFlight: Job? = null

    /** Streaming entries: the collecting coroutine owns the slot until its flow completes. */
    suspend fun <T> FlowCollector<T>.collectSingle(block: suspend FlowCollector<T>.() -> Unit) {
        enter()
        try {
            block()
        } finally {
            exit()
        }
    }

    /** One-shot entries: the calling coroutine owns the slot until the block returns. */
    suspend fun <R> run(block: suspend () -> R): R {
        enter()
        try {
            return block()
        } finally {
            exit()
        }
    }

    /**
     * Cancels the previous owner, if any, and joins it outside the mutex before the new owner
     * proceeds. Never joins itself: a re-entrant call from the owning coroutine proceeds without
     * waiting on its own job.
     */
    private suspend fun enter() {
        val self = currentCoroutineContext()[Job]
        val previous = mutex.withLock {
            val prev = inFlight?.takeIf { it !== self }
            prev?.cancel()
            inFlight = self
            prev
        }
        if (previous != null) {
            try {
                previous.join()
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    mutex.withLock {
                        if (inFlight === self) inFlight = null
                    }
                }
                throw cancelled
            }
        }
    }

    private suspend fun exit() {
        val self = currentCoroutineContext()[Job]
        withContext(NonCancellable) {
            mutex.withLock {
                if (inFlight === self) inFlight = null
            }
        }
    }
}
