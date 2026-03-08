package com.worldmates.messenger.data

import android.content.Context
import com.worldmates.messenger.data.local.AppDatabase
import com.worldmates.messenger.data.local.entity.AccountEntity
import com.worldmates.messenger.ui.chats.ChatOrganizationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AccountManager - менеджер мультиаккаунтної системи WorldMates.
 *
 * Ліміти:
 *   - Free користувачі: до 5 акаунтів
 *   - Pro користувачі : до 10 акаунтів
 *
 * Архітектура:
 *   - Акаунти зберігаються в Room (AccountEntity)
 *   - Активний акаунт відображається в UserSession (singleton-фасад для всього застосунку)
 *   - При перемиканні: поточний стан UserSession зберігається в БД, потім завантажується новий
 */
object AccountManager {

    const val MAX_ACCOUNTS_FREE = 5
    const val MAX_ACCOUNTS_PRO = 10

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    val accounts: StateFlow<List<AccountEntity>> = _accounts.asStateFlow()

    private val _activeAccount = MutableStateFlow<AccountEntity?>(null)
    val activeAccount: StateFlow<AccountEntity?> = _activeAccount.asStateFlow()

    private lateinit var db: AppDatabase
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        db = AppDatabase.getInstance(context)
        scope.launch { loadAccounts() }
    }

    // ==================== LIMITS ====================

    /** Максимально дозволена кількість акаунтів для поточного користувача */
    fun getMaxAccounts(): Int {
        return if (UserSession.isPro > 0) MAX_ACCOUNTS_PRO else MAX_ACCOUNTS_FREE
    }

    /** Чи можна додати ще один акаунт */
    fun canAddAccount(): Boolean {
        val currentUserId = UserSession.userId
        val nonCurrentAccounts = _accounts.value.count { it.userId != currentUserId }
        // +1 для поточного (який може ще не бути збережений)
        val totalAfterAdd = _accounts.value.size + 1
        return totalAfterAdd <= getMaxAccounts()
    }

    fun getAccountCount(): Int = _accounts.value.size

    // ==================== CRUD ====================

    /**
     * Зберегти поточну сесію як акаунт (при першому логіні).
     * Викликається після успішного логіну.
     */
    suspend fun saveCurrentSessionAsAccount() {
        val token = UserSession.accessToken ?: return
        if (!UserSession.isLoggedIn) return

        val account = AccountEntity(
            userId = UserSession.userId,
            accessToken = token,
            username = UserSession.username,
            avatar = UserSession.avatar,
            isPro = UserSession.isPro,
            isActive = true
        )
        db.accountDao().clearAllActive()
        db.accountDao().insertAccount(account)
        loadAccounts()
    }

    /**
     * Додати новий акаунт або оновити існуючий.
     * @return true якщо успішно, false якщо досягнуто ліміту
     */
    suspend fun addOrUpdateAccount(
        userId: Long,
        token: String,
        username: String?,
        avatar: String?,
        isPro: Int
    ): Boolean {
        val isExisting = _accounts.value.any { it.userId == userId }
        if (!isExisting && _accounts.value.size >= getMaxAccounts()) {
            return false
        }
        val account = AccountEntity(
            userId = userId,
            accessToken = token,
            username = username,
            avatar = avatar,
            isPro = isPro,
            isActive = false
        )
        db.accountDao().insertAccount(account)
        loadAccounts()
        return true
    }

    /**
     * Переключитись на інший акаунт.
     * Зберігає поточний стан сесії, завантажує новий.
     */
    suspend fun switchAccount(userId: Long, onComplete: (() -> Unit)? = null) {
        // 1. Зберегти поточну сесію
        val currentUserId = UserSession.userId
        if (currentUserId > 0 && UserSession.isLoggedIn) {
            db.accountDao().updateAccount(
                userId = currentUserId,
                token = UserSession.accessToken ?: "",
                username = UserSession.username,
                avatar = UserSession.avatar,
                isPro = UserSession.isPro
            )
        }

        // 2. Активувати обраний акаунт в БД
        db.accountDao().clearAllActive()
        db.accountDao().setActiveAccount(userId)

        // 3. Завантажити дані обраного акаунту
        val target = db.accountDao().getAccountById(userId) ?: return

        // 4. Оновити UserSession
        UserSession.saveSession(
            token = target.accessToken,
            id = target.userId,
            username = target.username,
            avatar = target.avatar,
            isPro = target.isPro
        )

        // 5. Перезавантажити організацію чатів для нового акаунту
        appContext?.let { ctx ->
            ChatOrganizationManager.reinitForAccount(ctx, userId)
        }

        loadAccounts()
        onComplete?.invoke()
    }

    /**
     * Видалити акаунт.
     * Якщо видаляється активний - переключає на інший або логаут.
     */
    suspend fun removeAccount(userId: Long) {
        val isActive = _activeAccount.value?.userId == userId
        db.accountDao().deleteAccountById(userId)
        loadAccounts()

        if (isActive) {
            val remaining = _accounts.value.filter { it.userId != userId }
            if (remaining.isNotEmpty()) {
                switchAccount(remaining.first().userId)
            } else {
                UserSession.clearSession()
            }
        }
    }

    /**
     * Оновити isPro поточного акаунту в Room (викликається з SubscriptionSyncWorker).
     */
    fun refreshCurrentAccountProStatus(isPro: Int) {
        val userId = UserSession.userId
        if (userId <= 0) return
        scope.launch {
            db.accountDao().updateAccount(
                userId = userId,
                token = UserSession.accessToken ?: "",
                username = UserSession.username,
                avatar = UserSession.avatar,
                isPro = isPro
            )
            loadAccounts()
        }
    }

    // ==================== INTERNAL ====================

    private suspend fun loadAccounts() {
        val list = db.accountDao().getAllAccounts()
        _accounts.value = list
        _activeAccount.value = list.firstOrNull { it.isActive } ?: list.firstOrNull()
    }
}
