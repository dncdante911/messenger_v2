package com.worldmates.messenger.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.GlobalSearchResult
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.UserSearchResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GlobalSearchUiState(
    val query:          String                  = "",
    val isLoading:      Boolean                 = false,
    val messageResults: List<GlobalSearchResult> = emptyList(),
    val userResults:    List<UserSearchResult>   = emptyList(),
    val error:          String?                  = null,
)

class GlobalSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query, error = null)
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

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        try {
            val api = NodeRetrofitClient.getApi(UserSession.accessToken ?: "")

            // Run both searches concurrently
            val messagesDeferred = viewModelScope.launch {
                try {
                    val resp = api.globalSearch(query = query, limit = 50)
                    if (resp.apiStatus == 200) {
                        _uiState.value = _uiState.value.copy(messageResults = resp.results)
                    }
                } catch (_: Exception) {}
            }

            val usersDeferred = viewModelScope.launch {
                try {
                    val resp = api.searchUsers(query = query, limit = 30)
                    if (resp.apiStatus == 200) {
                        _uiState.value = _uiState.value.copy(userResults = resp.users)
                    }
                } catch (_: Exception) {}
            }

            messagesDeferred.join()
            usersDeferred.join()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.message)
        } finally {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}
