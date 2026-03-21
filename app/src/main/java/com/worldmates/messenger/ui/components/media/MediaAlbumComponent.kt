package com.worldmates.messenger.ui.components.media

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R

/**
 * Компонент для відображення медіа-альбому — мозаїчна сітка фото/відео
 * (аналог альбомів у Telegram/WhatsApp).
 *
 * Правила розкладки:
 *  1 файл  — на всю ширину
 *  2 файли — пліч-о-пліч
 *  3 файли — один великий зліва + два маленьких стовпчиком справа
 *  4 файли — сітка 2×2
 *  5+ файлів — 3-колонна сітка; в останній комірці (+N ще)
 *
 * @param mediaUrls   список URL зображень/відео
 * @param mediaTypes  тип кожного елемента: "image" або "video"
 * @param onMediaClick callback при натисканні з індексом
 * @param modifier    зовнішній модифікатор
 */
@Composable
fun MediaAlbumComponent(
    mediaUrls: List<String>,
    mediaTypes: List<String>,
    onMediaClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mediaUrls.isEmpty()) return

    val maxVisible    = 9
    val displayUrls   = mediaUrls.take(maxVisible)
    val displayTypes  = mediaTypes.take(maxVisible)
    val extraCount    = (mediaUrls.size - maxVisible).coerceAtLeast(0)
    val count         = displayUrls.size

    val cornerShape = RoundedCornerShape(12.dp)
    val gap         = 2.dp

    Box(modifier = modifier.clip(cornerShape)) {
        when (count) {
            // ── 1 item ────────────────────────────────────────────────────────
            1 -> {
                MediaCell(
                    url       = displayUrls[0],
                    type      = displayTypes[0],
                    index     = 0,
                    showExtra = false,
                    extraCount = 0,
                    onClick   = onMediaClick,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.5f)
                )
            }

            // ── 2 items ───────────────────────────────────────────────────────
            2 -> {
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    repeat(2) { i ->
                        MediaCell(
                            url        = displayUrls[i],
                            type       = displayTypes[i],
                            index      = i,
                            showExtra  = false,
                            extraCount = 0,
                            onClick    = onMediaClick,
                            modifier   = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }
            }

            // ── 3 items ───────────────────────────────────────────────────────
            3 -> {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    // Left: large cell
                    MediaCell(
                        url        = displayUrls[0],
                        type       = displayTypes[0],
                        index      = 0,
                        showExtra  = false,
                        extraCount = 0,
                        onClick    = onMediaClick,
                        modifier   = Modifier
                            .weight(2f)
                            .aspectRatio(0.75f)
                    )
                    // Right: two stacked cells
                    Column(
                        modifier              = Modifier.weight(1f),
                        verticalArrangement   = Arrangement.spacedBy(gap)
                    ) {
                        repeat(2) { i ->
                            MediaCell(
                                url        = displayUrls[i + 1],
                                type       = displayTypes[i + 1],
                                index      = i + 1,
                                showExtra  = false,
                                extraCount = 0,
                                onClick    = onMediaClick,
                                modifier   = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.5f)
                            )
                        }
                    }
                }
            }

            // ── 4 items ───────────────────────────────────────────────────────
            4 -> {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(2) { row ->
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            repeat(2) { col ->
                                val idx = row * 2 + col
                                MediaCell(
                                    url        = displayUrls[idx],
                                    type       = displayTypes[idx],
                                    index      = idx,
                                    showExtra  = false,
                                    extraCount = 0,
                                    onClick    = onMediaClick,
                                    modifier   = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                }
            }

            // ── 5+ items: 3-column grid ───────────────────────────────────────
            else -> {
                val rows = (count + 2) / 3
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(rows) { row ->
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            repeat(3) { col ->
                                val idx       = row * 3 + col
                                val isLast    = idx == maxVisible - 1
                                val hasExtra  = isLast && extraCount > 0

                                if (idx < count) {
                                    MediaCell(
                                        url        = displayUrls[idx],
                                        type       = displayTypes[idx],
                                        index      = idx,
                                        showExtra  = hasExtra,
                                        extraCount = extraCount,
                                        onClick    = onMediaClick,
                                        modifier   = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                    )
                                } else {
                                    // Empty filler cell to maintain 3-column alignment
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Internal cell ────────────────────────────────────────────────────────────

@Composable
private fun MediaCell(
    url: String,
    type: String,
    index: Int,
    showExtra: Boolean,
    extraCount: Int,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.1f))
            .clickable { onClick(index) },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model              = url,
            contentDescription = if (type == "video") stringResource(R.string.cd_video_thumbnail) else stringResource(R.string.cd_album_image),
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop,
            onError            = {
                Log.e("MediaAlbumComponent", "Failed to load media: $url, error: ${it.result.throwable}")
            }
        )

        // Play button overlay for video items
        if (type == "video") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.PlayCircle,
                    contentDescription = stringResource(R.string.media_album_video),
                    tint               = Color.White,
                    modifier           = Modifier.size(40.dp)
                )
            }
        }

        // "+N more" overlay on the last cell when album exceeds maxVisible
        if (showExtra && extraCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = stringResource(R.string.media_album_more, extraCount),
                    color      = Color.White,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
