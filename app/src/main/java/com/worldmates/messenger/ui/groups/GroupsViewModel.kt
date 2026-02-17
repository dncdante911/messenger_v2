// ============ GroupsViewModel.kt ============

package com.worldmates.messenger.ui.groups

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.CreateGroupRequest
import com.worldmates.messenger.data.model.Group
import com.worldmates.messenger.data.model.GroupMember
import com.worldmates.messenger.network.RetrofitClient
import com.worldmates.messenger.network.SearchUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType

class GroupsViewModel : ViewModel() {

    private val _groupList = MutableStateFlow<List<Group>>(emptyList())
    val groupList: StateFlow<List<Group>> = _groupList

    private val _selectedGroup = MutableStateFlow<Group?>(null)
    val selectedGroup: StateFlow<Group?> = _selectedGroup

    private val _groupMembers = MutableStateFlow<List<GroupMember>>(emptyList())
    val groupMembers: StateFlow<List<GroupMember>> = _groupMembers

    private val _availableUsers = MutableStateFlow<List<SearchUser>>(emptyList())
    val availableUsers: StateFlow<List<SearchUser>> = _availableUsers

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isCreatingGroup = MutableStateFlow(false)
    val isCreatingGroup: StateFlow<Boolean> = _isCreatingGroup

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        // Загружаем группы сразу при инициализации
        // isLoading = true покажет индикатор загрузки вместо "групи не знайдено"
        _isLoading.value = true
        fetchGroups()
    }

    fun fetchGroups() {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Використовуємо API group_chat.php
                val response = RetrofitClient.apiService.getGroups(
                    accessToken = UserSession.accessToken!!,
                    limit = 100
                )

                if (response.apiStatus == 200 && response.groups != null) {
                    // Отримуємо готові Group об'єкти з нового API
                    _groupList.value = response.groups!!
                    _error.value = null
                    Log.d("GroupsViewModel", "Завантажено ${response.groups!!.size} груп")
                } else {
                    _error.value = response.errorMessage ?: "Помилка завантаження груп"
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e("GroupsViewModel", "Помилка завантаження груп", e)
            }
        }
    }

    fun selectGroup(group: Group) {
        _selectedGroup.value = group
        fetchGroupMembers(group.id)
    }

    fun fetchGroupMembers(groupId: Long) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getGroupMembers(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200 && response.members != null) {
                    _groupMembers.value = response.members!!
                    Log.d("GroupsViewModel", "Завантажено ${response.members!!.size} членів групи")
                } else {
                    _error.value = "Не вдалося завантажити членів групи"
                }
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                Log.e("GroupsViewModel", "Помилка завантаження членів групи", e)
            }
        }
    }

    fun loadAvailableUsers() {
        // Don't load all users at once - wait for search query
        // This improves performance and user experience
        _availableUsers.value = emptyList()
    }

    /**
     * Search users by query (supports Russian names, usernames, first/last names)
     */
    fun searchUsers(query: String) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        // Don't search for empty or very short queries
        if (query.isBlank() || query.length < 2) {
            _availableUsers.value = emptyList()
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.searchUsers(
                    accessToken = UserSession.accessToken!!,
                    query = query,
                    limit = 50
                )

                if (response.apiStatus == 200 && response.users != null) {
                    _availableUsers.value = response.users!!
                    Log.d("GroupsViewModel", "🔍 Знайдено ${response.users!!.size} користувачів для запиту: $query")
                } else {
                    _availableUsers.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("GroupsViewModel", "❌ Помилка пошуку користувачів", e)
                _availableUsers.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createGroup(
        name: String,
        description: String,
        memberIds: List<Long>,
        isPrivate: Boolean,
        avatarUri: android.net.Uri? = null,
        context: android.content.Context? = null,
        onSuccess: () -> Unit
    ) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        _isCreatingGroup.value = true

        viewModelScope.launch {
            try {
                // Group Chat API: uses 'group_name' and 'parts' parameters
                // Note: description is not supported in group-chat API (only in social groups)

                // Додаємо поточного користувача до parts якщо список порожній
                val allMemberIds = if (memberIds.isEmpty()) {
                    listOf(UserSession.userId!!)
                } else {
                    (memberIds + UserSession.userId!!).distinct()
                }

                val partsString = allMemberIds.joinToString(",")
                Log.d("GroupsViewModel", "Створення групи: name=$name, parts=$partsString")

                val response = RetrofitClient.apiService.createGroup(
                    accessToken = UserSession.accessToken!!,
                    name = name,
                    memberIds = partsString
                )

                Log.d("GroupsViewModel", "Response: $response")

                if (response == null) {
                    _error.value = "Сервер повернув порожню відповідь"
                    Log.e("GroupsViewModel", "Response is null!")
                } else if (response.apiStatus == 200) {
                    _error.value = null

                    // Отримуємо group_id з відповіді для завантаження аватара
                    val groupId = response.groupId ?: response.group?.id

                    // 📸 Завантажуємо аватар якщо він був вибраний
                    if (avatarUri != null && context != null && groupId != null) {
                        Log.d("GroupsViewModel", "📸 Завантажуємо аватар для групи $groupId")
                        uploadGroupAvatar(groupId, avatarUri, context)
                    }

                    fetchGroups()
                    onSuccess()
                    Log.d("GroupsViewModel", "Група створена успішно, id=$groupId")
                } else {
                    _error.value = response.errorMessage ?: "Не вдалося створити групу (код: ${response.apiStatus})"
                    Log.e("GroupsViewModel", "API error: ${response.errorMessage}, code: ${response.errorCode}")
                }

                _isCreatingGroup.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isCreatingGroup.value = false
                Log.e("GroupsViewModel", "Помилка створення групи", e)
            }
        }
    }

    fun updateGroup(
        groupId: Long,
        name: String,
        onSuccess: () -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Group Chat API: only group_name can be updated
                val response = RetrofitClient.apiService.updateGroup(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    name = name
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    fetchGroups()
                    Log.d("GroupsViewModel", "Група оновлена успішно")
                    onSuccess()
                } else {
                    _error.value = response.errorMessage ?: "Не вдалося оновити групу"
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e("GroupsViewModel", "Помилка оновлення групи", e)
            }
        }
    }

    fun deleteGroup(
        groupId: Long,
        onSuccess: () -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteGroup(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    _selectedGroup.value = null
                    fetchGroups()
                    Log.d("GroupsViewModel", "Група видалена успішно")
                    onSuccess()
                } else {
                    _error.value = response.errorMessage ?: "Не вдалося видалити групу"
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e("GroupsViewModel", "Помилка видалення групи", e)
            }
        }
    }

    fun addGroupMember(groupId: Long, userId: Long) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        viewModelScope.launch {
            try {
                // Group Chat API: uses 'parts' parameter (comma-separated user IDs)
                val response = RetrofitClient.apiService.addGroupMember(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    userIds = userId.toString()
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    fetchGroupMembers(groupId)
                    Log.d("GroupsViewModel", "Члена групи додано успішно")
                } else {
                    _error.value = response.errorMessage ?: "Не вдалося додати члена групи"
                }
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                Log.e("GroupsViewModel", "Помилка додавання члена групи", e)
            }
        }
    }

    fun removeGroupMember(groupId: Long, userId: Long) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        viewModelScope.launch {
            try {
                // Group Chat API: uses 'parts' parameter (comma-separated user IDs)
                val response = RetrofitClient.apiService.removeGroupMember(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    userIds = userId.toString()
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    fetchGroupMembers(groupId)
                    Log.d("GroupsViewModel", "Члена групи видалено успішно")
                } else {
                    _error.value = response.errorMessage ?: "Не вдалося видалити члена групи"
                }
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                Log.e("GroupsViewModel", "Помилка видалення члена групи", e)
            }
        }
    }

    fun setGroupRole(groupId: Long, userId: Long, role: String) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.setGroupMemberRole(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    userId = userId,
                    role = role
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    fetchGroupMembers(groupId)
                    Log.d("GroupsViewModel", "Роль користувача змінена успішно")
                } else {
                    _error.value = response.errorMessage ?: "Не вдалося змінити роль користувача"
                }
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                Log.e("GroupsViewModel", "Помилка зміни ролі користувача", e)
            }
        }
    }

    fun leaveGroup(groupId: Long) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.leaveGroup(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    _selectedGroup.value = null
                    fetchGroups()
                    Log.d("GroupsViewModel", "Групу вийшли успішно")
                } else {
                    _error.value = response.errorMessage ?: "Не вдалося вийти з групи"
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e("GroupsViewModel", "Помилка виходу з групи", e)
            }
        }
    }

    /**
     * 📸 Завантаження аватара групи через Uri (використовується з EditGroupDialog)
     */
    fun uploadGroupAvatar(groupId: Long, imageUri: android.net.Uri, context: android.content.Context) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Конвертуємо Uri в File
                val file = java.io.File(context.cacheDir, "group_avatar_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Використовуємо спеціалізований endpoint upload_group_avatar.php
                val accessTokenBody = okhttp3.RequestBody.create(
                    "text/plain".toMediaType(),
                    UserSession.accessToken!!
                )
                val groupIdBody = okhttp3.RequestBody.create(
                    "text/plain".toMediaType(),
                    groupId.toString()
                )
                val requestFile = okhttp3.RequestBody.create(
                    "image/*".toMediaType(),
                    file
                )
                val avatarPart = okhttp3.MultipartBody.Part.createFormData(
                    "avatar",
                    file.name,
                    requestFile
                )

                // Викликаємо правильний endpoint
                val response = RetrofitClient.apiService.uploadGroupAvatarDedicated(
                    accessToken = accessTokenBody,
                    groupId = groupIdBody,
                    avatar = avatarPart
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    fetchGroups() // Оновлюємо список груп
                    // Також оновлюємо деталі групи
                    fetchGroupDetails(groupId)
                    Log.d("GroupsViewModel", "📸 Аватарка групи $groupId успішно завантажена: ${response.avatarUrl}")
                } else {
                    _error.value = response.message ?: response.errorMessage ?: "Не вдалося завантажити аватарку"
                    Log.e("GroupsViewModel", "❌ Помилка завантаження аватарки: ${response.message}")
                }

                // Видаляємо тимчасовий файл
                file.delete()

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e("GroupsViewModel", "❌ Помилка завантаження аватарки", e)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    // ==================== 📌 PINNED MESSAGES ====================

    /**
     * 📌 Закрепить сообщение в группе
     */
    fun pinMessage(
        groupId: Long,
        messageId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.pinGroupMessage(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    messageId = messageId
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    // Обновляем данные группы чтобы получить закрепленное сообщение
                    fetchGroupDetails(groupId)
                    onSuccess()
                    Log.d("GroupsViewModel", "📌 Message $messageId pinned in group $groupId")
                } else {
                    val errorMsg = response.message ?: "Не вдалося закріпити повідомлення"
                    _error.value = errorMsg
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ Failed to pin message: ${response.message}")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error pinning message", e)
            }
        }
    }

    /**
     * 📌 Открепить сообщение в группе
     */
    fun unpinMessage(
        groupId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.unpinGroupMessage(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    // Обновляем данные группы
                    fetchGroupDetails(groupId)
                    onSuccess()
                    Log.d("GroupsViewModel", "📌 Message unpinned in group $groupId")
                } else {
                    val errorMsg = response.message ?: "Не вдалося відкріпити повідомлення"
                    _error.value = errorMsg
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ Failed to unpin message: ${response.message}")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error unpinning message", e)
            }
        }
    }

    /**
     * Обновить детали группы (для получения pinnedMessage)
     */
    fun fetchGroupDetails(groupId: Long) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getGroupDetails(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200 && response.group != null) {
                    _selectedGroup.value = response.group
                    // Также обновляем в списке групп
                    _groupList.value = _groupList.value.map {
                        if (it.id == groupId) response.group!! else it
                    }
                }
            } catch (e: Exception) {
                Log.e("GroupsViewModel", "❌ Error fetching group details", e)
            }
        }
    }

    /**
     * 📸 Завантаження аватара групи через File (використовується з галереї)
     */
    fun uploadGroupAvatar(
        groupId: Long,
        imageFile: java.io.File,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Створюємо RequestBody для файла
                val requestFile = okhttp3.RequestBody.create(
                    "image/*".toMediaType(),
                    imageFile
                )
                val avatarPart = okhttp3.MultipartBody.Part.createFormData(
                    "avatar",
                    imageFile.name,
                    requestFile
                )

                // Створюємо RequestBody для параметрів
                val accessTokenBody = okhttp3.RequestBody.create(
                    "text/plain".toMediaType(),
                    UserSession.accessToken!!
                )
                val groupIdBody = okhttp3.RequestBody.create(
                    "text/plain".toMediaType(),
                    groupId.toString()
                )

                // Використовуємо спеціалізований endpoint
                val response = RetrofitClient.apiService.uploadGroupAvatarDedicated(
                    accessToken = accessTokenBody,
                    groupId = groupIdBody,
                    avatar = avatarPart
                )

                if (response.apiStatus == 200 && response.avatarUrl != null) {
                    _error.value = null
                    // Оновлюємо деталі групи та список груп
                    fetchGroupDetails(groupId)
                    fetchGroups()
                    onSuccess(response.avatarUrl)
                    Log.d("GroupsViewModel", "📸 Аватарка групи $groupId завантажена: ${response.avatarUrl}")
                } else {
                    val errorMsg = response.message ?: response.errorMessage ?: "Не вдалося завантажити аватар"
                    _error.value = errorMsg
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ Помилка завантаження аватара: ${response.message}")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Помилка завантаження аватара", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 🔲 Генерація QR коду для групи
     */
    fun generateGroupQr(
        groupId: Long,
        onSuccess: (String, String) -> Unit = { _, _ -> }, // qrCode, joinUrl
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.generateGroupQr(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200 && response.qrCode != null && response.joinUrl != null) {
                    _error.value = null
                    onSuccess(response.qrCode, response.joinUrl)
                    Log.d("GroupsViewModel", "🔲 Group $groupId QR generated: ${response.qrCode}")
                } else {
                    val errorMsg = response.message ?: "Не вдалося згенерувати QR код"
                    _error.value = errorMsg
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ Failed to generate QR: ${response.message}")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error generating QR", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 🔲 Приєднання до групи за QR кодом
     */
    fun joinGroupByQr(
        qrCode: String,
        onSuccess: (Group) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.joinGroupByQr(
                    accessToken = UserSession.accessToken!!,
                    qrCode = qrCode
                )

                if (response.apiStatus == 200 && response.group != null) {
                    _error.value = null
                    // Оновлюємо список груп
                    fetchGroups()
                    onSuccess(response.group)
                    Log.d("GroupsViewModel", "🔲 Joined group ${response.group.id} via QR: $qrCode")
                } else {
                    val errorMsg = response.message ?: "Не вдалося приєднатися до групи"
                    _error.value = errorMsg
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ Failed to join by QR: ${response.message}")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error joining by QR", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 📝 Сохранение настроек форматирования группы
     */
    fun saveFormattingPermissions(
        groupId: Long,
        permissions: GroupFormattingPermissions,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                // Сохраняем в SharedPreferences локально
                val prefs = com.worldmates.messenger.WMApplication.instance
                    .getSharedPreferences("group_formatting_prefs", android.content.Context.MODE_PRIVATE)

                val json = com.google.gson.Gson().toJson(permissions)
                prefs.edit().putString("formatting_$groupId", json).apply()

                Log.d("GroupsViewModel", "💾 Saved formatting permissions for group $groupId")
                onSuccess()

                // TODO: В будущем добавить API вызов для сохранения на backend
                // val response = RetrofitClient.apiService.updateGroupFormattingPermissions(...)
            } catch (e: Exception) {
                val errorMsg = "Помилка збереження: ${e.localizedMessage}"
                Log.e("GroupsViewModel", "❌ Error saving formatting permissions", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * 📝 Загрузка настроек форматирования группы
     */
    fun loadFormattingPermissions(groupId: Long): GroupFormattingPermissions {
        return try {
            val prefs = com.worldmates.messenger.WMApplication.instance
                .getSharedPreferences("group_formatting_prefs", android.content.Context.MODE_PRIVATE)

            val json = prefs.getString("formatting_$groupId", null)
            if (json != null) {
                com.google.gson.Gson().fromJson(json, GroupFormattingPermissions::class.java)
            } else {
                GroupFormattingPermissions() // Default settings
            }
        } catch (e: Exception) {
            Log.e("GroupsViewModel", "❌ Error loading formatting permissions", e)
            GroupFormattingPermissions() // Default on error
        }
    }

    /**
     * 🔔 Сохранение настроек уведомлений группы (REAL API + local)
     */
    fun saveNotificationSettings(
        groupId: Long,
        enabled: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        if (UserSession.accessToken == null) return

        viewModelScope.launch {
            try {
                // Зберігаємо локально
                val prefs = com.worldmates.messenger.WMApplication.instance
                    .getSharedPreferences("group_notification_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("notifications_$groupId", enabled).apply()

                // Виклик API для mute/unmute
                val response = if (enabled) {
                    RetrofitClient.apiService.unmuteGroup(
                        accessToken = UserSession.accessToken!!,
                        groupId = groupId
                    )
                } else {
                    RetrofitClient.apiService.muteGroup(
                        accessToken = UserSession.accessToken!!,
                        groupId = groupId
                    )
                }

                if (response.apiStatus == 200) {
                    Log.d("GroupsViewModel", "🔔 Notification setting saved for group $groupId: $enabled via API")
                    onSuccess()
                } else {
                    Log.e("GroupsViewModel", "❌ API error saving notification settings: ${response.errorMessage}")
                    onSuccess() // Локально зберегли
                }
            } catch (e: Exception) {
                Log.e("GroupsViewModel", "❌ Error saving notification settings", e)
                onSuccess() // Локально зберегли
            }
        }
    }

    /**
     * 🔔 Загрузка настроек уведомлений группы
     */
    fun loadNotificationSettings(groupId: Long): Boolean {
        return try {
            val prefs = com.worldmates.messenger.WMApplication.instance
                .getSharedPreferences("group_notification_prefs", android.content.Context.MODE_PRIVATE)

            prefs.getBoolean("notifications_$groupId", true) // По умолчанию включены
        } catch (e: Exception) {
            Log.e("GroupsViewModel", "❌ Error loading notification settings", e)
            true // Default on error
        }
    }

    // ==================== 📊 GROUP STATISTICS ====================

    private val _groupStatistics = MutableStateFlow<com.worldmates.messenger.data.model.GroupStatistics?>(null)
    val groupStatistics: StateFlow<com.worldmates.messenger.data.model.GroupStatistics?> = _groupStatistics

    /**
     * 📊 Завантаження статистики групи
     */
    fun loadGroupStatistics(groupId: Long) {
        if (UserSession.accessToken == null) return

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getGroupStatistics(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200 && response.statistics != null) {
                    val stats = response.statistics
                    _groupStatistics.value = com.worldmates.messenger.data.model.GroupStatistics(
                        groupId = groupId,
                        membersCount = stats.membersCount,
                        messagesCount = stats.messagesCount,
                        messagesToday = stats.messagesToday,
                        messagesThisWeek = stats.messagesToday * 7, // Estimate
                        messagesThisMonth = stats.messagesCount,
                        activeMembers24h = stats.membersCount / 3, // Estimate
                        activeMembersWeek = stats.membersCount / 2,
                        mediaCount = 0,
                        linksCount = 0,
                        newMembersToday = 0,
                        newMembersWeek = stats.newMembersWeek,
                        leftMembersWeek = 0,
                        growthRate = 0f,
                        peakHours = listOf(10, 14, 19, 20, 21),
                        topContributors = stats.topContributors?.map {
                            com.worldmates.messenger.data.model.TopContributor(
                                userId = it.userId,
                                username = it.username,
                                name = it.name,
                                avatar = it.avatar,
                                messagesCount = it.messagesCount
                            )
                        } ?: emptyList()
                    )
                    Log.d("GroupsViewModel", "📊 Statistics loaded for group $groupId")
                }
            } catch (e: Exception) {
                Log.e("GroupsViewModel", "❌ Error loading group statistics", e)
            }
        }
    }

    // ==================== 📝 JOIN REQUESTS (for private groups) ====================

    private val _joinRequests = MutableStateFlow<List<com.worldmates.messenger.data.model.GroupJoinRequest>>(emptyList())
    val joinRequests: StateFlow<List<com.worldmates.messenger.data.model.GroupJoinRequest>> = _joinRequests

    /**
     * 📝 Завантаження запитів на вступ до групи
     */
    fun loadJoinRequests(groupId: Long) {
        if (UserSession.accessToken == null) return

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getGroupJoinRequests(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200 && response.joinRequests != null) {
                    _joinRequests.value = response.joinRequests.map { req ->
                        com.worldmates.messenger.data.model.GroupJoinRequest(
                            id = req.id,
                            groupId = req.groupId,
                            userId = req.userId,
                            username = req.username,
                            userAvatar = req.userAvatar,
                            message = req.message,
                            status = req.status,
                            createdTime = req.createdTime
                        )
                    }
                    Log.d("GroupsViewModel", "📝 Loaded ${response.joinRequests.size} join requests for group $groupId")
                } else {
                    _joinRequests.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("GroupsViewModel", "❌ Error loading join requests", e)
            }
        }
    }

    /**
     * ✅ Прийняти запит на вступ
     */
    fun approveJoinRequest(
        request: com.worldmates.messenger.data.model.GroupJoinRequest,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Не авторизовано")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.approveJoinRequest(
                    accessToken = UserSession.accessToken!!,
                    requestId = request.id
                )

                if (response.apiStatus == 200) {
                    // Видаляємо з локального списку
                    _joinRequests.value = _joinRequests.value.filter { it.id != request.id }
                    // Оновлюємо список учасників
                    fetchGroupMembers(request.groupId)
                    onSuccess()
                    Log.d("GroupsViewModel", "✅ Approved join request from ${request.username}")
                } else {
                    onError(response.errorMessage ?: "Помилка підтвердження запиту")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error approving join request", e)
            }
        }
    }

    /**
     * ❌ Відхилити запит на вступ
     */
    fun rejectJoinRequest(
        request: com.worldmates.messenger.data.model.GroupJoinRequest,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Не авторизовано")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.rejectJoinRequest(
                    accessToken = UserSession.accessToken!!,
                    requestId = request.id
                )

                if (response.apiStatus == 200) {
                    // Видаляємо з локального списку
                    _joinRequests.value = _joinRequests.value.filter { it.id != request.id }
                    onSuccess()
                    Log.d("GroupsViewModel", "❌ Rejected join request from ${request.username}")
                } else {
                    onError(response.errorMessage ?: "Помилка відхилення запиту")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error rejecting join request", e)
            }
        }
    }

    // ==================== 📅 SCHEDULED POSTS ====================

    private val _scheduledPosts = MutableStateFlow<List<com.worldmates.messenger.data.model.ScheduledPost>>(emptyList())
    val scheduledPosts: StateFlow<List<com.worldmates.messenger.data.model.ScheduledPost>> = _scheduledPosts

    /**
     * 📅 Завантаження запланованих постів (REAL API)
     */
    fun loadScheduledPosts(groupId: Long) {
        if (UserSession.accessToken == null) return

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getScheduledPosts(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200 && response.scheduledPosts != null) {
                    _scheduledPosts.value = response.scheduledPosts.map { post ->
                        com.worldmates.messenger.data.model.ScheduledPost(
                            id = post.id,
                            groupId = post.groupId,
                            authorId = post.authorId,
                            text = post.text,
                            scheduledTime = post.scheduledTime,
                            createdTime = post.createdTime,
                            mediaUrl = post.mediaUrl,
                            status = post.status,
                            repeatType = post.repeatType,
                            isPinned = post.isPinned,
                            notifyMembers = post.notifyMembers
                        )
                    }
                    Log.d("GroupsViewModel", "📅 Loaded ${response.scheduledPosts.size} scheduled posts for group $groupId")
                } else {
                    _scheduledPosts.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("GroupsViewModel", "❌ Error loading scheduled posts", e)
                _scheduledPosts.value = emptyList()
            }
        }
    }

    /**
     * ➕ Створити запланований пост (REAL API)
     */
    fun createScheduledPost(
        groupId: Long,
        text: String,
        scheduledTime: Long,
        mediaUrl: String? = null,
        repeatType: String = "none",
        isPinned: Boolean = false,
        notifyMembers: Boolean = true,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.createScheduledPost(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    text = text,
                    scheduledTime = scheduledTime,
                    mediaUrl = mediaUrl,
                    repeatType = repeatType,
                    isPinned = if (isPinned) 1 else 0,
                    notifyMembers = if (notifyMembers) 1 else 0
                )

                if (response.apiStatus == 200) {
                    // Перезавантажуємо список постів з сервера
                    loadScheduledPosts(groupId)
                    onSuccess()
                    Log.d("GroupsViewModel", "📅 Created scheduled post for group $groupId via API")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося створити запланований пост"
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ API error creating scheduled post: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error creating scheduled post", e)
            }
        }
    }

    /**
     * 🗑️ Видалити запланований пост (REAL API)
     */
    fun deleteScheduledPost(
        post: com.worldmates.messenger.data.model.ScheduledPost,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteScheduledPost(
                    accessToken = UserSession.accessToken!!,
                    groupId = post.groupId ?: 0,
                    postId = post.id
                )

                if (response.apiStatus == 200) {
                    _scheduledPosts.value = _scheduledPosts.value.filter { it.id != post.id }
                    onSuccess()
                    Log.d("GroupsViewModel", "🗑️ Deleted scheduled post ${post.id} via API")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося видалити запланований пост"
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ API error deleting scheduled post: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error deleting scheduled post", e)
            }
        }
    }

    /**
     * 📤 Опублікувати запланований пост зараз (REAL API)
     */
    fun publishScheduledPost(
        post: com.worldmates.messenger.data.model.ScheduledPost,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.publishScheduledPost(
                    accessToken = UserSession.accessToken!!,
                    groupId = post.groupId ?: 0,
                    postId = post.id
                )

                if (response.apiStatus == 200) {
                    _scheduledPosts.value = _scheduledPosts.value.filter { it.id != post.id }
                    onSuccess()
                    Log.d("GroupsViewModel", "📤 Published scheduled post ${post.id} via API")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося опублікувати пост"
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ API error publishing scheduled post: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error publishing scheduled post", e)
            }
        }
    }

    // ==================== ⚙️ GROUP SETTINGS ====================

    /**
     * ⚙️ Оновлення налаштувань групи (REAL API)
     */
    fun updateGroupSettings(
        groupId: Long,
        settings: com.worldmates.messenger.data.model.GroupSettings,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                // Викликаємо API з параметрами з об'єкта settings
                val response = RetrofitClient.apiService.updateGroupSettings(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    slowModeSeconds = settings.slowModeSeconds,
                    historyVisible = if (settings.historyVisibleForNewMembers) 1 else 0,
                    antiSpamEnabled = if (settings.antiSpamEnabled) 1 else 0,
                    maxMessagesPerMinute = settings.maxMessagesPerMinute,
                    allowMedia = if (settings.allowMembersSendMedia) 1 else 0,
                    allowLinks = if (settings.allowMembersSendLinks) 1 else 0,
                    allowStickers = if (settings.allowMembersSendStickers) 1 else 0,
                    allowInvite = if (settings.allowMembersInvite) 1 else 0
                )

                if (response.apiStatus == 200) {
                    // Оновлюємо локально після успішного API виклику
                    val updatedGroup = _selectedGroup.value?.copy(settings = settings)
                    _selectedGroup.value = updatedGroup
                    if (updatedGroup != null) {
                        _groupList.value = _groupList.value.map {
                            if (it.id == groupId) updatedGroup else it
                        }
                    }
                    onSuccess()
                    Log.d("GroupsViewModel", "⚙️ Updated settings for group $groupId via API")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося оновити налаштування"
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ API error updating settings: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error updating group settings", e)
            }
        }
    }

    /**
     * 🔒 Зміна приватності групи (REAL API)
     */
    fun updateGroupPrivacy(
        groupId: Long,
        isPrivate: Boolean,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.updateGroupPrivacy(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    isPrivate = if (isPrivate) 1 else 0
                )

                if (response.apiStatus == 200) {
                    // Оновлюємо деталі групи з сервера
                    fetchGroupDetails(groupId)
                    onSuccess()
                    Log.d("GroupsViewModel", "🔒 Updated privacy for group $groupId to $isPrivate via API")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося змінити приватність"
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ API error updating privacy: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error updating group privacy", e)
            }
        }
    }

    /**
     * 👤 Зміна ролі учасника
     */
    fun updateMemberRole(
        groupId: Long,
        userId: Long,
        newRole: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                // Call the API to update member role
                val response = RetrofitClient.apiService.setGroupMemberRole(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    userId = userId,
                    role = newRole
                )

                if (response.apiStatus == 200) {
                    // Оновлюємо локально після успішного API виклику
                    _groupMembers.value = _groupMembers.value.map { member ->
                        if (member.userId == userId) {
                            member.copy(role = newRole)
                        } else {
                            member
                        }
                    }
                    // Перезавантажуємо учасників для синхронізації
                    fetchGroupMembers(groupId)
                    onSuccess()
                    Log.d("GroupsViewModel", "👤 Updated role for user $userId to $newRole in group $groupId")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося змінити роль"
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ API error updating role: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error updating member role", e)
            }
        }
    }

    // ==================== 📱 SUBGROUPS (Topics) ====================

    private val _subgroups = MutableStateFlow<List<com.worldmates.messenger.data.model.Subgroup>>(emptyList())
    val subgroups: StateFlow<List<com.worldmates.messenger.data.model.Subgroup>> = _subgroups

    /**
     * 📱 Завантаження підгруп (топіків) (REAL API)
     */
    fun loadSubgroups(groupId: Long) {
        if (UserSession.accessToken == null) return

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getSubgroups(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200 && response.subgroups != null) {
                    _subgroups.value = response.subgroups.map { sub ->
                        com.worldmates.messenger.data.model.Subgroup(
                            id = sub.id,
                            parentGroupId = sub.parentGroupId,
                            name = sub.name,
                            description = sub.description,
                            color = sub.color,
                            isPrivate = sub.isPrivate,
                            membersCount = sub.membersCount,
                            messagesCount = sub.messagesCount,
                            createdBy = sub.createdBy,
                            createdTime = sub.createdTime
                        )
                    }
                    Log.d("GroupsViewModel", "📱 Loaded ${response.subgroups.size} subgroups for group $groupId")
                } else {
                    _subgroups.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("GroupsViewModel", "❌ Error loading subgroups", e)
                _subgroups.value = emptyList()
            }
        }
    }

    /**
     * ➕ Створити підгрупу (топік) (REAL API)
     */
    fun createSubgroup(
        groupId: Long,
        name: String,
        description: String?,
        isPrivate: Boolean,
        color: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.createSubgroup(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    name = name,
                    description = description,
                    color = color,
                    isPrivate = if (isPrivate) 1 else 0
                )

                if (response.apiStatus == 200) {
                    // Перезавантажуємо список підгруп з сервера
                    loadSubgroups(groupId)
                    onSuccess()
                    Log.d("GroupsViewModel", "📱 Created subgroup '$name' in group $groupId via API")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося створити підгрупу"
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ API error creating subgroup: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error creating subgroup", e)
            }
        }
    }

    /**
     * 🗑️ Видалити підгрупу (топік) (REAL API)
     */
    fun deleteSubgroup(
        groupId: Long,
        subgroupId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteSubgroup(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    subgroupId = subgroupId
                )

                if (response.apiStatus == 200) {
                    _subgroups.value = _subgroups.value.filter { it.id != subgroupId }
                    onSuccess()
                    Log.d("GroupsViewModel", "🗑️ Deleted subgroup $subgroupId via API")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося видалити підгрупу"
                    onError(errorMsg)
                    Log.e("GroupsViewModel", "❌ API error deleting subgroup: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e("GroupsViewModel", "❌ Error deleting subgroup", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("GroupsViewModel", "ViewModel очищена")
    }
}
