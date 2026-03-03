package com.worldmates.messenger.utils.signal

import com.google.gson.annotations.SerializedName

// ─── Local key containers ──────────────────────────────────────────────────────

/** Raw X25519 key pair (32-byte scalars, BouncyCastle lightweight format). */
data class X25519KeyPair(
    val privateKey: ByteArray,  // 32 bytes
    val publicKey:  ByteArray   // 32 bytes
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X25519KeyPair) return false
        return privateKey.contentEquals(other.privateKey) &&
               publicKey.contentEquals(other.publicKey)
    }
    override fun hashCode(): Int =
        privateKey.contentHashCode() * 31 + publicKey.contentHashCode()
}

// ─── Double Ratchet session state (persisted as JSON) ─────────────────────────

/**
 * Complete Double Ratchet session state.
 * Serialised via Gson → stored in EncryptedSharedPreferences.
 * All byte arrays are Base64-encoded strings (NO_WRAP).
 */
data class SessionState(
    /** DHs private key (Base64, 32 bytes). */
    val dhSendPriv: String,
    /** DHs public key (Base64, 32 bytes). */
    val dhSendPub: String,
    /** DHr — remote ratchet public key (Base64, 32 bytes). Null before first receive (Bob). */
    val dhRecvPub: String?,
    /** Root key RK (Base64, 32 bytes). */
    val rootKey: String,
    /** Chain key for sending CKs (Base64, 32 bytes). Null before first send (Bob). */
    val chainKeySend: String?,
    /** Chain key for receiving CKr (Base64, 32 bytes). Null before first receive. */
    val chainKeyRecv: String?,
    /** Message counter — sending chain. */
    val sendN: Int = 0,
    /** Message counter — receiving chain. */
    val recvN: Int = 0,
    /** Previous chain length PN. */
    val prevChainLen: Int = 0,
    /**
     * Skipped message keys.
     * Key:   "<ratchet_pub_hex>:<message_number>"
     * Value: Base64(messageKey)
     */
    val skippedKeys: Map<String, String> = emptyMap(),
    /**
     * Associated data AD = Encode(IK_A) || Encode(IK_B) (64 bytes, Base64).
     * Used as AEAD header in every message.
     */
    val associatedData: String = "",
    /** true = local device initiated the session (Alice role). */
    val isInitiator: Boolean = true,
    /** Remote user ID — used as lookup key in SignalKeyStore. */
    val remoteUserId: Long = 0
)

// ─── DR message header ─────────────────────────────────────────────────────────

/**
 * Double Ratchet message header transmitted alongside the ciphertext.
 *
 * For the FIRST message from Alice to Bob the X3DH fields (ik, ek, opk_id)
 * are also included so Bob can perform key agreement on first receive.
 */
data class DRHeader(
    /** Sender's current ratchet public key (32 bytes). */
    val ratchetKey: ByteArray,
    /** Message number in current sending chain. */
    val n: Int,
    /** Previous chain length (for skipped-message recovery). */
    val pn: Int,
    // ── X3DH fields (first message only) ────────────────────────────────────
    /** Initiator's identity public key (32 bytes, null after first message). */
    val identityKey: ByteArray? = null,
    /** Initiator's ephemeral public key (32 bytes, null after first message). */
    val ephemeralKey: ByteArray? = null,
    /** One-time pre-key ID used by initiator (null if no OPK available). */
    val oneTimePreKeyId: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DRHeader) return false
        return ratchetKey.contentEquals(other.ratchetKey) && n == other.n && pn == other.pn
    }
    override fun hashCode(): Int = ratchetKey.contentHashCode() * 31 + n
}

/** Result of a Double Ratchet encrypt operation. */
data class EncryptedDRMessage(
    val header:     DRHeader,
    val ciphertext: ByteArray,  // AES-256-GCM encrypted plaintext
    val iv:         ByteArray,  // 12-byte random nonce
    val tag:        ByteArray   // 16-byte GCM auth tag
)

// ─── Server API models ─────────────────────────────────────────────────────────

/** Pre-key bundle fetched from server for a remote user. */
data class PreKeyBundle(
    val identityKey:      ByteArray,
    val signedPreKeyId:   Int,
    val signedPreKey:     ByteArray,
    val signedPreKeySig:  ByteArray,
    val oneTimePreKeyId:  Int?,
    val oneTimePreKey:    ByteArray?
)

/** One-time pre-key in JSON form (for API requests). */
data class OneTimePreKeyJson(
    @SerializedName("id")  val id:  Int,
    @SerializedName("key") val key: String  // Base64 public key
)

/** POST /api/node/signal/register */
data class SignalRegisterRequest(
    @SerializedName("identity_key")      val identityKey:     String,
    @SerializedName("signed_prekey_id")  val signedPreKeyId:  Int,
    @SerializedName("signed_prekey")     val signedPreKey:    String,
    @SerializedName("signed_prekey_sig") val signedPreKeySig: String,
    @SerializedName("prekeys")           val prekeys:         List<OneTimePreKeyJson>
)

/** GET /api/node/signal/bundle/:userId */
data class PreKeyBundleResponse(
    @SerializedName("api_status")           val apiStatus:        Int,
    @SerializedName("user_id")              val userId:           Long?   = null,
    @SerializedName("identity_key")         val identityKey:      String? = null,
    @SerializedName("signed_prekey_id")     val signedPreKeyId:   Int?    = null,
    @SerializedName("signed_prekey")        val signedPreKey:     String? = null,
    @SerializedName("signed_prekey_sig")    val signedPreKeySig:  String? = null,
    @SerializedName("one_time_prekey_id")   val oneTimePreKeyId:  Int?    = null,
    @SerializedName("one_time_prekey")      val oneTimePreKey:    String? = null,
    @SerializedName("remaining_prekeys")    val remainingPreKeys: Int?    = null,
    @SerializedName("error_message")        val errorMessage:     String? = null
)

/** POST /api/node/signal/replenish */
data class SignalReplenishRequest(
    @SerializedName("prekeys") val prekeys: List<OneTimePreKeyJson>
)

/** Generic Signal API response. */
data class SignalSimpleResponse(
    @SerializedName("api_status")    val apiStatus:    Int,
    @SerializedName("message")       val message:      String? = null,
    @SerializedName("count")         val count:        Int?    = null,
    @SerializedName("error_message") val errorMessage: String? = null
)
