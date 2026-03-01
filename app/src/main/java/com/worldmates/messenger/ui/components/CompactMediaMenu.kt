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

                // Сітка опцій 3x4
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        listOf(
                            MediaOption(
                                icon = Icons.Default.Image,
                                label = labelGallery,
                                color = Color(0xFF2196F3), // Blue
                                onClick = {
                                    onPhotoClick()
                                    onDismiss()
                                }
                            ),
                            MediaOption(
                                icon = Icons.Default.PhotoCamera,
                                label = labelCamera,
                                color = Color(0xFFE91E63), // Pink
                                onClick = {
                                    onCameraClick()
                                    onDismiss()
                                }
                            ),
                            MediaOption(
                                icon = Icons.Default.VideoLibrary,
                                label = labelVideo,
                                color = Color(0xFF9C27B0), // Purple
                                onClick = {
                                    onVideoClick()
                                    onDismiss()
                                }
                            ),
                            MediaOption(
                                icon = Icons.Default.Videocam,
                                label = labelRecordVoice,
                                color = Color(0xFFFF5722), // Deep Orange
                                onClick = {
                                    onVideoCameraClick()
                                    onDismiss()
                                }
                            ),
                            MediaOption(
                                icon = Icons.Default.AudioFile,
                                label = labelAudio,
                                color = Color(0xFF00BCD4), // Cyan
                                onClick = {
                                    onAudioClick()
                                    onDismiss()
                                }
                            ),
                            MediaOption(
                                icon = Icons.Default.InsertDriveFile,
                                label = labelFile,
                                color = Color(0xFF607D8B), // Blue Grey
                                onClick = {
                                    onFileClick()
                                    onDismiss()
                                }
                            ),
                            MediaOption(
                                icon = Icons.Default.LocationOn,
                                label = labelLocation,
                                color = Color(0xFF4CAF50), // Green
                                onClick = {
                                    onLocationClick()
                                    onDismiss()
                                }
                            ),
                            MediaOption(
                                icon = Icons.Default.Person,
                                label = labelContact,
                                color = Color(0xFFFF9800), // Orange
                                onClick = {
                                    onContactClick()
                                    onDismiss()
                                }
                            ),
                            MediaOption(
                                icon = Icons.Default.EmojiEmotions,
                                label = labelEmoji,
                                color = Color(0xFFFFEB3B), // Yellow
                                onClick = {
                                    onEmojiClick()
                                    onDismiss()
                                }
                            ),
                            MediaOption(
                                icon = Icons.Default.StickyNote2,
                                label = labelStickers,
                                color = Color(0xFF795548), // Brown
                                onClick = {
                                    onStickerClick()
                                    onDismiss()
                                }
                            ),
                            MediaOption(
                                icon = Icons.Default.Gif,
                                label = labelGif,
                                color = Color(0xFF3F51B5), // Indigo
                                onClick = {
                                    onGifClick()
                                    onDismiss()
                                }
                            ),
                            MediaOption(
                                icon = Icons.Default.Store,
                                label = "Strapi CDN",
                                color = Color(0xFF009688), // Teal
                                onClick = {
                                    onStrapiClick()
                                    onDismiss()
                                }
                            )
                        )
                    ) { option ->
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
