package com.worldmates.messenger.ui.verification

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VerificationViewModel(application: Application) : AndroidViewModel(application) {

    private val _verificationState = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val verificationState: StateFlow<VerificationState> = _verificationState

    private val _resendTimer = MutableStateFlow(0)
    val resendTimer: StateFlow<Int> = _resendTimer

    private val api = NodeRetrofitClient.api

    /**
     * Відправка коду верифікації
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
                val response = api.sendVerificationCode(
                    verificationType = verificationType,
                    contactInfo      = contactInfo,
                    username         = username
                )

                if (response.actualStatus == 200) {
                    _verificationState.value = VerificationState.CodeSent
                    Log.d("VerificationVM", "Код успішно надіслано на $contactInfo")
                    startResendTimer()
                } else {
                    val errorMsg = response.errors ?: response.message ?: getApplication<Application>().getString(R.string.error_send_code)
                    _verificationState.value = VerificationState.Error(errorMsg)
                    Log.e("VerificationVM", "Помилка: $errorMsg")
                }
            } catch (e: Exception) {
                _verificationState.value = VerificationState.Error(getApplication<Application>().getString(R.string.geo_network_error, e.localizedMessage ?: ""))
                Log.e("VerificationVM", "Помилка відправки коду", e)
            }
        }
    }

    /**
     * Перевірка коду верифікації
     */
    fun verifyCode(
        verificationType: String,
        contactInfo: String,
        code: String,
        username: String? = null
    ) {
        if (code.length != 6) {
            _verificationState.value = VerificationState.Error(getApplication<Application>().getString(R.string.code_must_be_6_digits))
            return
        }

        _verificationState.value = VerificationState.Loading

        viewModelScope.launch {
            try {
                val response = api.verifyCode(
                    verificationType = verificationType,
                    contactInfo      = contactInfo,
                    code             = code,
                    username         = username
                )

                when {
                    response.apiStatus == 200 && response.accessToken != null && response.userId != null -> {
                        UserSession.saveSession(
                            response.accessToken!!,
                            response.userId!!,
                            username,
                            null
                        )
                        _verificationState.value = VerificationState.Success
                        Log.d("VerificationVM", "Верифікацію завершено, user ${response.userId}")
                    }
                    response.apiStatus == 400 -> {
                        val errorMsg = response.errors ?: response.message ?: getApplication<Application>().getString(R.string.invalid_code_error)
                        _verificationState.value = VerificationState.Error(errorMsg)
                        Log.e("VerificationVM", "Помилка верифікації: $errorMsg")
                    }
                    else -> {
                        val errorMsg = response.errors ?: response.message ?: getApplication<Application>().getString(R.string.unknown_error)
                        _verificationState.value = VerificationState.Error(errorMsg)
                        Log.e("VerificationVM", "Помилка ${response.apiStatus}: $errorMsg")
                    }
                }
            } catch (e: Exception) {
                _verificationState.value = VerificationState.Error(getApplication<Application>().getString(R.string.geo_network_error, e.localizedMessage ?: ""))
                Log.e("VerificationVM", "Помилка верифікації", e)
            }
        }
    }

    /**
     * Повторна відправка коду
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
                val response = api.sendVerificationCode(
                    verificationType = verificationType,
                    contactInfo      = contactInfo,
                    username         = username
                )

                if (response.actualStatus == 200) {
                    _verificationState.value = VerificationState.CodeSent
                    Log.d("VerificationVM", "Код надіслано повторно")
                    startResendTimer()
                } else {
                    val errorMsg = response.errors ?: response.message ?: getApplication<Application>().getString(R.string.error_send_code)
                    _verificationState.value = VerificationState.Error(errorMsg)
                    Log.e("VerificationVM", "Помилка: $errorMsg")
                }
            } catch (e: Exception) {
                _verificationState.value = VerificationState.Error(getApplication<Application>().getString(R.string.geo_network_error, e.localizedMessage ?: ""))
                Log.e("VerificationVM", "Помилка повторної відправки коду", e)
            }
        }
    }

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
