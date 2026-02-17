package com.worldmates.messenger.ui.bots

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.data.model.*

/**
 * Компоненти для бот-чату, які інтегруються в MessagesScreen.
 * Це ОКРЕМИЙ файл, щоб не розширювати MessagesScreen.kt (3984 рядки).
 *
 * Використання з MessagesScreen:
 *   if (isBot) {
 *       BotCommandBar(commands = botCommands, onCommandClick = { sendMessage("/${it.command}") })
 *       BotInlineKeyboard(markup = lastMessage.replyMarkup, onButtonClick = { ... })
 *   }
 */

/**
 * Панель команд бота - горизонтальна стрічка над полем вводу
 * Показує доступні slash-команди бота (як у Telegram)
 */
@Composable
fun BotCommandBar(
    commands: List<BotCommand>,
    isExpanded: Boolean = false,
    onCommandClick: (BotCommand) -> Unit,
    onToggleExpand: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (commands.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        // Collapsed: horizontal scroll of command chips
        AnimatedVisibility(visible = !isExpanded) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(commands) { command ->
                    SuggestionChip(
                        onClick = { onCommandClick(command) },
                        label = { Text("/${command.command}", style = MaterialTheme.typography.labelMedium) }
                    )
                }
                item {
                    if (commands.size > 5) {
                        SuggestionChip(
                            onClick = onToggleExpand,
                            label = { Text("...", style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
        }

        // Expanded: full list with descriptions
        AnimatedVisibility(visible = isExpanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Команди бота",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = onToggleExpand, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                        }
                    }
                    commands.forEach { command ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCommandClick(command) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "/${command.command}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(100.dp)
                            )
                            Text(
                                text = command.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Inline клавіатура бота - кнопки під повідомленням
 * (callback buttons, URL buttons)
 */
@Composable
fun BotInlineKeyboard(
    markup: BotReplyMarkup?,
    onButtonClick: (BotInlineButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = markup?.inlineKeyboard
    if (keyboard.isNullOrEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keyboard.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { button ->
                    OutlinedButton(
                        onClick = { onButtonClick(button) },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        if (button.url != null) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = button.text,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reply клавіатура бота - кнопки замість звичайної клавіатури
 */
@Composable
fun BotReplyKeyboard(
    markup: BotReplyMarkup?,
    onButtonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = markup?.keyboard
    if (keyboard.isNullOrEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keyboard.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { button ->
                    Button(
                        onClick = { onButtonClick(button.text) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = button.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Бот-хедер у чаті - показує інформацію про бота замість звичайного хедера
 */
@Composable
fun BotChatHeader(
    bot: Bot,
    onBotInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onBotInfoClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BotAvatar(
            avatarUrl = bot.avatar,
            displayName = bot.displayName,
            isVerified = bot.isVerified,
            size = 36
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bot.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (bot.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Verified",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "bot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Стартове повідомлення бота - показується при першому запуску
 * (як у Telegram - кнопка START)
 */
@Composable
fun BotStartMessage(
    bot: Bot,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BotAvatar(
            avatarUrl = bot.avatar,
            displayName = bot.displayName,
            isVerified = bot.isVerified,
            size = 80
        )
        Spacer(modifier = Modifier.height(16.dp))
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
        if (!bot.about.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = bot.about,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onStartClick,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("START", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Компонент для відображення poll/vote в чаті бота
 */
@Composable
fun BotPollCard(
    question: String,
    options: List<Pair<String, Float>>, // option text -> vote percentage (0.0..1.0)
    totalVotes: Int,
    isClosed: Boolean = false,
    selectedOption: Int? = null,
    onVote: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Poll header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Poll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isClosed) "Опитування закрито" else "Опитування",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = question,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Options
            options.forEachIndexed { index, (text, percentage) ->
                val isSelected = selectedOption == index
                val showResults = isClosed || selectedOption != null

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isClosed && selectedOption == null) { onVote(index) }
                ) {
                    if (showResults) {
                        // Show results with progress bar
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Background bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(percentage)
                                    .height(32.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                            )
                            // Text overlay
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = "${(percentage * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    } else {
                        // Simple button style before voting
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Total votes
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$totalVotes голосів",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
