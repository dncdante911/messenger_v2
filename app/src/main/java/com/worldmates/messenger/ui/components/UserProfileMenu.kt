package com.worldmates.messenger.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.util.toFullMediaUrl

/**
 * Данные пользователя для отображения в меню
 */
data class UserMenuData(
    val userId: Long,
    val username: String,
    val name: String?,
    val avatar: String?,
    val isVerified: Boolean = false,
    val isOnline: Boolean = false,
    val lastSeen: String? = null,
    val karmaScore: Float? = null,
    val trustLevel: String? = null,
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
    val isFriend: Boolean = false,
    val isFollowing: Boolean = false
)

/**
 * Действия в меню пользователя
 */
sealed class UserMenuAction {
    object ViewProfile : UserMenuAction()
    object SendMessage : UserMenuAction()
    object Call : UserMenuAction()
    object VideoCall : UserMenuAction()
    data class Block(val isBlocked: Boolean) : UserMenuAction()
    data class Mute(val isMuted: Boolean) : UserMenuAction()
    object Report : UserMenuAction()
    object CopyUsername : UserMenuAction()
    object AddToContacts : UserMenuAction()
    data class Follow(val isFollowing: Boolean) : UserMenuAction()
    object ShareProfile : UserMenuAction()
    object ClearChat : UserMenuAction()
    object DeleteChat : UserMenuAction()
}

