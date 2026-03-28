package com.worldmates.messenger.ui.business

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

data class BusinessUiState(
    val isLoading: Boolean = false,
    val profile: BusinessProfile? = null,
    val hours: List<BusinessHour> = emptyList(),
    val quickReplies: List<BusinessQuickReply> = emptyList(),
    val links: List<BusinessLink> = emptyList(),
    // Creator features
    val stats: BusinessStatsResponse? = null,
    val apiKey: BusinessApiKey? = null,
    val isStatsLoading: Boolean = false,
    val isApiKeyLoading: Boolean = false,
    val error: String? = null,
    val successMsg: String? = null
)

class BusinessViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BusinessViewModel"
    }

    private val api = NodeRetrofitClient.businessApi

    private val _state = MutableStateFlow(BusinessUiState())
    val state: StateFlow<BusinessUiState> = _state

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val profileResp = api.getMyProfile()
                val hoursResp   = api.getHours()
                val qrResp      = api.getQuickReplies()
                val linksResp   = api.getLinks()

                _state.value = _state.value.copy(
                    isLoading    = false,
                    profile      = profileResp.profile,
                    hours        = hoursResp.hours    ?: defaultHours(),
                    quickReplies = qrResp.quickReplies ?: emptyList(),
                    links        = linksResp.links    ?: emptyList()
                )
            } catch (e: Exception) {
                Log.e(TAG, "loadAll error", e)
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    fun saveProfile(req: UpdateBusinessProfileRequest) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val resp = api.updateProfile(req)
                if (resp.apiStatus == 200) {
                    _state.value = _state.value.copy(
                        isLoading  = false,
                        profile    = resp.profile,
                        successMsg = "profile_saved"
                    )
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = resp.errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "saveProfile error", e)
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteProfile() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                api.deleteProfile()
                _state.value = _state.value.copy(
                    isLoading  = false,
                    profile    = null,
                    hours      = defaultHours(),
                    successMsg = "profile_deleted"
                )
            } catch (e: Exception) {
                Log.e(TAG, "deleteProfile error", e)
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    // ── Working hours ─────────────────────────────────────────────────────────

    fun saveHours(hours: List<BusinessHourRequest>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val resp = api.updateHours(UpdateBusinessHoursRequest(hours))
                if (resp.apiStatus == 200) {
                    _state.value = _state.value.copy(
                        isLoading  = false,
                        hours      = resp.hours ?: _state.value.hours,
                        successMsg = "hours_saved"
                    )
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = resp.errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "saveHours error", e)
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    // ── Quick replies ─────────────────────────────────────────────────────────

    fun createQuickReply(shortcut: String, text: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val resp = api.createQuickReply(CreateQuickReplyRequest(shortcut = shortcut, text = text))
                if (resp.apiStatus == 200) {
                    loadQuickReplies()
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = resp.errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "createQuickReply error", e)
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateQuickReply(id: Long, shortcut: String, text: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val resp = api.updateQuickReply(id, CreateQuickReplyRequest(shortcut = shortcut, text = text))
                if (resp.apiStatus == 200) {
                    loadQuickReplies()
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = resp.errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateQuickReply error", e)
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteQuickReply(id: Long) {
        viewModelScope.launch {
            try {
                api.deleteQuickReply(id)
                _state.value = _state.value.copy(
                    quickReplies = _state.value.quickReplies.filter { it.id != id }
                )
            } catch (e: Exception) {
                Log.e(TAG, "deleteQuickReply error", e)
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    private fun loadQuickReplies() {
        viewModelScope.launch {
            try {
                val resp = api.getQuickReplies()
                _state.value = _state.value.copy(
                    isLoading    = false,
                    quickReplies = resp.quickReplies ?: emptyList(),
                    successMsg   = "qr_saved"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    // ── Business links ────────────────────────────────────────────────────────

    fun createLink(title: String, prefilledText: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val resp = api.createLink(CreateBusinessLinkRequest(title = title, prefilledText = prefilledText))
                if (resp.apiStatus == 200) {
                    loadLinks()
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = resp.errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "createLink error", e)
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteLink(id: Long) {
        viewModelScope.launch {
            try {
                api.deleteLink(id)
                _state.value = _state.value.copy(
                    links = _state.value.links.filter { it.id != id }
                )
            } catch (e: Exception) {
                Log.e(TAG, "deleteLink error", e)
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    private fun loadLinks() {
        viewModelScope.launch {
            try {
                val resp = api.getLinks()
                _state.value = _state.value.copy(
                    isLoading  = false,
                    links      = resp.links ?: emptyList(),
                    successMsg = "link_saved"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    fun loadStats(days: Int = 30) {
        _state.update { it.copy(isStatsLoading = true) }
        viewModelScope.launch {
            try {
                val resp = api.getStats(days)
                if (resp.apiStatus == 200) {
                    _state.update { it.copy(isStatsLoading = false, stats = resp) }
                } else {
                    _state.update { it.copy(isStatsLoading = false, error = resp.errorMessage) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadStats error", e)
                _state.update { it.copy(isStatsLoading = false, error = e.message) }
            }
        }
    }

    // ── Verification ─────────────────────────────────────────────────────────

    fun requestVerification() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val resp = api.requestVerification()
                if (resp.apiStatus == 200) {
                    // Refresh profile to get updated verification_status
                    val profileResp = api.getMyProfile()
                    _state.update { it.copy(
                        isLoading  = false,
                        profile    = profileResp.profile ?: it.profile,
                        successMsg = "verification_requested",
                    ) }
                } else {
                    _state.update { it.copy(isLoading = false, error = resp.errorMessage) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "requestVerification error", e)
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── API Key ───────────────────────────────────────────────────────────────

    fun loadApiKey() {
        _state.update { it.copy(isApiKeyLoading = true) }
        viewModelScope.launch {
            try {
                val resp = api.getApiKey()
                _state.update { it.copy(isApiKeyLoading = false, apiKey = resp.apiKey) }
            } catch (e: Exception) {
                Log.e(TAG, "loadApiKey error", e)
                _state.update { it.copy(isApiKeyLoading = false, error = e.message) }
            }
        }
    }

    fun generateApiKey() {
        _state.update { it.copy(isApiKeyLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val resp = api.generateApiKey()
                if (resp.apiStatus == 200) {
                    _state.update { it.copy(isApiKeyLoading = false, apiKey = resp.apiKey, successMsg = "api_key_generated") }
                } else {
                    _state.update { it.copy(isApiKeyLoading = false, error = resp.errorMessage) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "generateApiKey error", e)
                _state.update { it.copy(isApiKeyLoading = false, error = e.message) }
            }
        }
    }

    fun revokeApiKey() {
        _state.update { it.copy(isApiKeyLoading = true, error = null) }
        viewModelScope.launch {
            try {
                api.revokeApiKey()
                _state.update { it.copy(isApiKeyLoading = false, apiKey = null, successMsg = "api_key_revoked") }
            } catch (e: Exception) {
                Log.e(TAG, "revokeApiKey error", e)
                _state.update { it.copy(isApiKeyLoading = false, error = e.message) }
            }
        }
    }

    // ── Avatar ────────────────────────────────────────────────────────────────

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot read image")
                val tempFile = File(context.cacheDir, "biz_avatar_${System.currentTimeMillis()}.jpg")
                tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                inputStream.close()

                val requestBody = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", tempFile.name, requestBody)

                val response = NodeRetrofitClient.api.uploadAvatar(filePart)
                tempFile.delete()

                if (response.apiStatus == 200) {
                    response.avatar?.url?.let { url -> UserSession.avatar = url }
                    _state.update { it.copy(isLoading = false, successMsg = "avatar_saved") }
                } else {
                    _state.update { it.copy(isLoading = false, error = response.errorMessage) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "uploadAvatar error", e)
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun clearMessages() {
        _state.update { it.copy(error = null, successMsg = null) }
    }

    private fun defaultHours(): List<BusinessHour> =
        (0..6).map { d ->
            BusinessHour(weekday = d, isOpen = if (d in 1..5) 1 else 0)
        }
}
