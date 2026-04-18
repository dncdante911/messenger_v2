package com.worldmates.messenger.ui.channels.premium.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Re-usable gradients for the premium UI. All brushes only pull colors
 * from PremiumTokens so channel customization can swap the accent family
 * in one place.
 */
object PremiumBrushes {

    /** Subtle vertical obsidian background used on full-screen surfaces. */
    fun obsidianVertical(
        top: Color = PremiumTokens.ObsidianElevated,
        bottom: Color = PremiumTokens.ObsidianBlack,
    ): Brush = Brush.verticalGradient(
        colors = listOf(top, bottom),
    )

    /** Matte-gold button fill — three stops to avoid flat plastic look. */
    fun matteGoldLinear(
        highlight: Color = PremiumTokens.MatteGoldSoft,
        base: Color = PremiumTokens.MatteGold,
        shadow: Color = PremiumTokens.MatteGoldDeep,
    ): Brush = Brush.linearGradient(
        colors = listOf(highlight, base, shadow),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
    )

    /** Rose-gold companion gradient for secondary pills / alt avatar frames. */
    fun roseGoldLinear(): Brush = Brush.linearGradient(
        colors = listOf(
            PremiumTokens.RoseGoldSoft,
            PremiumTokens.RoseGold,
            PremiumTokens.RoseGoldDeep,
        ),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
    )

    /**
     * Aurora iridescent highlight. Only for active/press states and Level-Up
     * moments — never as a background.
     */
    fun auroraHighlight(): Brush = Brush.linearGradient(
        colors = listOf(
            PremiumTokens.AuroraIndigo,
            PremiumTokens.AuroraViolet,
            PremiumTokens.AuroraRose,
        ),
    )

    /** Obsidian scrim behind text over media. */
    fun obsidianScrim(): Brush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, PremiumTokens.GlassScrim),
    )

    /** Gold hairline for glass borders. */
    fun goldHairline(): Brush = Brush.linearGradient(
        colors = listOf(
            PremiumTokens.GlassStroke,
            PremiumTokens.GlassStrokeStrong,
            PremiumTokens.GlassStroke,
        ),
    )
}
