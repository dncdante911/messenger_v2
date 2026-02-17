package com.worldmates.messenger.ui.groups.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.worldmates.messenger.util.toFullMediaUrl

// ==================== DATA MODELS ====================

/**
 * Subgroup data model
 */
data class Subgroup(
    val id: Long,
    val parentGroupId: Long,
    val name: String,
    val description: String? = null,
    val avatarUrl: String = "",
    val membersCount: Int = 0,
    val messagesCount: Int = 0,
    val isPrivate: Boolean = false,
    val createdAt: String = "",
    val color: String = "#0088CC" // Default color for subgroup badge
)

/**
 * Subgroup member with additional permissions
 */
data class SubgroupMember(
    val userId: Long,
    val username: String,
    val name: String? = null,
    val avatarUrl: String = "",
    val role: String = "member", // member, moderator, admin
    val canPost: Boolean = true,
    val canInvite: Boolean = false
)

// ==================== SUBGROUPS SECTION ====================

/**
 * Section displaying subgroups within a group (like Telegram topics)
 */
@Composable
fun SubgroupsSection(
    subgroups: List<Subgroup>,
    canCreateSubgroup: Boolean,
    onSubgroupClick: (Subgroup) -> Unit,
    onCreateSubgroupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val cardBg = if (isDarkTheme) GroupColors.CardDark else GroupColors.CardLight
    val textPrimary = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) GroupColors.TextSecondaryDark else GroupColors.TextSecondaryLight

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cardBg
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        tint = GroupColors.TelegramBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Topics",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    if (subgroups.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GroupColors.TelegramBlue.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = subgroups.size.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GroupColors.TelegramBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (canCreateSubgroup) {
                    TextButton(
                        onClick = onCreateSubgroupClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = GroupColors.TelegramBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Topic",
                            fontSize = 13.sp,
                            color = GroupColors.TelegramBlue
                        )
                    }
                }
            }

            HorizontalDivider(
                color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            )

            // Subgroups list or empty state
            if (subgroups.isEmpty()) {
                EmptySubgroupsState(
                    canCreate = canCreateSubgroup,
                    onCreate = onCreateSubgroupClick,
                    isDarkTheme = isDarkTheme
                )
            } else {
                subgroups.forEach { subgroup ->
                    SubgroupCard(
                        subgroup = subgroup,
                        onClick = { onSubgroupClick(subgroup) },
                        isDarkTheme = isDarkTheme
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
                    )
                }
            }
        }
    }
}

// ==================== SUBGROUP CARD ====================

@Composable
fun SubgroupCard(
    subgroup: Subgroup,
    onClick: () -> Unit,
    isDarkTheme: Boolean
) {
    val textPrimary = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) GroupColors.TextSecondaryDark else GroupColors.TextSecondaryLight

    // Parse color from hex string
    val subgroupColor = try {
        Color(android.graphics.Color.parseColor(subgroup.color))
    } catch (e: Exception) {
        GroupColors.TelegramBlue
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Topic icon/avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(subgroupColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (subgroup.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = subgroup.avatarUrl.toFullMediaUrl(),
                        contentDescription = subgroup.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Tag,
                        contentDescription = null,
                        tint = subgroupColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Topic info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subgroup.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subgroup.isPrivate) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Private",
                            tint = textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.People,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${subgroup.membersCount}",
                        fontSize = 13.sp,
                        color = textSecondary
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${subgroup.messagesCount}",
                        fontSize = 13.sp,
                        color = textSecondary
                    )
                }
            }

            // Arrow
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ==================== EMPTY SUBGROUPS STATE ====================

@Composable
fun EmptySubgroupsState(
    canCreate: Boolean,
    onCreate: () -> Unit,
    isDarkTheme: Boolean
) {
    val textPrimary = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) GroupColors.TextSecondaryDark else GroupColors.TextSecondaryLight

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(GroupColors.TelegramBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Forum,
                contentDescription = null,
                tint = GroupColors.TelegramBlue,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No topics yet",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Topics help organize discussions\ninto separate threads",
            fontSize = 14.sp,
            color = textSecondary,
            textAlign = TextAlign.Center
        )

        if (canCreate) {
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onCreate,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GroupColors.TelegramBlue
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Topic")
            }
        }
    }
}

