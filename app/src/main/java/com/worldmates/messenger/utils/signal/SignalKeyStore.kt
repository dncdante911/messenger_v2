package com.worldmates.messenger.utils.signal

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.SecureRandom

/**
 * Persistent storage for all local Signal Protocol keys.
 *
 * Uses EncryptedSharedPreferences (backed by Android Keystore AES-256-GCM master key)
 * so private keys are encrypted at rest and never leave the device.
 *
 * Stores:
 *  – Identity key pair (X25519, generated once on first use)
 *  – Signed pre-key pair + ID + dummy signature
 *  – Pool of one-time pre-keys (generated in batches of [OPK_BATCH_SIZE])
 *  – Active DR session states (one per remote user ID)
 *  – Registration flag (tracks whether keys have been uploaded to server)
 */
class SignalKeyStore(private val context: Context) {

    private val TAG  = "SignalKeyStore"
    private val gson = Gson()

    companion object {
        private const val PREF_FILE           = "wm_signal_keys"
        private const val KEY_IK_PRIV         = "ik_priv"
        private const val KEY_IK_PUB          = "ik_pub"
        private const val KEY_SPK_ID          = "spk_id"
        private const val KEY_SPK_PRIV        = "spk_priv"
        private const val KEY_SPK_PUB         = "spk_pub"
        private const val KEY_SPK_SIG         = "spk_sig"
        private const val KEY_OPK_POOL        = "opk_pool"
        private const val KEY_REGISTERED      = "signal_registered"
        private const val KEY_NEXT_OPK_ID     = "next_opk_id"
        private const val KEY_PLAIN_PREFIX    = "plain_"

        const val OPK_BATCH_SIZE     = 100
        const val OPK_REPLENISH_LOW  = 20   // Replenish when below this count
    }

    // ─── EncryptedSharedPreferences (lazy, thread-safe via @Synchronized) ────

