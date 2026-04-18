package com.worldmates.messenger.ui.channels.premium.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.worldmates.messenger.data.model.Channel
import com.worldmates.messenger.data.model.ChannelPremiumCustomization
import com.worldmates.messenger.ui.channels.premium.components.AvatarFrameStyle

/**
 * Maps the server-sent preset IDs on [ChannelPremiumCustomization] into
 * ready-to-render Compose values. This is the single place where unknown
 * ids fall back to defaults so every other call site can use non-null
 * values without care.
 */
object PremiumCustomizationResolver {

    fun avatarFrame(customization: ChannelPremiumCustomization?): AvatarFrameStyle =
        PremiumPresets.avatarFramePreset(customization?.avatarFrame).style

    /**
     * Resolve a channel's full appearance bundle — used by premium
     * composables for non-theme knobs (frame, pattern, emoji pack). Theme
     * overrides flow through [PremiumDesign] instead because they need to
     * plumb through CompositionLocal.
     */
    fun resolveForChannel(channel: Channel): ResolvedChannelAppearance =
        resolve(channel.premiumCustomization)

    fun resolve(customization: ChannelPremiumCustomization?): ResolvedChannelAppearance {
        return ResolvedChannelAppearance(
            accent = PremiumPresets.accent(customization?.accentColorId),
            banner = PremiumPresets.banner(customization?.bannerPatternId),
            emojiPack = PremiumPresets.emojiPack(customization?.emojiPackId),
            fontWeight = PremiumPresets.fontWeight(customization?.fontWeight),
            cornerRadius = PremiumPresets.cornerRadius(customization?.postCornerRadius),
            avatarFrame = PremiumPresets.avatarFramePreset(customization?.avatarFrame),
            postsBackdropEnabled = customization?.postsBackdropEnabled ?: false,
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
