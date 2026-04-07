package com.worldmates.messenger.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Telegram-like default chat background — consistent across all screens.
 *
 * Renders four radial "blob" gradients at the screen corners, blending
 * together for a modern multi-color wash (matches Telegram's default wallpaper).
 * A subtle hex-dot texture is drawn on top.
 *
 * Light mode: peach / lavender / teal / sky-blue blobs.
 * Dark mode:  deep navy / indigo / dark-teal / midnight-blue blobs.
 */
@Composable
fun DefaultChatBackground(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()

    // ── Blob colors (4 corners: top-left, top-right, bottom-left, bottom-right) ──
    val blobTL: Color
    val blobTR: Color
    val blobBL: Color
    val blobBR: Color
    val baseColor: Color
    val dotColor: Color

    if (isDark) {
        baseColor = Color(0xFF0D1520)
        blobTL    = Color(0x992D1B4E)   // deep indigo
        blobTR    = Color(0x991A3040)   // dark teal
        blobBL    = Color(0x991C2E50)   // dark navy
        blobBR    = Color(0x99251A3D)   // dark violet
        dotColor  = Color(0x18FFFFFF)
    } else {
        baseColor = Color(0xFFF0EEE9)
        blobTL    = Color(0xCCFFD9C8)   // warm peach
        blobTR    = Color(0xCCC8E6FF)   // sky blue
        blobBL    = Color(0xCCE6D5FF)   // soft lavender
        blobBR    = Color(0xCCC0F0E0)   // mint
        dotColor  = Color(0x22456A8A)
    }

    Box(modifier = modifier) {
        // ── 1. Base fill ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor)
        )

        // ── 2. Four corner blobs + hex-dot pattern ────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val blobRadius = maxOf(w, h) * 0.78f

            // Top-left blob
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blobTL, Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = blobRadius,
                    tileMode = TileMode.Clamp
                ),
                radius = blobRadius,
                center = Offset(0f, 0f)
            )
            // Top-right blob
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blobTR, Color.Transparent),
                    center = Offset(w, 0f),
                    radius = blobRadius,
                    tileMode = TileMode.Clamp
                ),
                radius = blobRadius,
                center = Offset(w, 0f)
            )
            // Bottom-left blob
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blobBL, Color.Transparent),
                    center = Offset(0f, h),
                    radius = blobRadius,
                    tileMode = TileMode.Clamp
                ),
                radius = blobRadius,
                center = Offset(0f, h)
            )
            // Bottom-right blob
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blobBR, Color.Transparent),
                    center = Offset(w, h),
                    radius = blobRadius,
                    tileMode = TileMode.Clamp
                ),
                radius = blobRadius,
                center = Offset(w, h)
            )

            // ── 3. Hex-dot texture overlay ────────────────────────────────────
            val dotRadius = 1.6f
            val spacingX  = 26f
            // Row height for a hex grid: spacingX * sin(60°) ≈ spacingX * 0.866
            val spacingY  = spacingX * 0.866f
            val cols = (w / spacingX).toInt() + 2
            val rows = (h / spacingY).toInt() + 2

            for (row in 0..rows) {
                for (col in 0..cols) {
                    val offsetX = if (row % 2 == 0) 0f else spacingX / 2f
                    drawCircle(
                        color  = dotColor,
                        radius = dotRadius,
                        center = Offset(col * spacingX + offsetX, row * spacingY)
                    )
                }
            }
        }
    }
}