    private val prefs by lazy {
        val mk = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, PREF_FILE, mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ─── Identity key ─────────────────────────────────────────────────────────

    /**
     * Returns our X25519 identity key pair.
     * Generates and persists a new one on first call (once per app installation).
     */
    @Synchronized
    fun getOrCreateIdentityKey(): X25519KeyPair {
        val privB64 = prefs.getString(KEY_IK_PRIV, null)
        if (privB64 != null) {
            return X25519KeyPair(
                privateKey = Base64.decode(privB64, Base64.NO_WRAP),
                publicKey  = Base64.decode(prefs.getString(KEY_IK_PUB, "")!!, Base64.NO_WRAP)
            )
        }
        val kp = DoubleRatchetManager.generateKeyPair()
        prefs.edit()
            .putString(KEY_IK_PRIV, Base64.encodeToString(kp.privateKey, Base64.NO_WRAP))
            .putString(KEY_IK_PUB,  Base64.encodeToString(kp.publicKey,  Base64.NO_WRAP))
            .apply()
        Log.i(TAG, "Generated new identity key")
        return kp
    }

    /** Returns our identity public key as Base64, or null if not yet generated. */
    fun getIdentityPublicKeyBase64(): String? = prefs.getString(KEY_IK_PUB, null)

    // ─── Signed pre-key ───────────────────────────────────────────────────────

    /**
     * Returns the active signed pre-key (ID, key pair, signature).
     * Generates and persists a new one if none exists.
     *
     * Note: The signature is a placeholder (64 zero bytes). In a production upgrade,
     * replace with an Ed25519 signature over the pre-key using the identity key.
     */
    @Synchronized
    fun getOrCreateSignedPreKey(): Triple<Int, X25519KeyPair, ByteArray> {
        val id      = prefs.getInt(KEY_SPK_ID, -1)
        val privB64 = prefs.getString(KEY_SPK_PRIV, null)
        val pubB64  = prefs.getString(KEY_SPK_PUB,  null)
        val sigB64  = prefs.getString(KEY_SPK_SIG,  null)

        if (id >= 0 && privB64 != null && pubB64 != null && sigB64 != null) {
            return Triple(
                id,
                X25519KeyPair(
                    privateKey = Base64.decode(privB64, Base64.NO_WRAP),
                    publicKey  = Base64.decode(pubB64,  Base64.NO_WRAP)
                ),
                Base64.decode(sigB64, Base64.NO_WRAP)
            )
        }

        val newId    = SecureRandom().nextInt(Int.MAX_VALUE)
        val kp       = DoubleRatchetManager.generateKeyPair()
        val sig      = ByteArray(64)  // placeholder — replace with Ed25519 signing later

        prefs.edit()
            .putInt   (KEY_SPK_ID,   newId)
            .putString(KEY_SPK_PRIV, Base64.encodeToString(kp.privateKey, Base64.NO_WRAP))
            .putString(KEY_SPK_PUB,  Base64.encodeToString(kp.publicKey,  Base64.NO_WRAP))
            .putString(KEY_SPK_SIG,  Base64.encodeToString(sig, Base64.NO_WRAP))
            .apply()
        Log.i(TAG, "Generated signed pre-key id=$newId")
        return Triple(newId, kp, sig)
    }

    // ─── One-time pre-keys ────────────────────────────────────────────────────

    /**
     * Returns the current pool of one-time pre-keys.
     * Generates [OPK_BATCH_SIZE] keys if the pool is empty.
     */
    @Synchronized
    fun getOrCreateOneTimePreKeys(): List<Pair<Int, X25519KeyPair>> {
        val existing = loadOPKPool()
        if (existing.isNotEmpty()) return existing.toPairs()
        return generateAndSaveOPKBatch(OPK_BATCH_SIZE).toPairs()
    }

    /** Generate a fresh batch and save alongside any remaining keys. */
    @Synchronized
    fun generateAndSaveOPKBatch(batchSize: Int = OPK_BATCH_SIZE): List<StoredOPK> {
        val existing = loadOPKPool().toMutableList()
        var nextId   = prefs.getInt(KEY_NEXT_OPK_ID, 1)
        val newBatch = (0 until batchSize).map {
            val kp = DoubleRatchetManager.generateKeyPair()
            StoredOPK(
                id   = nextId++,
                priv = Base64.encodeToString(kp.privateKey, Base64.NO_WRAP),
                pub  = Base64.encodeToString(kp.publicKey,  Base64.NO_WRAP)
            )
        }
        val merged = existing + newBatch
        prefs.edit()
            .putString(KEY_OPK_POOL,    gson.toJson(merged))
            .putInt   (KEY_NEXT_OPK_ID, nextId)
            .apply()
        Log.i(TAG, "Generated $batchSize one-time pre-keys (total: ${merged.size})")
        return newBatch
    }

    /**
     * Consume (pop) one-time pre-key by ID.
     * The key is removed from storage and returned so it can be used in X3DH.
     */
    @Synchronized
    fun consumeOPK(id: Int): X25519KeyPair? {
        val pool = loadOPKPool().toMutableList()
        val idx  = pool.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val stored = pool.removeAt(idx)
        prefs.edit().putString(KEY_OPK_POOL, gson.toJson(pool)).apply()
        return X25519KeyPair(
            privateKey = Base64.decode(stored.priv, Base64.NO_WRAP),
            publicKey  = Base64.decode(stored.pub,  Base64.NO_WRAP)
        )
    }

    /** Number of remaining one-time pre-keys (local pool). */
    fun opkCount(): Int = loadOPKPool().size

    // ─── Session state ────────────────────────────────────────────────────────

    /** Persist a DR session state for a remote user. */
    @Synchronized
    fun saveSession(remoteUserId: Long, session: SessionState) {
        prefs.edit().putString(sessionKey(remoteUserId), gson.toJson(session)).apply()
    }

    /** Load DR session state for a remote user. Returns null if not found. */
    @Synchronized
    fun loadSession(remoteUserId: Long): SessionState? {
        val json = prefs.getString(sessionKey(remoteUserId), null) ?: return null
        return try {
            gson.fromJson(json, SessionState::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize session for $remoteUserId", e)
            null
        }
    }

    /** Delete session (e.g. when conversation is deleted). */
    @Synchronized
    fun deleteSession(remoteUserId: Long) {
        prefs.edit().remove(sessionKey(remoteUserId)).apply()
    }

    fun hasSession(remoteUserId: Long): Boolean =
        prefs.contains(sessionKey(remoteUserId))

    // ─── Plaintext message cache ──────────────────────────────────────────────

    /**
     * Cache the decrypted plaintext for a message by its server-assigned ID.
     * This prevents attempting to re-decrypt messages whose ratchet keys are
     * already consumed when the user re-opens a conversation.
     */
    fun cacheDecryptedMessage(msgId: Long, plaintext: String) {
        prefs.edit().putString("$KEY_PLAIN_PREFIX$msgId", plaintext).apply()
    }

    /** Returns cached plaintext for [msgId], or null if not cached. */
    fun getCachedDecryptedMessage(msgId: Long): String? =
        prefs.getString("$KEY_PLAIN_PREFIX$msgId", null)

    // ─── Registration status ──────────────────────────────────────────────────

    fun isRegistered(): Boolean  = prefs.getBoolean(KEY_REGISTERED, false)
    fun setRegistered(v: Boolean) = prefs.edit().putBoolean(KEY_REGISTERED, v).apply()

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private fun sessionKey(uid: Long) = "session_$uid"

    private fun loadOPKPool(): List<StoredOPK> {
        val json = prefs.getString(KEY_OPK_POOL, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<StoredOPK>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OPK pool", e)
            emptyList()
        }
    }

    private fun List<StoredOPK>.toPairs(): List<Pair<Int, X25519KeyPair>> = map { spk ->
        Pair(spk.id, X25519KeyPair(
            privateKey = Base64.decode(spk.priv, Base64.NO_WRAP),
            publicKey  = Base64.decode(spk.pub,  Base64.NO_WRAP)
        ))
    }

    /** Internal serialisation model for one-time pre-keys. */
    data class StoredOPK(val id: Int, val priv: String, val pub: String)
}
