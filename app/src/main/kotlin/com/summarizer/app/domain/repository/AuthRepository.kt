package com.summarizer.app.domain.repository

import com.summarizer.app.domain.model.PinVerification

interface AuthRepository {
    suspend fun hasPin(): Boolean
    suspend fun setPin(pin: String): Result<Unit>
    suspend fun verifyPin(pin: String): PinVerification
    suspend fun clearPin()

    /** When the unlocked app was last in use; drives how long an unlock stays valid. */
    suspend fun getLastActiveAt(): Long?
    suspend fun setLastActiveAt(timestamp: Long)
}
