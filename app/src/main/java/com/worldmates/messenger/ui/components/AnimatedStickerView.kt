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
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Універсальний компонент для відображення анімованих стікерів
 *
 * Підтримує формати:
 * - Lottie JSON (.json) - стандартний формат Lottie анімацій
 * - TGS (Telegram Stickers) - gzip стиснутий Lottie JSON
 * - GIF - анімовані GIF
 * - Static images (PNG, WebP) - статичні зображення
 *
 * Використання:
 * ```
 * AnimatedStickerView(
 *     url = "https://cdn.worldmates.club/sticker.json",
 *     modifier = Modifier.size(120.dp)
 * )
 * ```
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

    Box(modifier = modifier.size(size)) {
        when {
            // Lottie JSON анімація
            fileExtension == "json" -> {
                LottieAnimationView(
                    url = url,
                    autoPlay = autoPlay,
                    loop = loop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // TGS (Telegram Sticker) - gzip стиснутий Lottie JSON
            fileExtension == "tgs" -> {
                TgsAnimationView(
                    url = url,
                    autoPlay = autoPlay,
                    loop = loop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Всі інші формати (GIF, PNG, WebP) - через Coil
            else -> {
                AsyncImage(
                    model = url,
                    contentDescription = "Sticker",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }
        }
    }
}

/**
 * Відображає Lottie JSON анімацію з URL
 */
@Composable
private fun LottieAnimationView(
    url: String,
    autoPlay: Boolean,
    loop: Boolean,
    modifier: Modifier = Modifier
) {
    // Завантажуємо Lottie JSON з URL
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Url(url)
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = autoPlay,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        speed = 1f
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )
}

/**
 * Відображає TGS (Telegram Sticker) анімацію
 * TGS = gzip стиснутий Lottie JSON
 */
@Composable
private fun TgsAnimationView(
    url: String,
    autoPlay: Boolean,
    loop: Boolean,
    modifier: Modifier = Modifier
) {
    // Для TGS файлів потрібно спочатку завантажити, розпакувати gzip, і передати JSON в Lottie
    // Це складніша операція, тому використовуємо LaunchedEffect

    var tgsJsonString by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        try {
            // Завантажуємо TGS файл як ByteArray
            val tgsBytes = loadTgsFromUrl(url)

            // Розпаковуємо gzip
            val jsonString = decompressTgs(tgsBytes)

            tgsJsonString = jsonString
            isLoading = false
        } catch (e: Exception) {
            android.util.Log.e("AnimatedStickerView", "Failed to load TGS: $url", e)
            hasError = true
            isLoading = false
        }
    }

    when {
        isLoading -> {
            // Показуємо placeholder під час завантаження
            Box(modifier = modifier)
        }
        hasError || tgsJsonString == null -> {
            // При помилці показуємо fallback через AsyncImage
            AsyncImage(
                model = url,
                contentDescription = "Sticker",
                modifier = modifier,
                contentScale = ContentScale.Fit
            )
        }
        else -> {
            // Відображаємо розпакований Lottie JSON
            val composition by rememberLottieComposition(
                LottieCompositionSpec.JsonString(tgsJsonString!!)
            )

            val progress by animateLottieCompositionAsState(
                composition = composition,
                isPlaying = autoPlay,
                iterations = if (loop) LottieConstants.IterateForever else 1,
                speed = 1f
            )

            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = modifier
            )
        }
    }
}

/**
 * Завантажує TGS файл з URL як ByteArray
 * TODO: Це синхронна операція, краще перенести в background thread
 */
private suspend fun loadTgsFromUrl(url: String): ByteArray {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        java.net.URL(url).openStream().use { it.readBytes() }
    }
}

/**
 * Розпаковує gzip стиснутий TGS файл в Lottie JSON string
 */
private fun decompressTgs(tgsBytes: ByteArray): String {
    val inputStream = ByteArrayInputStream(tgsBytes)
    val gzipInputStream = GZIPInputStream(inputStream)
    return gzipInputStream.bufferedReader().use { it.readText() }
}

/**
 * Простий варіант AnimatedStickerView тільки з URL
 */
@Composable
fun AnimatedSticker(
    url: String,
    modifier: Modifier = Modifier
) {
    AnimatedStickerView(
        url = url,
        modifier = modifier,
        size = 120.dp,
        autoPlay = true,
        loop = true
    )
}

/**
 * Компактний варіант для списків
 */
@Composable
fun CompactAnimatedSticker(
    url: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    AnimatedStickerView(
        url = url,
        modifier = modifier,
        size = size,
        autoPlay = true,
        loop = true
    )
}
