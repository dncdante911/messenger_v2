package com.worldmates.messenger.ui.groups.components

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.*
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "GroupAdvancedSettings"

// ==================== SLOW MODE SETTINGS ====================

/**
 * Налаштування Slow Mode для групи
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlowModeSettingsPanel(
    currentSeconds: Int,
    onSecondsChange: (Int) -> Unit,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    val slowModeOptions = listOf(
        0 to stringResource(R.string.slow_mode_disabled_label),
        10 to stringResource(R.string.slow_mode_10s),
        30 to stringResource(R.string.slow_mode_30s),
        60 to stringResource(R.string.slow_mode_1m),
        300 to stringResource(R.string.slow_mode_5m),
        900 to stringResource(R.string.slow_mode_15m),
        3600 to stringResource(R.string.slow_mode_1h)
    )

    var expanded by remember { mutableStateOf(false) }
    val selectedOption = slowModeOptions.find { it.first == currentSeconds } ?: (currentSeconds to "${currentSeconds}с")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.slow_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.slow_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isAdmin) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedOption.second,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        slowModeOptions.forEach { (seconds, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onSecondsChange(seconds)
                                    expanded = false
                                },
                                leadingIcon = {
                                    if (seconds == currentSeconds) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = if (currentSeconds > 0) {
                        stringResource(R.string.slow_mode_delay_format, selectedOption.second)
                    } else {
                        stringResource(R.string.slow_mode_off)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (currentSeconds > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.slow_mode_hint_format, selectedOption.second),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ==================== ANTI-SPAM SETTINGS ====================

/**
 * Налаштування анти-спаму для групи
 */
@Composable
fun AntiSpamSettingsPanel(
    settings: GroupSettings,
    onSettingsChange: (GroupSettings) -> Unit,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.anti_spam),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.anti_spam_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isAdmin) {
                    Switch(
                        checked = settings.antiSpamEnabled,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(antiSpamEnabled = it))
                        }
                    )
                }
            }

            AnimatedVisibility(visible = settings.antiSpamEnabled && isAdmin) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Максимум повідомлень на хвилину
                    AntiSpamSlider(
                        label = stringResource(R.string.messages_per_minute),
                        value = settings.maxMessagesPerMinute.toFloat(),
                        onValueChange = {
                            onSettingsChange(settings.copy(maxMessagesPerMinute = it.toInt()))
                        },
                        valueRange = 5f..60f,
                        steps = 10,
                        valueLabel = "${settings.maxMessagesPerMinute}"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Автоматичне замутення
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.auto_mute_spammers),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.auto_mute_spammers_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoMuteSpammers,
                            onCheckedChange = {
                                onSettingsChange(settings.copy(autoMuteSpammers = it))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Обмеження для нових користувачів
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.restrict_new_members),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.restrict_new_members_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.blockNewUsersMedia,
                            onCheckedChange = {
                                onSettingsChange(settings.copy(blockNewUsersMedia = it))
                            }
                        )
                    }

                    if (settings.blockNewUsersMedia) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AntiSpamSlider(
                            label = stringResource(R.string.restriction_period),
                            value = settings.newUserRestrictionHours.toFloat(),
                            onValueChange = {
                                onSettingsChange(settings.copy(newUserRestrictionHours = it.toInt()))
                            },
                            valueRange = 1f..72f,
                            steps = 7,
                            valueLabel = stringResource(R.string.restriction_hours_format, settings.newUserRestrictionHours)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AntiSpamSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ==================== PRIVACY SETTINGS (PUBLIC/PRIVATE) ====================

/**
 * Налаштування приватності групи (публічна/приватна)
 */
@Composable
fun GroupPrivacySettingsPanel(
    isPrivate: Boolean,
    onPrivacyChange: (Boolean) -> Unit,
    settings: GroupSettings,
    onSettingsChange: (GroupSettings) -> Unit,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
                    contentDescription = null,
                    tint = if (isPrivate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPrivate) stringResource(R.string.private_group_label) else stringResource(R.string.public_group_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isPrivate)
                            stringResource(R.string.join_by_request)
                        else
                            stringResource(R.string.anyone_can_join),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isAdmin) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Public option
                    FilterChip(
                        selected = !isPrivate,
                        onClick = { onPrivacyChange(false) },
                        label = { Text(stringResource(R.string.public_group)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Private option
                    FilterChip(
                        selected = isPrivate,
                        onClick = { onPrivacyChange(true) },
                        label = { Text(stringResource(R.string.private_group)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Налаштування видимості історії
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.history_for_new_members),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.show_history),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.show_history_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.historyVisibleForNewMembers,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(historyVisibleForNewMembers = it))
                        }
                    )
                }

                AnimatedVisibility(visible = settings.historyVisibleForNewMembers) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = stringResource(R.string.history_messages_count_format, settings.historyMessagesCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = settings.historyMessagesCount.toFloat(),
                            onValueChange = {
                                onSettingsChange(settings.copy(historyMessagesCount = it.toInt()))
                            },
                            valueRange = 10f..500f,
                            steps = 9
                        )
                    }
                }
            }
        }
    }
}

