package com.worldmates.messenger.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R

/**
 * Horizontal picker row for animated chat backgrounds.
 * Shown in Settings > Themes between the static presets and bubble style sections.
 */
@Composable
fun AnimatedBackgroundPickerRow(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedVariant by remember {
        mutableStateOf(AnimatedBgPrefs.getVariant(context))
    }

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(AnimatedBgVariant.entries) { variant ->
            AnimatedBgCard(
                variant = variant,
                isSelected = selectedVariant == variant,
                onClick = {
                    selectedVariant = variant
                    AnimatedBgPrefs.setVariant(context, variant)
                }
            )
        }
    }
}

@Composable
private fun AnimatedBgCard(
    variant: AnimatedBgVariant,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(70.dp, 110.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (variant == AnimatedBgVariant.NONE) {
                Text(
                    text = "✕",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Live mini-preview of the animation
                ChatAnimatedBackground(
                    variant = variant,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(variantLabelRes(variant)),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

private fun variantLabelRes(variant: AnimatedBgVariant): Int = when (variant) {
    AnimatedBgVariant.NONE         -> R.string.animated_bg_none
    AnimatedBgVariant.AURORA       -> R.string.animated_bg_aurora
    AnimatedBgVariant.OCEAN_WAVES  -> R.string.animated_bg_ocean
    AnimatedBgVariant.COSMIC       -> R.string.animated_bg_cosmic
    AnimatedBgVariant.SUNSET_FLOW  -> R.string.animated_bg_sunset
    AnimatedBgVariant.NEON_PULSE   -> R.string.animated_bg_neon
    AnimatedBgVariant.FOREST_MIST  -> R.string.animated_bg_forest
    AnimatedBgVariant.FIRE_EMBERS  -> R.string.animated_bg_fire
}
