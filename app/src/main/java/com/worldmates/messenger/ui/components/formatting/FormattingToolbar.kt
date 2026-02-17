package com.worldmates.messenger.ui.components.formatting

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Formatting Toolbar for message input
 *
 * Provides quick formatting buttons:
 * - Bold, Italic, Strikethrough, Underline
 * - Code, Spoiler, Quote
 * - Link insertion
 * - Mention/hashtag helpers
 */

// ==================== MAIN TOOLBAR ====================

@Composable
fun FormattingToolbar(
    isVisible: Boolean,
    hasSelection: Boolean,
    settings: FormattingSettings = FormattingSettings(),
    onBoldClick: () -> Unit = {},
    onItalicClick: () -> Unit = {},
    onStrikethroughClick: () -> Unit = {},
    onUnderlineClick: () -> Unit = {},
    onCodeClick: () -> Unit = {},
    onSpoilerClick: () -> Unit = {},
    onQuoteClick: () -> Unit = {},
    onLinkClick: () -> Unit = {},
    onMentionClick: () -> Unit = {},
    onHashtagClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(200)
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(150)
        ) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bold
                if (settings.allowBold) {
                    FormattingButton(
                        icon = Icons.Default.FormatBold,
                        label = "Жирний",
                        enabled = hasSelection,
                        onClick = onBoldClick
                    )
                }

                // Italic
                if (settings.allowItalic) {
                    FormattingButton(
                        icon = Icons.Default.FormatItalic,
                        label = "Курсив",
                        enabled = hasSelection,
                        onClick = onItalicClick
                    )
                }

                // Strikethrough
                if (settings.allowStrikethrough) {
                    FormattingButton(
                        icon = Icons.Default.FormatStrikethrough,
                        label = "Закреслений",
                        enabled = hasSelection,
                        onClick = onStrikethroughClick
                    )
                }

                // Underline
                if (settings.allowUnderline) {
                    FormattingButton(
                        icon = Icons.Default.FormatUnderlined,
                        label = "Підкреслений",
                        enabled = hasSelection,
                        onClick = onUnderlineClick
                    )
                }

                // Divider
                ToolbarDivider()

                // Code
                if (settings.allowCode) {
                    FormattingButton(
                        icon = Icons.Default.Code,
                        label = "Код",
                        enabled = hasSelection,
                        onClick = onCodeClick
                    )
                }

                // Spoiler
                if (settings.allowSpoilers) {
                    FormattingButton(
                        icon = Icons.Default.VisibilityOff,
                        label = "Спойлер",
                        enabled = hasSelection,
                        onClick = onSpoilerClick,
                        highlightColor = Color(0xFF6B5B95)
                    )
                }

                // Quote
                if (settings.allowQuotes) {
                    FormattingButton(
                        icon = Icons.Default.FormatQuote,
                        label = "Цитата",
                        enabled = hasSelection,
                        onClick = onQuoteClick
                    )
                }

                // Divider
                ToolbarDivider()

                // Link
                if (settings.allowLinks) {
                    FormattingButton(
                        icon = Icons.Default.Link,
                        label = "Посилання",
                        enabled = hasSelection,
                        onClick = onLinkClick
                    )
                }

                // Mention
                if (settings.allowMentions) {
                    FormattingButton(
                        icon = Icons.Default.AlternateEmail,
                        label = "Згадка",
                        enabled = true, // Always enabled
                        onClick = onMentionClick,
                        highlightColor = FormattedTextColors.MentionColor
                    )
                }

                // Hashtag
                if (settings.allowHashtags) {
                    FormattingButton(
                        icon = Icons.Default.Tag,
                        label = "Хештег",
                        enabled = true, // Always enabled
                        onClick = onHashtagClick,
                        highlightColor = FormattedTextColors.HashtagColor
                    )
                }
            }
        }
    }
}

// ==================== FORMATTING BUTTON ====================

@Composable
private fun FormattingButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (enabled) {
        MaterialTheme.colorScheme.surface
    } else {
        Color.Transparent
    }

    val iconColor = if (enabled) {
        highlightColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
    )
}

// ==================== COMPACT TOOLBAR (for inline use) ====================

@Composable
fun CompactFormattingToolbar(
    onBoldClick: () -> Unit = {},
    onItalicClick: () -> Unit = {},
    onCodeClick: () -> Unit = {},
    onSpoilerClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactButton(
            text = "B",
            fontWeight = FontWeight.Bold,
            onClick = onBoldClick
        )
        CompactButton(
            text = "I",
            fontStyle = FontStyle.Italic,
            onClick = onItalicClick
        )
        CompactButton(
            text = "</>",
            fontFamily = FontFamily.Monospace,
            onClick = onCodeClick
        )
        CompactButton(
            text = "||",
            onClick = onSpoilerClick
        )
    }
}

@Composable
private fun CompactButton(
    text: String,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
    fontFamily: FontFamily = FontFamily.Default,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Text(
            text = text,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontFamily = fontFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ==================== LINK DIALOG ====================

@Composable
fun LinkInsertDialog(
    selectedText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Вставити посилання")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Текст: \"$selectedText\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) },
                enabled = url.isNotBlank()
            ) {
                Text("Вставити")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}

// ==================== MENTION PICKER ====================

@Composable
fun MentionPicker(
    searchQuery: String,
    users: List<MentionUser>,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onUserSelect: (MentionUser) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 8.dp
    ) {
        Column {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text("Пошук користувача...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = FormattedTextColors.MentionColor
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистити")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // User list
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (users.isEmpty() && searchQuery.isNotEmpty()) {
                Text(
                    text = "Користувачів не знайдено",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                users.forEach { user ->
                    MentionUserItem(
                        user = user,
                        onClick = { onUserSelect(user) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionUserItem(
    user: MentionUser,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar placeholder
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = FormattedTextColors.MentionColor.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = user.username.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = FormattedTextColors.MentionColor
                )
            }
        }

        Column {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Data class for mention user
 */
data class MentionUser(
    val userId: Long,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null
)

// ==================== HASHTAG SUGGESTIONS ====================

@Composable
fun HashtagSuggestions(
    currentInput: String,
    recentTags: List<String>,
    trendingTags: List<String>,
    onTagSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 150.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Recent tags
            if (recentTags.isNotEmpty()) {
                Text(
                    text = "Нещодавні",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentTags.take(5).forEach { tag ->
                        HashtagChip(tag = tag, onClick = { onTagSelect(tag) })
                    }
                }
            }

            // Trending tags
            if (trendingTags.isNotEmpty()) {
                Text(
                    text = "Популярні",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trendingTags.take(5).forEach { tag ->
                        HashtagChip(tag = tag, onClick = { onTagSelect(tag) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HashtagChip(
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = FormattedTextColors.HashtagColor.copy(alpha = 0.15f)
    ) {
        Text(
            text = "#$tag",
            color = FormattedTextColors.HashtagColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
