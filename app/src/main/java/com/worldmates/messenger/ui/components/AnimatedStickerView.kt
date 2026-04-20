package com.worldmates.messenger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.*

/**
 * Universal sticker view: .tgs / .json → Lottie; .webp / .gif / .png → AsyncImage.
 *
 * For TGS (Telegram animated stickers), uses LottieCompositionSpec.Url so Lottie's
 * own fromInputStream detects the gzip magic bytes and decompresses natively —
 * no manual download/decompress needed.
 */
@Composable
fun AnimatedStickerView(
    url: String,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    contentScale: ContentScale = ContentScale.Fit,
    autoPlay: Boolean = true,
    loop: Boolean = true
) {
    val fileExtension = remember(url) {
        url.substringAfterLast('.', "").lowercase()
    }
    val isLottie = fileExtension == "tgs" || fileExtension == "json"

    Box(modifier = modifier.size(size)) {
        if (isLottie) {
            LottieUrlView(
                url = url,
                autoPlay = autoPlay,
                loop = loop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = "Sticker",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }
    }
}

/**
 * Loads a Lottie animation from a URL. Handles both plain JSON and gzip-compressed
 * TGS (Telegram sticker) format — Lottie detects gzip magic bytes internally.
 * Falls back to AsyncImage if composition fails to load.
 */
@Composable
private fun LottieUrlView(
    url: String,
    autoPlay: Boolean,
    loop: Boolean,
    modifier: Modifier = Modifier
) {
    val compositionResult = rememberLottieComposition(LottieCompositionSpec.Url(url))

    when {
        compositionResult.isLoading -> {
            // Empty placeholder while loading
            Box(modifier = modifier)
        }
        compositionResult.isFailure -> {
            // Try to show a static thumbnail via Coil as last resort
            AsyncImage(
                model = url,
                contentDescription = "Sticker",
                modifier = modifier,
                contentScale = ContentScale.Fit
            )
        }
        else -> {
            val progress by animateLottieCompositionAsState(
                composition = compositionResult.value,
                isPlaying = autoPlay,
                iterations = if (loop) LottieConstants.IterateForever else 1,
                speed = 1f
            )
            LottieAnimation(
                composition = compositionResult.value,
                progress = { progress },
                modifier = modifier
            )
        }
    }
}

/** Convenience alias with default size. */
@Composable
fun AnimatedSticker(url: String, modifier: Modifier = Modifier) {
    AnimatedStickerView(url = url, modifier = modifier, size = 120.dp)
}

/** Compact variant for lists and thumbnails. */
@Composable
fun CompactAnimatedSticker(url: String, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    AnimatedStickerView(url = url, modifier = modifier, size = size)
}
