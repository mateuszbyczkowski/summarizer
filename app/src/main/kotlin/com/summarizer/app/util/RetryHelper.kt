package com.summarizer.app.util

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Helper object for retry logic with exponential backoff.
 */
object RetryHelper {

    /**
     * Retry a suspending operation with exponential backoff.
     *
     * @param times Number of retry attempts (default: 3)
     * @param initialDelay Initial delay in milliseconds (default: 1000ms)
     * @param maxDelay Maximum delay in milliseconds (default: 10000ms)
     * @param factor Multiplier for exponential backoff (default: 2.0)
     * @param block The operation to retry
     * @return Result of the operation
     */
    suspend fun <T> retry(
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 10000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        var lastException: Exception? = null

        repeat(times) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Timber.w(e, "Attempt ${attempt + 1}/$times failed")

                if (attempt < times - 1) {
                    Timber.d("Retrying in ${currentDelay}ms...")
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                }
            }
        }

        throw lastException ?: Exception("Retry failed with unknown error")
    }

    /**
     * Retry a suspending operation that returns a Result.
     *
     * @param times Number of retry attempts (default: 3)
     * @param initialDelay Initial delay in milliseconds (default: 1000ms)
     * @param maxDelay Maximum delay in milliseconds (default: 10000ms)
     * @param factor Multiplier for exponential backoff (default: 2.0)
     * @param block The operation to retry
     * @return Result of the operation
     */
    suspend fun <T> retryResult(
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 10000,
        factor: Double = 2.0,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay
        var lastResult: Result<T>? = null

        repeat(times) { attempt ->
            val result = block()

            if (result.isSuccess) {
                return result
            }

            lastResult = result
            val exception = result.exceptionOrNull()
            Timber.w(exception, "Attempt ${attempt + 1}/$times failed")

            if (attempt < times - 1) {
                Timber.d("Retrying in ${currentDelay}ms...")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }

        return lastResult ?: Result.failure(Exception("Retry failed with unknown error"))
    }

    /**
     * Check if an exception is retryable.
     */
    fun isRetryable(exception: Throwable): Boolean {
        return when (exception) {
            is java.net.UnknownHostException -> true // Network issue
            is java.net.SocketTimeoutException -> true // Timeout
            is java.io.IOException -> true // IO issue
            is kotlinx.coroutines.TimeoutCancellationException -> true // Coroutine timeout
            else -> false
        }
    }
}
