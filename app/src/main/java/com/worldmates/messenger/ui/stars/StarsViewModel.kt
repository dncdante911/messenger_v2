package com.worldmates.messenger.ui.stars

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.StarsPack
import com.worldmates.messenger.network.StarsTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── UI state ─────────────────────────────────────────────────────────────────

data class StarsUiState(
    val balance:          Int = 0,
    val totalPurchased:   Int = 0,
    val totalSent:        Int = 0,
    val totalReceived:    Int = 0,
    val transactions:     List<StarsTransaction> = emptyList(),
    val packs:            List<StarsPack> = emptyList(),
    val selectedPack:     StarsPack? = null,
    val isLoading:        Boolean = false,
    val isSending:        Boolean = false,
    val sendSuccess:      Boolean = false,      // flash after successful send
    val error:            String? = null,
    val successMessage:   String? = null,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class StarsViewModel(application: Application) : AndroidViewModel(application) {

    private val api = NodeRetrofitClient.starsApi

    private val _uiState = MutableStateFlow(StarsUiState())
    val uiState: StateFlow<StarsUiState> = _uiState

    init {
        loadBalance()
        loadPacks()
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    fun loadBalance() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val resp = api.getBalance()
                if (resp.apiStatus == 200) {
                    _uiState.update { it.copy(
                        isLoading      = false,
                        balance        = resp.balance,
                        totalPurchased = resp.totalPurchased,
                        totalSent      = resp.totalSent,
                        totalReceived  = resp.totalReceived,
                        transactions   = resp.recentTransactions,
                    ) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = resp.errorMessage) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadMoreTransactions() {
        val currentCount = _uiState.value.transactions.size
        viewModelScope.launch {
            try {
                val resp = api.getTransactions(limit = 20, offset = currentCount)
                if (resp.apiStatus == 200 && resp.transactions.isNotEmpty()) {
                    _uiState.update { it.copy(transactions = it.transactions + resp.transactions) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadPacks() {
        viewModelScope.launch {
            try {
                val resp = api.getPacks()
                if (resp.apiStatus == 200) {
                    _uiState.update { it.copy(
                        packs        = resp.packs,
                        selectedPack = resp.packs.firstOrNull { p -> p.isPopular } ?: resp.packs.firstOrNull(),
                    ) }
                }
            } catch (_: Exception) {}
        }
    }

    // ── Pack selection ────────────────────────────────────────────────────────

    fun selectPack(pack: StarsPack) {
        _uiState.update { it.copy(selectedPack = pack) }
    }

    // ── Purchase ─────────────────────────────────────────────────────────────

    fun purchase(provider: String = "wayforpay") {
        val pack = _uiState.value.selectedPack ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val resp = api.purchase(packId = pack.id, provider = provider)
                if (resp.apiStatus == 200 && resp.paymentUrl.isNotBlank()) {
                    openUrl(resp.paymentUrl)
                    _uiState.update { it.copy(isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = resp.errorMessage) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Send ─────────────────────────────────────────────────────────────────

    fun sendStars(toUserId: Int, amount: Int, note: String?) {
        if (amount <= 0 || _uiState.value.isSending) return
        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            try {
                val resp = api.sendStars(toUserId = toUserId, amount = amount, note = note?.ifBlank { null })
                if (resp.apiStatus == 200) {
                    _uiState.update { it.copy(
                        isSending      = false,
                        balance        = resp.newBalance,
                        sendSuccess    = true,
                        successMessage = null,
                    ) }
                    // Оновлюємо список транзакцій
                    loadBalance()
                } else {
                    _uiState.update { it.copy(isSending = false, error = resp.errorMessage) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, error = e.message) }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Викликається в onResume після повернення з браузера оплати. */
    fun syncAfterPayment() {
        loadBalance()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSendSuccess() {
        _uiState.update { it.copy(sendSuccess = false) }
    }

    private fun openUrl(url: String) {
        try {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }
}
