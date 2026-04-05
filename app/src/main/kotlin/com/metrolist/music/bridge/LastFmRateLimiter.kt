/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * Rate limiter for Last.fm API calls using a token-bucket approach.
 * Allows up to [maxPermits] concurrent requests, refilling one permit
 * every [refillIntervalMs] milliseconds.
 *
 * With maxPermits=5 and refillIntervalMs=200ms, the effective rate is:
 * up to 5 req/burst, then 5 req/sec sustained.
 *
 * Port of eccopath/lib/rateLimiter.ts TokenBucket.
 */
class LastFmRateLimiter(
    scope: CoroutineScope,
    private val maxPermits: Int = 5,
    private val refillIntervalMs: Long = 200L,
) {
    private val semaphore = Semaphore(maxPermits)

    init {
        scope.launch {
            while (true) {
                delay(refillIntervalMs)
                if (semaphore.availablePermits < maxPermits) {
                    semaphore.release()
                }
            }
        }
    }

    suspend fun acquire() = semaphore.acquire()
}
