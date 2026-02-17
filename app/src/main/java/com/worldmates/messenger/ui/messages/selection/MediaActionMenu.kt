package com.worldmates.messenger.ui.messages.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 📱 Меню для медіа файлів (фото/відео/аудіо)
 *
 * Показує "Поділитися" та "Видалити" для медіа контенту.
 * - Кнопка "Поділитися" в групи/користувачам
 * - Кнопка "Видалити" (тільки для своїх)
 * - Напівпрозорий фон з затемненням
 *
 * @param visible Чи показувати меню
 * @param isOwnMessage Чи це власне повідомлення (для показу кнопки видалення)
 * @param onShare Callback для поділитися
 * @param onDelete Callback для видалення
 * @param onDismiss Callback для закриття меню
 * @param modifier Modifier для кастомізації
 */
@Composable
fun MediaActionMenu(
    visible: Boolean,
    isOwnMessage: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (visible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss)
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Кнопка "Поділитися"
                    Button(
                        onClick = {
                            onShare()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Поділитися", fontWeight = FontWeight.SemiBold)
                    }

                    // Кнопка "Видалити" (тільки для своїх)
                    if (isOwnMessage) {
                        OutlinedButton(
                            onClick = {
                                onDelete()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Видалити", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
