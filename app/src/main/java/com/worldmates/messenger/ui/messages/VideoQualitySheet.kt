package com.worldmates.messenger.ui.messages

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worldmates.messenger.R
import com.worldmates.messenger.utils.VideoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ─── Data model ───────────────────────────────────────────────────────────────

/** Maps to VideoCompressor.Quality + "send as file" option. */
enum class VideoSendOption(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val quality: VideoCompressor.Quality?,  // null → send as file
    val sendAsFile: Boolean,
) {
    VIDEO_MESSAGE(
        icon       = Icons.Default.Videocam,
        titleRes   = R.string.video_quality_video_message,
        descRes    = R.string.video_quality_video_message_desc,
        quality    = VideoCompressor.Quality.VIDEO_MESSAGE,
        sendAsFile = false,
    ),
    COMPRESSED(
        icon       = Icons.Default.VideoFile,
        titleRes   = R.string.video_quality_compressed,
        descRes    = R.string.video_quality_compressed_desc,
        quality    = VideoCompressor.Quality.COMPRESSED,
        sendAsFile = false,
    ),
    HIGH_QUALITY(
        icon       = Icons.Default.HighQuality,
        titleRes   = R.string.video_quality_high,
        descRes    = R.string.video_quality_high_desc,
        quality    = VideoCompressor.Quality.HIGH_QUALITY,
        sendAsFile = false,
    ),
    ORIGINAL(
        icon       = Icons.Default.FolderOpen,
        titleRes   = R.string.video_quality_original_file,
        descRes    = R.string.video_quality_original_file_desc,
        quality    = null,
        sendAsFile = true,
    ),
}

// ─── Main Sheet ───────────────────────────────────────────────────────────────

/**
 * Telegram-like video quality bottom sheet.
 *
 * Shows a thumbnail, file info, and four quality options.
 * The user picks a quality and taps "Send".
 *
 * @param videoFile     The video file that will be sent.
 * @param onSend        Called with chosen quality and sendAsFile flag.
 * @param onDismiss     Called when the sheet is dismissed without sending.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQualitySheet(
    videoFile: File,
    onSend: (quality: VideoCompressor.Quality?, sendAsFile: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Default: "Compressed (720p)" — like Telegram's default
    var selected by remember { mutableStateOf(VideoSendOption.COMPRESSED) }

    // Load thumbnail + duration in background
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var durationMs by remember { mutableStateOf(0L) }

    LaunchedEffect(videoFile.absolutePath) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoFile.absolutePath)
                thumbnail = retriever.getFrameAtTime(
                    500_000L,  // 0.5 sec offset for a representative frame
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
            } finally {
                retriever.release()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {

            // ── Title ─────────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.video_send_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // ── File info row ─────────────────────────────────────────────────
            VideoFileInfoRow(
                videoFile  = videoFile,
                thumbnail  = thumbnail,
                durationMs = durationMs,
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // ── Section label ─────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.video_send_as),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            // ── Quality options ───────────────────────────────────────────────
            VideoSendOption.entries.forEach { option ->
                VideoQualityOptionRow(
                    option     = option,
                    isSelected = selected == option,
                    onClick    = { selected = option },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))

            // ── Action buttons ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }

                Button(
                    onClick  = { onSend(selected.quality, selected.sendAsFile) },
                    modifier = Modifier.weight(2f),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector     = Icons.Default.Send,
                        contentDescription = null,
                        modifier        = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text       = stringResource(R.string.video_send_button),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ─── File info row ─────────────────────────────────────────────────────────────

@Composable
private fun VideoFileInfoRow(
    videoFile: File,
    thumbnail: Bitmap?,
    durationMs: Long,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Thumbnail box
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap       = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier     = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            // Play icon overlay (always visible)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector     = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint            = Color.White,
                    modifier        = Modifier.size(20.dp),
                )
            }
        }

        // File name + meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = videoFile.name,
                style    = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val sizeTxt = formatVideoFileSize(videoFile.length())
            val durTxt  = if (durationMs > 0) formatVideoDuration(durationMs) else null
            Text(
                text  = if (durTxt != null) "$sizeTxt · $durTxt" else sizeTxt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Quality option row ────────────────────────────────────────────────────────

@Composable
private fun VideoQualityOptionRow(
    option: VideoSendOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(200),
        label = "optionBg",
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else
            Color.Transparent,
        animationSpec = tween(200),
        label = "optionBorder",
    )

    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(14.dp),
        color   = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Selection indicator
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp),
            )

            // Quality icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Labels
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = stringResource(option.titleRes),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (isSelected)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = stringResource(option.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

// ─── Compression progress overlay ─────────────────────────────────────────────

/**
 * Full-screen semi-transparent overlay shown while VideoCompressor is running.
 * Dismissible only by cancelling — the user cannot interact with the chat underneath.
 */
@Composable
fun VideoCompressionOverlay(progress: Int) {
    Dialog(
        onDismissRequest = {},  // non-dismissible
        properties = DialogProperties(
            dismissOnBackPress    = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape  = RoundedCornerShape(20.dp),
                color  = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .width(240.dp)
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Circular progress
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress    = { progress / 100f },
                            modifier    = Modifier.size(72.dp),
                            strokeWidth = 5.dp,
                            color       = MaterialTheme.colorScheme.primary,
                            trackColor  = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text(
                            text  = "$progress%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Text(
                        text  = stringResource(R.string.video_compressing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text  = stringResource(R.string.video_compression_progress, progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─── Size / duration helpers ──────────────────────────────────────────────────

internal fun formatVideoFileSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f ГБ".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L         -> "%.1f МБ".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L                 -> "%.0f КБ".format(bytes / 1024.0)
        else                           -> "$bytes Б"
    }
}

internal fun formatVideoDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min      = totalSec / 60
    val sec      = totalSec % 60
    return if (min >= 60) {
        val h = min / 60
        val m = min % 60
        "%d:%02d:%02d".format(h, m, sec)
    } else {
        "%d:%02d".format(min, sec)
    }
}
