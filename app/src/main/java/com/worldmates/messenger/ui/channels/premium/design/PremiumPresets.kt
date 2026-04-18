package com.worldmates.messenger.ui.channels.premium.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.ui.channels.premium.components.AvatarFrameStyle

/**
 * Fixed preset catalogs for premium channel customization.
 *
 * The client and server share a single string protocol — the server
 * stores the preset id (e.g. "rose_gold"), the client resolves it to
 * concrete Compose values. New presets are added here and mirrored on
 * the server; the client always falls back to the default when an id
 * is unknown so older clients never crash on new server data.
 */
object PremiumPresets {

    // ── Accent palettes ──────────────────────────────────────────────────────
    //
    // Every accent ships a soft / base / deep triad plus an onAccent text
    // color for legibility on top of the accent. Gold stays the system
    // default — alternates are drawn from the same muted-jewel family so
    // all channels feel like the same product.
    val accents: List<AccentPreset> = listOf(
        AccentPreset(
            id = "gold",
            displayName = "Matte Gold",
            base = Color(0xFFD4AF37),
            soft = Color(0xFFE8C877),
            deep = Color(0xFFB48A1F),
            onAccentDark = Color(0xFF0A0A0F),
            onAccentLight = Color(0xFF1A1611),
        ),
        AccentPreset(
            id = "rose_gold",
            displayName = "Rose Gold",
            base = Color(0xFFB76E79),
            soft = Color(0xFFD9A6AE),
            deep = Color(0xFF8F4E58),
            onAccentDark = Color(0xFF0A0A0F),
            onAccentLight = Color(0xFFFFF5F0),
        ),
        AccentPreset(
            id = "emerald",
            displayName = "Emerald",
            base = Color(0xFF3FB97C),
            soft = Color(0xFF7ED9A8),
            deep = Color(0xFF228056),
            onAccentDark = Color(0xFF08130D),
            onAccentLight = Color(0xFFECFBF3),
        ),
        AccentPreset(
            id = "sapphire",
            displayName = "Sapphire",
            base = Color(0xFF5A8AE0),
            soft = Color(0xFF9FB7EA),
            deep = Color(0xFF2D5AA8),
            onAccentDark = Color(0xFF06101F),
            onAccentLight = Color(0xFFEFF3FA),
        ),
        AccentPreset(
            id = "amethyst",
            displayName = "Amethyst",
            base = Color(0xFF9A6FD0),
            soft = Color(0xFFBFA0E4),
            deep = Color(0xFF6B4CA0),
            onAccentDark = Color(0xFF0F0719),
            onAccentLight = Color(0xFFF3EEF9),
        ),
        AccentPreset(
            id = "crimson",
            displayName = "Crimson",
            base = Color(0xFFD4555C),
            soft = Color(0xFFE88B91),
            deep = Color(0xFFA03940),
            onAccentDark = Color(0xFF170507),
            onAccentLight = Color(0xFFFBECEE),
        ),
    )

    val defaultAccent: AccentPreset get() = accents.first()

    fun accent(id: String?): AccentPreset =
        accents.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: defaultAccent

    // ── Banner patterns ──────────────────────────────────────────────────────
    //
    // The hero header currently uses a flat gradient; banners add an
    // overlay pattern on top. Patterns are procedural (not PNG assets) so
    // they stay crisp at any density and theme-adapt automatically.
    val banners: List<BannerPreset> = listOf(
        BannerPreset("none",        "None"),
        BannerPreset("dots",        "Dots"),
        BannerPreset("diagonal",    "Diagonal Weave"),
        BannerPreset("diamond",     "Diamond Grid"),
        BannerPreset("hex",         "Hex Mesh"),
        BannerPreset("aurora_veil", "Aurora Veil"),
    )

    val defaultBanner: BannerPreset get() = banners.first()

    fun banner(id: String?): BannerPreset =
        banners.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: defaultBanner

    // ── Emoji packs ──────────────────────────────────────────────────────────
    //
    // Used for the reaction row's default palette and the channel "emoji
    // status" suggestion list. Channels pick one pack; individual emojis
    // inside a post are unchanged.
    val emojiPacks: List<EmojiPackPreset> = listOf(
        EmojiPackPreset(
            id = "classic",
            displayName = "Classic",
            sample = "\uD83D\uDD25", // 🔥
            reactions = listOf("❤\uFE0F", "\uD83D\uDD25", "\uD83D\uDC4F", "\uD83D\uDE02", "\uD83D\uDE32", "\uD83D\uDE22", "\uD83D\uDC4E"),
            statusChoices = listOf("⭐\uFE0F", "\uD83D\uDC8E", "\uD83D\uDC51", "\uD83C\uDFC6", "\uD83D\uDD25"),
        ),
        EmojiPackPreset(
            id = "celebration",
            displayName = "Celebration",
            sample = "\uD83C\uDF89", // 🎉
            reactions = listOf("\uD83C\uDF89", "\uD83C\uDF8A", "\uD83E\uDD73", "\uD83C\uDF81", "\uD83C\uDF82", "\uD83C\uDF7E", "\uD83D\uDC83"),
            statusChoices = listOf("\uD83C\uDF89", "\uD83E\uDD73", "\uD83C\uDF81", "\uD83C\uDF1F", "✨"),
        ),
        EmojiPackPreset(
            id = "nature",
            displayName = "Nature",
            sample = "\uD83C\uDF3F", // 🌿
            reactions = listOf("\uD83C\uDF3F", "\uD83C\uDF38", "\uD83C\uDF3A", "\uD83C\uDF3B", "\uD83C\uDF44", "\uD83C\uDF42", "\uD83C\uDF31"),
            statusChoices = listOf("\uD83C\uDF38", "\uD83C\uDF3F", "\uD83C\uDF3A", "\uD83C\uDF3B", "\uD83C\uDF3F"),
        ),
        EmojiPackPreset(
            id = "finance",
            displayName = "Finance",
            sample = "\uD83D\uDCC8", // 📈
            reactions = listOf("\uD83D\uDCC8", "\uD83D\uDCB0", "\uD83D\uDCB8", "\uD83D\uDC8E", "\uD83D\uDCAF", "\uD83D\uDD25", "\uD83D\uDC4D"),
            statusChoices = listOf("\uD83D\uDCC8", "\uD83D\uDCB0", "\uD83D\uDC8E", "\uD83D\uDCAF", "\uD83E\uDD48"),
        ),
        EmojiPackPreset(
            id = "cosmos",
            displayName = "Cosmos",
            sample = "\uD83C\uDF0C", // 🌌
            reactions = listOf("\uD83C\uDF0C", "\uD83C\uDF1F", "✨", "\uD83C\uDF19", "☄\uFE0F", "\uD83D\uDE80", "\uD83D\uDC7D"),
            statusChoices = listOf("\uD83C\uDF1F", "\uD83C\uDF0C", "\uD83C\uDF19", "☄\uFE0F", "✨"),
        ),
    )