/**
 * Bottom Sheet с меню профиля пользователя
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileMenuSheet(
    user: UserMenuData,
    onDismiss: () -> Unit,
    onAction: (UserMenuAction) -> Unit,
    showChatOptions: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // User Header
            UserProfileHeader(user = user)

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Actions
            QuickActionButtons(
                user = user,
                onAction = onAction
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            // Menu Items
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                // Profile actions
                item {
                    MenuSection(title = "Profile")
                }

                item {
                    MenuItemRow(
                        icon = Icons.Outlined.Person,
                        title = "View Profile",
                        subtitle = "See full profile information",
                        onClick = { onAction(UserMenuAction.ViewProfile) }
                    )
                }

                item {
                    MenuItemRow(
                        icon = Icons.Outlined.ContentCopy,
                        title = "Copy Username",
                        subtitle = "@${user.username}",
                        onClick = { onAction(UserMenuAction.CopyUsername) }
                    )
                }

                item {
                    MenuItemRow(
                        icon = Icons.Outlined.Share,
                        title = "Share Profile",
                        subtitle = "Share link to this profile",
                        onClick = { onAction(UserMenuAction.ShareProfile) }
                    )
                }

                if (!user.isFriend) {
                    item {
                        MenuItemRow(
                            icon = Icons.Outlined.PersonAdd,
                            title = "Add to Contacts",
                            subtitle = "Add to your contact list",
                            onClick = { onAction(UserMenuAction.AddToContacts) }
                        )
                    }
                }

                item {
                    MenuItemRow(
                        icon = if (user.isFollowing) Icons.Filled.PersonRemove else Icons.Outlined.PersonAdd,
                        title = if (user.isFollowing) "Unfollow" else "Follow",
                        subtitle = if (user.isFollowing) "Stop following this user" else "Follow this user",
                        onClick = { onAction(UserMenuAction.Follow(user.isFollowing)) }
                    )
                }

                // Privacy & Safety
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    MenuSection(title = "Privacy & Safety")
                }

                item {
                    MenuItemRow(
                        icon = if (user.isMuted) Icons.Filled.VolumeOff else Icons.Outlined.VolumeOff,
                        title = if (user.isMuted) "Unmute" else "Mute",
                        subtitle = if (user.isMuted) "Enable notifications" else "Disable notifications",
                        iconTint = if (user.isMuted) MaterialTheme.colorScheme.tertiary else null,
                        onClick = { onAction(UserMenuAction.Mute(user.isMuted)) }
                    )
                }

                item {
                    MenuItemRow(
                        icon = if (user.isBlocked) Icons.Filled.Block else Icons.Outlined.Block,
                        title = if (user.isBlocked) "Unblock" else "Block",
                        subtitle = if (user.isBlocked) "Allow messages from this user" else "Block all messages",
                        iconTint = if (user.isBlocked) Color(0xFFFF4444) else null,
                        titleColor = if (user.isBlocked) Color(0xFFFF4444) else null,
                        onClick = { onAction(UserMenuAction.Block(user.isBlocked)) }
                    )
                }

                item {
                    MenuItemRow(
                        icon = Icons.Outlined.Flag,
                        title = "Report",
                        subtitle = "Report inappropriate behavior",
                        iconTint = Color(0xFFFF6B6B),
                        onClick = { onAction(UserMenuAction.Report) }
                    )
                }

                // Chat options
                if (showChatOptions) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        MenuSection(title = "Chat")
                    }

                    item {
                        MenuItemRow(
                            icon = Icons.Outlined.CleaningServices,
                            title = "Clear Chat History",
                            subtitle = "Delete all messages in this chat",
                            iconTint = Color(0xFFFFBB33),
                            onClick = { onAction(UserMenuAction.ClearChat) }
                        )
                    }

                    item {
                        MenuItemRow(
                            icon = Icons.Outlined.Delete,
                            title = "Delete Chat",
                            subtitle = "Remove this conversation",
                            iconTint = Color(0xFFFF4444),
                            titleColor = Color(0xFFFF4444),
                            onClick = { onAction(UserMenuAction.DeleteChat) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun UserProfileHeader(
    user: UserMenuData,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar with status indicator
        Box {
            // Gradient ring for verified users
            if (user.isVerified) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF00D4FF),
                                    Color(0xFF0A84FF),
                                    Color(0xFF00D4FF)
                                )
                            )
                        )
                        .padding(3.dp)
                ) {
                    AvatarImage(
                        avatarUrl = user.avatar,
                        name = user.name ?: user.username,
                        size = 82
                    )
                }
            } else {
                AvatarImage(
                    avatarUrl = user.avatar,
                    name = user.name ?: user.username,
                    size = 84
                )
            }

            // Online indicator
            if (user.isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF00C851))
                    )
                }
            }

            // Verified badge
            if (user.isVerified) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp),
                    shape = CircleShape,
                    color = Color(0xFF0A84FF),
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Verified",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Name
        Text(
            text = user.name ?: user.username,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        // Username
        Text(
            text = "@${user.username}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )

        // Status/Last seen
        if (user.isOnline) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00C851))
                )
                Text(
                    text = "Online",
                    fontSize = 13.sp,
                    color = Color(0xFF00C851),
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (user.lastSeen != null) {
            Text(
                text = "Last seen ${user.lastSeen}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Karma & Trust badges
        if (user.karmaScore != null || user.trustLevel != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                user.karmaScore?.let { score ->
                    KarmaBadge(score = score)
                }
                user.trustLevel?.let { level ->
                    TrustBadge(trustLevel = level)
                }
            }
        }
    }
}

@Composable
private fun AvatarImage(
    avatarUrl: String?,
    name: String,
    size: Int
) {
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl.toFullMediaUrl(),
            contentDescription = name,
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
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(2).uppercase(),
                fontSize = (size / 2.5).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun QuickActionButtons(
    user: UserMenuData,
    onAction: (UserMenuAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(
            icon = Icons.Outlined.Message,
            label = "Message",
            onClick = { onAction(UserMenuAction.SendMessage) }
        )
        QuickActionButton(
            icon = Icons.Outlined.Call,
            label = "Call",
            onClick = { onAction(UserMenuAction.Call) }
        )
        QuickActionButton(
            icon = Icons.Outlined.Videocam,
            label = "Video",
            onClick = { onAction(UserMenuAction.VideoCall) }
        )
        QuickActionButton(
            icon = Icons.Outlined.Person,
            label = "Profile",
            onClick = { onAction(UserMenuAction.ViewProfile) }
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MenuSection(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color? = null,
    titleColor: Color? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = (iconTint ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.1f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = titleColor ?: MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun KarmaBadge(score: Float) {
    val scoreColor = when {
        score >= 4.0f -> Color(0xFF00C851)
        score >= 3.0f -> Color(0xFF00BCD4)
        score >= 2.0f -> Color(0xFFFFBB33)
        else -> Color(0xFFFF4444)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scoreColor.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = scoreColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = String.format("%.1f", score),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
        }
    }
}

@Composable
private fun TrustBadge(trustLevel: String) {
    val (color, icon, label) = when (trustLevel.lowercase()) {
        "verified" -> Triple(Color(0xFF00C851), Icons.Default.Verified, "Verified")
        "trusted" -> Triple(Color(0xFF0A84FF), Icons.Default.ThumbUp, "Trusted")
        "neutral" -> Triple(Color(0xFF8E8E93), Icons.Default.Person, "Neutral")
        "untrusted" -> Triple(Color(0xFFFF4444), Icons.Default.Warning, "Untrusted")
        else -> Triple(Color(0xFF8E8E93), Icons.Default.Person, trustLevel)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

/**
 * Confirmation dialog for dangerous actions
 */
@Composable
fun UserActionConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isDestructive) Color(0xFFFF4444) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = confirmText,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = cancelText)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Report user dialog with reason selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportUserDialog(
    userName: String,
    onReport: (reason: String, details: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }

    val reportReasons = listOf(
        "Spam" to "Sending unwanted messages or content",
        "Harassment" to "Bullying, threats, or abuse",
        "Inappropriate Content" to "Offensive or explicit material",
        "Impersonation" to "Pretending to be someone else",
        "Scam or Fraud" to "Attempting to deceive or defraud",
        "Other" to "Something else not listed above"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Report @$userName",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Why are you reporting this user?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                reportReasons.forEach { (reason, description) ->
                    Surface(
                        onClick = { selectedReason = reason },
                        color = if (selectedReason == reason)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = reason,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                if (selectedReason != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("Additional details (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedReason?.let { onReport(it, details) }
                },
                enabled = selectedReason != null,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFFF4444)
                )
            ) {
                Text(
                    text = "Report",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
