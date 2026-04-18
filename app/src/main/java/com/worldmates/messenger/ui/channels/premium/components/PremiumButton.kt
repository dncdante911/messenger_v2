package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.current

/** Filled matte-gold pill — primary CTA. */
@Composable
fun PremiumPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val design = PremiumDesign.current
    val shape = design.shapes.pill
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "primaryScale")

    Box(
        modifier = modifier
            .scale(scale)
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(PremiumBrushes.matteGoldLinear(), shape)
            .then(if (pressed) Modifier.background(PremiumBrushes.auroraHighlight(), shape) else Modifier)
            .border(width = 0.8.dp, brush = PremiumBrushes.goldHairline(), shape = shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = design.colors.onAccent),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = design.typography.button,
                color = design.colors.onAccent,
            )
            trailing?.invoke()
        }
    }
}

/** Outlined gold pill — secondary CTA. */
@Composable
fun PremiumSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val design = PremiumDesign.current
    val shape = design.shapes.pill
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (pressed) design.colors.glassFillStrong else design.colors.glassFill,
        label = "secondaryBg",
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bg, shape)
            .border(width = 1.dp, brush = PremiumBrushes.goldHairline(), shape = shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = design.colors.accent),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = design.typography.button,
                color = design.colors.accent,
            )
        }
    }
}

/** Text-only action — for tertiary actions inside cards. */
@Composable
fun PremiumGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val design = PremiumDesign.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = design.typography.button,
            color = design.colors.onSecondary,
        )
    }
}

/**
 * Circular icon button sitting on a glass pill — used for back/settings
 * in the hero header. Exposes `content` so callers drop the icon in.
 */
@Composable
fun PremiumGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(999.dp),
    brush: Brush = PremiumBrushes.goldHairline(),
    content: @Composable () -> Unit,
) {
    val design = PremiumDesign.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(design.colors.glassFill, shape)
            .border(width = 0.6.dp, brush = brush, shape = shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
