package com.ayushig.localrag.android.internal

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext

/**
 * One in-flight generation per process, shared by every entry point that touches the engine.
 *
 * A new call cancels the previous one: the native conversation is not safe for concurrent use,
 * and answering a stale question after the user already asked another is never what is wanted.
 * The slot clears itself when its owner finishes, so sequential calls never block each other.
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

    private suspend fun enter() {
        val self = currentCoroutineContext()[Job]
        mutex.withLock {
            inFlight?.takeIf { it !== self }?.cancel()
            inFlight = self
        }
    }

    private suspend fun exit() {
        val self = currentCoroutineContext()[Job]
        mutex.withLock {
            if (inFlight === self) inFlight = null
        }
    }
}
