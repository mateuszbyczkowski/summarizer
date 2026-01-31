package com.summarizer.app.di

import android.content.Context
import androidx.room.Room
import com.summarizer.app.data.local.dao.AIModelDao
import com.summarizer.app.data.local.database.AppDatabase
import com.summarizer.app.data.local.database.dao.MessageDao
import com.summarizer.app.data.local.database.dao.SummaryDao
import com.summarizer.app.data.local.database.dao.ThreadDao
import com.summarizer.app.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        // Generate passphrase from Android ID (unique per device)
        val passphrase = SQLiteDatabase.getBytes(
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ).toCharArray()
        )
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            Constants.DATABASE_NAME
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration() // For I1, we can afford to lose data
            .build()
    }

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideThreadDao(database: AppDatabase): ThreadDao {
        return database.threadDao()
    }

    @Provides
    fun provideSummaryDao(database: AppDatabase): SummaryDao {
        return database.summaryDao()
    }

    @Provides
    fun provideAIModelDao(database: AppDatabase): AIModelDao {
        return database.aiModelDao()
    }
}
