package com.worldmates.messenger.ui.channels.premium.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
        val Light = PremiumDesign(colors = PremiumColorScheme.Light)
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
    /** Vertical background gradient matching the current scheme. */
    fun backgroundBrush(): Brush = Brush.verticalGradient(
        colors = listOf(backgroundElevated, background),
    )

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

        /**
         * Ivory Gold — light-mode twin of the Obsidian scheme. Keeps the
         * matte-gold accent but swaps surfaces to warm ivory and text to
         * deep ink so premium channels read correctly under a light app
         * theme.
         */
        val Light = PremiumColorScheme(
            background = PremiumTokens.IvoryCream,
            backgroundElevated = PremiumTokens.IvoryElevated,
            surface = PremiumTokens.IvorySurface,
            surfaceHigh = PremiumTokens.IvorySurfaceHigh,
            outline = PremiumTokens.IvoryOutline,
            accent = PremiumTokens.MatteGold,
            accentSoft = PremiumTokens.MatteGoldSoft,
            accentDeep = PremiumTokens.MatteGoldDeep,
            onAccent = PremiumTokens.OnIvoryPrimary,
            secondary = PremiumTokens.RoseGold,
            secondarySoft = PremiumTokens.RoseGoldSoft,
            onPrimary = PremiumTokens.OnIvoryPrimary,
            onSecondary = PremiumTokens.OnIvorySecondary,
            onMuted = PremiumTokens.OnIvoryMuted,
            onDisabled = PremiumTokens.OnIvoryDisabled,
            glassStroke = PremiumTokens.GlassStrokeLight,
            glassFill = PremiumTokens.GlassFillLight,
            glassFillStrong = PremiumTokens.GlassFillLightStrong,
            scrim = PremiumTokens.GlassScrimLight,
            success = PremiumTokens.Success,
            warning = PremiumTokens.Warning,
            danger = PremiumTokens.Danger,
            info = PremiumTokens.Info,
            reactionSelectedFill = PremiumTokens.ReactionSelectedFillLight,
            reactionIdleFill = PremiumTokens.ReactionIdleFillLight,
        )
    }
}

private val LocalPremiumDesign = staticCompositionLocalOf { PremiumDesign.Default }

/**
 * Call site for premium UI. Wrap any premium-channel screen/composable
 * with this to make `PremiumDesign.current` resolve correctly.
 *
 * Defaults to following the app's theme state via
 * `com.worldmates.messenger.ui.theme.rememberThemeState()` so the Obsidian
 * Gold (dark) and Ivory Gold (light) skins swap in lock-step with the
 * rest of the app. Callers may still force a specific scheme by passing
 * an explicit `design`.
 */
@Composable
fun PremiumTheme(
    isDark: Boolean = premiumIsAppDark(),
    appearance: ResolvedChannelAppearance = ChannelAppearance.current,
    design: PremiumDesign = rememberPremiumDesign(isDark, appearance),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPremiumDesign provides design,
        LocalChannelAppearance provides appearance,
        LocalContentColor provides design.colors.onPrimary,
        LocalTextStyle provides design.typography.body.copy(color = design.colors.onPrimary),
        content = content,
    )
}

/**
 * Blend the base (theme-adaptive) Obsidian / Ivory palette with the
 * channel's customization: accent triad, title weight, post-card corner
 * radius. Surface tokens stay untouched so every premium channel still
 * reads as part of the same product.
 */
@Composable
private fun rememberPremiumDesign(
    isDark: Boolean,
    appearance: ResolvedChannelAppearance,
): PremiumDesign {
    val base = if (isDark) PremiumDesign.Default else PremiumDesign.Light
    val accent = appearance.accent
    val colors = base.colors.copy(
        accent = accent.base,
        accentSoft = accent.soft,
        accentDeep = accent.deep,
        onAccent = accent.onAccent(isDark),
    )
    val typography = base.typography.copy(
        display = base.typography.display.copy(fontWeight = appearance.fontWeight.title),
        title = base.typography.title.copy(fontWeight = appearance.fontWeight.title),
        titleSmall = base.typography.titleSmall.copy(fontWeight = appearance.fontWeight.title),
    )
    val shapes = base.shapes.copy(cornerMedium = appearance.cornerRadius.value)
    return PremiumDesign(colors = colors, typography = typography, shapes = shapes)
}

/**
 * Resolve whether the app is currently rendering dark. Reads the app's
 * own theme state first (so the user's manual toggle wins), falling back
 * to `isSystemInDarkTheme()` if the theme layer is unavailable (previews,
 * tests).
 */
@Composable
private fun premiumIsAppDark(): Boolean = try {
    com.worldmates.messenger.ui.theme.rememberThemeState().isDark
} catch (t: Throwable) {
    isSystemInDarkTheme()
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
