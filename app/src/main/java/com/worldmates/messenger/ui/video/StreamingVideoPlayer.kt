package com.worldmates.messenger.ui.video

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Streaming video player factory.
 *
 * Creates an ExoPlayer configured for no-download streaming:
 *   - HLS (.m3u8)  → HlsMediaSource   (adaptive bitrate)
 *   - DASH (.mpd)  → DashMediaSource  (adaptive bitrate)
 *   - MP4/WebM     → DefaultMediaSourceFactory with OkHttp data source
 *     (progressive streaming, no full download required)
 *
 * The player uses a bounded cache of [CACHE_SIZE_BYTES] so playback is
 * entirely in-memory and nothing is written to disk unless explicitly cached.
 */
object StreamingVideoPlayer {

    private const val TAG = "StreamingVideoPlayer"
    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val READ_TIMEOUT_MS    = 30_000L

    // Shared OkHttpClient for all streaming requests
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Detect the stream type from the URL.
     */
    fun detectStreamType(url: String): StreamType {
        val lower = url.lowercase().substringBefore("?")
        return when {
            lower.endsWith(".m3u8") || lower.contains("/hls/")  -> StreamType.HLS
            lower.endsWith(".mpd")  || lower.contains("/dash/") -> StreamType.DASH
            else                                                  -> StreamType.PROGRESSIVE
        }
    }

    enum class StreamType { HLS, DASH, PROGRESSIVE }

    /**
     * Build an ExoPlayer instance for streaming [videoUrl] without full download.
     *
     * @param context   Application or Activity context
     * @param videoUrl  Remote URL (http/https). m3u8 → HLS, mpd → DASH, else progressive.
     * @param authToken Optional Bearer token for authenticated media URLs.
     * @param autoPlay  Whether to start playing immediately.
     */
    @OptIn(UnstableApi::class)
    fun build(
        context: Context,
        videoUrl: String,
        authToken: String? = null,
        autoPlay: Boolean = true
    ): ExoPlayer {
        Log.d(TAG, "Building streaming player for: $videoUrl")

        val headers: Map<String, String> = if (authToken != null)
            mapOf("Authorization" to "Bearer $authToken")
        else emptyMap()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .apply {
                if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
                setUserAgent("WorldMatesMessenger/2.0 (Android)")
            }

        val streamType = detectStreamType(videoUrl)
        Log.d(TAG, "Detected stream type: $streamType")

        val mediaSource: MediaSource = when (streamType) {
            StreamType.HLS -> {
                val dataSourceFactory = okHttpDataSourceFactory
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)))
            }
            StreamType.DASH -> {
                val dataSourceFactory = okHttpDataSourceFactory
                DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)))
            }
            StreamType.PROGRESSIVE -> {
                // For MP4/WebM/etc — progressive streaming via OkHttp (no full download)
                val mediaSourceFactory = DefaultMediaSourceFactory(okHttpDataSourceFactory)
                mediaSourceFactory.createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)))
            }
        }

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(okHttpDataSourceFactory))
            .build()
            .apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = autoPlay
            }
    }
}
