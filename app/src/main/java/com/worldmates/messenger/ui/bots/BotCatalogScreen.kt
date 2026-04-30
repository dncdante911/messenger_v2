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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.Bot
import com.worldmates.messenger.data.model.BotCategory
import com.worldmates.messenger.data.model.BotCommand

/**
 * Екран каталогу ботів — Bot Store
 * Пошук, категорії, Featured/Trending секція, список ботів
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
                    if (it.length >= 2 || it.isEmpty()) onSearch(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.bot_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = ""; onSearch("") }) {
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
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    item {
                        FilterChip(
                            selected = catalogState.selectedCategory == null,
                            onClick = { onCategorySelected(null) },
                            label = { Text(stringResource(R.string.bot_category_all)) }
                        )
                    }
                    items(catalogState.categories) { category ->
                        FilterChip(
                            selected = catalogState.selectedCategory == category.category,
                            onClick = { onCategorySelected(category.category) },
                            label = { Text("${getCategoryName(category.category)} (${category.count})") }
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
                                Icons.Default.Search, contentDescription = null,
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
                    // Featured bots = system/verified + highest user count
                    val featuredBots = catalogState.bots
                        .filter { it.isVerified || it.isSystem || it.totalUsers >= 10 }
                        .sortedByDescending { it.totalUsers }
                        .take(8)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // ── Featured / Trending section ────────────────────────
                        if (featuredBots.isNotEmpty() && searchText.isBlank()) {
                            item {
                                FeaturedBotsSection(
                                    bots = featuredBots,
                                    onBotClick = onBotClick
                                )
                            }
                        }

                        // ── Section header for full list ───────────────────────
                        item {
                            Text(
                                text = if (searchText.isNotBlank())
                                    stringResource(R.string.bot_search_results_label, catalogState.bots.size)
                                else
                                    stringResource(R.string.bot_all_bots_label),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }

                        // ── Bot list ────────────────────────────────────────────
                        items(catalogState.bots, key = { it.botId }) { bot ->
                            BotListItem(
                                bot = bot,
                                onClick = { onBotClick(bot) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Featured / Trending section ──────────────────────────────────────────────

@Composable
fun FeaturedBotsSection(bots: List<Bot>, onBotClick: (Bot) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Whatshot,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.bot_featured_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(bots, key = { "featured_${it.botId}" }) { bot ->
                FeaturedBotCard(bot = bot, onClick = { onBotClick(bot) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
fun FeaturedBotCard(bot: Bot, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                BotAvatar(
                    avatarUrl = bot.avatar,
                    displayName = bot.displayName,
                    size = 52
                )
                // Type badge overlay
                val badge = getBotTypeBadge(bot)
                if (badge != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp),
                        shape = CircleShape,
                        color = badge.color
                    ) {
                        Text(
                            text = badge.icon,
                            fontSize = 10.sp,
                            modifier = Modifier.wrapContentSize(Alignment.Center)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = bot.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (bot.totalUsers > 0) {
                Text(
                    text = formatUserCount(bot.totalUsers),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Bot type badge ────────────────────────────────────────────────────────────

data class BotTypeBadge(val icon: String, val color: Color, val labelRes: Int)

@Composable
fun getBotTypeBadge(bot: Bot): BotTypeBadge? = when {
    bot.botType == "official" -> BotTypeBadge(
        icon = "★",
        color = Color(0xFFFFAB00),
        labelRes = R.string.bot_type_official
    )
    bot.isSystem -> BotTypeBadge(
        icon = "●",
        color = MaterialTheme.colorScheme.primary,
        labelRes = R.string.bot_type_system
    )
    bot.isVerified -> BotTypeBadge(
        icon = "✓",
        color = Color(0xFF4CAF50),
        labelRes = R.string.bot_type_verified
    )
    else -> null
}

// Bot type badge as inline row element (for list items)
@Composable
fun BotTypeBadgeChip(bot: Bot) {
    val badge = getBotTypeBadge(bot) ?: return
    val label = stringResource(badge.labelRes)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = badge.color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(badge.icon, fontSize = 10.sp, color = badge.color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = badge.color)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = bot.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    BotTypeBadgeChip(bot)
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
                // Feature pills row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (bot.hasMiniApp) MiniPill(stringResource(R.string.bot_pill_mini_app))
                    if (bot.isInline == 1) MiniPill(stringResource(R.string.bot_pill_inline))
                    if (bot.canJoinGroups == 1) MiniPill(stringResource(R.string.bot_pill_groups))
                }
            }

            // Stats column
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (bot.totalUsers > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.People, contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatUserCount(bot.totalUsers),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                bot.category?.let {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = getCategoryName(it),
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

@Composable
private fun MiniPill(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
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
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "B",
                    style = if (size >= 48) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                    color = Color.White,
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
            ) { CircularProgressIndicator() }
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
                        Spacer(modifier = Modifier.height(8.dp))
                        // Type badge chip, centered
                        BotTypeBadgeChip(bot)
                    }
                }

                // Stats row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(stringResource(R.string.bot_stat_users), formatUserCount(bot.totalUsers))
                        StatItem(stringResource(R.string.bot_stat_category), getCategoryName(bot.category ?: "general"))
                        if (bot.activeUsers24h > 0) {
                            StatItem(stringResource(R.string.bot_stat_active_24h), formatUserCount(bot.activeUsers24h))
                        }
                    }
                }

                // Feature badges row
                if (bot.hasMiniApp || bot.isInline == 1 || bot.canJoinGroups == 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (bot.hasMiniApp) {
                                FeatureTag(Icons.Default.Language, stringResource(R.string.bot_pill_mini_app))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (bot.isInline == 1) {
                                FeatureTag(Icons.Default.Code, stringResource(R.string.bot_pill_inline))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (bot.canJoinGroups == 1) {
                                FeatureTag(Icons.Default.Group, stringResource(R.string.bot_pill_groups))
                            }
                        }
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
                        Text(stringResource(R.string.bot_start_chat))
                    }
                }

                // Description
                if (!bot.description.isNullOrBlank()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.bot_description_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(bot.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // About (extended description — shown when bot has an about field)
                if (!bot.about.isNullOrBlank() && bot.about != bot.description) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.bot_about_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(bot.about, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Commands
                if (state.commands.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.bot_commands_count, state.commands.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                state.commands.forEach { cmd -> BotCommandItem(command = cmd) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureTag(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    count >= 1_000     -> "${count / 1_000}K"
    else               -> count.toString()
}
