package com.worldmates.messenger.ui.messages

import android.util.Log
import com.worldmates.messenger.network.NodeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Helpers for sending private-chat messages via the Node.js REST API.
 *
 * Extracted from MessagesViewModel (>1300 lines) into this file.
 * All functions are top-level so they work with any CoroutineScope.
 *
 * Covers:
 *   ✅ sendGifViaNode     — GIF URL stored in `stickers` field (Node detects .gif)
 *   ✅ sendLocationViaNode — lat/lng stored as native location message (type = map)
 *   ✅ sendContactViaNode  — vCard text stored in `contact` field (type_two = contact)
 *   ✅ forwardPrivateMsg   — forward via /api/node/chat/forward (decrypts+re-encrypts on server)
 */

private const val TAG_SENDERS = "PrivateChatNodeSenders"

// ─── sendGif ─────────────────────────────────────────────────────────────────

/**
 * Sends a GIF as a private message via Node.js.
 * GIF URL is placed in `stickers` field; the server classifies it as type=gif
 * when the URL ends with `.gif`.
 *
 * @param onSuccess Called with the decrypted message after successful send.
 *                  Append it to the local messages list in the ViewModel.
 */
fun sendGifViaNode(
    scope:       CoroutineScope,
    api:         NodeApi,
    recipientId: Long,
    gifUrl:      String,
    isLoading:   MutableStateFlow<Boolean>,
    error:       MutableStateFlow<String?>,
    onSuccess:   () -> Unit
) {
    if (gifUrl.isBlank()) {
        error.value = "GIF URL не може бути порожнім"
        return
    }

    scope.launch {
        isLoading.value = true
        try {
            val resp = api.sendMessage(
                recipientId = recipientId,
                text        = "",
                stickers    = gifUrl
            )
            if (resp.apiStatus == 200) {
                Log.d(TAG_SENDERS, "GIF sent via Node.js: $gifUrl")
                error.value = null
                onSuccess()
            } else {
                error.value = resp.errorMessage ?: "Не вдалося надіслати GIF"
                Log.e(TAG_SENDERS, "sendGifViaNode error: ${resp.errorMessage}")
            }
        } catch (e: Exception) {
            error.value = "Помилка: ${e.localizedMessage}"
            Log.e(TAG_SENDERS, "sendGifViaNode exception", e)
        } finally {
            isLoading.value = false
        }
    }
}

// ─── sendLocation ─────────────────────────────────────────────────────────────

/**
 * Sends a geolocation message via Node.js.
 * Stored as native lat/lng fields (type = map in buildMessage).
 */
fun sendLocationViaNode(
    scope:       CoroutineScope,
    api:         NodeApi,
    recipientId: Long,
    lat:         String,
    lng:         String,
    address:     String,
    isLoading:   MutableStateFlow<Boolean>,
    error:       MutableStateFlow<String?>,
    onSuccess:   () -> Unit
) {
    scope.launch {
        isLoading.value = true
        try {
            val resp = api.sendMessage(
                recipientId = recipientId,
                text        = address.ifBlank { "" },
                lat         = lat,
                lng         = lng
            )
            if (resp.apiStatus == 200) {
                Log.d(TAG_SENDERS, "Location sent via Node.js: $lat,$lng")
                error.value = null
                onSuccess()
            } else {
                error.value = resp.errorMessage ?: "Не вдалося надіслати геолокацію"
                Log.e(TAG_SENDERS, "sendLocationViaNode error: ${resp.errorMessage}")
            }
        } catch (e: Exception) {
            error.value = "Помилка: ${e.localizedMessage}"
            Log.e(TAG_SENDERS, "sendLocationViaNode exception", e)
        } finally {
            isLoading.value = false
        }
    }
}

// ─── sendContact ──────────────────────────────────────────────────────────────

/**
 * Sends a contact (vCard) message via Node.js.
 * Stored in `contact` field with type_two = contact on server side.
 */
fun sendContactViaNode(
    scope:       CoroutineScope,
    api:         NodeApi,
    recipientId: Long,
    vCard:       String,
    isLoading:   MutableStateFlow<Boolean>,
    error:       MutableStateFlow<String?>,
    onSuccess:   () -> Unit
) {
    if (vCard.isBlank()) {
        error.value = "vCard не може бути порожнім"
        return
    }

    scope.launch {
        isLoading.value = true
        try {
            val resp = api.sendMessage(
                recipientId = recipientId,
                text        = "",
                contact     = vCard
            )
            if (resp.apiStatus == 200) {
                Log.d(TAG_SENDERS, "Contact sent via Node.js")
                error.value = null
                onSuccess()
            } else {
                error.value = resp.errorMessage ?: "Не вдалося надіслати контакт"
                Log.e(TAG_SENDERS, "sendContactViaNode error: ${resp.errorMessage}")
            }
        } catch (e: Exception) {
            error.value = "Помилка: ${e.localizedMessage}"
            Log.e(TAG_SENDERS, "sendContactViaNode exception", e)
        } finally {
            isLoading.value = false
        }
    }
}

// ─── forwardMessages (private chats) ─────────────────────────────────────────

/**
 * Forwards messages to private-chat recipients via Node.js.
 * Node.js decrypts and re-encrypts with the new timestamp — no key mismatch.
 *
 * Returns true if all forwards succeeded.
 */
fun forwardPrivateMsgsViaNode(
    scope:        CoroutineScope,
    api:          NodeApi,
    messageIds:   Set<Long>,
    recipientIds: List<Long>,
    onResult:     (allOk: Boolean) -> Unit
) {
    if (messageIds.isEmpty() || recipientIds.isEmpty()) return

    scope.launch {
        var allOk = true
        try {
            for (msgId in messageIds) {
                val idsCsv = recipientIds.joinToString(",")
                val resp   = api.forwardMessage(msgId, idsCsv)
                if (resp.apiStatus != 200) {
                    allOk = false
                    Log.w(TAG_SENDERS, "Forward msg $msgId failed: ${resp.errorMessage}")
                } else {
                    Log.d(TAG_SENDERS, "Forwarded msg $msgId to $idsCsv via Node.js")
                }
            }
        } catch (e: Exception) {
            allOk = false
            Log.e(TAG_SENDERS, "forwardPrivateMsgsViaNode exception", e)
        }
        onResult(allOk)
    }
}
