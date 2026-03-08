package com.worldmates.messenger.ui.premium

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.services.SubscriptionSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class PaymentProvider { WAYFORPAY, LIQPAY }

data class PremiumUiState(
    val isPro:        Boolean = false,
    val proExpiresAt: Long    = 0L,
    val daysLeft:     Int     = 0,
    val months:       Int     = 1,       // slider value 1..24
    val amountUah:    Int     = 149,     // calculated price
    val perMonthUah:  Int     = 149,     // price per month
    val isLoading:    Boolean = false,
    val paymentUrl:   String? = null,    // set when payment URL is ready
    val error:        String? = null
)

class PremiumViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PremiumUiState())
    val uiState: StateFlow<PremiumUiState> = _uiState

    init {
        val now = System.currentTimeMillis()
        val exp = UserSession.proExpiresAt
        _uiState.value = _uiState.value.copy(
            isPro        = UserSession.isProActive,
            proExpiresAt = exp,
            daysLeft     = if (exp > now) ((exp - now) / 86_400_000).toInt() else 0
        )
        recalcPrice(1)
    }

    fun setMonths(months: Int) {
        recalcPrice(months)
    }

    private fun recalcPrice(months: Int) {
        val total      = calcPrice(months)
        val perMonth   = (total.toFloat() / months).toInt()
        _uiState.value = _uiState.value.copy(
            months      = months,
            amountUah   = total,
            perMonthUah = perMonth
        )
    }

    /**
     * Called when user taps a payment button.
     * Calls Node.js to create payment URL, then opens it in Custom Tab.
     */
    fun pay(provider: PaymentProvider) {
        val months = _uiState.value.months
        if (months < 1) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val api      = NodeRetrofitClient.subscriptionApi
                val provStr  = if (provider == PaymentProvider.WAYFORPAY) "wayforpay" else "liqpay"
                val response = api.createPayment(months = months, provider = provStr)
                if (response.apiStatus == 200 && response.paymentUrl.isNotBlank()) {
                    openUrl(response.paymentUrl)
                    _uiState.value = _uiState.value.copy(isLoading = false, paymentUrl = response.paymentUrl)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.errorMessage ?: "Помилка. Спробуйте ще раз."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Немає зв'язку. Перевірте інтернет."
                )
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }

    /** Called in onResume after user returns from browser */
    fun syncSubscription() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val api      = NodeRetrofitClient.subscriptionApi
                val response = api.getStatus()
                if (response.apiStatus == 200) {
                    val expMs = response.proTime * 1000L
                    UserSession.updateProStatus(response.isPro, response.proType, expMs)
                    val now = System.currentTimeMillis()
                    _uiState.value = _uiState.value.copy(
                        isPro        = UserSession.isProActive,
                        proExpiresAt = expMs,
                        daysLeft     = if (expMs > now) ((expMs - now) / 86_400_000).toInt() else 0,
                        isLoading    = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (_: Exception) {
                // Fallback to WorkManager sync
                SubscriptionSyncWorker.runOnce(getApplication())
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        /**
         * Mirrors the server-side calcPrice() in routes/subscription.js.
         * Discounts: 1m=0%, 2-3m=5%, 4-6m=10%, 7-12m=15%, 13-24m=20%
         */
        const val BASE_PRICE_UAH = 149

        fun calcPrice(months: Int): Int {
            val discount = when {
                months >= 13 -> 0.80f
                months >= 7  -> 0.85f
                months >= 4  -> 0.90f
                months >= 2  -> 0.95f
                else         -> 1.00f
            }
            return (BASE_PRICE_UAH * months * discount).toInt()
        }
    }
}
