package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.current

/**
 * Frosted-glass container. Uses a translucent fill + gold hairline border
 * to suggest depth without blurring the contained content (a real backdrop
 * blur would require a two-pass render into the parent brush, which is
 * beyond what [Modifier.blur] offers — it blurs the subtree including
 * our own text, which makes the panel look broken).
 *
 * Use for FAB, picker sheets, floating action chips on top of media.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    strong: Boolean = false,
    borderBrush: Brush = PremiumBrushes.goldHairline(),
    content: @Composable () -> Unit,
) {
    val design = PremiumDesign.current
    val fill = if (strong) design.colors.glassFillStrong else design.colors.glassFill

    val glassModifier = modifier
        .clip(shape)
        .background(fill, shape)
        .border(width = 0.6.dp, brush = borderBrush, shape = shape)

    Box(modifier = glassModifier) { content() }
}

/** Transparent ring with a gold hairline — use behind avatars/buttons. */
@Composable
fun PremiumHairlineOutline(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    color: Color = PremiumDesign.current.colors.glassStroke,
    width: Dp = 0.6.dp,
) {
    Box(modifier.border(width = width, color = color, shape = shape))
}