// ==================== MEMBER PERMISSIONS SETTINGS ====================

/**
 * Налаштування прав учасників групи
 */
@Composable
fun MemberPermissionsPanel(
    settings: GroupSettings,
    onSettingsChange: (GroupSettings) -> Unit,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.member_permissions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Права на контент
            Text(
                text = stringResource(R.string.content_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionToggle(
                icon = Icons.Default.Image,
                title = stringResource(R.string.media),
                description = stringResource(R.string.media_types_desc),
                enabled = settings.allowMembersSendMedia,
                onToggle = { onSettingsChange(settings.copy(allowMembersSendMedia = it)) },
                isAdmin = isAdmin
            )

            PermissionToggle(
                icon = Icons.Default.EmojiEmotions,
                title = stringResource(R.string.stickers_label),
                description = stringResource(R.string.stickers_emoji_desc),
                enabled = settings.allowMembersSendStickers,
                onToggle = { onSettingsChange(settings.copy(allowMembersSendStickers = it)) },
                isAdmin = isAdmin
            )

            PermissionToggle(
                icon = Icons.Default.Gif,
                title = "GIF",
                description = stringResource(R.string.gif_desc),
                enabled = settings.allowMembersSendGifs,
                onToggle = { onSettingsChange(settings.copy(allowMembersSendGifs = it)) },
                isAdmin = isAdmin
            )

            PermissionToggle(
                icon = Icons.Default.Link,
                title = stringResource(R.string.links),
                description = stringResource(R.string.links_url_desc),
                enabled = settings.allowMembersSendLinks,
                onToggle = { onSettingsChange(settings.copy(allowMembersSendLinks = it)) },
                isAdmin = isAdmin
            )

            PermissionToggle(
                icon = Icons.Default.Poll,
                title = stringResource(R.string.polls),
                description = stringResource(R.string.polls_create_desc),
                enabled = settings.allowMembersSendPolls,
                onToggle = { onSettingsChange(settings.copy(allowMembersSendPolls = it)) },
                isAdmin = isAdmin
            )

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Права на управління
            Text(
                text = stringResource(R.string.management_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionToggle(
                icon = Icons.Default.PersonAdd,
                title = stringResource(R.string.invites),
                description = stringResource(R.string.invite_members_desc),
                enabled = settings.allowMembersInvite,
                onToggle = { onSettingsChange(settings.copy(allowMembersInvite = it)) },
                isAdmin = isAdmin
            )

            PermissionToggle(
                icon = Icons.Default.PushPin,
                title = stringResource(R.string.pinning),
                description = stringResource(R.string.pinning_desc),
                enabled = settings.allowMembersPin,
                onToggle = { onSettingsChange(settings.copy(allowMembersPin = it)) },
                isAdmin = isAdmin
            )

            PermissionToggle(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.deleting),
                description = stringResource(R.string.deleting_desc),
                enabled = settings.allowMembersDeleteMessages,
                onToggle = { onSettingsChange(settings.copy(allowMembersDeleteMessages = it)) },
                isAdmin = isAdmin
            )
        }
    }
}

@Composable
private fun PermissionToggle(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isAdmin: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isAdmin) {
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        } else {
            Icon(
                imageVector = if (enabled) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==================== JOIN REQUESTS PANEL ====================

/**
 * Панель запитів на вступ до приватної групи
 */
@Composable
fun JoinRequestsPanel(
    requests: List<GroupJoinRequest>,
    onApprove: (GroupJoinRequest) -> Unit,
    onReject: (GroupJoinRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.join_requests),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (requests.isNotEmpty()) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Text(requests.size.toString())
                    }
                }
            }

            if (requests.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_new_requests),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                requests.take(5).forEach { request ->
                    JoinRequestItem(
                        request = request,
                        onApprove = { onApprove(request) },
                        onReject = { onReject(request) }
                    )
                    if (request != requests.last()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                if (requests.size > 5) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.and_more_requests, requests.size - 5),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun JoinRequestItem(
    request: GroupJoinRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Avatar
        AsyncImage(
            model = request.userAvatar ?: "",
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.username,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (!request.message.isNullOrEmpty()) {
                Text(
                    text = request.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatTime(request.createdTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // Actions
        IconButton(
            onClick = onReject,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.reject_request_cd))
        }

        IconButton(
            onClick = onApprove,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.approve_request_cd))
        }
    }
}

// ==================== ROLES MANAGEMENT ====================

/**
 * Управління ролями учасників групи
 */
@Composable
fun RolesManagementPanel(
    members: List<GroupMember>,
    onRoleChange: (Long, String) -> Unit,
    currentUserId: Long,
    isOwner: Boolean,
    modifier: Modifier = Modifier
) {
    var selectedMember by remember { mutableStateOf<GroupMember?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.member_roles),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Admins
            val admins = members.filter { it.role == "admin" }
            if (admins.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.admins_count, admins.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                admins.forEach { member ->
                    MemberRoleItem(
                        member = member,
                        onClick = { if (isOwner && member.userId != currentUserId) selectedMember = member },
                        isClickable = isOwner && member.userId != currentUserId
                    )
                }
            }

            // Moderators
            val moderators = members.filter { it.role == "moderator" }
            if (moderators.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.moderators_count, moderators.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                moderators.forEach { member ->
                    MemberRoleItem(
                        member = member,
                        onClick = { if (isOwner) selectedMember = member },
                        isClickable = isOwner
                    )
                }
            }

            // Regular members (show only few)
            val regularMembers = members.filter { it.role == "member" }.take(5)
            if (regularMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.members_with_count, members.count { it.role == "member" }),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                regularMembers.forEach { member ->
                    MemberRoleItem(
                        member = member,
                        onClick = { if (isOwner) selectedMember = member },
                        isClickable = isOwner
                    )
                }
            }
        }
    }

    // Role change dialog
    selectedMember?.let { member ->
        RoleChangeDialog(
            member = member,
            onDismiss = { selectedMember = null },
            onRoleSelected = { role ->
                onRoleChange(member.userId, role)
                selectedMember = null
            }
        )
    }
}

