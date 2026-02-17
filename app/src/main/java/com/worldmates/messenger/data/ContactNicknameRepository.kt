package com.worldmates.messenger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Репозиторій для зберігання локальних псевдонімів контактів.
 * Дозволяє користувачу задавати власні імена для контактів для зручності.
 */
private val Context.contactNicknamesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "contact_nicknames"
)

class ContactNicknameRepository(private val context: Context) {

    /**
     * Зберігає псевдонім для контакту
     * @param userId ID користувача
     * @param nickname Новий псевдонім (або null для видалення)
     */
    suspend fun setNickname(userId: Long, nickname: String?) {
        val key = stringPreferencesKey("nickname_$userId")
        context.contactNicknamesDataStore.edit { preferences ->
            if (nickname.isNullOrBlank()) {
                preferences.remove(key)
            } else {
                preferences[key] = nickname.trim()
            }
        }
    }

    /**
     * Отримує псевдонім для контакту
     * @param userId ID користувача
     * @return Flow з псевдонімом або null
     */
    fun getNickname(userId: Long): Flow<String?> {
        val key = stringPreferencesKey("nickname_$userId")
        return context.contactNicknamesDataStore.data.map { preferences ->
            preferences[key]
        }
    }

    /**
     * Отримує всі псевдоніми
     * @return Flow з Map<userId, nickname>
     */
    fun getAllNicknames(): Flow<Map<Long, String>> {
        return context.contactNicknamesDataStore.data.map { preferences ->
            preferences.asMap()
                .filterKeys { it.name.startsWith("nickname_") }
                .mapKeys { (key, _) ->
                    key.name.removePrefix("nickname_").toLongOrNull() ?: 0L
                }
                .filterKeys { it != 0L }
                .mapValues { (_, value) -> value.toString() }
        }
    }

    /**
     * Видаляє псевдонім контакту
     * @param userId ID користувача
     */
    suspend fun removeNickname(userId: Long) {
        setNickname(userId, null)
    }

    /**
     * Очищує всі псевдоніми (для logout)
     */
    suspend fun clearAllNicknames() {
        context.contactNicknamesDataStore.edit { preferences ->
            val nicknameKeys = preferences.asMap().keys
                .filter { it.name.startsWith("nickname_") }
            nicknameKeys.forEach { key ->
                preferences.remove(key)
            }
        }
    }
}
