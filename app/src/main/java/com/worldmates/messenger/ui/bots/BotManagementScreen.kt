package com.worldmates.messenger.ui.bots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.data.model.Bot

/**
 * Екран "Мої боти" - управління створеними ботами
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotManagementScreen(
    state: MyBotsState,
    onCreateBot: () -> Unit,
    onEditBot: (Bot) -> Unit,
    onDeleteBot: (String) -> Unit,
    onRegenerateToken: (String) -> Unit,
    onBack: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf<Bot?>(null) }
    var showTokenDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мої боти") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onCreateBot) {
                        Icon(Icons.Default.Add, contentDescription = "Create Bot")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.bots.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("У вас немає ботів", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Створіть свого першого бота",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onCreateBot) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Створити бота")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.bots, key = { it.botId }) { bot ->
                        MyBotCard(
                            bot = bot,
                            onEdit = { onEditBot(bot) },
                            onDelete = { showDeleteDialog = bot },
                            onRegenerateToken = { onRegenerateToken(bot.botId) }
                        )
                    }
                }
            }
        }
    }

    // Token regenerated dialog
    if (state.regeneratedToken != null && showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Новий токен") },
            text = {
                Column {
                    Text("Ваш новий токен бота:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.regeneratedToken,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Збережіть токен. Він не буде показаний повторно.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { bot ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Видалити бота?") },
            text = { Text("Бот @${bot.username} та всі його дані будуть видалені. Ця дія незворотна.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBot(bot.botId)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Видалити")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Скасувати")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBotCard(
    bot: Bot,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRegenerateToken: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotAvatar(
                    avatarUrl = bot.avatar,
                    displayName = bot.displayName,
                    isVerified = bot.isVerified,
                    size = 48
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bot.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "@${bot.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Status chip
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = if (bot.isActive) "Active" else bot.status,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(24.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (bot.isActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BotStatChip("Користувачів: ${bot.totalUsers}")
                BotStatChip("Повідомлень: ${bot.messagesSent}")
                BotStatChip("Команд: ${bot.commandsCount}")
            }

            // Webhook status
            if (bot.webhookEnabled == 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Webhook: ${bot.webhookUrl ?: "configured"}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Редагувати")
                }
                OutlinedButton(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }

                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Перегенерувати токен") },
                        onClick = {
                            expanded = false
                            onRegenerateToken()
                        },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Видалити", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BotStatChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Екран створення нового бота
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBotScreen(
    state: CreateBotState,
    onUpdateForm: (
        username: String?, displayName: String?, description: String?,
        about: String?, category: String?, isPublic: Boolean?, canJoinGroups: Boolean?
    ) -> Unit,
    onCreateBot: () -> Unit,
    onBack: () -> Unit
) {
    val categories = listOf("general", "news", "weather", "tools", "entertainment", "support", "tech", "finance", "education", "games")
    var selectedCategoryIndex by remember { mutableIntStateOf(categories.indexOf(state.category).coerceAtLeast(0)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.createdBot != null) "Бот створено!" else "Новий бот") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.createdBot != null) {
            // Success screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Бот @${state.createdBot.username} створено!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (state.createdToken != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Bot Token (збережіть!):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.createdToken,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Цей токен не буде показано повторно!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Готово")
                }
            }
        } else {
            // Creation form
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = { onUpdateForm(it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }, null, null, null, null, null, null) },
                        label = { Text("Username *") },
                        placeholder = { Text("my_cool_bot") },
                        supportingText = { Text("Повинен закінчуватися на _bot") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.error?.contains("username", ignoreCase = true) == true
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = { onUpdateForm(null, it, null, null, null, null, null) },
                        label = { Text("Назва бота *") },
                        placeholder = { Text("My Cool Bot") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = { onUpdateForm(null, null, it, null, null, null, null) },
                        label = { Text("Опис") },
                        placeholder = { Text("Що робить бот...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.about,
                        onValueChange = { onUpdateForm(null, null, null, it, null, null, null) },
                        label = { Text("Короткий опис (about)") },
                        placeholder = { Text("Короткий текст у профілі") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Category selector
                item {
                    Text("Категорія:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories.size) { idx ->
                            FilterChip(
                                selected = idx == selectedCategoryIndex,
                                onClick = {
                                    selectedCategoryIndex = idx
                                    onUpdateForm(null, null, null, null, categories[idx], null, null)
                                },
                                label = { Text(getCategoryName(categories[idx])) }
                            )
                        }
                    }
                }

                // Toggles
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Публічний бот (видимий у каталозі)")
                        Switch(
                            checked = state.isPublic,
                            onCheckedChange = { onUpdateForm(null, null, null, null, null, it, null) }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Може приєднуватися до груп")
                        Switch(
                            checked = state.canJoinGroups,
                            onCheckedChange = { onUpdateForm(null, null, null, null, null, null, it) }
                        )
                    }
                }

                // Error
                if (state.error != null) {
                    item {
                        Text(
                            text = state.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Create button
                item {
                    Button(
                        onClick = onCreateBot,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isCreating && state.username.isNotBlank() && state.displayName.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Створити бота")
                    }
                }
            }
        }
    }
}
