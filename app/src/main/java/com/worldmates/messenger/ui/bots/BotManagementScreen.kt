package com.worldmates.messenger.ui.bots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
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
    onRssFeeds: (Bot) -> Unit = {},
    onSetMiniApp: (botId: String, url: String) -> Unit = { _, _ -> },
    onClearMiniApp: (botId: String) -> Unit = {},
    onBack: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf<Bot?>(null) }
    var showTokenDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_bots)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = onCreateBot) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_bot_cd))
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
                        Text(stringResource(R.string.bot_no_bots_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.bot_no_bots_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onCreateBot) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.bot_create_button))
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
                            onRegenerateToken = { onRegenerateToken(bot.botId) },
                            onRssFeeds = { onRssFeeds(bot) },
                            onSetMiniApp = { url -> onSetMiniApp(bot.botId, url) },
                            onClearMiniApp = { onClearMiniApp(bot.botId) }
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
            title = { Text(stringResource(R.string.bot_new_token_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.bot_token_save_label))
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
                        stringResource(R.string.bot_token_once_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { bot ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.bot_delete_confirm_title)) },
            text = { Text(stringResource(R.string.bot_delete_confirm_message, bot.username)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBot(bot.botId)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
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
    onRegenerateToken: () -> Unit,
    onRssFeeds: () -> Unit = {},
    onSetMiniApp: (url: String) -> Unit = {},
    onClearMiniApp: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var showMiniAppDialog by remember { mutableStateOf(false) }
    var miniAppUrlInput by remember(bot.webAppUrl) { mutableStateOf(bot.webAppUrl ?: "") }
    var miniAppUrlError by remember { mutableStateOf(false) }

    // Mini App URL setup dialog
    if (showMiniAppDialog) {
        AlertDialog(
            onDismissRequest = { showMiniAppDialog = false },
            title = { Text(stringResource(R.string.mini_app_set_url_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mini_app_set_url_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = miniAppUrlInput,
                        onValueChange = { miniAppUrlInput = it; miniAppUrlError = false },
                        label = { Text(stringResource(R.string.mini_app_set_url_label)) },
                        placeholder = { Text("https://my-mini-app.example.com") },
                        isError = miniAppUrlError,
                        supportingText = if (miniAppUrlError) ({ Text(stringResource(R.string.mini_app_url_error), color = MaterialTheme.colorScheme.error) }) else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = miniAppUrlInput.trim()
                    if (url.isNotEmpty() && !url.startsWith("https://")) {
                        miniAppUrlError = true
                    } else {
                        onSetMiniApp(url)
                        showMiniAppDialog = false
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showMiniAppDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

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
                // Status badge (display-only, not clickable)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (bot.isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = if (bot.isActive) stringResource(R.string.bot_status_active) else bot.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bot.isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BotStatChip(stringResource(R.string.bot_stat_users_count, bot.totalUsers))
                BotStatChip(stringResource(R.string.bot_stat_messages_count, bot.messagesSent))
                BotStatChip(stringResource(R.string.bot_stat_commands_count, bot.commandsCount))
            }

            // Mini App URL status
            if (bot.webAppUrl != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = bot.webAppUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { miniAppUrlInput = bot.webAppUrl; showMiniAppDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onClearMiniApp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.mini_app_remove), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
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

            // Action buttons — icon + short label, evenly spaced in one row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotActionButton(
                    icon  = Icons.Default.Edit,
                    label = stringResource(R.string.edit),
                    onClick = onEdit
                )
                BotActionButton(
                    icon  = Icons.Default.RssFeed,
                    label = "RSS",
                    onClick = onRssFeeds
                )
                BotActionButton(
                    icon  = Icons.Default.Language,
                    label = stringResource(R.string.mini_app_add),
                    onClick = { miniAppUrlInput = bot.webAppUrl ?: ""; showMiniAppDialog = true }
                )
                // "⋮" more menu
                Box {
                    BotActionButton(
                        icon  = Icons.Default.MoreVert,
                        label = stringResource(R.string.more_options_label),
                        onClick = { expanded = true }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bot_regenerate_token)) },
                        onClick = {
                            expanded = false
                            onRegenerateToken()
                        },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
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

/** Small icon-button with a text label below, used in the bot card action row. */
@Composable
private fun BotActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
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
                title = {
                    Text(
                        if (state.createdBot != null) stringResource(R.string.bot_created_title)
                        else stringResource(R.string.bot_new_bot_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_cd))
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
                    stringResource(R.string.bot_created_success, state.createdBot.username),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (state.createdToken != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.bot_token_save_hint), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.createdToken,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.bot_token_once_warning2),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.done))
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
                        label = { Text(stringResource(R.string.bot_username_label)) },
                        placeholder = { Text("my_cool_bot") },
                        supportingText = { Text(stringResource(R.string.bot_username_supporting)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.error?.contains("username", ignoreCase = true) == true
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = { onUpdateForm(null, it, null, null, null, null, null) },
                        label = { Text(stringResource(R.string.bot_name_hint)) },
                        placeholder = { Text("My Cool Bot") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = { onUpdateForm(null, null, it, null, null, null, null) },
                        label = { Text(stringResource(R.string.bot_description_label)) },
                        placeholder = { Text(stringResource(R.string.bot_description_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.about,
                        onValueChange = { onUpdateForm(null, null, null, it, null, null, null) },
                        label = { Text(stringResource(R.string.bot_short_about)) },
                        placeholder = { Text(stringResource(R.string.bot_about_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Category selector
                item {
                    Text(stringResource(R.string.bot_category_label), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories.size) { idx ->
                            val catName = getCategoryName(categories[idx])
                            FilterChip(
                                selected = idx == selectedCategoryIndex,
                                onClick = {
                                    selectedCategoryIndex = idx
                                    onUpdateForm(null, null, null, null, categories[idx], null, null)
                                },
                                label = { Text(catName) }
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
                        Text(stringResource(R.string.bot_public_label))
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
                        Text(stringResource(R.string.bot_can_join_groups))
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
                        Text(stringResource(R.string.bot_create_button))
                    }
                }
            }
        }
    }
}
