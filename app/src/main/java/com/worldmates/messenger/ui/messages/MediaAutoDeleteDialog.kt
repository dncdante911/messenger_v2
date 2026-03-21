package com.worldmates.messenger.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.MediaAutoDeleteOption

/**
 * Dialog for selecting the media auto-delete timer for a private chat.
 *
 * Shown when the user taps "Автоудаление медиа" in the chat header menu.
 * Selecting an option saves it via the ViewModel; the server will apply it to
 * all future media messages sent in this chat by the caller.
 *
 * @param currentOption  Currently active option (shown as pre-selected).
 * @param onOptionSelected  Called with the chosen option; callee should persist + dismiss.
 * @param onDismiss  Called when the user taps "Закрыть" or outside the dialog.
 */
@Composable
fun MediaAutoDeleteDialog(
    currentOption: MediaAutoDeleteOption,
    onOptionSelected: (MediaAutoDeleteOption) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(currentOption) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.media_auto_delete_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Description
                Text(
                    text = stringResource(R.string.media_auto_delete_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(Modifier.height(4.dp))

                // Option list
                MediaAutoDeleteOption.values().forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = option
                                onOptionSelected(option)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = {
                                selected = option
                                onOptionSelected(option)
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (option != MediaAutoDeleteOption.NEVER) {
                            Text(
                                text = formatStorageHint(option),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

/**
 * Returns an approximate storage hint string for a given auto-delete period.
 * This is a rough estimate shown next to each option (e.g. "~50–200 MB").
 */
private fun formatStorageHint(option: MediaAutoDeleteOption): String {
    return when (option) {
        MediaAutoDeleteOption.ONE_DAY    -> "~1 день"
        MediaAutoDeleteOption.THREE_DAYS -> "~3 дня"
        MediaAutoDeleteOption.ONE_WEEK   -> "~1 нед."
        MediaAutoDeleteOption.TWO_WEEKS  -> "~2 нед."
        MediaAutoDeleteOption.ONE_MONTH  -> "~1 мес."
        else                             -> ""
    }
}
