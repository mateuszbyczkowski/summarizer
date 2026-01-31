package com.summarizer.app.data.repository

import com.summarizer.app.data.local.preferences.SecurePreferences
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.util.Constants
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val securePreferences: SecurePreferences
) : AuthRepository {

    override fun hasPin(): Boolean {
        return securePreferences.getString(Constants.KEY_PIN_HASH) != null
    }

    override fun setPin(pin: String): Result<Unit> {
        if (pin.length != Constants.PIN_LENGTH || !pin.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("PIN must be ${Constants.PIN_LENGTH} digits"))
        }

        val hashedPin = securePreferences.hashPin(pin)
        securePreferences.putString(Constants.KEY_PIN_HASH, hashedPin)

        return Result.success(Unit)
    }

    override fun verifyPin(pin: String): Boolean {
        val storedHash = securePreferences.getString(Constants.KEY_PIN_HASH) ?: return false
        val hashedPin = securePreferences.hashPin(pin)
        return hashedPin == storedHash
    }

    override fun clearPin() {
        securePreferences.remove(Constants.KEY_PIN_HASH)
        securePreferences.remove(Constants.KEY_PIN_SALT)
    }
}
