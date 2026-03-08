package com.worldmates.messenger.utils.signal

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import java.security.SecureRandom

/**
 * Постійне сховище для групових SenderKey станів.
 *
 * Використовує EncryptedSharedPreferences (AES-256-GCM через Android Keystore).
 * Приватні ключі зашифровані в спокої та ніколи не покидають пристрій.
 *
 * Структура ключів SharedPreferences:
 *   "my_sk_{groupId}"             — мій SenderKeyState для групи
 *   "sk_{groupId}_{senderId}"     — SenderKeyState іншого учасника групи
 *   "sk_distr_{groupId}"          — чи вже розподілявся мій SenderKey в цій групі
 *   "plain_{msgId}"               — кешований plaintext розшифрованого повідомлення
 */
class SignalSenderKeyStore(private val context: Context) {

    private val TAG  = "SenderKeyStore"
    private val gson = Gson()

    companion object {
        private const val PREF_FILE           = "wm_sender_keys"
        private const val PREFIX_MY_SK        = "my_sk_"
        private const val PREFIX_THEIR_SK     = "sk_"
        private const val PREFIX_DISTRIBUTED  = "sk_distr_"
        private const val PREFIX_PLAIN        = "plain_"
    }

    // ─── EncryptedSharedPreferences ───────────────────────────────────────────

    private val prefs by lazy {
        val mk = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, PREF_FILE, mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ─── Мій власний SenderKey ────────────────────────────────────────────────

    /**
     * Отримати або згенерувати мій SenderKey для групи [groupId].
     * @return Pair(state, isNew) — isNew=true якщо ключ щойно згенерований
     */
    @Synchronized
    fun getOrCreateMySenderKey(groupId: Long): Pair<SenderKeyState, Boolean> {
        val key  = "$PREFIX_MY_SK$groupId"
        val json = prefs.getString(key, null)
        if (json != null) {
            val state = runCatching { gson.fromJson(json, SenderKeyState::class.java) }.getOrNull()
            if (state != null) return Pair(state, false)
        }
        // Генерація нового SenderKey
        val chainKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val chainId  = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val state = SenderKeyState(
            chainKey = Base64.encodeToString(chainKey, Base64.NO_WRAP),
            chainId  = Base64.encodeToString(chainId,  Base64.NO_WRAP),
            counter  = 0
        )
        prefs.edit().putString(key, gson.toJson(state)).apply()
        Log.i(TAG, "Згенеровано новий SenderKey для групи $groupId")
        return Pair(state, true)
    }

    /** Зберегти оновлений стан мого SenderKey (після кожного відправленого повідомлення). */
    @Synchronized
    fun saveMySenderKey(groupId: Long, state: SenderKeyState) {
        prefs.edit().putString("$PREFIX_MY_SK$groupId", gson.toJson(state)).apply()
    }

    /**
     * Видалити мій SenderKey для групи (при виході з групи або примусовому re-key).
     * Наступний `getOrCreateMySenderKey()` згенерує новий ключ.
     */
    @Synchronized
    fun invalidateMySenderKey(groupId: Long) {
        prefs.edit()
            .remove("$PREFIX_MY_SK$groupId")
            .remove("$PREFIX_DISTRIBUTED$groupId")
            .apply()
        Log.i(TAG, "SenderKey інвалідовано для групи $groupId")
    }

    // ─── SenderKey інших учасників ────────────────────────────────────────────

    /**
     * Зберегти SenderKey від учасника [senderId] для групи [groupId].
     * Викликається після отримання та розшифрування SenderKeyDistribution.
     */
    @Synchronized
    fun saveSenderKey(groupId: Long, senderId: Long, state: SenderKeyState) {
        val key = "$PREFIX_THEIR_SK${groupId}_${senderId}"
        prefs.edit().putString(key, gson.toJson(state)).apply()
        Log.d(TAG, "SenderKey від user=$senderId у групі $groupId збережено")
    }

    /**
     * Завантажити SenderKey від [senderId] для [groupId].
     * @return SenderKeyState або null якщо ключ ще не отримано
     */
    @Synchronized
    fun loadSenderKey(groupId: Long, senderId: Long): SenderKeyState? {
        val key  = "$PREFIX_THEIR_SK${groupId}_${senderId}"
        val json = prefs.getString(key, null) ?: return null
        return runCatching { gson.fromJson(json, SenderKeyState::class.java) }.getOrNull()
    }

    /** Оновити збережений SenderKey після розшифровки чергового повідомлення. */
    @Synchronized
    fun updateSenderKey(groupId: Long, senderId: Long, state: SenderKeyState) =
        saveSenderKey(groupId, senderId, state)

    /**
     * Видалити SenderKey конкретного учасника (наприклад, після його виходу з групи).
     * Наступне повідомлення від нього буде нечитабельним до отримання нового distribution.
     */
    @Synchronized
    fun removeSenderKey(groupId: Long, senderId: Long) {
        prefs.edit().remove("$PREFIX_THEIR_SK${groupId}_${senderId}").apply()
    }

    // ─── Статус розподілу мого SenderKey ─────────────────────────────────────

    /**
     * Чи вже розподілявся мій SenderKey у групі [groupId]?
     * Після успішного distribute → setDistributed(groupId, true).
     * Після invalidate → автоматично скидається у false.
     */
    fun isDistributed(groupId: Long): Boolean =
        prefs.getBoolean("$PREFIX_DISTRIBUTED$groupId", false)

    /** Зафіксувати факт успішного розподілу SenderKey у групі. */
    @Synchronized
    fun setDistributed(groupId: Long, distributed: Boolean) {
        prefs.edit().putBoolean("$PREFIX_DISTRIBUTED$groupId", distributed).apply()
    }

    // ─── Кеш розшифрованих повідомлень ───────────────────────────────────────

    /**
     * Кешує розшифрований plaintext для повідомлення [msgId].
     * Дозволяє показати повідомлення при повторному відкритті чату
     * без повторної деривації ключа.
     */
    fun cachePlaintext(msgId: Long, plaintext: String) {
        prefs.edit().putString("$PREFIX_PLAIN$msgId", plaintext).apply()
    }

    /** Повернути кешований plaintext або null. */
    fun getCachedPlaintext(msgId: Long): String? =
        prefs.getString("$PREFIX_PLAIN$msgId", null)
}
