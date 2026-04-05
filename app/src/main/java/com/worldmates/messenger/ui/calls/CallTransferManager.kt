package com.worldmates.messenger.ui.calls

import android.util.Log
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * CallTransferManager — менеджер передачі активного дзвінка іншому користувачу.
 *
 * Підтримувані стани ([CallTransferState]):
 *   IDLE        → нема активного переносу
 *   INITIATING  → ініціатор відправив запит на передачу
 *   PENDING     → очікуємо відповідь від цільового користувача
 *   COMPLETED   → передача успішна; initiator від'єднується
 *   FAILED      → цільовий відхилив або не відповів
 *
 * Протокол Socket.IO (синхронізується з calls-listener.js):
 *   → call:transfer_initiate  { fromCallId, toUserId, fromUserId, roomName, callType }
 *   ← call:transfer_incoming  { fromUserId, fromUserName, fromUserAvatar, roomName, callType }
 *   → call:transfer_accept    { roomName, toUserId }
 *   → call:transfer_reject    { roomName, toUserId }
 *   ← call:transfer_accepted  { toUserId, toUserName }
 *   ← call:transfer_rejected  { toUserId, reason }
 *   ← call:transfer_timeout   { toUserId }
 *
 * Після успішної передачі:
 *   • Ініціатор завершує свою сесію (endCall() у CallsViewModel)
 *   • Цільовий отримує звонок як звичайний incoming call
 */

/** Можливі стани процесу передачі дзвінка. */
enum class CallTransferState {
    IDLE,
    INITIATING,
    PENDING,
    COMPLETED,
    FAILED,
}

/** Дані про контакт для передачі. */
data class TransferTarget(
    val userId: Long,
    val userName: String,
    val userAvatar: String?,
)

class CallTransferManager(private val socket: Socket) {

    companion object {
        private const val TAG = "CallTransferManager"
        /** Таймаут очікування відповіді (мс). */
        private const val TRANSFER_TIMEOUT_MS = 30_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── State ────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(CallTransferState.IDLE)
    val state: StateFlow<CallTransferState> = _state.asStateFlow()

    /** Ціль поточного переносу (для показу в UI). */
    private val _pendingTarget = MutableStateFlow<TransferTarget?>(null)
    val pendingTarget: StateFlow<TransferTarget?> = _pendingTarget.asStateFlow()

    /** Ім'я користувача, від якого прийшов incoming transfer (для IncomingCallActivity). */
    private val _incomingTransferFrom = MutableStateFlow<String?>(null)
    val incomingTransferFrom: StateFlow<String?> = _incomingTransferFrom.asStateFlow()

    // Callbacks, які виконуються у CallsViewModel
    var onTransferCompleted: (() -> Unit)? = null
    var onTransferFailed: ((reason: String) -> Unit)? = null
    var onIncomingTransfer: ((from: TransferTarget, roomName: String, callType: String) -> Unit)? = null

    // ─── Socket listener registration ────────────────────────────────────

    /**
     * Реєструє всі Socket.IO слухачі передачі.
     * Викликати одноразово при ініціалізації CallsViewModel.
     */
    fun registerListeners() {

        // ← Успішна передача: цільовий прийняв
        socket.on("call:transfer_accepted") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val toUserName = data.optString("toUserName", "")
            Log.d(TAG, "✅ Transfer accepted by $toUserName")
            _state.value = CallTransferState.COMPLETED
            onTransferCompleted?.invoke()
        }

        // ← Передача відхилена або таймаут
        socket.on("call:transfer_rejected") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val reason = data.optString("reason", "rejected")
            Log.d(TAG, "❌ Transfer rejected: $reason")
            _state.value = CallTransferState.FAILED
            onTransferFailed?.invoke(reason)
            resetAfterDelay()
        }

