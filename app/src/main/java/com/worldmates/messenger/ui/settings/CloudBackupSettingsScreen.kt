package com.worldmates.messenger.ui.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmates.messenger.data.model.CloudBackupSettings
import com.worldmates.messenger.data.model.SyncProgress
import com.worldmates.messenger.data.model.BackupFileInfo
import kotlinx.coroutines.launch

/**
 * 📦 CLOUD BACKUP: Экран управления бэкапами
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: CloudBackupViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val backupProgress by viewModel.backupProgress.collectAsState()
    val backupList by viewModel.backupList.collectAsState()
    val backupStatistics by viewModel.backupStatistics.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val scope = rememberCoroutineScope()

    var showBackupProviderDialog by remember { mutableStateOf(false) }
    var showCreateBackupDialog by remember { mutableStateOf(false) }
    var showBackupListDialog by remember { mutableStateOf(false) }
    var showRestoreBackupDialog by remember { mutableStateOf<BackupFileInfo?>(null) }

    // Загрузить список бэкапов при открытии экрана
    LaunchedEffect(Unit) {
        viewModel.loadBackupList()
        viewModel.refreshStatistics()
    }

    // Показать ошибку если есть
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сховище та бэкап") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ==================== СТАТИСТИКА ====================
            item {
                SectionHeader("Статистика сховища")
            }

            item {
                backupStatistics?.let { stats ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            StatRow("📊 Всього повідомлень:", "${stats.totalMessages}")
                            StatRow("📤 Відправлено:", "${stats.messagesSent}")
                            StatRow("📥 Отримано:", "${stats.messagesReceived}")
                            StatRow("📷 Медіа файлів:", "${stats.mediaFilesCount}")
                            StatRow("💾 Розмір медіа:", "${stats.mediaSizeMb} MB")
                            StatRow("📦 Загальний розмір:", "${stats.totalStorageMb} MB")
                        }
                    }
                } ?: run {
                    // Загрузка статистики
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Завантаження статистики...")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ==================== ОБЛАЧНЫЙ БЭКАП ====================
            item {
                SectionHeader("Налаштування бекапу")
            }

            item {
                settings?.let { s ->
                    SettingsItemWithSwitch(
                        icon = Icons.Default.CloudUpload,
                        title = "Увімкнути бекап",
                        subtitle = "Автоматичне збереження в хмару",
                        checked = s.backupEnabled,
                        onCheckedChange = { viewModel.updateBackupEnabled(it) }
                    )
                }
            }

            item {
                settings?.let { s ->
                    if (s.backupEnabled) {
                        SettingsItem(
                            icon = Icons.Default.Cloud,
                            title = "Провайдер хмари",
                            subtitle = s.backupProvider.displayName,
                            onClick = { showBackupProviderDialog = true }
                        )
                    }
                }
            }

            item {
                settings?.let { s ->
                    if (s.backupEnabled && s.lastBackupTime != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Останній бекап: ${formatTime(s.lastBackupTime)}")
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ==================== УПРАВЛЕНИЕ БЭКАПАМИ ====================
            item {
                SectionHeader("Керування бекапами")
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Add,
                    title = "Створити бекап зараз",
                    subtitle = "Зберегти всі дані на сервер",
                    onClick = { showCreateBackupDialog = true }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.List,
                    title = "Список бекапів",
                    subtitle = if (backupList.isEmpty()) "Немає доступних бекапів" else "${backupList.size} бекапів",
                    onClick = { showBackupListDialog = true }
                )
            }

            // Прогресс-бар создания/восстановления бэкапа
            item {
                if (backupProgress.isRunning) {
                    BackupProgressBar(backupProgress)
                }
            }

            // Ошибка
            errorMessage?.let { error ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // ==================== ДИАЛОГИ ====================

    if (showBackupProviderDialog) {
        BackupProviderDialog(
            currentProvider = settings!!.backupProvider,
            onDismiss = { showBackupProviderDialog = false },
            onSelect = { provider ->
                viewModel.updateBackupProvider(provider)
                showBackupProviderDialog = false
            }
        )
    }

    // Диалог создания бэкапа
    if (showCreateBackupDialog) {
        CreateBackupDialog(
            currentProvider = settings!!.backupProvider,
            onDismiss = { showCreateBackupDialog = false },
            onCreate = { uploadToCloud ->
                viewModel.createBackup(uploadToCloud)
                showCreateBackupDialog = false
            }
        )
    }

    // Диалог списка бэкапов
    if (showBackupListDialog) {
        BackupListDialog(
            backups = backupList,
            onDismiss = { showBackupListDialog = false },
            onRestore = { backup ->
                showRestoreBackupDialog = backup
                showBackupListDialog = false
            },
            onDelete = { backup ->
                viewModel.deleteBackup(backup)
            }
        )
    }

    // Диалог подтверждения восстановления
    showRestoreBackupDialog?.let { backup ->
        RestoreBackupDialog(
            backup = backup,
            onDismiss = { showRestoreBackupDialog = null },
            onConfirm = {
                viewModel.restoreFromBackup(backup)
                showRestoreBackupDialog = null
            }
        )
    }
}

// ==================== UI КОМПОНЕНТЫ ====================

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsItemWithSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * Прогресс-бар бэкапа
 */
@Composable
private fun BackupProgressBar(progress: com.worldmates.messenger.data.model.BackupProgress) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progress.currentStep,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${progress.progress}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progress.progress.toFloat() / 100f,
                modifier = Modifier.fillMaxWidth()
            )

            progress.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "❌ $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ==================== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ====================

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val hours = diff / (1000 * 60 * 60)
    return when {
        hours < 1 -> "щойно"
        hours < 24 -> "$hours год. тому"
        else -> "${hours / 24} дн. тому"
    }
}