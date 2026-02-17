package com.worldmates.messenger.ui.chats

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.worldmates.messenger.data.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Менеджер організації контенту: універсальні папки (Telegram-style), архів, теги.
 * Папки працюють для УСІХ типів: чати, канали, групи.
 * Ліміти: Free = 10 папок, Pro = 50 папок.
 * Зберігає дані локально в SharedPreferences.
 */
object ChatOrganizationManager {
    private const val PREFS_NAME = "chat_organization"
    private const val KEY_FOLDERS = "folders_v2"
    private const val KEY_ARCHIVED_CHAT_IDS = "archived_chat_ids"
    private const val KEY_CHAT_TAGS = "chat_tags"
    private const val KEY_CHAT_FOLDER_MAPPING = "chat_folder_mapping"
    private const val KEY_CHANNEL_FOLDER_MAPPING = "channel_folder_mapping"
    private const val KEY_GROUP_FOLDER_MAPPING = "group_folder_mapping"

    const val MAX_FOLDERS_FREE = 10
    const val MAX_FOLDERS_PRO = 50

    private val gson = Gson()

    private val _folders = MutableStateFlow<List<ChatFolder>>(emptyList())
    val folders: StateFlow<List<ChatFolder>> = _folders.asStateFlow()

    private val _archivedChatIds = MutableStateFlow<Set<Long>>(emptySet())
    val archivedChatIds: StateFlow<Set<Long>> = _archivedChatIds.asStateFlow()

    private val _chatTags = MutableStateFlow<Map<Long, List<ChatTag>>>(emptyMap())
    val chatTags: StateFlow<Map<Long, List<ChatTag>>> = _chatTags.asStateFlow()

    private val _chatFolderMapping = MutableStateFlow<Map<Long, String>>(emptyMap())
    val chatFolderMapping: StateFlow<Map<Long, String>> = _chatFolderMapping.asStateFlow()

    private val _channelFolderMapping = MutableStateFlow<Map<Long, String>>(emptyMap())
    val channelFolderMapping: StateFlow<Map<Long, String>> = _channelFolderMapping.asStateFlow()

