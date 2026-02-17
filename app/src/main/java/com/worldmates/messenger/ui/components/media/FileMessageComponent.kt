package com.worldmates.messenger.ui.components.media

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Компонент для отображения файла в сообщении
 *
 * Извлечено из MessagesScreen.kt (строка 2044-2065) для уменьшения размера файла
 *
 * @param fileUrl URL файла
 * @param fileName Имя файла (опционально, извлекается из URL)
 * @param fileSize Размер файла в байтах (опционально)
 * @param textColor Цвет текста
 * @param showTextAbove Есть ли текст над файлом (для отступа)
 * @param modifier Дополнительный модификатор
 */
@Composable
fun FileMessageComponent(
    fileUrl: String,
    fileName: String? = null,
    fileSize: Long? = null,
    textColor: Color = Color.Black,
    showTextAbove: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayFileName = fileName ?: fileUrl.substringAfterLast("/")
    val fileExtension = displayFileName.substringAfterLast(".", "")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (showTextAbove) 8.dp else 0.dp)
            .clickable {
                // Открываем файл
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(fileUrl)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("FileMessageComponent", "Не вдалося відкрити файл: $fileUrl", e)
                }
            }
    ) {
        // Иконка в зависимости от типа файла
        Icon(
            imageVector = getFileIcon(fileExtension),
            contentDescription = "File",
            tint = textColor,
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Имя файла
            Text(
                text = displayFileName,
                color = textColor,
                fontSize = 14.sp,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Размер файла (если есть)
            if (fileSize != null) {
                Text(
                    text = formatFileSize(fileSize),
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Иконка скачивания
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Download",
            tint = textColor.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Получить иконку для файла по расширению
 */
private fun getFileIcon(extension: String): ImageVector {
    return when (extension.lowercase()) {
        "pdf" -> Icons.Default.PictureAsPdf
        "doc", "docx" -> Icons.Default.Description
        "xls", "xlsx" -> Icons.Default.TableChart
        "ppt", "pptx" -> Icons.Default.Slideshow
        "zip", "rar", "7z" -> Icons.Default.FolderZip
        "mp3", "wav", "ogg" -> Icons.Default.AudioFile
        "mp4", "avi", "mkv" -> Icons.Default.VideoFile
        "jpg", "jpeg", "png", "gif" -> Icons.Default.Image
        "txt" -> Icons.Default.TextSnippet
        else -> Icons.Default.InsertDriveFile
    }
}

/**
 * Форматировать размер файла в удобочитаемый вид
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
