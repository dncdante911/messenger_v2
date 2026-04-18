package com.worldmates.messenger.ui.channels.premium.appearance

import android.content.Context
import android.content.SharedPreferences
import com.worldmates.messenger.data.model.ChannelPremiumCustomization

/**
 * On-device persistence of a channel owner's customization choices.
 *
 * This is the durable store used by [ChannelAppearanceActivity] so the
 * owner can tweak colors without a round-trip to the server on every
 * change. The backend sync is a separate concern: a later commit will
 * add a PUT endpoint and call [ChannelAppearanceStore.save] on a
 * successful server echo. For now the local copy is the source of truth
 * and the UI re-reads it when a channel is reopened.
 */
class ChannelAppearanceStore private constructor(private val prefs: SharedPreferences) {

    fun read(channelId: Long): ChannelPremiumCustomization? {
        val key = keyPrefix(channelId)
        if (!prefs.contains("${key}_accent")
            && !prefs.contains("${key}_banner")
            && !prefs.contains("${key}_emoji")
            && !prefs.contains("${key}_weight")
            && !prefs.contains("${key}_frame")
            && !prefs.contains("${key}_radius")
            && !prefs.contains("${key}_backdrop")
        ) return null

        return ChannelPremiumCustomization(
            accentColorId = prefs.getString("${key}_accent", null),
            bannerPatternId = prefs.getString("${key}_banner", null),
            emojiPackId = prefs.getString("${key}_emoji", null),
            fontWeight = prefs.getString("${key}_weight", null),
            postCornerRadius = prefs.getInt("${key}_radius", -1).takeIf { it >= 0 },
            avatarFrame = prefs.getString("${key}_frame", null),
            postsBackdropEnabled = prefs.getBoolean("${key}_backdrop", false),
        )
    }

    fun save(channelId: Long, c: ChannelPremiumCustomization) {
        val key = keyPrefix(channelId)
        prefs.edit().apply {
            putString("${key}_accent", c.accentColorId)
            putString("${key}_banner", c.bannerPatternId)
            putString("${key}_emoji", c.emojiPackId)
            putString("${key}_weight", c.fontWeight)
            c.postCornerRadius?.let { putInt("${key}_radius", it) } ?: remove("${key}_radius")
            putString("${key}_frame", c.avatarFrame)
            putBoolean("${key}_backdrop", c.postsBackdropEnabled)
        }.apply()
    }

    fun clear(channelId: Long) {
        val key = keyPrefix(channelId)
        prefs.edit()
            .remove("${key}_accent")
            .remove("${key}_banner")
            .remove("${key}_emoji")
            .remove("${key}_weight")
            .remove("${key}_radius")
            .remove("${key}_frame")
            .remove("${key}_backdrop")
            .apply()
    }

    private fun keyPrefix(channelId: Long) = "channel_$channelId"

    companion object {
        private const val FILE = "premium_channel_appearance"
        fun from(context: Context): ChannelAppearanceStore {
            val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return ChannelAppearanceStore(prefs)
        }
    }
}
