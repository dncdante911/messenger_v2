package com.worldmates.messenger.ui.settings

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.Group
import com.worldmates.messenger.data.model.User
import com.worldmates.messenger.data.model.toGroup
import com.worldmates.messenger.network.FileManager
import com.worldmates.messenger.network.RetrofitClient
import com.worldmates.messenger.update.AppUpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val api = RetrofitClient.apiService
    private val fileManager = FileManager(application.applicationContext)

    private val _username = MutableStateFlow(UserSession.username ?: "")
    val username: StateFlow<String> = _username

    private val _avatar = MutableStateFlow(UserSession.avatar)
    val avatar: StateFlow<String?> = _avatar

    private val _userId = MutableStateFlow(UserSession.userId)
    val userId: StateFlow<Long> = _userId

    private val _userData = MutableStateFlow<User?>(null)
    val userData: StateFlow<User?> = _userData

    private val _myGroups = MutableStateFlow<List<Group>>(emptyList())
    val myGroups: StateFlow<List<Group>> = _myGroups

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage

    init {
        loadUserData()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            _username.value = UserSession.username ?: ""
            _avatar.value = UserSession.avatar
            _userId.value = UserSession.userId
        }
    }

    /**
     * Загрузить полные данные пользователя из API
     */
    fun fetchUserData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val accessToken = UserSession.accessToken
                if (accessToken == null) {
                    _errorMessage.value = "Токен доступу відсутній"
                    _isLoading.value = false
                    return@launch
                }

                val response = api.getUserData(
                    accessToken = accessToken,
                    userId = UserSession.userId,
                    fetch = "user_data" // Загружаем только данные пользователя
                )

                if (response.apiStatus == 200 && response.userData != null) {
                    _userData.value = response.userData

                    // Обновить сессию
                    UserSession.username = response.userData.username
                    UserSession.avatar = response.userData.avatar

                    _username.value = response.userData.username
                    _avatar.value = response.userData.avatar
                    _userId.value = response.userData.userId

                    Log.d(TAG, "User data loaded successfully")
                } else {
                    _errorMessage.value = response.errorMessage ?: "Помилка завантаження даних"
                    Log.e(TAG, "Error loading user data: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Помилка: ${e.message}"
                Log.e(TAG, "Exception loading user data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Обновить профиль пользователя
     */
    fun updateUserProfile(
        firstName: String? = null,
        lastName: String? = null,
        about: String? = null,
        birthday: String? = null,
        gender: String? = null,
        phoneNumber: String? = null,
        website: String? = null,
        working: String? = null,
        address: String? = null,
        city: String? = null,
        school: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            try {
                val accessToken = UserSession.accessToken
                if (accessToken == null) {
                    _errorMessage.value = "Токен доступу відсутній"
                    _isLoading.value = false
                    return@launch
                }

                val response = api.updateUserData(
                    accessToken = accessToken,
                    firstName = firstName,
                    lastName = lastName,
                    about = about,
                    birthday = birthday,
                    gender = gender,
                    phoneNumber = phoneNumber,
                    website = website,
                    working = working,
                    address = address,
                    city = city,
                    school = school
                )

                if (response.apiStatus == 200) {
                    _successMessage.value = "Профіль успішно оновлено"
                    fetchUserData()
                    Log.d(TAG, "User profile updated successfully")
                } else {
                    _errorMessage.value = response.errorMessage ?: "Помилка оновлення профілю"
                    Log.e(TAG, "Error updating profile: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Помилка: ${e.message}"
                Log.e(TAG, "Exception updating profile", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Обновить настройки конфиденциальности
     */
    fun updatePrivacySettings(
        followPrivacy: String? = null,
        friendPrivacy: String? = null,
        postPrivacy: String? = null,
        messagePrivacy: String? = null,
        confirmFollowers: String? = null,
        showActivitiesPrivacy: String? = null,
        birthPrivacy: String? = null,
        visitPrivacy: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            try {
                val accessToken = UserSession.accessToken
                if (accessToken == null) {
                    _errorMessage.value = "Токен доступу відсутній"
                    _isLoading.value = false
                    return@launch
                }

                val response = api.updatePrivacySettings(
                    accessToken = accessToken,
                    followPrivacy = followPrivacy,
                    friendPrivacy = friendPrivacy,
                    postPrivacy = postPrivacy,
                    messagePrivacy = messagePrivacy,
                    confirmFollowers = confirmFollowers,
                    showActivitiesPrivacy = showActivitiesPrivacy,
                    birthPrivacy = birthPrivacy,
                    visitPrivacy = visitPrivacy
                )

                if (response.apiStatus == 200) {
                    _successMessage.value = "Налаштування конфіденційності оновлено"
                    Log.d(TAG, "Privacy settings updated successfully")
                } else {
                    _errorMessage.value = response.errorMessage ?: "Помилка оновлення налаштувань"
                    Log.e(TAG, "Error updating privacy: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Помилка: ${e.message}"
                Log.e(TAG, "Exception updating privacy", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Обновить настройки уведомлений
     */
    fun updateNotificationSettings(
        emailNotification: Int? = null,
        eLiked: Int? = null,
        eWondered: Int? = null,
        eShared: Int? = null,
        eFollowed: Int? = null,
        eCommented: Int? = null,
        eVisited: Int? = null,
        eLikedPage: Int? = null,
        eMentioned: Int? = null,
        eJoinedGroup: Int? = null,
        eAccepted: Int? = null,
        eProfileWallPost: Int? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            try {
                val accessToken = UserSession.accessToken
                if (accessToken == null) {
                    _errorMessage.value = "Токен доступу відсутній"
                    _isLoading.value = false
                    return@launch
                }

                val response = api.updateNotificationSettings(
                    accessToken = accessToken,
                    emailNotification = emailNotification,
                    eLiked = eLiked,
                    eWondered = eWondered,
                    eShared = eShared,
                    eFollowed = eFollowed,
                    eCommented = eCommented,
                    eVisited = eVisited,
                    eLikedPage = eLikedPage,
                    eMentioned = eMentioned,
                    eJoinedGroup = eJoinedGroup,
                    eAccepted = eAccepted,
                    eProfileWallPost = eProfileWallPost
                )

                if (response.apiStatus == 200) {
                    _successMessage.value = "Налаштування сповіщень оновлено"
                    Log.d(TAG, "Notification settings updated successfully")
                } else {
                    _errorMessage.value = response.errorMessage ?: "Помилка оновлення налаштувань"
                    Log.e(TAG, "Error updating notifications: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Помилка: ${e.message}"
                Log.e(TAG, "Exception updating notifications", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Загрузить аватар пользователя
     */
    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _successMessage.value = null

            try {
                val accessToken = UserSession.accessToken
                if (accessToken == null) {
                    _errorMessage.value = "Токен доступу відсутній"
                    _isLoading.value = false
                    return@launch
                }

                // Копировать файл из URI в кеш
                val file = fileManager.copyUriToCache(uri)
                if (file == null) {
                    _errorMessage.value = "Не вдалося прочитати файл"
                    _isLoading.value = false
                    return@launch
                }

                // Получить MIME-тип
                val mimeType = fileManager.getMimeType(uri) ?: "image/jpeg"

                // Создать RequestBody из файла
                val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData(
                    "file",  // API upload_user_avatar очікує "file"
                    file.name,
                    requestFile
                )

                val response = api.uploadUserAvatar(
                    accessToken = accessToken,
                    file = filePart
                )

                // Удалить временный файл
                fileManager.deleteFile(file)

                if (response.apiStatus == 200 && response.url != null) {
                    _successMessage.value = "Аватар успішно оновлено"
                    UserSession.avatar = response.url
                    _avatar.value = response.url

                    // Обновить данные пользователя
                    fetchUserData()
                    Log.d(TAG, "Avatar uploaded successfully")
                } else {
                    _errorMessage.value = response.errorMessage ?: "Помилка завантаження аватара"
                    Log.e(TAG, "Error uploading avatar: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Помилка: ${e.message}"
                Log.e(TAG, "Exception uploading avatar", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Загрузить группы пользователя
     * Используем endpoint get-my-groups с типом joined_groups
     */
    fun loadMyGroups() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val accessToken = UserSession.accessToken
                if (accessToken == null) {
                    _errorMessage.value = "Токен доступу відсутній"
                    _isLoading.value = false
                    return@launch
                }

                // Используем endpoint get-my-groups с типом joined_groups для получения групп пользователя
                val response = api.getMyGroups(
                    accessToken = accessToken,
                    type = "joined_groups", // Получаем группы, в которых состоит пользователь
                    userId = UserSession.userId,
                    limit = 100,
                    offset = 0
                )

                // Используем локальную переменную для smart cast
                val groupsList = response.groups
                if (response.apiStatus == 200 && groupsList != null) {
                    _myGroups.value = groupsList
                    Log.d(TAG, "Loaded ${groupsList.size} groups")
                } else {
                    _errorMessage.value = response.errorMessage ?: "Помилка завантаження груп"
                    Log.e(TAG, "Error loading groups: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Помилка: ${e.message}"
                Log.e(TAG, "Exception loading groups", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUsername(newUsername: String) {
        _username.value = newUsername
        UserSession.username = newUsername
    }

    fun updateAvatar(newAvatar: String) {
        _avatar.value = newAvatar
        UserSession.avatar = newAvatar
    }

    fun clearError() {
        _errorMessage.value = null
    }

    

    fun checkUpdates(force: Boolean = false) {
        viewModelScope.launch {
            AppUpdateManager.checkForUpdates(force = force)
        }
    }

    fun snoozeUpdatePrompt() {
        AppUpdateManager.snoozePrompt(hours = 12)
    }
    fun clearSuccess() {
        _successMessage.value = null
    }
}
