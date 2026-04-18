package com.worldmates.messenger.ui.channels.premium.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radius scale for premium surfaces. Kept intentionally small so
 * channel customization (14 dp ↔ 22 dp) can freely swap between two
 * shapes without redesigning layout.
 */
data class PremiumShapes(
    val cornerXSmall: Dp = 6.dp,
    val cornerSmall: Dp = 10.dp,
    val cornerMedium: Dp = 14.dp,
    val cornerLarge: Dp = 22.dp,
    val cornerXLarge: Dp = 28.dp,
) {
    val small get() = RoundedCornerShape(cornerSmall)
    val medium get() = RoundedCornerShape(cornerMedium)
    val large get() = RoundedCornerShape(cornerLarge)
    val xLarge get() = RoundedCornerShape(cornerXLarge)
    val pill get() = CircleShape

    companion object {
        val Default = PremiumShapes()
    }
}
