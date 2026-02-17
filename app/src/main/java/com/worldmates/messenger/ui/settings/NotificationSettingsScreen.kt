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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    // Обновить локальные состояния когда userData меняется
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
            title = { Text("Сповіщення") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
                        Text("Зберегти", color = Color.White)
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
                // Error Message
                if (errorMessage != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE)
                            )
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color.Red,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Section Header: General
                item {
                    Text(
                        text = "ЗАГАЛЬНІ",
                        fontSize = 14.sp,
                        color = Color(0xFF0084FF),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Email Notification
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Email,
                        title = "Email сповіщення",
                        description = "Отримувати сповіщення на електронну пошту",
                        checked = emailNotification,
                        onCheckedChange = { emailNotification = it }
                    )
                }

                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Section Header: Social
                item {
                    Text(
                        text = "СОЦІАЛЬНІ СПОВІЩЕННЯ",
                        fontSize = 14.sp,
                        color = Color(0xFF0084FF),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Liked
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.ThumbUp,
                        title = "Вподобання",
                        description = "Коли хтось вподобав ваш пост",
                        checked = eLiked,
                        onCheckedChange = { eLiked = it }
                    )
                }

                // Wondered
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Star,
                        title = "Вау реакція",
                        description = "Коли хтось відреагував на ваш пост",
                        checked = eWondered,
                        onCheckedChange = { eWondered = it }
                    )
                }

                // Shared
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Share,
                        title = "Поділився",
                        description = "Коли хтось поділився вашим постом",
                        checked = eShared,
                        onCheckedChange = { eShared = it }
                    )
                }

                // Followed
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.PersonAdd,
                        title = "Нові підписники",
                        description = "Коли хтось підписався на вас",
                        checked = eFollowed,
                        onCheckedChange = { eFollowed = it }
                    )
                }

                // Commented
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Comment,
                        title = "Коментарі",
                        description = "Коли хтось прокоментував ваш пост",
                        checked = eCommented,
                        onCheckedChange = { eCommented = it }
                    )
                }

                // Visited
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Visibility,
                        title = "Відвідування профілю",
                        description = "Коли хтось відвідав ваш профіль",
                        checked = eVisited,
                        onCheckedChange = { eVisited = it }
                    )
                }

                // Mentioned
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.AlternateEmail,
                        title = "Згадування",
                        description = "Коли хтось згадав вас у пості",
                        checked = eMentioned,
                        onCheckedChange = { eMentioned = it }
                    )
                }

                // Accepted
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Check,
                        title = "Прийняті запити",
                        description = "Коли хтось прийняв ваш запит у друзі",
                        checked = eAccepted,
                        onCheckedChange = { eAccepted = it }
                    )
                }

                // Profile Wall Post
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Article,
                        title = "Пости на стіні",
                        description = "Коли хтось опублікував на вашій стіні",
                        checked = eProfileWallPost,
                        onCheckedChange = { eProfileWallPost = it }
                    )
                }

                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Section Header: Pages & Groups
                item {
                    Text(
                        text = "СТОРІНКИ ТА ГРУПИ",
                        fontSize = 14.sp,
                        color = Color(0xFF0084FF),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Liked Page
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Pages,
                        title = "Вподобав сторінку",
                        description = "Коли хтось вподобав вашу сторінку",
                        checked = eLikedPage,
                        onCheckedChange = { eLikedPage = it }
                    )
                }

                // Joined Group
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Group,
                        title = "Приєднання до групи",
                        description = "Коли хтось приєднався до вашої групи",
                        checked = eJoinedGroup,
                        onCheckedChange = { eJoinedGroup = it }
                    )
                }

                // Sent Gift (if applicable)
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.CardGiftcard,
                        title = "Подарунки",
                        description = "Коли хтось надіслав вам подарунок",
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
