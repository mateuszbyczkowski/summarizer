package com.summarizer.app.data.local.database

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import net.sqlcipher.database.SQLiteDatabase
import timber.log.Timber
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides the SQLCipher passphrase: 32 random bytes, stored wrapped by a non-exportable
 * Android Keystore key, so a copied database file cannot be decrypted off the device.
 *
 * Databases created by older versions were keyed with ANDROID_ID; they are re-keyed on first
 * launch. The new key is persisted as "pending" before re-keying, so a crash mid-way can resume.
 * A database that cannot be opened with any known key is moved aside instead of crashing the app.
 */
class DatabaseKeyProvider(
    private val context: Context,
    private val databaseName: String
) {

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val WRAPPING_KEY_ALIAS = "summarizer_database_key"
        private const val PREFS_NAME = "summarizer_database_key"
        private const val KEY_CURRENT = "wrapped_key"
        private const val KEY_PENDING = "wrapped_key_pending"
        private const val GCM_TAG_BITS = 128
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getPassphrase(): ByteArray {
        val databaseFile = context.getDatabasePath(databaseName)

        val stored = prefs.getString(KEY_CURRENT, null)
        if (stored != null) {
            runCatching { unwrap(stored) }
                .onSuccess { return it }
                .onFailure { Timber.e(it, "Database key could not be unwrapped") }
            // Keystore key lost (e.g. after a device-security reset): the data is unrecoverable
            quarantine(databaseFile)
            prefs.edit(commit = true) { remove(KEY_CURRENT) }
        }

        val pending = prefs.getString(KEY_PENDING, null)?.let { runCatching { unwrap(it) }.getOrNull() }
        val key = pending ?: newPassphrase().also { prefs.edit(commit = true) { putString(KEY_PENDING, wrap(it)) } }

        if (databaseFile.exists() && !canOpen(databaseFile, key)) {
            val legacy = legacyPassphrase()
            if (legacy == null || !rekey(databaseFile, legacy, key)) {
                quarantine(databaseFile)
            }
        }

        prefs.edit(commit = true) {
            putString(KEY_CURRENT, wrap(key))
            remove(KEY_PENDING)
        }
        return key
    }

    private fun newPassphrase(): ByteArray {
        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        // Hex text keeps the passphrase identical between SupportFactory(bytes) and changePassword(chars)
        return random.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.UTF_8)
    }

    @SuppressLint("HardwareIds")
    private fun legacyPassphrase(): ByteArray? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.toByteArray(Charsets.UTF_8)

    private fun canOpen(file: File, passphrase: ByteArray): Boolean = runCatching {
        SQLiteDatabase.loadLibs(context)
        SQLiteDatabase.openDatabase(file.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null).use { db ->
            db.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray<Any>()).use { it.moveToFirst() }
        }
    }.getOrDefault(false)

    private fun rekey(file: File, oldPassphrase: ByteArray, newPassphrase: ByteArray): Boolean = runCatching {
        SQLiteDatabase.loadLibs(context)
        SQLiteDatabase.openDatabase(file.path, oldPassphrase, null, SQLiteDatabase.OPEN_READWRITE, null, null).use { db ->
            // Fold any WAL content into the main file before changing the key
            db.rawExecSQL("PRAGMA journal_mode = DELETE")
            db.changePassword(String(newPassphrase, Charsets.UTF_8).toCharArray())
        }
        Timber.i("Database re-keyed with a Keystore-protected key")
        true
    }.onFailure { Timber.e(it, "Failed to re-key legacy database") }.getOrDefault(false)

    /** Keep an unreadable database on disk under a new name and let the app start fresh. */
    private fun quarantine(file: File) {
        if (!file.exists()) return
        val suffix = ".unreadable-${System.currentTimeMillis()}"
        listOf("", "-wal", "-shm", "-journal").forEach { ext ->
            val part = File(file.path + ext)
            if (part.exists()) part.renameTo(File(file.path + ext + suffix))
        }
        Timber.w("Unreadable database moved aside")
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(WRAPPING_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    WRAPPING_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private fun wrap(passphrase: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(cipher.iv) + ":" + encoder.encodeToString(cipher.doFinal(passphrase))
    }

    private fun unwrap(wrapped: String): ByteArray {
        val (iv, data) = wrapped.split(':').map { Base64.getDecoder().decode(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(data)
    }
}
