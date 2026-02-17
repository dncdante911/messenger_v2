package com.worldmates.messenger.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.worldmates.messenger.data.local.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream

/**
 * 📦 MediaLoadingManager - Менеджер прогресивного завантаження медіа
 *
 * Функції:
 * - Lazy loading: завантажує медіа тільки коли потрібно
 * - Progressive loading: спочатку превью, потім повне медіа
 * - Кешування: зберігає завантажені файли локально
 * - Пріоритизація: важливі медіа завантажуються першими
 */
class MediaLoadingManager(private val context: Context) {

    companion object {
        private const val TAG = "MediaLoadingManager"
        private const val CACHE_DIR_NAME = "media_cache"
        private const val THUMBNAILS_DIR_NAME = "thumbnails"
        private const val MAX_CONCURRENT_DOWNLOADS = 3
    }

    /**
     * Стан завантаження медіа
     */
    enum class LoadingState {
        IDLE,           // Не завантажується
        LOADING_THUMB,  // Завантажується превью
        THUMB_LOADED,   // Превью завантажено
        LOADING_FULL,   // Завантажується повне медіа
        FULL_LOADED,    // Повне медіа завантажено
        ERROR           // Помилка завантаження
    }

    data class MediaLoadProgress(
        val messageId: Long,
        val state: LoadingState,
        val progress: Int, // 0-100
        val thumbnailPath: String? = null,
        val fullMediaPath: String? = null,
        val error: String? = null
    )

