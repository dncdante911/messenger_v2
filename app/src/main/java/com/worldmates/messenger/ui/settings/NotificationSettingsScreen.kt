package com.worldmates.messenger.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val userData by viewModel.userData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

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
                        Text(stringResource(R.string.save), color = Color.White)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF0084FF),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color.Red,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.notif_section_general),
                        fontSize = 14.sp,
                        color = Color(0xFF0084FF),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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

                item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }

                item {
                    Text(
                        text = stringResource(R.string.notif_section_social),
                        fontSize = 14.sp,
                        color = Color(0xFF0084FF),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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

                item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }

                item {
                    Text(
                        text = stringResource(R.string.notif_section_pages),
                        fontSize = 14.sp,
                        color = Color(0xFF0084FF),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
