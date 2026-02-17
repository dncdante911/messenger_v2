// ============ ChannelDetailsViewModel.kt ============

package com.worldmates.messenger.ui.channels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChannelDetailsViewModel : ViewModel() {

    // Socket.IO handler for real-time updates
    private var socketHandler: ChannelSocketHandler? = null
    private var socketChannelId: Long = 0

    private val _channel = MutableStateFlow<Channel?>(null)
    val channel: StateFlow<Channel?> = _channel

    private val _posts = MutableStateFlow<List<ChannelPost>>(emptyList())
    val posts: StateFlow<List<ChannelPost>> = _posts

    private val _selectedPost = MutableStateFlow<ChannelPost?>(null)
    val selectedPost: StateFlow<ChannelPost?> = _selectedPost

    private val _comments = MutableStateFlow<List<ChannelComment>>(emptyList())
    val comments: StateFlow<List<ChannelComment>> = _comments

    private val _admins = MutableStateFlow<List<ChannelAdmin>>(emptyList())
    val admins: StateFlow<List<ChannelAdmin>> = _admins

    private val _subscribers = MutableStateFlow<List<ChannelSubscriber>>(emptyList())
    val subscribers: StateFlow<List<ChannelSubscriber>> = _subscribers

    private val _statistics = MutableStateFlow<ChannelStatistics?>(null)
    val statistics: StateFlow<ChannelStatistics?> = _statistics

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingPosts = MutableStateFlow(false)
    val isLoadingPosts: StateFlow<Boolean> = _isLoadingPosts

    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * Завантажує деталі каналу
     */
    fun loadChannelDetails(channelId: Long) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getChannelDetails(
                    accessToken = UserSession.accessToken!!,
                    channelId = channelId
                )

                if (response.apiStatus == 200 && response.channel != null) {
                    _channel.value = response.channel!!
                    _admins.value = response.admins ?: emptyList()
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Деталі каналу завантажено: ${response.channel!!.name}")

                    // Автоматично завантажуємо пости
                    loadChannelPosts(channelId)
                } else {
                    _error.value = response.errorMessage ?: "Помилка завантаження каналу"
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e("ChannelDetailsVM", "Помилка завантаження каналу", e)
            }
        }
    }

    /**
     * Завантажує пости каналу
     */
    fun loadChannelPosts(channelId: Long, beforePostId: Long? = null) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        _isLoadingPosts.value = true

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getChannelPosts(
                    accessToken = UserSession.accessToken!!,
                    channelId = channelId,
                    limit = 20,
                    beforePostId = beforePostId
                )

                if (response.apiStatus == 200 && response.posts != null) {
                    if (beforePostId == null) {
                        // Перше завантаження
                        _posts.value = response.posts!!
                    } else {
                        // Підвантаження старих постів
                        _posts.value = _posts.value + response.posts!!
                    }
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Завантажено ${response.posts!!.size} постів")
                } else {
                    _error.value = response.errorMessage ?: "Помилка завантаження постів"
                }

                _isLoadingPosts.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoadingPosts.value = false
                Log.e("ChannelDetailsVM", "Помилка завантаження постів", e)
            }
        }
    }

    /**
     * Створює новий пост у каналі
     */
    fun createPost(
        channelId: Long,
        text: String,
        media: List<PostMedia>? = null,
        disableComments: Boolean = false,
        notifySubscribers: Boolean = true,
        onSuccess: (ChannelPost) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        if (text.isBlank() && media.isNullOrEmpty()) {
            onError("Введіть текст або додайте медіа")
            return
        }

        viewModelScope.launch {
            try {
                val mediaJson = if (!media.isNullOrEmpty()) {
                    Gson().toJson(media)
                } else null

                val response = RetrofitClient.apiService.createChannelPost(
                    accessToken = UserSession.accessToken!!,
                    channelId = channelId,
                    text = text,
                    mediaUrls = mediaJson,
                    disableComments = if (disableComments) 1 else 0,
                    notifySubscribers = if (notifySubscribers) 1 else 0
                )

                if (response.apiStatus == 200 && response.post != null) {
                    val newPost = response.post!!
                    _posts.value = listOf(newPost) + _posts.value
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Пост створено")
                    onSuccess(newPost)

                    // Broadcast via Socket.IO to other subscribers
                    socketHandler?.emitNewPost(newPost)

                    // Оновлюємо кількість постів у каналі
                    _channel.value?.let { channel ->
                        _channel.value = channel.copy(
                            postsCount = channel.postsCount + 1
                        )
                    }
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка створення поста"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка створення поста", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Оновлює пост
     */
    fun updatePost(
        postId: Long,
        text: String,
        media: List<PostMedia>? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val mediaJson = if (!media.isNullOrEmpty()) {
                    Gson().toJson(media)
                } else null

                val response = RetrofitClient.apiService.updateChannelPost(
                    accessToken = UserSession.accessToken!!,
                    postId = postId,
                    text = text,
                    mediaUrls = mediaJson
                )

                if (response.apiStatus == 200 && response.post != null) {
                    // Оновлюємо пост у списку
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) response.post!! else post
                    }
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Пост оновлено")
                    onSuccess()
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка оновлення поста"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка оновлення поста", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Видаляє пост
     */
    fun deletePost(postId: Long, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteChannelPost(
                    accessToken = UserSession.accessToken!!,
                    postId = postId
                )

                if (response.apiStatus == 200) {
                    // Видаляємо пост зі списку
                    _posts.value = _posts.value.filter { it.id != postId }
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Пост видалено")
                    onSuccess()

                    // Broadcast deletion via Socket.IO
                    socketHandler?.emitPostDeleted(postId)

                    // Оновлюємо кількість постів
                    _channel.value?.let { channel ->
                        _channel.value = channel.copy(
                            postsCount = maxOf(0, channel.postsCount - 1)
                        )
                    }
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка видалення поста"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка видалення поста", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Закріплює/відкріплює пост
     */
    fun togglePinPost(postId: Long, isPinned: Boolean, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = if (isPinned) {
                    RetrofitClient.apiService.unpinChannelPost(
                        accessToken = UserSession.accessToken!!,
                        postId = postId
                    )
                } else {
                    RetrofitClient.apiService.pinChannelPost(
                        accessToken = UserSession.accessToken!!,
                        postId = postId
                    )
                }

                if (response.apiStatus == 200) {
                    // Оновлюємо стан закріплення
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) {
                            post.copy(isPinned = !isPinned)
                        } else {
                            post
                        }
                    }

                    _error.value = null
                    Log.d("ChannelDetailsVM", "Стан закріплення оновлено")
                    onSuccess()
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка зміни закріплення"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка зміни закріплення", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Завантажує коментарі до поста
     */
    fun loadComments(postId: Long) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        _selectedPost.value = _posts.value.find { it.id == postId }
        _isLoadingComments.value = true

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getChannelComments(
                    accessToken = UserSession.accessToken!!,
                    postId = postId,
                    limit = 100
                )

                if (response.apiStatus == 200 && response.comments != null) {
                    _comments.value = response.comments!!
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Завантажено ${response.comments!!.size} коментарів")
                } else {
                    _error.value = response.errorMessage ?: "Помилка завантаження коментарів"
                }

                _isLoadingComments.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoadingComments.value = false
                Log.e("ChannelDetailsVM", "Помилка завантаження коментарів", e)
            }
        }
    }

    /**
     * Додає коментар до поста
     */
    fun addComment(
        postId: Long,
        text: String,
        replyToId: Long? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        if (text.isBlank()) {
            onError("Введіть текст коментаря")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.addChannelComment(
                    accessToken = UserSession.accessToken!!,
                    postId = postId,
                    text = text,
                    replyToId = replyToId
                )

                if (response.apiStatus == 200) {
                    // Перезавантажуємо коментарі
                    loadComments(postId)

                    // Оновлюємо кількість коментарів у пості
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) {
                            post.copy(commentsCount = post.commentsCount + 1)
                        } else {
                            post
                        }
                    }

                    _error.value = null
                    Log.d("ChannelDetailsVM", "Коментар додано")
                    onSuccess()
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка додавання коментаря"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка додавання коментаря", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Видаляє коментар
     */
    fun deleteComment(commentId: Long, postId: Long, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteChannelComment(
                    accessToken = UserSession.accessToken!!,
                    commentId = commentId
                )

                if (response.apiStatus == 200) {
                    // Видаляємо коментар зі списку
                    _comments.value = _comments.value.filter { it.id != commentId }

                    // Оновлюємо кількість коментарів у пості
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) {
                            post.copy(commentsCount = maxOf(0, post.commentsCount - 1))
                        } else {
                            post
                        }
                    }

                    _error.value = null
                    Log.d("ChannelDetailsVM", "Коментар видалено")
                    onSuccess()
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка видалення коментаря"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка видалення коментаря", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Додає реакцію на пост
     */
    fun addPostReaction(postId: Long, emoji: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.addPostReaction(
                    accessToken = UserSession.accessToken!!,
                    postId = postId,
                    emoji = emoji
                )

                if (response.apiStatus == 200) {
                    // Оновлюємо лічильник реакцій локально
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) {
                            post.copy(reactionsCount = post.reactionsCount + 1)
                        } else {
                            post
                        }
                    }

                    _error.value = null
                    Log.d("ChannelDetailsVM", "Реакцію додано")
                    onSuccess()
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка додавання реакції"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка додавання реакції", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Видаляє реакцію з поста
     */
    fun removePostReaction(postId: Long, emoji: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.removePostReaction(
                    accessToken = UserSession.accessToken!!,
                    postId = postId,
                    emoji = emoji
                )

                if (response.apiStatus == 200) {
                    // Оновлюємо лічильник реакцій локально
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) {
                            post.copy(reactionsCount = maxOf(0, post.reactionsCount - 1))
                        } else {
                            post
                        }
                    }

                    _error.value = null
                    Log.d("ChannelDetailsVM", "Реакцію видалено")
                    onSuccess()
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка видалення реакції"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка видалення реакції", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Завантажує статистику каналу (тільки для адмінів)
     */
    fun loadStatistics(channelId: Long) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getChannelStatistics(
                    accessToken = UserSession.accessToken!!,
                    channelId = channelId
                )

                if (response.apiStatus == 200 && response.statistics != null) {
                    _statistics.value = response.statistics
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Статистику завантажено")
                } else {
                    _error.value = response.errorMessage ?: "Помилка завантаження статистики"
                }
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                Log.e("ChannelDetailsVM", "Помилка завантаження статистики", e)
            }
        }
    }

    /**
     * Завантажує список підписників (тільки для адмінів)
     */
    fun loadSubscribers(channelId: Long) {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getChannelSubscribers(
                    accessToken = UserSession.accessToken!!,
                    channelId = channelId,
                    limit = 1000
                )

                if (response.apiStatus == 200 && response.subscribers != null) {
                    _subscribers.value = response.subscribers!!
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Завантажено ${response.subscribers!!.size} підписників")
                } else {
                    _error.value = response.errorMessage ?: "Помилка завантаження підписників"
                }
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                Log.e("ChannelDetailsVM", "Помилка завантаження підписників", e)
            }
        }
    }

    /**
     * Очищує помилку
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Очищає вибраний пост
     */
    fun clearSelectedPost() {
        _selectedPost.value = null
        _comments.value = emptyList()
    }

    /**
     * Додає реакцію на коментар
     */
    fun addCommentReaction(
        commentId: Long,
        emoji: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.addCommentReaction(
                    accessToken = UserSession.accessToken!!,
                    commentId = commentId,
                    reaction = emoji
                )

                if (response.apiStatus == 200) {
                    // Оновлюємо кількість реакцій на коментарі
                    _comments.value = _comments.value.map { comment ->
                        if (comment.id == commentId) {
                            comment.copy(reactionsCount = comment.reactionsCount + 1)
                        } else {
                            comment
                        }
                    }
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Реакцію на коментар додано")
                    onSuccess()
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка додавання реакції"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка додавання реакції на коментар", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Додає адміністратора каналу
     */
    fun addChannelAdmin(
        channelId: Long,
        userSearch: String,
        role: String = "admin",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.addChannelAdmin(
                    accessToken = UserSession.accessToken!!,
                    channelId = channelId,
                    userId = null,
                    userSearch = userSearch,
                    role = role
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Адміністратора додано: $userSearch")
                    onSuccess()
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка додавання адміністратора"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка додавання адміністратора", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Видаляє адміністратора каналу
     */
    fun removeChannelAdmin(
        channelId: Long,
        userId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.removeChannelAdmin(
                    accessToken = UserSession.accessToken!!,
                    channelId = channelId,
                    userId = userId
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Адміністратора видалено")
                    onSuccess()
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка видалення адміністратора"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка видалення адміністратора", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Оновлює інформацію про канал (назва, опис, username)
     */
    fun updateChannel(
        channelId: Long,
        name: String? = null,
        description: String? = null,
        username: String? = null,
        onSuccess: (Channel) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.updateChannel(
                    accessToken = UserSession.accessToken!!,
                    channelId = channelId,
                    name = name,
                    description = description,
                    username = username
                )

                if (response.apiStatus == 200 && response.channel != null) {
                    _channel.value = response.channel
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Канал оновлено: ${response.channel.name}")
                    onSuccess(response.channel)
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка оновлення каналу"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка оновлення каналу", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Оновлює налаштування каналу
     */
    fun updateChannelSettings(
        channelId: Long,
        settings: ChannelSettings,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                // Конвертуємо налаштування в JSON
                val settingsJson = Gson().toJson(settings)

                val response = RetrofitClient.apiService.updateChannelSettings(
                    accessToken = UserSession.accessToken!!,
                    channelId = channelId,
                    settingsJson = settingsJson
                )

                if (response.apiStatus == 200) {
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Налаштування оновлено")
                    onSuccess()
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка оновлення налаштувань"
                    _error.value = errorMsg
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                Log.e("ChannelDetailsVM", "Помилка оновлення налаштувань", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * Зареєструвати перегляд поста
     */
    fun registerPostView(
        postId: Long,
        onSuccess: (Int) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Користувач не авторизований")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.registerPostView(
                    accessToken = UserSession.accessToken!!,
                    postId = postId
                )

                if (response.apiStatus == 200) {
                    Log.d("ChannelDetailsVM", "Перегляд зареєстровано для поста $postId")
                    onSuccess(0) // TODO: можна повертати views_count з response
                } else {
                    val errorMsg = response.errorMessage ?: "Помилка реєстрації перегляду"
                    Log.w("ChannelDetailsVM", "Помилка реєстрації перегляду: $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                Log.e("ChannelDetailsVM", "Помилка реєстрації перегляду", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * 📝 Сохранение настроек форматирования канала
     */
    fun saveFormattingPermissions(
        channelId: Long,
        permissions: com.worldmates.messenger.ui.groups.GroupFormattingPermissions,
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
                    .getSharedPreferences("channel_formatting_prefs", android.content.Context.MODE_PRIVATE)

                val json = com.google.gson.Gson().toJson(permissions)
                prefs.edit().putString("formatting_$channelId", json).apply()

                Log.d("ChannelDetailsVM", "💾 Saved formatting permissions for channel $channelId")
                onSuccess()

                // TODO: В будущем добавить API вызов для сохранения на backend
                // val response = RetrofitClient.apiService.updateChannelFormattingPermissions(...)
            } catch (e: Exception) {
                val errorMsg = "Помилка збереження: ${e.localizedMessage}"
                Log.e("ChannelDetailsVM", "❌ Error saving formatting permissions", e)
                onError(errorMsg)
            }
        }
    }

    /**
     * 📝 Загрузка настроек форматирования канала
     */
    fun loadFormattingPermissions(channelId: Long): com.worldmates.messenger.ui.groups.GroupFormattingPermissions {
        return try {
            val prefs = com.worldmates.messenger.WMApplication.instance
                .getSharedPreferences("channel_formatting_prefs", android.content.Context.MODE_PRIVATE)

            val json = prefs.getString("formatting_$channelId", null)
            if (json != null) {
                com.google.gson.Gson().fromJson(json, com.worldmates.messenger.ui.groups.GroupFormattingPermissions::class.java)
            } else {
                com.worldmates.messenger.ui.groups.GroupFormattingPermissions() // Default settings
            }
        } catch (e: Exception) {
            Log.e("ChannelDetailsVM", "❌ Error loading formatting permissions", e)
            com.worldmates.messenger.ui.groups.GroupFormattingPermissions() // Default on error
        }
    }

    // ==================== SOCKET.IO REAL-TIME ====================

    /**
     * Initialize Socket.IO connection for real-time channel updates.
     * Call this from Activity's onCreate/onResume.
     */
    fun connectSocket(context: Context, channelId: Long) {
        if (socketHandler != null && socketChannelId == channelId) return // already connected

        disconnectSocket()
        socketChannelId = channelId

        socketHandler = ChannelSocketHandler(context).apply {
            onPostCreated = { post ->
                // Don't duplicate own posts (already added via REST response)
                if (post.authorId != UserSession.userId) {
                    viewModelScope.launch {
                        _posts.value = listOf(post) + _posts.value
                        _channel.value?.let { ch ->
                            _channel.value = ch.copy(postsCount = ch.postsCount + 1)
                        }
                        Log.d("ChannelDetailsVM", "RT: New post ${post.id} from user ${post.authorId}")
                    }
                }
            }

            onPostUpdated = { postId, text, _ ->
                viewModelScope.launch {
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId && text != null) {
                            post.copy(text = text, isEdited = true)
                        } else post
                    }
                    Log.d("ChannelDetailsVM", "RT: Post $postId updated")
                }
            }

            onPostDeleted = { postId ->
                viewModelScope.launch {
                    _posts.value = _posts.value.filter { it.id != postId }
                    _channel.value?.let { ch ->
                        _channel.value = ch.copy(postsCount = maxOf(0, ch.postsCount - 1))
                    }
                    Log.d("ChannelDetailsVM", "RT: Post $postId deleted")
                }
            }

            onCommentAdded = { postId, comment ->
                viewModelScope.launch {
                    // If we're viewing this post's comments, add to the list
                    if (_selectedPost.value?.id == postId && comment.userId != UserSession.userId) {
                        _comments.value = _comments.value + comment
                    }
                    // Update comment count
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) post.copy(commentsCount = post.commentsCount + 1) else post
                    }
                    Log.d("ChannelDetailsVM", "RT: New comment on post $postId")
                }
            }
        }
        socketHandler?.connect(channelId)
        Log.d("ChannelDetailsVM", "Socket.IO connected for channel $channelId")
    }

    /**
     * Disconnect Socket.IO. Call from Activity's onPause/onDestroy.
     */
    fun disconnectSocket() {
        socketHandler?.disconnect()
        socketHandler = null
        socketChannelId = 0
    }

    /**
     * Get socket handler for emitting events from Activity/UI
     */
    fun getSocketHandler(): ChannelSocketHandler? = socketHandler

    override fun onCleared() {
        super.onCleared()
        disconnectSocket()
    }
}
