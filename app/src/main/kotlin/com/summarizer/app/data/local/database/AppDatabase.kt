package com.summarizer.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.summarizer.app.data.local.dao.AIModelDao
import com.summarizer.app.data.local.database.dao.MessageDao
import com.summarizer.app.data.local.database.dao.SummaryDao
import com.summarizer.app.data.local.database.dao.ThreadDao
import com.summarizer.app.data.local.database.dao.ThreadSettingsDao
import com.summarizer.app.data.local.entity.AIModelEntity
import com.summarizer.app.data.local.entity.MessageEntity
import com.summarizer.app.data.local.entity.SummaryEntity
import com.summarizer.app.data.local.entity.ThreadEntity
import com.summarizer.app.data.local.entity.ThreadSettingsEntity

@Database(
    entities = [
        MessageEntity::class,
        ThreadEntity::class,
        SummaryEntity::class,
        AIModelEntity::class,
        ThreadSettingsEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun threadDao(): ThreadDao
    abstract fun summaryDao(): SummaryDao
    abstract fun aiModelDao(): AIModelDao
    abstract fun threadSettingsDao(): ThreadSettingsDao
}
