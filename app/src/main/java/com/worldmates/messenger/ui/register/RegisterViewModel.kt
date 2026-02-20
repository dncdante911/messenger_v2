package com.worldmates.messenger.ui.register

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class RegisterViewModel : ViewModel() {

    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState: StateFlow<RegisterState> = _registerState

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
                // Генеруємо унікальний session ID для WoWonder API
                val sessionId = generateSessionId()

                // Виклик API для регистрации
                val response = RetrofitClient.apiService.register(
                    username = username,
                    email = email,
                    password = password,
                    confirmPassword = confirmPassword,
                    sessionId = sessionId,
                    deviceType = "phone"
                )

                // ДЕТАЛЬНЕ ЛОГУВАННЯ для діагностики
                Log.d("RegisterViewModel", "=== RESPONSE DEBUG ===")
                Log.d("RegisterViewModel", "apiStatus: ${response.apiStatus}")
                Log.d("RegisterViewModel", "accessToken: ${response.accessToken}")
                Log.d("RegisterViewModel", "userId: ${response.userId}")
                Log.d("RegisterViewModel", "successType: ${response.successType}")
                Log.d("RegisterViewModel", "message: ${response.message}")
                Log.d("RegisterViewModel", "errorMessage: ${response.errorMessage}")
                Log.d("RegisterViewModel", "======================")

                // Перевірка статусу відповіді
                when {
                    response.apiStatus == 200 && response.accessToken != null && response.userId != null -> {
                        // Успішна регистрация з автоматичним логіном
                        UserSession.saveSession(
                            response.accessToken!!,
                            response.userId!!.toLong(),
                            response.username,
                            response.avatar
                        )
                        _registerState.value = RegisterState.Success
                        Log.d("RegisterViewModel", "Успішно зареєстровано! User ID: ${response.userId}")
                    }
                    response.apiStatus == 200 && response.successType == "verification" -> {
                        // Успішна реєстрація з email верифікацією
                        _registerState.value = RegisterState.VerificationRequired(
                            userId = 0,
                            username = username,
                            verificationType = "email",
                            contactInfo = email
                        )
                        Log.d("RegisterViewModel", "Реєстрація успішна! Потрібна верифікація email: $email")
                    }
                    response.apiStatus == 400 -> {
                        val errorMsg = response.errors?.errorText
                            ?: response.errorMessage
                            ?: "Помилка реєстрації"
                        _registerState.value = RegisterState.Error(errorMsg)
                        Log.e("RegisterViewModel", "Помилка реєстрації: $errorMsg")
                    }
                    else -> {
                        val errorMsg = response.errors?.errorText
                            ?: response.errorMessage
                            ?: response.message
                            ?: "Невідома помилка"
                        _registerState.value = RegisterState.Error(errorMsg)
                        Log.e("RegisterViewModel", "Помилка: ${response.apiStatus} - $errorMsg")
                    }
                }
            } catch (e: HttpException) {
                val errorMsg = parseHttpError(e)
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "HTTP помилка реєстрації: ${e.code()}", e)
            } catch (e: java.net.ConnectException) {
                val errorMsg = "Помилка з'єднання. Перевірте мережу"
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "Помилка з'єднання", e)
            } catch (e: java.net.SocketTimeoutException) {
                val errorMsg = "Тайм-аут з'єднання. Спробуйте ще раз"
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "Тайм-аут", e)
            } catch (e: Exception) {
                val errorMsg = "Помилка мережі: ${e.localizedMessage}"
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "Помилка реєстрації", e)
            }
        }
    }

    /**
     * Регистрация с верификацией через Email
     */
    fun registerWithEmail(
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
        gender: String = "male"  // ✅ Добавлено
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
                // Генеруємо унікальний session ID
                val sessionId = generateSessionId()

                val response = RetrofitClient.apiService.register(
                    username = username,
                    email = email,
                    phoneNumber = null,
                    password = password,
                    confirmPassword = confirmPassword,
                    sessionId = sessionId,
                    gender = gender  // ✅ Добавлено
                )

                Log.d("RegisterViewModel", "=== EMAIL REGISTRATION ===")
                Log.d("RegisterViewModel", "apiStatus: ${response.apiStatus}")
                Log.d("RegisterViewModel", "successType: ${response.successType}")
                Log.d("RegisterViewModel", "accessToken: ${response.accessToken}")
                Log.d("RegisterViewModel", "userId: ${response.userId}")

                when {
                    response.apiStatus == 200 && response.accessToken != null && response.userId != null -> {
                        // Успішна регистрация (може бути як auto-login так і verification)
                        // Зберігаємо сесію в обох випадках
                        UserSession.saveSession(
                            response.accessToken!!,
                            response.userId!!.toLong(),
                            response.username,
                            response.avatar
                        )

                        if (response.successType == "verification") {
                            // Email верифікація потрібна, але сесія вже збережена
                            _registerState.value = RegisterState.VerificationRequired(
                                userId = response.userId!!.toLong(),
                                username = response.username ?: username,
                                verificationType = "email",
                                contactInfo = email
                            )
                            Log.d("RegisterViewModel", "Email верифікація потрібна, але сесію збережено")
                        } else {
                            // Автоматичний логін
                            _registerState.value = RegisterState.Success
                            Log.d("RegisterViewModel", "Успішно зареєстровано з auto-login")
                        }
                    }
                    response.apiStatus == 400 -> {
                        val errorMsg = response.errors?.errorText
                            ?: response.errorMessage
                            ?: "Помилка реєстрації"
                        _registerState.value = RegisterState.Error(errorMsg)
                        Log.e("RegisterViewModel", "Помилка: $errorMsg")
                    }
                    else -> {
                        val errorMsg = response.errors?.errorText
                            ?: response.errorMessage
                            ?: response.message
                            ?: "Невідома помилка"
                        _registerState.value = RegisterState.Error(errorMsg)
                        Log.e("RegisterViewModel", "Помилка: $errorMsg")
                    }
                }
            } catch (e: HttpException) {
                val errorMsg = parseHttpError(e)
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "HTTP помилка email реєстрації: ${e.code()}", e)
            } catch (e: java.net.ConnectException) {
                val errorMsg = "Помилка з'єднання. Перевірте мережу"
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "Помилка з'єднання", e)
            } catch (e: java.net.SocketTimeoutException) {
                val errorMsg = "Тайм-аут з'єднання. Сервер не відповідає, спробуйте ще раз"
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "Тайм-аут email реєстрації", e)
            } catch (e: Exception) {
                val errorMsg = "Помилка мережі: ${e.localizedMessage}"
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "Помилка реєстрації", e)
            }
        }
    }

    /**
     * Регистрация с верификацией через SMS
     */
    fun registerWithPhone(
        username: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String,
        gender: String = "male"  // ✅ Добавлено
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
                // Генеруємо унікальний session ID
                val sessionId = generateSessionId()

                val response = RetrofitClient.apiService.register(
                    username = username,
                    email = null,
                    phoneNumber = phoneNumber,
                    password = password,
                    confirmPassword = confirmPassword,
                    sessionId = sessionId,
                    gender = gender  // ✅ Добавлено
                )

                Log.d("RegisterViewModel", "=== PHONE REGISTRATION ===")
                Log.d("RegisterViewModel", "apiStatus: ${response.apiStatus}")
                Log.d("RegisterViewModel", "successType: ${response.successType}")
                Log.d("RegisterViewModel", "accessToken: ${response.accessToken}")
                Log.d("RegisterViewModel", "userId: ${response.userId}")

                when {
                    response.apiStatus == 200 && response.accessToken != null && response.userId != null -> {
                        // Успішна регистрация
                        // Phone registration should auto-activate (no verification needed)
                        UserSession.saveSession(
                            response.accessToken!!,
                            response.userId!!.toLong(),
                            response.username,
                            response.avatar
                        )
                        _registerState.value = RegisterState.Success
                        Log.d("RegisterViewModel", "Успішно зареєстровано через телефон! User ID: ${response.userId}")
                    }
                    response.apiStatus == 400 -> {
                        val errorMsg = response.errors?.errorText
                            ?: response.errorMessage
                            ?: "Помилка реєстрації"
                        _registerState.value = RegisterState.Error(errorMsg)
                        Log.e("RegisterViewModel", "Помилка: $errorMsg")
                    }
                    else -> {
                        val errorMsg = response.errors?.errorText
                            ?: response.errorMessage
                            ?: response.message
                            ?: "Невідома помилка"
                        _registerState.value = RegisterState.Error(errorMsg)
                        Log.e("RegisterViewModel", "Помилка: $errorMsg")
                    }
                }
            } catch (e: HttpException) {
                val errorMsg = parseHttpError(e)
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "HTTP помилка phone реєстрації: ${e.code()}", e)
            } catch (e: java.net.ConnectException) {
                val errorMsg = "Помилка з'єднання. Перевірте мережу"
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "Помилка з'єднання", e)
            } catch (e: java.net.SocketTimeoutException) {
                val errorMsg = "Тайм-аут з'єднання. Сервер не відповідає, спробуйте ще раз"
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "Тайм-аут phone реєстрації", e)
            } catch (e: Exception) {
                val errorMsg = "Помилка мережі: ${e.localizedMessage}"
                _registerState.value = RegisterState.Error(errorMsg)
                Log.e("RegisterViewModel", "Помилка реєстрації", e)
            }
        }
    }

    fun resetState() {
        _registerState.value = RegisterState.Idle
    }

    /**
     * Парсить помилку з HTTP-відповіді сервера
     */
    private fun parseHttpError(e: HttpException): String {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e("RegisterViewModel", "HTTP ${e.code()} error body: $errorBody")

            if (!errorBody.isNullOrBlank()) {
                // Спроба розібрати JSON відповідь
                val gson = com.google.gson.Gson()
                try {
                    val errorResponse = gson.fromJson(errorBody, com.worldmates.messenger.data.model.AuthResponse::class.java)
                    errorResponse?.errorMessage
                        ?: errorResponse?.errors?.errorText
                        ?: errorResponse?.message
                        ?: "Помилка сервера (${e.code()})"
                } catch (jsonE: Exception) {
                    // JSON не вдалося розібрати - шукаємо текстову помилку
                    if (errorBody.contains("error", ignoreCase = true)) {
                        "Помилка сервера: ${errorBody.take(200)}"
                    } else {
                        "Помилка сервера (${e.code()})"
                    }
                }
            } else {
                when (e.code()) {
                    500 -> "Внутрішня помилка сервера. Спробуйте пізніше"
                    502 -> "Сервер тимчасово недоступний"
                    503 -> "Сервіс тимчасово недоступний"
                    429 -> "Забагато запитів. Зачекайте та спробуйте ще раз"
                    else -> "Помилка сервера (${e.code()})"
                }
            }
        } catch (parseE: Exception) {
            Log.e("RegisterViewModel", "Помилка парсингу HTTP error", parseE)
            "Помилка сервера (${e.code()})"
        }
    }

    /**
     * Генерує унікальний session ID для WoWonder API
     * Формат схожий на те, що генерує сервер: SHA1 + MD5 + random
     */
    private fun generateSessionId(): String {
        val timestamp = System.currentTimeMillis()
        val random = (100000000..999999999).random()
        val baseString = "$timestamp-$random-${System.nanoTime()}"

        // Генеруємо хеш схожий на WoWonder формат
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(baseString.toByteArray())
            .joinToString("") { "%02x".format(it) }
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
