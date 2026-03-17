package com.worldmates.messenger.data.repository

import android.util.Log
import com.worldmates.messenger.data.local.AppDatabase
import com.worldmates.messenger.data.local.entity.OutgoingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * OutgoingMessageQueue — офлайн-черга вихідних повідомлень.
 *
 * Використання:
 *  1. Перед відправкою через сокет перевірте isConnected.
 *     Якщо false → [enqueue] зберігає в Room і повернеться одразу.
 *  2. При [Socket.EVENT_CONNECT] або "reconnect" — викличте [flush].
 *  3. [flush] проходить по всіх pending записах і відправляє через [sender].
 *
 * Потокобезпека: всі операції з БД виконуються в IO-dispatcher.
 */
class OutgoingMessageQueue(db: AppDatabase) {

    private val dao   = db.outgoingMessageDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "OutgoingMessageQueue"
    }

    /** Кількість pending повідомлень у черзі (для UI-індикатора) */
    val pendingCountFlow: Flow<Int> = dao.getPendingCountFlow()

    /**
     * Помістити повідомлення в чергу (зберігається в Room).
     * Викликається коли сокет недоступний.
     */
    suspend fun enqueue(
        chatType: String,
        chatId: Long,
        plaintext: String,
        replyId: Long = 0,
        stickers: String = ""
    ): Long {
        val msg = OutgoingMessage(
            chatType = chatType,
            chatId   = chatId,
            plaintext = plaintext,
            replyId  = replyId,
            stickers = stickers
        )
        val id = dao.enqueue(msg)
        Log.d(TAG, "Enqueued message id=$id chatType=$chatType chatId=$chatId")
        return id
    }

    /**
     * Відправити всі pending повідомлення з черги.
     * Викликати після успішного підключення Socket.IO.
     *
     * @param sender лямбда яка відправляє одне повідомлення.
     *               Повертає true при успіху, false при помилці.
     */
    fun flush(sender: suspend (OutgoingMessage) -> Boolean) {
        scope.launch {
            val pending = dao.getPendingMessages()
            if (pending.isEmpty()) return@launch

            Log.d(TAG, "Flushing ${pending.size} queued messages…")

            for (msg in pending) {
                dao.updateStatus(msg.id, OutgoingMessage.STATUS_SENDING)
                val success = try {
                    sender(msg)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send queued msg ${msg.id}: ${e.message}")
                    false
                }

                if (success) {
                    dao.delete(msg.id)
                    Log.d(TAG, "Queued msg ${msg.id} sent and removed from queue")
                } else {
                    dao.incrementRetry(msg.id)
                    Log.w(TAG, "Queued msg ${msg.id} failed (retry=${msg.retryCount + 1}/${OutgoingMessage.MAX_RETRIES})")
                }
            }

            // Clean up permanently failed messages
            dao.clearFailed()
        }
    }
}
