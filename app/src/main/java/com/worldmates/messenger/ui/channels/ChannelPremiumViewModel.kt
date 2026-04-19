package com.worldmates.messenger.ui.channels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.http.*

// ─── API models ───────────────────────────────────────────────────────────────

data class ChannelPremiumStatus(
    val api_status: Int = 0,
    val is_active: Int = 0,
    val plan: String? = null,
    val expires_at: String? = null,
    val days_left: Int = 0,
    val started_at: String? = null,
    val base_price_uah: Double = 299.0,
    val plans: Map<String, ChannelPlanInfo>? = null,
    val trial_available: Int = 0,
    val trial_days: Int = 7,
    val error_message: String? = null
)

data class StartTrialResponse(
    val api_status: Int = 0,
    val expires_at: String? = null,
    val trial_days: Int = 0,
    val error_message: String? = null
)

data class ChannelPlanInfo(
    val months: Int,
    val price_uah: Int
)

data class CreateChannelPaymentRequest(
    val plan: String,
    val provider: String = "wayforpay"
)

data class CreateChannelPaymentResponse(
    val api_status: Int = 0,
    val provider: String? = null,
    val invoice_url: String? = null,    // wayforpay
    val data: String? = null,           // liqpay
    val signature: String? = null,      // liqpay
    val checkout_url: String? = null,   // liqpay
    val order_id: String? = null,
    val amount_uah: Int? = null,
    val error_message: String? = null
)

interface ChannelPremiumApi {
    @GET("api/node/channels/{channel_id}/premium/status")
    suspend fun getPremiumStatus(
        @Path("channel_id") channelId: Long
    ): ChannelPremiumStatus

    @FormUrlEncoded
    @POST("api/node/channels/{channel_id}/premium/create-payment")
    suspend fun createPayment(
        @Path("channel_id") channelId: Long,
        @Field("plan") plan: String,
        @Field("provider") provider: String
    ): CreateChannelPaymentResponse

    @POST("api/node/channels/{channel_id}/premium/start-trial")
    suspend fun startTrial(
        @Path("channel_id") channelId: Long
    ): StartTrialResponse
}

// ─── UI State ─────────────────────────────────────────────────────────────────

sealed class ChannelPremiumUiState {
    object Loading : ChannelPremiumUiState()
    data class Loaded(val status: ChannelPremiumStatus) : ChannelPremiumUiState()
    data class PaymentReady(val response: CreateChannelPaymentResponse) : ChannelPremiumUiState()
    data class Error(val message: String) : ChannelPremiumUiState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ChannelPremiumViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "ChannelPremiumVM"

        // Pricing display
        val PLAN_LABELS = mapOf(
            "monthly"   to "Monthly",
            "quarterly" to "Quarterly (3 months)",
            "annual"    to "Annual (12 months)"
        )

        val PLAN_DISCOUNTS = mapOf(
            "monthly"   to "Full price",
            "quarterly" to "10% off",
            "annual"    to "25% off"
        )
    }

    private val api: ChannelPremiumApi by lazy {
        NodeRetrofitClient.retrofit.create(ChannelPremiumApi::class.java)
    }

    private val _uiState = MutableStateFlow<ChannelPremiumUiState>(ChannelPremiumUiState.Loading)
    val uiState: StateFlow<ChannelPremiumUiState> = _uiState

    fun loadStatus(channelId: Long) {
        _uiState.value = ChannelPremiumUiState.Loading
        viewModelScope.launch {
            try {
                val status = api.getPremiumStatus(channelId)
                _uiState.value = ChannelPremiumUiState.Loaded(status)
                Log.d(TAG, "Status loaded: is_active=${status.is_active} plan=${status.plan}")
            } catch (e: Exception) {
                Log.e(TAG, "loadStatus error", e)
                _uiState.value = ChannelPremiumUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun startTrial(channelId: Long) {
        viewModelScope.launch {
            try {
                val resp = api.startTrial(channelId)
                if (resp.api_status == 200) {
                    Log.d(TAG, "Trial started: days=${resp.trial_days} expires=${resp.expires_at}")
                    loadStatus(channelId)
                } else {
                    _uiState.value = ChannelPremiumUiState.Error(resp.error_message ?: "Trial activation failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "startTrial error", e)
                _uiState.value = ChannelPremiumUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun createPayment(channelId: Long, plan: String, provider: String = "wayforpay") {
        viewModelScope.launch {
            try {
                val resp = api.createPayment(channelId, plan, provider)
                if (resp.api_status == 200) {
                    _uiState.value = ChannelPremiumUiState.PaymentReady(resp)
                    Log.d(TAG, "Payment created: provider=${resp.provider} orderId=${resp.order_id}")
                } else {
                    _uiState.value = ChannelPremiumUiState.Error(resp.error_message ?: "Payment creation failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "createPayment error", e)
                _uiState.value = ChannelPremiumUiState.Error(e.message ?: "Network error")
            }
        }
    }
}
