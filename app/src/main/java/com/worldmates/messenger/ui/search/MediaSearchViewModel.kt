package com.worldmates.messenger.ui.search

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.utils.EncryptedMediaHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import com.google.gson.annotations.SerializedName
import java.io.File
import java.net.URL

/**
 * 🔍 MEDIA SEARCH VIEWMODEL (ENHANCED)
 *
 * ViewModel для поиска медиа-файлов с расширенными функциями:
 * - Кэширование результатов поиска
 * - Сортировка (дата, размер, имя)
 * - Массовый выбор и экспорт
 * - Поддержка зашифрованных медиа
 */
class MediaSearchViewModel : ViewModel() {
    private val TAG = "MediaSearchViewModel"

    // 🔍 Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(MediaFilter.ALL)
    val selectedFilter: StateFlow<MediaFilter> = _selectedFilter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Message>>(emptyList())
    val searchResults: StateFlow<List<Message>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 📊 Sorting state
    private val _selectedSort = MutableStateFlow(SortOption.DATE_DESC)
    val selectedSort: StateFlow<SortOption> = _selectedSort.asStateFlow()

    // ✅ Selection state
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedMessages = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMessages: StateFlow<Set<Long>> = _selectedMessages.asStateFlow()

    // 💾 Cache
    private val _searchCache = mutableMapOf<String, List<Message>>()
    private var currentChatId: Long? = null
    private var currentGroupId: Long? = null

