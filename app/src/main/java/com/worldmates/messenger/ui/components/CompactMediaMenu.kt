package com.worldmates.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R

/**
 * Modern attachment menu — Telegram-style BottomSheet
 *
 * Clean 4-column grid: Gallery, Camera, Video, Audio / File, Location, Contact, Poll
 * Emoji / Stickers / GIF are in the input tab bar — not duplicated here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactMediaMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPhotoClick: () -> Unit,
    onCameraClick: () -> Unit,
    onVideoClick: () -> Unit,
    onVideoCameraClick: () -> Unit,
    onAudioClick: () -> Unit,
    onFileClick: () -> Unit,
    onLocationClick: () -> Unit,
    onContactClick: () -> Unit,
    onPollClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.attach),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            val labelGallery      = stringResource(R.string.gallery)
            val labelCamera       = stringResource(R.string.camera)
            val labelVideo        = stringResource(R.string.video)
            val labelVideoMsg     = stringResource(R.string.record_voice)
            val labelAudio        = stringResource(R.string.audio_caption)
            val labelFile         = stringResource(R.string.file)
            val labelLocation     = stringResource(R.string.location)
            val labelContact      = stringResource(R.string.contact_share)
            val labelPoll         = stringResource(R.string.poll_label)

            val menuItems = buildList {
                add(AttachItem(Icons.Default.PhotoLibrary,   labelGallery,  Color(0xFF2196F3)) { onPhotoClick(); onDismiss() })
                add(AttachItem(Icons.Default.PhotoCamera,    labelCamera,   Color(0xFFE91E63)) { onCameraClick(); onDismiss() })
                add(AttachItem(Icons.Default.VideoLibrary,   labelVideo,    Color(0xFF9C27B0)) { onVideoClick(); onDismiss() })
                add(AttachItem(Icons.Default.Videocam,       labelVideoMsg, Color(0xFFFF5722)) { onVideoCameraClick(); onDismiss() })
                add(AttachItem(Icons.Default.AudioFile,      labelAudio,    Color(0xFF00BCD4)) { onAudioClick(); onDismiss() })
                add(AttachItem(Icons.Default.InsertDriveFile,labelFile,     Color(0xFF607D8B)) { onFileClick(); onDismiss() })
                add(AttachItem(Icons.Default.LocationOn,     labelLocation, Color(0xFF4CAF50)) { onLocationClick(); onDismiss() })
                add(AttachItem(Icons.Default.Person,         labelContact,  Color(0xFFFF9800)) { onContactClick(); onDismiss() })
                if (onPollClick != null) {
                    add(AttachItem(Icons.Default.HowToVote, labelPoll, Color(0xFF673AB7)) { onPollClick(); onDismiss() })
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(menuItems) { item ->
                    AttachItemView(item)
                }
            }
        }
    }
}

private data class AttachItem(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun AttachItemView(item: AttachItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = item.onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            item.color.copy(alpha = 0.25f),
                            item.color.copy(alpha = 0.10f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = item.color,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Attach button for the input bar
 */
@Composable
fun AttachButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = stringResource(R.string.attach),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
