package com.worldmates.messenger.ui.channels

import android.content.Context
import android.util.Log
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.ChannelComment
import com.worldmates.messenger.data.model.ChannelPost
import com.worldmates.messenger.network.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Socket.IO bridge for channel real-time updates.
 * Handles subscription, event parsing, and callback dispatch.
 *
 * Usage:
 *   val handler = ChannelSocketHandler(context)
 *   handler.connect(channelId, onPostCreated = { ... }, onPostUpdated = { ... })
 *   handler.disconnect()
 */
class ChannelSocketHandler(context: Context) {

    companion object {
        private const val TAG = "ChannelSocket"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var channelId: Long = 0
    private var isConnected = false

    private val socketManager: SocketManager

    // Callbacks
    var onPostCreated: ((ChannelPost) -> Unit)? = null
    var onPostUpdated: ((Long, String?, String?) -> Unit)? = null  // postId, text, media
    var onPostDeleted: ((Long) -> Unit)? = null  // postId
    var onPostPinned: ((Long, Boolean) -> Unit)? = null  // postId, isPinned
    var onCommentAdded: ((Long, ChannelComment) -> Unit)? = null  // postId, comment
    var onCommentDeleted: ((Long, Long) -> Unit)? = null  // postId, commentId
    var onReactionChanged: ((Long, String, String) -> Unit)? = null  // postId, emoji, action

    init {
        val listener = object : SocketManager.SocketListener {
            override fun onNewMessage(messageJson: JSONObject) { /* not used for channels */ }
            override fun onSocketConnected() {
                Log.d(TAG, "Socket connected, re-subscribing to channel $channelId")
                if (channelId > 0) {
                    socketManager.subscribeToChannel(channelId)
                }
            }
            override fun onSocketDisconnected() {
                Log.d(TAG, "Socket disconnected from channel $channelId")
            }
            override fun onSocketError(error: String) {
                Log.e(TAG, "Socket error: $error")
            }
        }
        socketManager = SocketManager(listener, context)
    }

    /**
     * Connect to channel room and start listening for events
     */
    fun connect(channelId: Long) {
        this.channelId = channelId
        socketManager.connect()

        registerListeners()

        // Subscribe after a short delay to ensure connection
        scope.launch {
            kotlinx.coroutines.delay(500)
            socketManager.subscribeToChannel(channelId)
            isConnected = true
            Log.d(TAG, "Connected to channel $channelId")
        }
    }

    /**
     * Disconnect from channel and cleanup
     */
    fun disconnect() {
        if (channelId > 0) {
            socketManager.unsubscribeFromChannel(channelId)
        }
        socketManager.disconnect()
        isConnected = false
        scope.cancel()
        Log.d(TAG, "Disconnected from channel $channelId")
    }

    /**
     * Emit new post event so other subscribers get it in real-time
     */
    fun emitNewPost(post: ChannelPost) {
        try {
            val postJson = JSONObject().apply {
                put("id", post.id)
                put("channel_id", channelId)
                put("user_id", post.authorId)
                put("username", post.authorUsername ?: "")
                put("user_name", post.authorName ?: "")
                put("user_avatar", post.authorAvatar ?: "")
                put("text", post.text)
                put("created_time", post.createdTime)
                put("is_pinned", post.isPinned)
                put("views_count", post.viewsCount)
                put("comments_count", post.commentsCount)
            }
            val data = JSONObject().apply {
                put("channelId", channelId)
                put("post", postJson)
            }
            socketManager.emitRaw("channel:new_post", data)
            Log.d(TAG, "Emitted new post ${post.id} to channel $channelId")
        } catch (e: Exception) {
            Log.e(TAG, "Error emitting new post", e)
        }
    }

    /**
     * Emit post deleted event
     */
    fun emitPostDeleted(postId: Long) {
        try {
            val data = JSONObject().apply {
                put("channelId", channelId)
                put("postId", postId)
            }
            socketManager.emitRaw("channel:post_deleted", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error emitting post deleted", e)
        }
    }

    /**
     * Emit post updated event
     */
    fun emitPostUpdated(postId: Long, text: String, media: String? = null) {
        try {
            val data = JSONObject().apply {
                put("channelId", channelId)
                put("postId", postId)
                put("text", text)
                if (media != null) put("media", media)
            }
            socketManager.emitRaw("channel:post_updated", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error emitting post updated", e)
        }
    }

    /**
     * Emit new comment event
     */
    fun emitNewComment(postId: Long, comment: ChannelComment) {
        try {
            val commentJson = JSONObject().apply {
                put("id", comment.id)
                put("user_id", comment.userId)
                put("username", comment.username ?: "")
                put("user_avatar", comment.userAvatar ?: "")
                put("text", comment.text)
                put("created_time", comment.time)
            }
            val data = JSONObject().apply {
                put("channelId", channelId)
                put("postId", postId)
                put("comment", commentJson)
            }
            socketManager.emitRaw("channel:new_comment", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error emitting new comment", e)
        }
    }

    /**
     * Emit reaction event
     */
    fun emitReaction(postId: Long, emoji: String, action: String) {
        try {
            val data = JSONObject().apply {
                put("channelId", channelId)
                put("postId", postId)
                put("userId", UserSession.userId)
                put("emoji", emoji)
                put("action", action)
            }
            socketManager.emitRaw("channel:post_reaction", data)
        } catch (e: Exception) {
            Log.e(TAG, "Error emitting reaction", e)
        }
    }

    /**
     * Send typing indicator for comment composition
     */
    fun sendTyping(postId: Long, isTyping: Boolean) {
        socketManager.sendChannelTyping(channelId, postId, isTyping)
    }

    // ==================== PRIVATE ====================

    private fun registerListeners() {
        socketManager.onChannelPostCreated { json ->
            scope.launch {
                try {
                    val post = parsePostFromSocket(json)
                    if (post != null) {
                        onPostCreated?.invoke(post)
                        Log.d(TAG, "Received new post: ${post.id}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing new post", e)
                }
            }
        }

        socketManager.onChannelPostUpdated { json ->
            scope.launch {
                try {
                    val postId = json.optLong("postId", 0)
                    val text = json.optString("text", null)
                    val media = json.optString("media", null)
                    if (postId > 0) {
                        onPostUpdated?.invoke(postId, text, media)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing post update", e)
                }
            }
        }

        socketManager.onChannelPostDeleted { json ->
            scope.launch {
                try {
                    val postId = json.optLong("postId", 0)
                    if (postId > 0) {
                        onPostDeleted?.invoke(postId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing post deletion", e)
                }
            }
        }

        socketManager.onChannelCommentAdded { json ->
            scope.launch {
                try {
                    val postId = json.optLong("postId", 0)
                    val commentJson = json.optJSONObject("comment")
                    if (postId > 0 && commentJson != null) {
                        val comment = ChannelComment(
                            id = commentJson.optLong("id", 0),
                            userId = commentJson.optLong("userId", 0),
                            username = commentJson.optString("username", null),
                            userName = commentJson.optString("username", null),
                            userAvatar = commentJson.optString("userAvatar", null),
                            text = commentJson.optString("text", ""),
                            time = commentJson.optLong("createdTime", System.currentTimeMillis() / 1000)
                        )
                        onCommentAdded?.invoke(postId, comment)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing comment", e)
                }
            }
        }
    }

    /**
     * Parse a channel post from the minified socket format
     * Minified fields: id, cid, uid, un, uname, uav, txt, med, ct, pin, views, coms, reacts
     */
    private fun parsePostFromSocket(json: JSONObject): ChannelPost? {
        val postJson = json.optJSONObject("post") ?: return null

        return ChannelPost(
            id = postJson.optLong("id", 0),
            authorId = postJson.optLong("uid", postJson.optLong("user_id", 0)),
            authorUsername = postJson.optString("un", postJson.optString("username", null)),
            authorName = postJson.optString("uname", postJson.optString("user_name", null)),
            authorAvatar = postJson.optString("uav", postJson.optString("user_avatar", null)),
            text = postJson.optString("txt", postJson.optString("text", "")),
            createdTime = postJson.optLong("ct", postJson.optLong("created_time", 0)),
            isPinned = postJson.optBoolean("pin", postJson.optBoolean("is_pinned", false)),
            viewsCount = postJson.optInt("views", postJson.optInt("views_count", 0)),
            commentsCount = postJson.optInt("coms", postJson.optInt("comments_count", 0)),
            reactionsCount = postJson.optInt("reacts_count", 0)
        )
    }
}
