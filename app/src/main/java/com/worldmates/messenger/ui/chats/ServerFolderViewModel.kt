package com.worldmates.messenger.ui.chats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.ServerFolder
import com.worldmates.messenger.network.FolderChatItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Manages server-synced shared folders (совместные папки чатов).
 * Works alongside the local-only ChatOrganizationManager.
 *
 * Shared folders:
 *  - Stored on server (wm_chat_folders table)
 *  - Can be shared via invite link (share_code)
 *  - Other users can join via the link
 *  - All members see the same chats in the folder
 *  - Free: up to 10 folders; Premium: up to 50
 */
class ServerFolderViewModel : ViewModel() {

    private val _folders    = MutableStateFlow<List<ServerFolder>>(emptyList())
    val folders: StateFlow<List<ServerFolder>> = _folders

    private val _isLoading  = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSyncing  = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _error      = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _shareUrl   = MutableStateFlow<String?>(null)
    val shareUrl: StateFlow<String?> = _shareUrl

    fun loadFolders() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.getFolders()
                if (resp.apiStatus == 200) {
                    _folders.value = resp.folders ?: emptyList()
                } else {
                    _error.value = resp.errorMessage
                }
            } catch (e: Exception) {
                Log.e("ServerFolders", "loadFolders error", e)
                _error.value = e.localizedMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createFolder(
        name: String,
        emoji: String = "📁",
        color: String = "#2196F3",
        onCreated: (ServerFolder) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.createFolder(name, emoji, color)
                if (resp.apiStatus == 200 && resp.folder != null) {
                    _folders.value = _folders.value + resp.folder
                    onCreated(resp.folder)
                } else {
                    onError(resp.errorMessage ?: "Не вдалося створити папку")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Помилка")
            }
        }
    }

    fun updateFolder(
        id: Long,
        name: String? = null,
        emoji: String? = null,
        color: String? = null,
        onUpdated: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.updateFolder(id, name, emoji, color)
                if (resp.apiStatus == 200 && resp.folder != null) {
                    _folders.value = _folders.value.map { if (it.id == id) resp.folder else it }
                    onUpdated()
                } else {
                    onError(resp.errorMessage ?: "Не вдалося оновити папку")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Помилка")
            }
        }
    }

    fun deleteFolder(
        id: Long,
        onDeleted: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.deleteFolder(id)
                if (resp.apiStatus == 200) {
                    _folders.value = _folders.value.filter { it.id != id }
                    onDeleted()
                } else {
                    onError(resp.errorMessage ?: "Не вдалося видалити папку")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Помилка")
            }
        }
    }

    fun addChatToFolder(
        folderId: Long,
        chatType: String,
        chatId: Long,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.addChatToFolder(folderId, chatType, chatId)
                if (resp.apiStatus == 200) {
                    // Refresh folder chats list locally
                    _folders.value = _folders.value.map { folder ->
                        if (folder.id == folderId) {
                            val updatedChats = (folder.chats ?: emptyList()) +
                                    FolderChatItem(chatType, chatId, System.currentTimeMillis() / 1000)
                            folder.copy(chats = updatedChats)
                        } else folder
                    }
                    onDone()
                }
            } catch (e: Exception) {
                Log.e("ServerFolders", "addChatToFolder error", e)
            }
        }
    }

    fun removeChatFromFolder(
        folderId: Long,
        chatType: String,
        chatId: Long,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.removeChatFromFolder(folderId, chatType, chatId)
                if (resp.apiStatus == 200) {
                    _folders.value = _folders.value.map { folder ->
                        if (folder.id == folderId) {
                            folder.copy(chats = folder.chats?.filter {
                                !(it.chatType == chatType && it.chatId == chatId)
                            })
                        } else folder
                    }
                    onDone()
                }
            } catch (e: Exception) {
                Log.e("ServerFolders", "removeChatFromFolder error", e)
            }
        }
    }

    fun reorderFolders(ids: List<Long>) {
        viewModelScope.launch {
            try {
                NodeRetrofitClient.api.reorderFolders(ids)
            } catch (e: Exception) {
                Log.e("ServerFolders", "reorder error", e)
            }
        }
    }

    fun shareFolder(
        id: Long,
        onShared: (shareUrl: String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.shareFolder(id)
                if (resp.apiStatus == 200 && resp.shareUrl != null) {
                    _folders.value = _folders.value.map { folder ->
                        if (folder.id == id) folder.copy(isShared = true, shareCode = resp.shareCode) else folder
                    }
                    _shareUrl.value = resp.shareUrl
                    onShared(resp.shareUrl)
                } else {
                    onError(resp.errorMessage ?: "Не вдалося поширити папку")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Помилка")
            }
        }
    }

    fun joinFolder(
        code: String,
        onJoined: (ServerFolder) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.joinFolder(code)
                if (resp.apiStatus == 200 && resp.folder != null) {
                    if (_folders.value.none { it.id == resp.folder.id }) {
                        _folders.value = _folders.value + resp.folder
                    }
                    onJoined(resp.folder)
                } else {
                    onError(resp.errorMessage ?: "Не вдалося приєднатися")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Помилка")
            }
        }
    }

    fun leaveFolder(
        id: Long,
        onLeft: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.leaveFolder(id)
                if (resp.apiStatus == 200) {
                    _folders.value = _folders.value.filter { it.id != id }
                    onLeft()
                } else {
                    onError(resp.errorMessage ?: "Не вдалося покинути папку")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Помилка")
            }
        }
    }

    fun clearShareUrl() { _shareUrl.value = null }
    fun clearError()    { _error.value = null }
}
