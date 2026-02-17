package com.worldmates.messenger.ui.groups.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.network.SearchUser
import com.worldmates.messenger.util.toFullMediaUrl

// ==================== COLOR PALETTE ====================

object GroupColors {
    val TelegramBlue = Color(0xFF0088CC)
    val SuccessGreen = Color(0xFF00C853)
    val CardLight = Color(0xFFFFFFFF)
    val CardDark = Color(0xFF2D2D2D)
    val TextPrimaryLight = Color(0xFF1A1A1A)
    val TextSecondaryLight = Color(0xFF6B7280)
    val TextPrimaryDark = Color(0xFFFFFFFF)
    val TextSecondaryDark = Color(0xFF9CA3AF)
}

// ==================== MODERN INVITE MEMBERS DIALOG ====================

/**
 * Modern dialog for inviting members to a group
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernInviteMembersDialog(
    groupName: String,
    availableUsers: List<SearchUser>,
    existingMemberIds: List<Long>,
    onDismiss: () -> Unit,
    onInviteUsers: (List<Long>) -> Unit,
    onSearchUsers: (String) -> Unit,
    isSearching: Boolean = false,
    inviteLink: String? = null,
    onShareLink: ((String) -> Unit)? = null,
    onGenerateQr: (() -> Unit)? = null
) {
    val isDarkTheme = isSystemInDarkTheme()
    val cardBg = if (isDarkTheme) GroupColors.CardDark else GroupColors.CardLight
    val textPrimary = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) GroupColors.TextSecondaryDark else GroupColors.TextSecondaryLight

    var searchQuery by remember { mutableStateOf("") }
    var selectedUsers by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Filter users - exclude existing members
    val filteredUsers = availableUsers.filter { user ->
        user.userId !in existingMemberIds
    }

    // Search effect
    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(300) // Debounce
        onSearchUsers(searchQuery)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cardBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Invite Members",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = "to $groupName",
                        fontSize = 14.sp,
                        color = textSecondary
                    )
                }

                // Close button
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Invite Link Section (if available)
            if (inviteLink != null) {
                InviteLinkCard(
                    inviteLink = inviteLink,
                    onShareLink = onShareLink,
                    onGenerateQr = onGenerateQr,
                    isDarkTheme = isDarkTheme
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Search bar
            ModernSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search users by name or username...",
                isLoading = isSearching,
                isDarkTheme = isDarkTheme
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Selected users chips
            if (selectedUsers.isNotEmpty()) {
                SelectedUsersChips(
                    selectedUsers = filteredUsers.filter { it.userId in selectedUsers },
                    onRemoveUser = { userId ->
                        selectedUsers = selectedUsers - userId
                    },
                    isDarkTheme = isDarkTheme
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Users list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredUsers.isEmpty() && searchQuery.isNotBlank() && !isSearching) {
                    item {
                        EmptySearchState(
                            query = searchQuery,
                            isDarkTheme = isDarkTheme
                        )
                    }
                } else if (filteredUsers.isEmpty() && searchQuery.isBlank()) {
                    item {
                        EmptyUsersState(isDarkTheme = isDarkTheme)
                    }
                } else {
                    items(filteredUsers, key = { it.userId }) { user ->
                        SelectableUserCard(
                            user = user,
                            isSelected = user.userId in selectedUsers,
                            onToggleSelect = {
                                selectedUsers = if (user.userId in selectedUsers) {
                                    selectedUsers - user.userId
                                } else {
                                    selectedUsers + user.userId
                                }
                            },
                            isDarkTheme = isDarkTheme
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            // Bottom action bar
            AnimatedVisibility(
                visible = selectedUsers.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardBg,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = {
                            onInviteUsers(selectedUsers.toList())
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GroupColors.TelegramBlue
                        )
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Invite ${selectedUsers.size} ${if (selectedUsers.size == 1) "user" else "users"}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ==================== INVITE LINK CARD ====================

@Composable
fun InviteLinkCard(
    inviteLink: String,
    onShareLink: ((String) -> Unit)?,
    onGenerateQr: (() -> Unit)?,
    isDarkTheme: Boolean
) {
    val clipboardManager = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }

    val bgColor = if (isDarkTheme) Color(0xFF1E3A5F) else Color(0xFFE3F2FD)
    val textPrimary = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight

    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(2000)
            showCopied = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = null,
                    tint = GroupColors.TelegramBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Invite Link",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GroupColors.TelegramBlue
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Link text
            Text(
                text = inviteLink,
                fontSize = 13.sp,
                color = textPrimary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy button
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(inviteLink))
                        showCopied = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = GroupColors.TelegramBlue
                    )
                ) {
                    Icon(
                        if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showCopied) "Copied!" else "Copy",
                        fontSize = 13.sp
                    )
                }

                // Share button
                if (onShareLink != null) {
                    OutlinedButton(
                        onClick = { onShareLink(inviteLink) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = GroupColors.TelegramBlue
                        )
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Share", fontSize = 13.sp)
                    }
                }

                // QR button
                if (onGenerateQr != null) {
                    OutlinedButton(
                        onClick = onGenerateQr,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = GroupColors.TelegramBlue
                        )
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "QR", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ==================== MODERN SEARCH BAR ====================

@Composable
fun ModernSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    isLoading: Boolean,
    isDarkTheme: Boolean
) {
    val bgColor = if (isDarkTheme) Color(0xFF3A3A3A) else Color(0xFFF5F5F5)
    val textColor = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight
    val placeholderColor = if (isDarkTheme) GroupColors.TextSecondaryDark else GroupColors.TextSecondaryLight

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = GroupColors.TelegramBlue,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 15.sp,
                        color = placeholderColor
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = textColor
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = GroupColors.TelegramBlue,
                    strokeWidth = 2.dp
                )
            } else if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = placeholderColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ==================== SELECTED USERS CHIPS ====================

@Composable
fun SelectedUsersChips(
    selectedUsers: List<SearchUser>,
    onRemoveUser: (Long) -> Unit,
    isDarkTheme: Boolean
) {
    val bgColor = if (isDarkTheme) Color(0xFF1E3A5F) else Color(0xFFE3F2FD)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        selectedUsers.forEach { user ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = bgColor
            ) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini avatar
                    if (user.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = user.avatarUrl.toFullMediaUrl(),
                            contentDescription = user.username,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(GroupColors.TelegramBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (user.name?.firstOrNull() ?: user.username.firstOrNull() ?: 'U').uppercaseChar().toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = user.name ?: user.username,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GroupColors.TelegramBlue,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Remove button
                    IconButton(
                        onClick = { onRemoveUser(user.userId) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = GroupColors.TelegramBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== SELECTABLE USER CARD ====================

@Composable
fun SelectableUserCard(
    user: SearchUser,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    isDarkTheme: Boolean
) {
    val bgColor = when {
        isSelected -> GroupColors.TelegramBlue.copy(alpha = 0.1f)
        isDarkTheme -> GroupColors.CardDark
        else -> GroupColors.CardLight
    }
    val textPrimary = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) GroupColors.TextSecondaryDark else GroupColors.TextSecondaryLight

    Card(
        onClick = onToggleSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box {
                if (user.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = user.avatarUrl.toFullMediaUrl(),
                        contentDescription = user.username,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        GroupColors.TelegramBlue,
                                        Color(0xFF764ba2)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (user.name?.firstOrNull() ?: user.username.firstOrNull() ?: 'U').uppercaseChar().toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Selection indicator
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(GroupColors.SuccessGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // User info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name ?: user.username,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${user.username}",
                    fontSize = 14.sp,
                    color = textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Checkbox
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = GroupColors.TelegramBlue,
                    uncheckedColor = textSecondary.copy(alpha = 0.5f)
                )
            )
        }
    }
}

// ==================== EMPTY STATES ====================

@Composable
fun EmptySearchState(
    query: String,
    isDarkTheme: Boolean
) {
    val textPrimary = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) GroupColors.TextSecondaryDark else GroupColors.TextSecondaryLight

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = textSecondary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No users found",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No results for \"$query\".\nTry a different search term.",
            fontSize = 14.sp,
            color = textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EmptyUsersState(isDarkTheme: Boolean) {
    val textPrimary = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) GroupColors.TextSecondaryDark else GroupColors.TextSecondaryLight

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.People,
            contentDescription = null,
            tint = GroupColors.TelegramBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Search for users",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start typing to find users\nyou want to invite to this group",
            fontSize = 14.sp,
            color = textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ==================== QUICK INVITE OPTIONS ====================

@Composable
fun QuickInviteOptions(
    onShareLink: () -> Unit,
    onScanQr: () -> Unit,
    onShowQr: () -> Unit,
    isDarkTheme: Boolean
) {
    val cardBg = if (isDarkTheme) GroupColors.CardDark else GroupColors.CardLight

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickInviteButton(
            icon = Icons.Default.Share,
            label = "Share Link",
            onClick = onShareLink,
            isDarkTheme = isDarkTheme,
            modifier = Modifier.weight(1f)
        )
        QuickInviteButton(
            icon = Icons.Default.QrCodeScanner,
            label = "Scan QR",
            onClick = onScanQr,
            isDarkTheme = isDarkTheme,
            modifier = Modifier.weight(1f)
        )
        QuickInviteButton(
            icon = Icons.Default.QrCode,
            label = "My QR",
            onClick = onShowQr,
            isDarkTheme = isDarkTheme,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QuickInviteButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDarkTheme) Color(0xFF3A3A3A) else Color(0xFFF5F5F5)
    val textColor = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = GroupColors.TelegramBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}
