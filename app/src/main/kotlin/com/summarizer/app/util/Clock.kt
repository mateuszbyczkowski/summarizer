package com.summarizer.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The current time (epoch millis), now and then at the start of every minute, so relative times
 * ("5 min ago") and date groups ("Today") stay correct while a screen is open.
 */
fun minuteTicks(): Flow<Long> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(now)
        delay(60_000 - now % 60_000)
    }
}
