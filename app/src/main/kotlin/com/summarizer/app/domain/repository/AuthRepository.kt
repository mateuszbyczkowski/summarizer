package com.summarizer.app.domain.repository

interface AuthRepository {
    fun hasPin(): Boolean
    fun setPin(pin: String): Result<Unit>
    fun verifyPin(pin: String): Boolean
    fun clearPin()
}
