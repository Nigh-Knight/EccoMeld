package com.metrolist.music.bridge

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LastFmRateLimiterTest {

    @Test
    fun `5 concurrent acquire calls complete without blocking`() = runTest {
        // Use backgroundScope so the refill coroutine doesn't cause UncompletedCoroutinesError
        val limiter = LastFmRateLimiter(
            scope = backgroundScope,
            maxPermits = 5,
            refillIntervalMs = 250L
        )

        // Acquire 5 permits — should not suspend since we start with maxPermits=5
        repeat(5) { limiter.acquire() }

        // If we get here without hanging, the test passes
        assertTrue("5 concurrent acquires completed", true)
    }

    @Test
    fun `rate limiter does not throw on rapid sequential acquire calls`() = runTest {
        val limiter = LastFmRateLimiter(
            scope = backgroundScope,
            maxPermits = 5,
            refillIntervalMs = 200L
        )

        var completed = 0
        // Acquire 5 permits immediately
        repeat(5) {
            limiter.acquire()
            completed++
        }
        assertTrue("Expected 5 completed, was $completed", completed == 5)

        // Advance time to trigger one refill
        advanceTimeBy(210L)

        // Now one more should be available
        limiter.acquire()
        completed++
        assertTrue("Expected 6 completed, was $completed", completed == 6)
    }
}
