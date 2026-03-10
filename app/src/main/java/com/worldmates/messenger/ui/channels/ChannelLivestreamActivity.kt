package com.worldmates.messenger.ui.channels

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

/**
 * Channel Livestream Activity
 *
 * Launched with:
 *   EXTRA_CHANNEL_ID  — channel page_id (Long)
 *   EXTRA_IS_HOST     — true if current user is the broadcaster (Boolean, default false)
 *   EXTRA_IS_PREMIUM  — true if channel has premium (Boolean, default false)
 *
 * For hosts   : shows start/end controls + quality picker
 * For viewers : joins existing stream and shows viewer count
 */
class ChannelLivestreamActivity : AppCompatActivity() {

    private lateinit var viewModel: ChannelLivestreamViewModel
    private var channelId: Long = 0
    private var isHost: Boolean = false
    private var channelIsPremium: Boolean = false
    private var channelName: String = ""

    companion object {
        const val EXTRA_CHANNEL_ID  = "channel_id"
        const val EXTRA_IS_HOST     = "is_host"
        const val EXTRA_IS_PREMIUM  = "is_premium"
        const val EXTRA_CHANNEL_NAME = "channel_name"

        fun createIntent(
            context: Context,
            channelId: Long,
            isHost: Boolean,
            isPremium: Boolean,
            channelName: String = ""
        ) = Intent(context, ChannelLivestreamActivity::class.java).apply {
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_IS_HOST, isHost)
            putExtra(EXTRA_IS_PREMIUM, isPremium)
            putExtra(EXTRA_CHANNEL_NAME, channelName)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.initialize(this)

        channelId       = intent.getLongExtra(EXTRA_CHANNEL_ID, 0)
        isHost          = intent.getBooleanExtra(EXTRA_IS_HOST, false)
        channelIsPremium = intent.getBooleanExtra(EXTRA_IS_PREMIUM, false)
        channelName     = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""

        if (channelId == 0L) { finish(); return }

        // Enable edge-to-edge layout so we can draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        viewModel = ViewModelProvider(this)[ChannelLivestreamViewModel::class.java]

        // Lock portrait for setup; unlock rotation once streaming/viewing starts
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                requestedOrientation = when (state) {
                    is LivestreamUiState.Hosting,
                    is LivestreamUiState.Viewing -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        }

        // Viewer: check if a stream is already active
        if (!isHost) {
            viewModel.joinStream(channelId)
        }

        setContent {
            WorldMatesThemedApp {
                LivestreamScreen(
                    viewModel        = viewModel,
                    channelId        = channelId,
                    channelName      = channelName,
                    isHost           = isHost,
                    channelIsPremium = channelIsPremium,
                    onClose          = { finish() }
                )
            }
        }
    }
}

// ─── Main composable ──────────────────────────────────────────────────────────

@Composable
private fun LivestreamScreen(
    viewModel: ChannelLivestreamViewModel,
    channelId: Long,
    channelName: String,
    isHost: Boolean,
    channelIsPremium: Boolean,
    onClose: () -> Unit
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val localStream by viewModel.localStream.collectAsStateWithLifecycle()
    val remoteStream by viewModel.remoteStream.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0A0A1A), Color(0xFF1A1A2E)))
            )
            .systemBarsPadding()
    ) {
        when (val state = uiState) {
            is LivestreamUiState.Idle -> {
                if (isHost) {
                    HostSetupScreen(
                        channelIsPremium = channelIsPremium,
                        onStart          = { quality, title ->
                            viewModel.startStream(channelId, quality, title)
                        },
                        onClose          = onClose
                    )
                } else {
                    // Waiting for stream
                    WaitingScreen(channelName = channelName, onClose = onClose)
                }
            }

            is LivestreamUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFE91E8C))
                }
            }

            is LivestreamUiState.Hosting -> {
                HostingScreen(
                    info        = state.info,
                    localStream = localStream,
                    onEnd       = { viewModel.endStream(); onClose() },
                    onClose     = onClose,
                    onToggleCamera  = { viewModel.switchCamera() },
                    onToggleAudio   = { enabled -> viewModel.toggleAudio(enabled) },
                    onToggleVideo   = { enabled -> viewModel.toggleVideo(enabled) }
                )
            }

            is LivestreamUiState.Viewing -> {
                ViewingScreen(
                    info         = state.info,
                    remoteStream = remoteStream,
                    onLeave      = { viewModel.leaveStream(); onClose() }
                )
            }

            is LivestreamUiState.Ended -> {
                LaunchedEffect(Unit) { onClose() }
            }

            is LivestreamUiState.Error -> {
                ErrorStreamScreen(message = state.message, onClose = onClose)
            }
        }
    }
}

