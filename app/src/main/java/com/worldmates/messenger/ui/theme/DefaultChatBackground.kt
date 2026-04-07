package com.worldmates.messenger.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

/**
 * Default chat background — used on ALL screens when no custom image or preset is set.
 * Inspired by Telegram's default wallpaper: a 4-point mesh gradient that blends
 * teal, blue, lavender, and peach blobs, with a subtle hex-dot texture on top.
 *
 * Light mode: warm off-white base + pastel mesh (sky-blue / lavender / peach / mint).
 * Dark mode:  Telegram Night — deep navy + indigo + teal blobs on #17212B base.
 */
@Composable
fun DefaultChatBackground(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (!isDark) {
                // ── Light mode ──────────────────────────────────────────────
                // 1. Warm off-white base
                drawRect(color = Color(0xFFF2ECE6))

                // 2. Four overlapping radial blobs (mesh gradient effect)
                //    Top-left  → sky blue
                //    Top-right → soft lavender
                //    Bottom-left → mint green
                //    Bottom-right → warm peach
                val blobR = maxOf(w, h) * 0.85f

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF9EC9E8), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = blobR,
                        tileMode = TileMode.Clamp
                    ),
                    size = Size(w, h)
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFB8A0E8), Color.Transparent),
                        center = Offset(w, 0f),
                        radius = blobR,
                        tileMode = TileMode.Clamp
                    ),
                    size = Size(w, h)
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF8ECFB0), Color.Transparent),
                        center = Offset(0f, h),
                        radius = blobR,
                        tileMode = TileMode.Clamp
                    ),
                    size = Size(w, h)
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFE8B090), Color.Transparent),
                        center = Offset(w, h),
                        radius = blobR,
                        tileMode = TileMode.Clamp
                    ),
                    size = Size(w, h)
                )

                // 3. Light white overlay in center to keep readability
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xA0FFFFFF), Color.Transparent),
                        center = Offset(w / 2f, h / 2f),
                        radius = minOf(w, h) * 0.55f,
                        tileMode = TileMode.Clamp
                    ),
                    size = Size(w, h)
                )

                // 4. Hex-dot texture
                val dotColor = Color(0x30305870)
                val spacingX = 26f
                val spacingY = spacingX * 0.866f
                val cols = (w / spacingX).toInt() + 2
                val rows = (h / spacingY).toInt() + 2
                for (row in 0..rows) {
                    for (col in 0..cols) {
                        val offsetX = if (row % 2 == 0) 0f else spacingX / 2f
                        drawCircle(
                            color = dotColor,
                            radius = 1.5f,
                            center = Offset(col * spacingX + offsetX, row * spacingY)
                        )
                    }
                }

            } else {
                // ── Dark mode (Telegram Night) ──────────────────────────────
                // 1. #17212B base — classic Telegram Night color
                drawRect(color = Color(0xFF17212B))

                val blobR = maxOf(w, h) * 0.85f

                // Top-left → deep indigo
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2A1F55), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = blobR,
                        tileMode = TileMode.Clamp
                    ),
                    size = Size(w, h)
                )
                // Top-right → dark teal
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF0F3040), Color.Transparent),
                        center = Offset(w, 0f),
                        radius = blobR,
                        tileMode = TileMode.Clamp
                    ),
                    size = Size(w, h)
                )
                // Bottom-left → navy blue
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF152540), Color.Transparent),
                        center = Offset(0f, h),
                        radius = blobR,
                        tileMode = TileMode.Clamp
                    ),
                    size = Size(w, h)
                )
                // Bottom-right → dark violet
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF221535), Color.Transparent),
                        center = Offset(w, h),
                        radius = blobR,
                        tileMode = TileMode.Clamp
                    ),
                    size = Size(w, h)
                )

                // Hex-dot texture
                val dotColor = Color(0x20FFFFFF)
                val spacingX = 26f
                val spacingY = spacingX * 0.866f
                val cols = (w / spacingX).toInt() + 2
                val rows = (h / spacingY).toInt() + 2
                for (row in 0..rows) {
                    for (col in 0..cols) {
                        val offsetX = if (row % 2 == 0) 0f else spacingX / 2f
                        drawCircle(
                            color = dotColor,
                            radius = 1.5f,
                            center = Offset(col * spacingX + offsetX, row * spacingY)
                        )
                    }
                }
            }
        }
    }
}
