package com.worldmates.messenger.ui.stories

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.data.repository.StoryRepository
import com.worldmates.messenger.network.StoryReactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для Stories
 * Керує логікою перегляду, створення та взаємодії з stories
 */
class StoryViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "StoryViewModel"
    private val repository = StoryRepository(application)

    // Список stories
    val stories: StateFlow<List<Story>> = repository.stories

    // Поточна story
    val currentStory: StateFlow<Story?> = repository.currentStory

    // Коментарі
    val comments: StateFlow<List<StoryComment>> = repository.comments

    // Перегляди
    val viewers: StateFlow<List<StoryViewer>> = repository.viewers

    // Стан завантаження
    val isLoading: StateFlow<Boolean> = repository.isLoading

    // Помилки
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Успішні операції
    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success

    // Обмеження користувача
    private val _userLimits = MutableStateFlow(StoryLimits.forUser(false))
    val userLimits: StateFlow<StoryLimits> = _userLimits

    init {
        updateUserLimits()
        loadStories()
    }

    /**
     * Оновити обмеження користувача
     */
    private fun updateUserLimits() {
        val isPro = UserSession.isPro == 1
        _userLimits.value = StoryLimits.forUser(isPro)
        Log.d(TAG, "User limits updated: isPro=$isPro, maxStories=${_userLimits.value.maxStories}")
    }

    /**
     * Завантажити список stories
     */
    fun loadStories() {
        viewModelScope.launch {
            try {
                repository.fetchStories().onSuccess {
                    Log.d(TAG, "Stories loaded: ${it.size}")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error loading stories", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception loading stories", e)
            }
        }
    }

    /**
     * Створити нову story
     */
    fun createStory(
        mediaUri: Uri,
        fileType: String,
        title: String? = null,
        description: String? = null,
        videoDuration: Int? = null,
        coverUri: Uri? = null
    ) {
        // Перевірка обмежень
        if (!canCreateStory()) {
            _error.value = if (UserSession.isPro == 1) {
                "Ви досягли максимуму ${_userLimits.value.maxStories} stories"
            } else {
                "Безкоштовні користувачі можуть мати макс. ${_userLimits.value.maxStories} stories. Оформіть підписку!"
            }
            return
        }

        // Перевірка тривалості відео
        if (fileType == "video" && videoDuration != null) {
            if (videoDuration > _userLimits.value.maxVideoDuration) {
                _error.value = if (UserSession.isPro == 1) {
                    "Максимальна тривалість відео: ${_userLimits.value.maxVideoDuration} сек"
                } else {
                    "Безкоштовні користувачі можуть завантажити відео до ${_userLimits.value.maxVideoDuration} сек. Оформіть підписку!"
                }
                return
            }
        }

        viewModelScope.launch {
            try {
                repository.createStory(
                    mediaUri = mediaUri,
                    fileType = fileType,
                    title = title,
                    description = description,
                    videoDuration = videoDuration,
                    coverUri = coverUri
                ).onSuccess {
                    _success.value = "Story створена успішно!"
                    Log.d(TAG, "Story created: ${it.storyId}")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error creating story", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception creating story", e)
            }
        }
    }

    /**
     * Перевірити, чи може користувач створити ще одну story
     */
    fun canCreateStory(): Boolean {
        val activeStories = stories.value.count { it.userId == UserSession.userId && !it.isExpired() }
        return activeStories < _userLimits.value.maxStories
    }

    /**
     * Отримати кількість активних stories користувача
     */
    fun getActiveStoriesCount(): Int {
        return stories.value.count { it.userId == UserSession.userId && !it.isExpired() }
    }

    /**
     * Отримати stories конкретного користувача
     */
    fun loadUserStories(userId: Long) {
        viewModelScope.launch {
            try {
                repository.getUserStories(userId).onSuccess { stories ->
                    Log.d(TAG, "User stories loaded: ${stories.size}")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error loading user stories", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception loading user stories", e)
            }
        }
    }

    /**
     * Завантажити story за ID
     */
    fun loadStoryById(storyId: Long) {
        viewModelScope.launch {
            try {
                repository.getStoryById(storyId).onSuccess { story ->
                    Log.d(TAG, "Story loaded: ${story.id}")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error loading story", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception loading story", e)
            }
        }
    }

    /**
     * Встановити поточну story
     */
    fun setCurrentStory(story: Story) {
        repository.setCurrentStory(story)
        // Не загружаем комментарии автоматически - только когда пользователь откроет комментарии
        // loadComments(story.id)
    }

    /**
     * Видалити story
     */
    fun deleteStory(storyId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteStory(storyId).onSuccess {
                    _success.value = "Story видалена"
                    Log.d(TAG, "Story deleted: $storyId")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error deleting story", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception deleting story", e)
            }
        }
    }

    /**
     * Завантажити перегляди story
     */
    fun loadStoryViews(storyId: Long, offset: Int = 0) {
        viewModelScope.launch {
            try {
                repository.getStoryViews(storyId, offset = offset).onSuccess {
                    Log.d(TAG, "Story views loaded: ${it.size}")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error loading views", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception loading views", e)
            }
        }
    }

    /**
     * Додати реакцію на story
     */
    fun reactToStory(storyId: Long, reactionType: StoryReactionType) {
        viewModelScope.launch {
            try {
                repository.reactToStory(storyId, reactionType).onSuccess {
                    Log.d(TAG, "Reaction added: ${reactionType.value}")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error adding reaction", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception adding reaction", e)
            }
        }
    }

    /**
     * Приглушити stories користувача
     */
    fun muteStory(userId: Long) {
        viewModelScope.launch {
            try {
                repository.muteStory(userId).onSuccess {
                    _success.value = "Stories приглушені"
                    Log.d(TAG, "Story muted for user: $userId")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error muting story", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception muting story", e)
            }
        }
    }

    /**
     * Створити коментар
     */
    fun createComment(storyId: Long, text: String) {
        viewModelScope.launch {
            try {
                repository.createComment(storyId, text).onSuccess {
                    Log.d(TAG, "Comment created: ${it.id}")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error creating comment", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception creating comment", e)
            }
        }
    }

    /**
     * Завантажити коментарі
     */
    fun loadComments(storyId: Long, offset: Int = 0) {
        viewModelScope.launch {
            try {
                repository.getComments(storyId, offset = offset).onSuccess {
                    Log.d(TAG, "Comments loaded: ${it.size}")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error loading comments", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception loading comments", e)
            }
        }
    }

    /**
     * Видалити коментар
     */
    fun deleteComment(commentId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteComment(commentId).onSuccess {
                    _success.value = "Коментар видалено"
                    Log.d(TAG, "Comment deleted: $commentId")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error deleting comment", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception deleting comment", e)
            }
        }
    }

    /**
     * Очистити повідомлення про помилку
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Очистити повідомлення про успіх
     */
    fun clearSuccess() {
        _success.value = null
    }

    /**
     * Очистити коментарі
     */
    fun clearComments() {
        repository.clearComments()
    }

    /**
     * Очистити перегляди
     */
    fun clearViewers() {
        repository.clearViewers()
    }

    // ==================== CHANNEL STORIES ====================

    /** Stories каналів */
    val channelStories: StateFlow<List<Story>> = repository.channelStories

    /** Завантажити stories підписаних каналів */
    fun loadChannelStories() {
        viewModelScope.launch {
            try {
                repository.fetchSubscribedChannelStories().onSuccess {
                    Log.d(TAG, "Channel stories loaded: ${it.size}")
                }.onFailure { e ->
                    Log.e(TAG, "Error loading channel stories", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading channel stories", e)
            }
        }
    }

    /** Створити story каналу */
    fun createChannelStory(
        channelId: Long,
        mediaUri: Uri,
        fileType: String,
        title: String? = null,
        description: String? = null
    ) {
        viewModelScope.launch {
            try {
                repository.createChannelStory(
                    channelId = channelId,
                    mediaUri = mediaUri,
                    fileType = fileType,
                    title = title,
                    description = description
                ).onSuccess {
                    _success.value = "Story каналу створена!"
                    Log.d(TAG, "Channel story created: ${it.storyId}")
                }.onFailure { e ->
                    _error.value = e.message
                    Log.e(TAG, "Error creating channel story", e)
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception creating channel story", e)
            }
        }
    }

    /** Видалити story каналу */
    fun deleteChannelStory(storyId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteChannelStory(storyId).onSuccess {
                    _success.value = "Story каналу видалена"
                }.onFailure { e ->
                    _error.value = e.message
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}