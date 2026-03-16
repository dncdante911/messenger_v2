package com.worldmates.messenger.network

/**
 * Reaction types for stories.
 * [value] is the string sent to the API.
 */
enum class StoryReactionType(val value: String) {
    LIKE("like"),
    LOVE("love"),
    HAHA("haha"),
    WOW("wow"),
    SAD("sad"),
    ANGRY("angry")
}
