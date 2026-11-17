package com.newpos.wordcard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [AnkiCardEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(CardContentConverters::class)
abstract class AnkiDatabase : RoomDatabase() {
    abstract fun cards(): AnkiCardDao

    companion object {
        fun open(context: Context): AnkiDatabase = Room.databaseBuilder(
            context.applicationContext, AnkiDatabase::class.java, "wordcard-room.db"
        )
            .addCallback(BundledVocabularyInitializer(context.applicationContext))
            // 开发阶段不迁移实验数据，表结构变化时重新创建数据库。
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
}
