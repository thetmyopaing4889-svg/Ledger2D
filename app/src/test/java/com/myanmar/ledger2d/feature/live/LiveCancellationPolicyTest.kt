package com.myanmar.ledger2d.feature.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCancellationPolicyTest {
    @Test
    fun suspend_failure_wrapper_propagates_cancellation_instead_of_treating_it_as_null() = runTest {
        val result = runCatching {
            runSuspendCatchingCancellable {
                throw CancellationException("cancel stale history operation")
            }
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
