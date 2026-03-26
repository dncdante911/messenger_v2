package com.worldmates.messenger.ui.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.Story
import com.worldmates.messenger.data.model.User
import com.worldmates.messenger.data.model.UserRating
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.UserMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class UserProfileViewModel : ViewModel() {

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profileState: StateFlow<ProfileState> = _profileState

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    private val _ratingState = MutableStateFlow<RatingState>(RatingState.Loading)
    val ratingState: StateFlow<RatingState> = _ratingState

    private val _avatarUploadState = MutableStateFlow<AvatarUploadState>(AvatarUploadState.Idle)
    val avatarUploadState: StateFlow<AvatarUploadState> = _avatarUploadState

    private val _mediaState = MutableStateFlow<MediaState>(MediaState.Idle)
    val mediaState: StateFlow<MediaState> = _mediaState

    private val _archiveState = MutableStateFlow<ArchiveState>(ArchiveState.Idle)
    val archiveState: StateFlow<ArchiveState> = _archiveState

    /**
     * Завантажити дані профілю користувача
     */
    fun loadUserProfile(userId: Long? = null) {
        _profileState.value = ProfileState.Loading

        viewModelScope.launch {
            try {
                val targetUserId = userId ?: UserSession.userId

                // Use Node.js API — no access_token param needed (sent via header by NodeRetrofitClient)
                val response = if (userId == null || userId == UserSession.userId) {
                    NodeRetrofitClient.profileApi.getMyProfile()
                } else {
                    NodeRetrofitClient.profileApi.getUserProfile(targetUserId)
                }

                Log.d("UserProfileViewModel", "Profile response: apiStatus=${response.apiStatus}")

                if (response.apiStatus == 200 && response.userData != null) {
                    _profileState.value = ProfileState.Success(response.userData)
                    Log.d("UserProfileViewModel", "Profile loaded: ${response.userData.username}")

                    // Load rating for this user
                    loadUserRating(targetUserId)
                } else {
                    val errorMsg = response.errorMessage ?: "Failed to load profile"
                    _profileState.value = ProfileState.Error(errorMsg)
                    Log.e("UserProfileViewModel", "Error: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Network error: ${e.localizedMessage}"
                _profileState.value = ProfileState.Error(errorMsg)
                Log.e("UserProfileViewModel", "Exception loading profile", e)
            }
        }
    }

    /**
     * Оновити дані профілю
     */
    fun updateProfile(
        firstName: String?,
        lastName: String?,
        about: String?,
        birthday: String?,
        gender: String?,
        phoneNumber: String?,
        website: String?,
        working: String?,
        address: String?,
        city: String?,
        school: String?
    ) {
        _updateState.value = UpdateState.Loading

        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.profileApi.updateMyProfile(
                    firstName  = firstName,
                    lastName   = lastName,
                    about      = about,
                    birthday   = birthday,
                    gender     = gender,
                    address    = address,
                    city       = city,
                    website    = website,
                    working    = working,
                    school     = school,
                )

                Log.d("UserProfileViewModel", "Update response: apiStatus=${response.apiStatus}")

                if (response.apiStatus == 200) {
                    _updateState.value = UpdateState.Success
                    // Reload profile to get updated data
                    loadUserProfile()
                } else {
                    val errorMsg = response.errorMessage ?: "Failed to update profile"
                    _updateState.value = UpdateState.Error(errorMsg)
                    Log.e("UserProfileViewModel", "Update error: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Network error: ${e.localizedMessage}"
                _updateState.value = UpdateState.Error(errorMsg)
                Log.e("UserProfileViewModel", "Exception updating profile", e)
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    /**
     * Завантажити новий аватар користувача
     */
    fun uploadAvatar(uri: Uri, context: Context) {
        _avatarUploadState.value = AvatarUploadState.Loading

        viewModelScope.launch {
            try {
                // Копіюємо файл з Uri в кеш
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot read image")
                val tempFile = File(context.cacheDir, "avatar_upload_${System.currentTimeMillis()}.jpg")
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()

                val requestBody = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", tempFile.name, requestBody)

                // Avatar upload already on Node.js — uses NodeRetrofitClient.api (NodeApi)
                val response = NodeRetrofitClient.api.uploadAvatar(filePart)

                // Видаляємо тимчасовий файл
                tempFile.delete()

                Log.d("UserProfileViewModel", "Avatar upload response: ${response.apiStatus}")

                if (response.apiStatus == 200) {
                    // Оновлюємо аватар в UserSession (NodeApi повертає avatar.url)
                    response.avatar?.url?.let { url ->
                        UserSession.avatar = url
                    }
                    _avatarUploadState.value = AvatarUploadState.Success
                    // Перезавантажуємо профіль
                    loadUserProfile()
                } else {
                    _avatarUploadState.value = AvatarUploadState.Error(
                        response.errorMessage ?: "Не вдалося завантажити аватар"
                    )
                }
            } catch (e: Exception) {
                _avatarUploadState.value = AvatarUploadState.Error(
                    "Помилка: ${e.localizedMessage}"
                )
                Log.e("UserProfileViewModel", "Exception uploading avatar", e)
            }
        }
    }

    fun resetAvatarUploadState() {
        _avatarUploadState.value = AvatarUploadState.Idle
    }

    /**
     * Оновити оформлення профілю (акцент-колір, бейдж, стиль заголовка).
     */
    fun updateAppearance(
        profileAccent: String? = null,
        profileBadge: String? = null,
        profileHeaderStyle: String? = null,
    ) {
        _updateState.value = UpdateState.Loading
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.profileApi.updateAppearance(
                    profileAccent       = profileAccent,
                    profileBadge        = profileBadge,
                    profileHeaderStyle  = profileHeaderStyle,
                )
                if (response.apiStatus == 200) {
                    _updateState.value = UpdateState.Success
                    loadUserProfile()
                } else {
                    _updateState.value = UpdateState.Error(response.errorMessage ?: "Failed to update appearance")
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Завантажити рейтинг користувача
     */
    fun loadUserRating(userId: Long) {
        _ratingState.value = RatingState.Loading

        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.profileApi.getUserRating(
                    userId = userId,
                    includeDetails = "1",
                )

                Log.d("UserProfileViewModel", "Rating response: apiStatus=${response.apiStatus}")

                if (response.apiStatus == 200 && response.rating != null) {
                    _ratingState.value = RatingState.Success(response.rating)
                    Log.d("UserProfileViewModel", "Rating loaded: likes=${response.rating.likes}, dislikes=${response.rating.dislikes}")
                } else {
                    val errorMsg = response.errorMessage ?: "Failed to load rating"
                    _ratingState.value = RatingState.Error(errorMsg)
                    Log.e("UserProfileViewModel", "Rating error: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Network error: ${e.localizedMessage}"
                _ratingState.value = RatingState.Error(errorMsg)
                Log.e("UserProfileViewModel", "Exception loading rating", e)
            }
        }
    }

    /**
     * Поставити лайк або дизлайк користувачу
     */
    fun rateUser(userId: Long, ratingType: String, comment: String? = null) {
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.profileApi.rateUser(
                    userId     = userId,
                    ratingType = ratingType,
                    comment    = comment,
                )

                Log.d("UserProfileViewModel", "Rate user response: apiStatus=${response.apiStatus}, action=${response.action}")

                if (response.apiStatus == 200 && response.userRating != null) {
                    // Update rating state with new data
                    _ratingState.value = RatingState.Success(response.userRating)
                    Log.d("UserProfileViewModel", "Rating updated: action=${response.action}")
                } else {
                    val errorMsg = response.errorMessage ?: "Failed to rate user"
                    Log.e("UserProfileViewModel", "Rate user error: $errorMsg")
                }
            } catch (e: Exception) {
                Log.e("UserProfileViewModel", "Exception rating user", e)
            }
        }
    }

    fun loadMyMedia() {
        if (_mediaState.value is MediaState.Loading) return
        _mediaState.value = MediaState.Loading
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.profileApi.getMyMedia()
                if (response.apiStatus == 200) {
                    _mediaState.value = MediaState.Success(response.media ?: emptyList())
                } else {
                    _mediaState.value = MediaState.Error(response.errorMessage ?: "Failed to load media")
                }
            } catch (e: Exception) {
                Log.e("UserProfileViewModel", "Exception loading media", e)
                _mediaState.value = MediaState.Error(e.localizedMessage ?: "Network error")
            }
        }
    }

    fun loadMyStoriesArchive() {
        if (_archiveState.value is ArchiveState.Loading) return
        _archiveState.value = ArchiveState.Loading
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.storiesApi.getMyStoriesArchive()
                if (response.apiStatus == 200) {
                    _archiveState.value = ArchiveState.Success(response.stories ?: emptyList())
                } else {
                    _archiveState.value = ArchiveState.Error(response.errorMessage ?: "Failed to load archive")
                }
            } catch (e: Exception) {
                Log.e("UserProfileViewModel", "Exception loading story archive", e)
                _archiveState.value = ArchiveState.Error(e.localizedMessage ?: "Network error")
            }
        }
    }
}

sealed class ProfileState {
    object Loading : ProfileState()
    data class Success(val user: User) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

sealed class UpdateState {
    object Idle : UpdateState()
    object Loading : UpdateState()
    object Success : UpdateState()
    data class Error(val message: String) : UpdateState()
}

sealed class RatingState {
    object Loading : RatingState()
    data class Success(val rating: UserRating) : RatingState()
    data class Error(val message: String) : RatingState()
}

sealed class AvatarUploadState {
    object Idle : AvatarUploadState()
    object Loading : AvatarUploadState()
    object Success : AvatarUploadState()
    data class Error(val message: String) : AvatarUploadState()
}

sealed class MediaState {
    object Idle : MediaState()
    object Loading : MediaState()
    data class Success(val items: List<UserMediaItem>) : MediaState()
    data class Error(val message: String) : MediaState()
}

sealed class ArchiveState {
    object Idle : ArchiveState()
    object Loading : ArchiveState()
    data class Success(val stories: List<Story>) : ArchiveState()
    data class Error(val message: String) : ArchiveState()
}
