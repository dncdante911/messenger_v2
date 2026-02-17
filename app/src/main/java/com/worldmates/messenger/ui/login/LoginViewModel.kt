package com.worldmates.messenger.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Заповніть всі поля")
            return
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            try {
                // Виклик API
                val response = RetrofitClient.apiService.login(
                    username = username,
                    password = password,
                    deviceType = "phone"
                )

                // Перевірка статусу відповіді
                when {
                    response.apiStatus == 200 && response.accessToken != null && response.userId != null -> {
                        // Успішний вхід
                        UserSession.saveSession(response.accessToken, response.userId)

                        // Синхронізуємо токен з сервером
                        try {
                            val syncResponse = RetrofitClient.apiService.syncSession(
                                accessToken = response.accessToken,
                                userId = response.userId,
                                platform = "phone"
                            )
                            Log.d("LoginViewModel", "Session synced: ${syncResponse.message}")
                        } catch (e: Exception) {
                            Log.w("LoginViewModel", "Failed to sync session: ${e.message}")
                            // Не критична помилка, продовжуємо
                        }

                        _loginState.value = LoginState.Success
                        Log.d("LoginViewModel", "Успішно увійшли! User ID: ${response.userId}")
                    }
                    response.apiStatus == 400 -> {
                        // Помилка від сервера
                        val errorMsg = response.errorMessage ?: "Невірні учетні дані"
                        _loginState.value = LoginState.Error(errorMsg)
                        Log.e("LoginViewModel", "Помилка входу: $errorMsg")
                    }
                    else -> {
                        val errorMsg = response.errorMessage ?: "Невідома помилка"
                        _loginState.value = LoginState.Error(errorMsg)
                        Log.e("LoginViewModel", "Помилка: ${response.apiStatus} - $errorMsg")
                    }
                }
            } catch (e: java.net.ConnectException) {
                val errorMsg = "Помилка з'єднання. Перевірте мережу"
                _loginState.value = LoginState.Error(errorMsg)
                Log.e("LoginViewModel", "Помилка з'єднання", e)
            } catch (e: java.net.SocketTimeoutException) {
                val errorMsg = "Тайм-аут з'єднання. Спробуйте ще раз"
                _loginState.value = LoginState.Error(errorMsg)
                Log.e("LoginViewModel", "Тайм-аут", e)
            } catch (e: Exception) {
                val errorMsg = "Помилка мережи: ${e.localizedMessage}"
                _loginState.value = LoginState.Error(errorMsg)
                Log.e("LoginViewModel", "Помилка входу", e)
            }
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}