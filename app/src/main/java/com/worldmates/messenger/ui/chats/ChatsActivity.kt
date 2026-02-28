package com.worldmates.messenger.ui.chats

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.worldmates.messenger.services.MessageNotificationService
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.Factory
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.Chat
import com.worldmates.messenger.data.ContactNicknameRepository
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import com.worldmates.messenger.ui.messages.MessagesActivity
import com.worldmates.messenger.ui.preferences.UIStyle
import com.worldmates.messenger.ui.preferences.rememberUIStyle
import kotlinx.coroutines.delay
import com.worldmates.messenger.ui.theme.AnimatedGradientBackground
import com.worldmates.messenger.ui.theme.ChatGlassCard
import com.worldmates.messenger.ui.theme.ExpressiveFAB
import com.worldmates.messenger.ui.theme.ExpressiveIconButton
import com.worldmates.messenger.ui.theme.GlassTopAppBar
import com.worldmates.messenger.ui.theme.PulsingBadge
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WMColors
import com.worldmates.messenger.ui.theme.WMGradients
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R

class ChatsActivity : AppCompatActivity() {

    private lateinit var viewModel: ChatsViewModel
    private lateinit var groupsViewModel: com.worldmates.messenger.ui.groups.GroupsViewModel
    private lateinit var channelsViewModel: com.worldmates.messenger.ui.channels.ChannelsViewModel
    private lateinit var storyViewModel: com.worldmates.messenger.ui.stories.StoryViewModel
    private lateinit var callsViewModel: com.worldmates.messenger.ui.calls.CallsViewModel

