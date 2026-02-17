package com.worldmates.messenger.ui.strapi

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.model.StrapiContentItem
import com.worldmates.messenger.data.model.StrapiContentPack
import com.worldmates.messenger.data.repository.StrapiStickerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для роботи зі Strapi контентом (стікери, GIF, емодзі)
 */
class StrapiContentViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "StrapiContentVM"
    }

    private val repository = StrapiStickerRepository.getInstance(application)

    // Всі пакети
    val allPacks: StateFlow<List<StrapiContentPack>> = repository.allPacks

    // Стікери
    val stickerPacks: StateFlow<List<StrapiContentPack>> = repository.stickerPacks

    // GIF
    val gifPacks: StateFlow<List<StrapiContentPack>> = repository.gifPacks

    // Емодзі
    val emojiPacks: StateFlow<List<StrapiContentPack>> = repository.emojiPacks

    // Стан завантаження
    val isLoading: StateFlow<Boolean> = repository.isLoading

    // Помилки
    val error: StateFlow<String?> = repository.error

    // Поточний вибраний тип контенту
    private val _selectedTab = MutableStateFlow(ContentTab.STICKERS)
    val selectedTab: StateFlow<ContentTab> = _selectedTab.asStateFlow()

    // Поточний вибраний пакет
    private val _selectedPack = MutableStateFlow<StrapiContentPack?>(null)
    val selectedPack: StateFlow<StrapiContentPack?> = _selectedPack.asStateFlow()

    // Пошуковий запит
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Відфільтровані пакети по пошуку
    private val _filteredPacks = MutableStateFlow<List<StrapiContentPack>>(emptyList())
    val filteredPacks: StateFlow<List<StrapiContentPack>> = _filteredPacks.asStateFlow()

    init {
        // Завантажуємо контент при ініціалізації
        loadContent()

        // Реактивне оновлення filteredPacks при зміні даних в репозиторії
        viewModelScope.launch {
            allPacks.collect {
                updateFilteredPacks()
            }
        }
    }

    /**
     * Завантажити контент з Strapi
     */
    fun loadContent(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            Log.d(TAG, "Завантаження контенту${if (forceRefresh) " (примусове)" else ""}")
            val result = repository.fetchAllPacks(forceRefresh)

            result.onSuccess { packs ->
                Log.d(TAG, "✅ Успішно завантажено ${packs.size} пакетів")
                updateFilteredPacks()
            }.onFailure { e ->
                Log.e(TAG, "❌ Помилка завантаження контенту", e)
            }
        }
    }

    /**
     * Обрати тип контенту (вкладка)
     */
    fun selectTab(tab: ContentTab) {
        _selectedTab.value = tab
        updateFilteredPacks()
        Log.d(TAG, "Обрано вкладку: $tab")
    }

    /**
     * Обрати пакет
     */
    fun selectPack(pack: StrapiContentPack?) {
        _selectedPack.value = pack
        Log.d(TAG, "Обрано пакет: ${pack?.name ?: "null"}")
    }

    /**
     * Оновити пошуковий запит
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        updateFilteredPacks()
    }

    /**
     * Оновити відфільтровані пакети
     */
    private fun updateFilteredPacks() {
        val packs = when (_selectedTab.value) {
            ContentTab.STICKERS -> stickerPacks.value
            ContentTab.GIFS -> gifPacks.value
            ContentTab.EMOJIS -> emojiPacks.value
            ContentTab.ALL -> allPacks.value
        }

        _filteredPacks.value = if (_searchQuery.value.isBlank()) {
            packs
        } else {
            packs.filter { pack ->
                pack.name.contains(_searchQuery.value, ignoreCase = true) ||
                        pack.slug.contains(_searchQuery.value, ignoreCase = true)
            }
        }
    }

    /**
     * Очистити кеш та перезавантажити
     */
    fun clearCacheAndReload() {
        repository.clearCache()
        loadContent(forceRefresh = true)
    }

    /**
     * Оновити контент
     */
    fun refresh() {
        loadContent(forceRefresh = true)
    }

    /**
     * Отримати всі елементи з обраного паку
     */
    fun getItemsFromSelectedPack(): List<StrapiContentItem> {
        return _selectedPack.value?.items ?: emptyList()
    }

    /**
     * Отримати популярні/рекомендовані стікери (перші 20 з першого паку)
     */
    fun getRecentItems(limit: Int = 20): List<StrapiContentItem> {
        val packs = when (_selectedTab.value) {
            ContentTab.STICKERS -> stickerPacks.value
            ContentTab.GIFS -> gifPacks.value
            ContentTab.EMOJIS -> emojiPacks.value
            ContentTab.ALL -> allPacks.value
        }

        return packs.firstOrNull()?.items?.take(limit) ?: emptyList()
    }

    /**
     * Типи вкладок контенту
     */
    enum class ContentTab {
        ALL,
        STICKERS,
        GIFS,
        EMOJIS
    }
}