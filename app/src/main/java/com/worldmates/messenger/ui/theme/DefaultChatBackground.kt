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

/**
 * Telegram-like default chat background.
 *
 * Light mode: soft blue-grey gradient (like classic Telegram default).
 * Dark mode:  deep navy gradient (like Telegram Night).
 *
 * A subtle repeating dot pattern is drawn on top of the gradient,
 * just like Telegram's default wallpaper texture.
 */
@Composable
fun DefaultChatBackground(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()

    val gradientColors = if (isDark) {
        listOf(
            Color(0xFF0F1626),
            Color(0xFF1A2640),
            Color(0xFF0F1F38)
        )
    } else {
        listOf(
            Color(0xFFEEF3FA),
            Color(0xFFDAE8F5),
            Color(0xFFEEF3FA)
        )
    }

    val dotColor = if (isDark) Color(0x14FFFFFF) else Color(0x18487CAA)

    Box(modifier = modifier) {
        // Base gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(colors = gradientColors)
                )
        )

        // Dot pattern overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotRadius = 1.8f
            val spacingX = 28f
            val spacingY = 28f
            val cols = (size.width / spacingX).toInt() + 2
            val rows = (size.height / spacingY).toInt() + 2

            for (row in 0..rows) {
                for (col in 0..cols) {
                    val offsetX = if (row % 2 == 0) 0f else spacingX / 2f
                    val x = col * spacingX + offsetX
                    val y = row * spacingY
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}
