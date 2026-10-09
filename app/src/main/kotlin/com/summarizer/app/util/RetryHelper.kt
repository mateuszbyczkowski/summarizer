package com.summarizer.app.util

import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/**
 * Helper object for retry logic with exponential backoff.
 */
object RetryHelper {

    /**
     * Retry a suspending operation that returns a Result, but only while [shouldRetry] says the
     * failure is transient (network errors, rate limits, server errors).
     *
     * @param times Number of attempts (default: 3)
     * @param initialDelay Initial delay in milliseconds (default: 1000ms)
     * @param maxDelay Maximum delay in milliseconds (default: 10000ms)
     * @param factor Multiplier for exponential backoff (default: 2.0)
     * @param shouldRetry Whether a failure is worth retrying
     * @param block The operation to retry
     * @return Result of the last attempt
     */
    suspend fun <T> retryResult(
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 10000,
        factor: Double = 2.0,
        shouldRetry: (Throwable) -> Boolean,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay
        var attempt = 0
        while (true) {
            val result = block()
            val exception = result.exceptionOrNull() ?: return result
            attempt++
            if (attempt >= times || !shouldRetry(exception)) return result

            Timber.w(exception, "Attempt $attempt/$times failed, retrying in ${currentDelay}ms")
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
}

/**
 * Like [runCatching], but rethrows [CancellationException] so coroutine cancellation is not swallowed.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
