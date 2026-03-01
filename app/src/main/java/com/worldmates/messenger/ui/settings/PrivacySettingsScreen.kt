package com.worldmates.messenger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val userData by viewModel.userData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val colorScheme = MaterialTheme.colorScheme

    var followPrivacy by remember { mutableStateOf("everyone") }
    var friendPrivacy by remember { mutableStateOf("everyone") }
    var postPrivacy by remember { mutableStateOf("everyone") }
    var messagePrivacy by remember { mutableStateOf("everyone") }
    var confirmFollowers by remember { mutableStateOf("0") }
    var showActivitiesPrivacy by remember { mutableStateOf("1") }
    var birthPrivacy by remember { mutableStateOf("everyone") }
    var visitPrivacy by remember { mutableStateOf("everyone") }

    var showPrivacyDialog by remember { mutableStateOf<PrivacyType?>(null) }

    LaunchedEffect(userData) {
        userData?.let { user ->
            followPrivacy = user.followPrivacy ?: "everyone"
            friendPrivacy = user.friendPrivacy ?: "everyone"
            postPrivacy = user.postPrivacy ?: "everyone"
            messagePrivacy = user.messagePrivacy ?: "everyone"
            confirmFollowers = user.confirmFollowers ?: "0"
            showActivitiesPrivacy = user.showActivitiesPrivacy ?: "1"
            birthPrivacy = user.birthPrivacy ?: "everyone"
            visitPrivacy = user.visitPrivacy ?: "everyone"
        }
    }

    val labelEveryone = stringResource(R.string.privacy_everyone)
    val labelFriends = stringResource(R.string.privacy_friends)
    val labelFollowers = stringResource(R.string.privacy_followers)
    val labelOnlyMe = stringResource(R.string.privacy_only_me)

    fun privacyLabel(value: String) = when (value) {
        "everyone" -> labelEveryone
        "friends" -> labelFriends
        "followers" -> labelFollowers
        "me" -> labelOnlyMe
        else -> labelEveryone
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.privacy_title), fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (!isLoading) {
                    TextButton(
                        onClick = {
                            viewModel.updatePrivacySettings(
                                followPrivacy = followPrivacy,
                                friendPrivacy = friendPrivacy,
                                postPrivacy = postPrivacy,
                                messagePrivacy = messagePrivacy,
                                confirmFollowers = confirmFollowers,
                                showActivitiesPrivacy = showActivitiesPrivacy,
                                birthPrivacy = birthPrivacy,
                                visitPrivacy = visitPrivacy
                            )
                        }
                    ) {
                        Text(
                            stringResource(R.string.save),
                            color = colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.primary,
                titleContentColor = colorScheme.onPrimary,
                navigationIconContentColor = colorScheme.onPrimary
            )
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (errorMessage != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.privacy_description),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Messenger-specific settings first
                item {
                    PrivacySettingItem(
                        icon = Icons.Default.Message,
                        title = stringResource(R.string.who_can_message),
                        value = privacyLabel(messagePrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.MESSAGE },
                        tintColor = colorScheme.primary,
                        surfaceColor = colorScheme.surface,
                        textColor = colorScheme.onSurface,
                        subtextColor = colorScheme.onSurfaceVariant
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Visibility,
                        title = stringResource(R.string.show_activity),
                        description = stringResource(R.string.show_activity_desc),
                        checked = showActivitiesPrivacy == "1",
                        onCheckedChange = { showActivitiesPrivacy = if (it) "1" else "0" },
                        tintColor = colorScheme.primary,
                        surfaceColor = colorScheme.surface,
                        textColor = colorScheme.onSurface,
                        subtextColor = colorScheme.onSurfaceVariant
                    )
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                // Social/Profile settings
                item {
                    PrivacySettingItem(
                        icon = Icons.Default.PersonAdd,
                        title = stringResource(R.string.who_can_follow),
                        value = privacyLabel(followPrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.FOLLOW },
                        tintColor = colorScheme.primary,
                        surfaceColor = colorScheme.surface,
                        textColor = colorScheme.onSurface,
                        subtextColor = colorScheme.onSurfaceVariant
                    )
                }

                item {
                    PrivacySettingItem(
                        icon = Icons.Default.Group,
                        title = stringResource(R.string.who_can_add_friend),
                        value = privacyLabel(friendPrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.FRIEND },
                        tintColor = colorScheme.primary,
                        surfaceColor = colorScheme.surface,
                        textColor = colorScheme.onSurface,
                        subtextColor = colorScheme.onSurfaceVariant
                    )
                }

                item {
                    PrivacySettingItem(
                        icon = Icons.Default.Article,
                        title = stringResource(R.string.who_sees_posts),
                        value = privacyLabel(postPrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.POST },
                        tintColor = colorScheme.primary,
                        surfaceColor = colorScheme.surface,
                        textColor = colorScheme.onSurface,
                        subtextColor = colorScheme.onSurfaceVariant
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.CheckCircle,
                        title = stringResource(R.string.confirm_followers),
                        description = stringResource(R.string.confirm_followers_desc),
                        checked = confirmFollowers == "1",
                        onCheckedChange = { confirmFollowers = if (it) "1" else "0" },
                        tintColor = colorScheme.primary,
                        surfaceColor = colorScheme.surface,
                        textColor = colorScheme.onSurface,
                        subtextColor = colorScheme.onSurfaceVariant
                    )
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                item {
                    PrivacySettingItem(
                        icon = Icons.Default.Cake,
                        title = stringResource(R.string.who_sees_birthday),
                        value = privacyLabel(birthPrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.BIRTH },
                        tintColor = colorScheme.primary,
                        surfaceColor = colorScheme.surface,
                        textColor = colorScheme.onSurface,
                        subtextColor = colorScheme.onSurfaceVariant
                    )
                }

                item {
                    PrivacySettingItem(
                        icon = Icons.Default.RemoveRedEye,
                        title = stringResource(R.string.who_sees_profile_visits),
                        value = privacyLabel(visitPrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.VISIT },
                        tintColor = colorScheme.primary,
                        surfaceColor = colorScheme.surface,
                        textColor = colorScheme.onSurface,
                        subtextColor = colorScheme.onSurfaceVariant
                    )
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }

    showPrivacyDialog?.let { type ->
        val dialogTitle = when (type) {
            PrivacyType.FOLLOW -> stringResource(R.string.privacy_who_can_follow_short)
            PrivacyType.FRIEND -> stringResource(R.string.privacy_who_can_add_friend_short)
            PrivacyType.POST -> stringResource(R.string.privacy_who_sees_posts_short)
            PrivacyType.MESSAGE -> stringResource(R.string.privacy_who_can_write_short)
            PrivacyType.BIRTH -> stringResource(R.string.privacy_who_sees_birthday_short)
            PrivacyType.VISIT -> stringResource(R.string.privacy_who_sees_visits_short)
        }
        val privacyOptions = listOf(
            "everyone" to labelEveryone,
            "friends" to labelFriends,
            "followers" to labelFollowers,
            "me" to labelOnlyMe
        )
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = null },
            title = { Text(dialogTitle) },
            text = {
                Column {
                    privacyOptions.forEach { (value, label) ->
                        val currentValue = when (type) {
                            PrivacyType.FOLLOW -> followPrivacy
                            PrivacyType.FRIEND -> friendPrivacy
                            PrivacyType.POST -> postPrivacy
                            PrivacyType.MESSAGE -> messagePrivacy
                            PrivacyType.BIRTH -> birthPrivacy
                            PrivacyType.VISIT -> visitPrivacy
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    when (type) {
                                        PrivacyType.FOLLOW -> followPrivacy = value
                                        PrivacyType.FRIEND -> friendPrivacy = value
                                        PrivacyType.POST -> postPrivacy = value
                                        PrivacyType.MESSAGE -> messagePrivacy = value
                                        PrivacyType.BIRTH -> birthPrivacy = value
                                        PrivacyType.VISIT -> visitPrivacy = value
                                    }
                                    showPrivacyDialog = null
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentValue == value,
                                onClick = {
                                    when (type) {
                                        PrivacyType.FOLLOW -> followPrivacy = value
                                        PrivacyType.FRIEND -> friendPrivacy = value
                                        PrivacyType.POST -> postPrivacy = value
                                        PrivacyType.MESSAGE -> messagePrivacy = value
                                        PrivacyType.BIRTH -> birthPrivacy = value
                                        PrivacyType.VISIT -> visitPrivacy = value
                                    }
                                    showPrivacyDialog = null
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

enum class PrivacyType {
    FOLLOW, FRIEND, POST, MESSAGE, BIRTH, VISIT
}

@Composable
fun PrivacySettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    tintColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    surfaceColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    subtextColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = surfaceColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tintColor,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            ) {
                Text(text = title, fontSize = 16.sp, color = textColor)
                Text(
                    text = value,
                    fontSize = 13.sp,
                    color = subtextColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = stringResource(R.string.change),
                tint = subtextColor
            )
        }
    }
}

@Composable
fun SwitchSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    tintColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    surfaceColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    subtextColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val alpha = if (enabled) 1f else 0.38f
    Surface(modifier = Modifier.fillMaxWidth(), color = surfaceColor) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tintColor.copy(alpha = alpha),
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            ) {
                Text(text = title, fontSize = 16.sp, color = textColor.copy(alpha = alpha))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = subtextColor.copy(alpha = alpha),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
