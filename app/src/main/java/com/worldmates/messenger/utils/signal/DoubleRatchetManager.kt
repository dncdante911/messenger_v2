// ═══════════════════════════════════════════════════════════════════════════════
// ⛔  СТОП — ЗОНА ПОВЫШЕННОЙ ОПАСНОСТИ
// ═══════════════════════════════════════════════════════════════════════════════
//
//  Чистая реализация Signal Double Ratchet Algorithm + X3DH.
//  Все операции — one-time, не идемпотентны. Изменение любого шага
//  (rkRatchet, ckRatchet, buildAD, skipMessageKeys) ломает совместимость
//  с уже установленными сессиями на всех устройствах.
//
//  Подробности — в /claude.md, раздел "ЗОНА ПОВЫШЕННОЙ ОПАСНОСТИ".
//
// ═══════════════════════════════════════════════════════════════════════════════
package com.worldmates.messenger.utils.signal

import android.util.Base64
import android.util.Log
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Double Ratchet Algorithm (Signal Protocol) — pure Kotlin implementation.
 *
 * Implements:
 *   1. X3DH  — Extended Triple Diffie-Hellman initial key agreement
 *   2. DR    — Double Ratchet Algorithm for per-message forward secrecy
 *
 * Cryptographic primitives (all via BouncyCastle 1.70 lightweight API):
 *   ► X25519 (Curve25519 DH)
 *   ► HKDF-SHA256
 *   ► HMAC-SHA256
 *   ► AES-256-GCM  (Android JCA)
 *
 * Public key format: raw 32-byte little-endian (X25519PrivateKeyParameters.encoded).
 * Session state is serialised as JSON via Gson and stored in EncryptedSharedPreferences.
 *
 * Performance note:
 *   – Key generation   : ~0.5 ms
 *   – X3DH             : ~2 ms (4 DH operations)
 *   – DR encrypt/decrypt: ~0.3 ms (1 HMAC + 1 AES-GCM)
 *   All operations are non-blocking; callers should run on IO / Default dispatcher.
 */
object DoubleRatchetManager {

    private const val TAG = "DoubleRatchet"
    private const val MAX_SKIP = 500  // Maximum skipped messages before error

    // HKDF info strings
    private val INFO_RK   = "WorldMates_DR_RK".toByteArray(Charsets.UTF_8)
    private val INFO_X3DH = "WorldMates_X3DH".toByteArray(Charsets.UTF_8)
    private val INFO_MSG  = "WorldMates_DR_MSG".toByteArray(Charsets.UTF_8)
    private val ZERO_SALT = ByteArray(32) // RFC 5869 — all-zero salt

    // ─── Key generation ───────────────────────────────────────────────────────

    /** Generate a new random X25519 key pair. Thread-safe (SecureRandom). */
    fun generateKeyPair(): X25519KeyPair {
        val priv = X25519PrivateKeyParameters(SecureRandom())
        val pub  = priv.generatePublicKey()
        return X25519KeyPair(priv.encoded, pub.encoded)
    }

    // ─── X3DH ────────────────────────────────────────────────────────────────

    /**
     * Alice (session initiator) computes the shared secret from Bob's pre-key bundle.
     *
     * X3DH formulas:
     *   DH1 = DH(IK_A, SPK_B)
     *   DH2 = DH(EK_A, IK_B)
     *   DH3 = DH(EK_A, SPK_B)
     *   DH4 = DH(EK_A, OPK_B)  [optional]
     *   SK  = HKDF(0, DH1||DH2||DH3[||DH4], "WorldMates_X3DH", 32)
     *
     * @return Pair(sharedSecret: 32 bytes, associatedData: 64 bytes)
     */
    fun x3dhAlice(
        ikA:     X25519KeyPair, // Alice's identity key
        ikBPub:  ByteArray,     // Bob's identity public key  (32 bytes)
        spkBPub: ByteArray,     // Bob's signed pre-key       (32 bytes)
        opkBPub: ByteArray?,    // Bob's one-time pre-key     (32 bytes, may be null)
        ekA:     X25519KeyPair  // Alice's ephemeral key (fresh for this session)
    ): Pair<ByteArray, ByteArray> {
        val dh1 = dhRaw(ikA.privateKey,  spkBPub)
        val dh2 = dhRaw(ekA.privateKey,  ikBPub)
        val dh3 = dhRaw(ekA.privateKey,  spkBPub)

        val dhInput = if (opkBPub != null) {
            dh1 + dh2 + dh3 + dhRaw(ekA.privateKey, opkBPub)
        } else {
            dh1 + dh2 + dh3
        }

        val sk = hkdf(ZERO_SALT, dhInput, INFO_X3DH, 32)
        val ad = ikA.publicKey + ikBPub  // 64 bytes: Encode(IK_A) || Encode(IK_B)
        return Pair(sk, ad)
    }

