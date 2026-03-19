package com.worldmates.messenger.utils.signal

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.worldmates.messenger.network.NodeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * High-level service for Signal Protocol (Double Ratchet) message encryption.
 *
 * Responsibilities:
 *   1. Ensure local keys are registered with the server (once per install).
 *   2. Establish a DR session with a remote user when needed (X3DH).
 *   3. Encrypt outgoing messages using DR.
 *   4. Decrypt incoming messages using DR.
 *   5. Replenish one-time pre-keys automatically when running low.
 *
 * Thread safety: all public functions are `suspend` and run on [Dispatchers.Default].
 * Session state updates are serialised through [SignalKeyStore] synchronized methods.
 */
class SignalEncryptionService private constructor(
    private val context: Context,
    private val nodeApi: NodeApi
) {
    private val TAG         = "SignalEncSvc"
    private val keyStore    = SignalKeyStore(context)
    private val gson        = Gson()

    /**
     * Per-sender mutex — serialises all crypto operations for a given remote user.
     * Prevents race conditions when the same message is processed by two coroutines
     * simultaneously (e.g. Socket.IO event + API message-list reload):
     *   – Thread A: consumeOPK(5) → gets key → saves correct session S1
     *   – Thread B: consumeOPK(5) → null (already consumed) → saves wrong session S2
     *   → S2 overwrites S1, both decrypt attempts fail with BAD_DECRYPT.
     * With the mutex only one coroutine runs at a time per sender.
     */
    private val senderLocks = ConcurrentHashMap<Long, Mutex>()

    companion object {
        const val CIPHER_VERSION_SIGNAL = 3

        @Volatile private var INSTANCE: SignalEncryptionService? = null

        fun getInstance(context: Context, nodeApi: NodeApi): SignalEncryptionService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SignalEncryptionService(context.applicationContext, nodeApi)
                    .also { INSTANCE = it }
            }
    }

    // ─── Initialisation ───────────────────────────────────────────────────────

    /**
     * Ensure our identity + pre-key bundle is registered with the server.
     * Safe to call on every app launch — skips if already registered.
     */
    suspend fun ensureRegistered() = withContext(Dispatchers.Default) {
        if (keyStore.isRegistered()) return@withContext
        try {
            val ik                  = keyStore.getOrCreateIdentityKey()
            val (spkId, spkKp, sig) = keyStore.getOrCreateSignedPreKey()
            val opkBatch            = keyStore.generateAndSaveOPKBatch()

            val preKeysJson = gson.toJson(opkBatch.map { mapOf("id" to it.id, "key" to it.pub) })
            val resp = nodeApi.registerSignalKeys(
                identityKey     = Base64.encodeToString(ik.publicKey,    Base64.NO_WRAP),
                signedPreKeyId  = spkId,
                signedPreKey    = Base64.encodeToString(spkKp.publicKey, Base64.NO_WRAP),
                signedPreKeySig = Base64.encodeToString(sig,             Base64.NO_WRAP),
                prekeys         = preKeysJson
            )
            if (resp.apiStatus == 200) {
                keyStore.setRegistered(true)
                Log.i(TAG, "Signal keys registered (OPKs: ${opkBatch.size})")
            } else {
                Log.e(TAG, "Failed to register Signal keys: ${resp.errorMessage}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureRegistered error", e)
        }
    }

    // ─── Encrypt outgoing message ─────────────────────────────────────────────

    /**
     * Encrypt [plaintext] for [recipientId] using Double Ratchet.
     *
     * If no DR session exists, performs X3DH first:
     *   1. Fetches recipient's pre-key bundle.
     *   2. Computes X3DH shared secret.
     *   3. Initialises DR session.
     *
     * @return [SignalOutgoingPayload] for POST /api/node/chat/send, or null on error.
     */
    suspend fun encryptForSend(
        recipientId: Long,
        plaintext:   String
    ): SignalOutgoingPayload? = withContext(Dispatchers.Default) {
        try {
            var session    = keyStore.loadSession(recipientId)
            var x3dhHeader: X3DHHeader? = null

            // ── Stale session detection ───────────────────────────────────────
            // If we have a cached session, verify the remote user's identity key
            // still matches the server. If the remote user reinstalled the app and
            // the signal:identity_changed socket event was missed (e.g. device
            // was offline), our session is stale and will produce undecryptable
            // ciphertext on the recipient side. This check uses a lightweight
            // endpoint that does NOT consume any one-time pre-key.
            if (session != null) {
                val remoteIkB64 = extractRemoteIkB64FromSession(session)
                val serverIkB64 = fetchRemoteIdentityKey(recipientId)
                if (serverIkB64 != null && remoteIkB64 != null && serverIkB64 != remoteIkB64) {
                    Log.w(TAG, "Remote IK changed for $recipientId — clearing stale session and re-keying")
                    keyStore.deleteSession(recipientId)
                    session = null
                    // Fall through to fresh X3DH below
                }
            }

            // ── X3DH session establishment ────────────────────────────────────
            if (session == null) {
                val bundle = fetchPreKeyBundle(recipientId)
                    ?: return@withContext null.also {
                        Log.e(TAG, "No pre-key bundle for user $recipientId")
                    }

                val ik   = keyStore.getOrCreateIdentityKey()
                val ekA  = DoubleRatchetManager.generateKeyPair()

                val (sk, ad) = DoubleRatchetManager.x3dhAlice(
                    ikA     = ik,
                    ikBPub  = bundle.identityKey,
                    spkBPub = bundle.signedPreKey,
                    opkBPub = bundle.oneTimePreKey,
                    ekA     = ekA
                )
                session = DoubleRatchetManager.initAliceSession(
                    sk           = sk,
                    ad           = ad,
                    spkBPub      = bundle.signedPreKey,
                    remoteUserId = recipientId
                )
                x3dhHeader = X3DHHeader(
                    identityKey     = Base64.encodeToString(ik.publicKey,  Base64.NO_WRAP),
                    ephemeralKey    = Base64.encodeToString(ekA.publicKey, Base64.NO_WRAP),
                    oneTimePreKeyId = bundle.oneTimePreKeyId
                )
                Log.i(TAG, "X3DH completed for user $recipientId opk=${bundle.oneTimePreKeyId}")
            }

            // ── DR encrypt ────────────────────────────────────────────────────
            val (newSession, encMsg) = DoubleRatchetManager.ratchetEncrypt(
                state     = session,
                plaintext = plaintext.toByteArray(Charsets.UTF_8)
            )
            keyStore.saveSession(recipientId, newSession)

            // Replenish OPKs quietly in background if running low
            if (keyStore.opkCount() < SignalKeyStore.OPK_REPLENISH_LOW) {
                replenishOPKsSilently()
            }

            SignalOutgoingPayload(
                ciphertext   = Base64.encodeToString(encMsg.ciphertext, Base64.NO_WRAP),
                iv           = Base64.encodeToString(encMsg.iv,         Base64.NO_WRAP),
                tag          = Base64.encodeToString(encMsg.tag,        Base64.NO_WRAP),
                signalHeader = buildHeaderJson(encMsg.header, x3dhHeader)
            )
        } catch (e: Exception) {
            Log.e(TAG, "encryptForSend error for user $recipientId", e)
            null
        }
    }

    // ─── Decrypt incoming message ─────────────────────────────────────────────

    /**
     * Decrypt an incoming cipher_version=3 message.
     *
     * If the signal_header contains X3DH fields (first message from a new sender),
     * performs X3DH and initialises a new DR session before decrypting.
     *
     * Thread safety: serialised per sender via [senderLocks]. Concurrent calls for
     * the same [senderId] (e.g. Socket.IO event + API reload arriving simultaneously)
     * are queued rather than executed in parallel, preventing OPK double-consumption
     * and session state corruption.  The [msgId] cache check inside the lock also
     * ensures the same message is never decrypted twice even when called from
     * multiple coroutines.
     *
     * @param msgId  Unique message ID — used to skip re-decryption if the result is
     *               already in the plaintext cache (the second coroutine in the queue
     *               will just return the cached value without touching the session).
     * @return Decrypted plaintext, or null on auth/session failure.
     */
    suspend fun decryptIncoming(
        senderId:         Long,
        msgId:            Long = 0L,  // 0 = no caching (e.g. distribution payloads)
        ciphertextB64:    String,
        ivB64:            String,
        tagB64:           String,
        signalHeaderJson: String
    ): String? = withContext(Dispatchers.Default) {
        val lock = senderLocks.getOrPut(senderId) { Mutex() }
        lock.withLock {
            try {
                // ── Cache check (inside lock) ─────────────────────────────────
                // If another coroutine already decrypted this message while we were
                // waiting for the lock, return the cached plaintext immediately.
                // DR session keys are one-time: re-decrypting advances (or breaks) the chain.
                if (msgId > 0L) {
                    val cached = keyStore.getCachedDecryptedMessage(msgId)
                    if (cached != null) {
                        Log.d(TAG, "📱 [Signal] msg $msgId from cache (inside lock — duplicate call)")
                        return@withLock cached
                    }
                }

                @Suppress("UNCHECKED_CAST")
                val h = gson.fromJson(signalHeaderJson, Map::class.java) as Map<String, Any>

                val ratchetKey = Base64.decode(
                    h["rk"] as? String
                        ?: return@withLock null.also { Log.e(TAG, "Missing rk") },
                    Base64.NO_WRAP
                )
                val n  = (h["n"]  as? Double)?.toInt() ?: 0
                val pn = (h["pn"] as? Double)?.toInt() ?: 0

                // ── X3DH on first incoming message (or re-key after sender reinstall) ──
                // Always reinitialize when 'ik' is present — even if we have a stale session.
                // Presence of 'ik' means the sender started a fresh X3DH (e.g. after reinstall).
                // Keeping the old session and trying to decrypt with it will always fail.
                if (h.containsKey("ik")) {
                    val ikAPub = Base64.decode(h["ik"] as String, Base64.NO_WRAP)
                    val ekAPub = Base64.decode(h["ek"] as? String
                        ?: return@withLock null.also { Log.e(TAG, "Missing ek") },
                        Base64.NO_WRAP)
                    val opkId = (h["opk_id"] as? Double)?.toInt()

                    val ik              = keyStore.getOrCreateIdentityKey()
                    val (_, spkKp, _)   = keyStore.getOrCreateSignedPreKey()
                    val opkKp           = opkId?.let { keyStore.consumeOPK(it) }

                    // Delete stale session before creating a new one
                    if (keyStore.hasSession(senderId)) {
                        keyStore.deleteSession(senderId)
                        Log.i(TAG, "X3DH(Bob) clearing stale session for $senderId before re-keying")
                    }

                    val (sk, ad) = DoubleRatchetManager.x3dhBob(
                        ikB    = ik,
                        spkB   = spkKp,
                        opkB   = opkKp,
                        ikAPub = ikAPub,
                        ekAPub = ekAPub
                    )
                    val session = DoubleRatchetManager.initBobSession(
                        sk           = sk,
                        ad           = ad,
                        spkB         = spkKp,
                        ekAPub       = ekAPub,
                        remoteUserId = senderId
                    )
                    keyStore.saveSession(senderId, session)
                    Log.i(TAG, "X3DH(Bob) completed for sender $senderId")
                }

                // ── DR decrypt ────────────────────────────────────────────────
                val session = keyStore.loadSession(senderId)
                    ?: return@withLock null.also {
                        Log.e(TAG, "No DR session for sender $senderId")
                    }

                val encMsg = EncryptedDRMessage(
                    header     = DRHeader(ratchetKey = ratchetKey, n = n, pn = pn),
                    ciphertext = Base64.decode(ciphertextB64, Base64.NO_WRAP),
                    iv         = Base64.decode(ivB64,         Base64.NO_WRAP),
                    tag        = Base64.decode(tagB64,        Base64.NO_WRAP)
                )

                val (newSession, plainBytes) = DoubleRatchetManager.ratchetDecrypt(session, encMsg)
                keyStore.saveSession(senderId, newSession)
                val plainText = String(plainBytes, Charsets.UTF_8)

                // Cache immediately inside the lock so any queued coroutine for the
                // same msgId gets a cache hit and skips re-decryption.
                if (msgId > 0L) keyStore.cacheDecryptedMessage(msgId, plainText)
                plainText

            } catch (e: Exception) {
                // AEADBadTagException = session desync (keys don't match).
                // Clear the stale session so the next outgoing message triggers a fresh
                // X3DH key-agreement, re-synchronising both sides automatically.
                val isBadTag = e is javax.crypto.AEADBadTagException ||
                               e.cause is javax.crypto.AEADBadTagException
                if (isBadTag) {
                    Log.w(TAG, "decryptIncoming BAD_DECRYPT from $senderId — " +
                        "session desync detected, clearing session for re-key on next send")
                    try { keyStore.deleteSession(senderId) } catch (ignored: Exception) {}
                } else {
                    Log.e(TAG, "decryptIncoming error from $senderId", e)
                }
                null
            }
        }
    }

    // ─── Plaintext cache (delegate to keyStore → Room DB) ────────────────────

    /**
     * Persist [plaintext] for [msgId] in Room DB so it survives reinstall
     * (Android Auto Backup includes the Room database).
     */
    suspend fun cacheDecryptedMessage(msgId: Long, plaintext: String) =
        keyStore.cacheDecryptedMessage(msgId, plaintext)

    /** Returns cached plaintext for [msgId], or null if not cached. */
    suspend fun getCachedDecryptedMessage(msgId: Long): String? =
        keyStore.getCachedDecryptedMessage(msgId)

    // ─── Identity key fetch (lightweight — no OPK consumed) ──────────────────

    /**
     * Fetch the remote user's identity key without consuming any OPK.
     * Returns Base64-encoded identity key, or null on error.
     */
    private suspend fun fetchRemoteIdentityKey(userId: Long): String? = try {
        val resp = nodeApi.getSignalIdentityKey(userId)
        if (resp.apiStatus == 200) resp.identityKey else null
    } catch (e: Exception) {
        Log.w(TAG, "fetchRemoteIdentityKey error for $userId (non-fatal): ${e.message}")
        null  // Don't invalidate session on network error — only on confirmed IK change
    }

    /**
     * Extract the remote user's identity public key (Base64) from a stored session.
     *
     * The session's associatedData = Encode(IK_A) || Encode(IK_B) (64 bytes, Base64):
     *   – If we are the initiator (Alice): IK_A = ours [0..32), IK_B = remote [32..64)
     *   – If we are the responder (Bob):   IK_A = remote [0..32), IK_B = ours [32..64)
     */
    private fun extractRemoteIkB64FromSession(session: SessionState): String? = try {
        val ad = Base64.decode(session.associatedData, Base64.NO_WRAP)
        if (ad.size < 64) return null
        val remoteIkBytes = if (session.isInitiator) ad.copyOfRange(32, 64) else ad.copyOfRange(0, 32)
        Base64.encodeToString(remoteIkBytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.w(TAG, "extractRemoteIkB64FromSession failed: ${e.message}")
        null
    }

    // ─── Pre-key bundle fetch ─────────────────────────────────────────────────

    private suspend fun fetchPreKeyBundle(userId: Long): PreKeyBundle? = try {
        val resp = nodeApi.getSignalBundle(userId)
        if (resp.apiStatus == 200 && resp.identityKey != null && resp.signedPreKey != null) {
            PreKeyBundle(
                identityKey     = Base64.decode(resp.identityKey,    Base64.NO_WRAP),
                signedPreKeyId  = resp.signedPreKeyId  ?: 0,
                signedPreKey    = Base64.decode(resp.signedPreKey,   Base64.NO_WRAP),
                signedPreKeySig = resp.signedPreKeySig
                    ?.let { Base64.decode(it, Base64.NO_WRAP) } ?: ByteArray(64),
                oneTimePreKeyId = resp.oneTimePreKeyId,
                oneTimePreKey   = resp.oneTimePreKey
                    ?.let { Base64.decode(it, Base64.NO_WRAP) }
            )
        } else {
            Log.e(TAG, "Bundle fetch failed for $userId: ${resp.errorMessage}")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "fetchPreKeyBundle error for $userId", e)
        null
    }

    // ─── OPK replenishment ────────────────────────────────────────────────────

    private suspend fun replenishOPKsSilently() {
        try {
            val batch        = keyStore.generateAndSaveOPKBatch()
            val preKeysJson  = gson.toJson(batch.map { mapOf("id" to it.id, "key" to it.pub) })
            val resp         = nodeApi.replenishSignalPreKeys(prekeys = preKeysJson)
            if (resp.apiStatus == 200) Log.i(TAG, "Replenished ${batch.size} OPKs")
        } catch (e: Exception) {
            Log.w(TAG, "OPK replenishment failed (non-critical): ${e.message}")
        }
    }

    // ─── JSON helpers ─────────────────────────────────────────────────────────

    private fun buildHeaderJson(header: DRHeader, x3dh: X3DHHeader?): String {
        val map = linkedMapOf<String, Any>(
            "rk" to Base64.encodeToString(header.ratchetKey, Base64.NO_WRAP),
            "n"  to header.n,
            "pn" to header.pn
        )
        if (x3dh != null) {
            map["ik"] = x3dh.identityKey
            map["ek"] = x3dh.ephemeralKey
            x3dh.oneTimePreKeyId?.let { map["opk_id"] = it }
        }
        return gson.toJson(map)
    }

    // ─── Result types ─────────────────────────────────────────────────────────

    private data class X3DHHeader(
        val identityKey:     String,
        val ephemeralKey:    String,
        val oneTimePreKeyId: Int?
    )

    /** Payload ready to POST to /api/node/chat/send as cipher_version=3. */
    data class SignalOutgoingPayload(
        val ciphertext:   String,  // Base64(ciphertext)
        val iv:           String,  // Base64(IV, 12 bytes)
        val tag:          String,  // Base64(auth tag, 16 bytes)
        val signalHeader: String   // JSON DR+X3DH header
    )
}
