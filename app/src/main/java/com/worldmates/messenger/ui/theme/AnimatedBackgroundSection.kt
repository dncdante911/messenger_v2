package com.worldmates.messenger.ui.theme

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.ui.premium.PremiumActivity

/**
 * Horizontal picker row for animated chat backgrounds.
 * Animated variants are available to Pro users and new users (first 5 days).
 * Shown in Settings > Themes.
 */
@Composable
fun AnimatedBackgroundPickerRow(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canUse = remember { UserSession.canUseAnimatedBackground }
    var selectedVariant by remember {
        mutableStateOf(AnimatedBgPrefs.getVariant(context))
    }

    // If user lost access, reset to NONE automatically
    LaunchedEffect(canUse) {
        if (!canUse && selectedVariant != AnimatedBgVariant.NONE) {
            selectedVariant = AnimatedBgVariant.NONE
            AnimatedBgPrefs.setVariant(context, AnimatedBgVariant.NONE)
        }
    }

    Column(modifier = modifier) {
        // Trial/Pro hint banner
        if (!canUse) {
            PremiumHintBanner(
                onClick = {
                    context.startActivity(Intent(context, PremiumActivity::class.java))
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        } else if (UserSession.isNewUser) {
            TrialRemainingBanner()
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(AnimatedBgVariant.entries) { variant ->
                val isLocked = variant != AnimatedBgVariant.NONE && !canUse
                AnimatedBgCard(
                    variant = variant,
                    isSelected = selectedVariant == variant,
                    isLocked = isLocked,
                    onClick = {
                        if (isLocked) {
                            context.startActivity(Intent(context, PremiumActivity::class.java))
                        } else {
                            selectedVariant = variant
                            AnimatedBgPrefs.setVariant(context, variant)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PremiumHintBanner(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.animated_bg_premium_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.animated_bg_premium_desc),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
        Text(
            text = stringResource(R.string.animated_bg_get_pro),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TrialRemainingBanner() {
    val registeredAt = UserSession.registeredAt
    val daysLeft = if (registeredAt > 0L) {
        val elapsed = System.currentTimeMillis() - registeredAt
        val remaining = 5L * 24 * 60 * 60 * 1000 - elapsed
        ((remaining / (24 * 60 * 60 * 1000)) + 1).toInt().coerceAtLeast(1)
    } else 5

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "✨", fontSize = 18.sp)
        Text(
            text = stringResource(R.string.animated_bg_trial_banner, daysLeft),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AnimatedBgCard(
    variant: AnimatedBgVariant,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else       -> MaterialTheme.colorScheme.outlineVariant
    }

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

            // Lock overlay for non-eligible users
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "PRO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(variantLabelRes(variant)),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isLocked   -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else       -> MaterialTheme.colorScheme.onSurfaceVariant
            },
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
