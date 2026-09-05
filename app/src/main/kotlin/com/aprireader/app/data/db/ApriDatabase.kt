package com.aprireader.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SourceEntity::class,
        BookEntity::class,
        MarkEntity::class,
        ReadingSessionEntity::class,
        TypographyPresetEntity::class,
        AchievementEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ApriDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao
    abstract fun bookDao(): BookDao
    abstract fun markDao(): MarkDao
    abstract fun statsDao(): StatsDao
    abstract fun presetDao(): PresetDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        fun create(context: Context): ApriDatabase =
            Room.databaseBuilder(context, ApriDatabase::class.java, "apri.db")
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
