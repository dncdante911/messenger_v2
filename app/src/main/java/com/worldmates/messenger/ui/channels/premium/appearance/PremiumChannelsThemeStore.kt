package com.worldmates.messenger.ui.channels.premium.appearance

import android.content.Context
import android.content.SharedPreferences
import com.worldmates.messenger.data.model.ChannelPremiumCustomization

/**
 * Global premium-channels appearance preferences.
 *
 * Lets the user opt into the new premium-channel design app-wide and
 * pick the default accent / banner / frame / etc. that will be used
 * for any premium channel that hasn't set its own customization.
 *
 * Backed by a dedicated SharedPreferences file so it stays independent
 * from the per-channel store.
 */
class PremiumChannelsThemeStore private constructor(private val prefs: SharedPreferences) {

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    fun read(): ChannelPremiumCustomization = ChannelPremiumCustomization(
        accentColorId = prefs.getString(KEY_ACCENT, null),
        bannerPatternId = prefs.getString(KEY_BANNER, null),
        emojiPackId = prefs.getString(KEY_EMOJI, null),
        fontWeight = prefs.getString(KEY_WEIGHT, null),
        postCornerRadius = prefs.getInt(KEY_RADIUS, -1).takeIf { it >= 0 },
        avatarFrame = prefs.getString(KEY_FRAME, null),
        postsBackdropEnabled = prefs.getBoolean(KEY_BACKDROP, false),
    )

    fun save(c: ChannelPremiumCustomization) {
        prefs.edit().apply {
            putString(KEY_ACCENT, c.accentColorId)
            putString(KEY_BANNER, c.bannerPatternId)
            putString(KEY_EMOJI, c.emojiPackId)
            putString(KEY_WEIGHT, c.fontWeight)
            c.postCornerRadius?.let { putInt(KEY_RADIUS, it) } ?: remove(KEY_RADIUS)
            putString(KEY_FRAME, c.avatarFrame)
            putBoolean(KEY_BACKDROP, c.postsBackdropEnabled)
        }.apply()
    }

    fun reset() {
        prefs.edit()
            .remove(KEY_ACCENT)
            .remove(KEY_BANNER)
            .remove(KEY_EMOJI)
            .remove(KEY_WEIGHT)
            .remove(KEY_RADIUS)
            .remove(KEY_FRAME)
            .remove(KEY_BACKDROP)
            .apply()
    }

    companion object {
        private const val FILE = "premium_channels_theme"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ACCENT  = "accent"
        private const val KEY_BANNER  = "banner"
        private const val KEY_EMOJI   = "emoji"
        private const val KEY_WEIGHT  = "weight"
        private const val KEY_RADIUS  = "radius"
        private const val KEY_FRAME   = "frame"
        private const val KEY_BACKDROP = "backdrop"

        @Volatile private var instance: PremiumChannelsThemeStore? = null

        fun from(context: Context): PremiumChannelsThemeStore {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: PremiumChannelsThemeStore(
                    context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }
}
