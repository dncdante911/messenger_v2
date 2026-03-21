package com.worldmates.messenger.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.model.AdminLogDto
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GroupAdminLogsViewModel : ViewModel() {

    private val api = NodeRetrofitClient.groupApi

    private val _logs = MutableStateFlow<List<AdminLogDto>>(emptyList())
    val logs: StateFlow<List<AdminLogDto>> = _logs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _total = MutableStateFlow(0)
    val total: StateFlow<Int> = _total

    fun loadLogs(groupId: Long, page: Int = 1) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = api.getAdminLogs(groupId, page)
                if (response.apiStatus == 200) {
                    val newLogs = response.logs ?: emptyList()
                    if (page == 1) _logs.value = newLogs
                    else _logs.value = _logs.value + newLogs
                    _total.value = response.total
                } else {
                    _error.value = response.errorMessage ?: "Unknown error"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}
