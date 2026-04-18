package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.PremiumTokens
import com.worldmates.messenger.ui.channels.premium.design.current
import android.os.Build

/**
 * Frame presets available to a premium channel owner. Selected via the
 * Appearance settings screen (Phase 5).
 */
enum class AvatarFrameStyle {
    None,
    GoldRing,
    GoldDoubleRing,
    RoseGoldHalo,
    AuroraGradient,
    EngravedNotch,
    CrystalEdge,
}

/**
 * Premium channel avatar. Always circular, optionally wrapped in one of
 * seven frame styles. Supports an animated GIF/WebP avatar when the
 * channel owner has uploaded one — the static image is still used as
 * the placeholder while the animated version decodes.
 *
 * Initials are rendered in Exo 2 Bold on a rose-gold wash when the
 * image fails to load or is missing.
 */
@Composable
fun PremiumChannelAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    imageUrl: String? = null,
    animatedUrl: String? = null,
    initials: String = "",
    frame: AvatarFrameStyle = AvatarFrameStyle.GoldRing,
) {
    val design = PremiumDesign.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val loader = remember(animatedUrl) {
        if (animatedUrl.isNullOrBlank()) null else ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
    val effectiveUrl = animatedUrl?.takeIf { it.isNotBlank() } ?: imageUrl
    val framePadding = frameInsets(frame, size)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        PremiumAvatarFrame(frame = frame, size = size)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(framePadding)
                .clip(CircleShape)
                .background(PremiumBrushes.roseGoldLinear(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (!effectiveUrl.isNullOrBlank()) {
                val request = ImageRequest.Builder(context)
                    .data(effectiveUrl)
                    .crossfade(true)
                    .build()
                if (loader != null) {
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        imageLoader = loader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
            } else {
                Text(
                    text = initials.take(2).uppercase(),
                    color = design.colors.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.32f).sp,
                )
            }
        }
    }
}

private fun frameInsets(frame: AvatarFrameStyle, size: Dp): Dp = when (frame) {
    AvatarFrameStyle.None -> 0.dp
    AvatarFrameStyle.GoldRing -> (size.value * 0.04f).dp
    AvatarFrameStyle.GoldDoubleRing -> (size.value * 0.08f).dp
    AvatarFrameStyle.RoseGoldHalo -> (size.value * 0.05f).dp
    AvatarFrameStyle.AuroraGradient -> (size.value * 0.05f).dp
    AvatarFrameStyle.EngravedNotch -> (size.value * 0.05f).dp
    AvatarFrameStyle.CrystalEdge -> (size.value * 0.05f).dp
}

@Composable
private fun PremiumAvatarFrame(
    frame: AvatarFrameStyle,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (frame == AvatarFrameStyle.None) return
    val colors = PremiumDesign.current.colors

    Canvas(modifier.fillMaxSize().size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = kotlin.math.min(w, h) / 2f

        when (frame) {
            AvatarFrameStyle.GoldRing -> {
                drawCircle(
                    brush = PremiumBrushes.matteGoldLinear(),
                    radius = r,
                    center = center,
                    style = Stroke(width = w * 0.055f),
                )
            }
            AvatarFrameStyle.GoldDoubleRing -> {
                drawCircle(
                    brush = PremiumBrushes.matteGoldLinear(),
                    radius = r,
                    center = center,
                    style = Stroke(width = w * 0.05f),
                )
                drawCircle(
                    color = colors.accentSoft.copy(alpha = 0.65f),
                    radius = r - w * 0.08f,
                    center = center,
                    style = Stroke(width = w * 0.018f),
                )
            }
            AvatarFrameStyle.RoseGoldHalo -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.secondarySoft.copy(alpha = 0.9f),
                            colors.secondary.copy(alpha = 0.0f),
                        ),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                    center = center,
                )
                drawCircle(
                    brush = PremiumBrushes.roseGoldLinear(),
                    radius = r,
                    center = center,
                    style = Stroke(width = w * 0.045f),
                )
            }
            AvatarFrameStyle.AuroraGradient -> {
                drawCircle(
                    brush = PremiumBrushes.auroraHighlight(),
                    radius = r,
                    center = center,
                    style = Stroke(width = w * 0.06f),
                )
            }
            AvatarFrameStyle.EngravedNotch -> {
                drawCircle(
                    brush = PremiumBrushes.matteGoldLinear(),
                    radius = r,
                    center = center,
                    style = Stroke(width = w * 0.055f),
                )
                // Small obsidian notch on top — "engraved" feel.
                drawCircle(
                    color = PremiumTokens.ObsidianBlack,
                    radius = w * 0.06f,
                    center = Offset(center.x, center.y - r + w * 0.005f),
                )
            }
            AvatarFrameStyle.CrystalEdge -> {
                // Prismatic multi-stroke ring.
                listOf(
                    Color(0xFFE8C877) to w * 0.02f,
                    Color(0xFFD4AF37) to w * 0.018f,
                    Color(0x66FFFFFF) to w * 0.01f,
                ).forEachIndexed { i, (c, strokeW) ->
                    drawCircle(
                        color = c,
                        radius = r - i * w * 0.02f,
                        center = center,
                        style = Stroke(width = strokeW),
                    )
                }
            }
            AvatarFrameStyle.None -> Unit
        }
    }
}
