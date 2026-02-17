package com.worldmates.messenger.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.BlockedUser
import com.worldmates.messenger.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для управления списком заблокированных пользователей
 */
class BlockedUsersViewModel : ViewModel() {

    private val _blockedUsers = MutableStateFlow<List<BlockedUser>>(emptyList())
    val blockedUsers: StateFlow<List<BlockedUser>> = _blockedUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // Множество ID пользователей, которых сейчас разблокируем
    private val _unblockingUsers = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Загрузить список заблокированных пользователей
     */
    fun loadBlockedUsers() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                val accessToken = UserSession.accessToken
                if (accessToken.isNullOrEmpty()) {
                    _errorMessage.value = "Помилка авторизації"
                    return@launch
                }

                val response = RetrofitClient.apiService.getBlockedUsers(accessToken)

                if (response.apiStatus == 200) {
                    _blockedUsers.value = response.blockedUsers
                    Log.d(TAG, "Loaded ${response.blockedUsers.size} blocked users")
                } else {
                    _errorMessage.value = response.errorMessage ?: "Помилка завантаження"
                    Log.e(TAG, "Failed to load blocked users: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Помилка підключення: ${e.message}"
                Log.e(TAG, "Error loading blocked users", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Заблокировать пользователя
     */
    fun blockUser(userId: Long, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null

                val accessToken = UserSession.accessToken
                if (accessToken.isNullOrEmpty()) {
                    _errorMessage.value = "Помилка авторизації"
                    return@launch
                }

                val response = RetrofitClient.apiService.blockUser(
                    accessToken = accessToken,
                    userId = userId
                )

                if (response.apiStatus == 200 && response.blockStatus == "blocked") {
                    _successMessage.value = "Користувача заблоковано"
                    Log.d(TAG, "User $userId blocked successfully")

                    // Обновляем список
                    loadBlockedUsers()
                    onSuccess?.invoke()
                } else {
                    _errorMessage.value = response.errorMessage ?: "Не вдалося заблокувати"
                    Log.e(TAG, "Failed to block user: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Помилка підключення: ${e.message}"
                Log.e(TAG, "Error blocking user", e)
            }
        }
    }

    /**
     * Разблокировать пользователя
     */
    fun unblockUser(userId: Long) {
        viewModelScope.launch {
            try {
                // Добавляем в список разблокируемых
                _unblockingUsers.value = _unblockingUsers.value + userId
                _errorMessage.value = null

                val accessToken = UserSession.accessToken
                if (accessToken.isNullOrEmpty()) {
                    _errorMessage.value = "Помилка авторизації"
                    _unblockingUsers.value = _unblockingUsers.value - userId
                    return@launch
                }

                val response = RetrofitClient.apiService.unblockUser(
                    accessToken = accessToken,
                    userId = userId
                )

                if (response.apiStatus == 200 &&
                    (response.blockStatus == "unblocked" || response.blockStatus == "un-blocked" ||
                            response.blockStatus == "already_unblocked")
                ) {
                    _successMessage.value = "Користувача розблоковано"
                    Log.d(TAG, "User $userId unblocked successfully")

                    // Удаляем из списка локально
                    _blockedUsers.value = _blockedUsers.value.filter { it.userId != userId }
                } else {
                    _errorMessage.value = response.errorMessage ?: "Не вдалося розблокувати"
                    Log.e(TAG, "Failed to unblock user: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Помилка підключення: ${e.message}"
                Log.e(TAG, "Error unblocking user", e)
            } finally {
                // Убираем из списка разблокируемых
                _unblockingUsers.value = _unblockingUsers.value - userId
            }
        }
    }

    /**
     * Проверить статус блокировки с пользователем
     */
    fun checkBlockStatus(userId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val accessToken = UserSession.accessToken
                if (accessToken.isNullOrEmpty()) {
                    onResult(false)
                    return@launch
                }

                val response = RetrofitClient.apiService.checkIsBlocked(
                    accessToken = accessToken,
                    userId = userId
                )

                if (response.apiStatus == 200) {
                    onResult(response.blockedByMe)
                    Log.d(TAG, "Block status for user $userId: ${response.blockedByMe}")
                } else {
                    onResult(false)
                    Log.e(TAG, "Failed to check block status: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                onResult(false)
                Log.e(TAG, "Error checking block status", e)
            }
        }
    }

    /**
     * Проверить, разблокируется ли сейчас пользователь
     */
    fun isUnblocking(userId: Long): Boolean {
        return _unblockingUsers.value.contains(userId)
    }

    /**
     * Очистить сообщение об успехе
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    /**
     * Очистить сообщение об ошибке
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    companion object {
        private const val TAG = "BlockedUsersViewModel"
    }
}