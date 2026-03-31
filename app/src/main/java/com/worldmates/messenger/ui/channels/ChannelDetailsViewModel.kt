// ============ ChannelDetailsViewModel.kt ============

package com.worldmates.messenger.ui.channels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.network.NodeRetrofitClient
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

    private val _bannedMembers = MutableStateFlow<List<com.worldmates.messenger.data.model.ChannelBannedMember>>(emptyList())
    val bannedMembers: StateFlow<List<com.worldmates.messenger.data.model.ChannelBannedMember>> = _bannedMembers

    private val _statistics = MutableStateFlow<ChannelStatistics?>(null)
    val statistics: StateFlow<ChannelStatistics?> = _statistics

    /** Room name of the currently active livestream for this channel, or null if none. */
    private val _activeStreamRoomName = MutableStateFlow<String?>(null)
    val activeStreamRoomName: StateFlow<String?> = _activeStreamRoomName

    /** Past stream recordings for the channel archive. */
    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings

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
                val response = NodeRetrofitClient.channelApi.getChannelDetails(
                    channelId = channelId
                )

                if (response.apiStatus == 200 && response.channel != null) {
                    _channel.value = response.channel!!
                    _admins.value = response.admins ?: emptyList()
                    _error.value = null
                    Log.d("ChannelDetailsVM", "Деталі каналу завантажено: ${response.channel!!.name}")

                    // Seed local formatting permissions cache from server response
                    response.channel!!.formattingPermissions?.let { serverJson ->
                        com.worldmates.messenger.WMApplication.instance
                            .getSharedPreferences("channel_formatting_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putString("formatting_$channelId", serverJson).apply()
                    }

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
                val response = NodeRetrofitClient.channelApi.getChannelPosts(
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

                val response = NodeRetrofitClient.channelApi.createChannelPost(
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

                val response = NodeRetrofitClient.channelApi.updateChannelPost(
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
                val response = NodeRetrofitClient.channelApi.deleteChannelPost(
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
                    NodeRetrofitClient.channelApi.unpinChannelPost(
                        postId = postId
                    )
                } else {
                    NodeRetrofitClient.channelApi.pinChannelPost(
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
                val response = NodeRetrofitClient.channelApi.getChannelComments(
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
                val response = NodeRetrofitClient.channelApi.addChannelComment(
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
                val response = NodeRetrofitClient.channelApi.deleteChannelComment(
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
                val response = NodeRetrofitClient.channelApi.addPostReaction(
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
                val response = NodeRetrofitClient.channelApi.removePostReaction(
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
                val response = NodeRetrofitClient.channelApi.getChannelStatistics(
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
                val response = NodeRetrofitClient.channelApi.getChannelSubscribers(
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
                val response = NodeRetrofitClient.channelApi.addCommentReaction(
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
                val response = NodeRetrofitClient.channelApi.addChannelAdmin(
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
                val response = NodeRetrofitClient.channelApi.removeChannelAdmin(
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
                val response = NodeRetrofitClient.channelApi.updateChannel(
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

                val response = NodeRetrofitClient.channelApi.updateChannelSettings(
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
                val response = NodeRetrofitClient.channelApi.registerPostView(
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
                val prefs = com.worldmates.messenger.WMApplication.instance
                    .getSharedPreferences("channel_formatting_prefs", android.content.Context.MODE_PRIVATE)

                val json = com.google.gson.Gson().toJson(permissions)
                prefs.edit().putString("formatting_$channelId", json).apply()

                // Sync with backend
                val response = com.worldmates.messenger.network.NodeRetrofitClient.channelApi
                    .updateChannelSettings(
                        channelId = channelId,
                        formattingPermissions = json
                    )

                if (response.apiStatus == 200) {
                    Log.d("ChannelDetailsVM", "💾 Saved formatting permissions for channel $channelId (backend + local)")
                } else {
                    Log.w("ChannelDetailsVM", "⚠️ Backend rejected formatting permissions: ${response.errorMessage}")
                }

                onSuccess()
            } catch (e: Exception) {
                Log.e("ChannelDetailsVM", "❌ Error saving formatting permissions", e)
                // Local save already succeeded — report success to UI
                onSuccess()
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

    // ==================== MEMBER MANAGEMENT ====================

    /**
     * Завантажує список забанених учасників
     */
    fun loadBannedMembers(channelId: Long) {
        if (UserSession.accessToken == null) return

        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.channelApi.getChannelBannedMembers(
                    channelId = channelId
                )
                if (response.apiStatus == 200) {
                    _bannedMembers.value = response.bannedMembers ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e("ChannelDetailsVM", "Помилка завантаження банів", e)
            }
        }
    }

    /**
     * Банить учасника каналу
     */
    fun banMember(
        channelId: Long,
        userId: Long,
        reason: String = "",
        durationSeconds: Int = 0,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) { onError("Не авторизовано"); return }

        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.channelApi.banChannelMember(
                    channelId = channelId,
                    userId = userId,
                    reason = reason.ifBlank { null },
                    durationSeconds = durationSeconds
                )
                if (response.apiStatus == 200) {
                    // Remove from active subscribers list
                    _subscribers.value = _subscribers.value.filter { it.userId != userId }
                    loadBannedMembers(channelId)
                    onSuccess()
                } else {
                    onError(response.errorMessage ?: "Помилка бану")
                }
            } catch (e: Exception) {
                onError("Помилка: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Знімає бан з учасника
     */
    fun unbanMember(
        channelId: Long,
        userId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) { onError("Не авторизовано"); return }

        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.channelApi.unbanChannelMember(
                    channelId = channelId,
                    userId = userId
                )
                if (response.apiStatus == 200) {
                    _bannedMembers.value = _bannedMembers.value.filter { it.userId != userId }
                    onSuccess()
                } else {
                    onError(response.errorMessage ?: "Помилка розбану")
                }
            } catch (e: Exception) {
                onError("Помилка: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Виганяє учасника без бану
     */
    fun kickMember(
        channelId: Long,
        userId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) { onError("Не авторизовано"); return }

        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.channelApi.kickChannelMember(
                    channelId = channelId,
                    userId = userId
                )
                if (response.apiStatus == 200) {
                    _subscribers.value = _subscribers.value.filter { it.userId != userId }
                    onSuccess()
                } else {
                    onError(response.errorMessage ?: "Помилка кіку")
                }
            } catch (e: Exception) {
                onError("Помилка: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Видаляє канал (тільки власник)
     */
    fun deleteChannel(
        channelId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) { onError("Не авторизовано"); return }

        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.channelApi.deleteChannel(
                    channelId = channelId
                )
                if (response.apiStatus == 200) {
                    onSuccess()
                } else {
                    onError(response.errorMessage ?: "Помилка видалення каналу")
                }
            } catch (e: Exception) {
                onError("Помилка: ${e.localizedMessage}")
            }
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
                // Don't duplicate posts already in the list (from REST response or previous socket event)
                val alreadyExists = _posts.value.any { it.id == post.id }
                if (!alreadyExists) {
                    viewModelScope.launch {
                        _posts.value = listOf(post) + _posts.value
                        _channel.value?.let { ch ->
                            _channel.value = ch.copy(postsCount = ch.postsCount + 1)
                        }
                        Log.d("ChannelDetailsVM", "RT: New post ${post.id} from user ${post.authorId}")
                    }
                } else {
                    Log.d("ChannelDetailsVM", "RT: Skipping duplicate post ${post.id}")
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

            onStreamStarted = { roomName ->
                viewModelScope.launch { _activeStreamRoomName.value = roomName }
            }
            onStreamEnded = {
                viewModelScope.launch { _activeStreamRoomName.value = null }
            }
        }
        // Check if the channel is already live (set by a previous socket event)
        if (LiveChannelTracker.isLive(channelId)) {
            _activeStreamRoomName.value = ""  // live but roomName not yet known from this session
            // Verify with the server — the tracker may be stale if the stream ended while the
            // socket was disconnected (e.g. user was in ChannelLivestreamActivity)
            viewModelScope.launch {
                try {
                    val lsApi = NodeRetrofitClient.retrofit.create(ChannelLivestreamApi::class.java)
                    val resp  = lsApi.getActiveStream(channelId)
                    if (resp.stream == null) {
                        LiveChannelTracker.markEnded(channelId)
                        _activeStreamRoomName.value = null
                    }
                } catch (_: Exception) { /* stale state is acceptable; socket events will correct it */ }
            }
        }
        socketHandler?.connect(channelId)
        Log.d("ChannelDetailsVM", "Socket.IO connected for channel $channelId")
    }

    // ==================== POLLS ====================

    fun createChannelPoll(
        channelId: Long,
        question: String,
        options: List<String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val optionsJson = com.google.gson.Gson().toJson(options)
                val response = NodeRetrofitClient.channelApi.createChannelPoll(
                    channelId = channelId,
                    question = question,
                    options = optionsJson
                )
                if (response.apiStatus == 200) onSuccess()
                else onError(response.errorMessage ?: "Failed to create poll")
            } catch (e: Exception) {
                onError("Error: ${e.localizedMessage}")
            }
        }
    }

    fun voteOnChannelPoll(pollId: Long, optionId: Long) {
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.channelApi.voteChannelPoll(
                    pollId = pollId,
                    optionIds = optionId.toString()
                )
                if (response.apiStatus == 200 && response.poll != null) {
                    val updatedPoll = response.poll
                    _posts.value = _posts.value.map { post ->
                        if (post.poll?.id == pollId) post.copy(poll = updatedPoll) else post
                    }
                }
            } catch (e: Exception) {
                Log.e("ChannelDetailsVM", "Poll vote failed: ${e.localizedMessage}")
            }
        }
    }

    // ==================== CHANNEL SUB-GROUPS ====================

    private val _subGroups = MutableStateFlow<List<ChannelSubGroup>>(emptyList())
    val subGroups: StateFlow<List<ChannelSubGroup>> = _subGroups

    private val _subGroupsLoading = MutableStateFlow(false)
    val subGroupsLoading: StateFlow<Boolean> = _subGroupsLoading

    private val _subGroupsError = MutableStateFlow<String?>(null)
    val subGroupsError: StateFlow<String?> = _subGroupsError

    private val groupsManager = ChannelGroupsManager(NodeRetrofitClient.channelApi)

    fun loadSubGroups(channelId: Long) {
        viewModelScope.launch {
            _subGroupsLoading.value = true
            groupsManager.loadGroups(channelId)
                .onSuccess { _subGroups.value = it; _subGroupsError.value = null }
                .onFailure { _subGroupsError.value = it.message }
            _subGroupsLoading.value = false
        }
    }

    fun createSubGroup(
        channelId: Long,
        groupName: String,
        description: String? = null,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            groupsManager.createGroup(channelId, groupName, description)
                .onSuccess { resp ->
                    onSuccess(resp.groupId ?: 0L)
                    loadSubGroups(channelId)
                }
                .onFailure { onError(it.message ?: "Error") }
        }
    }

    fun detachSubGroup(channelId: Long, groupId: Long) {
        viewModelScope.launch {
            groupsManager.detachGroup(channelId, groupId)
                .onSuccess { loadSubGroups(channelId) }
                .onFailure { _subGroupsError.value = it.message }
        }
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

    fun loadRecordings(channelId: Long) {
        viewModelScope.launch {
            try {
                val api = NodeRetrofitClient.retrofit.create(RecordingsApi::class.java)
                val resp = api.getChannelRecordings(channelId)
                if (resp.api_status == 200) {
                    _recordings.value = resp.recordings ?: emptyList()
                }
            } catch (e: Exception) {
                Log.w("ChannelDetailsVM", "loadRecordings error", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectSocket()
    }
}

// ── Recordings API + model ─────────────────────────────────────────────────────

data class RecordingItem(
    val id: Int,
    val room_name: String,
    val type: String,
    val uploader_id: Int,
    val filename: String,
    val file_size: Long,
    val duration: Int,
    val mime_type: String,
    val created_at: String
)

data class RecordingsResponse(
    val api_status: Int,
    val recordings: List<RecordingItem>?
)

interface RecordingsApi {
    @GET("api/node/recordings/channel/{channel_id}")
    suspend fun getChannelRecordings(
        @retrofit2.http.Path("channel_id") channelId: Long
    ): RecordingsResponse
}