    /**
     * Bob (session responder) recomputes the shared secret from the X3DH header
     * embedded in Alice's first message.
     *
     * X3DH formulas (symmetric to Alice's):
     *   DH1 = DH(SPK_B, IK_A)
     *   DH2 = DH(IK_B,  EK_A)
     *   DH3 = DH(SPK_B, EK_A)
     *   DH4 = DH(OPK_B, EK_A)  [optional]
     *
     * @return Pair(sharedSecret: 32 bytes, associatedData: 64 bytes)
     */
    fun x3dhBob(
        ikB:    X25519KeyPair, // Bob's identity key
        spkB:   X25519KeyPair, // Bob's signed pre-key (key pair)
        opkB:   X25519KeyPair?,// Bob's one-time pre-key (key pair, null if not used)
        ikAPub: ByteArray,     // Alice's identity public key (from X3DH header)
        ekAPub: ByteArray      // Alice's ephemeral public key (from X3DH header)
    ): Pair<ByteArray, ByteArray> {
        val dh1 = dhRaw(spkB.privateKey, ikAPub)
        val dh2 = dhRaw(ikB.privateKey,  ekAPub)
        val dh3 = dhRaw(spkB.privateKey, ekAPub)

        val dhInput = if (opkB != null) {
            dh1 + dh2 + dh3 + dhRaw(opkB.privateKey, ekAPub)
        } else {
            dh1 + dh2 + dh3
        }

        val sk = hkdf(ZERO_SALT, dhInput, INFO_X3DH, 32)
        val ad = ikAPub + ikB.publicKey  // same order as Alice: IK_A || IK_B
        return Pair(sk, ad)
    }

    // ─── Session initialisation ───────────────────────────────────────────────

    /**
     * Initialise the DR session for Alice (initiator), called after [x3dhAlice].
     *
     * Per DR spec:
     *   state.DHs = generate_dh()  (new ratchet key, not EK_A)
     *   state.DHr = SPK_B.public   (Bob's signed pre-key)
     *   state.RK, state.CKs = KDF_RK(SK, DH(DHs, DHr))
     *
     * @param sk           32-byte shared secret from X3DH.
     * @param ad           64-byte associated data from X3DH.
     * @param spkBPub      Bob's signed pre-key public (initial DHr).
     * @param remoteUserId Used as session key in [SignalKeyStore].
     */
    fun initAliceSession(
        sk:           ByteArray,
        ad:           ByteArray,
        spkBPub:      ByteArray,
        remoteUserId: Long
    ): SessionState {
        val dhS          = generateKeyPair()
        val (rk, cks)    = rkRatchet(sk, dhRaw(dhS.privateKey, spkBPub))
        return SessionState(
            dhSendPriv     = enc(dhS.privateKey),
            dhSendPub      = enc(dhS.publicKey),
            dhRecvPub      = enc(spkBPub),
            rootKey        = enc(rk),
            chainKeySend   = enc(cks),
            chainKeyRecv   = null,
            sendN          = 0,
            recvN          = 0,
            prevChainLen   = 0,
            skippedKeys    = emptyMap(),
            associatedData = enc(ad),
            isInitiator    = true,
            remoteUserId   = remoteUserId
        )
    }

