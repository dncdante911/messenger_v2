package com.worldmates.messenger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

// Shared client for downloading TGS / Lottie JSON files from external CDNs.
// Using a lazy singleton avoids creating a new client on every recomposition.
private val stickerHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/**
 * Universal sticker / animated-emoji renderer.
 *
 * Supported URL schemes:
 *  - lottie://resource_name   → embedded raw resource via LottieCompositionSpec.RawRes
 *  - https://…/sticker.tgs   → Telegram animated sticker (gzip Lottie JSON)
 *  - https://…/sticker.json  → plain Lottie JSON
 *  - https://…/sticker.gif   → animated GIF via Coil
 *  - android.resource://…    → local resource via Coil
 *  - anything else            → Coil (WebP, PNG, …)
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
    Box(modifier = modifier.size(size)) {
        when {
            url.startsWith("lottie://") ->
                EmbeddedLottieView(lottieUrl = url, autoPlay = autoPlay, loop = loop,
                    modifier = Modifier.fillMaxSize())

            url.endsWith(".tgs", ignoreCase = true) ->
                TgsAnimationView(url = url, autoPlay = autoPlay, loop = loop,
                    modifier = Modifier.fillMaxSize())

            url.endsWith(".json", ignoreCase = true) ->
                LottieUrlView(url = url, autoPlay = autoPlay, loop = loop,
                    modifier = Modifier.fillMaxSize())

            else ->
                AsyncImage(
                    model = url,
                    contentDescription = "Sticker",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
        }
    }
}

// ── Embedded sticker (lottie://name) ─────────────────────────────────────────

@Composable
private fun EmbeddedLottieView(
    lottieUrl: String,
    autoPlay: Boolean,
    loop: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resourceName = lottieUrl.removePrefix("lottie://")
    val resId = remember(lottieUrl) {
        context.resources.getIdentifier(resourceName, "raw", context.packageName)
    }

    if (resId != 0) {
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
        val progress by animateLottieCompositionAsState(
            composition = composition,
            isPlaying = autoPlay,
            iterations = if (loop) LottieConstants.IterateForever else 1,
            speed = 1f
        )
        LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
    } else {
        // Raw resource not found — no-op placeholder
        Box(modifier = modifier)
    }
}

// ── Lottie JSON from HTTP URL ─────────────────────────────────────────────────

@Composable
private fun LottieUrlView(
    url: String,
    autoPlay: Boolean,
    loop: Boolean,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Url(url))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = autoPlay,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        speed = 1f
    )
    LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
}

// ── TGS (gzip-compressed Lottie JSON) from HTTP URL ──────────────────────────

@Composable
private fun TgsAnimationView(
    url: String,
    autoPlay: Boolean,
    loop: Boolean,
    modifier: Modifier = Modifier
) {
    var tgsJson by remember(url) { mutableStateOf<String?>(null) }
    var loadFailed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                // Use OkHttp with explicit timeouts.
                // Accept-Encoding: identity prevents the HTTP stack from adding
                // a second gzip layer on top of the file-level gzip in the TGS.
                val request = Request.Builder()
                    .url(url)
                    .header("Accept-Encoding", "identity")
                    .build()
                val bytes = stickerHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.bytes() ?: throw Exception("Empty body")
                }
                tgsJson = decompressTgs(bytes)
            } catch (e: Exception) {
                android.util.Log.e("AnimatedStickerView", "TGS load failed [$url]: ${e.message}")
                loadFailed = true
            }
        }
    }

    when {
        tgsJson != null -> {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.JsonString(tgsJson!!)
            )
            val progress by animateLottieCompositionAsState(
                composition = compositionResult.value,
                isPlaying = autoPlay,
                iterations = if (loop) LottieConstants.IterateForever else 1,
                speed = 1f
            )
            LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
        }
        // Loading or error — keep the reserved space but render nothing
        else -> Box(modifier = modifier)
    }
}

private fun decompressTgs(bytes: ByteArray): String =
    GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }

// ── Convenience variants ──────────────────────────────────────────────────────

@Composable
fun AnimatedSticker(url: String, modifier: Modifier = Modifier) {
    AnimatedStickerView(url = url, modifier = modifier, size = 120.dp, autoPlay = true, loop = true)
}

@Composable
fun CompactAnimatedSticker(url: String, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    AnimatedStickerView(url = url, modifier = modifier, size = size, autoPlay = true, loop = true)
}