// ==================== CREATE SUBGROUP DIALOG ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSubgroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?, isPrivate: Boolean, color: String) -> Unit,
    isLoading: Boolean = false
) {
    val isDarkTheme = isSystemInDarkTheme()
    val cardBg = if (isDarkTheme) GroupColors.CardDark else GroupColors.CardLight
    val textPrimary = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) GroupColors.TextSecondaryDark else GroupColors.TextSecondaryLight

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var selectedColorIndex by remember { mutableIntStateOf(0) }

    // Available colors for topics
    val topicColors = listOf(
        "#0088CC", // Blue
        "#00C853", // Green
        "#FF5722", // Orange
        "#E91E63", // Pink
        "#9C27B0", // Purple
        "#3F51B5", // Indigo
        "#009688", // Teal
        "#FF9800"  // Amber
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Create Topic",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Topic preview
                val selectedColor = try {
                    Color(android.graphics.Color.parseColor(topicColors[selectedColorIndex]))
                } catch (e: Exception) {
                    GroupColors.TelegramBlue
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(selectedColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Tag,
                        contentDescription = null,
                        tint = selectedColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Topic name") },
                    placeholder = { Text("e.g., General Discussion") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("What's this topic about?") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Color picker
                Text(
                    text = "Topic color",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    topicColors.forEachIndexed { index, colorHex ->
                        val color = try {
                            Color(android.graphics.Color.parseColor(colorHex))
                        } catch (e: Exception) {
                            GroupColors.TelegramBlue
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColorIndex == index) 3.dp else 0.dp,
                                    color = if (selectedColorIndex == index) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColorIndex == index) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Private toggle
                Surface(
                    onClick = { isPrivate = !isPrivate },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDarkTheme) Color(0xFF3A3A3A) else Color(0xFFF5F5F5)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isPrivate) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isPrivate) GroupColors.TelegramBlue else textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Private topic",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary
                            )
                            Text(
                                text = "Only selected members can access",
                                fontSize = 13.sp,
                                color = textSecondary
                            )
                        }
                        Switch(
                            checked = isPrivate,
                            onCheckedChange = { isPrivate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GroupColors.TelegramBlue
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onCreate(
                                    name.trim(),
                                    description.ifBlank { null },
                                    isPrivate,
                                    topicColors[selectedColorIndex]
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = name.isNotBlank() && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GroupColors.TelegramBlue
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }
}

// ==================== SUBGROUP DETAILS HEADER ====================

@Composable
fun SubgroupDetailsHeader(
    subgroup: Subgroup,
    parentGroupName: String,
    membersCount: Int,
    onBackClick: () -> Unit,
    onSettingsClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val textPrimary = if (isDarkTheme) GroupColors.TextPrimaryDark else GroupColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) GroupColors.TextSecondaryDark else GroupColors.TextSecondaryLight

    val subgroupColor = try {
        Color(android.graphics.Color.parseColor(subgroup.color))
    } catch (e: Exception) {
        GroupColors.TelegramBlue
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        subgroupColor.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                )
            )
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary
                )
            }

            if (onSettingsClick != null) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = textPrimary
                    )
                }
            }
        }

        // Topic info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Topic icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(subgroupColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (subgroup.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = subgroup.avatarUrl.toFullMediaUrl(),
                        contentDescription = subgroup.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Tag,
                        contentDescription = null,
                        tint = subgroupColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Topic name
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subgroup.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )
                if (subgroup.isPrivate) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Private",
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Parent group
            Text(
                text = "in $parentGroupName",
                fontSize = 14.sp,
                color = textSecondary
            )

            if (subgroup.description != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subgroup.description,
                    fontSize = 14.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = membersCount.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = "Members",
                        fontSize = 13.sp,
                        color = textSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = subgroup.messagesCount.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = "Messages",
                        fontSize = 13.sp,
                        color = textSecondary
                    )
                }
            }
        }

        HorizontalDivider(
            color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
        )
    }
}
