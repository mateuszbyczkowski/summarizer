package com.summarizer.app.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetryHelperTest {

    @Test
    fun `transient failures are retried until success`() = runTest {
        var attempts = 0
        val result = RetryHelper.retryResult(times = 3, shouldRetry = { it is IOException }) {
            attempts++
            if (attempts < 3) Result.failure(IOException("offline")) else Result.success("ok")
        }

        assertEquals("ok", result.getOrNull())
        assertEquals(3, attempts)
    }

    @Test
    fun `permanent failures are not retried`() = runTest {
        var attempts = 0
        val result = RetryHelper.retryResult<String>(times = 3, shouldRetry = { it is IOException }) {
            attempts++
            Result.failure(IllegalArgumentException("invalid key"))
        }

        assertTrue(result.isFailure)
        assertEquals(1, attempts)
    }
}