    // Android 13+ runtime permission launcher for POST_NOTIFICATIONS
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Start the service regardless — on older Android it works without permission,
            // on Android 13+ the user's choice is respected by the system automatically.
            MessageNotificationService.start(this)
        }

    // Factory для створення ChatsViewModel з параметром context
    private class ChatsViewModelFactory(private val context: android.content.Context) : Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatsViewModel::class.java)) {
                return ChatsViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: app renders under system bars; each composable adds its own inset padding
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Инициализируем ThemeManager
        ThemeManager.initialize(this)

        // Запускаємо сервіс Socket.IO-сповіщень (без Firebase)
        startMessageNotificationService()

        // Ініціалізуємо UI Style Preferences
        com.worldmates.messenger.ui.preferences.UIStylePreferences.init(this)

        // Ініціалізуємо менеджер організації чатів (папки, архів, теги)
        ChatOrganizationManager.init(this)

        viewModel = ViewModelProvider(this, ChatsViewModelFactory(applicationContext)).get(ChatsViewModel::class.java)
        groupsViewModel = ViewModelProvider(this).get(com.worldmates.messenger.ui.groups.GroupsViewModel::class.java)
        channelsViewModel = ViewModelProvider(this).get(com.worldmates.messenger.ui.channels.ChannelsViewModel::class.java)
        storyViewModel = ViewModelProvider(this).get(com.worldmates.messenger.ui.stories.StoryViewModel::class.java)

        // ✅ Ініціалізуємо CallsViewModel для обробки вхідних дзвінків
        callsViewModel = ViewModelProvider(this).get(com.worldmates.messenger.ui.calls.CallsViewModel::class.java)
        android.util.Log.d("ChatsActivity", "📞 CallsViewModel initialized for incoming calls")

        setContent {
            WorldMatesThemedApp {
                // Обробка необхідності перелогіну
                val needsRelogin by viewModel.needsRelogin.collectAsState()

                LaunchedEffect(needsRelogin) {
                    if (needsRelogin) {
                        // Перенаправляємо на екран логіну
                        navigateToLogin()
                        finish()
                    }
                }

                // Нижня навігація: Чати | Контакти | Настройки | Профіль
                var selectedBottomTab by remember {
                    mutableStateOf(BottomNavTab.CHATS)
                }

                // Основна структура: контент + нижня навігація
                Box(modifier = Modifier.fillMaxSize()) {
                    // Контент займає весь простір мінус висота нижньої навігації
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp) // Місце для нижньої навігації
                    ) {
                        when (selectedBottomTab) {
                            BottomNavTab.CHATS -> {
                                ChatsScreenModern(
                                    viewModel = viewModel,
                                    groupsViewModel = groupsViewModel,
                                    channelsViewModel = channelsViewModel,
                                    storyViewModel = storyViewModel,
                                    onChatClick = { chat ->
                                        navigateToMessages(chat)
                                    },
                                    onGroupClick = { group ->
                                        navigateToGroupMessages(group)
                                    },
                                    onChannelClick = { channel ->
                                        navigateToChannelDetails(channel)
                                    },
                                    onSettingsClick = {
                                        navigateToSettings()
                                    },
                                    onCreateChannelClick = {
                                        navigateToCreateChannel()
                                    }
                                )
                            }
                            BottomNavTab.CONTACTS -> {
                                com.worldmates.messenger.ui.components.ContactPicker(
                                    onContactSelected = { contact ->
                                        android.widget.Toast.makeText(
                                            this@ChatsActivity,
                                            "Контакт: ${contact.name}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onDismiss = {
                                        selectedBottomTab = BottomNavTab.CHATS
                                    }
                                )
                            }
                            else -> {
                                // Settings та Profile відкриваються як окремі Activity
                                ChatsScreenModern(
                                    viewModel = viewModel,
                                    groupsViewModel = groupsViewModel,
                                    channelsViewModel = channelsViewModel,
                                    storyViewModel = storyViewModel,
                                    onChatClick = { chat -> navigateToMessages(chat) },
                                    onGroupClick = { group -> navigateToGroupMessages(group) },
                                    onChannelClick = { channel -> navigateToChannelDetails(channel) },
                                    onSettingsClick = { navigateToSettings() },
                                    onCreateChannelClick = { navigateToCreateChannel() }
                                )
                            }
                        }
                    }

                    // Нижня навігація прикріплена до низу
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        AppBottomNavBar(
                            selectedTab = selectedBottomTab,
                            onTabSelected = { tab ->
                                when (tab) {
                                    BottomNavTab.CONTACTS -> {
                                        selectedBottomTab = tab
                                    }
                                    BottomNavTab.SETTINGS -> {
                                        navigateToSettings()
                                    }
                                    BottomNavTab.PROFILE -> {
                                        startActivity(
                                            Intent(
                                                this@ChatsActivity,
                                                com.worldmates.messenger.ui.profile.UserProfileActivity::class.java
                                            )
                                        )
                                    }
                                    BottomNavTab.CHATS -> {
                                        selectedBottomTab = BottomNavTab.CHATS
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Оновлюємо список чатів при поверненні на екран
        viewModel.fetchChats()
        groupsViewModel.fetchGroups()
        channelsViewModel.fetchSubscribedChannels()
        storyViewModel.loadStories()
    }

    private fun navigateToMessages(chat: Chat) {
        startActivity(Intent(this, MessagesActivity::class.java).apply {
            putExtra("recipient_id", chat.userId)
            putExtra("recipient_name", chat.username)
            putExtra("recipient_avatar", chat.avatarUrl)
        })
    }

    private fun navigateToGroupMessages(group: com.worldmates.messenger.data.model.Group) {
        startActivity(Intent(this, MessagesActivity::class.java).apply {
            putExtra("group_id", group.id)
            putExtra("recipient_name", group.name)
            putExtra("recipient_avatar", group.avatarUrl)
            putExtra("is_group", true)
        })
    }

    private fun navigateToChannelDetails(channel: com.worldmates.messenger.data.model.Channel) {
        startActivity(Intent(this, com.worldmates.messenger.ui.channels.ChannelDetailsActivity::class.java).apply {
            putExtra("channel_id", channel.id)
        })
    }

    private fun navigateToSettings() {
        startActivity(Intent(this, com.worldmates.messenger.ui.settings.SettingsActivity::class.java))
    }

    private fun navigateToCreateChannel() {
        startActivity(Intent(this, com.worldmates.messenger.ui.channels.CreateChannelActivity::class.java))
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, com.worldmates.messenger.ui.login.LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    /**
     * Запитує дозвіл POST_NOTIFICATIONS (Android 13+) і запускає
     * фоновий сервіс Socket.IO-сповіщень.
     * На Android ≤ 12 дозвіл не потрібен — сервіс стартує одразу.
     */
    private fun startMessageNotificationService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    MessageNotificationService.start(this)
                }
                else -> {
                    // Launcher показує системний діалог; сервіс стартує в колбеку
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            MessageNotificationService.start(this)
        }

        // Запитуємо вимкнення оптимізації батареї для надійної доставки повідомлень
        requestBatteryOptimizationExemption()
    }

    /**
     * Запитує у користувача дозвіл на вимкнення оптимізації батареї,
     * щоб система не вбивала фоновий сервіс сповіщень.
     * Показується тільки один раз (якщо ще не вимкнено).
     */
    private fun requestBatteryOptimizationExemption() {
        if (!com.worldmates.messenger.services.NotificationKeepAliveManager.isBatteryOptimizationDisabled(this)) {
            try {
                val intent = com.worldmates.messenger.services.NotificationKeepAliveManager
                    .getBatteryOptimizationIntent(this)
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.w("ChatsActivity", "Cannot request battery optimization exemption", e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    viewModel: ChatsViewModel,
    groupsViewModel: com.worldmates.messenger.ui.groups.GroupsViewModel,
    onChatClick: (Chat) -> Unit,
    onGroupClick: (com.worldmates.messenger.data.model.Group) -> Unit,
    onSettingsClick: () -> Unit
) {
    val chats by viewModel.chatList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val groups by groupsViewModel.groupList.collectAsState()
    val isLoadingGroups by groupsViewModel.isLoading.collectAsState()
    val errorGroups by groupsViewModel.error.collectAsState()

    // Стан для бічної панелі налаштувань
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 📇 Стан для ContactPicker
    var showContactPicker by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                // Контент бічної панелі налаштувань
                SettingsDrawerContent(
                    onNavigateToFullSettings = {
                        scope.launch {
                            drawerState.close()
                        }
                        onSettingsClick()
                    },
                    onClose = {
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    onShowContactPicker = {
                        showContactPicker = true
                    },
                    onShowDrafts = {
                        // Открываем экран черновиков
                        context.startActivity(
                            Intent(context, com.worldmates.messenger.ui.drafts.DraftsActivity::class.java)
                        )
                    },
                    onCreateGroup = {
                        // Open create group dialog
                        showCreateGroupDialog = true
                    }
                )
            }
        },
        gesturesEnabled = true
    ) {
    val availableUsers by groupsViewModel.availableUsers.collectAsState()
    val isCreatingGroup by groupsViewModel.isCreatingGroup.collectAsState()

    var searchText by remember { mutableStateOf("") }
    var showGroups by remember { mutableStateOf(false) }
    var selectedChat by remember { mutableStateOf<Chat?>(null) }
    var showContactMenu by remember { mutableStateOf(false) }

    // Для редагування груп
    var selectedGroup by remember { mutableStateOf<com.worldmates.messenger.data.model.Group?>(null) }
    var showEditGroupDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val nicknameRepository = remember { ContactNicknameRepository(context) }

    // Load available users when switching to groups tab
    LaunchedEffect(showGroups) {
        if (showGroups) {
            groupsViewModel.loadAvailableUsers()
        }
    }

    // Показуємо помилки через Snackbar
    LaunchedEffect(errorGroups) {
        errorGroups?.let { errorMessage ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    // Фільтруємо тільки особисті чати (НЕ групи)
    val filteredChats = chats.filter {
        !it.isGroup && it.username?.contains(searchText, ignoreCase = true) == true
    }
    // Фільтруємо групи
    val filteredGroups = groups.filter {
        it.name.contains(searchText, ignoreCase = true)
    }

    // Telegram-style - простой цвет фона без градиента
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,  // Цвет фона из темы
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (showGroups) {
                ModernFAB(
                    onClick = { showCreateGroupDialog = true },
                    icon = Icons.Default.Add
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        // Glass Header with expressive motion
        GlassTopAppBar(
            title = {
                Text(
                    "Повідомлення",
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                var showSearchDialog by remember { mutableStateOf(false) }

                // Refresh button with expressive animation
                ExpressiveIconButton(onClick = {
                    if (showGroups) {
                        groupsViewModel.fetchGroups()
                    } else {
                        viewModel.fetchChats()
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Оновити")
                }

                ExpressiveIconButton(onClick = { showSearchDialog = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Пошук користувачів")
                }
                ExpressiveIconButton(onClick = {
                    scope.launch {
                        drawerState.open()
                    }
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "Налаштування")
                }

                if (showSearchDialog) {
                    UserSearchDialog(
                        onDismiss = { showSearchDialog = false },
                        onUserClick = { user ->
                            showSearchDialog = false
                            // TODO: Navigate to messages with this user
                        }
                    )
                }
            }
        )

        // Modern Search Bar
        ModernSearchBar(
            searchText = searchText,
            onSearchChange = { searchText = it }
        )

        // Modern Tabs
        ModernTabsRow(
            selectedTab = if (showGroups) 1 else 0,
            onTabSelected = { tab -> showGroups = (tab == 1) }
        )

        // Content
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                // Loading indicator
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF0084FF))
                    Text(
                        "Завантаження...",
                        modifier = Modifier.padding(top = 16.dp),
                        color = Color.Gray
                    )
                }
            } else if (error != null) {
                // Error state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "⚠️",
                        fontSize = 48.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        error ?: "Помилка завантаження",
                        color = Color.Red,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Button(
                        onClick = { viewModel.fetchChats() },
                        modifier = Modifier.padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0084FF)
                        )
                    ) {
                        Text("Спробувати ще раз")
                    }
                }
            } else if (showGroups) {
                // Groups List
                if (isLoadingGroups) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF0084FF))
                        Text(
                            "Завантаження груп...",
                            modifier = Modifier.padding(top = 16.dp),
                            color = Color.Gray
                        )
                    }
                } else if (errorGroups != null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "⚠️",
                            fontSize = 48.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            errorGroups ?: "Помилка завантаження груп",
                            color = Color.Red,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Button(
                            onClick = { groupsViewModel.fetchGroups() },
                            modifier = Modifier.padding(top = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0084FF)
                            )
                        ) {
                            Text("Спробувати ще раз")
                        }
                    }
                } else if (filteredGroups.isEmpty()) {
                    EmptyGroupsState(onCreateClick = { showCreateGroupDialog = true })
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredGroups) { group ->
                            ModernGroupCard(
                                group = group,
                                onClick = { onGroupClick(group) },
                                onLongPress = {
                                    // Відкриваємо діалог редагування групи
                                    android.util.Log.d("ChatsActivity", "Long press on group: ${group.name}")
                                    selectedGroup = group
                                    showEditGroupDialog = true
                                    android.util.Log.d("ChatsActivity", "showEditGroupDialog = $showEditGroupDialog, selectedGroup = ${selectedGroup?.name}")
                                }
                            )
                        }
                    }
                }
            } else {
                // Chats List
                if (filteredChats.isEmpty()) {
                    EmptyChatsState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredChats) { chat ->
                            val nickname by nicknameRepository.getNickname(chat.userId).collectAsState(initial = null)
                            ModernChatCard(
                                chat = chat,
                                nickname = nickname,
                                onClick = { onChatClick(chat) },
                                onLongPress = {
                                    selectedChat = chat
                                    showContactMenu = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // Create Group Dialog
        if (showCreateGroupDialog) {
            val context = LocalContext.current
            com.worldmates.messenger.ui.groups.CreateGroupDialog(
                onDismiss = { showCreateGroupDialog = false },
                availableUsers = availableUsers,
                onCreateGroup = { name, description, memberIds, isPrivate, avatarUri ->
                    groupsViewModel.createGroup(
                        name = name,
                        description = description,
                        memberIds = memberIds,
                        isPrivate = isPrivate,
                        avatarUri = avatarUri,
                        context = context,
                        onSuccess = {
                            showCreateGroupDialog = false
                        }
                    )
                },
                isLoading = isCreatingGroup
            )
        }

        // Edit Group Dialog
        android.util.Log.d("ChatsActivity", "Checking EditGroupDialog: showEditGroupDialog=$showEditGroupDialog, selectedGroup=${selectedGroup?.name}")
        if (showEditGroupDialog && selectedGroup != null) {
            android.util.Log.d("ChatsActivity", "Showing EditGroupDialog for group: ${selectedGroup?.name}")
            com.worldmates.messenger.ui.groups.EditGroupDialog(
                group = selectedGroup!!,
                onDismiss = {
                    showEditGroupDialog = false
                    selectedGroup = null
                },
                onUpdate = { newName ->
                    // Оновлення назви групи
                    groupsViewModel.updateGroup(
                        groupId = selectedGroup!!.id,
                        name = newName,
                        onSuccess = {
                            showEditGroupDialog = false
                            selectedGroup = null
                            groupsViewModel.fetchGroups() // Оновлюємо список
                        }
                    )
                },
                onDelete = {
                    // Видалення групи
                    groupsViewModel.deleteGroup(
                        groupId = selectedGroup!!.id,
                        onSuccess = {
                            showEditGroupDialog = false
                            selectedGroup = null
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Групу видалено",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    )
                },
                onUploadAvatar = { uri ->
                    // Завантаження нової аватарки групи
                    val currentGroup = groupsViewModel.selectedGroup.value
                    if (currentGroup != null) {
                        groupsViewModel.uploadGroupAvatar(currentGroup.id, uri, context)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Завантаження аватарки...",
                                duration = SnackbarDuration.Short
                            )
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Оберіть групу для зміни аватарки",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                isLoading = groupsViewModel.isLoading.collectAsState().value
            )
        }

        // Contact Context Menu
        if (showContactMenu && selectedChat != null) {
            ContactContextMenu(
                chat = selectedChat!!,
                onDismiss = {
                    showContactMenu = false
                    selectedChat = null
                },
                onRename = { chat: Chat ->
                    // Діалог відкривається всередині ContactContextMenu
                },
                onDelete = { chat: Chat ->
                    showContactMenu = false
                    // Видалити чат локально (приховати)
                    scope.launch {
                        viewModel.hideChat(chat.userId)
                        snackbarHostState.showSnackbar(
                            message = "Чат приховано",
                            duration = SnackbarDuration.Short
                        )
                    }
                    selectedChat = null
                },
                nicknameRepository = nicknameRepository
            )
        }

        // 📇 ContactPicker для выбора контакта из телефонной книги
        if (showContactPicker) {
            com.worldmates.messenger.ui.components.ContactPicker(
                onContactSelected = { contact ->
                    // Здесь можно добавить логику - например, открыть чат с контактом
                    // или показать детали контакта
                    Toast.makeText(
                        context,
                        "Выбран контакт: ${contact.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    showContactPicker = false
                },
                onDismiss = {
                    showContactPicker = false
                }
            )
        }
    }  // Конец lambda paddingValues для Scaffold
    }  // Конец Scaffold
    }  // Конец ModalNavigationDrawer
}  // Конец функции ChatsScreen

/**
 * Контент бічної панелі налаштувань
 */
@Composable
fun SettingsDrawerContent(
    onNavigateToFullSettings: () -> Unit,
    onClose: () -> Unit,
    onShowContactPicker: () -> Unit = {},
    onShowDrafts: () -> Unit = {},
    onCreateStoryClick: () -> Unit = {},
    onCreateGroup: () -> Unit = {}
) {
    val context = LocalContext.current

    // Observe avatar changes
    val currentAvatar by com.worldmates.messenger.data.UserSession.avatarFlow.collectAsState()

    // State для діалогів
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF667eea),
                        Color(0xFF764ba2)
                    )
                )
            )
    ) {
        // Header з інфо користувача
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = currentAvatar,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = com.worldmates.messenger.data.UserSession.username ?: "Користувач",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "+380 (93) 025 39 41",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Закрити",
                        tint = Color.White
                    )
                }
            }
        }

        // Меню items
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            item {
                DrawerMenuItem(
                    icon = Icons.Default.Person,
                    title = "Мій профіль",
                    onClick = {
                        onClose()
                        context.startActivity(
                            Intent(context, com.worldmates.messenger.ui.profile.UserProfileActivity::class.java)
                        )
                    }
                )
            }

            item {
                DrawerMenuItem(
                    icon = Icons.Default.Group,
                    title = "Нова група",
                    onClick = {
                        onClose()
                        onCreateGroup()
                    }
                )
            }

            item {
                DrawerMenuItem(
                    icon = Icons.Default.CameraAlt,
                    title = "Створити Story",
                    onClick = {
                        onClose()
                        onCreateStoryClick()
                    }
                )
            }

            item {
                DrawerMenuItem(
                    icon = Icons.Default.Person,
                    title = "Контакти",
                    onClick = {
                        onClose()
                        onShowContactPicker()
                    }
                )
            }

            item {
                DrawerMenuItem(
                    icon = Icons.Default.Edit,
                    title = "Черновики",
                    onClick = {
                        onClose()
                        onShowDrafts()
                    }
                )
            }

            item {
                DrawerMenuItem(
                    icon = Icons.Default.Call,
                    title = "Дзвінки",
                    onClick = {
                        onClose()
                        context.startActivity(
                            android.content.Intent(context, com.worldmates.messenger.ui.calls.CallHistoryActivity::class.java)
                        )
                    }
                )
            }

            item {
                DrawerMenuItem(
                    icon = Icons.Default.Star,
                    title = "Збережені повідомлення",
                    onClick = {
                        onClose()
                        Toast.makeText(context, "Збережені", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                Divider(
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp)
                )
            }

            item {
                DrawerMenuItem(
                    icon = Icons.Default.Settings,
                    title = "Налаштування",
                    onClick = onNavigateToFullSettings
                )
            }

            item {
                DrawerMenuItem(
                    icon = Icons.Default.Share,
                    title = "Запросити друзів",
                    onClick = {
                        onClose()
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Приєднуйся до WorldMates - найкращого месенджера! 🚀\n" +
                                "Завантаж тут: https://worldmates.club"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Запросити друга"))
                    }
                )
            }

            item {
                DrawerMenuItem(
                    icon = Icons.Default.Info,
                    title = "Про додаток",
                    onClick = {
                        onClose()
                        showAboutDialog = true
                    }
                )
            }
        }
    }

    // Діалог "Про додаток"
    if (showAboutDialog) {
        com.worldmates.messenger.ui.components.AboutAppDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SearchBar(
    searchText: String,
    onSearchChange: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface)  // Цвет из темы
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = "Search",
            modifier = Modifier.padding(horizontal = 12.dp),
            tint = colorScheme.onSurfaceVariant
        )

        TextField(
            value = searchText,
            onValueChange = onSearchChange,
            modifier = Modifier
                .weight(1f)
                .background(colorScheme.surfaceVariant, RoundedCornerShape(24.dp)),
            placeholder = { Text("Пошук чатів...", color = colorScheme.onSurfaceVariant) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatItemRow(
    chat: Chat,
    nickname: String? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(10.dp),  // Внутренний padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        AsyncImage(
            model = chat.avatarUrl,
            contentDescription = chat.username,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        // Chat info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            // Показуємо псевдонім якщо є, інакше оригінальне ім'я
            Text(
                text = nickname ?: chat.username ?: "Unknown",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Якщо є псевдонім, показуємо оригінальне ім'я нижче
            if (nickname != null && chat.username != null) {
                Text(
                    text = "@${chat.username}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Text(
                text = chat.lastMessage?.let {
                    com.worldmates.messenger.ui.messages.getLastMessagePreview(it)
                } ?: "Немає повідомлень",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

            // Pulsing badge for unread messages
            if (chat.unreadCount > 0) {
                PulsingBadge(count = chat.unreadCount)
            }
        }
    }

@Composable
fun EmptyChatsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📭",
            fontSize = 48.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Немаєте чатів",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Text(
            text = "Почніть розмову зараз!",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun GroupItemRow(
    group: com.worldmates.messenger.data.model.Group,
    onClick: () -> Unit
) {
    ChatGlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)  // Компактнее
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),  // Меньше внутренний padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            AsyncImage(
                model = group.avatarUrl,
                contentDescription = group.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            // Group info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = group.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row {
                    Text(
                        text = "${group.membersCount} членів",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (group.isPrivate) {
                        Text(
                            text = " • Приватна",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Admin badge
            if (group.isAdmin) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "👑",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyGroupsState(onCreateClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "👥",
            fontSize = 48.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Немає груп",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Text(
            text = "Створіть нову групу або приєднайтесь до існуючої",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Велика кнопка створення групи
        Button(
            onClick = onCreateClick,
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(0.8f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0084FF)
            )
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Створити групу",
                fontSize = 16.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSearchDialog(
    onDismiss: () -> Unit,
    onUserClick: (com.worldmates.messenger.network.SearchUser) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.worldmates.messenger.network.SearchUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пошук користувачів") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                // Search field
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Введіть ім'я або username") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Search button
                Button(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            isSearching = true
                            errorMessage = null
                            // Perform search
                            coroutineScope.launch {
                                try {
                                    val response = com.worldmates.messenger.network.RetrofitClient.apiService.searchUsers(
                                        accessToken = com.worldmates.messenger.data.UserSession.accessToken ?: "",
                                        query = searchQuery
                                    )
                                    if (response.apiStatus == 200 && response.users != null) {
                                        searchResults = response.users
                                        errorMessage = null
                                    } else {
                                        errorMessage = "Нічого не знайдено"
                                        searchResults = emptyList()
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Помилка: ${e.localizedMessage}"
                                    searchResults = emptyList()
                                } finally {
                                    isSearching = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSearching && searchQuery.isNotBlank()
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Шукати")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Error message
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Search results
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(searchResults, key = { it.userId }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUserClick(user) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = user.username,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = user.name ?: user.username,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@${user.username}",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }

                            if (user.verified == 1) {
                                Text("✓", color = Color(0xFF0084FF), fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити")
            }
        }
    )
}

// ContactContextMenu та RenameContactDialog витягнуто в ChatContactMenu.kt
