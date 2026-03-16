package com.worldmates.messenger.ui.register

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RegisterViewModel : ViewModel() {

    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState: StateFlow<RegisterState> = _registerState

    private val api = NodeRetrofitClient.api

    fun register(username: String, email: String, password: String, confirmPassword: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            _registerState.value = RegisterState.Error("Заповніть всі поля")
            return
        }

        if (password != confirmPassword) {
            _registerState.value = RegisterState.Error("Паролі не співпадають")
            return
        }

        if (password.length < 6) {
            _registerState.value = RegisterState.Error("Пароль має містити мінімум 6 символів")
            return
        }

        _registerState.value = RegisterState.Loading

        viewModelScope.launch {
            try {
                val response = api.register(
                    username        = username,
                    email           = email,
                    password        = password,
                    confirmPassword = confirmPassword
                )

                Log.d("RegisterViewModel", "apiStatus: ${response.apiStatus}, successType: ${response.successType}")

                when {
                    response.apiStatus == 200 && response.accessToken != null && response.userId != null -> {
                        UserSession.saveSession(
                            response.accessToken!!,
                            response.userId!!,
                            response.username,
                            response.avatar
                        )
                        _registerState.value = RegisterState.Success
                        Log.d("RegisterViewModel", "Registered with auto-login, user ${response.userId}")
                    }
                    response.apiStatus == 200 && response.successType == "verification" -> {
                        _registerState.value = RegisterState.VerificationRequired(
                            userId            = response.userId ?: 0L,
                            username          = response.username ?: username,
                            verificationType  = "email",
                            contactInfo       = email
                        )
                        Log.d("RegisterViewModel", "Verification required for $email")
                    }
                    else -> {
                        val errorMsg = response.errors?.errorText
                            ?: response.errorMessage
                            ?: "Помилка реєстрації"
                        _registerState.value = RegisterState.Error(errorMsg)
                        Log.e("RegisterViewModel", "Error ${response.apiStatus}: $errorMsg")
                    }
                }
            } catch (e: Exception) {
                _registerState.value = RegisterState.Error("Помилка мережі: ${e.localizedMessage}")
                Log.e("RegisterViewModel", "Exception in register", e)
            }
        }
    }

    fun registerWithEmail(
        username:        String,
        email:           String,
        password:        String,
        confirmPassword: String,
        gender:          String = "male"
    ) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            _registerState.value = RegisterState.Error("Заповніть всі поля")
            return
        }

        if (password != confirmPassword) {
            _registerState.value = RegisterState.Error("Паролі не співпадають")
            return
        }

        if (password.length < 6) {
            _registerState.value = RegisterState.Error("Пароль має містити мінімум 6 символів")
            return
        }

        _registerState.value = RegisterState.Loading

        viewModelScope.launch {
            try {
                val response = api.register(
                    username        = username,
                    email           = email,
                    password        = password,
                    confirmPassword = confirmPassword,
                    gender          = gender
                )

                Log.d("RegisterViewModel", "Email reg: apiStatus=${response.apiStatus}, successType=${response.successType}")

                when {
                    response.apiStatus == 200 && response.accessToken != null && response.userId != null -> {
                        UserSession.saveSession(
                            response.accessToken!!,
                            response.userId!!,
                            response.username,
                            response.avatar
                        )
                        if (response.successType == "verification") {
                            _registerState.value = RegisterState.VerificationRequired(
                                userId           = response.userId!!,
                                username         = response.username ?: username,
                                verificationType = "email",
                                contactInfo      = email
                            )
                        } else {
                            _registerState.value = RegisterState.Success
                        }
                    }
                    response.apiStatus == 200 && response.successType == "verification" -> {
                        _registerState.value = RegisterState.VerificationRequired(
                            userId           = response.userId ?: 0L,
                            username         = response.username ?: username,
                            verificationType = "email",
                            contactInfo      = email
                        )
                    }
                    else -> {
                        val errorMsg = response.errors?.errorText
                            ?: response.errorMessage
                            ?: "Помилка реєстрації"
                        _registerState.value = RegisterState.Error(errorMsg)
                        Log.e("RegisterViewModel", "Error ${response.apiStatus}: $errorMsg")
                    }
                }
            } catch (e: Exception) {
                _registerState.value = RegisterState.Error("Помилка мережі: ${e.localizedMessage}")
                Log.e("RegisterViewModel", "Exception in registerWithEmail", e)
            }
        }
    }

    fun registerWithPhone(
        username:        String,
        phoneNumber:     String,
        password:        String,
        confirmPassword: String,
        gender:          String = "male"
    ) {
        if (username.isBlank() || phoneNumber.isBlank() || password.isBlank()) {
            _registerState.value = RegisterState.Error("Заповніть всі поля")
            return
        }

        if (password != confirmPassword) {
            _registerState.value = RegisterState.Error("Паролі не співпадають")
            return
        }

        if (password.length < 6) {
            _registerState.value = RegisterState.Error("Пароль має містити мінімум 6 символів")
            return
        }

        _registerState.value = RegisterState.Loading

        viewModelScope.launch {
            try {
                val response = api.register(
                    username        = username,
                    phoneNumber     = phoneNumber,
                    password        = password,
                    confirmPassword = confirmPassword,
                    gender          = gender
                )

                Log.d("RegisterViewModel", "Phone reg: apiStatus=${response.apiStatus}, successType=${response.successType}")

                when {
                    response.apiStatus == 200 && response.accessToken != null && response.userId != null -> {
                        UserSession.saveSession(
                            response.accessToken!!,
                            response.userId!!,
                            response.username,
                            response.avatar
                        )
                        _registerState.value = RegisterState.Success
                        Log.d("RegisterViewModel", "Phone registered, user ${response.userId}")
                    }
                    response.apiStatus == 200 && response.successType == "verification" -> {
                        _registerState.value = RegisterState.VerificationRequired(
                            userId           = response.userId ?: 0L,
                            username         = response.username ?: username,
                            verificationType = "phone",
                            contactInfo      = phoneNumber
                        )
                    }
                    else -> {
                        val errorMsg = response.errors?.errorText
                            ?: response.errorMessage
                            ?: "Помилка реєстрації"
                        _registerState.value = RegisterState.Error(errorMsg)
                        Log.e("RegisterViewModel", "Error ${response.apiStatus}: $errorMsg")
                    }
                }
            } catch (e: Exception) {
                _registerState.value = RegisterState.Error("Помилка мережі: ${e.localizedMessage}")
                Log.e("RegisterViewModel", "Exception in registerWithPhone", e)
            }
        }
    }

    fun resetState() {
        _registerState.value = RegisterState.Idle
    }
}

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    object Success : RegisterState()
    data class VerificationRequired(
        val userId: Long,
        val username: String,
        val verificationType: String,
        val contactInfo: String
    ) : RegisterState()
    data class Error(val message: String) : RegisterState()
}