    /**
     * Initialise the DR session for Bob (responder), called after [x3dhBob].
     *
     * Per DR spec:
     *   state.DHs = SPK_B     (Bob's signed pre-key)
     *   state.DHr = None      (learned from Alice's first DR header)
     *   state.RK  = SK
     *   CKs = CKr = None
     *
     * @param sk           32-byte shared secret from X3DH.
     * @param ad           64-byte associated data from X3DH.
     * @param spkB         Bob's signed pre-key pair (becomes initial DHs).
     * @param ekAPub       Alice's ephemeral key (becomes initial DHr, triggers first recv ratchet).
     * @param remoteUserId Used as session key in [SignalKeyStore].
     */
    fun initBobSession(
        sk:           ByteArray,
        ad:           ByteArray,
        spkB:         X25519KeyPair,
        ekAPub:       ByteArray,
        remoteUserId: Long
    ): SessionState = SessionState(
        dhSendPriv     = enc(spkB.privateKey),
        dhSendPub      = enc(spkB.publicKey),
        dhRecvPub      = enc(ekAPub),
        rootKey        = enc(sk),
        chainKeySend   = null,
        chainKeyRecv   = null,
        sendN          = 0,
        recvN          = 0,
        prevChainLen   = 0,
        skippedKeys    = emptyMap(),
        associatedData = enc(ad),
        isInitiator    = false,
        remoteUserId   = remoteUserId
    )

    // ─── Double Ratchet encrypt ───────────────────────────────────────────────

    /**
     * Encrypt a plaintext message and advance the DR send chain.
     *
     * If [state.chainKeySend] is null (Bob's first outgoing message),
     * a DH ratchet step is performed first to derive a fresh send chain.
     *
     * @return Pair(updatedState, encryptedMessage) — caller MUST persist updatedState.
     */
    fun ratchetEncrypt(
        state:     SessionState,
        plaintext: ByteArray
    ): Pair<SessionState, EncryptedDRMessage> {
        var s = state

        // ── DH ratchet step (Bob's first send, or after receiving new ratchet key) ──
        if (s.chainKeySend == null) {
            val dhRPub       = dec(s.dhRecvPub!!)
            val newDHS       = generateKeyPair()
            val dhOut        = dhRaw(newDHS.privateKey, dhRPub)
            val (newRK, cks) = rkRatchet(dec(s.rootKey), dhOut)
            s = s.copy(
                dhSendPriv   = enc(newDHS.privateKey),
                dhSendPub    = enc(newDHS.publicKey),
                rootKey      = enc(newRK),
                chainKeySend = enc(cks),
                prevChainLen = s.sendN,
                sendN        = 0
            )
        }

        val (nextCKs, mk) = ckRatchet(dec(s.chainKeySend!!))

        val header = DRHeader(
            ratchetKey = dec(s.dhSendPub),
            n          = s.sendN,
            pn         = s.prevChainLen
        )

        val ad                    = buildAD(s.associatedData, header)
        val (ciphertext, iv, tag) = encryptWithMK(mk, plaintext, ad)

        val newState = s.copy(
            chainKeySend = enc(nextCKs),
            sendN        = s.sendN + 1
        )

        return Pair(newState, EncryptedDRMessage(header, ciphertext, iv, tag))
    }

    // ─── Double Ratchet decrypt ───────────────────────────────────────────────