    private val apiService = RetrofitClient.apiService
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Кеш директорії
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    }

    private val thumbnailsDir: File by lazy {
        File(cacheDir, THUMBNAILS_DIR_NAME).apply { mkdirs() }
    }

    // Черга завантаження з пріоритетами
    private val downloadQueue = Channel<MediaDownloadTask>(capacity = Channel.UNLIMITED)

    // Активні завантаження
    private val activeDownloads = mutableMapOf<Long, Job>()

    // Стани завантаження для кожного медіа
    private val loadingStates = mutableMapOf<Long, MutableStateFlow<MediaLoadProgress>>()

    init {
        startDownloadWorkers()
    }

    /**
     * Запустити воркери для завантаження
     */
    private fun startDownloadWorkers() {
        repeat(MAX_CONCURRENT_DOWNLOADS) { workerId ->
            scope.launch {
                for (task in downloadQueue) {
                    try {
                        Log.d(TAG, "Worker #$workerId: processing ${task.messageId}")
                        processDownloadTask(task)
                    } catch (e: Exception) {
                        Log.e(TAG, "Worker #$workerId error", e)
                        updateState(task.messageId, LoadingState.ERROR, error = e.message)
                    }
                }
            }
        }
    }

    /**
     * Завантажити превью (thumbnail)
     */
    fun loadThumbnail(messageId: Long, thumbnailUrl: String, priority: Int = 0): StateFlow<MediaLoadProgress> {
        return getOrCreateStateFlow(messageId).apply {
            // Перевіряємо чи вже є в кеші
            val cachedThumb = getCachedThumbnail(messageId)
            if (cachedThumb != null) {
                value = value.copy(
                    state = LoadingState.THUMB_LOADED,
                    progress = 100,
                    thumbnailPath = cachedThumb.absolutePath
                )
                return@apply
            }

            // Додаємо в чергу
            scope.launch {
                downloadQueue.send(
                    MediaDownloadTask(
                        messageId = messageId,
                        url = thumbnailUrl,
                        type = MediaType.THUMBNAIL,
                        priority = priority
                    )
                )
            }

            // Оновлюємо стан
            value = value.copy(state = LoadingState.LOADING_THUMB, progress = 0)
        }
    }

    /**
     * Завантажити повне медіа
     */
    fun loadFullMedia(messageId: Long, mediaUrl: String, priority: Int = 5): StateFlow<MediaLoadProgress> {
        return getOrCreateStateFlow(messageId).apply {
            // Перевіряємо чи вже є в кеші
            val cachedFull = getCachedFullMedia(messageId)
            if (cachedFull != null) {
                value = value.copy(
                    state = LoadingState.FULL_LOADED,
                    progress = 100,
                    fullMediaPath = cachedFull.absolutePath
                )
                return@apply
            }

            // Додаємо в чергу з вищим пріоритетом
            scope.launch {
                downloadQueue.send(
                    MediaDownloadTask(
                        messageId = messageId,
                        url = mediaUrl,
                        type = MediaType.FULL,
                        priority = priority
                    )
                )
            }

            // Оновлюємо стан
            value = value.copy(state = LoadingState.LOADING_FULL, progress = 0)
        }
    }

    /**
     * Обробити задачу завантаження
     */
    private suspend fun processDownloadTask(task: MediaDownloadTask) = withContext(Dispatchers.IO) {
        try {
            when (task.type) {
                MediaType.THUMBNAIL -> downloadThumbnail(task)
                MediaType.FULL -> downloadFullMedia(task)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download ${task.type} for message ${task.messageId}", e)
            updateState(task.messageId, LoadingState.ERROR, error = e.message)
        }
    }

    /**
     * Завантажити превью
     */
    private suspend fun downloadThumbnail(task: MediaDownloadTask) {
        Log.d(TAG, "📥 Downloading thumbnail for message ${task.messageId}")

        updateState(task.messageId, LoadingState.LOADING_THUMB, progress = 10)

        try {
            val response = apiService.getMediaThumbnail(task.url)
            val bytes = response.bytes()

            updateState(task.messageId, LoadingState.LOADING_THUMB, progress = 50)

            // Зберігаємо в кеш
            val thumbnailFile = File(thumbnailsDir, "thumb_${task.messageId}.jpg")
            FileOutputStream(thumbnailFile).use { it.write(bytes) }

            updateState(
                task.messageId,
                LoadingState.THUMB_LOADED,
                progress = 100,
                thumbnailPath = thumbnailFile.absolutePath
            )

            Log.d(TAG, "✅ Thumbnail loaded for message ${task.messageId}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to download thumbnail", e)
            updateState(task.messageId, LoadingState.ERROR, error = e.message)
        }
    }

    /**
     * Завантажити повне медіа
     */
    private suspend fun downloadFullMedia(task: MediaDownloadTask) {
        Log.d(TAG, "📥 Downloading full media for message ${task.messageId}")

        updateState(task.messageId, LoadingState.LOADING_FULL, progress = 10)

        try {
            val response = apiService.getFullMedia(task.url)
            val bytes = response.bytes()

            updateState(task.messageId, LoadingState.LOADING_FULL, progress = 50)

            // Визначаємо розширення файлу з URL
            val extension = task.url.substringAfterLast('.', "jpg")
            val mediaFile = File(cacheDir, "media_${task.messageId}.$extension")

            FileOutputStream(mediaFile).use { it.write(bytes) }

            updateState(
                task.messageId,
                LoadingState.FULL_LOADED,
                progress = 100,
                fullMediaPath = mediaFile.absolutePath
            )

            // Оновлюємо базу даних
            saveMediaPathToDatabase(task.messageId, mediaFile.absolutePath)

            Log.d(TAG, "✅ Full media loaded for message ${task.messageId}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to download full media", e)
            updateState(task.messageId, LoadingState.ERROR, error = e.message)
        }
    }

    /**
     * Оновити стан завантаження
     */
    private fun updateState(
        messageId: Long,
        state: LoadingState,
        progress: Int = 0,
        thumbnailPath: String? = null,
        fullMediaPath: String? = null,
        error: String? = null
    ) {
        val flow = getOrCreateStateFlow(messageId)
        flow.value = flow.value.copy(
            state = state,
            progress = progress,
            thumbnailPath = thumbnailPath ?: flow.value.thumbnailPath,
            fullMediaPath = fullMediaPath ?: flow.value.fullMediaPath,
            error = error
        )
    }

    /**
     * Отримати або створити StateFlow для медіа
     */
    private fun getOrCreateStateFlow(messageId: Long): MutableStateFlow<MediaLoadProgress> {
        return loadingStates.getOrPut(messageId) {
            MutableStateFlow(
                MediaLoadProgress(
                    messageId = messageId,
                    state = LoadingState.IDLE,
                    progress = 0
                )
            )
        }
    }

    /**
     * Отримати кешоване превью
     */
    private fun getCachedThumbnail(messageId: Long): File? {
        val file = File(thumbnailsDir, "thumb_${messageId}.jpg")
        return if (file.exists()) file else null
    }

    /**
     * Отримати кешоване повне медіа
     */
    private fun getCachedFullMedia(messageId: Long): File? {
        // Шукаємо файл з будь-яким розширенням
        val files = cacheDir.listFiles { _, name ->
            name.startsWith("media_$messageId.")
        }
        return files?.firstOrNull()
    }

    /**
     * Зберегти шлях до медіа в базу даних
     */
    private suspend fun saveMediaPathToDatabase(messageId: Long, localPath: String) {
        try {
            val database = AppDatabase.getInstance(context)
            database.messageDao().updateLocalMediaPath(messageId, localPath)
            Log.d(TAG, "💾 Saved media path to database: $localPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save media path to database", e)
        }
    }

    /**
     * Скасувати завантаження
     */
    fun cancelDownload(messageId: Long) {
        activeDownloads[messageId]?.cancel()
        activeDownloads.remove(messageId)
        updateState(messageId, LoadingState.IDLE, progress = 0)
        Log.d(TAG, "❌ Cancelled download for message $messageId")
    }

    /**
     * Очистити кеш превью
     */
    fun clearThumbnailCache() {
        thumbnailsDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "🗑️ Thumbnail cache cleared")
    }

    /**
     * Очистити весь медіа-кеш
     */
    fun clearAllCache() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        thumbnailsDir.mkdirs()
        Log.d(TAG, "🗑️ All media cache cleared")
    }

    /**
     * Отримати розмір кешу в байтах
     */
    fun getCacheSize(): Long {
        return cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    /**
     * Очистити ресурси
     */
    fun cleanup() {
        downloadQueue.close()
        scope.cancel()
    }

    // ==================== ВНУТРІШНІ КЛАСИ ====================

    private data class MediaDownloadTask(
        val messageId: Long,
        val url: String,
        val type: MediaType,
        val priority: Int
    ) : Comparable<MediaDownloadTask> {
        override fun compareTo(other: MediaDownloadTask): Int {
            // Вищий пріоритет = менше число
            return this.priority.compareTo(other.priority)
        }
    }

    private enum class MediaType {
        THUMBNAIL,
        FULL
    }
}