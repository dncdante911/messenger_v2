package com.worldmates.messenger.ui.bots

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.network.BotRepository
import com.worldmates.messenger.network.BotSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для Bot UI (каталог ботів, управління, створення).
 *
 * Підтримує:
 * - Перегляд каталогу ботів
 * - Пошук ботів за ім'ям/категорією
 * - Перегляд деталей бота (профіль, команди)
 * - Управління своїми ботами (CRUD)
 * - Створення нового бота
 */
class BotViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val botRepository: BotRepository = BotRepository()

    companion object {
        private const val TAG = "BotViewModel"
    }

    // ==================== UI STATE ====================

    /** Стан екрану каталогу ботів */
    private val _catalogState = MutableStateFlow(BotCatalogState())
    val catalogState: StateFlow<BotCatalogState> = _catalogState.asStateFlow()

    /** Стан деталей бота */
    private val _botDetailState = MutableStateFlow(BotDetailState())
    val botDetailState: StateFlow<BotDetailState> = _botDetailState.asStateFlow()

    /** Стан управління ботами (мої боти) */
    private val _myBotsState = MutableStateFlow(MyBotsState())
    val myBotsState: StateFlow<MyBotsState> = _myBotsState.asStateFlow()

    /** Стан створення бота */
    private val _createBotState = MutableStateFlow(CreateBotState())
    val createBotState: StateFlow<CreateBotState> = _createBotState.asStateFlow()

    // ==================== CATALOG ====================

    fun loadBotCatalog(query: String? = null, category: String? = null) {
        viewModelScope.launch {
            _catalogState.value = _catalogState.value.copy(isLoading = true, error = null)

            val token = UserSession.accessToken ?: run {
                _catalogState.value = _catalogState.value.copy(
                    isLoading = false,
                    error = getApplication<Application>().getString(R.string.bot_error_not_authenticated)
                )
                return@launch
            }

            val result = botRepository.searchBots(
                accessToken = token,
                query = query,
                category = category,
                limit = 30
            )

            result.fold(
                onSuccess = { searchResult ->
                    _catalogState.value = _catalogState.value.copy(
                        isLoading = false,
                        bots = searchResult.bots,
                        categories = searchResult.categories,
                        searchQuery = query ?: "",
                        selectedCategory = category
                    )
                },
                onFailure = { e ->
                    Log.e(TAG, "loadBotCatalog error", e)
                    _catalogState.value = _catalogState.value.copy(
                        isLoading = false,
                        error = e.message ?: getApplication<Application>().getString(R.string.bot_error_load_failed)
                    )
                }
            )
        }
    }

    fun searchBots(query: String) {
        loadBotCatalog(query = query.ifBlank { null })
    }

    fun filterByCategory(category: String?) {
        loadBotCatalog(category = category)
    }

    // ==================== BOT DETAILS ====================

    fun loadBotDetails(botId: String? = null, username: String? = null) {
        viewModelScope.launch {
            _botDetailState.value = BotDetailState(isLoading = true)

            val token = UserSession.accessToken ?: return@launch

            val result = botRepository.getBotInfo(token, botId = botId, username = username)
            result.fold(
                onSuccess = { bot ->
                    // Also load commands
                    val commands = botRepository.getBotCommands(token, bot.botId)
                    _botDetailState.value = BotDetailState(
                        bot = bot,
                        commands = commands.getOrNull() ?: bot.commands ?: emptyList()
                    )
                },
                onFailure = { e ->
                    _botDetailState.value = BotDetailState(error = e.message)
                }
            )
        }
    }

    // ==================== MY BOTS ====================

    fun loadMyBots() {
        viewModelScope.launch {
            _myBotsState.value = _myBotsState.value.copy(isLoading = true, error = null)

            val token = UserSession.accessToken ?: return@launch

            val result = botRepository.getMyBots(token)
            result.fold(
                onSuccess = { bots ->
                    _myBotsState.value = MyBotsState(bots = bots)
                },
                onFailure = { e ->
                    _myBotsState.value = MyBotsState(error = e.message)
                }
            )
        }
    }

    fun deleteBot(botId: String) {
        viewModelScope.launch {
            val token = UserSession.accessToken ?: return@launch

            val result = botRepository.deleteBot(token, botId)
            result.fold(
                onSuccess = {
                    // Reload my bots list
                    loadMyBots()
                },
                onFailure = { e ->
                    _myBotsState.value = _myBotsState.value.copy(error = e.message)
                }
            )
        }
    }

    fun regenerateToken(botId: String) {
        viewModelScope.launch {
            val token = UserSession.accessToken ?: return@launch
            _myBotsState.value = _myBotsState.value.copy(regeneratedToken = null)

            val result = botRepository.regenerateToken(token, botId)
            result.fold(
                onSuccess = { newToken ->
                    _myBotsState.value = _myBotsState.value.copy(regeneratedToken = newToken)
                },
                onFailure = { e ->
                    _myBotsState.value = _myBotsState.value.copy(error = e.message)
                }
            )
        }
    }

    // ==================== CREATE BOT ====================

    fun updateCreateForm(
        username: String? = null,
        displayName: String? = null,
        description: String? = null,
        about: String? = null,
        category: String? = null,
        isPublic: Boolean? = null,
        canJoinGroups: Boolean? = null
    ) {
        val current = _createBotState.value
        _createBotState.value = current.copy(
            username = username ?: current.username,
            displayName = displayName ?: current.displayName,
            description = description ?: current.description,
            about = about ?: current.about,
            category = category ?: current.category,
            isPublic = isPublic ?: current.isPublic,
            canJoinGroups = canJoinGroups ?: current.canJoinGroups,
            error = null
        )
    }

    fun createBot() {
        viewModelScope.launch {
            val state = _createBotState.value
            val token = UserSession.accessToken ?: return@launch
            val app = getApplication<Application>()

            // Validate
            if (state.username.isBlank()) {
                _createBotState.value = state.copy(error = app.getString(R.string.bot_error_username_required))
                return@launch
            }
            if (state.displayName.isBlank()) {
                _createBotState.value = state.copy(error = app.getString(R.string.bot_error_name_required))
                return@launch
            }
            if (!state.username.endsWith("_bot")) {
                _createBotState.value = state.copy(error = app.getString(R.string.bot_error_username_format))
                return@launch
            }

            _createBotState.value = state.copy(isCreating = true, error = null)

            val result = botRepository.createBot(
                accessToken = token,
                username = state.username,
                displayName = state.displayName,
                description = state.description,
                about = state.about,
                category = state.category,
                canJoinGroups = state.canJoinGroups,
                isPublic = state.isPublic
            )

            result.fold(
                onSuccess = { bot ->
                    _createBotState.value = CreateBotState(
                        isCreating = false,
                        createdBot = bot,
                        createdToken = bot.botToken
                    )
                },
                onFailure = { e ->
                    _createBotState.value = state.copy(
                        isCreating = false,
                        error = e.message ?: app.getString(R.string.bot_error_create_failed)
                    )
                }
            )
        }
    }

    fun resetCreateForm() {
        _createBotState.value = CreateBotState()
    }

    fun clearError() {
        _catalogState.value = _catalogState.value.copy(error = null)
        _myBotsState.value = _myBotsState.value.copy(error = null)
        _createBotState.value = _createBotState.value.copy(error = null)
    }

    // ==================== RSS FEEDS ====================

    private val _rssState = MutableStateFlow(RssFeedsState())
    val rssState: StateFlow<RssFeedsState> = _rssState.asStateFlow()

    fun loadRssFeeds(botId: String) {
        viewModelScope.launch {
            val token = UserSession.accessToken ?: return@launch
            _rssState.value = _rssState.value.copy(isLoading = true, error = null, botId = botId)
            botRepository.getRssFeeds(token, botId).fold(
                onSuccess = { feeds -> _rssState.value = _rssState.value.copy(isLoading = false, feeds = feeds) },
                onFailure = { e -> _rssState.value = _rssState.value.copy(isLoading = false, error = e.message) }
            )
        }
    }

    fun addRssFeed(feedUrl: String, chatId: String, feedName: String?) {
        viewModelScope.launch {
            val token = UserSession.accessToken ?: return@launch
            val botId = _rssState.value.botId ?: return@launch
            _rssState.value = _rssState.value.copy(isSaving = true, error = null)
            botRepository.addRssFeed(token, botId, feedUrl, chatId, feedName).fold(
                onSuccess = { loadRssFeeds(botId) },
                onFailure = { e -> _rssState.value = _rssState.value.copy(isSaving = false, error = e.message) }
            )
        }
    }

    fun toggleRssFeed(feedId: Int, isActive: Boolean) {
        viewModelScope.launch {
            val token = UserSession.accessToken ?: return@launch
            val botId = _rssState.value.botId ?: return@launch
            botRepository.toggleRssFeed(token, botId, feedId, isActive).fold(
                onSuccess = { loadRssFeeds(botId) },
                onFailure = { e -> _rssState.value = _rssState.value.copy(error = e.message) }
            )
        }
    }

    fun deleteRssFeed(feedId: Int) {
        viewModelScope.launch {
            val token = UserSession.accessToken ?: return@launch
            val botId = _rssState.value.botId ?: return@launch
            botRepository.deleteRssFeed(token, botId, feedId).fold(
                onSuccess = { loadRssFeeds(botId) },
                onFailure = { e -> _rssState.value = _rssState.value.copy(error = e.message) }
            )
        }
    }
}

// ==================== UI STATE DATA CLASSES ====================

data class BotCatalogState(
    val isLoading: Boolean = false,
    val bots: List<Bot> = emptyList(),
    val categories: List<BotCategory> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val error: String? = null
)

data class BotDetailState(
    val isLoading: Boolean = false,
    val bot: Bot? = null,
    val commands: List<BotCommand> = emptyList(),
    val error: String? = null
)

data class MyBotsState(
    val isLoading: Boolean = false,
    val bots: List<Bot> = emptyList(),
    val regeneratedToken: String? = null,
    val error: String? = null
)

data class CreateBotState(
    val username: String = "",
    val displayName: String = "",
    val description: String = "",
    val about: String = "",
    val category: String = "general",
    val isPublic: Boolean = true,
    val canJoinGroups: Boolean = true,
    val isCreating: Boolean = false,
    val createdBot: Bot? = null,
    val createdToken: String? = null,
    val error: String? = null
)

data class RssFeedsState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val botId: String? = null,
    val feeds: List<com.worldmates.messenger.data.model.RssFeed> = emptyList(),
    val error: String? = null
)
