package com.worldmates.messenger.ui.channels.premium.design

import com.worldmates.messenger.data.model.Channel
import com.worldmates.messenger.data.model.ChannelPremiumCustomization
import com.worldmates.messenger.ui.channels.premium.components.AvatarFrameStyle

/**
 * Maps server-sent preset IDs to client-side enums. Keeps the string
 * protocol decoupled from Kotlin enum ordinals and is the single place
 * where unknown IDs fall back to sensible defaults.
 *
 * Phase 3 resolves only the avatar frame; Phase 5 adds accent / banner
 * / emoji-pack / font weight / corner radius mappings.
 */
object PremiumCustomizationResolver {

    fun avatarFrame(customization: ChannelPremiumCustomization?): AvatarFrameStyle {
        val id = customization?.avatarFrame?.lowercase() ?: return AvatarFrameStyle.GoldRing
        return when (id) {
            "none" -> AvatarFrameStyle.None
            "gold_ring", "gold" -> AvatarFrameStyle.GoldRing
            "gold_double", "gold_double_ring" -> AvatarFrameStyle.GoldDoubleRing
            "rose_gold_halo", "rose_gold", "rose" -> AvatarFrameStyle.RoseGoldHalo
            "aurora_gradient", "aurora" -> AvatarFrameStyle.AuroraGradient
            "engraved_notch", "engraved" -> AvatarFrameStyle.EngravedNotch
            "crystal_edge", "crystal" -> AvatarFrameStyle.CrystalEdge
            else -> AvatarFrameStyle.GoldRing
        }
    }

    /**
     * Single entry point used by premium composables: returns sensible
     * defaults for a non-premium channel so the callers never need to
     * null-check.
     */
    fun resolveForChannel(channel: Channel): ResolvedChannelAppearance {
        val c = channel.premiumCustomization
        return ResolvedChannelAppearance(
            avatarFrame = avatarFrame(c),
        )
    }
}

/**
 * Flat, fully-resolved bundle of appearance knobs. Phase 5 extends this
 * with accent color, banner pattern, corner radius, etc.
 */
data class ResolvedChannelAppearance(
    val avatarFrame: AvatarFrameStyle,
)
