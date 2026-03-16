package com.worldmates.messenger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.worldmates.messenger.data.local.dao.AccountDao
import com.worldmates.messenger.data.local.dao.DraftDao
import com.worldmates.messenger.data.local.dao.MessageDao
import com.worldmates.messenger.data.local.dao.SignalPlaintextCacheDao
import com.worldmates.messenger.data.local.entity.AccountEntity
import com.worldmates.messenger.data.local.entity.Draft
import com.worldmates.messenger.data.local.entity.CachedMessage
import com.worldmates.messenger.data.local.entity.SignalPlaintextCache

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
        CachedMessage::class,
        SignalPlaintextCache::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun draftDao(): DraftDao
    abstract fun messageDao(): MessageDao
    abstract fun signalPlaintextCacheDao(): SignalPlaintextCacheDao

    companion object {
        private const val DATABASE_NAME = "worldmates_messenger.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS signal_plaintext_cache (
                        msgId   INTEGER NOT NULL PRIMARY KEY,
                        plaintext TEXT NOT NULL,
                        cachedAt  INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_signal_plaintext_cache_msgId ON signal_plaintext_cache (msgId)")
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_5_6)
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
