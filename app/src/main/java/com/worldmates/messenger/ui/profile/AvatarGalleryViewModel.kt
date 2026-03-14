package com.worldmates.messenger.ui.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.model.UserAvatar
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AvatarGalleryVM"

class AvatarGalleryViewModel : ViewModel() {

    private val _avatars   = MutableStateFlow<List<UserAvatar>>(emptyList())
    val avatars: StateFlow<List<UserAvatar>> = _avatars

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /** null = idle, non-null = error message */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Avatar upload progress / limit info */
    private val _limitInfo = MutableStateFlow<LimitInfo?>(null)
    val limitInfo: StateFlow<LimitInfo?> = _limitInfo

    data class LimitInfo(val count: Int, val limit: Int, val isPremium: Boolean)

    // ─── Load ──────────────────────────────────────────────────────────────────

    fun loadAvatars(userId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val resp = NodeRetrofitClient.api.getUserAvatars(userId)
                if (resp.apiStatus == 200) {
                    _avatars.value = resp.avatars ?: emptyList()
                } else {
                    _error.value = resp.errorMessage ?: "Failed to load avatars"
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadAvatars error", e)
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Upload ────────────────────────────────────────────────────────────────

    fun uploadAvatar(context: Context, uri: Uri, setAsMain: Boolean = true, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val tmpFile = uriToTempFile(context, uri) ?: run {
                    _error.value = "Cannot read file"
                    _isLoading.value = false
                    onDone(false)
                    return@launch
                }

                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when {
                    mimeType.contains("gif")  -> ".gif"
                    mimeType.contains("png")  -> ".png"
                    mimeType.contains("webp") -> ".webp"
                    else                      -> ".jpg"
                }

                val requestFile = tmpFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("avatar", "avatar$ext", requestFile)
                val setMainBody = (if (setAsMain) "true" else "false")
                    .toRequestBody("text/plain".toMediaTypeOrNull())

                val resp = NodeRetrofitClient.api.uploadAvatar(body, setMainBody)
                tmpFile.delete()

                if (resp.apiStatus == 200 && resp.avatar != null) {
                    // Prepend the new avatar so it appears first if it's main
                    _avatars.value = if (setAsMain) {
                        listOf(resp.avatar) + _avatars.value
                    } else {
                        _avatars.value + resp.avatar
                    }
                    _limitInfo.value = LimitInfo(
                        count     = resp.count,
                        limit     = resp.limit,
                        isPremium = resp.limit > 10
                    )
                    onDone(true)
                } else {
                    _error.value = resp.errorMessage ?: "Upload failed"
                    onDone(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "uploadAvatar error", e)
                _error.value = e.message
                onDone(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Set main ──────────────────────────────────────────────────────────────

    fun setMainAvatar(avatarId: Long, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val resp = NodeRetrofitClient.api.setMainAvatar(avatarId)
                if (resp.apiStatus == 200) {
                    // Move selected avatar to front of the list
                    val current = _avatars.value.toMutableList()
                    val idx = current.indexOfFirst { it.id == avatarId }
                    if (idx > 0) {
                        val moved = current.removeAt(idx)
                        current.add(0, moved)
                        _avatars.value = current
                    }
                    onDone(true)
                } else {
                    _error.value = resp.errorMessage
                    onDone(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "setMainAvatar error", e)
                _error.value = e.message
                onDone(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    fun deleteAvatar(avatarId: Long, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val resp = NodeRetrofitClient.api.deleteAvatar(avatarId)
                if (resp.apiStatus == 200) {
                    _avatars.value = _avatars.value.filter { it.id != avatarId }
                    onDone(true)
                } else {
                    _error.value = resp.errorMessage
                    onDone(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteAvatar error", e)
                _error.value = e.message
                onDone(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Reorder ───────────────────────────────────────────────────────────────

    fun reorderAvatars(orderedIds: List<Long>) {
        viewModelScope.launch {
            try {
                NodeRetrofitClient.api.reorderAvatars(orderedIds)
                // Reorder local list to match
                val byId = _avatars.value.associateBy { it.id }
                _avatars.value = orderedIds.mapNotNull { byId[it] }
            } catch (e: Exception) {
                Log.e(TAG, "reorderAvatars error", e)
            }
        }
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    private fun uriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val tmp = File.createTempFile("avatar_", ".tmp", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            tmp
        } catch (e: Exception) {
            Log.e(TAG, "uriToTempFile error", e)
            null
        }
    }
}