    /**
     * Decrypt an incoming DR message and advance the receive chain.
     *
     * Handles:
     *   – Out-of-order messages (up to MAX_SKIP ahead).
     *   – DH ratchet steps (new ratchet key in header).
     *   – First message from Alice (DHr is null in Bob's initial state).
     *
     * @throws IllegalStateException on authentication failure or too many skipped messages.
     * @return Pair(updatedState, plaintext) — caller MUST persist updatedState.
     */
    fun ratchetDecrypt(
        state: SessionState,
        msg:   EncryptedDRMessage
    ): Pair<SessionState, ByteArray> {
        val header = msg.header
        var s      = state

        // ── 1. Check skipped message keys ────────────────────────────────────
        val skipId     = skippedKeyId(header.ratchetKey, header.n)
        val skippedMK  = s.skippedKeys[skipId]
        if (skippedMK != null) {
            val mk       = dec(skippedMK)
            val ad       = buildAD(s.associatedData, header)
            val plain    = decryptWithMK(mk, msg.ciphertext, msg.iv, msg.tag, ad)
            return Pair(s.copy(skippedKeys = s.skippedKeys - skipId), plain)
        }

        // ── 2. DH ratchet step if new ratchet key detected ───────────────────
        val curDHr     = s.dhRecvPub?.let { dec(it) }
        val newRatchet = curDHr == null || !header.ratchetKey.contentEquals(curDHr)
        if (newRatchet) {
            if (s.chainKeyRecv != null) {
                // Save skipped keys for the old receiving chain (up to header.pn)
                s = skipMessageKeys(s, header.pn)
            }
            s = performDHRatchetStep(s, header.ratchetKey)
        }

        // ── 3. Skip ahead to message n ───────────────────────────────────────
        s = skipMessageKeys(s, header.n)

        // ── 4. Derive message key and decrypt ────────────────────────────────
        val (nextCKr, mk) = ckRatchet(dec(s.chainKeyRecv!!))
        s = s.copy(
            chainKeyRecv = enc(nextCKr),
            recvN        = s.recvN + 1
        )

        val ad    = buildAD(s.associatedData, header)
        val plain = decryptWithMK(mk, msg.ciphertext, msg.iv, msg.tag, ad)
        return Pair(s, plain)
    }

    // ─── Private DR helpers ────────────────────────────────────────────────────

    /** Perform one DH ratchet step upon receiving a new remote ratchet key. */
    private fun performDHRatchetStep(state: SessionState, newDHRPub: ByteArray): SessionState {
        val prevSendN = state.sendN

        // Derive new receiving chain key using current DHs and new DHr
        val dhOut1       = dhRaw(dec(state.dhSendPriv), newDHRPub)
        val (rk1, newCKr) = rkRatchet(dec(state.rootKey), dhOut1)

        // Generate new sending ratchet key pair
        val newDHS       = generateKeyPair()
        val dhOut2       = dhRaw(newDHS.privateKey, newDHRPub)
        val (rk2, newCKs) = rkRatchet(rk1, dhOut2)

        return state.copy(
            dhSendPriv   = enc(newDHS.privateKey),
            dhSendPub    = enc(newDHS.publicKey),
            dhRecvPub    = enc(newDHRPub),
            rootKey      = enc(rk2),
            chainKeySend = enc(newCKs),
            chainKeyRecv = enc(newCKr),
            sendN        = 0,
            recvN        = 0,
            prevChainLen = prevSendN
        )
    }

    /** Advance the receive chain forward, saving skipped message keys. */
    private fun skipMessageKeys(state: SessionState, until: Int): SessionState {
        val toSkip = until - state.recvN
        if (toSkip > MAX_SKIP) {
            Log.w(TAG, "Too many skipped messages: $toSkip")
            throw IllegalStateException("Exceeded maximum skipped messages ($MAX_SKIP)")
        }
        var s = state
        while (s.recvN < until) {
            val (nextCKr, mk) = ckRatchet(dec(s.chainKeyRecv!!))
            val id            = skippedKeyId(dec(s.dhRecvPub!!), s.recvN)
            s = s.copy(
                chainKeyRecv = enc(nextCKr),
                recvN        = s.recvN + 1,
                skippedKeys  = s.skippedKeys + (id to enc(mk))
            )
        }
        return s
    }

    // ─── Low-level crypto ─────────────────────────────────────────────────────

    /** X25519 DH agreement — returns 32-byte shared secret. */
    internal fun dhRaw(privateKeyBytes: ByteArray, publicKeyBytes: ByteArray): ByteArray {
        val priv      = X25519PrivateKeyParameters(privateKeyBytes, 0)
        val pub       = X25519PublicKeyParameters(publicKeyBytes, 0)
        val agreement = X25519Agreement()
        agreement.init(priv)
        val result = ByteArray(32)
        agreement.calculateAgreement(pub, result, 0)
        return result
    }

