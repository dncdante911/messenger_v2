package com.worldmates.messenger.ui.premium

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class PricingPlan { MONTHLY, YEARLY }

data class PremiumUiState(
    val isPro: Boolean = false,
    val proExpiresAt: Long = 0L,
    val selectedPlan: PricingPlan = PricingPlan.YEARLY,
    val isLoading: Boolean = false,
    val error: String? = null
)

class PremiumViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState

    init {
        _uiState.value = _uiState.value.copy(
            isPro = UserSession.isProActive,
            proExpiresAt = UserSession.proExpiresAt
        )
    }

    fun selectPlan(plan: PricingPlan) {
        _uiState.value = _uiState.value.copy(selectedPlan = plan)
    }

    /**
     * Відкриває сторінку оплати на сайті. Сервер оброблює транзакцію та встановлює isPro=1.
     * Після повернення в app SubscriptionSyncWorker синхронізує статус.
     */
    fun openPaymentPage() {
        val token = UserSession.accessToken ?: return
        val plan = if (_uiState.value.selectedPlan == PricingPlan.YEARLY) "yearly" else "monthly"
        val url = "${Constants.BASE_URL.trimEnd('/')}/../premium?token=$token&plan=$plan&return_url=worldmates://premium_activated"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) { /* Browser not available */ }
    }

    /** Синхронізація статусу підписки після повернення з оплати */
    fun syncSubscription() {
        val token = UserSession.accessToken ?: return
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val api = RetrofitClient.apiService
                val response = api.getUserData(accessToken = token)
                if (response.apiStatus == 200) {
                    val user = response.userData
                    if (user != null) {
                        val expiresMs = parseExpiresAt(user.proExpiresAt)
                        UserSession.updateProStatus(user.isPro, user.proType, expiresMs)
                        _uiState.value = _uiState.value.copy(
                            isPro = UserSession.isProActive,
                            proExpiresAt = expiresMs,
                            isLoading = false
                        )
                    }
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** Парсить рядок дати "YYYY-MM-DD HH:mm:ss" → Unix timestamp в мс. */
    private fun parseExpiresAt(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            sdf.parse(raw)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }
}
