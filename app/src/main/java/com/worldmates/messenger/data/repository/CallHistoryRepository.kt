package com.worldmates.messenger.data.repository

import android.content.Context
import android.util.Log
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.CallHistoryItem
import com.worldmates.messenger.network.CallHistoryApiService
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Репозиторій для історії дзвінків
 */
class CallHistoryRepository(context: Context) {

    private val TAG = "CallHistoryRepository"

    private val apiService: CallHistoryApiService =
        NodeRetrofitClient.callHistoryApi

    private val _calls = MutableStateFlow<List<CallHistoryItem>>(emptyList())
    val calls: StateFlow<List<CallHistoryItem>> = _calls

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    /**
     * Завантажити історію дзвінків
     */
    suspend fun fetchCallHistory(
        filter: String = "all",
        limit: Int = 50,
        offset: Int = 0,
        append: Boolean = false
    ): Result<List<CallHistoryItem>> {
        val token = UserSession.accessToken ?: return Result.failure(Exception("Not authenticated"))

        _isLoading.value = true
        return try {
            val response = apiService.getCallHistory(
                accessToken = token,
                filter = filter,
                limit = limit,
                offset = offset
            )

            if (response.isSuccess) {
                val callsList = response.calls ?: emptyList()
                if (append) {
                    _calls.value = _calls.value + callsList
                } else {
                    _calls.value = callsList
                }
                _totalCount.value = response.total
                Log.d(TAG, "Loaded ${callsList.size} calls, total: ${response.total}")
                Result.success(callsList)
            } else {
                val error = response.errorMessage ?: "Failed to load call history"
                Log.e(TAG, "API error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading call history", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Видалити дзвінок з історії
     */
    suspend fun deleteCall(callId: Long): Result<Unit> {
        val token = UserSession.accessToken ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = apiService.deleteCall(
                accessToken = token,
                callId = callId
            )

            if (response.isSuccess) {
                _calls.value = _calls.value.filter { it.id != callId }
                Log.d(TAG, "Deleted call: $callId")
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Failed to delete call"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception deleting call", e)
            Result.failure(e)
        }
    }

    /**
     * Очистити всю історію
     */
    suspend fun clearHistory(): Result<Unit> {
        val token = UserSession.accessToken ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = apiService.clearHistory(accessToken = token)

            if (response.isSuccess) {
                _calls.value = emptyList()
                _totalCount.value = 0
                Log.d(TAG, "Call history cleared")
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Failed to clear history"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception clearing history", e)
            Result.failure(e)
        }
    }
}
