package com.worldmates.messenger.ui.channels.premium.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Premium design bundle — colors, typography, shapes, motion.
 *
 * Exposed as a CompositionLocal so individual channels can override a
 * subset (accent color, corner radius, etc.) without re-implementing
 * the whole design. Access inside a composable via `PremiumDesign.current`.
 */
@Immutable
data class PremiumDesign(
    val colors: PremiumColorScheme = PremiumColorScheme.Default,
    val typography: PremiumTypography = PremiumTypography.Default,
    val shapes: PremiumShapes = PremiumShapes.Default,
) {
    companion object {
        val Default = PremiumDesign()
    }
}

/**
 * Resolved color slots. Keeps raw tokens private — components reference
 * semantic roles (accent / onAccent / background / …) rather than
 * hex codes or raw token names.
 */
@Immutable
data class PremiumColorScheme(
    val background: androidx.compose.ui.graphics.Color,
    val backgroundElevated: androidx.compose.ui.graphics.Color,
    val surface: androidx.compose.ui.graphics.Color,
    val surfaceHigh: androidx.compose.ui.graphics.Color,
    val outline: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
    val accentSoft: androidx.compose.ui.graphics.Color,
    val accentDeep: androidx.compose.ui.graphics.Color,
    val onAccent: androidx.compose.ui.graphics.Color,
    val secondary: androidx.compose.ui.graphics.Color,
    val secondarySoft: androidx.compose.ui.graphics.Color,
    val onPrimary: androidx.compose.ui.graphics.Color,
    val onSecondary: androidx.compose.ui.graphics.Color,
    val onMuted: androidx.compose.ui.graphics.Color,
    val onDisabled: androidx.compose.ui.graphics.Color,
    val glassStroke: androidx.compose.ui.graphics.Color,
    val glassFill: androidx.compose.ui.graphics.Color,
    val glassFillStrong: androidx.compose.ui.graphics.Color,
    val scrim: androidx.compose.ui.graphics.Color,
    val success: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color,
    val danger: androidx.compose.ui.graphics.Color,
    val info: androidx.compose.ui.graphics.Color,
    val reactionSelectedFill: androidx.compose.ui.graphics.Color,
    val reactionIdleFill: androidx.compose.ui.graphics.Color,
) {
    companion object {
        val Default = PremiumColorScheme(
            background = PremiumTokens.ObsidianBlack,
            backgroundElevated = PremiumTokens.ObsidianElevated,
            surface = PremiumTokens.ObsidianSurface,
            surfaceHigh = PremiumTokens.ObsidianSurfaceHigh,
            outline = PremiumTokens.ObsidianOutline,
            accent = PremiumTokens.MatteGold,
            accentSoft = PremiumTokens.MatteGoldSoft,
            accentDeep = PremiumTokens.MatteGoldDeep,
            onAccent = PremiumTokens.ObsidianBlack,
            secondary = PremiumTokens.RoseGold,
            secondarySoft = PremiumTokens.RoseGoldSoft,
            onPrimary = PremiumTokens.OnObsidianPrimary,
            onSecondary = PremiumTokens.OnObsidianSecondary,
            onMuted = PremiumTokens.OnObsidianMuted,
            onDisabled = PremiumTokens.OnObsidianDisabled,
            glassStroke = PremiumTokens.GlassStroke,
            glassFill = PremiumTokens.GlassFill,
            glassFillStrong = PremiumTokens.GlassFillStrong,
            scrim = PremiumTokens.GlassScrim,
            success = PremiumTokens.Success,
            warning = PremiumTokens.Warning,
            danger = PremiumTokens.Danger,
            info = PremiumTokens.Info,
            reactionSelectedFill = PremiumTokens.ReactionSelectedFill,
            reactionIdleFill = PremiumTokens.ReactionIdleFill,
        )
    }
}

private val LocalPremiumDesign = staticCompositionLocalOf { PremiumDesign.Default }

/**
 * Call site for premium UI. Wrap any premium-channel screen/composable
 * with this to make `PremiumDesign.current` resolve correctly.
 *
 * The `isDark` parameter is kept for future-proofing — a light-mode
 * premium skin would require a separate `PremiumColorScheme`. Today the
 * premium UI is always dark (Obsidian), so the flag is ignored.
 */
@Composable
fun PremiumTheme(
    @Suppress("UNUSED_PARAMETER") isDark: Boolean = isSystemInDarkTheme(),
    design: PremiumDesign = PremiumDesign.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPremiumDesign provides design,
        LocalContentColor provides design.colors.onPrimary,
        LocalTextStyle provides design.typography.body.copy(color = design.colors.onPrimary),
        content = content,
    )
}

/** Short accessor — `PremiumDesign.current` inside composables. */
object PremiumDesignAccess {
    val current: PremiumDesign
        @Composable
        @ReadOnlyComposable
        get() = LocalPremiumDesign.current
}

/** Convention: `PremiumDesign.current` reads the composition-local design. */
val PremiumDesign.Companion.current: PremiumDesign
    @Composable
    @ReadOnlyComposable
    get() = LocalPremiumDesign.current
