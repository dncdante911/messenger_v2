package com.worldmates.messenger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.worldmates.messenger.R

/**
 * Premium Display Font Families for WorldMates Premium UI.
 *
 * All fonts are loaded via Google Fonts (GMS font provider) — no bundled TTF needed.
 * They download once, then are cached on-device. Graceful fallback to system sans-serif
 * if GMS is unavailable (e.g. emulator without Play Services).
 *
 * Available fonts:
 *  - [Exo2FontFamily]    — Futuristic geometric, multi-weight. Best for channel names.
 *  - [RussoOneFontFamily]— Bold authoritative display. Best for big headings.
 *  - [RighteousFontFamily]— Rounded friendly-bold. Best for badges & labels.
 *  - [OrbitronFontFamily]— Sci-fi volumetric. Best for stats & brand moments.
 *
 * Usage in Compose:
 * ```kotlin
 * Text(
 *     text = channelName,
 *     fontFamily = PremiumFonts.Exo2,
 *     fontWeight = FontWeight.Bold
 * )
 * ```
 */
object PremiumFonts {

    /**
     * Exo 2 — geometric futuristic, supports full weight range (100–900).
     * Great for channel titles, subscriber counts, post headings.
     */
    val Exo2: FontFamily by lazy {
        FontFamily(
            Font(
                resId = R.font.exo2,
                weight = FontWeight.Normal,
                style = FontStyle.Normal,
                loadingStrategy = FontLoadingStrategy.OptionalLocal
            ),
            Font(
                resId = R.font.exo2_bold,
                weight = FontWeight.Bold,
                style = FontStyle.Normal,
                loadingStrategy = FontLoadingStrategy.OptionalLocal
            )
        )
    }

    /**
     * Russo One — wide bold geometric display (single weight 400 = renders as Bold).
     * Authoritative. Great for section titles and channel names in explore.
     */
    val RussoOne: FontFamily by lazy {
        FontFamily(
            Font(
                resId = R.font.russo_one,
                weight = FontWeight.Normal,
                style = FontStyle.Normal,
                loadingStrategy = FontLoadingStrategy.OptionalLocal
            )
        )
    }

    /**
     * Righteous — rounded bold, friendly yet strong.
     * Great for premium badges, category tags, action button labels.
     */
    val Righteous: FontFamily by lazy {
        FontFamily(
            Font(
                resId = R.font.righteous,
                weight = FontWeight.Normal,
                style = FontStyle.Normal,
                loadingStrategy = FontLoadingStrategy.OptionalLocal
            )
        )
    }

    /**
     * Orbitron — sci-fi/tech display with volumetric weight.
     * Great for stats, live indicators, and brand accent moments.
     */
    val Orbitron: FontFamily by lazy {
        FontFamily(
            Font(
                resId = R.font.orbitron,
                weight = FontWeight.Bold,
                style = FontStyle.Normal,
                loadingStrategy = FontLoadingStrategy.OptionalLocal
            )
        )
    }
}

/**
 * Composable helper — returns [PremiumFonts.Exo2] remembered across recompositions.
 * Use this when you need the font inside a Composable without creating a new object each time.
 */
@Composable
fun rememberExo2(): FontFamily = remember { PremiumFonts.Exo2 }

@Composable
fun rememberRussoOne(): FontFamily = remember { PremiumFonts.RussoOne }

@Composable
fun rememberRighteous(): FontFamily = remember { PremiumFonts.Righteous }

@Composable
fun rememberOrbitron(): FontFamily = remember { PremiumFonts.Orbitron }
