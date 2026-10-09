package com.summarizer.app.data.repository

import com.summarizer.app.data.local.preferences.SecurePreferences
import com.summarizer.app.domain.model.PinVerification
import com.summarizer.app.domain.repository.AuthRepository
import com.summarizer.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the PIN as a salted PBKDF2 hash and rate-limits wrong entries with an
 * exponentially growing lockout.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val securePreferences: SecurePreferences
) : AuthRepository {

    companion object {
        private const val HASH_PREFIX = "pbkdf2-sha256"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_BYTES = 16

        // Deliberately lenient: a short pause after many wrong entries, never a long lockout
        private const val FREE_ATTEMPTS = 10
        private const val BASE_LOCKOUT_MS = 30_000L
        private const val MAX_LOCKOUT_MS = 5 * 60_000L
    }

    override suspend fun hasPin(): Boolean = withContext(Dispatchers.IO) {
        securePreferences.getString(Constants.KEY_PIN_HASH) != null
    }

    override suspend fun setPin(pin: String): Result<Unit> = withContext(Dispatchers.Default) {
        if (pin.length != Constants.PIN_LENGTH || !pin.all { it.isDigit() }) {
            return@withContext Result.failure(IllegalArgumentException("PIN must be ${Constants.PIN_LENGTH} digits"))
        }
        storePinHash(pin)
        resetFailedAttempts()
        Result.success(Unit)
    }

    override suspend fun verifyPin(pin: String): PinVerification = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val lockedUntil = securePreferences.getLong(Constants.KEY_PIN_LOCKED_UNTIL)
        if (now < lockedUntil) {
            return@withContext PinVerification.LockedOut(lockedUntil - now)
        }

        val stored = securePreferences.getString(Constants.KEY_PIN_HASH)
            ?: return@withContext PinVerification.Incorrect(FREE_ATTEMPTS)

        val matches = if (stored.startsWith("$HASH_PREFIX$")) {
            verifyPbkdf2(pin, stored)
        } else {
            verifyLegacySha256(pin, stored)
        }

        if (matches) {
            // Upgrade PINs stored with the old single-round SHA-256 scheme
            if (!stored.startsWith("$HASH_PREFIX$")) storePinHash(pin)
            resetFailedAttempts()
            return@withContext PinVerification.Success
        }

        val failed = securePreferences.getInt(Constants.KEY_PIN_FAILED_ATTEMPTS) + 1
        securePreferences.putInt(Constants.KEY_PIN_FAILED_ATTEMPTS, failed)
        if (failed < FREE_ATTEMPTS) {
            return@withContext PinVerification.Incorrect(FREE_ATTEMPTS - failed)
        }

        val lockout = (BASE_LOCKOUT_MS shl (failed - FREE_ATTEMPTS).coerceAtMost(10))
            .coerceAtMost(MAX_LOCKOUT_MS)
        securePreferences.putLong(Constants.KEY_PIN_LOCKED_UNTIL, now + lockout)
        PinVerification.LockedOut(lockout)
    }

    override suspend fun clearPin() = withContext(Dispatchers.IO) {
        securePreferences.remove(Constants.KEY_PIN_HASH)
        securePreferences.remove(Constants.KEY_PIN_SALT)
        securePreferences.remove(Constants.KEY_LAST_ACTIVE_AT)
        resetFailedAttempts()
    }

    override suspend fun getLastActiveAt(): Long? = withContext(Dispatchers.IO) {
        securePreferences.getLong(Constants.KEY_LAST_ACTIVE_AT).takeIf { it > 0 }
    }

    override suspend fun setLastActiveAt(timestamp: Long) = withContext(Dispatchers.IO) {
        securePreferences.putLong(Constants.KEY_LAST_ACTIVE_AT, timestamp)
    }

    private fun storePinHash(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt, ITERATIONS)
        val encoder = Base64.getEncoder()
        securePreferences.putString(
            Constants.KEY_PIN_HASH,
            "$HASH_PREFIX$$ITERATIONS$${encoder.encodeToString(salt)}$${encoder.encodeToString(hash)}"
        )
        securePreferences.remove(Constants.KEY_PIN_SALT)
    }

    private fun verifyPbkdf2(pin: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[2])
        val expected = decoder.decode(parts[3])
        return MessageDigest.isEqual(pbkdf2(pin, salt, iterations), expected)
    }

    private fun verifyLegacySha256(pin: String, stored: String): Boolean {
        val salt = securePreferences.getString(Constants.KEY_PIN_SALT) ?: return false
        val hash = MessageDigest.getInstance("SHA-256").digest((pin + salt).toByteArray())
        val hex = hash.joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(hex.toByteArray(), stored.toByteArray())
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun resetFailedAttempts() {
        securePreferences.remove(Constants.KEY_PIN_FAILED_ATTEMPTS)
        securePreferences.remove(Constants.KEY_PIN_LOCKED_UNTIL)
    }
}
