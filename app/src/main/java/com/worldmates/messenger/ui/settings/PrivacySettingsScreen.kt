package com.worldmates.messenger.ui.settings

import androidx.compose.foundation.clickable
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
fun PrivacySettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val userData by viewModel.userData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var followPrivacy by remember { mutableStateOf("everyone") }
    var friendPrivacy by remember { mutableStateOf("everyone") }
    var postPrivacy by remember { mutableStateOf("everyone") }
    var messagePrivacy by remember { mutableStateOf("everyone") }
    var confirmFollowers by remember { mutableStateOf("0") }
    var showActivitiesPrivacy by remember { mutableStateOf("1") }
    var birthPrivacy by remember { mutableStateOf("everyone") }
    var visitPrivacy by remember { mutableStateOf("everyone") }

    var showPrivacyDialog by remember { mutableStateOf<PrivacyType?>(null) }

    // Обновить локальные состояния когда userData меняется
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Конфіденційність") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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

                // Description
                item {
                    Text(
                        text = "Налаштуйте, хто може бачити ваші дії та профіль",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Follow Privacy
                item {
                    PrivacySettingItem(
                        icon = Icons.Default.PersonAdd,
                        title = "Хто може підписатися на мене",
                        value = getPrivacyLabel(followPrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.FOLLOW }
                    )
                }

                // Friend Privacy
                item {
                    PrivacySettingItem(
                        icon = Icons.Default.Group,
                        title = "Хто може додати мене в друзі",
                        value = getPrivacyLabel(friendPrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.FRIEND }
                    )
                }

                // Post Privacy
                item {
                    PrivacySettingItem(
                        icon = Icons.Default.Article,
                        title = "Хто бачить мої пости",
                        value = getPrivacyLabel(postPrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.POST }
                    )
                }

                // Message Privacy
                item {
                    PrivacySettingItem(
                        icon = Icons.Default.Message,
                        title = "Хто може надсилати мені повідомлення",
                        value = getPrivacyLabel(messagePrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.MESSAGE }
                    )
                }

                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Confirm Followers
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.CheckCircle,
                        title = "Підтвердження підписників",
                        description = "Підтверджувати запити на підписку",
                        checked = confirmFollowers == "1",
                        onCheckedChange = { confirmFollowers = if (it) "1" else "0" }
                    )
                }

                // Show Activities
                item {
                    SwitchSettingItem(
                        icon = Icons.Default.Visibility,
                        title = "Показувати мою активність",
                        description = "Дозволити іншим бачити мою активність",
                        checked = showActivitiesPrivacy == "1",
                        onCheckedChange = { showActivitiesPrivacy = if (it) "1" else "0" }
                    )
                }

                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Birth Privacy
                item {
                    PrivacySettingItem(
                        icon = Icons.Default.Cake,
                        title = "Хто бачить мій день народження",
                        value = getPrivacyLabel(birthPrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.BIRTH }
                    )
                }

                // Visit Privacy
                item {
                    PrivacySettingItem(
                        icon = Icons.Default.RemoveRedEye,
                        title = "Хто бачить мої відвідування профілю",
                        value = getPrivacyLabel(visitPrivacy),
                        onClick = { showPrivacyDialog = PrivacyType.VISIT }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Privacy Selection Dialog
    showPrivacyDialog?.let { type ->
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = null },
            title = { Text(getPrivacyDialogTitle(type)) },
            text = {
                Column {
                    listOf(
                        "everyone" to "Всі",
                        "friends" to "Друзі",
                        "followers" to "Підписники",
                        "me" to "Тільки я"
                    ).forEach { (value, label) ->
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
                    Text("Закрити")
                }
            }
        )
    }
}

enum class PrivacyType {
    FOLLOW, FRIEND, POST, MESSAGE, BIRTH, VISIT
}

fun getPrivacyLabel(value: String): String {
    return when (value) {
        "everyone" -> "Всі"
        "friends" -> "Друзі"
        "followers" -> "Підписники"
        "me" -> "Тільки я"
        else -> "Всі"
    }
}

fun getPrivacyDialogTitle(type: PrivacyType): String {
    return when (type) {
        PrivacyType.FOLLOW -> "Хто може підписатися"
        PrivacyType.FRIEND -> "Хто може додати в друзі"
        PrivacyType.POST -> "Хто бачить пости"
        PrivacyType.MESSAGE -> "Хто може писати"
        PrivacyType.BIRTH -> "Хто бачить день народження"
        PrivacyType.VISIT -> "Хто бачить відвідування"
    }
}

@Composable
fun PrivacySettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF0084FF),
                modifier = Modifier.size(24.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                Text(
                    text = value,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "Змінити",
                tint = Color.Gray
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
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF0084FF),
                modifier = Modifier.size(24.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
