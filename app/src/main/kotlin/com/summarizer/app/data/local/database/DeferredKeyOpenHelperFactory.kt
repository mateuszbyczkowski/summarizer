package com.summarizer.app.data.local.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.sqlcipher.database.SupportFactory

/**
 * SQLCipher open helper factory that fetches the passphrase only when the database is first
 * opened. Room builds its open helper wherever AppDatabase is first injected (often the main
 * thread), but opens the database on a background thread; key retrieval, and a one-time
 * re-key after upgrading, belong on the latter.
 */
class DeferredKeyOpenHelperFactory(
    private val passphrase: () -> ByteArray
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        DeferredOpenHelper(configuration)

    private inner class DeferredOpenHelper(
        private val configuration: SupportSQLiteOpenHelper.Configuration
    ) : SupportSQLiteOpenHelper {

        @Volatile
        private var writeAheadLogging: Boolean? = null

        private val delegateLazy = lazy {
            SupportFactory(passphrase()).create(configuration).also { helper ->
                writeAheadLogging?.let(helper::setWriteAheadLoggingEnabled)
            }
        }
        private val delegate by delegateLazy

        override val databaseName: String?
            get() = configuration.name

        override val writableDatabase: SupportSQLiteDatabase
            get() = delegate.writableDatabase

        override val readableDatabase: SupportSQLiteDatabase
            get() = delegate.readableDatabase

        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            if (delegateLazy.isInitialized()) delegate.setWriteAheadLoggingEnabled(enabled) else writeAheadLogging = enabled
        }

        override fun close() {
            if (delegateLazy.isInitialized()) delegate.close()
        }
    }
}
