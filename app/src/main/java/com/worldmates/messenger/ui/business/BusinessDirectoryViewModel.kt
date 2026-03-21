package com.worldmates.messenger.ui.business

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.model.BusinessDirectoryItem
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BusinessDirectoryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BusinessDirectoryVM"
    }

    private val api = NodeRetrofitClient.businessDirectoryApi

    // ── State flows ───────────────────────────────────────────────────────────

    private val _businesses = MutableStateFlow<List<BusinessDirectoryItem>>(emptyList())
    val businesses: StateFlow<List<BusinessDirectoryItem>> = _businesses

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadDirectory()
        loadCategories()
    }

    // ── Public actions ────────────────────────────────────────────────────────

    fun loadDirectory(page: Int = 1) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val category = _selectedCategory.value
                val search   = _searchQuery.value.takeIf { it.isNotBlank() }

                val response = api.getBusinessDirectory(
                    page     = page,
                    limit    = 20,
                    category = category,
                    search   = search
                )

                if (response.status == 200) {
                    _businesses.value = response.businesses
                } else {
                    _error.value = "Error: ${response.status}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadDirectory failed: ${e.message}", e)
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                val response = api.getBusinessCategories()
                if (response.status == 200) {
                    _categories.value = response.categories
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadCategories failed: ${e.message}", e)
            }
        }
    }

    fun setCategory(cat: String?) {
        _selectedCategory.value = cat
        loadDirectory()
    }

    fun setSearch(q: String) {
        _searchQuery.value = q
        loadDirectory()
    }
}
