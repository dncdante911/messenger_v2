package com.worldmates.messenger.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Global real-time online presence state.
 *
 * MessagesViewModel writes here when it receives on_user_loggedin / on_user_loggedoff
 * WebSocket events. Any screen (e.g. ChatsScreenModern) can read the state to show
 * online indicators without creating an extra socket connection.
 */
object PresenceTracker {

    private val _onlineUsers = MutableStateFlow<Set<Long>>(emptySet())
    val onlineUsers: StateFlow<Set<Long>> = _onlineUsers

    fun setOnline(userId: Long) = _onlineUsers.update { it + userId }
    fun setOffline(userId: Long) = _onlineUsers.update { it - userId }
    fun isOnline(userId: Long): Boolean = userId in _onlineUsers.value
}
