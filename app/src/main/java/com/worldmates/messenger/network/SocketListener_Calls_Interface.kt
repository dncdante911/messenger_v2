package com.worldmates.messenger.network

import com.google.gson.JsonObject

/**
 * Слухач для подій Socket.IO
 * Додати ці методи до вже існуючого SocketListener інтерфейсу
 */
interface SocketListener {
    
    // Існуючі методи (залишити як є)
    // fun onMessageReceived(data: JsonObject) { }
    // fun onUserOnline(userId: Int) { }
    // fn onUserOffline(userId: Int) { }
    
    // ============================================================
    // НОВІ МЕТОДИ ДЛЯ WEBRTC ДЗВІНКІВ
    // ============================================================
    
    /**
     * Вхідний дзвінок (особистий або груповий)
     * Дані:
     * - fromId: ID того, хто дзвонить
     * - fromName: Ім'я того, хто дзвонить
     * - fromAvatar: URL аватара
     * - callId: ID дзвінка
     * - callType: 'audio' або 'video'
     * - roomName: Унікальне ім'я кімнати
     * - sdpOffer: WebRTC offer
     * - groupId: ID групи (якщо груповий)
     */
    fun onIncomingCall(data: JsonObject) { }
    
    /**
     * Відповідь на дзвінок від іншого користувача
     * Дані:
     * - roomName: Ім'я кімнати
     * - sdpAnswer: WebRTC answer
     */
    fun onCallAnswer(data: JsonObject) { }
    
    /**
     * Отримання ICE candidate від іншого користувача
     * Дані:
     * - roomName: Ім'я кімнати
     * - candidate: ICE candidate string
     * - sdpMLineIndex: M-line index
     * - sdpMid: Media ID
     */
    fun onIceCandidate(data: JsonObject) { }
    
    /**
     * Дзвінок завершений
     * Дані:
     * - roomName: Ім'я кімнати
     * - reason: Причина (user_ended, rejected, network_error)
     */
    fun onCallEnded(data: JsonObject) { }
    
    /**
     * Груповий дзвінок - новий учасник приєднався
     */
    fun onGroupCallParticipantJoined(data: JsonObject) { }
    
    /**
     * Груповий дзвінок - учасник вийшов
     */
    fun onGroupCallParticipantLeft(data: JsonObject) { }
}

/**
 * ============================================================
 * ПРИКЛАД РЕАЛІЗАЦІЇ У SocketManager.kt
 * ============================================================
 * 
 * class SocketManager private constructor() {
 *     
 *     private val listeners = mutableListOf<SocketListener>()
 *     
 *     init {
 *         setupCallsHandlers()
 *     }
 *     
 *     private fun setupCallsHandlers() {
 *         socket?.on("call:incoming") { args ->
 *             val data = args[0] as? JsonObject
 *             data?.let { listeners.forEach { it.onIncomingCall(data) } }
 *         }
 *         
 *         socket?.on("call:answer") { args ->
 *             val data = args[0] as? JsonObject
 *             data?.let { listeners.forEach { it.onCallAnswer(data) } }
 *         }
 *         
 *         socket?.on("ice:candidate") { args ->
 *             val data = args[0] as? JsonObject
 *             data?.let { listeners.forEach { it.onIceCandidate(data) } }
 *         }
 *         
 *         socket?.on("call:ended") { args ->
 *             val data = args[0] as? JsonObject
 *             data?.let { listeners.forEach { it.onCallEnded(data) } }
 *         }
 *         
 *         socket?.on("group_call:participant_joined") { args ->
 *             val data = args[0] as? JsonObject
 *             data?.let { listeners.forEach { it.onGroupCallParticipantJoined(data) } }
 *         }
 *         
 *         socket?.on("group_call:participant_left") { args ->
 *             val data = args[0] as? JsonObject
 *             data?.let { listeners.forEach { it.onGroupCallParticipantLeft(data) } }
 *         }
 *     }
 * }
 * 
 * ============================================================
 */