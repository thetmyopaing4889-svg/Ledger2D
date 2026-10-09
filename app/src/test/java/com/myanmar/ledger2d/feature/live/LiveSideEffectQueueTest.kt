package com.myanmar.ledger2d.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSideEffectQueueTest {
    @Test
    fun effects_dispatched_inside_state_lock_run_only_after_lock_is_released() {
        val stateLock = Any()
        val queue = LiveSideEffectQueue { Thread.holdsLock(stateLock) }
        val order = mutableListOf<String>()

        synchronized(stateLock) {
            queue.dispatch {
                assertFalse(Thread.holdsLock(stateLock))
                order += "cache"
            }
            queue.dispatch {
                assertFalse(Thread.holdsLock(stateLock))
                order += "room"
            }
            assertTrue(order.isEmpty())
        }

        assertTrue(order.isEmpty())
        queue.drain()
        assertEquals(listOf("cache", "room"), order)
    }

    @Test
    fun failed_side_effect_does_not_discard_later_queued_effects() {
        val stateLock = Any()
        val queue = LiveSideEffectQueue { Thread.holdsLock(stateLock) }
        val order = mutableListOf<Int>()

        synchronized(stateLock) {
            queue.dispatch {
                order += 1
                throw IllegalStateException("simulated cache failure")
            }
            queue.dispatch { order += 2 }
        }

        queue.drain()
        assertEquals(listOf(1, 2), order)
    }
}