    val defaultEmojiPack: EmojiPackPreset get() = emojiPacks.first()

    fun emojiPack(id: String?): EmojiPackPreset =
        emojiPacks.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: defaultEmojiPack

    // ── Font weight presets ──────────────────────────────────────────────────
    //
    // Applies to titles + section headers. Body weight stays fixed so
    // long-form posts remain readable. We ship just three choices — the
    // intent is mood, not fine-grained control.
    val fontWeights: List<FontWeightPreset> = listOf(
        FontWeightPreset(id = "editorial", displayName = "Editorial", title = FontWeight.SemiBold),
        FontWeightPreset(id = "classic",   displayName = "Classic",   title = FontWeight.Bold),
        FontWeightPreset(id = "display",   displayName = "Display",   title = FontWeight.ExtraBold),
    )

    val defaultFontWeight: FontWeightPreset get() = fontWeights[1]

    fun fontWeight(id: String?): FontWeightPreset =
        fontWeights.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: defaultFontWeight

    // ── Corner radius presets ────────────────────────────────────────────────
    //
    // Matches the two base PremiumShapes presets — cornerMedium is the
    // "post card" radius. The preset id is stored as an int on the
    // server (post_corner_radius) so this map is int → preset.
    val cornerRadii: List<CornerRadiusPreset> = listOf(
        CornerRadiusPreset(value = 10.dp, displayName = "Crisp"),
        CornerRadiusPreset(value = 14.dp, displayName = "Classic"),
        CornerRadiusPreset(value = 18.dp, displayName = "Soft"),
        CornerRadiusPreset(value = 22.dp, displayName = "Pillow"),
    )

    val defaultCornerRadius: CornerRadiusPreset get() = cornerRadii[1]

    fun cornerRadius(value: Int?): CornerRadiusPreset {
        val v = value ?: return defaultCornerRadius
        return cornerRadii.minByOrNull { kotlin.math.abs(it.value.value.toInt() - v) }
            ?: defaultCornerRadius
    }

    // ── Avatar frame presets ─────────────────────────────────────────────────
    //
    // Thin wrapper over AvatarFrameStyle that adds a display name for the
    // settings screen. Draws are already defined inside PremiumChannelAvatar.
    val avatarFrames: List<AvatarFramePreset> = listOf(
        AvatarFramePreset("gold_ring",       "Gold Ring",        AvatarFrameStyle.GoldRing),
        AvatarFramePreset("gold_double",     "Gold Double",      AvatarFrameStyle.GoldDoubleRing),
        AvatarFramePreset("rose_gold_halo",  "Rose Halo",        AvatarFrameStyle.RoseGoldHalo),
        AvatarFramePreset("aurora_gradient", "Aurora Gradient",  AvatarFrameStyle.AuroraGradient),
        AvatarFramePreset("engraved_notch",  "Engraved Notch",   AvatarFrameStyle.EngravedNotch),
        AvatarFramePreset("crystal_edge",    "Crystal Edge",     AvatarFrameStyle.CrystalEdge),
        AvatarFramePreset("none",            "No Frame",         AvatarFrameStyle.None),
    )

    val defaultAvatarFrame: AvatarFramePreset get() = avatarFrames.first()

    fun avatarFramePreset(id: String?): AvatarFramePreset =
        avatarFrames.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: defaultAvatarFrame
}

/** Accent color triad plus the text colors that read on top of it. */
data class AccentPreset(
    val id: String,
    val displayName: String,
    val base: Color,
    val soft: Color,
    val deep: Color,
    val onAccentDark: Color,
    val onAccentLight: Color,
) {
    /** Text color that reads on top of [base] given the current scheme. */
    fun onAccent(isDark: Boolean): Color = if (isDark) onAccentDark else onAccentLight
}

/** Procedural background pattern painted over the hero / list-card header. */
data class BannerPreset(val id: String, val displayName: String)

/** A bundle of emojis used for the default reactions row + status suggestions. */
data class EmojiPackPreset(
    val id: String,
    val displayName: String,
    val sample: String,
    val reactions: List<String>,
    val statusChoices: List<String>,
)

/** Title weight preset — body weight stays fixed. */
data class FontWeightPreset(val id: String, val displayName: String, val title: FontWeight)

/** Discrete post-card corner radius choice. */
data class CornerRadiusPreset(val value: Dp, val displayName: String)

/** Named avatar frame drawable. */
data class AvatarFramePreset(val id: String, val displayName: String, val style: AvatarFrameStyle)
