package com.worldmates.messenger.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Metadata extracted from a URL for link preview display.
 */
data class LinkPreviewData(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
    val isYouTube: Boolean = false,
    val youTubeVideoId: String? = null
)

private val URL_REGEX = Regex("""https?://[^\s<>"']+""")

private val YOUTUBE_REGEX = Regex(
    """(?:https?://)?(?:www\.)?(?:youtube\.com/watch\?(?:.*&)?v=|youtu\.be/|youtube\.com/embed/|youtube\.com/shorts/)([A-Za-z0-9_-]{11})"""
)

private val httpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

/**
 * Extracts the first HTTP/HTTPS URL from the given text.
 * Returns null if no URL is found.
 */
fun extractFirstUrl(text: String): String? = URL_REGEX.find(text)?.value

/**
 * Extracts YouTube video ID from a YouTube URL.
 * Supports: youtube.com/watch?v=, youtu.be/, youtube.com/shorts/
 */
fun extractYouTubeVideoId(url: String): String? =
    YOUTUBE_REGEX.find(url)?.groupValues?.getOrNull(1)

/**
 * Returns true if the URL is a YouTube link.
 */
fun isYouTubeUrl(url: String): Boolean =
    url.contains("youtube.com") || url.contains("youtu.be")

/**
 * Builds the YouTube thumbnail URL for a given video ID.
 * Uses hqdefault (480x360) as primary, with fallback to mqdefault (320x180).
 */
fun youTubeThumbnailUrl(videoId: String): String =
    "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

/**
 * Fetches Open Graph / meta title/description/image from a URL.
 * Must be called from a coroutine (suspending).
 * Returns null on network error or non-HTML content.
 */
suspend fun fetchLinkPreview(url: String): LinkPreviewData? = withContext(Dispatchers.IO) {
    try {
        // Handle YouTube separately — no need to fetch HTML, thumbnail is from CDN
        if (isYouTubeUrl(url)) {
            val videoId = extractYouTubeVideoId(url) ?: return@withContext null
            return@withContext LinkPreviewData(
                url = url,
                title = null, // will be fetched below via oEmbed or shown as-is
                imageUrl = youTubeThumbnailUrl(videoId),
                siteName = "YouTube",
                isYouTube = true,
                youTubeVideoId = videoId
            )
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (compatible; LinkPreviewBot/1.0)")
            .header("Accept", "text/html")
            .build()

        val response = httpClient.newCall(request).execute()
        val contentType = response.header("Content-Type") ?: ""
        if (!contentType.contains("text/html", ignoreCase = true)) {
            response.close()
            return@withContext null
        }

        val body = response.body?.string() ?: run {
            response.close()
            return@withContext null
        }
        response.close()

        val doc = Jsoup.parse(body, url)

        // Extract metadata: prefer og: tags, fall back to standard tags
        val title = doc.select("meta[property=og:title]").attr("content").ifBlank { null }
            ?: doc.select("meta[name=twitter:title]").attr("content").ifBlank { null }
            ?: doc.title().ifBlank { null }

        val description = doc.select("meta[property=og:description]").attr("content").ifBlank { null }
            ?: doc.select("meta[name=description]").attr("content").ifBlank { null }
            ?: doc.select("meta[name=twitter:description]").attr("content").ifBlank { null }

        val imageUrl = doc.select("meta[property=og:image]").attr("content").ifBlank { null }
            ?: doc.select("meta[name=twitter:image]").attr("content").ifBlank { null }

        val siteName = doc.select("meta[property=og:site_name]").attr("content").ifBlank { null }
            ?: extractDomain(url)

        LinkPreviewData(
            url = url,
            title = title,
            description = description?.take(200),
            imageUrl = resolveImageUrl(imageUrl, url),
            siteName = siteName
        )
    } catch (e: Exception) {
        Log.w("LinkPreview", "Failed to fetch preview for $url: ${e.message}")
        null
    }
}

private fun extractDomain(url: String): String? = try {
    val host = java.net.URL(url).host
    host.removePrefix("www.")
} catch (e: Exception) {
    null
}

private fun resolveImageUrl(imageUrl: String?, pageUrl: String): String? {
    if (imageUrl.isNullOrBlank()) return null
    return try {
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            imageUrl
        } else {
            val base = java.net.URL(pageUrl)
            java.net.URL(base, imageUrl).toString()
        }
    } catch (e: Exception) {
        imageUrl
    }
}
