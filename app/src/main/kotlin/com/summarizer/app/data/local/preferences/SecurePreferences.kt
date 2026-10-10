package com.summarizer.app.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.summarizer.app.util.Constants
import com.summarizer.app.util.KeystoreCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's single store for secrets (PIN hash, OpenAI API key, lock state).
 *
 * Values are encrypted with a non-exportable Android Keystore key and kept in private
 * SharedPreferences. Initialization touches the Keystore, so the store is opened lazily and
 * should be used off the main thread.
 */
@Singleton
class SecurePreferences(
    private val context: Context,
    private val storeName: String,
    keyAlias: String,
    private val migrateLegacy: Boolean
) {
    /** The app's store. Tests use the primary constructor with their own names. */
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, STORE_NAME, KEY_ALIAS, migrateLegacy = true)

    private companion object {
        const val STORE_NAME = "summarizer_secure_store"
        const val KEY_ALIAS = "summarizer_secure_store_key"
        const val KEY_MIGRATED = "migrated_from_encrypted_prefs"

        // Stores written by androidx.security EncryptedSharedPreferences (deprecated)
        val LEGACY_STORES = listOf(Constants.PREFS_NAME, "encrypted_prefs")
    }

    private val cipher = KeystoreCipher(keyAlias)

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(storeName, Context.MODE_PRIVATE).also { if (migrateLegacy) migrateLegacyStores(it) }
    }

    fun getString(key: String, defaultValue: String? = null): String? =
        prefs.getString(key, null)?.let(::decrypt) ?: defaultValue

    fun putString(key: String, value: String) {
        prefs.edit(commit = true) { putString(key, cipher.encrypt(value.toByteArray(Charsets.UTF_8))) }
    }

    fun getInt(key: String, defaultValue: Int = 0): Int =
        getString(key)?.toIntOrNull() ?: defaultValue

    fun putInt(key: String, value: Int) = putString(key, value.toString())

    fun getLong(key: String, defaultValue: Long = 0L): Long =
        getString(key)?.toLongOrNull() ?: defaultValue

    fun putLong(key: String, value: Long) = putString(key, value.toString())

    fun remove(key: String) {
        prefs.edit(commit = true) { remove(key) }
    }

    /** Remove all secrets. */
    fun clear() {
        prefs.edit(commit = true) {
            clear()
            putBoolean(KEY_MIGRATED, true)
        }
    }

    private fun decrypt(value: String): String? = runCatching {
        String(cipher.decrypt(value), Charsets.UTF_8)
    }.onFailure { Timber.e(it, "Could not decrypt secure preference") }.getOrNull()

    @Suppress("DEPRECATION")
    private fun migrateLegacyStores(target: SharedPreferences) {
        if (target.getBoolean(KEY_MIGRATED, false)) return

        LEGACY_STORES.forEach { name ->
            runCatching {
                val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                val legacy = EncryptedSharedPreferences.create(
                    context,
                    name,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                target.edit(commit = true) {
                    legacy.all.forEach { (key, value) ->
                        if (value != null) putString(key, cipher.encrypt(value.toString().toByteArray(Charsets.UTF_8)))
                    }
                }
                legacy.edit(commit = true) { clear() }
            }.onFailure { Timber.e(it, "Could not migrate legacy secure preferences") }
            context.deleteSharedPreferences(name)
        }
        target.edit(commit = true) { putBoolean(KEY_MIGRATED, true) }
    }
}
