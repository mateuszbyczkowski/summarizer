package com.summarizer.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.summarizer.app.data.local.preferences.SecurePreferences
import com.summarizer.app.domain.model.PinVerification
import com.summarizer.app.util.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class AuthRepositoryImplTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val securePreferences = SecurePreferences(context)
    private val repository = AuthRepositoryImpl(securePreferences)

    @Before
    fun setUp() = runTest {
        repository.clearPin()
    }

    @Test
    fun correctPinUnlocksAndWrongPinIsRejected() = runTest {
        repository.setPin("123456")

        assertEquals(PinVerification.Success, repository.verifyPin("123456"))
        assertTrue(repository.verifyPin("654321") is PinVerification.Incorrect)
    }

    @Test
    fun repeatedWrongEntriesCauseAShortPause() = runTest {
        repository.setPin("123456")
        repeat(9) { assertTrue(repository.verifyPin("000000") is PinVerification.Incorrect) }

        assertTrue(repository.verifyPin("000000") is PinVerification.LockedOut)
        assertTrue(repository.verifyPin("123456") is PinVerification.LockedOut)
    }

    @Test
    fun legacySha256PinStillUnlocksAndIsUpgraded() = runTest {
        val salt = "legacy-salt"
        val hash = MessageDigest.getInstance("SHA-256").digest(("123456" + salt).toByteArray())
            .joinToString("") { "%02x".format(it) }
        securePreferences.putString(Constants.KEY_PIN_SALT, salt)
        securePreferences.putString(Constants.KEY_PIN_HASH, hash)

        assertEquals(PinVerification.Success, repository.verifyPin("123456"))
        assertTrue(securePreferences.getString(Constants.KEY_PIN_HASH)!!.startsWith("pbkdf2-sha256$"))
        assertEquals(PinVerification.Success, repository.verifyPin("123456"))
    }
}