@Composable
private fun MemberRoleItem(
    member: GroupMember,
    onClick: () -> Unit,
    isClickable: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp)
    ) {
        AsyncImage(
            model = member.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = member.username,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        // Role badge
        Surface(
            color = when (member.role) {
                "admin" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                "moderator" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = when (member.role) {
                    "admin" -> stringResource(R.string.role_admin_short)
                    "moderator" -> stringResource(R.string.role_moderator_short)
                    else -> stringResource(R.string.role_member_short)
                },
                style = MaterialTheme.typography.labelSmall,
                color = when (member.role) {
                    "admin" -> MaterialTheme.colorScheme.primary
                    "moderator" -> Color(0xFF4CAF50)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        if (isClickable) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RoleChangeDialog(
    member: GroupMember,
    onDismiss: () -> Unit,
    onRoleSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_role)) },
        text = {
            Column {
                Text(stringResource(R.string.select_role_for_format, member.username))
                Spacer(modifier = Modifier.height(16.dp))

                listOf(
                    "admin" to stringResource(R.string.role_admin),
                    "moderator" to stringResource(R.string.role_moderator),
                    "member" to stringResource(R.string.role_member)
                ).forEach { (role, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRoleSelected(role) }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(
                            selected = member.role == role,
                            onClick = { onRoleSelected(role) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ==================== GROUP INFO FOR USERS ====================

/**
 * Інформація про групу для звичайних користувачів
 */
@Composable
fun GroupInfoPanel(
    group: Group,
    statistics: GroupStatistics?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Group type badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = if (group.isPrivate)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (group.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                            contentDescription = null,
                            tint = if (group.isPrivate)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (group.isPrivate) stringResource(R.string.private_group_label) else stringResource(R.string.public_group_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (group.isPrivate)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.Group,
                    value = group.membersCount.toString(),
                    label = stringResource(R.string.members_count_label)
                )
                StatItem(
                    icon = Icons.Default.Message,
                    value = statistics?.messagesCount?.toString() ?: "—",
                    label = stringResource(R.string.messages_count_label)
                )
                StatItem(
                    icon = Icons.Default.TrendingUp,
                    value = statistics?.activeMembers24h?.toString() ?: "—",
                    label = stringResource(R.string.stat_active_label)
                )
            }

            // Description
            if (!group.description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Created date
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.group_created_date, formatDate(group.createdTime)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
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

// ==================== UTILITY FUNCTIONS ====================

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
