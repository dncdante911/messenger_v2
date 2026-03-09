package com.worldmates.messenger.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.TopicDto
import com.worldmates.messenger.ui.groups.components.Subgroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GroupTopicsViewModel : ViewModel() {

    private val api = NodeRetrofitClient.groupApi

    private val _topics = MutableStateFlow<List<Subgroup>>(emptyList())
    val topics: StateFlow<List<Subgroup>> = _topics

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentGroupId: Long = 0L

    fun loadTopics(groupId: Long) {
        currentGroupId = groupId
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val resp = api.getTopics(groupId)
                if (resp.apiStatus == 200) {
                    _topics.value = resp.topics?.map { it.toSubgroup(groupId) } ?: emptyList()
                } else {
                    _error.value = resp.errorMessage
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createTopic(name: String, description: String? = null, color: String? = null, isPrivate: Boolean = false) {
        viewModelScope.launch {
            try {
                val resp = api.createTopic(
                    groupId = currentGroupId,
                    name = name,
                    description = description,
                    color = color,
                    isPrivate = if (isPrivate) 1 else 0
                )
                if (resp.apiStatus == 200) loadTopics(currentGroupId)
                else _error.value = resp.errorMessage
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun updateTopic(topicId: Long, name: String? = null, description: String? = null, color: String? = null) {
        viewModelScope.launch {
            try {
                val resp = api.updateTopic(currentGroupId, topicId, name, description, color)
                if (resp.apiStatus == 200) loadTopics(currentGroupId)
                else _error.value = resp.errorMessage
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun deleteTopic(topicId: Long) {
        viewModelScope.launch {
            try {
                val resp = api.deleteTopic(currentGroupId, topicId)
                if (resp.apiStatus == 200) loadTopics(currentGroupId)
                else _error.value = resp.errorMessage
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun clearError() { _error.value = null }
}
