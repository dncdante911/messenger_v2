package com.worldmates.messenger.ui.calls

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.model.CallHistoryItem
import com.worldmates.messenger.data.repository.CallHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для екрану історії дзвінків
 */
class CallHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "CallHistoryViewModel"
    private val repository = CallHistoryRepository(application)

    val calls: StateFlow<List<CallHistoryItem>> = repository.calls
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val totalCount: StateFlow<Int> = repository.totalCount

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentFilter = MutableStateFlow("all")
    val currentFilter: StateFlow<String> = _currentFilter

    private var currentOffset = 0
    private val pageSize = 50

    init {
        loadCalls()
    }

    /**
     * Завантажити дзвінки
     */
    fun loadCalls(filter: String = _currentFilter.value) {
        _currentFilter.value = filter
        currentOffset = 0
        viewModelScope.launch {
            repository.fetchCallHistory(
                filter = filter,
                limit = pageSize,
                offset = 0,
                append = false
            ).onFailure { e ->
                _error.value = e.message
                Log.e(TAG, "Error loading calls", e)
            }
        }
    }

    /**
     * Завантажити наступну сторінку
     */
    fun loadMore() {
        if (isLoading.value) return
        if (calls.value.size >= totalCount.value) return

        currentOffset += pageSize
        viewModelScope.launch {
            repository.fetchCallHistory(
                filter = _currentFilter.value,
                limit = pageSize,
                offset = currentOffset,
                append = true
            ).onFailure { e ->
                _error.value = e.message
                currentOffset -= pageSize
            }
        }
    }

    /**
     * Оновити список (pull-to-refresh)
     */
    fun refresh() {
        loadCalls(_currentFilter.value)
    }

    /**
     * Видалити дзвінок
     */
    fun deleteCall(callId: Long) {
        viewModelScope.launch {
            repository.deleteCall(callId).onFailure { e ->
                _error.value = e.message
            }
        }
    }

    /**
     * Очистити всю історію
     */
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory().onFailure { e ->
                _error.value = e.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