    /**
     * Root key ratchet (KDF_RK):
     *   HKDF(salt=RK, IKM=dhOutput, info="WorldMates_DR_RK", length=64)
     *   → split into (newRK[0..32), newCK[32..64))
     */
    private fun rkRatchet(rk: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
        val out = hkdf(rk, dhOutput, INFO_RK, 64)
        return Pair(out.copyOf(32), out.copyOfRange(32, 64))
    }

    /**
     * Chain key ratchet (KDF_CK):
     *   messageKey  = HMAC-SHA256(CK, 0x01)
     *   nextChainKey = HMAC-SHA256(CK, 0x02)
     */
    private fun ckRatchet(ck: ByteArray): Pair<ByteArray, ByteArray> {
        val mk     = hmacSHA256(ck, byteArrayOf(0x01))
        val nextCk = hmacSHA256(ck, byteArrayOf(0x02))
        return Pair(nextCk, mk)
    }

    /** HKDF-SHA256 per RFC 5869. */
    private fun hkdf(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(ikm, salt, info))
        val out = ByteArray(length)
        gen.generateBytes(out, 0, length)
        return out
    }

    /** HMAC-SHA256. */
    private fun hmacSHA256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        val out = ByteArray(mac.macSize)
        mac.doFinal(out, 0)
        return out
    }

    /**
     * AES-256-GCM encryption with message key.
     * Derives AES key from message key via HKDF to ensure key separation.
     * Returns (ciphertext, iv[12], tag[16]).
     */
    private fun encryptWithMK(
        mk:        ByteArray,
        plaintext: ByteArray,
        ad:        ByteArray
    ): Triple<ByteArray, ByteArray, ByteArray> {
        val aesKey = deriveAesKey(mk)
        val iv     = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(ad)
        val withTag    = cipher.doFinal(plaintext)
        val ciphertext = withTag.copyOf(withTag.size - 16)
        val tag        = withTag.copyOfRange(withTag.size - 16, withTag.size)
        return Triple(ciphertext, iv, tag)
    }

    /**
     * AES-256-GCM decryption with message key.
     * Throws [javax.crypto.AEADBadTagException] if authentication fails.
     */
    private fun decryptWithMK(
        mk:         ByteArray,
        ciphertext: ByteArray,
        iv:         ByteArray,
        tag:        ByteArray,
        ad:         ByteArray
    ): ByteArray {
        val aesKey = deriveAesKey(mk)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(ad)
        return cipher.doFinal(ciphertext + tag)
    }

    /** Derive 32-byte AES key from message key via HKDF (key separation). */
    private fun deriveAesKey(mk: ByteArray): ByteArray =
        hkdf(ByteArray(32), mk, INFO_MSG, 32)

    // ─── Associated data helpers ──────────────────────────────────────────────

    /**
     * Build per-message associated data for AEAD:
     *   AD = session_AD (64 bytes) || Encode(header) (40 bytes)
     */
    private fun buildAD(adBase64: String, header: DRHeader): ByteArray {
        val sessionAD  = if (adBase64.isNotEmpty()) dec(adBase64) else ByteArray(0)
        return sessionAD + encodeHeader(header)
    }

    /**
     * Minimal binary encoding of the DR header for AEAD integrity protection.
     * Format: ratchet_key[32] || n[4 LE] || pn[4 LE]  =  40 bytes.
     */
    private fun encodeHeader(h: DRHeader): ByteArray {
        val buf = ByteArray(40)
        h.ratchetKey.copyInto(buf, 0)
        intToLE(h.n,  buf, 32)
        intToLE(h.pn, buf, 36)
        return buf
    }

    private fun intToLE(v: Int, buf: ByteArray, offset: Int) {
        buf[offset]     = (v         and 0xFF).toByte()
        buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((v shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((v shr 24) and 0xFF).toByte()
    }

    // ─── Skipped-key map helpers ──────────────────────────────────────────────

    private fun skippedKeyId(ratchetPub: ByteArray, n: Int): String {
        val hex = ratchetPub.joinToString("") { "%02x".format(it) }
        return "$hex:$n"
    }

    // ─── Base64 helpers (NO_WRAP, no line breaks) ─────────────────────────────

    /** Base64-encode byte array. */
    private fun enc(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    /** Base64-decode string. */
    private fun dec(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