    private val apiService: MediaSearchApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(MediaSearchApiService::class.java)
    }

    /**
     * Установить ID чата или группы
     */
    fun setChatId(chatId: Long?, groupId: Long?) {
        if (currentChatId != chatId || currentGroupId != groupId) {
            currentChatId = chatId
            currentGroupId = groupId
            _searchCache.clear()  // Очистить кэш при смене чата
            Log.d(TAG, "📍 Set chatId=$chatId, groupId=$groupId")
        }
    }

    /**
     * Обновить поисковый запрос
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length >= 2) {
            performSearch()
        } else if (query.isEmpty()) {
            _searchResults.value = emptyList()
        }
    }

    /**
     * Выбрать фильтр
     */
    fun selectFilter(filter: MediaFilter) {
        _selectedFilter.value = filter
        applySortingAndFiltering()
    }

    /**
     * Установить опцию сортировки
     */
    fun setSortOption(sortOption: SortOption) {
        _selectedSort.value = sortOption
        applySortingAndFiltering()
    }

    /**
     * Применить сортировку и фильтрацию
     */
    private fun applySortingAndFiltering() {
        val results = _searchResults.value

        // Фильтрация
        val filteredResults = if (_selectedFilter.value == MediaFilter.ALL) {
            results
        } else {
            results.filter { message ->
                message.type in _selectedFilter.value.mediaTypes
            }
        }

        // Сортировка
        val sortedResults = when (_selectedSort.value) {
            SortOption.DATE_DESC -> filteredResults.sortedByDescending { it.timeStamp }
            SortOption.DATE_ASC -> filteredResults.sortedBy { it.timeStamp }
            SortOption.SIZE_DESC -> filteredResults.sortedByDescending { it.mediaSize ?: 0 }
            SortOption.SIZE_ASC -> filteredResults.sortedBy { it.mediaSize ?: 0 }
            SortOption.NAME_ASC -> filteredResults.sortedBy { it.mediaUrl?.substringAfterLast("/") ?: "" }
            SortOption.NAME_DESC -> filteredResults.sortedByDescending { it.mediaUrl?.substringAfterLast("/") ?: "" }
        }

        _searchResults.value = sortedResults
    }

    /**
     * Выполнить поиск
     */
    fun performSearch() {
        val query = _searchQuery.value.trim()
        if (query.length < 2) {
            Log.d(TAG, "⚠️ Query too short: $query")
            return
        }

        // Проверить кэш
        val cacheKey = buildCacheKey(query)
        _searchCache[cacheKey]?.let { cachedResults ->
            Log.d(TAG, "💾 Using cached results for: $query")
            _searchResults.value = cachedResults
            applySortingAndFiltering()
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "🔍 Searching: query='$query'")

                val accessToken = UserSession.accessToken
                if (accessToken.isNullOrEmpty()) {
                    Log.e(TAG, "❌ No access token")
                    return@launch
                }

                val results = when {
                    currentGroupId != null -> searchInGroup(accessToken, currentGroupId!!, query)
                    currentChatId != null -> searchInChat(accessToken, currentChatId!!, query)
                    else -> {
                        Log.w(TAG, "⚠️ No chat or group ID set")
                        emptyList()
                    }
                }

                // Сохранить в кэш
                _searchCache[cacheKey] = results
                _searchResults.value = results
                applySortingAndFiltering()

                Log.d(TAG, "✅ Found ${results.size} results")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Search error: ${e.message}", e)
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Построить ключ кэша
     */
    private fun buildCacheKey(query: String): String {
        return "${currentChatId}_${currentGroupId}_$query"
    }

    /**
     * Поиск в группе
     */
    private suspend fun searchInGroup(accessToken: String, groupId: Long, query: String): List<Message> {
        return try {
            val response = apiService.searchGroupMessages(
                accessToken = accessToken,
                groupId = groupId,
                query = query,
                limit = 500  // Увеличено для лучших результатов
            )

            if (response.apiStatus == 200) {
                response.messages.orEmpty()
            } else {
                Log.e(TAG, "❌ Group search API error: ${response.errorMessage}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Group search exception: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Поиск в личном чате
     */
    private suspend fun searchInChat(accessToken: String, chatId: Long, query: String): List<Message> {
        return try {
            val response = apiService.searchChatMessages(
                accessToken = accessToken,
                chatId = chatId,
                query = query,
                limit = 500
            )

            if (response.apiStatus == 200) {
                response.messages.orEmpty()
            } else {
                Log.e(TAG, "❌ Chat search API error: ${response.errorMessage}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Chat search exception: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Переключить выбор сообщения
     */
    fun toggleSelection(messageId: Long) {
        val currentSelection = _selectedMessages.value.toMutableSet()

        if (currentSelection.contains(messageId)) {
            currentSelection.remove(messageId)
        } else {
            currentSelection.add(messageId)
        }

        _selectedMessages.value = currentSelection

        // Войти в режим выбора, если есть выбранные элементы
        if (currentSelection.isNotEmpty() && !_selectionMode.value) {
            _selectionMode.value = true
        } else if (currentSelection.isEmpty() && _selectionMode.value) {
            _selectionMode.value = false
        }

        Log.d(TAG, "✅ Selected ${currentSelection.size} messages")
    }

    /**
     * Выбрать все сообщения
     */
    fun selectAll() {
        val allIds = _searchResults.value.map { it.id }.toSet()
        _selectedMessages.value = allIds
        _selectionMode.value = true
        Log.d(TAG, "✅ Selected all ${allIds.size} messages")
    }

    /**
     * Выйти из режима выбора
     */
    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedMessages.value = emptySet()
        Log.d(TAG, "❌ Exited selection mode")
    }

    /**
     * Экспорт выбранных медиа
     */
    fun exportSelectedMedia(context: Context) {
        viewModelScope.launch {
            try {
                val selectedIds = _selectedMessages.value
                if (selectedIds.isEmpty()) {
                    Toast.makeText(context, "Нет выбранных файлов", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Toast.makeText(context, "Скачивание ${selectedIds.size} файлов...", Toast.LENGTH_SHORT).show()

                val selectedMsgs = _searchResults.value.filter { it.id in selectedIds }
                var successCount = 0

                selectedMsgs.forEach { message ->
                    try {
                        val downloaded = downloadMedia(message, context)
                        if (downloaded) successCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Download error for ${message.id}: ${e.message}", e)
                    }
                }

                Toast.makeText(
                    context,
                    "✅ Скачано $successCount из ${selectedIds.size} файлов",
                    Toast.LENGTH_LONG
                ).show()

                // Выйти из режима выбора
                exitSelectionMode()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Export error: ${e.message}", e)
                Toast.makeText(context, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Скачать медиа-файл
     */
    private suspend fun downloadMedia(message: Message, context: Context): Boolean {
        try {
            val mediaUrl = message.decryptedMediaUrl ?: message.mediaUrl ?: return false
            val fullUrl = EncryptedMediaHandler.getFullMediaUrl(mediaUrl, message.type) ?: return false

            // Определить папку для сохранения
            val downloadDir = when (message.type) {
                "image" -> Environment.DIRECTORY_PICTURES
                "video" -> Environment.DIRECTORY_MOVIES
                "audio", "voice" -> Environment.DIRECTORY_MUSIC
                else -> Environment.DIRECTORY_DOWNLOADS
            }

            val downloadsFolder = Environment.getExternalStoragePublicDirectory(downloadDir)
            downloadsFolder.mkdirs()

            // Имя файла
            val fileName = mediaUrl.substringAfterLast("/")
            val destFile = File(downloadsFolder, fileName)

            // Скачать
            val url = URL(fullUrl)
            url.openStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "✅ Downloaded: ${destFile.absolutePath}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Download failed for ${message.id}: ${e.message}", e)
            return false
        }
    }

    /**
     * Очистить поиск
     */
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _selectedMessages.value = emptySet()
        _selectionMode.value = false
    }
}

/**
 * 🌐 API Service для поиска медиа
 */
interface MediaSearchApiService {
    @FormUrlEncoded
    @POST("v2/endpoints/search_group_messages.php")
    suspend fun searchGroupMessages(
        @Field("access_token") accessToken: String,
        @Field("group_id") groupId: Long,
        @Field("query") query: String,
        @Field("limit") limit: Int = 500,
        @Field("offset") offset: Int = 0
    ): SearchMessagesResponse

    @FormUrlEncoded
    @POST("v2/endpoints/search_chat_messages.php")
    suspend fun searchChatMessages(
        @Field("access_token") accessToken: String,
        @Field("chat_id") chatId: Long,
        @Field("query") query: String,
        @Field("limit") limit: Int = 500,
        @Field("offset") offset: Int = 0
    ): SearchMessagesResponse
}

/**
 * 📦 Response для поиска сообщений
 */
data class SearchMessagesResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("messages") val messages: List<Message>? = null,
    @SerializedName("total_count") val totalCount: Int? = null,
    @SerializedName("query") val query: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)