        socket.on("call:transfer_timeout") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val toUserName = data.optString("toUserName", "")
            Log.d(TAG, "⏱️ Transfer timeout for $toUserName")
            _state.value = CallTransferState.FAILED
            onTransferFailed?.invoke("timeout")
            resetAfterDelay()
        }

        // ← Вхідний transfer для іншого боку (той, кому передають)
        socket.on("call:transfer_incoming") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val fromUserId   = data.optLong("fromUserId")
            val fromUserName = data.optString("fromUserName", "")
            val fromAvatar   = data.optString("fromUserAvatar", null)
            val roomName     = data.optString("roomName", "")
            val callType     = data.optString("callType", "video")

            Log.d(TAG, "📲 Incoming transfer from $fromUserName (room: $roomName)")
            _incomingTransferFrom.value = fromUserName

            val target = TransferTarget(fromUserId, fromUserName, fromAvatar)
            onIncomingTransfer?.invoke(target, roomName, callType)
        }
    }

    /**
     * Знімає всі Socket.IO слухачі передачі.
     * Викликати при знищенні CallsViewModel / завершенні дзвінка.
     */
    fun unregisterListeners() {
        socket.off("call:transfer_accepted")
        socket.off("call:transfer_rejected")
        socket.off("call:transfer_timeout")
        socket.off("call:transfer_incoming")
    }

    // ─── Initiate transfer ────────────────────────────────────────────────

    /**
     * Ініціює передачу поточного дзвінка [target]-у.
     *
     * @param roomName   Кімната поточного дзвінка
     * @param callType   "video" або "audio"
     * @param fromUserId ID ініціатора
     */
    fun initiateTransfer(
        target: TransferTarget,
        roomName: String,
        callType: String,
        fromUserId: Long
    ) {
        if (_state.value != CallTransferState.IDLE) {
            Log.w(TAG, "⚠️ Transfer already in progress, ignoring")
            return
        }

        Log.d(TAG, "📤 Initiating transfer to ${target.userName} (room: $roomName)")
        _state.value = CallTransferState.INITIATING
        _pendingTarget.value = target

        val payload = JSONObject().apply {
            put("toUserId", target.userId)
            put("fromUserId", fromUserId)
            put("roomName", roomName)
            put("callType", callType)
        }

        socket.emit("call:transfer_initiate", payload)
        _state.value = CallTransferState.PENDING

        // Локальний таймаут як fallback (сервер теж має свій)
        scope.launch {
            delay(TRANSFER_TIMEOUT_MS + 5000L)
            if (_state.value == CallTransferState.PENDING) {
                Log.w(TAG, "⏱️ Local transfer timeout")
                _state.value = CallTransferState.FAILED
                onTransferFailed?.invoke("timeout")
                resetAfterDelay()
            }
        }
    }

    // ─── Accept / Reject (для цільового користувача) ─────────────────────

    /**
     * Цільовий користувач приймає переданий дзвінок.
     */
    fun acceptTransfer(roomName: String, fromUserId: Long) {
        val payload = JSONObject().apply {
            put("roomName", roomName)
            put("fromUserId", fromUserId)
        }
        socket.emit("call:transfer_accept", payload)
        Log.d(TAG, "✅ Transfer accepted, joining room: $roomName")
    }

    /**
     * Цільовий користувач відхиляє переданий дзвінок.
     */
    fun rejectTransfer(roomName: String, fromUserId: Long) {
        val payload = JSONObject().apply {
            put("roomName", roomName)
            put("fromUserId", fromUserId)
        }
        socket.emit("call:transfer_reject", payload)
        Log.d(TAG, "❌ Transfer rejected")
        _incomingTransferFrom.value = null
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    /** Скидає стан до IDLE через 3 секунди (щоб показати UI фідбек). */
    private fun resetAfterDelay() {
        scope.launch {
            delay(3_000L)
            _state.value = CallTransferState.IDLE
            _pendingTarget.value = null
        }
    }

    /** Примусово скидає стан (виклик при завершенні дзвінка). */
    fun reset() {
        _state.value = CallTransferState.IDLE
        _pendingTarget.value = null
        _incomingTransferFrom.value = null
    }
}
