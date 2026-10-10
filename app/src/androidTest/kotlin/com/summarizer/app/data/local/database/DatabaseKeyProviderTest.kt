package com.summarizer.app.data.local.database

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.sqlcipher.database.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseKeyProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "key-provider-test.db"

    @Before
    fun setUp() {
        SQLiteDatabase.loadLibs(context)
        cleanUp()
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
        // The provider keeps the key for this test database in "<name>_key", never the app's
        context.getSharedPreferences("${databaseName}_key", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun legacyAndroidIdDatabaseIsRekeyedAndKeepsItsData() {
        val legacyKey = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).toByteArray()
        val file = context.getDatabasePath(databaseName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, String(legacyKey), null).use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            db.execSQL("INSERT INTO t VALUES ('kept')")
        }

        val key = DatabaseKeyProvider(context, databaseName).getPassphrase()

        SQLiteDatabase.openDatabase(file.path, key, null, SQLiteDatabase.OPEN_READONLY, null, null).use { db ->
            db.rawQuery("SELECT v FROM t", emptyArray<Any>()).use { c ->
                c.moveToFirst()
                assertEquals("kept", c.getString(0))
            }
        }
        // The same key is returned on the next launch
        assertArrayEquals(key, DatabaseKeyProvider(context, databaseName).getPassphrase())
    }

    @Test
    fun unreadableDatabaseIsMovedAsideInsteadOfCrashing() {
        val file = context.getDatabasePath(databaseName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, "some-other-key", null).use { it.execSQL("CREATE TABLE t (v TEXT)") }

        DatabaseKeyProvider(context, databaseName).getPassphrase()

        assertEquals(false, file.exists())
        file.parentFile!!.listFiles()!!.filter { it.name.startsWith("$databaseName.unreadable-") }.forEach { it.delete() }
    }
}
