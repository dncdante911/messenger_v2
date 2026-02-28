package com.worldmates.messenger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.worldmates.messenger.data.local.dao.DraftDao
import com.worldmates.messenger.data.local.dao.MessageDao
import com.worldmates.messenger.data.local.entity.Draft
import com.worldmates.messenger.data.local.entity.CachedMessage

/**
 * 💾 AppDatabase - локальная база данных приложения
 *
 * Хранит:
 * - Черновики сообщений (Draft)
 * - Кэш сообщений для офлайн доступа (CachedMessage) 📦 CLOUD BACKUP
 */
@Database(
    entities = [
        Draft::class,
        CachedMessage::class  // 📦 CLOUD BACKUP: Кэш сообщений
    ],
    version = 4,  // Увеличена версия до 4 (схема изменилась)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun draftDao(): DraftDao
    abstract fun messageDao(): MessageDao  // 📦 CLOUD BACKUP: DAO для кэша сообщений

    companion object {
        private const val DATABASE_NAME = "worldmates_messenger.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // При изменении схемы - пересоздаём БД
                .build()
        }

        /**
         * Для тестирования - in-memory database
         */
        fun getInMemoryDatabase(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            ).build()
        }
    }
}
