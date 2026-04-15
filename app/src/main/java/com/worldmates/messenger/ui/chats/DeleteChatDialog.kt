package com.worldmates.messenger.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.Chat

/**
 * Діалог видалення чату з трьома варіантами:
 *  1. Видалити для мене (тільки для поточного користувача)
 *  2. Видалити і очистити у обох (очищає переписку для обох сторін)
 *  3. Видалити і заблокувати (видаляє чат + блокує користувача)
 */
@Composable
fun DeleteChatDialog(
    chat: Chat,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDeleteAndBlock: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text  = stringResource(R.string.delete_chat_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text  = chat.username ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text  = stringResource(R.string.delete_chat_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Option 1: Delete for me only
                DeleteOption(
                    icon        = Icons.Default.Delete,
                    title       = stringResource(R.string.delete_for_me_only),
                    description = stringResource(R.string.delete_for_me_only_desc),
                    tint        = MaterialTheme.colorScheme.onSurface,
                    onClick     = {
                        onDeleteForMe()
                        onDismiss()
                    }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Option 2: Delete and clear for both
                DeleteOption(
                    icon        = Icons.Default.DeleteForever,
                    title       = stringResource(R.string.delete_clear_both),
                    description = stringResource(R.string.delete_clear_both_desc),
                    tint        = Color(0xFFE65100),
                    onClick     = {
                        onDeleteForEveryone()
                        onDismiss()
                    }
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Option 3: Delete and block
                DeleteOption(
                    icon        = Icons.Default.Block,
                    title       = stringResource(R.string.delete_and_block),
                    description = stringResource(R.string.delete_and_block_desc),
                    tint        = Color(0xFFD32F2F),
                    onClick     = {
                        onDeleteAndBlock()
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DeleteOption(
    icon: ImageVector,
    title: String,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
                fontWeight = FontWeight.Medium
            )
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
