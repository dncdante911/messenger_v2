package com.worldmates.messenger.ui.groups

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Менеджер онлайн-статусів учасників групи.
 *
 * Отримує події від Socket.IO (через SocketManager) і зберігає множину
 * user_id, що наразі онлайн у групі.  Усі компоненти підписуються на
 * [onlineMemberIds] і оновлюються реактивно.
 *
 * Socket-події:
 *   • group:member_online   { group_id, user_id }
 *   • group:member_offline  { group_id, user_id }
 *   • group:online_members  { group_id, user_ids: [Int] }   — початкова синхронізація
 */
object GroupOnlineStatusManager {

    private const val TAG = "GroupOnlineStatus"

    // groupId -> Set<userId>
    private val _onlineByGroup = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())

    /** Публічний StateFlow: Map<groupId, Set<userId>> */
    val onlineByGroup: StateFlow<Map<Long, Set<Long>>> = _onlineByGroup.asStateFlow()

    // ─── socket event handlers ───────────────────────────────────────────────

    /**
     * Викликається при отриманні події «group:online_members» (початкова синхронізація).
     * @param groupId  ідентифікатор групи
     * @param userIds  список user_id, що зараз онлайн
     */
    fun onInitialSync(groupId: Long, userIds: List<Long>) {
        Log.d(TAG, "onInitialSync group=$groupId count=${userIds.size}")
        val current = _onlineByGroup.value.toMutableMap()
        current[groupId] = userIds.toSet()
        _onlineByGroup.value = current
    }

    /**
     * Викликається при отриманні події «group:member_online».
     */
    fun onMemberOnline(groupId: Long, userId: Long) {
        Log.d(TAG, "onMemberOnline group=$groupId user=$userId")
        val current = _onlineByGroup.value.toMutableMap()
        val set = current[groupId]?.toMutableSet() ?: mutableSetOf()
        set.add(userId)
        current[groupId] = set
        _onlineByGroup.value = current
    }

    /**
     * Викликається при отриманні події «group:member_offline».
     */
    fun onMemberOffline(groupId: Long, userId: Long) {
        Log.d(TAG, "onMemberOffline group=$groupId user=$userId")
        val current = _onlineByGroup.value.toMutableMap()
        val set = current[groupId]?.toMutableSet() ?: return
        set.remove(userId)
        current[groupId] = set
        _onlineByGroup.value = current
    }

    /** Очистити дані для групи при виході. */
    fun clearGroup(groupId: Long) {
        val current = _onlineByGroup.value.toMutableMap()
        current.remove(groupId)
        _onlineByGroup.value = current
    }

    /** Перевірити онлайн-статус конкретного учасника групи. */
    fun isMemberOnline(groupId: Long, userId: Long): Boolean =
        _onlineByGroup.value[groupId]?.contains(userId) == true

    /** Кількість онлайн-учасників у групі. */
    fun onlineCount(groupId: Long): Int =
        _onlineByGroup.value[groupId]?.size ?: 0
}
