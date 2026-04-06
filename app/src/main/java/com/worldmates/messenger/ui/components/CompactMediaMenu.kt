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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R

/**
 * 📎 Компактне меню медіа у стилі Telegram
 *
 * Показується як BottomSheet з сіткою опцій
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
    onStickerClick: () -> Unit,
    onGifClick: () -> Unit,
    onEmojiClick: () -> Unit,
    onStrapiClick: () -> Unit,
    onBatchClick: () -> Unit,
    onPollClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = colorScheme.surface,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Заголовок
                Text(
                    text = stringResource(R.string.attach),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Resolve labels in composable scope before passing to items()
                val labelGallery = stringResource(R.string.gallery)
                val labelCamera = stringResource(R.string.camera)
                val labelVideo = stringResource(R.string.video)
                val labelRecordVoice = stringResource(R.string.record_voice)
                val labelAudio = stringResource(R.string.audio_caption)
                val labelFile = stringResource(R.string.file)
                val labelLocation = stringResource(R.string.location)
                val labelContact = stringResource(R.string.contact_share)
                val labelEmoji = stringResource(R.string.emoji)
                val labelStickers = stringResource(R.string.stickers_label)
                val labelGif = stringResource(R.string.gif)
                val labelMultiple = stringResource(R.string.attach_multiple)
                val labelPoll = stringResource(R.string.poll_label)

                // Сітка опцій 3x4
                val mediaItems = buildList {
                    add(MediaOption(Icons.Default.Image, labelGallery, Color(0xFF2196F3)) { onPhotoClick(); onDismiss() })
                    add(MediaOption(Icons.Default.PhotoCamera, labelCamera, Color(0xFFE91E63)) { onCameraClick(); onDismiss() })
                    add(MediaOption(Icons.Default.VideoLibrary, labelVideo, Color(0xFF9C27B0)) { onVideoClick(); onDismiss() })
                    add(MediaOption(Icons.Default.Videocam, labelRecordVoice, Color(0xFFFF5722)) { onVideoCameraClick(); onDismiss() })
                    add(MediaOption(Icons.Default.AudioFile, labelAudio, Color(0xFF00BCD4)) { onAudioClick(); onDismiss() })
                    add(MediaOption(Icons.Default.InsertDriveFile, labelFile, Color(0xFF607D8B)) { onFileClick(); onDismiss() })
                    add(MediaOption(Icons.Default.LocationOn, labelLocation, Color(0xFF4CAF50)) { onLocationClick(); onDismiss() })
                    add(MediaOption(Icons.Default.Person, labelContact, Color(0xFFFF9800)) { onContactClick(); onDismiss() })
                    add(MediaOption(Icons.Default.EmojiEmotions, labelEmoji, Color(0xFFFFEB3B)) { onEmojiClick(); onDismiss() })
                    add(MediaOption(Icons.Default.StickyNote2, labelStickers, Color(0xFF795548)) { onStickerClick(); onDismiss() })
                    add(MediaOption(Icons.Default.Gif, labelGif, Color(0xFF3F51B5)) { onGifClick(); onDismiss() })
                    add(MediaOption(Icons.Default.Store, "Strapi CDN", Color(0xFF009688)) { onStrapiClick(); onDismiss() })
                    add(MediaOption(Icons.Default.PhotoLibrary, labelMultiple, Color(0xFF1E88E5)) { onBatchClick(); onDismiss() })
                    if (onPollClick != null) {
                        add(MediaOption(Icons.Default.HowToVote, labelPoll, Color(0xFF673AB7)) { onPollClick(); onDismiss() })
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(mediaItems) { option ->
                        CompactMediaOptionItem(option)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Опція медіа (дата клас)
 */
private data class MediaOption(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onClick: () -> Unit
)

/**
 * Елемент опції медіа в сітці
 */
@Composable
private fun CompactMediaOptionItem(
    option: MediaOption
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = option.onClick)
            .padding(vertical = 8.dp)
    ) {
        // Кругла іконка з кольором
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(option.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.label,
                tint = option.color,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Підпис
        Text(
            text = option.label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/**
 * 📎 Кнопка для відкриття меню (як в Telegram)
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
