package com.worldmates.messenger.data.model

/**
 * Represents all possible presence/activity statuses for a chat recipient.
 * Used in the MessagesHeaderBar to show what the other person is doing.
 */
sealed class UserPresenceStatus {
    /** Recipient is offline */
    object Offline : UserPresenceStatus()

    /** Recipient is online and idle */
    object Online : UserPresenceStatus()

    /** Private chat: recipient is typing */
    object Typing : UserPresenceStatus()

    /** Group chat: a specific member is typing */
    data class GroupTyping(val userName: String) : UserPresenceStatus()

    /** Recipient is recording a voice message */
    object RecordingVoice : UserPresenceStatus()

    /** Recipient is recording a video message */
    object RecordingVideo : UserPresenceStatus()

    /** Recipient is listening to an audio message */
    object ListeningAudio : UserPresenceStatus()

    /** Recipient is viewing a photo or sticker */
    object ViewingMedia : UserPresenceStatus()

    /** Recipient is browsing the sticker panel */
    object ChoosingSticker : UserPresenceStatus()

    /** Recipient is offline and was last seen at [timestamp] (Unix seconds) */
    data class LastSeen(val timestamp: Long) : UserPresenceStatus()
}
