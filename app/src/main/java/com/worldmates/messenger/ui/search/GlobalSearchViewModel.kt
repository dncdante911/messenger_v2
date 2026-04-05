package com.worldmates.messenger.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.network.GlobalSearchResult
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.SavedSearch
import com.worldmates.messenger.network.SearchSuggestion
import com.worldmates.messenger.network.UserSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── Filter model ─────────────────────────────────────────────────────────────

enum class MsgTypeFilter {
    ALL, TEXT, MEDIA, STICKER;

    /** API value passed to the backend. */
    val apiValue: String get() = when (this) {
        ALL     -> "all"
        TEXT    -> "text"
        MEDIA   -> "media"
        STICKER -> "sticker"
    }
}

/**
 * Active search filter state.
 * [isActive] = true when any non-default option is set.
 */
data class SearchFilters(
    /** Lower bound in unix seconds (inclusive). */
    val dateFrom: Long? = null,
    /** Upper bound in unix seconds (inclusive). */
    val dateTo:   Long? = null,
    /** Filter to messages sent by a specific user (their user_id). */
    val fromId:   Long? = null,
    /** Message type filter. */
    val msgType:  MsgTypeFilter = MsgTypeFilter.ALL,
) {
    val isActive: Boolean
        get() = dateFrom != null || dateTo != null || fromId != null || msgType != MsgTypeFilter.ALL
}

// ─── UI state ─────────────────────────────────────────────────────────────────

data class GlobalSearchUiState(
    val query:              String                   = "",
    val isLoading:          Boolean                  = false,
    val messageResults:     List<GlobalSearchResult> = emptyList(),
    val userResults:        List<UserSearchResult>   = emptyList(),
    val totalMessages:      Int                      = 0,
    val filters:            SearchFilters            = SearchFilters(),
    // Suggestion panel (shown when query < 2 or field first focused)
    val suggestions:        List<SearchSuggestion>   = emptyList(),
    val suggestionsLoading: Boolean                  = false,
    // Saved searches list (shown in suggestion panel and/or dedicated section)
    val savedSearches:      List<SavedSearch>        = emptyList(),
    val error:              String?                  = null,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class GlobalSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    // ── Initial load ──────────────────────────────────────────────────────────

    init {
        loadSavedSearches()
        loadSuggestions("") // prefetch recent+saved for empty state
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query, error = null)

        // Trigger suggestions fetch on every keystroke (debounced, fast)
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(150)
            loadSuggestions(query)
        }

        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(
                messageResults = emptyList(),
                userResults    = emptyList(),
                isLoading      = false,
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce
            performSearch(query)
        }
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    fun updateFilters(filters: SearchFilters) {
        _uiState.value = _uiState.value.copy(filters = filters)
        val q = _uiState.value.query
        if (q.length >= 2) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(q) }
        }
    }

    fun clearFilters() = updateFilters(SearchFilters())

    // ── Search ────────────────────────────────────────────────────────────────

    /** Called when user submits search (keyboard "Search" action or suggestion tap). */
    fun submitSearch(query: String) {
        if (query.length < 2) return
        viewModelScope.launch {
            // Fire-and-forget: save to recent history
            runCatching { NodeRetrofitClient.api.saveRecentSearch(query) }
            // Reload suggestions so the new recent entry appears
            loadSuggestions(query)
        }
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        val filters = _uiState.value.filters
        try {
            val api = NodeRetrofitClient.api

            val msgJob = viewModelScope.launch {
                runCatching {
                    api.globalSearch(
                        query    = query,
                        limit    = 50,
                        dateFrom = filters.dateFrom,
                        dateTo   = filters.dateTo,
                        fromId   = filters.fromId,
                        msgType  = if (filters.msgType != MsgTypeFilter.ALL) filters.msgType.apiValue else null,
                    )
                }.onSuccess { resp ->
                    if (resp.apiStatus == 200) {
                        _uiState.value = _uiState.value.copy(
                            messageResults = resp.results,
                            totalMessages  = resp.total,
                        )
                    }
                }
            }

            val usersJob = viewModelScope.launch {
                runCatching {
                    api.searchUsers(query = query, limit = 30)
                }.onSuccess { resp ->
                    if (resp.apiStatus == 200) {
                        _uiState.value = _uiState.value.copy(userResults = resp.users)
                    }
                }
            }

            msgJob.join()
            usersJob.join()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.message)
        } finally {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    // ── Suggestions ───────────────────────────────────────────────────────────

    private fun loadSuggestions(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(suggestionsLoading = true)
            runCatching {
                NodeRetrofitClient.api.getSearchSuggestions(query)
            }.onSuccess { resp ->
                if (resp.apiStatus == 200) {
                    _uiState.value = _uiState.value.copy(suggestions = resp.suggestions)
                }
            }
            _uiState.value = _uiState.value.copy(suggestionsLoading = false)
        }
    }

    // ── Saved Searches ────────────────────────────────────────────────────────

    fun loadSavedSearches() {
        viewModelScope.launch {
            runCatching {
                NodeRetrofitClient.api.listSavedSearches()
            }.onSuccess { resp ->
                if (resp.apiStatus == 200) {
                    _uiState.value = _uiState.value.copy(savedSearches = resp.saved)
                }
            }
        }
    }

    fun saveCurrentQuery() {
        val query = _uiState.value.query.trim()
        if (query.length < 2) return
        viewModelScope.launch {
            runCatching { NodeRetrofitClient.api.saveSearch(query) }
                .onSuccess { loadSavedSearches(); loadSuggestions(query) }
        }
    }

    fun deleteSavedSearch(id: Long) {
        viewModelScope.launch {
            runCatching { NodeRetrofitClient.api.deleteSavedSearch(id) }
                .onSuccess { loadSavedSearches(); loadSuggestions(_uiState.value.query) }
        }
    }

    fun deleteRecentSuggestion(id: Long) {
        viewModelScope.launch {
            runCatching { NodeRetrofitClient.api.deleteSavedSearch(id) }
                .onSuccess { loadSuggestions(_uiState.value.query) }
        }
    }

    fun clearRecentHistory() {
        viewModelScope.launch {
            runCatching { NodeRetrofitClient.api.clearRecentSearches() }
                .onSuccess { loadSuggestions(_uiState.value.query) }
        }
    }
}
