package com.worldmates.messenger.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.Bot
import com.worldmates.messenger.data.model.BotCategory
import com.worldmates.messenger.data.model.BotCommand

/**
 * Екран каталогу ботів - Bot Store
 * Пошук, категорії, список ботів
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotCatalogScreen(
    catalogState: BotCatalogState,
    onSearch: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onBotClick: (Bot) -> Unit,
    onCreateBotClick: () -> Unit,
    onMyBotsClick: () -> Unit,
    onBack: () -> Unit
) {
    var searchText by remember { mutableStateOf(catalogState.searchQuery) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bot_store)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = onMyBotsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.my_bots))
                    }
                    IconButton(onClick = onCreateBotClick) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_bot_cd))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    if (it.length >= 2 || it.isEmpty()) {
                        onSearch(it)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.bot_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText = ""
                            onSearch("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.close))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            // Categories horizontal scroll
            if (catalogState.categories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    // "All" chip
                    item {
                        FilterChip(
                            selected = catalogState.selectedCategory == null,
                            onClick = { onCategorySelected(null) },
                            label = { Text(stringResource(R.string.bot_category_all)) }
                        )
                    }
                    items(catalogState.categories) { category ->
                        val categoryName = getCategoryName(category.category)
                        FilterChip(
                            selected = catalogState.selectedCategory == category.category,
                            onClick = { onCategorySelected(category.category) },
                            label = { Text("$categoryName (${category.count})") }
                        )
                    }
                }
            }

            // Content
            when {
                catalogState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                catalogState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(catalogState.error, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { onSearch(searchText) }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                catalogState.bots.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.bot_not_found), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.bot_not_found_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(catalogState.bots, key = { it.botId }) { bot ->
                            BotListItem(bot = bot, onClick = { onBotClick(bot) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Елемент списку бота
 */
@Composable
fun BotListItem(
    bot: Bot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val verifiedLabel = stringResource(R.string.bot_verified_label)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            BotAvatar(
                avatarUrl = bot.avatar,
                displayName = bot.displayName,
                isVerified = bot.isVerified,
                size = 48
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bot.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (bot.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = verifiedLabel,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "@${bot.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!bot.description.isNullOrBlank()) {
                    Text(
                        text = bot.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Stats
            Column(horizontalAlignment = Alignment.End) {
                if (bot.totalUsers > 0) {
                    Text(
                        text = formatUserCount(bot.totalUsers),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                bot.category?.let {
                    val catName = getCategoryName(it)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = catName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Аватар бота
 */
@Composable
fun BotAvatar(
    avatarUrl: String?,
    displayName: String,
    isVerified: Boolean = false,
    size: Int = 48
) {
    Box(contentAlignment = Alignment.Center) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = displayName,
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Default bot avatar with first letter
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "B",
                    style = if (size >= 48) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Екран деталей бота
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotDetailScreen(
    state: BotDetailState,
    onStartChat: (Bot) -> Unit,
    onBack: () -> Unit
) {
    val verifiedLabel = stringResource(R.string.bot_verified_label)
    val verifiedBotLabel = stringResource(R.string.bot_verified_bot_label)
    val botStatUsers = stringResource(R.string.bot_stat_users)
    val botStatCategory = stringResource(R.string.bot_stat_category)
    val botStartChat = stringResource(R.string.bot_start_chat)
    val botDescriptionLabel = stringResource(R.string.bot_description_label)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.bot?.displayName ?: stringResource(R.string.bots_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.bot != null) {
            val bot = state.bot
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BotAvatar(
                            avatarUrl = bot.avatar,
                            displayName = bot.displayName,
                            isVerified = bot.isVerified,
                            size = 80
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = bot.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${bot.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (bot.isVerified) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = verifiedLabel,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(verifiedBotLabel, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Stats row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(botStatUsers, formatUserCount(bot.totalUsers))
                        StatItem(botStatCategory, getCategoryName(bot.category ?: "general"))
                    }
                }

                // Start chat button
                item {
                    Button(
                        onClick = { onStartChat(bot) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(botStartChat)
                    }
                }

                // Description
                if (!bot.description.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(botDescriptionLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(bot.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Commands
                if (state.commands.isNotEmpty()) {
                    item {
                        val commandsTitle = stringResource(R.string.bot_commands_count, state.commands.size)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    commandsTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                state.commands.forEach { cmd ->
                                    BotCommandItem(command = cmd)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BotCommandItem(command: BotCommand) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "/${command.command}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = command.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== UTILITY ====================

@Composable
fun getCategoryName(key: String): String = when (key) {
    "general"       -> stringResource(R.string.bot_category_general)
    "news"          -> stringResource(R.string.bot_category_news)
    "weather"       -> stringResource(R.string.bot_category_weather)
    "tools"         -> stringResource(R.string.bot_category_tools)
    "entertainment" -> stringResource(R.string.bot_category_entertainment)
    "support"       -> stringResource(R.string.bot_category_support)
    "tech"          -> stringResource(R.string.bot_category_tech)
    "finance"       -> stringResource(R.string.bot_category_finance)
    "education"     -> stringResource(R.string.bot_category_education)
    "games"         -> stringResource(R.string.bot_category_games)
    else            -> key.replaceFirstChar { it.uppercase() }
}

fun formatUserCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000 -> "${count / 1_000}K"
    else -> count.toString()
}
