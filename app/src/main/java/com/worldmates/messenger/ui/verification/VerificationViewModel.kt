package com.worldmates.messenger.ui.verification

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VerificationViewModel : ViewModel() {

    private val _verificationState = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val verificationState: StateFlow<VerificationState> = _verificationState

    private val _resendTimer = MutableStateFlow(0)
    val resendTimer: StateFlow<Int> = _resendTimer

    /**
     * Отправка кода верификации
     */
    fun sendVerificationCode(
        verificationType: String,
        contactInfo: String,
        username: String? = null
    ) {
        if (_resendTimer.value > 0) {
            Log.d("VerificationVM", "Таймер ще не закінчився: ${_resendTimer.value}")
            return
        }

        _verificationState.value = VerificationState.Sending

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.sendVerificationCode(
                    verificationType = verificationType,
                    contactInfo = contactInfo,
                    username = username
                )

                if (response.actualStatus == 200) {
                    _verificationState.value = VerificationState.CodeSent
                    Log.d("VerificationVM", "Код успішно надіслано на $contactInfo")
                    startResendTimer()
                } else {
                    val errorMsg = response.errors ?: response.message ?: "Помилка відправки коду"
                    _verificationState.value = VerificationState.Error(errorMsg)
                    Log.e("VerificationVM", "Помилка: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка мережі: ${e.localizedMessage}"
                _verificationState.value = VerificationState.Error(errorMsg)
                Log.e("VerificationVM", "Помилка відправки коду", e)
            }
        }
    }

    /**
     * Проверка кода верификации
     */
    fun verifyCode(
        verificationType: String,
        contactInfo: String,
        code: String,
        username: String? = null
    ) {
        if (code.length != 6) {
            _verificationState.value = VerificationState.Error("Код має містити 6 цифр")
            return
        }

        _verificationState.value = VerificationState.Loading

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.verifyCode(
                    verificationType = verificationType,
                    contactInfo = contactInfo,
                    code = code,
                    username = username
                )

                when {
                    response.apiStatus == 200 && response.accessToken != null && response.userId != null -> {
                        // Успешная верификация - сохраняем сессию
                        UserSession.saveSession(
                            response.accessToken!!,
                            response.userId!!.toLong(),
                            username,
                            null
                        )
                        _verificationState.value = VerificationState.Success
                        Log.d("VerificationVM", "Верифікацію успішно завершено! User ID: ${response.userId}")
                    }
                    response.apiStatus == 400 -> {
                        val errorMsg = response.errors ?: response.message ?: "Невірний код"
                        _verificationState.value = VerificationState.Error(errorMsg)
                        Log.e("VerificationVM", "Помилка верифікації: $errorMsg")
                    }
                    else -> {
                        val errorMsg = response.errors ?: response.message ?: "Невідома помилка"
                        _verificationState.value = VerificationState.Error(errorMsg)
                        Log.e("VerificationVM", "Помилка: ${response.apiStatus} - $errorMsg")
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка мережі: ${e.localizedMessage}"
                _verificationState.value = VerificationState.Error(errorMsg)
                Log.e("VerificationVM", "Помилка верифікації", e)
            }
        }
    }

    /**
     * Повторная отправка кода
     */
    fun resendCode(
        verificationType: String,
        contactInfo: String,
        username: String? = null
    ) {
        if (_resendTimer.value > 0) {
            Log.d("VerificationVM", "Таймер ще не закінчився: ${_resendTimer.value}")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.resendVerificationCode(
                    verificationType = verificationType,
                    contactInfo = contactInfo,
                    username = username
                )

                if (response.apiStatus == 200) {
                    _verificationState.value = VerificationState.CodeSent
                    Log.d("VerificationVM", "Код успішно надіслано повторно")
                    startResendTimer()
                } else {
                    val errorMsg = response.errors ?: response.message ?: "Помилка відправки коду"
                    _verificationState.value = VerificationState.Error(errorMsg)
                    Log.e("VerificationVM", "Помилка: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка мережі: ${e.localizedMessage}"
                _verificationState.value = VerificationState.Error(errorMsg)
                Log.e("VerificationVM", "Помилка повторної відправки коду", e)
            }
        }
    }

    /**
     * Таймер для повторной отправки кода (60 секунд)
     */
    private fun startResendTimer() {
        viewModelScope.launch {
            _resendTimer.value = 60
            while (_resendTimer.value > 0) {
                delay(1000)
                _resendTimer.value -= 1
            }
        }
    }

    fun resetState() {
        _verificationState.value = VerificationState.Idle
    }
}

sealed class VerificationState {
    object Idle : VerificationState()
    object Sending : VerificationState()
    object CodeSent : VerificationState()
    object Loading : VerificationState()
    object Success : VerificationState()
    data class Error(val message: String) : VerificationState()
}
