package com.worldmates.messenger.ui.channels.premium.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.worldmates.messenger.data.model.Channel
import com.worldmates.messenger.data.model.ChannelPremiumCustomization
import com.worldmates.messenger.ui.channels.premium.appearance.PremiumChannelsThemeStore
import com.worldmates.messenger.ui.channels.premium.components.AvatarFrameStyle

/**
 * Maps the server-sent preset IDs on [ChannelPremiumCustomization] into
 * ready-to-render Compose values. This is the single place where unknown
 * ids fall back to defaults so every other call site can use non-null
 * values without care.
 *
 * Resolution order for any given field:
 *   1. Per-channel customization from the server / per-channel store.
 *   2. App-wide default chosen by the user in Settings → Themes.
 *   3. Built-in catalog default in [PremiumPresets].
 */
object PremiumCustomizationResolver {

    /** Process-wide cache of the global defaults so we don't hit prefs on every recompose. */
    @Volatile private var globalDefaults: ChannelPremiumCustomization? = null

    /**
     * Master switch from Settings → Themes → Premium channels. When false the
     * resolver ignores global defaults entirely — per-channel customization
     * set by an owner still wins, but non-customized channels render the
     * plain catalog defaults.
     */
    @Volatile private var globalEnabled: Boolean = true

    /** Called by Application / Activity startup to load the user's global defaults once. */
    fun primeGlobalDefaults(context: Context) {
        val store = PremiumChannelsThemeStore.from(context)
        globalDefaults = store.read()
        globalEnabled = store.enabled
    }

    /** Force-refresh the cache after the user saves new defaults from Settings. */
    fun refreshGlobalDefaults(context: Context) {
        val store = PremiumChannelsThemeStore.from(context)
        globalDefaults = store.read()
        globalEnabled = store.enabled
    }

    /** True when the user has opted into the Obsidian-Gold premium design system. */
    fun isGloballyEnabled(): Boolean = globalEnabled

    fun avatarFrame(customization: ChannelPremiumCustomization?): AvatarFrameStyle {
        val g = globalDefaults?.takeIf { globalEnabled }
        return PremiumPresets.avatarFramePreset(customization?.avatarFrame ?: g?.avatarFrame).style
    }

    fun resolveForChannel(channel: Channel): ResolvedChannelAppearance =
        resolve(channel.premiumCustomization)

    fun resolve(customization: ChannelPremiumCustomization?): ResolvedChannelAppearance {
        val g = globalDefaults?.takeIf { globalEnabled }
        return ResolvedChannelAppearance(
            accent = PremiumPresets.accent(customization?.accentColorId ?: g?.accentColorId),
            banner = PremiumPresets.banner(customization?.bannerPatternId ?: g?.bannerPatternId),
            emojiPack = PremiumPresets.emojiPack(customization?.emojiPackId ?: g?.emojiPackId),
            fontWeight = PremiumPresets.fontWeight(customization?.fontWeight ?: g?.fontWeight),
            cornerRadius = PremiumPresets.cornerRadius(customization?.postCornerRadius ?: g?.postCornerRadius),
            avatarFrame = PremiumPresets.avatarFramePreset(customization?.avatarFrame ?: g?.avatarFrame),
            postsBackdropEnabled = customization?.postsBackdropEnabled
                ?: g?.postsBackdropEnabled
                ?: false,
        )
    }
}

/**
 * Flat, fully-resolved bundle of appearance knobs. All fields are
 * non-null — unknown server ids resolve to the catalog defaults.
 */
data class ResolvedChannelAppearance(
    val accent: AccentPreset,
    val banner: BannerPreset,
    val emojiPack: EmojiPackPreset,
    val fontWeight: FontWeightPreset,
    val cornerRadius: CornerRadiusPreset,
    val avatarFrame: AvatarFramePreset,
    val postsBackdropEnabled: Boolean,
) {
    val avatarFrameStyle: AvatarFrameStyle get() = avatarFrame.style

    companion object {
        val Default = ResolvedChannelAppearance(
            accent = PremiumPresets.defaultAccent,
            banner = PremiumPresets.defaultBanner,
            emojiPack = PremiumPresets.defaultEmojiPack,
            fontWeight = PremiumPresets.defaultFontWeight,
            cornerRadius = PremiumPresets.defaultCornerRadius,
            avatarFrame = PremiumPresets.defaultAvatarFrame,
            postsBackdropEnabled = false,
        )
    }
}

/**
 * CompositionLocal carrying the currently-resolved appearance. Premium
 * composables inside a channel read this to customize colors / frames /
 * radii without needing the appearance threaded through every call.
 *
 * Defaults to [ResolvedChannelAppearance.Default] so composables outside
 * a channel context (previews, app shell) keep working.
 */
val LocalChannelAppearance = staticCompositionLocalOf { ResolvedChannelAppearance.Default }

/** Short accessor: `ChannelAppearance.current` inside composables. */
object ChannelAppearance {
    val current: ResolvedChannelAppearance
        @Composable
        @ReadOnlyComposable
        get() = LocalChannelAppearance.current
}
