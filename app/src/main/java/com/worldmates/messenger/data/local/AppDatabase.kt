package com.worldmates.messenger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.worldmates.messenger.data.local.dao.AccountDao
import com.worldmates.messenger.data.local.dao.DraftDao
import com.worldmates.messenger.data.local.dao.MessageDao
import com.worldmates.messenger.data.local.entity.AccountEntity
import com.worldmates.messenger.data.local.entity.Draft
import com.worldmates.messenger.data.local.entity.CachedMessage

/**
 * AppDatabase - локальна база даних застосунку.
 *
 * Зберігає:
 * - AccountEntity  - мультиаккаунтна система (v5)
 * - Draft          - чернетки повідомлень
 * - CachedMessage  - кеш повідомлень + поля секретних чатів (destroyAt, isSecret) (v5)
 */
@Database(
    entities = [
        AccountEntity::class,
        Draft::class,
        CachedMessage::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun draftDao(): DraftDao
    abstract fun messageDao(): MessageDao

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
                .fallbackToDestructiveMigration()
                .build()
        }

        fun getInMemoryDatabase(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            ).build()
        }
    }
}
