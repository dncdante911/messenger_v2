package com.worldmates.messenger.ui.messages.selection

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

/**
 * ⚡ Кнопки дій в TopBar (режим вибору)
 *
 * Показує іконки "Редагувати" та "Видалити" справа в шапці.
 * - Кнопка "Вибрати все" (тільки якщо не всі вибрані)
 * - Кнопка "Редагувати" (тільки для 1 свого повідомлення)
 * - Кнопка "Закріпити" (тільки для груп, 1 повідомлення, адмін/модератор)
 * - Кнопка "Видалити" (червона)
 * - Кнопка "Закрити" режим вибору
 *
 * @param selectedCount Кількість вибраних повідомлень
 * @param totalCount Загальна кількість повідомлень
 * @param canEdit Чи можна редагувати (тільки для 1 свого повідомлення)
 * @param canPin Чи можна закріпити (для груп, адмін/модератор)
 * @param onEdit Callback для редагування
 * @param onPin Callback для закріплення
 * @param onDelete Callback для видалення
 * @param onSelectAll Callback для вибору всіх повідомлень
 * @param onClose Callback для закриття режиму вибору
 */
@Composable
fun SelectionTopBarActions(
    selectedCount: Int,
    totalCount: Int = 0,
    canEdit: Boolean,
    canPin: Boolean = false,
    onEdit: () -> Unit,
    onPin: () -> Unit = {},
    onDelete: () -> Unit,
    onSelectAll: () -> Unit = {},
    onClose: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка "Вибрати все" (тільки якщо не всі вибрані)
        if (totalCount > 0 && selectedCount < totalCount) {
            IconButton(onClick = onSelectAll) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Вибрати все",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // Кнопка "Редагувати" (тільки для 1 свого повідомлення)
        if (canEdit && selectedCount == 1) {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Редагувати",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // Кнопка "Закріпити" (тільки для груп, 1 повідомлення, адмін/модератор)
        if (canPin && selectedCount == 1) {
            IconButton(onClick = onPin) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = "Закріпити",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // Кнопка "Видалити"
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Видалити",
                tint = MaterialTheme.colorScheme.error
            )
        }

        // Кнопка "Закрити режим вибору"
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Закрити",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