    private val _groupFolderMapping = MutableStateFlow<Map<Long, String>>(emptyMap())
    val groupFolderMapping: StateFlow<Map<Long, String>> = _groupFolderMapping.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadAll()
    }

    private fun loadAll() {
        loadFolders()
        loadArchivedIds()
        loadChatTags()
        loadFolderMapping()
        loadChannelFolderMapping()
        loadGroupFolderMapping()
    }

    /** Максимальна кількість кастомних папок для поточного користувача */
    fun getMaxCustomFolders(): Int {
        return if (UserSession.isPro > 0) MAX_FOLDERS_PRO else MAX_FOLDERS_FREE
    }

    /** Кількість створених кастомних папок */
    fun getCustomFolderCount(): Int {
        return _folders.value.count { it.isCustom }
    }

    /** Чи можна створити нову папку */
    fun canCreateFolder(): Boolean {
        return getCustomFolderCount() < getMaxCustomFolders()
    }

    // ==================== FOLDERS ====================

    private fun loadFolders() {
        val json = prefs?.getString(KEY_FOLDERS, null)
        if (json != null) {
            val type = object : TypeToken<List<ChatFolder>>() {}.type
            _folders.value = gson.fromJson(json, type) ?: defaultFolders()
        } else {
            // Перевіряємо старий формат і мігруємо
            val oldJson = prefs?.getString("folders", null)
            if (oldJson != null) {
                // Мігруємо зі старого формату
                prefs?.edit()?.remove("folders")?.apply()
            }
            _folders.value = defaultFolders()
            saveFolders()
        }
    }

    /**
     * Стандартні папки Telegram-style.
     * Замінюють окремий TabRow (Чати/Канали/Групи) + старі папки.
     */
    private fun defaultFolders(): List<ChatFolder> = listOf(
        ChatFolder("all", "Усі", "💬", 0, contentType = ContentType.ALL),
        ChatFolder("personal", "Особисті", "👤", 1, contentType = ContentType.CHATS),
        ChatFolder("channels", "Канали", "📢", 2, contentType = ContentType.CHANNELS),
        ChatFolder("groups", "Групи", "👥", 3, contentType = ContentType.GROUPS),
        ChatFolder("unread", "Непрочитані", "🔴", 4, contentType = ContentType.ALL)
    )

    fun addFolder(name: String, emoji: String): Boolean {
        if (!canCreateFolder()) return false
        val id = "custom_${System.currentTimeMillis()}"
        val order = _folders.value.size
        val newFolder = ChatFolder(id, name, emoji, order, isCustom = true, contentType = ContentType.ALL)
        _folders.value = _folders.value + newFolder
        saveFolders()
        return true
    }

    fun removeFolder(folderId: String) {
        _folders.value = _folders.value.filter { it.id != folderId }
        // Remove content from this folder
        _chatFolderMapping.value = _chatFolderMapping.value.filter { it.value != folderId }
        _channelFolderMapping.value = _channelFolderMapping.value.filter { it.value != folderId }
        _groupFolderMapping.value = _groupFolderMapping.value.filter { it.value != folderId }
        saveFolders()
        saveFolderMapping()
        saveChannelFolderMapping()
        saveGroupFolderMapping()
    }

    fun renameFolder(folderId: String, newName: String, newEmoji: String) {
        _folders.value = _folders.value.map {
            if (it.id == folderId) it.copy(name = newName, emoji = newEmoji) else it
        }
        saveFolders()
    }

    private fun saveFolders() {
        prefs?.edit()?.putString(KEY_FOLDERS, gson.toJson(_folders.value))?.apply()
    }

    // ==================== ARCHIVE ====================

    private fun loadArchivedIds() {
        val json = prefs?.getString(KEY_ARCHIVED_CHAT_IDS, null)
        if (json != null) {
            val type = object : TypeToken<Set<Long>>() {}.type
            _archivedChatIds.value = gson.fromJson(json, type) ?: emptySet()
        }
    }

    fun archiveChat(chatId: Long) {
        _archivedChatIds.value = _archivedChatIds.value + chatId
        saveArchivedIds()
    }

    fun unarchiveChat(chatId: Long) {
        _archivedChatIds.value = _archivedChatIds.value - chatId
        saveArchivedIds()
    }

    fun isArchived(chatId: Long): Boolean = _archivedChatIds.value.contains(chatId)

    private fun saveArchivedIds() {
        prefs?.edit()?.putString(KEY_ARCHIVED_CHAT_IDS, gson.toJson(_archivedChatIds.value))?.apply()
    }

    // ==================== TAGS ====================

    private fun loadChatTags() {
        val json = prefs?.getString(KEY_CHAT_TAGS, null)
        if (json != null) {
            val type = object : TypeToken<Map<Long, List<ChatTag>>>() {}.type
            _chatTags.value = gson.fromJson(json, type) ?: emptyMap()
        }
    }

    fun addTagToChat(chatId: Long, tag: ChatTag) {
        val current = _chatTags.value.toMutableMap()
        val tags = current.getOrDefault(chatId, emptyList()).toMutableList()
        if (tags.none { it.name == tag.name }) {
            tags.add(tag)
            current[chatId] = tags
            _chatTags.value = current
            saveChatTags()
        }
    }

    fun removeTagFromChat(chatId: Long, tagName: String) {
        val current = _chatTags.value.toMutableMap()
        val tags = current.getOrDefault(chatId, emptyList()).filter { it.name != tagName }
        if (tags.isEmpty()) {
            current.remove(chatId)
        } else {
            current[chatId] = tags
        }
        _chatTags.value = current
        saveChatTags()
    }

    fun getTagsForChat(chatId: Long): List<ChatTag> {
        return _chatTags.value[chatId] ?: emptyList()
    }

    fun getAllUsedTags(): List<ChatTag> {
        return _chatTags.value.values.flatten().distinctBy { it.name }
    }

    private fun saveChatTags() {
        prefs?.edit()?.putString(KEY_CHAT_TAGS, gson.toJson(_chatTags.value))?.apply()
    }

    // ==================== CHAT FOLDER MAPPING ====================

    private fun loadFolderMapping() {
        val json = prefs?.getString(KEY_CHAT_FOLDER_MAPPING, null)
        if (json != null) {
            val type = object : TypeToken<Map<Long, String>>() {}.type
            _chatFolderMapping.value = gson.fromJson(json, type) ?: emptyMap()
        }
    }

    fun moveChatToFolder(chatId: Long, folderId: String) {
        val current = _chatFolderMapping.value.toMutableMap()
        current[chatId] = folderId
        _chatFolderMapping.value = current
        saveFolderMapping()
    }

    fun removeChatFromFolder(chatId: Long) {
        val current = _chatFolderMapping.value.toMutableMap()
        current.remove(chatId)
        _chatFolderMapping.value = current
        saveFolderMapping()
    }

    fun getChatFolder(chatId: Long): String? = _chatFolderMapping.value[chatId]

    private fun saveFolderMapping() {
        prefs?.edit()?.putString(KEY_CHAT_FOLDER_MAPPING, gson.toJson(_chatFolderMapping.value))?.apply()
    }

    // ==================== CHANNEL FOLDER MAPPING ====================

    private fun loadChannelFolderMapping() {
        val json = prefs?.getString(KEY_CHANNEL_FOLDER_MAPPING, null)
        if (json != null) {
            val type = object : TypeToken<Map<Long, String>>() {}.type
            _channelFolderMapping.value = gson.fromJson(json, type) ?: emptyMap()
        }
    }

    fun moveChannelToFolder(channelId: Long, folderId: String) {
        val current = _channelFolderMapping.value.toMutableMap()
        current[channelId] = folderId
        _channelFolderMapping.value = current
        saveChannelFolderMapping()
    }

    fun removeChannelFromFolder(channelId: Long) {
        val current = _channelFolderMapping.value.toMutableMap()
        current.remove(channelId)
        _channelFolderMapping.value = current
        saveChannelFolderMapping()
    }

    private fun saveChannelFolderMapping() {
        prefs?.edit()?.putString(KEY_CHANNEL_FOLDER_MAPPING, gson.toJson(_channelFolderMapping.value))?.apply()
    }

    // ==================== GROUP FOLDER MAPPING ====================

    private fun loadGroupFolderMapping() {
        val json = prefs?.getString(KEY_GROUP_FOLDER_MAPPING, null)
        if (json != null) {
            val type = object : TypeToken<Map<Long, String>>() {}.type
            _groupFolderMapping.value = gson.fromJson(json, type) ?: emptyMap()
        }
    }

    fun moveGroupToFolder(groupId: Long, folderId: String) {
        val current = _groupFolderMapping.value.toMutableMap()
        current[groupId] = folderId
        _groupFolderMapping.value = current
        saveGroupFolderMapping()
    }

    fun removeGroupFromFolder(groupId: Long) {
        val current = _groupFolderMapping.value.toMutableMap()
        current.remove(groupId)
        _groupFolderMapping.value = current
        saveGroupFolderMapping()
    }

    private fun saveGroupFolderMapping() {
        prefs?.edit()?.putString(KEY_GROUP_FOLDER_MAPPING, gson.toJson(_groupFolderMapping.value))?.apply()
    }
}

/**
 * Тип контенту для папки
 */
enum class ContentType {
    ALL,       // Усі типи
    CHATS,     // Тільки особисті чати
    CHANNELS,  // Тільки канали
    GROUPS     // Тільки групи
}

/**
 * Папка для організації контенту (Telegram-style)
 */
data class ChatFolder(
    val id: String,
    val name: String,
    val emoji: String,
    val order: Int,
    val isCustom: Boolean = false,
    val contentType: ContentType = ContentType.ALL
)

/**
 * Тег/мітка для чату
 */
data class ChatTag(
    val name: String,
    val color: String = "#2196F3"
) {
    companion object {
        val PRESET_TAGS = listOf(
            ChatTag("Робота", "#FF5722"),
            ChatTag("Сім'я", "#E91E63"),
            ChatTag("Друзі", "#2196F3"),
            ChatTag("Важливе", "#FF9800"),
            ChatTag("Покупки", "#4CAF50"),
            ChatTag("Навчання", "#9C27B0"),
            ChatTag("Проекти", "#607D8B"),
            ChatTag("Подорожі", "#00BCD4")
        )
    }
}
