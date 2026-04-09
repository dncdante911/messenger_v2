package com.worldmates.messenger.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    /** Set to true before calling login() when adding a second account. */
    var isAddAccountMode: Boolean = false

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
                val response = NodeRetrofitClient.api.login(
                    username = username,
                    password = password
                )

                when {
                    response.apiStatus == 200 && response.accessToken != null && response.userId != null -> {
                        if (isAddAccountMode) {
                            // Add-account mode: do NOT overwrite UserSession —
                            // just carry the new credentials back to the Activity.
                            _loginState.value = LoginState.AddAccountSuccess(
                                userId   = response.userId!!,
                                token    = response.accessToken!!,
                                username = response.username,
                                avatar   = response.avatar
                            )
                            Log.d("LoginViewModel", "Add-account success, new userId=${response.userId}")
                        } else {
                            UserSession.saveSession(
                                token    = response.accessToken!!,
                                id       = response.userId!!,
                                username = response.username,
                                avatar   = response.avatar
                            )
                            _loginState.value = LoginState.Success
                            Log.d("LoginViewModel", "Успішно увійшли! User ID: ${response.userId}")
                        }
                    }
                    else -> {
                        val errorMsg = response.errorMessage ?: "Невірні учетні дані"
                        _loginState.value = LoginState.Error(errorMsg)
                        Log.e("LoginViewModel", "Помилка входу: ${response.apiStatus} - $errorMsg")
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
    object Idle    : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
    /** Emitted in add-account mode: new credentials ready, UserSession NOT touched. */
    data class AddAccountSuccess(
        val userId:   Long,
        val token:    String,
        val username: String?,
        val avatar:   String?
    ) : LoginState()
}