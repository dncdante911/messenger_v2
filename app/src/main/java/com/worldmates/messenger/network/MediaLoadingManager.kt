package com.worldmates.messenger.network

import android.content.Context
import android.util.Log
import com.worldmates.messenger.data.local.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 📦 MediaLoadingManager - Менеджер прогресивного завантаження медіа
 *
 * Функції:
 * - Lazy loading: завантажує медіа тільки коли потрібно
 * - Progressive loading: спочатку превью, потім повне медіа
 * - Кешування: зберігає завантажені файли локально
 * - Пріоритизація: важливі медіа завантажуються першими
 *
 * Fixes:
 * - OOM: streaming замість bytes() — файл не тримається цілком в RAM
 * - activeDownloads: Semaphore + ConcurrentHashMap — скасування реально працює
 * - loadingStates: ConcurrentHashMap — thread-safe доступ
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

    private val apiService = NodeRetrofitClient.chatUploadApi
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Семафор для обмеження кількості паралельних завантажень
    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    // Кеш директорії
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    }

    private val thumbnailsDir: File by lazy {
        File(cacheDir, THUMBNAILS_DIR_NAME).apply { mkdirs() }
    }

    // Активні завантаження: messageId → Job (thread-safe, для скасування)
    private val activeDownloads = ConcurrentHashMap<Long, Job>()

    // Стани завантаження для кожного медіа (thread-safe)
    private val loadingStates = ConcurrentHashMap<Long, MutableStateFlow<MediaLoadProgress>>()

    /**
     * Завантажити превью (thumbnail)
     */
    fun loadThumbnail(messageId: Long, thumbnailUrl: String, priority: Int = 0): StateFlow<MediaLoadProgress> {
        val stateFlow = getOrCreateStateFlow(messageId)

        // Перевіряємо чи вже є в кеші
        val cachedThumb = getCachedThumbnail(messageId)
        if (cachedThumb != null) {
            stateFlow.value = stateFlow.value.copy(
                state = LoadingState.THUMB_LOADED,
                progress = 100,
                thumbnailPath = cachedThumb.absolutePath
            )
            return stateFlow
        }

        // Якщо вже завантажується — не дублюємо
        if (stateFlow.value.state == LoadingState.LOADING_THUMB) return stateFlow

        stateFlow.value = stateFlow.value.copy(state = LoadingState.LOADING_THUMB, progress = 0)

        val job = scope.launch {
            downloadSemaphore.withPermit {
                try {
                    downloadThumbnail(messageId, thumbnailUrl)
                } catch (e: CancellationException) {
                    Log.d(TAG, "Thumbnail download cancelled for $messageId")
                    updateState(messageId, LoadingState.IDLE)
                } catch (e: Exception) {
                    Log.e(TAG, "Thumbnail download error for $messageId", e)
                    updateState(messageId, LoadingState.ERROR, error = e.message)
                } finally {
                    activeDownloads.remove(messageId)
                }
            }
        }
        activeDownloads[messageId] = job

        return stateFlow
    }

    /**
     * Завантажити повне медіа
     */
    fun loadFullMedia(messageId: Long, mediaUrl: String, priority: Int = 5): StateFlow<MediaLoadProgress> {
        val stateFlow = getOrCreateStateFlow(messageId)

        // Перевіряємо чи вже є в кеші
        val cachedFull = getCachedFullMedia(messageId)
        if (cachedFull != null) {
            stateFlow.value = stateFlow.value.copy(
                state = LoadingState.FULL_LOADED,
                progress = 100,
                fullMediaPath = cachedFull.absolutePath
            )
            return stateFlow
        }

        // Якщо вже завантажується — не дублюємо
        if (stateFlow.value.state == LoadingState.LOADING_FULL) return stateFlow

        stateFlow.value = stateFlow.value.copy(state = LoadingState.LOADING_FULL, progress = 0)

        val job = scope.launch {
            downloadSemaphore.withPermit {
                try {
                    downloadFullMedia(messageId, mediaUrl)
                } catch (e: CancellationException) {
                    Log.d(TAG, "Full media download cancelled for $messageId")
                    updateState(messageId, LoadingState.IDLE)
                } catch (e: Exception) {
                    Log.e(TAG, "Full media download error for $messageId", e)
                    updateState(messageId, LoadingState.ERROR, error = e.message)
                } finally {
                    activeDownloads.remove(messageId)
                }
            }
        }
        activeDownloads[messageId] = job

        return stateFlow
    }

    /**
     * Завантажити превью — streaming (без завантаження всього файлу в RAM)
     */
    private suspend fun downloadThumbnail(messageId: Long, url: String) {
        Log.d(TAG, "📥 Downloading thumbnail for message $messageId")
        updateState(messageId, LoadingState.LOADING_THUMB, progress = 10)

        val response = apiService.downloadMedia(url)
        val thumbnailFile = File(thumbnailsDir, "thumb_$messageId.jpg")

        updateState(messageId, LoadingState.LOADING_THUMB, progress = 30)

        // Streaming: файл пишеться по шматках, не тримається в RAM
        response.byteStream().use { input ->
            FileOutputStream(thumbnailFile).use { output ->
                input.copyTo(output, bufferSize = 8 * 1024)
            }
        }

        updateState(
            messageId,
            LoadingState.THUMB_LOADED,
            progress = 100,
            thumbnailPath = thumbnailFile.absolutePath
        )
        Log.d(TAG, "✅ Thumbnail loaded for message $messageId")
    }

    /**
     * Завантажити повне медіа — streaming (без завантаження всього файлу в RAM)
     */
    private suspend fun downloadFullMedia(messageId: Long, url: String) {
        Log.d(TAG, "📥 Downloading full media for message $messageId")
        updateState(messageId, LoadingState.LOADING_FULL, progress = 10)

        val response = apiService.downloadMedia(url)

        // Визначаємо розширення файлу з URL (без query-параметрів)
        val extension = url.substringBefore("?").substringAfterLast('.', "bin")
        val mediaFile = File(cacheDir, "media_$messageId.$extension")

        updateState(messageId, LoadingState.LOADING_FULL, progress = 30)

        // Streaming: копіюємо по шматках
        response.byteStream().use { input ->
            FileOutputStream(mediaFile).use { output ->
                input.copyTo(output, bufferSize = 16 * 1024)
            }
        }

        updateState(
            messageId,
            LoadingState.FULL_LOADED,
            progress = 100,
            fullMediaPath = mediaFile.absolutePath
        )

        saveMediaPathToDatabase(messageId, mediaFile.absolutePath)
        Log.d(TAG, "✅ Full media loaded for message $messageId")
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
     * Отримати або створити StateFlow для медіа (thread-safe через ConcurrentHashMap)
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
        val file = File(thumbnailsDir, "thumb_$messageId.jpg")
        return if (file.exists()) file else null
    }

    /**
     * Отримати кешоване повне медіа
     */
    private fun getCachedFullMedia(messageId: Long): File? {
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
     * Скасувати завантаження — тепер реально відміняє Job
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
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
        scope.cancel()
    }
}
