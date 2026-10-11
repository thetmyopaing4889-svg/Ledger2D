package com.myanmar.ledger2d.feature.live

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Queues synchronous cache/display persistence callbacks raised by state commits.
 *
 * [dispatch] only queues work. The collector drains the queue after leaving its
 * state-lock critical section. Effects run serially in enqueue order.
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
    }

    fun drain() {
        if (stateLockHeldByCurrentThread()) return
        if (!draining.compareAndSet(false, true)) return

        try {
            while (!stateLockHeldByCurrentThread()) {
                val next = synchronized(queueLock) {
                    if (pending.isEmpty()) null else pending.removeFirst()
                } ?: break

                // Retain best-effort persistence semantics without losing later effects.
                try {
                    next()
                } catch (_: Exception) {
                    // Continue draining effects already committed in sequence.
                }
            }
        } finally {
            draining.set(false)
        }

        // Close the enqueue-after-empty / before-drain-release race.
        if (!stateLockHeldByCurrentThread() && synchronized(queueLock) { pending.isNotEmpty() }) {
            drain()
        }
    }
}
