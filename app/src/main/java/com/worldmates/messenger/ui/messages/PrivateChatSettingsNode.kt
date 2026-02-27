package com.worldmates.messenger.ui.messages

import android.util.Log
import com.worldmates.messenger.network.NodeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Helpers for private-chat settings via the Node.js REST API.
 *
 * Extracted from MessagesViewModel (>1300 lines) into this file.
 * All functions are top-level so they work with any CoroutineScope.
 *
 * Covers:
 *   ✅ mutePrivateChatViaNode   — mute/unmute notifications (POST /api/node/chat/mute)
 *   ✅ clearHistoryViaNode      — soft-delete all messages for caller (POST /api/node/chat/clear-history)
 *   ✅ loadMuteStatusViaNode    — fetch current notify/archive/pin status (POST /api/node/chat/mute-status)
 */

private const val TAG_SETTINGS = "PrivateChatSettingsNode"

// ─── mutePrivateChat ──────────────────────────────────────────────────────────

/**
 * Mute or unmute notifications for a private chat via Node.js.
 *
 * @param mute  true → mute (notify="no"), false → unmute (notify="yes")
 * @param onSuccess Called with the new muted state (true = muted) on success.
 */
fun mutePrivateChatViaNode(
    scope:       CoroutineScope,
    api:         NodeApi,
    chatId:      Long,
    mute:        Boolean,
    isLoading:   MutableStateFlow<Boolean>,
    error:       MutableStateFlow<String?>,
    onSuccess:   (isMuted: Boolean) -> Unit
) {
    if (chatId <= 0L) {
        error.value = "Невірний ідентифікатор чату"
        return
    }

    scope.launch {
        isLoading.value = true
        try {
            val resp = api.muteChat(
                chatId   = chatId,
                notify   = if (mute) "no" else "yes",
                callChat = if (mute) "no" else "yes"
            )
            if (resp.apiStatus == 200) {
                Log.d(TAG_SETTINGS, "Chat $chatId ${if (mute) "muted" else "unmuted"} via Node.js")
                error.value = null
                onSuccess(mute)
            } else {
                error.value = resp.errorMessage ?: "Не вдалося змінити налаштування сповіщень"
                Log.e(TAG_SETTINGS, "mutePrivateChatViaNode error: ${resp.errorMessage}")
            }
        } catch (e: Exception) {
            error.value = "Помилка: ${e.localizedMessage}"
            Log.e(TAG_SETTINGS, "mutePrivateChatViaNode exception", e)
        } finally {
            isLoading.value = false
        }
    }
}

// ─── clearHistory ─────────────────────────────────────────────────────────────

/**
 * Soft-deletes all messages in a private chat for the caller only via Node.js.
 * The conversation entry and the other party's messages are kept intact.
 */
fun clearHistoryViaNode(
    scope:       CoroutineScope,
    api:         NodeApi,
    recipientId: Long,
    isLoading:   MutableStateFlow<Boolean>,
    error:       MutableStateFlow<String?>,
    onSuccess:   () -> Unit
) {
    if (recipientId <= 0L) {
        error.value = "Невірний ідентифікатор співрозмовника"
        return
    }

    scope.launch {
        isLoading.value = true
        try {
            val resp = api.clearHistory(recipientId = recipientId)
            if (resp.apiStatus == 200) {
                Log.d(TAG_SETTINGS, "Chat history cleared for recipient $recipientId via Node.js")
                error.value = null
                onSuccess()
            } else {
                error.value = resp.errorMessage ?: "Не вдалося очистити історію"
                Log.e(TAG_SETTINGS, "clearHistoryViaNode error: ${resp.errorMessage}")
            }
        } catch (e: Exception) {
            error.value = "Помилка: ${e.localizedMessage}"
            Log.e(TAG_SETTINGS, "clearHistoryViaNode exception", e)
        } finally {
            isLoading.value = false
        }
    }
}

// ─── loadMuteStatus ───────────────────────────────────────────────────────────

/**
 * Loads the current mute/notify status for a private chat from Node.js.
 *
 * @param onResult Called with isMuted = true when notify == "no".
 */
fun loadMuteStatusViaNode(
    scope:    CoroutineScope,
    api:      NodeApi,
    chatId:   Long,
    onResult: (isMuted: Boolean) -> Unit
) {
    if (chatId <= 0L) return

    scope.launch {
        try {
            val resp = api.getMuteStatus(chatId = chatId)
            if (resp.apiStatus == 200) {
                val isMuted = resp.notify == "no"
                Log.d(TAG_SETTINGS, "MuteStatus for chat $chatId: notify=${resp.notify} → isMuted=$isMuted")
                onResult(isMuted)
            } else {
                Log.w(TAG_SETTINGS, "getMuteStatus returned ${resp.apiStatus}: ${resp.errorMessage}")
            }
        } catch (e: Exception) {
            Log.e(TAG_SETTINGS, "loadMuteStatusViaNode exception", e)
        }
    }
}
