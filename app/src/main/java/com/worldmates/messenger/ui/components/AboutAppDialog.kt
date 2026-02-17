package com.worldmates.messenger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.BuildConfig

/**
 * Диалог "Про додаток" (About App)
 *
 * Показывает информацию о приложении, версию, функции
 */
@Composable
fun AboutAppDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "WorldMates Messenger",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Версия
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Версія:",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        fontWeight = FontWeight.Bold
                    )
                }

                Divider()

                // Описание
                Text(
                    text = "Розроблено з ❤️ для спілкування",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Divider()

                // Функции
                Text(
                    text = "Функції:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FeatureItem("✓ Безпечні чати з шифруванням")
                    FeatureItem("✓ Групові чати та канали")
                    FeatureItem("✓ Аудіо та відео дзвінки")
                    FeatureItem("✓ Stories та статуси")
                    FeatureItem("✓ Хмарне сховище")
                    FeatureItem("✓ Стікери та емодзі")
                }

                Divider()

                // Copyright
                Text(
                    text = "© 2024-2026 WorldMates",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onDismiss
            ) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun FeatureItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium
    )
}
