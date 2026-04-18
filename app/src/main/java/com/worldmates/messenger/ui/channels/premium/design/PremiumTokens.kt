package com.worldmates.messenger.ui.channels.premium.design

import androidx.compose.ui.graphics.Color

/**
 * Obsidian Gold token palette for the premium channels UI.
 *
 * 80-85% matte gold on deep black, with a rose-gold companion and a
 * contained aurora triad that is only used for active/press highlights.
 * These tokens are the single source of truth for premium colors — no
 * premium composable should declare raw hex codes.
 */
object PremiumTokens {

    // ── Obsidian surfaces ────────────────────────────────────────────────────
    val ObsidianBlack = Color(0xFF0A0A0F)
    val ObsidianElevated = Color(0xFF12121A)
    val ObsidianSurface = Color(0xFF1A1A26)
    val ObsidianSurfaceHigh = Color(0xFF22222F)
    val ObsidianOutline = Color(0xFF2A2A38)

    // ── Gold family (primary accent) ─────────────────────────────────────────
    val MatteGold = Color(0xFFD4AF37)
    val MatteGoldSoft = Color(0xFFE8C877)
    val MatteGoldDeep = Color(0xFFB48A1F)
    val GoldInk = Color(0xFF8F7520)

    // ── Rose gold (secondary accent) ─────────────────────────────────────────
    val RoseGold = Color(0xFFB76E79)
    val RoseGoldSoft = Color(0xFFD9A6AE)
    val RoseGoldDeep = Color(0xFF8F4E58)

    // ── Aurora highlight triad (press / active only) ─────────────────────────
    val AuroraIndigo = Color(0xFF6B5FD3)
    val AuroraViolet = Color(0xFF9D6FD3)
    val AuroraRose = Color(0xFFD36F9F)

    // ── Glass layer (blur + polyfill) ────────────────────────────────────────
    val GlassStroke = Color(0x33D4AF37)        // gold hairline 20%
    val GlassStrokeStrong = Color(0x66D4AF37)  // gold hairline 40%
    val GlassFill = Color(0x14FFFFFF)          // frosted 8%
    val GlassFillStrong = Color(0x24FFFFFF)    // frosted 14%
    val GlassScrim = Color(0x660A0A0F)         // obsidian scrim 40%

    // ── Foreground (text / icons) ────────────────────────────────────────────
    val OnObsidianPrimary = Color(0xFFF5F0E8)     // warm off-white
    val OnObsidianSecondary = Color(0xB3F5F0E8)   // 70%
    val OnObsidianMuted = Color(0x80F5F0E8)       // 50%
    val OnObsidianDisabled = Color(0x4DF5F0E8)    // 30%

    // ── Semantic ─────────────────────────────────────────────────────────────
    val Success = Color(0xFF3FB36B)
    val Warning = Color(0xFFE8A03C)
    val Danger = Color(0xFFE05260)
    val Info = Color(0xFF5FA8E8)

    // ── Reaction chip fill (pressed) ─────────────────────────────────────────
    val ReactionSelectedFill = Color(0x33D4AF37)
    val ReactionIdleFill = Color(0x0DFFFFFF)
}
