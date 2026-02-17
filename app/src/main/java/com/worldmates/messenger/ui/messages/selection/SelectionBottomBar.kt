package com.worldmates.messenger.ui.messages.selection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 🔥 Нижня панель дій для вибраних повідомлень
 *
 * Відображається знизу екрану з напівпрозорими кнопками.
 * - Кнопка "Ответить" (активна тільки для 1 повідомлення)
 * - Кнопка "Переслать" (для будь-якої кількості)
 * - Красива анімація появи/зникнення
 *
 * @param selectedCount Кількість вибраних повідомлень
 * @param onForward Callback для пересилання
 * @param onReply Callback для відповіді (тільки для 1 повідомлення)
 * @param modifier Modifier для кастомізації
 */
@Composable
fun SelectionBottomBar(
    selectedCount: Int,
    onForward: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Кнопка "Ответить"
                OutlinedButton(
                    onClick = onReply,
                    enabled = selectedCount == 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        Icons.Default.Reply,
                        contentDescription = "Ответить",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ответить", fontWeight = FontWeight.SemiBold)
                }

                // Кнопка "Переслать"
                Button(
                    onClick = onForward,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                ) {
                    Icon(
                        Icons.Default.Forward,
                        contentDescription = "Переслать",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Переслать", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
