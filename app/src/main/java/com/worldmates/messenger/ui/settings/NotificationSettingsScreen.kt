package com.worldmates.messenger.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserPreferencesRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val userData by viewModel.userData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember { UserPreferencesRepository(context) }

    // --- Messenger local preferences ---
    var messengerNotifEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var previewEnabled by remember { mutableStateOf(true) }
    var groupNotifEnabled by remember { mutableStateOf(true) }

    // Load local messenger preferences from DataStore
    val prefsFlow = remember { prefsRepo.userPreferencesFlow }
    val prefs by prefsFlow.collectAsState(initial = null)

    LaunchedEffect(prefs) {
        prefs?.let {
            messengerNotifEnabled = it.notificationsEnabled
            soundEnabled = it.soundEnabled
            vibrationEnabled = it.vibrationEnabled
            previewEnabled = it.previewEnabled
            groupNotifEnabled = it.groupNotificationsEnabled
        }
    }

    // --- WoWonder server-side notification settings ---
    var emailNotification by remember { mutableStateOf(true) }
    var eLiked by remember { mutableStateOf(true) }
    var eWondered by remember { mutableStateOf(true) }
    var eShared by remember { mutableStateOf(true) }
    var eFollowed by remember { mutableStateOf(true) }
    var eCommented by remember { mutableStateOf(true) }
    var eVisited by remember { mutableStateOf(true) }
    var eLikedPage by remember { mutableStateOf(true) }
    var eMentioned by remember { mutableStateOf(true) }
    var eJoinedGroup by remember { mutableStateOf(true) }
    var eAccepted by remember { mutableStateOf(true) }
    var eProfileWallPost by remember { mutableStateOf(true) }
    var eSentGift by remember { mutableStateOf(true) }

    LaunchedEffect(userData) {
        userData?.let { user ->
            emailNotification = user.emailNotification == "1"
            eLiked = user.eLiked == 1
            eWondered = user.eWondered == 1
            eShared = user.eShared == 1
            eFollowed = user.eFollowed == 1
            eCommented = user.eCommented == 1
            eVisited = user.eVisited == 1
            eLikedPage = user.eLikedPage == 1
            eMentioned = user.eMentioned == 1
            eJoinedGroup = user.eJoinedGroup == 1
            eAccepted = user.eAccepted == 1
            eProfileWallPost = user.eProfileWallPost == 1
            eSentGift = user.eSentGift == 1
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.notification_settings)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (!isLoading) {
                    TextButton(
                        onClick = {
                            // Save messenger local preferences
                            scope.launch {
                                prefsRepo.setNotificationsEnabled(messengerNotifEnabled)
                                prefsRepo.setSoundEnabled(soundEnabled)
                                prefsRepo.setVibrationEnabled(vibrationEnabled)
                                prefsRepo.setPreviewEnabled(previewEnabled)
                                prefsRepo.setGroupNotificationsEnabled(groupNotifEnabled)
                            }
                            // Save WoWonder server-side settings
                            viewModel.updateNotificationSettings(
                                emailNotification = if (emailNotification) 1 else 0,
                                eLiked = if (eLiked) 1 else 0,
                                eWondered = if (eWondered) 1 else 0,
                                eShared = if (eShared) 1 else 0,
                                eFollowed = if (eFollowed) 1 else 0,
                                eCommented = if (eCommented) 1 else 0,
                                eVisited = if (eVisited) 1 else 0,
                                eLikedPage = if (eLikedPage) 1 else 0,
                                eMentioned = if (eMentioned) 1 else 0,
                                eJoinedGroup = if (eJoinedGroup) 1 else 0,
                                eAccepted = if (eAccepted) 1 else 0,
                                eProfileWallPost = if (eProfileWallPost) 1 else 0
                            )
                        }
                    ) {
                        Text(
                            stringResource(R.string.save),
                            color = colorScheme.onPrimary
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = colorScheme.errorContainer
                            ),
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

                // ======================================================
                // MESSENGER NOTIFICATIONS (local DataStore preferences)
                // ======================================================
                item {
                    NotifSectionHeader(
                        text = stringResource(R.string.notif_section_messenger),
                        color = colorScheme.primary
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Chat,
                        title = stringResource(R.string.notif_chat_messages),
                        description = stringResource(R.string.notif_chat_messages_desc),
                        checked = messengerNotifEnabled,
                        onCheckedChange = {
                            messengerNotifEnabled = it
                            scope.launch { prefsRepo.setNotificationsEnabled(it) }
                        }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.VolumeUp,
                        title = stringResource(R.string.notif_sound),
                        description = stringResource(R.string.notif_sound_desc),
                        checked = soundEnabled,
                        enabled = messengerNotifEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            scope.launch { prefsRepo.setSoundEnabled(it) }
                        }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Vibration,
                        title = stringResource(R.string.notif_vibration),
                        description = stringResource(R.string.notif_vibration_desc),
                        checked = vibrationEnabled,
                        enabled = messengerNotifEnabled,
                        onCheckedChange = {
                            vibrationEnabled = it
                            scope.launch { prefsRepo.setVibrationEnabled(it) }
                        }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.RemoveRedEye,
                        title = stringResource(R.string.notif_preview),
                        description = stringResource(R.string.notif_preview_desc),
                        checked = previewEnabled,
                        enabled = messengerNotifEnabled,
                        onCheckedChange = {
                            previewEnabled = it
                            scope.launch { prefsRepo.setPreviewEnabled(it) }
                        }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Groups,
                        title = stringResource(R.string.notif_group_messages),
                        description = stringResource(R.string.notif_group_messages_desc),
                        checked = groupNotifEnabled,
                        enabled = messengerNotifEnabled,
                        onCheckedChange = {
                            groupNotifEnabled = it
                            scope.launch { prefsRepo.setGroupNotificationsEnabled(it) }
                        }
                    )
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                // ======================================================
                // GENERAL (email notifications — server side)
                // ======================================================
                item {
                    NotifSectionHeader(
                        text = stringResource(R.string.notif_section_general),
                        color = colorScheme.primary
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Email,
                        title = stringResource(R.string.email_notifications),
                        description = stringResource(R.string.email_notifications_desc),
                        checked = emailNotification,
                        onCheckedChange = { emailNotification = it }
                    )
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                // ======================================================
                // SOCIAL NOTIFICATIONS (server side)
                // ======================================================
                item {
                    NotifSectionHeader(
                        text = stringResource(R.string.notif_section_social),
                        color = colorScheme.primary
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.ThumbUp,
                        title = stringResource(R.string.notif_liked),
                        description = stringResource(R.string.notif_liked_desc),
                        checked = eLiked,
                        onCheckedChange = { eLiked = it }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Star,
                        title = stringResource(R.string.notif_wondered),
                        description = stringResource(R.string.notif_wondered_desc),
                        checked = eWondered,
                        onCheckedChange = { eWondered = it }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.notif_shared),
                        description = stringResource(R.string.notif_shared_desc),
                        checked = eShared,
                        onCheckedChange = { eShared = it }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.PersonAdd,
                        title = stringResource(R.string.notif_followed),
                        description = stringResource(R.string.notif_followed_desc),
                        checked = eFollowed,
                        onCheckedChange = { eFollowed = it }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Comment,
                        title = stringResource(R.string.notif_commented),
                        description = stringResource(R.string.notif_commented_desc),
                        checked = eCommented,
                        onCheckedChange = { eCommented = it }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Visibility,
                        title = stringResource(R.string.notif_visited),
                        description = stringResource(R.string.notif_visited_desc),
                        checked = eVisited,
                        onCheckedChange = { eVisited = it }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.AlternateEmail,
                        title = stringResource(R.string.notif_mentioned),
                        description = stringResource(R.string.notif_mentioned_desc),
                        checked = eMentioned,
                        onCheckedChange = { eMentioned = it }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Check,
                        title = stringResource(R.string.notif_accepted),
                        description = stringResource(R.string.notif_accepted_desc),
                        checked = eAccepted,
                        onCheckedChange = { eAccepted = it }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Article,
                        title = stringResource(R.string.notif_wall_post),
                        description = stringResource(R.string.notif_wall_post_desc),
                        checked = eProfileWallPost,
                        onCheckedChange = { eProfileWallPost = it }
                    )
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                // ======================================================
                // PAGES & GROUPS (server side)
                // ======================================================
                item {
                    NotifSectionHeader(
                        text = stringResource(R.string.notif_section_pages),
                        color = colorScheme.primary
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Pages,
                        title = stringResource(R.string.notif_liked_page),
                        description = stringResource(R.string.notif_liked_page_desc),
                        checked = eLikedPage,
                        onCheckedChange = { eLikedPage = it }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Group,
                        title = stringResource(R.string.notif_joined_group),
                        description = stringResource(R.string.notif_joined_group_desc),
                        checked = eJoinedGroup,
                        onCheckedChange = { eJoinedGroup = it }
                    )
                }

                item {
                    SwitchSettingItem(
                        icon = Icons.Default.CardGiftcard,
                        title = stringResource(R.string.notif_gift),
                        description = stringResource(R.string.notif_gift_desc),
                        checked = eSentGift,
                        onCheckedChange = { eSentGift = it }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun NotifSectionHeader(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        letterSpacing = 0.5.sp
    )
}
