package com.worldmates.messenger.utils.signal

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.worldmates.messenger.data.model.GroupMember
import com.worldmates.messenger.network.NodeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * Сервіс шифрування для групових чатів — Signal Sender Key Protocol.
 *
 * Протокол (сумісний з Signal/WhatsApp для груп):
 *   1. Кожен учасник генерує власний SenderKey для кожної групи.
 *   2. SenderKey розподіляється між учасниками через індивідуальні DR-сеанси
 *      (зашифрований SenderKeyDistribution для кожного одержувача).
 *   3. Кожне повідомлення шифрується за допомогою chain key відправника.
 *   4. Одержувачі дешифрують за допомогою збереженого SenderKey відправника.
 *
 * Thread safety: всі публічні функції є `suspend` та виконуються на [Dispatchers.Default].
 *
 * ВАЖЛИВО: сервер зберігає тільки зашифровані payload-и.
 *   Plaintext — НІКОЛИ не залишає пристрій.
 */
class SignalGroupEncryptionService private constructor(
    private val context: Context,
    private val nodeApi: NodeApi,
    private val signalService: SignalEncryptionService
) {
    private val TAG          = "SignalGroupEncSvc"
    private val keyStore     = SignalSenderKeyStore(context)
    private val gson         = Gson()

    companion object {
        const val CIPHER_VERSION_SIGNAL = 3

        @Volatile private var INSTANCE: SignalGroupEncryptionService? = null

        fun getInstance(
            context:       Context,
            nodeApi:       NodeApi,
            signalService: SignalEncryptionService
        ): SignalGroupEncryptionService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SignalGroupEncryptionService(
                    context.applicationContext, nodeApi, signalService
                ).also { INSTANCE = it }
            }
    }

    // ─── Encrypt outgoing group message ───────────────────────────────────────

    /**
     * Зашифрувати [plaintext] для групи [groupId].
     *
     * Якщо SenderKey ще не розподілено між учасниками — розподіляє спочатку.
     * Повертає [GroupOutgoingPayload] готовий до POST /api/node/group/messages/send.
     *
     * @param groupId   ID групи
     * @param plaintext Відкритий текст повідомлення
     * @param members   Список учасників групи (для distribution, якщо потрібен)
     * @param myUserId  Поточний userId (відправник)
     *
     * @return Зашифрований payload або null при помилці
     */
    suspend fun encryptForGroup(
        groupId:   Long,
        plaintext: String,
        members:   List<GroupMember>,
        myUserId:  Long
    ): GroupOutgoingPayload? = withContext(Dispatchers.Default) {
        try {
            val (state, isNew) = keyStore.getOrCreateMySenderKey(groupId)
            val needsDist      = isNew || !keyStore.isDistributed(groupId)

            // Розподіл SenderKey якщо потрібно
            if (needsDist) {
                val distributed = distributeSenderKeyToMembers(groupId, state, members, myUserId)
                if (distributed) {
                    keyStore.setDistributed(groupId, true)
                    Log.i(TAG, "[Group $groupId] SenderKey розподілено між ${members.size - 1} учасниками")
                } else {
                    Log.w(TAG, "[Group $groupId] Не вдалося розподілити SenderKey — відміна шифрування")
                    return@withContext null
                }
            }

            // Шифрування повідомлення
            val (newState, encResult) = senderKeyEncrypt(state, plaintext, groupId, myUserId)
            keyStore.saveMySenderKey(groupId, newState)

            val header = GroupSignalHeader(
                groupId  = groupId,
                senderId = myUserId,
                chainId  = state.chainId,
                counter  = state.counter
            )

            Log.d(TAG, "[Group $groupId] msg зашифровано (counter=${state.counter})")

            GroupOutgoingPayload(
                ciphertext   = Base64.encodeToString(encResult.ciphertext, Base64.NO_WRAP),
                iv           = Base64.encodeToString(encResult.iv,         Base64.NO_WRAP),
                tag          = Base64.encodeToString(encResult.tag,        Base64.NO_WRAP),
                signalHeader = gson.toJson(header)
            )
        } catch (e: Exception) {
            Log.e(TAG, "[Group $groupId] encryptForGroup error", e)
            null
        }
    }

    // ─── Decrypt incoming group message ───────────────────────────────────────

    /**
     * Розшифрувати вхідне групове повідомлення з [cipher_version=3].
     *
     * @param groupId       ID групи
     * @param senderId      ID відправника
     * @param ciphertextB64 Base64 зашифрованого тексту
     * @param ivB64         Base64 IV (12 bytes)
     * @param tagB64        Base64 GCM auth tag (16 bytes)
     * @param headerJson    JSON GroupSignalHeader
     *
     * @return Розшифрований plaintext або null при помилці
     */
    suspend fun decryptGroupMessage(
        groupId:       Long,
        senderId:      Long,
        ciphertextB64: String,
        ivB64:         String,
        tagB64:        String,
        headerJson:    String
    ): String? = withContext(Dispatchers.Default) {
        try {
            val senderKeyState = keyStore.loadSenderKey(groupId, senderId)
            if (senderKeyState == null) {
                Log.w(TAG, "[Group $groupId] SenderKey від user=$senderId не знайдено — розшифровка неможлива")
                return@withContext null
            }

            val header = runCatching {
                gson.fromJson(headerJson, GroupSignalHeader::class.java)
            }.getOrNull()

            if (header == null) {
                Log.e(TAG, "[Group $groupId] Невалідний signal_header: $headerJson")
                return@withContext null
            }

            // Перемотати chain до потрібного counter (якщо пропущені повідомлення)
            val targetCounter = header.counter
            val (advancedState, messageKey) = advanceChainToCounter(senderKeyState, targetCounter)
                ?: return@withContext null.also {
                    Log.e(TAG, "[Group $groupId] Не вдалося перемотати chain до counter=$targetCounter")
                }

            // Розшифрування AES-256-GCM
            val ciphertext = Base64.decode(ciphertextB64, Base64.NO_WRAP)
            val iv         = Base64.decode(ivB64,         Base64.NO_WRAP)
            val tag        = Base64.decode(tagB64,        Base64.NO_WRAP)
            val ad         = buildGroupAD(groupId, senderId, header.chainId)

            val plainBytes = aesGcmDecrypt(messageKey, ciphertext, iv, tag, ad)
                ?: return@withContext null.also {
                    Log.e(TAG, "[Group $groupId] AES-GCM decrypt failed (auth tag mismatch?)")
                }

            // Зберегти оновлений стан chain тільки після успішного розшифрування.
            // Якщо зберегти до перевірки автентифікації, невдача GCM залишить лічильник
            // в просунутому стані, через що повторні спроби розшифрування завжди матимуть
            // targetCounter < currentCounter.
            keyStore.updateSenderKey(groupId, senderId, advancedState)

            String(plainBytes, Charsets.UTF_8)

        } catch (e: Exception) {
            Log.e(TAG, "[Group $groupId] decryptGroupMessage error from sender=$senderId", e)
            null
        }
    }

    // ─── Fetch + apply pending SenderKey distributions ────────────────────────

    /**
     * Завантажити та застосувати всі невидані SenderKey distributions для групи [groupId].
     *
     * Викликається:
     *   - при відкритті групового чату
     *   - при отриманні групового повідомлення, яке не вдалося розшифрувати
     *   - після приєднання до групи
     *
     * @return Кількість успішно застосованих distributions
     */
    suspend fun fetchAndApplyPendingDistributions(groupId: Long): Int =
        withContext(Dispatchers.Default) {
            try {
                val resp = nodeApi.getGroupPendingDistributions(groupId = groupId)
                val items = resp.distributions.orEmpty()
                if (items.isEmpty()) return@withContext 0

                var applied = 0
                val confirmedIds = mutableListOf<Long>()

                for (item in items) {
                    val decryptedPayload = decryptDistributionPayload(item.distribution, item.senderId)
                    if (decryptedPayload != null) {
                        val newState = SenderKeyState(
                            chainKey = decryptedPayload.chainKey,
                            chainId  = decryptedPayload.chainId,
                            counter  = decryptedPayload.counter
                        )
                        // Не перезаписуємо стан chain з вищим лічильником — це означає,
                        // що ми вже розшифрували повідомлення і просувати ланцюг назад не потрібно.
                        val existingState = keyStore.loadSenderKey(item.groupId, item.senderId)
                        if (existingState == null || existingState.counter <= newState.counter) {
                            keyStore.saveSenderKey(
                                groupId  = item.groupId,
                                senderId = item.senderId,
                                state    = newState
                            )
                            Log.d(TAG, "[Group $groupId] SenderKey від user=${item.senderId} застосовано (counter=${newState.counter})")
                        } else {
                            Log.d(TAG, "[Group $groupId] SenderKey від user=${item.senderId} пропущено — chain вже на counter=${existingState.counter}")
                        }
                        confirmedIds.add(item.id)
                        applied++
                    } else {
                        Log.w(TAG, "[Group $groupId] Не вдалося розшифрувати distribution від ${item.senderId}")
                    }
                }

                // Підтвердити доставку для успішно оброблених distributions
                if (confirmedIds.isNotEmpty()) {
                    runCatching {
                        nodeApi.confirmGroupDistributionDelivery(
                            distributionIds = gson.toJson(confirmedIds)
                        )
                    }
                }

                Log.i(TAG, "[Group $groupId] Застосовано $applied/${items.size} distributions")
                applied
            } catch (e: Exception) {
                Log.e(TAG, "[Group $groupId] fetchAndApplyPendingDistributions error", e)
                0
            }
        }

    // ─── Plaintext cache ──────────────────────────────────────────────────────

    /** Кешувати розшифрований plaintext. */
    fun cachePlaintext(msgId: Long, plaintext: String) =
        keyStore.cachePlaintext(msgId, plaintext)

    /** Отримати кешований plaintext або null. */
    fun getCachedPlaintext(msgId: Long): String? =
        keyStore.getCachedPlaintext(msgId)

    /** Перевірити чи є SenderKey від відправника [senderId] для групи [groupId]. */
    fun hasSenderKey(groupId: Long, senderId: Long): Boolean =
        keyStore.loadSenderKey(groupId, senderId) != null

    /**
     * Видалити SenderKey учасника [memberId] з групи [groupId].
     * Викликається коли учасник покидає групу.
     */
    fun invalidateMemberKey(groupId: Long, memberId: Long) {
        keyStore.removeSenderKey(groupId, memberId)
    }

    /**
     * Видалити власний SenderKey та скинути прапор distribution.
     * Викликається при виході з групи або forced re-key.
     */
    fun invalidateMyKey(groupId: Long) {
        keyStore.invalidateMySenderKey(groupId)
    }

    // ─── Приватні методи ──────────────────────────────────────────────────────

    /**
     * Розподілити власний SenderKey між учасниками [members].
     * Для кожного учасника шифруємо distribution через DR (Double Ratchet).
     */
    private suspend fun distributeSenderKeyToMembers(
        groupId:  Long,
        myState:  SenderKeyState,
        members:  List<GroupMember>,
        myUserId: Long
    ): Boolean {
        val distributions = mutableListOf<SignalGroupDistributionItem>()

        val payload = SenderKeyDistributionPayload(
            chainKey = myState.chainKey,
            chainId  = myState.chainId,
            counter  = myState.counter
        )
        val payloadJson = gson.toJson(payload)

        for (member in members) {
            if (member.userId == myUserId) continue  // не шифруємо для себе

            val encPayload = signalService.encryptForSend(member.userId, payloadJson)
            if (encPayload == null) {
                Log.w(TAG, "[Group $groupId] Не вдалося зашифрувати distribution для user=${member.userId}")
                // Не відміняємо — продовжуємо для інших учасників
                continue
            }

            // Distribution = JSON із encrypted payload та Signal header (щоб одержувач міг розшифрувати)
            val distributionJson = gson.toJson(mapOf(
                "ct"  to encPayload.ciphertext,
                "iv"  to encPayload.iv,
                "tag" to encPayload.tag,
                "hdr" to encPayload.signalHeader
            ))
            distributions.add(SignalGroupDistributionItem(
                recipientId  = member.userId,
                distribution = Base64.encodeToString(
                    distributionJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP
                )
            ))
        }

        if (distributions.isEmpty()) {
            Log.w(TAG, "[Group $groupId] Немає учасників для distribution")
            return false
        }

        return try {
            val resp = nodeApi.distributeGroupSenderKey(
                groupId       = groupId,
                distributions = gson.toJson(distributions)
            )
            resp.apiStatus == 200
        } catch (e: Exception) {
            Log.e(TAG, "[Group $groupId] distributeGroupSenderKey error", e)
            false
        }
    }

    /**
     * Розшифрувати SenderKey distribution від [senderId].
     * Distribution = Base64(JSON({ct, iv, tag, hdr})) зашифрований через DR.
     */
    private suspend fun decryptDistributionPayload(
        distributionB64: String,
        senderId:        Long
    ): SenderKeyDistributionPayload? {
        return try {
            val distributionJson = String(
                Base64.decode(distributionB64, Base64.NO_WRAP), Charsets.UTF_8
            )
            @Suppress("UNCHECKED_CAST")
            val map = gson.fromJson(distributionJson, Map::class.java) as Map<String, String>

            val ct  = map["ct"]  ?: return null
            val iv  = map["iv"]  ?: return null
            val tag = map["tag"] ?: return null
            val hdr = map["hdr"] ?: return null

            // Розшифровуємо через DR сеанс з відправником
            val payloadJson = signalService.decryptIncoming(
                senderId         = senderId,
                ciphertextB64    = ct,
                ivB64            = iv,
                tagB64           = tag,
                signalHeaderJson = hdr
            ) ?: return null

            gson.fromJson(payloadJson, SenderKeyDistributionPayload::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "decryptDistributionPayload error from sender=$senderId", e)
            null
        }
    }

    /**
     * Зашифрувати один рядок за допомогою поточного chain key.
     * @return Pair(нового стану, результату шифрування)
     */
    private fun senderKeyEncrypt(
        state:     SenderKeyState,
        plaintext: String,
        groupId:   Long,
        senderId:  Long
    ): Pair<SenderKeyState, SenderKeyEncResult> {
        val chainKeyBytes = Base64.decode(state.chainKey, Base64.NO_WRAP)
        val (nextChainKey, messageKey) = ckRatchet(chainKeyBytes)
        val aesKey = deriveAesKey(messageKey)
        val ad     = buildGroupAD(groupId, senderId, state.chainId)

        val iv      = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher  = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(ad)
        val withTag    = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ciphertext = withTag.copyOf(withTag.size - 16)
        val tag        = withTag.copyOfRange(withTag.size - 16, withTag.size)

        val newState = SenderKeyState(
            chainKey = Base64.encodeToString(nextChainKey, Base64.NO_WRAP),
            chainId  = state.chainId,
            counter  = state.counter + 1
        )
        return Pair(newState, SenderKeyEncResult(ciphertext, iv, tag))
    }

    /**
     * Перемотати chain key від поточного counter до [targetCounter].
     * Повертає (новий стан після перемотки, messageKey для targetCounter).
     * Підтримка out-of-order: якщо targetCounter < currentCounter, повертає null.
     */
    private fun advanceChainToCounter(
        state:         SenderKeyState,
        targetCounter: Int
    ): Pair<SenderKeyState, ByteArray>? {
        if (targetCounter < state.counter) {
            Log.w(TAG, "advanceChainToCounter: targetCounter=$targetCounter < currentCounter=${state.counter}")
            return null
        }

        var chainKeyBytes = Base64.decode(state.chainKey, Base64.NO_WRAP)
        var currentCounter = state.counter
        var lastMessageKey: ByteArray? = null

        // Просуваємо chain до targetCounter включно
        while (currentCounter <= targetCounter) {
            val (nextCk, mk) = ckRatchet(chainKeyBytes)
            if (currentCounter == targetCounter) lastMessageKey = mk
            chainKeyBytes  = nextCk
            currentCounter++
        }

        if (lastMessageKey == null) return null

        val newState = SenderKeyState(
            chainKey = Base64.encodeToString(chainKeyBytes, Base64.NO_WRAP),
            chainId  = state.chainId,
            counter  = currentCounter
        )
        return Pair(newState, lastMessageKey)
    }

    /**
     * AES-256-GCM дешифрування.
     * @return plaintext або null при помилці автентифікації
     */
    private fun aesGcmDecrypt(
        messageKey: ByteArray,
        ciphertext: ByteArray,
        iv:         ByteArray,
        tag:        ByteArray,
        ad:         ByteArray
    ): ByteArray? {
        return try {
            val aesKey = deriveAesKey(messageKey)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(ad)
            cipher.doFinal(ciphertext + tag)
        } catch (e: Exception) {
            Log.e(TAG, "aesGcmDecrypt failed: ${e.message}")
            null
        }
    }

    // ─── Chain key ratchet (KDF_CK) — аналогічно DoubleRatchetManager ─────────

    /**
     * Крок chain key ratchet:
     *   messageKey  = HMAC-SHA256(ck, 0x01)
     *   nextChainKey = HMAC-SHA256(ck, 0x02)
     */
    private fun ckRatchet(ck: ByteArray): Pair<ByteArray, ByteArray> {
        val mk     = hmacSHA256(ck, byteArrayOf(0x01))
        val nextCk = hmacSHA256(ck, byteArrayOf(0x02))
        return Pair(nextCk, mk)
    }

    /** HMAC-SHA256 */
    private fun hmacSHA256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /** Derive 32-byte AES key from messageKey (key separation). */
    private fun deriveAesKey(mk: ByteArray): ByteArray {
        // HKDF-like: expand mk with info bytes
        val info = "WorldMates_SK_MSG".toByteArray(Charsets.UTF_8)
        val mac  = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(mk, "HmacSHA256"))
        mac.update(info)
        return mac.doFinal(byteArrayOf(0x01)).copyOf(32)
    }

    /**
     * Associated data для AEAD = "WM_GRP" || groupId(8B) || senderId(8B) || chainId(16B).
     * Прив'язує шифротекст до конкретної групи та відправника.
     */
    private fun buildGroupAD(groupId: Long, senderId: Long, chainIdB64: String): ByteArray {
        val prefix  = "WM_GRP".toByteArray(Charsets.UTF_8)
        val gidB    = longToBytes(groupId)
        val sidB    = longToBytes(senderId)
        val chainId = try { Base64.decode(chainIdB64, Base64.NO_WRAP) } catch (_: Exception) { ByteArray(16) }
        return prefix + gidB + sidB + chainId
    }

    private fun longToBytes(v: Long): ByteArray {
        val b = ByteArray(8)
        for (i in 7 downTo 0) { b[i] = (v shr ((7 - i) * 8)).toByte() }
        return b
    }

    // ─── Result types ─────────────────────────────────────────────────────────

    private data class SenderKeyEncResult(
        val ciphertext: ByteArray,
        val iv:         ByteArray,
        val tag:        ByteArray
    )

    /** Payload готовий до POST /api/node/group/messages/send як cipher_version=3. */
    data class GroupOutgoingPayload(
        val ciphertext:   String,   // Base64(ciphertext)
        val iv:           String,   // Base64(IV, 12 bytes)
        val tag:          String,   // Base64(GCM auth tag, 16 bytes)
        val signalHeader: String    // JSON GroupSignalHeader
    )
}
