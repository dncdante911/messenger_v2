package com.worldmates.messenger.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.RetrofitClient
import com.worldmates.messenger.network.StoriesApiService
import com.worldmates.messenger.network.StoryReactionType
import com.worldmates.messenger.utils.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import java.io.File

/**
 * Репозиторій для роботи зі Stories
 * Обробляє всі операції зі stories: створення, перегляд, коментування, реакції
 */
class StoryRepository(private val context: Context) {

    private val TAG = "StoryRepository"

    // PHP API for secondary story endpoints (delete, views, reactions, comments, channels)
    private val storiesApi: StoriesApiService by lazy {
        RetrofitClient.retrofit.create(StoriesApiService::class.java)
    }

    // Node.js API for primary story endpoints (create, get) — replaces broken PHP
    private val nodeStoriesApi by lazy { NodeRetrofitClient.storiesApi }

    // Поточний список stories
    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories

    // Поточна активна story
    private val _currentStory = MutableStateFlow<Story?>(null)
    val currentStory: StateFlow<Story?> = _currentStory

    // Коментарі поточної story
    private val _comments = MutableStateFlow<List<StoryComment>>(emptyList())
    val comments: StateFlow<List<StoryComment>> = _comments

    // Перегляди поточної story
    private val _viewers = MutableStateFlow<List<StoryViewer>>(emptyList())
    val viewers: StateFlow<List<StoryViewer>> = _viewers

    // Стан завантаження
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // ==================== STORIES BASIC ====================

    /**
     * Створити нову story
     * @param mediaUri URI медіа файлу (фото або відео)
     * @param fileType Тип файлу: "image" або "video"
     * @param title Заголовок story (опціонально)
     * @param description Опис story (опціонально)
     * @param videoDuration Тривалість відео в секундах (для відео)
     * @param coverUri URI обкладинки для відео (опціонально)
     */
    suspend fun createStory(
        mediaUri: Uri,
        fileType: String,
        title: String? = null,
        description: String? = null,
        videoDuration: Int? = null,
        coverUri: Uri? = null
    ): Result<CreateStoryResponse> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            _isLoading.value = true

            // Перевірка обмежень підписки
            val limits = StoryLimits.forUser(UserSession.isPro == 1)

            // Якщо це відео, перевіряємо тривалість
            if (fileType == "video" && videoDuration != null) {
                if (videoDuration > limits.maxVideoDuration) {
                    _isLoading.value = false
                    return Result.failure(Exception(
                        if (UserSession.isPro == 1) {
                            "Тривалість відео не може перевищувати ${limits.maxVideoDuration} секунд"
                        } else {
                            "Для безкоштовних користувачів макс. ${limits.maxVideoDuration} сек. Оформіть підписку для відео до 45 сек."
                        }
                    ))
                }
            }

            // Конвертуємо файл
            val mediaFile = FileUtils.getFileFromUri(context, mediaUri)
                ?: return Result.failure(Exception("Не вдалося прочитати файл"))

            // Стискаємо зображення якщо це фото (не відео)
            // Фото: макс 15MB, якість 90% (висока)
            // Відео: без стиснення на клієнті (ffmpeg на сервері)
            val finalMediaFile = if (fileType == "image") {
                FileUtils.compressImageIfNeeded(context, mediaFile, maxSizeKB = 15360, quality = 90)
            } else {
                // Для відео перевіряємо розмір
                val videoSizeKB = mediaFile.length() / 1024
                val videoSizeMB = videoSizeKB / 1024
                Log.d(TAG, "Розмір відео: ${videoSizeMB}MB")

                // Максимум 500MB для відео
                if (videoSizeMB > 500) {
                    _isLoading.value = false
                    return Result.failure(Exception("Розмір відео не може перевищувати 500MB. Ваше відео: ${videoSizeMB}MB"))
                }
                mediaFile
            }

            val fileSizeKB = finalMediaFile.length() / 1024
            val fileSizeMB = fileSizeKB / 1024.0
            Log.d(TAG, "Файл для завантаження: ${finalMediaFile.absolutePath}, розмір: ${fileSizeKB}KB (${String.format("%.2f", fileSizeMB)}MB)")

