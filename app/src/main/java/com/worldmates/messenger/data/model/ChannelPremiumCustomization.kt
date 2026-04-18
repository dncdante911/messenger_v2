package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * Per-channel premium appearance. Only channels whose owner has an
 * active premium subscription populate this object — for everyone else
 * the field is null and the UI falls back to defaults.
 *
 * Phase 3 introduces the data carrier. Phase 5 adds:
 *   - the Appearance settings screen,
 *   - API endpoints (GET/PUT customization),
 *   - resolved composable state via CompositionLocal.
 *
 * All fields are string IDs (e.g. "gold", "aurora") because the client
 * must not be trusted with raw HEX — the server validates against a
 * fixed preset list.
 */
data class ChannelPremiumCustomization(
    @SerializedName("accent_color_id") val accentColorId: String? = null,
    @SerializedName("banner_pattern_id") val bannerPatternId: String? = null,
    @SerializedName("emoji_pack_id") val emojiPackId: String? = null,
    @SerializedName("font_weight") val fontWeight: String? = null,
    @SerializedName("post_corner_radius") val postCornerRadius: Int? = null,
    @SerializedName("avatar_frame") val avatarFrame: String? = null,
    @SerializedName("posts_backdrop_enabled") val postsBackdropEnabled: Boolean = false,
)