// ─── Host Setup ───────────────────────────────────────────────────────────────

@Composable
private fun HostSetupScreen(
    channelIsPremium: Boolean,
    onStart: (quality: String, title: String?) -> Unit,
    onClose: () -> Unit
) {
    val qualities = if (channelIsPremium)
        ChannelLivestreamViewModel.PREMIUM_QUALITIES
    else
        ChannelLivestreamViewModel.REGULAR_QUALITIES

    var selectedQuality by remember { mutableStateOf(qualities.last()) }
    var title by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.livestream_close), tint = Color.White)
            }
            Text(
                text = stringResource(R.string.livestream_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }

        // Premium badge
        if (channelIsPremium) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFD700).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.livestream_premium_hint), color = Color(0xFFFFD700), fontSize = 13.sp)
            }
        }

        // Title input
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.livestream_title_hint), color = Color.Gray) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFE91E8C),
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Quality selector
        Text(stringResource(R.string.livestream_quality_label), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            qualities.forEach { quality ->
                QualityOption(
                    quality    = quality,
                    selected   = quality == selectedQuality,
                    isPremium  = quality in listOf("1080p", "1080p60"),
                    onClick    = { selectedQuality = quality }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Start button
        Button(
            onClick = { onStart(selectedQuality, title.takeIf { it.isNotBlank() }) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E8C)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.livestream_go_live_btn), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QualityOption(quality: String, selected: Boolean, isPremium: Boolean, onClick: () -> Unit) {
    val qualityLabels = mapOf(
        "240p"    to stringResource(R.string.quality_240p),
        "360p"    to stringResource(R.string.quality_360p),
        "480p"    to stringResource(R.string.quality_480p),
        "720p"    to stringResource(R.string.quality_720p),
        "1080p"   to stringResource(R.string.quality_1080p),
        "1080p60" to stringResource(R.string.quality_1080p60)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) Color(0xFFE91E8C).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected  = selected,
            onClick   = null,
            colors    = RadioButtonDefaults.colors(selectedColor = Color(0xFFE91E8C))
        )
        Text(
            text      = qualityLabels[quality] ?: quality,
            color     = if (selected) Color.White else Color.LightGray,
            fontSize  = 14.sp,
            modifier  = Modifier.weight(1f)
        )
        if (isPremium) {
            Text(
                stringResource(R.string.livestream_quality_pro_badge),
                color      = Color(0xFFFFD700),
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier
                    .background(Color(0xFFFFD700).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// ─── Hosting Screen ───────────────────────────────────────────────────────────

@Composable
private fun HostingScreen(
    info: LivestreamInfo,
    localStream: MediaStream?,
    onEnd: () -> Unit,
    onClose: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleAudio: (Boolean) -> Unit,
    onToggleVideo: (Boolean) -> Unit
) {
    var showEndConfirm by remember { mutableStateOf(false) }
    var audioEnabled   by remember { mutableStateOf(true) }
    var videoEnabled   by remember { mutableStateOf(true) }

    // Full-screen layout: camera fills the screen, controls overlay on top/bottom
    Box(modifier = Modifier.fillMaxSize()) {

        // Local camera preview — fills entire screen
        val eglContext = remember { com.worldmates.messenger.network.WebRTCManager.getEglContext() }
        AndroidView(
            factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    try {
                        init(eglContext, null)
                        setMirror(true)
                        setEnableHardwareScaler(true)
                        setZOrderMediaOverlay(false)
                        localStream?.videoTracks?.firstOrNull()?.addSink(this)
                    } catch (e: Exception) {
                        android.util.Log.e("Livestream", "SurfaceViewRenderer init error", e)
                    }
                }
            },
            update = { renderer ->
                // Re-attach when localStream changes
                try {
                    localStream?.videoTracks?.firstOrNull()?.addSink(renderer)
                } catch (e: Exception) { /* ignore */ }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Dark gradient at top for readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )

        // Top bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiveBadge()
            Spacer(Modifier.width(8.dp))
            Text(
                text     = info.quality.uppercase(),
                color    = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showEndConfirm = true }) {
                Icon(Icons.Default.Close, stringResource(R.string.livestream_end_stream_desc), tint = Color.White)
            }
        }

        // Dark gradient at bottom for readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        // Bottom controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, bottom = 24.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mic toggle
            IconButton(
                onClick = {
                    audioEnabled = !audioEnabled
                    onToggleAudio(audioEnabled)
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (audioEnabled) Color.White.copy(alpha = 0.15f) else Color(0xFFFF4444).copy(alpha = 0.8f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (audioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Camera flip
            IconButton(
                onClick = onToggleCamera,
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Default.Cameraswitch, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }

            // Video toggle
            IconButton(
                onClick = {
                    videoEnabled = !videoEnabled
                    onToggleVideo(videoEnabled)
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (videoEnabled) Color.White.copy(alpha = 0.15f) else Color(0xFFFF4444).copy(alpha = 0.8f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (videoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            // End stream button
            OutlinedButton(
                onClick  = { showEndConfirm = true },
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444)),
                border   = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF4444)),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.livestream_end_stream), fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text(stringResource(R.string.livestream_end_stream_title)) },
            text  = { Text(stringResource(R.string.livestream_end_stream_message)) },
            confirmButton = {
                TextButton(onClick = { showEndConfirm = false; onEnd() }) {
                    Text(stringResource(R.string.livestream_end_confirm), color = Color(0xFFFF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) { Text(stringResource(R.string.livestream_cancel)) }
            }
        )
    }
}

// ─── Viewer Screen ────────────────────────────────────────────────────────────

@Composable
private fun ViewingScreen(
    info: LivestreamInfo,
    remoteStream: MediaStream?,
    onLeave: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (remoteStream != null) {
            // Remote stream renders full-screen
            val eglContext = remember { com.worldmates.messenger.network.WebRTCManager.getEglContext() }
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        try {
                            init(eglContext, null)
                            setEnableHardwareScaler(true)
                            setZOrderMediaOverlay(false)
                            remoteStream.videoTracks?.firstOrNull()?.addSink(this)
                        } catch (e: Exception) {
                            android.util.Log.e("Livestream", "ViewingScreen renderer init error", e)
                        }
                    }
                },
                update = { renderer ->
                    try {
                        remoteStream.videoTracks?.firstOrNull()?.addSink(renderer)
                    } catch (e: Exception) { /* ignore */ }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Waiting for stream to connect
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFE91E8C))
                Text(
                    text     = stringResource(R.string.livestream_watching_quality, info.quality),
                    color    = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
                )
            }
        }

        // Dark top gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )

        // Top bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiveBadge()
            Spacer(Modifier.width(8.dp))
            Text(
                text  = stringResource(R.string.livestream_viewer_count, info.viewer_count),
                color = Color.White,
                fontSize = 13.sp
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onLeave,
                modifier = Modifier
                    .background(Color(0xFFE91E8C).copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            ) {
                Text(stringResource(R.string.livestream_leave), color = Color(0xFFE91E8C))
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun LiveBadge() {
    Text(
        text     = stringResource(R.string.livestream_badge_live),
        color    = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(Color(0xFFE91E8C), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun WaitingScreen(channelName: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.LiveTv, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.livestream_no_active), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.livestream_not_live_now, channelName), color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onClose) { Text(stringResource(R.string.livestream_go_back)) }
    }
}

@Composable
private fun ErrorStreamScreen(message: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF5252), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onClose) { Text(stringResource(R.string.livestream_close)) }
    }
}
