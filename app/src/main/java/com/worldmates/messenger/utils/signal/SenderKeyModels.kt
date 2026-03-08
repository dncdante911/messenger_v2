package com.worldmates.messenger.utils.signal

import com.google.gson.annotations.SerializedName

// ─── Sender Key state (зберігається локально для кожного (groupId, senderId)) ──

/**
 * Стан SenderKey одного учасника групи.
 *
 * Для ВІДПРАВНИКА: (groupId → мій власний стан)
 *   chainKey просувається з кожним відправленим повідомленням.
 *
 * Для ОДЕРЖУВАЧА: (groupId, senderId → їх стан)
 *   chainKey просувається з кожним прийнятим повідомленням від цього відправника.
 *
 * Серіалізується через Gson → зберігається в EncryptedSharedPreferences.
 */
data class SenderKeyState(
    /** Поточний chain key (Base64, 32 bytes). Просувається HMAC-SHA256(ck, 0x02). */
    val chainKey:  String,
    /** Стабільний ідентифікатор ланцюжка (Base64, 16 bytes). Не змінюється. */
    val chainId:   String,
    /** Лічильник повідомлень, відправлених/отриманих по цьому ланцюжку. */
    val counter:   Int = 0
)

// ─── Payload для SenderKey distribution ──────────────────────────────────────

/**
 * Вміст SenderKey distribution — серіалізується в JSON, потім шифрується
 * через Double Ratchet для конкретного одержувача і відправляється на сервер.
 *
 * Сервер бачить тільки Base64-зашифрований blob — вміст недоступний.
 */
data class SenderKeyDistributionPayload(
    @SerializedName("chain_key") val chainKey: String,   // Base64 current chain key
    @SerializedName("chain_id")  val chainId:  String,   // Base64 chain ID
    @SerializedName("counter")   val counter:  Int = 0   // Starting message counter
)

// ─── Signal header для групових повідомлень ───────────────────────────────────

/**
 * JSON-заголовок, що зберігається у полі `signal_header` групового повідомлення.
 * Одержувач використовує його для пошуку правильного SenderKey і позиції в ланцюжку.
 */
data class GroupSignalHeader(
    @SerializedName("gid") val groupId:  Long,    // ID групи
    @SerializedName("sid") val senderId: Long,    // ID відправника (для пошуку SenderKey)
    @SerializedName("cid") val chainId:  String,  // Base64 chain ID (для верифікації)
    @SerializedName("n")   val counter:  Int      // Номер повідомлення в ланцюжку
)

// ─── Server API models ────────────────────────────────────────────────────────

/** POST /api/node/signal/group/distribute */
data class SignalGroupDistributeResponse(
    @SerializedName("api_status")    val apiStatus:    Int,
    @SerializedName("saved")         val saved:         Int?    = null,
    @SerializedName("message")       val message:       String? = null,
    @SerializedName("error_message") val errorMessage:  String? = null
)

/** Елемент масиву distributions у запиті на розподіл. */
data class SignalGroupDistributionItem(
    @SerializedName("recipient_id")  val recipientId:  Long,
    @SerializedName("distribution")  val distribution: String   // Base64 зашифрованого payload
)

/** Елемент відповіді GET /api/node/signal/group/pending-distributions. */
data class SignalGroupPendingItem(
    @SerializedName("id")          val id:          Long,
    @SerializedName("group_id")    val groupId:     Long,
    @SerializedName("sender_id")   val senderId:    Long,
    @SerializedName("distribution") val distribution: String,   // Base64 зашифрований payload
    @SerializedName("created_at")  val createdAt:   String? = null
)

/** GET /api/node/signal/group/pending-distributions */
data class SignalGroupPendingResponse(
    @SerializedName("api_status")    val apiStatus:     Int,
    @SerializedName("distributions") val distributions: List<SignalGroupPendingItem>? = null,
    @SerializedName("count")         val count:         Int?    = null,
    @SerializedName("error_message") val errorMessage:  String? = null
)

/** POST /api/node/signal/group/confirm-delivery */
data class SignalGroupConfirmResponse(
    @SerializedName("api_status")    val apiStatus:   Int,
    @SerializedName("confirmed")     val confirmed:   Int?    = null,
    @SerializedName("message")       val message:     String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)
