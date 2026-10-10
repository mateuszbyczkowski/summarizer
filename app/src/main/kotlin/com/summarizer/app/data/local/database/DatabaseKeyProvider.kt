package com.summarizer.app.data.local.database

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import com.summarizer.app.util.KeystoreCipher
import net.sqlcipher.database.SQLiteDatabase
import timber.log.Timber
import java.io.File
import java.security.SecureRandom

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
        private const val KEY_CURRENT = "wrapped_key"
        private const val KEY_PENDING = "wrapped_key_pending"
    }

    // Named after the database, so a test database never touches the app's key
    // ("summarizer_database" -> "summarizer_database_key", the historical name)
    private val prefs = context.getSharedPreferences("${databaseName}_key", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("${databaseName}_key")

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

    private fun wrap(passphrase: ByteArray): String = cipher.encrypt(passphrase)

    private fun unwrap(wrapped: String): ByteArray = cipher.decrypt(wrapped)
}
