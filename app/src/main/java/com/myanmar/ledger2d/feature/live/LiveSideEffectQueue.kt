package com.myanmar.ledger2d.feature.live

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serializes synchronous persistence/display side effects after the caller's state lock is released.
 *
 * Dispatching while the state lock is held only enqueues work. The caller must call [drain]
 * after leaving that critical section. Actions are drained in enqueue order and never overlap.
 */
internal class LiveSideEffectQueue(
    private val stateLockHeldByCurrentThread: () -> Boolean,
) {
    private val queueLock = Any()
    private val pending = ArrayDeque<() -> Unit>()
    private val draining = AtomicBoolean(false)

    fun dispatch(effect: () -> Unit) {
        synchronized(queueLock) {
            pending.addLast(effect)
        }
        drain()
    }

    fun drain() {
        if (stateLockHeldByCurrentThread()) return

        while (draining.compareAndSet(false, true)) {
            try {
                while (true) {
                    val next = synchronized(queueLock) {
                        if (pending.isEmpty()) null else pending.removeFirst()
                    } ?: break

                    // A persistence callback must not prevent later committed effects from running.
                    try {
                        next()
                    } catch (_: Exception) {
                        // Callers keep their existing best-effort persistence contract.
                    }
                }
            } finally {
                draining.set(false)
            }

            val hasPending = synchronized(queueLock) { pending.isNotEmpty() }
            if (!hasPending || stateLockHeldByCurrentThread()) return
        }
    }
}