            val requestFile = finalMediaFile.asRequestBody("multipart/form-data".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", finalMediaFile.name, requestFile)

            // Створюємо тіло запиту
            val fileTypeBody = fileType.toRequestBody("text/plain".toMediaTypeOrNull())
            val titleBody = title?.toRequestBody("text/plain".toMediaTypeOrNull())
            val descriptionBody = description?.toRequestBody("text/plain".toMediaTypeOrNull())
            val durationBody = videoDuration?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

            // Якщо є cover для відео
            val coverPart = coverUri?.let { uri ->
                FileUtils.getFileFromUri(context, uri)?.let { coverFile ->
                    val coverRequestFile = coverFile.asRequestBody("multipart/form-data".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("cover", coverFile.name, coverRequestFile)
                }
            }

            val response = nodeStoriesApi.createStory(
                file = filePart,
                fileType = fileTypeBody,
                storyTitle = titleBody,
                storyDescription = descriptionBody,
                videoDuration = durationBody,
                cover = coverPart
            )

            _isLoading.value = false

            if (response.apiStatus == 200) {
                // Оновлюємо список stories
                fetchStories()
                Log.d(TAG, "Story створена: ${response.storyId}")
                Result.success(response)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Помилка створення story"))
            }
        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "Помилка створення story", e)
            Result.failure(e)
        }
    }

    /**
     * Отримати список активних stories
     */
    suspend fun fetchStories(limit: Int = 35): Result<List<Story>> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            _isLoading.value = true

            Log.d(TAG, "Fetching stories with limit=$limit, access_token=${UserSession.accessToken?.take(10)}...")

            val response = nodeStoriesApi.getStories(
                limit = limit
            )

            Log.d(TAG, "API Response: apiStatus=${response.apiStatus}, stories count=${response.stories?.size}, errorMessage=${response.errorMessage}")

            _isLoading.value = false

            if (response.apiStatus == 200 && response.stories != null) {
                Log.d(TAG, "Raw stories from API: ${response.stories.size} stories")
                response.stories.forEachIndexed { index, story ->
                    Log.d(TAG, "Story[$index]: id=${story.id}, userId=${story.userId}, images=${story.images?.size}, videos=${story.videos?.size}")
                }

                // Фільтруємо неактивні та дублікати
                val uniqueActiveStories = response.stories
                    .filter { !it.isExpired() }
                    .distinctBy { it.id }

                _stories.value = uniqueActiveStories
                Log.d(TAG, "Після фільтрації та видалення дублікатів: ${_stories.value.size} унікальних активних stories")
                Result.success(_stories.value)
            } else {
                Log.e(TAG, "API error: ${response.errorMessage ?: "Unknown error"}")
                Result.failure(Exception(response.errorMessage ?: "Помилка завантаження stories"))
            }
        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "Exception while fetching stories: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Отримати story за ID
     */
    suspend fun getStoryById(storyId: Long): Result<Story> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = storiesApi.getStoryById(
                accessToken = UserSession.accessToken!!,
                storyId = storyId
            )

            if (response.apiStatus == 200 && response.story != null) {
                _currentStory.value = response.story
                Result.success(response.story)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Story не знайдена"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка завантаження story", e)
            Result.failure(e)
        }
    }

    /**
     * Отримати stories користувача
     */
    suspend fun getUserStories(userId: Long, limit: Int = 35): Result<List<Story>> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            Log.d(TAG, "getUserStories: userId=$userId, limit=$limit")

            val response = nodeStoriesApi.getUserStories(
                userId = userId,
                limit = limit
            )

            Log.d(TAG, "getUserStories Response: apiStatus=${response.apiStatus}, stories=${response.stories?.size}, error=${response.errorMessage}")

            if (response.apiStatus == 200 && response.stories != null) {
                response.stories.forEachIndexed { index, story ->
                    Log.d(TAG, "UserStory[$index]: id=${story.id}, userId=${story.userId}, images=${story.images?.size}, videos=${story.videos?.size}, mediaItems=${story.mediaItems.size}")
                    story.mediaItems.forEachIndexed { mediaIndex, media ->
                        Log.d(TAG, "  Media[$mediaIndex]: type=${media.type}, filename=${media.filename}")
                    }
                }
                val filtered = response.stories.filter { !it.isExpired() }
                Log.d(TAG, "getUserStories: Повертаємо ${filtered.size} активних stories")

                // FIXED: Оновлюємо список stories для відображення всіх stories користувача
                _stories.value = filtered
                Log.d(TAG, "✅ _stories updated with ${filtered.size} stories")

                // Set first story as current
                _currentStory.value = filtered.firstOrNull()
                if (_currentStory.value != null) {
                    Log.d(TAG, "✅ Current story set: id=${_currentStory.value!!.id}, mediaItems=${_currentStory.value!!.mediaItems.size}")
                } else {
                    Log.w(TAG, "No stories to display")
                }

                Result.success(filtered)
            } else {
                Log.e(TAG, "getUserStories API error: ${response.errorMessage}")
                Result.failure(Exception(response.errorMessage ?: "Помилка завантаження stories користувача"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUserStories Exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Mark story as viewed (fire-and-forget)
     */
    suspend fun markStoryViewed(storyId: Long) {
        try {
            if (UserSession.accessToken == null) return
            nodeStoriesApi.markStoryViewed(storyId)
            Log.d(TAG, "Story $storyId marked as viewed")
        } catch (e: Exception) {
            Log.e(TAG, "markStoryViewed error: ${e.message}")
        }
    }

    /**
     * Встановити поточну story
     */
    fun setCurrentStory(story: Story) {
        _currentStory.value = story
        Log.d(TAG, "Current story set: id=${story.id}, userId=${story.userId}")
    }

    /**
     * Видалити story
     */
    suspend fun deleteStory(storyId: Long): Result<DeleteStoryResponse> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = storiesApi.deleteStory(
                accessToken = UserSession.accessToken!!,
                storyId = storyId
            )

            if (response.apiStatus == 200) {
                // Видаляємо зі списку
                _stories.value = _stories.value.filter { it.id != storyId }
                Log.d(TAG, "Story видалена: $storyId")
                Result.success(response)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Помилка видалення story"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка видалення story", e)
            Result.failure(e)
        }
    }

    // ==================== STORY VIEWS ====================

    /**
     * Отримати перегляди story
     */
    suspend fun getStoryViews(storyId: Long, limit: Int = 20, offset: Int = 0): Result<List<StoryViewer>> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = storiesApi.getStoryViews(
                accessToken = UserSession.accessToken!!,
                storyId = storyId,
                limit = limit,
                offset = offset
            )

            if (response.apiStatus == 200 && response.users != null) {
                if (offset == 0) {
                    _viewers.value = response.users
                } else {
                    _viewers.value = _viewers.value + response.users
                }
                Result.success(response.users)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Помилка завантаження переглядів"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка завантаження переглядів", e)
            Result.failure(e)
        }
    }

    // ==================== STORY REACTIONS ====================

    /**
     * Додати/видалити реакцію на story
     */
    suspend fun reactToStory(storyId: Long, reactionType: StoryReactionType): Result<ReactStoryResponse> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = nodeStoriesApi.reactToStory(
                storyId = storyId,
                reaction = reactionType.value
            )

            if (response.apiStatus == 200) {
                // Оновлюємо story в списку
                _currentStory.value?.let { story ->
                    if (story.id == storyId) {
                        getStoryById(storyId)
                    }
                }
                Log.d(TAG, "Реакція на story: $storyId - ${reactionType.value}")
                Result.success(response)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Помилка додавання реакції"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка додавання реакції", e)
            Result.failure(e)
        }
    }

    // ==================== STORY MUTE ====================

    /**
     * Приглушити stories користувача
     */
    suspend fun muteStory(userId: Long): Result<MuteStoryResponse> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = storiesApi.muteStory(
                accessToken = UserSession.accessToken!!,
                userId = userId
            )

            if (response.apiStatus == 200) {
                // Видаляємо stories цього користувача зі списку
                _stories.value = _stories.value.filter { it.userId != userId }
                Log.d(TAG, "Stories користувача приглушені: $userId")
                Result.success(response)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Помилка приглушення stories"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка приглушення stories", e)
            Result.failure(e)
        }
    }

    // ==================== STORY COMMENTS ====================

    /**
     * Створити коментар до story
     */
    suspend fun createComment(storyId: Long, text: String): Result<StoryComment> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = nodeStoriesApi.createStoryComment(
                storyId = storyId,
                text = text
            )

            if (response.apiStatus == 200 && response.comment != null) {
                // Додаємо коментар на початок списку
                _comments.value = listOf(response.comment) + _comments.value
                // Оновлюємо кількість коментарів у story
                _currentStory.value?.let { story ->
                    if (story.id == storyId) {
                        getStoryById(storyId)
                    }
                }
                Log.d(TAG, "Коментар створено: ${response.comment.id}")
                Result.success(response.comment)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Помилка створення коментаря"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка створення коментаря", e)
            Result.failure(e)
        }
    }

    /**
     * Отримати коментарі до story
     */
    suspend fun getComments(storyId: Long, limit: Int = 20, offset: Int = 0): Result<List<StoryComment>> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = nodeStoriesApi.getStoryComments(
                storyId = storyId,
                limit = limit,
                offset = offset
            )

            if (response.apiStatus == 200 && response.comments != null) {
                if (offset == 0) {
                    _comments.value = response.comments
                } else {
                    _comments.value = _comments.value + response.comments
                }
                Log.d(TAG, "Завантажено ${response.comments.size} коментарів")
                Result.success(response.comments)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Помилка завантаження коментарів"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка завантаження коментарів", e)
            Result.failure(e)
        }
    }

    /**
     * Видалити коментар
     */
    suspend fun deleteComment(commentId: Long): Result<DeleteStoryCommentResponse> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = storiesApi.deleteStoryComment(
                accessToken = UserSession.accessToken!!,
                commentId = commentId
            )

            if (response.apiStatus == 200) {
                // Видаляємо зі списку
                _comments.value = _comments.value.filter { it.id != commentId }
                Log.d(TAG, "Коментар видалено: $commentId")
                Result.success(response)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Помилка видалення коментаря"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка видалення коментаря", e)
            Result.failure(e)
        }
    }

    /**
     * Очистити коментарі
     */
    fun clearComments() {
        _comments.value = emptyList()
    }

    /**
     * Очистити перегляди
     */
    fun clearViewers() {
        _viewers.value = emptyList()
    }

    // ==================== CHANNEL STORIES ====================

    // Stories каналів (окремо від особистих)
    private val _channelStories = MutableStateFlow<List<Story>>(emptyList())
    val channelStories: StateFlow<List<Story>> = _channelStories

    /**
     * Створити story каналу
     */
    suspend fun createChannelStory(
        channelId: Long,
        mediaUri: Uri,
        fileType: String,
        title: String? = null,
        description: String? = null
    ): Result<CreateStoryResponse> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            _isLoading.value = true

            val mediaFile = FileUtils.getFileFromUri(context, mediaUri)
                ?: return Result.failure(Exception("Не вдалося прочитати файл"))

            val finalMediaFile = if (fileType == "image") {
                FileUtils.compressImageIfNeeded(context, mediaFile, maxSizeKB = 15360, quality = 90)
            } else {
                mediaFile
            }

            val requestFile = finalMediaFile.asRequestBody("multipart/form-data".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", finalMediaFile.name, requestFile)

            val channelIdBody = channelId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val fileTypeBody = fileType.toRequestBody("text/plain".toMediaTypeOrNull())
            val titleBody = title?.toRequestBody("text/plain".toMediaTypeOrNull())
            val descriptionBody = description?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = storiesApi.createChannelStory(
                accessToken = UserSession.accessToken!!,
                channelId = channelIdBody,
                file = filePart,
                fileType = fileTypeBody,
                storyTitle = titleBody,
                storyDescription = descriptionBody
            )

            _isLoading.value = false

            if (response.apiStatus == 200) {
                fetchSubscribedChannelStories()
                Log.d(TAG, "Channel story створена: ${response.storyId}")
                Result.success(response)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Помилка створення channel story"))
            }
        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "Помилка створення channel story", e)
            Result.failure(e)
        }
    }

    /**
     * Отримати stories підписаних каналів
     */
    suspend fun fetchSubscribedChannelStories(limit: Int = 30): Result<List<Story>> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = storiesApi.getSubscribedChannelStories(
                accessToken = UserSession.accessToken!!,
                limit = limit
            )

            if (response.apiStatus == 200 && response.stories != null) {
                val active = response.stories.filter { !it.isExpired() }.distinctBy { it.id }
                _channelStories.value = active
                Log.d(TAG, "Channel stories завантажено: ${active.size}")
                Result.success(active)
            } else {
                Log.e(TAG, "Channel stories error: ${response.errorMessage}")
                Result.failure(Exception(response.errorMessage ?: "Помилка завантаження channel stories"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Channel stories exception", e)
            Result.failure(e)
        }
    }

    /**
     * Видалити story каналу
     */
    suspend fun deleteChannelStory(storyId: Long): Result<DeleteStoryResponse> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = storiesApi.deleteChannelStory(
                accessToken = UserSession.accessToken!!,
                storyId = storyId
            )

            if (response.apiStatus == 200) {
                _channelStories.value = _channelStories.value.filter { it.id != storyId }
                Result.success(response)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Помилка видалення channel story"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка видалення channel story", e)
            Result.failure(e)
        }
    }
}