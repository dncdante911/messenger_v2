package com.worldmates.messenger.ui.components.media

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R

/**
 * Елемент аудіофайлу у списку
 *
 * @param url      URL для відтворення
 * @param duration тривалість у секундах (0 якщо невідомо)
 * @param filename оригінальне ім'я файлу
 */
data class AudioItem(
    val url:      String,
    val duration: Long   = 0L,
    val filename: String = ""
)

/**
 * Компонент аудіо-альбому — вертикальний список аудіодоріжок у єдиній бульбашці.
 *
 * Кожен рядок містить:
 *  - кнопку Play/Pause
 *  - анімований еквалайзер (прості стовпчики)
 *  - тривалість
 *  - ім'я файлу
 *
 * @param audioFiles список аудіодоріжок
 * @param modifier   зовнішній модифікатор
 */
@Composable
fun AudioAlbumComponent(
    audioFiles: List<AudioItem>,
    modifier: Modifier = Modifier
) {
    if (audioFiles.isEmpty()) return

    // Tracks which audio index is currently playing (-1 = none)
    var playingIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier           = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Header
        Text(
            text       = stringResource(R.string.audio_album_title),
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.primary,
            modifier   = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        audioFiles.forEachIndexed { index, audio ->
            val isPlaying = playingIndex == index

            AudioTrackRow(
                audio     = audio,
                isPlaying = isPlaying,
                onPlayPauseClick = {
                    playingIndex = if (isPlaying) -1 else index
                },
                modifier  = Modifier.fillMaxWidth()
            )

            if (index < audioFiles.lastIndex) {
                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 8.dp),
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ─── Single audio track row ───────────────────────────────────────────────────

@Composable
private fun AudioTrackRow(
    audio: AudioItem,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier           = modifier.padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment  = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Play / Pause button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onPlayPauseClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                tint               = Color.White,
                modifier           = Modifier.size(24.dp)
            )
        }

        // Waveform bars + filename + duration
        Column(modifier = Modifier.weight(1f)) {
            // Animated waveform bars
            WaveformBars(
                isPlaying = isPlaying,
                modifier  = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Filename
                Text(
                    text     = audio.filename.ifEmpty { "audio" },
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color    = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Duration
                if (audio.duration > 0L) {
                    Text(
                        text     = formatAudioDuration(audio.duration),
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

// ─── Simple animated waveform ─────────────────────────────────────────────────

private val BAR_HEIGHTS = listOf(0.4f, 0.7f, 0.55f, 0.9f, 0.6f, 0.8f, 0.45f, 0.75f, 0.5f, 0.65f,
    0.85f, 0.55f, 0.7f, 0.4f, 0.6f, 0.8f, 0.5f, 0.9f, 0.65f, 0.45f)

@Composable
private fun WaveformBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    // Each bar gets a phase-shifted animation when playing
    val animatedHeights = BAR_HEIGHTS.mapIndexed { i, base ->
        val phase = i * 0.15f
        val animated by infiniteTransition.animateFloat(
            initialValue = base * 0.5f,
            targetValue  = base,
            animationSpec = infiniteRepeatable(
                animation  = tween(
                    durationMillis = 500 + i * 40,
                    easing         = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset((phase * 1000).toInt())
            ),
            label = "bar_$i"
        )
        if (isPlaying) animated else base * 0.6f
    }

    val barColor = MaterialTheme.colorScheme.primary

    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment     = Alignment.Bottom
    ) {
        animatedHeights.forEach { heightFraction ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor.copy(alpha = if (isPlaying) 1f else 0.5f))
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatAudioDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
