package com.summarizer.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.summarizer.app.data.local.database.dao.MessageDao
import com.summarizer.app.data.local.database.dao.SummaryDao
import com.summarizer.app.data.local.database.dao.ThreadDao
import com.summarizer.app.data.local.entity.MessageEntity
import com.summarizer.app.data.local.entity.SummaryEntity
import com.summarizer.app.data.local.entity.ThreadEntity

@Database(
    entities = [
        MessageEntity::class,
        ThreadEntity::class,
        SummaryEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun threadDao(): ThreadDao
    abstract fun summaryDao(): SummaryDao
}
