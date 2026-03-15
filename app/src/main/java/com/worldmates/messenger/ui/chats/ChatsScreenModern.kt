package com.worldmates.messenger.ui.chats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.ContactNicknameRepository
import com.worldmates.messenger.data.model.Chat
import com.worldmates.messenger.data.model.Group
import com.worldmates.messenger.ui.channels.ChannelCard
import com.worldmates.messenger.ui.channels.PremiumChannelListItem
import com.worldmates.messenger.ui.preferences.ChannelViewStyle
import com.worldmates.messenger.ui.preferences.UIStyle
import com.worldmates.messenger.ui.preferences.rememberChannelViewStyle
import com.worldmates.messenger.ui.preferences.rememberUIStyle
import com.worldmates.messenger.ui.theme.ExpressiveFAB
import com.worldmates.messenger.ui.theme.ExpressiveIconButton
import com.worldmates.messenger.ui.theme.GlassTopAppBar
import com.worldmates.messenger.ui.theme.rememberThemeState
import com.worldmates.messenger.ui.theme.BackgroundImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R

/**
 * Сучасний екран чатів з:
 * - HorizontalPager для свайпу між вкладками
 * - Pull-to-Refresh на кожній вкладці
 * - Автооновлення кожні 6 секунд
 * - Вибір між WorldMates та Telegram стилем
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterialApi::class)
@Composable
fun ChatsScreenModern(
    viewModel: ChatsViewModel,
    groupsViewModel: com.worldmates.messenger.ui.groups.GroupsViewModel,
    channelsViewModel: com.worldmates.messenger.ui.channels.ChannelsViewModel,
    storyViewModel: com.worldmates.messenger.ui.stories.StoryViewModel,
    onChatClick: (Chat) -> Unit,
    onGroupClick: (Group) -> Unit,
    onChannelClick: (com.worldmates.messenger.data.model.Channel) -> Unit,
    onSettingsClick: () -> Unit,
    onCreateChannelClick: () -> Unit = {}
) {
    val chats by viewModel.chatList.collectAsState()
    val groups by groupsViewModel.groupList.collectAsState()
    val channels by channelsViewModel.subscribedChannels.collectAsState()
    val liveChannelIds by channelsViewModel.liveChannelIds.collectAsState()
    val isLoadingChats by viewModel.isLoading.collectAsState()
    val isLoadingGroups by groupsViewModel.isLoading.collectAsState()
    val isLoadingChannels by channelsViewModel.isLoading.collectAsState()
    val availableUsers by groupsViewModel.availableUsers.collectAsState()
    val isCreatingGroup by groupsViewModel.isCreatingGroup.collectAsState()

    // Stories state
    val stories by storyViewModel.stories.collectAsState()
    val isLoadingStories by storyViewModel.isLoading.collectAsState()

    // Channel stories state
    val channelStories by storyViewModel.channelStories.collectAsState()

    val uiStyle = rememberUIStyle()
    val themeState = rememberThemeState()
    val pagerState = rememberPagerState(initialPage = 0) { 3 } // 3 вкладки: Чати, Канали, Групи
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Стан для бічної панелі налаштувань
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Автооновлення кожні 6 секунд
    LaunchedEffect(pagerState.currentPage) {
        while (true) {
            delay(6000) // 6 секунд
            when (pagerState.currentPage) {
                0 -> viewModel.fetchChats()
                1 -> {
                    channelsViewModel.fetchSubscribedChannels()
                    storyViewModel.loadChannelStories()
                }
                2 -> groupsViewModel.fetchGroups()
            }
        }
    }

    // Load channel stories when switching to channels tab
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) {
            storyViewModel.loadChannelStories()
        }
        if (pagerState.currentPage == 2) {
            groupsViewModel.loadAvailableUsers()
        }
    }

    // Стан для діалогів
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreateStoryDialog by remember { mutableStateOf(false) }
    var showCreateChannelStoryDialog by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var showEditGroupDialog by remember { mutableStateOf(false) }
    var selectedChat by remember { mutableStateOf<Chat?>(null) }
    var showContactMenu by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }

    // 📇 Стан для ContactPicker
    var showContactPicker by remember { mutableStateOf(false) }

    // Організація контенту: папки (Telegram-style), архів, теги
    var selectedFolderId by remember { mutableStateOf("all") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showManageTagsDialog by remember { mutableStateOf(false) }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    var tagTargetChatId by remember { mutableStateOf<Long?>(null) }
    var tagTargetChatName by remember { mutableStateOf("") }
    var folderTargetChatId by remember { mutableStateOf<Long?>(null) }
    var folderTargetChatName by remember { mutableStateOf("") }

    val archivedIds by ChatOrganizationManager.archivedChatIds.collectAsState()
    val folderMapping by ChatOrganizationManager.chatFolderMapping.collectAsState()
    val chatFolders by ChatOrganizationManager.folders.collectAsState()

    // Визначаємо яку сторінку пейджера показувати за обраною папкою
    val targetPagerPage = remember(selectedFolderId) {
        when (selectedFolderId) {
            "channels" -> 1
            "groups" -> 2
            else -> 0 // all, personal, unread, archived, custom folders -> chats page
        }
    }

    // Синхронізуємо пейджер з обраною папкою
    LaunchedEffect(targetPagerPage) {
        if (pagerState.currentPage != targetPagerPage) {
            pagerState.animateScrollToPage(targetPagerPage)
        }
    }

    // Фільтрація чатів за обраною папкою
    val filteredChats = remember(chats, selectedFolderId, archivedIds, folderMapping) {
        filterChatsByFolder(chats, selectedFolderId, archivedIds, folderMapping)
    }

    val context = LocalContext.current
    val nicknameRepository = remember { ContactNicknameRepository(context) }
    val chatLabel = stringResource(R.string.chat_label)

    // ModalNavigationDrawer для свайпу налаштувань
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = androidx.compose.ui.Modifier.width(300.dp),
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
                            android.content.Intent(context, com.worldmates.messenger.ui.drafts.DraftsActivity::class.java)
                        )
                    },
                    onCreateStoryClick = {
                        showCreateStoryDialog = true
                    }
                )
            }
        },
        gesturesEnabled = true
    ) {
    // Box з фоновим зображенням з налаштувань тем
    Box(modifier = Modifier.fillMaxSize()) {
        // Фонове зображення з налаштувань тем
        BackgroundImage(
            backgroundImageUri = themeState.backgroundImageUri,
            presetBackgroundId = themeState.presetBackgroundId
        )

        Scaffold(
            containerColor = Color.Transparent,  // Прозорий фон, щоб було видно BackgroundImage
            snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = {
                    Text(
                        text = when {
                            selectedFolderId == "all" -> "WorldMates"
                            selectedFolderId == "archived" -> stringResource(R.string.archive_folder_label)
                            selectedFolderId == "channels" -> stringResource(R.string.channels)
                            selectedFolderId == "groups" -> stringResource(R.string.groups)
                            selectedFolderId == "personal" -> stringResource(R.string.personal)
                            selectedFolderId == "unread" -> stringResource(R.string.unread_label)
                            else -> chatFolders.find { it.id == selectedFolderId }?.let {
                                "${it.emoji} ${it.name}"
                            } ?: "WorldMates"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    // Кнопка меню для відкриття drawer
                    ExpressiveIconButton(onClick = {
                        scope.launch {
                            drawerState.open()
                        }
                    }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Menu,
                            contentDescription = stringResource(R.string.menu)
                        )
                    }
                },
                actions = {
                    // Пошук користувачів/груп
                    ExpressiveIconButton(onClick = { showSearchDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                    // Налаштування (залишаємо для швидкого доступу)
                    ExpressiveIconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            // FAB для створення каналу/групи
            when (pagerState.currentPage) {
                1 -> {
                    // Вкладка Канали - створити канал
                    ExpressiveFAB(
                        onClick = onCreateChannelClick
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_channel))
                    }
                }
                2 -> {
                    // Вкладка Групи - створити групу
                    ExpressiveFAB(
                        onClick = { showCreateGroupDialog = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_group))
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Telegram-style папки замінюють TabRow
            ChatFolderTabs(
                selectedFolderId = selectedFolderId,
                onFolderSelected = { folderId ->
                    selectedFolderId = folderId
                },
                onAddFolder = { showCreateFolderDialog = true }
            )

            // HorizontalPager з вкладками
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false // Контролюється папками
            ) { page ->
                when (page) {
                    0 -> {
                        // Вкладка "Чати" з pull-to-refresh + Stories
                        Column(modifier = Modifier.fillMaxSize()) {
                            val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                            val hasMoreChats  by viewModel.hasMoreChats.collectAsState()
                            // Список чатів (вже відфільтрований)
                            ChatListTabWithStories(
                                chats = filteredChats,
                                stories = if (selectedFolderId == "all") stories else emptyList(),
                                isLoading = isLoadingChats,
                                isLoadingStories = isLoadingStories,
                                uiStyle = uiStyle,
                                onRefresh = {
                                    viewModel.fetchChats()
                                    storyViewModel.loadStories()
                                },
                                onChatClick = onChatClick,
                                onChatLongPress = { chat ->
                                    selectedChat = chat
                                    showContactMenu = true
                                },
                                onCreateStoryClick = {
                                    showCreateStoryDialog = true
                                },
                                isLoadingMore = isLoadingMore,
                                hasMoreChats  = hasMoreChats,
                                onLoadMore    = { viewModel.loadMoreChats() }
                            )
                        }
                    }
                    1 -> {
                        // Вкладка "Канали" з channel stories
                        ChannelListTabWithStories(
                            channels = channels,
                            stories = channelStories,
                            isLoading = isLoadingChannels,
                            isLoadingStories = false,
                            uiStyle = uiStyle,
                            channelsViewModel = channelsViewModel,
                            onRefresh = {
                                channelsViewModel.fetchSubscribedChannels()
                                storyViewModel.loadChannelStories()
                            },
                            onChannelClick = onChannelClick,
                            onCreateChannelStoryClick = {
                                showCreateChannelStoryDialog = true
                            },
                            liveChannelIds = liveChannelIds
                        )
                    }
                    2 -> {
                        // Вкладка "Групи" з pull-to-refresh
                        GroupListTab(
                            groups = groups,
                            isLoading = isLoadingGroups,
                            uiStyle = uiStyle,
                            onRefresh = { groupsViewModel.fetchGroups() },
                            onGroupClick = onGroupClick,
                            onGroupLongPress = { group ->
                                selectedGroup = group
                                showEditGroupDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Діалоги
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
    if (showEditGroupDialog && selectedGroup != null) {
        com.worldmates.messenger.ui.groups.EditGroupDialog(
            group = selectedGroup!!,
            onDismiss = {
                showEditGroupDialog = false
                selectedGroup = null
            },
            onUpdate = { newName ->
                groupsViewModel.updateGroup(
                    groupId = selectedGroup!!.id,
                    name = newName,
                    onSuccess = {
                        showEditGroupDialog = false
                        selectedGroup = null
                        groupsViewModel.fetchGroups()
                    }
                )
            },
            onDelete = {
                groupsViewModel.deleteGroup(
                    groupId = selectedGroup!!.id,
                    onSuccess = {
                        showEditGroupDialog = false
                        selectedGroup = null
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.group_deleted_toast),
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
            },
            onUploadAvatar = { uri ->
                // Отримуємо ID вибраної групи
                val selectedGroup = groupsViewModel.selectedGroup.value
                if (selectedGroup != null) {
                    groupsViewModel.uploadGroupAvatar(selectedGroup.id, uri, context)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.uploading_avatar),
                            duration = SnackbarDuration.Short
                        )
                    }
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.select_group_for_avatar),
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            },
            isLoading = groupsViewModel.isLoading.collectAsState().value
        )
    }

    // User Search Dialog
    if (showSearchDialog) {
        UserSearchDialogForChats(
            onDismiss = { showSearchDialog = false },
            onUserClick = { user ->
                showSearchDialog = false
                onChatClick(
                    Chat(
                        id = 0,
                        userId = user.userId,
                        username = user.username,
                        avatarUrl = user.avatarUrl,
                        lastMessage = null,
                        unreadCount = 0
                    )
                )
            }
        )
    }

    // Contact Context Menu з підтримкою архіву, тегів, папок
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
                scope.launch {
                    viewModel.hideChat(chat.userId)
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.chat_hidden_toast),
                        duration = SnackbarDuration.Short
                    )
                }
                selectedChat = null
            },
            nicknameRepository = nicknameRepository,
            onArchive = { chat ->
                ChatOrganizationManager.archiveChat(chat.userId)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.chat_archived_toast),
                        duration = SnackbarDuration.Short
                    )
                }
            },
            onUnarchive = { chat ->
                ChatOrganizationManager.unarchiveChat(chat.userId)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.chat_unarchived_toast),
                        duration = SnackbarDuration.Short
                    )
                }
            },
            onManageTags = { chat ->
                tagTargetChatId = chat.userId
                tagTargetChatName = chat.username ?: chatLabel
                showManageTagsDialog = true
            },
            onMoveToFolder = { chat ->
                folderTargetChatId = chat.userId
                folderTargetChatName = chat.username ?: chatLabel
                showMoveFolderDialog = true
            }
        )
    }

    // 📇 ContactPicker для выбора контакта из телефонной книги
    if (showContactPicker) {
        com.worldmates.messenger.ui.components.ContactPicker(
            onContactSelected = { contact ->
                // Здесь можно добавить логику - например, открыть чат с контактом
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.contact_selected, contact.name),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                showContactPicker = false
            },
            onDismiss = {
                showContactPicker = false
            }
        )
    }

    // Create Story Dialog
    if (showCreateStoryDialog) {
        com.worldmates.messenger.ui.stories.CreateStoryDialog(
            onDismiss = { showCreateStoryDialog = false },
            viewModel = storyViewModel
        )
    }

    // Create Channel Story Dialog
    if (showCreateChannelStoryDialog) {
        val adminChannels = channels.filter { it.isAdmin }
        if (adminChannels.isNotEmpty()) {
            com.worldmates.messenger.ui.stories.CreateChannelStoryDialog(
                adminChannels = adminChannels,
                onDismiss = { showCreateChannelStoryDialog = false },
                viewModel = storyViewModel
            )
        } else {
            showCreateChannelStoryDialog = false
        }
    }

    // Діалог створення нової папки
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, emoji ->
                ChatOrganizationManager.addFolder(name, emoji)
                showCreateFolderDialog = false
            }
        )
    }

    // Діалог управління тегами чату
    if (showManageTagsDialog && tagTargetChatId != null) {
        ManageTagsDialog(
            chatId = tagTargetChatId!!,
            chatName = tagTargetChatName,
            onDismiss = {
                showManageTagsDialog = false
                tagTargetChatId = null
            }
        )
    }

    // Діалог переміщення чату в папку
    if (showMoveFolderDialog && folderTargetChatId != null) {
        MoveToChatFolderDialog(
            chatId = folderTargetChatId!!,
            chatName = folderTargetChatName,
            onDismiss = {
                showMoveFolderDialog = false
                folderTargetChatId = null
            }
        )
    }
    }  // Закриваємо Box з фоновим зображенням
    }  // Закриваємо ModalNavigationDrawer
}

/**
 * Вкладка зі списком чатів та pull-to-refresh
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChatListTab(
    chats: List<Chat>,
    isLoading: Boolean,
    uiStyle: UIStyle,
    onRefresh: () -> Unit,
    onChatClick: (Chat) -> Unit,
    onChatLongPress: (Chat) -> Unit
) {
    val context = LocalContext.current
    val nicknameRepository = remember { ContactNicknameRepository(context) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = onRefresh
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        if (chats.isEmpty() && !isLoading) {
            // Порожній стан
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.no_chats),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(chats, key = { it.userId }) { chat ->
                    val nickname by nicknameRepository.getNickname(chat.userId).collectAsState(initial = null)

                    // Користувач може вибрати стиль в налаштуваннях
                    when (uiStyle) {
                        UIStyle.TELEGRAM -> {
                            TelegramChatItem(
                                chat = chat,
                                nickname = nickname,
                                onClick = { onChatClick(chat) },
                                onLongPress = { onChatLongPress(chat) }
                            )
                        }
                        UIStyle.WORLDMATES -> {
                            ModernChatCard(
                                chat = chat,
                                nickname = nickname,
                                onClick = { onChatClick(chat) },
                                onLongPress = { onChatLongPress(chat) }
                            )
                        }
                    }
                }
            }
        }

        // Pull-to-refresh індикатор
        PullRefreshIndicator(
            refreshing = isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Вкладка зі списком груп та pull-to-refresh
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GroupListTab(
    groups: List<Group>,
    isLoading: Boolean,
    uiStyle: UIStyle,
    onRefresh: () -> Unit,
    onGroupClick: (Group) -> Unit,
    onGroupLongPress: (Group) -> Unit
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = onRefresh
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        if (groups.isEmpty() && !isLoading) {
            // Порожній стан
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.no_groups),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(groups, key = { it.id }) { group ->
                    // Користувач може вибрати стиль в налаштуваннях
                    when (uiStyle) {
                        UIStyle.TELEGRAM -> {
                            TelegramGroupItem(
                                group = group,
                                onClick = { onGroupClick(group) },
                                onLongPress = { onGroupLongPress(group) }
                            )
                        }
                        UIStyle.WORLDMATES -> {
                            ModernGroupCard(
                                group = group,
                                onClick = { onGroupClick(group) },
                                onLongPress = { onGroupLongPress(group) }
                            )
                        }
                    }
                }
            }
        }

        // Pull-to-refresh індикатор
        PullRefreshIndicator(
            refreshing = isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSearchDialogForChats(
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
        title = { Text(stringResource(R.string.search_users_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                // Search field
                TextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        if (it.length >= 2) {
                            coroutineScope.launch {
                                isSearching = true
                                errorMessage = null
                                try {
                                    val response = com.worldmates.messenger.network.NodeRetrofitClient.profileApi.searchUsers(
                                        query = it,
                                        limit = 20
                                    )
                                    searchResults = response.users ?: emptyList()
                                } catch (e: Exception) {
                                    errorMessage = "Помилка пошуку: ${e.message}"
                                } finally {
                                    isSearching = false
                                }
                            }
                        } else {
                            searchResults = emptyList()
                        }
                    },
                    placeholder = { Text(stringResource(R.string.enter_name_or_username)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Loading indicator
                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // Error message
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Search results
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(searchResults, key = { it.userId }) { user ->
                        ListItem(
                            headlineContent = { Text(user.name ?: user.username) },
                            supportingContent = { Text("@${user.username}") },
                            leadingContent = {
                                AsyncImage(
                                    model = user.avatarUrl,
                                    contentDescription = user.username,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            },
                            modifier = Modifier.clickable { onUserClick(user) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

/**
 * Вкладка зі списком каналів
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChannelListTab(
    channels: List<com.worldmates.messenger.data.model.Channel>,
    isLoading: Boolean,
    uiStyle: UIStyle,
    channelsViewModel: com.worldmates.messenger.ui.channels.ChannelsViewModel,
    onRefresh: () -> Unit,
    onChannelClick: (com.worldmates.messenger.data.model.Channel) -> Unit,
    liveChannelIds: Set<Long> = emptySet()
) {
    val context = LocalContext.current
    val refreshing by remember { mutableStateOf(false) }
    val searchQuery by channelsViewModel.searchQuery.collectAsState()
    val channelViewStyle = rememberChannelViewStyle()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = onRefresh
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Пошукова панель
        var localQuery by remember { mutableStateOf(searchQuery) }

        // Автоматичний пошук з затримкою після введення
        LaunchedEffect(localQuery) {
            kotlinx.coroutines.delay(500) // Затримка 500мс після введення
            if (localQuery.isEmpty()) {
                channelsViewModel.fetchChannels()
            } else if (localQuery.length >= 2) {
                channelsViewModel.searchChannels(localQuery)
            }
        }

        // Автоматичне оновлення списку каналів кожні 20 секунд (тільки якщо не в режимі пошуку)
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(20000) // 20 секунд
                if (localQuery.isEmpty()) {
                    // Тихе оновлення без показу індикатора
                    channelsViewModel.fetchChannels()
                }
            }
        }

        com.worldmates.messenger.ui.channels.ChannelSearchBar(
            searchQuery = localQuery,
            onQueryChange = { query ->
                localQuery = query
            },
            onSearch = {
                if (localQuery.isNotEmpty()) {
                    channelsViewModel.searchChannels(localQuery)
                }
            },
            onClear = {
                localQuery = ""
                channelsViewModel.fetchChannels()
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            if (channels.isEmpty() && !isLoading) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Label,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = Color.Gray.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.no_channels),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.no_channels_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = channels,
                    key = { it.id }
                ) { channel ->
                    // Спершу перевіряємо ChannelViewStyle, потім UIStyle
                    when (channelViewStyle) {
                        com.worldmates.messenger.ui.preferences.ChannelViewStyle.PREMIUM -> {
                            // Преміальний вид каналів
                            PremiumChannelListItem(
                                channel = channel,
                                onClick = { onChannelClick(channel) },
                                onSubscribeToggle = { isCurrentlySubscribed ->
                                    if (isCurrentlySubscribed) {
                                        channelsViewModel.unsubscribeChannel(
                                            channelId = channel.id,
                                            onSuccess = {
                                                android.widget.Toast.makeText(context, context.getString(R.string.unsubscribed_toast), android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { error ->
                                                android.widget.Toast.makeText(context, "Помилка: $error", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        channelsViewModel.subscribeChannel(
                                            channelId = channel.id,
                                            onSuccess = {
                                                android.widget.Toast.makeText(context, context.getString(R.string.subscribed_toast), android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { error ->
                                                android.widget.Toast.makeText(context, "Помилка: $error", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .animateItem()
                            )
                        }
                        com.worldmates.messenger.ui.preferences.ChannelViewStyle.CLASSIC -> {
                            // Класичний вид — залежить від UIStyle
                            when (uiStyle) {
                                com.worldmates.messenger.ui.preferences.UIStyle.TELEGRAM -> {
                                    com.worldmates.messenger.ui.channels.TelegramChannelItem(
                                        channel = channel,
                                        onClick = { onChannelClick(channel) },
                                        modifier = Modifier.animateItem(),
                                        isLive = channel.id in liveChannelIds
                                    )
                                }
                                com.worldmates.messenger.ui.preferences.UIStyle.WORLDMATES -> {
                                    com.worldmates.messenger.ui.channels.ChannelCard(
                                        channel = channel,
                                        onClick = { onChannelClick(channel) },
                                        onSubscribeToggle = { isCurrentlySubscribed ->
                                            if (isCurrentlySubscribed) {
                                                channelsViewModel.unsubscribeChannel(
                                                    channelId = channel.id,
                                                    onSuccess = {
                                                        android.widget.Toast.makeText(context, context.getString(R.string.unsubscribed_toast), android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    onError = { error ->
                                                        android.widget.Toast.makeText(context, "Помилка: $error", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            } else {
                                                channelsViewModel.subscribeChannel(
                                                    channelId = channel.id,
                                                    onSuccess = {
                                                        android.widget.Toast.makeText(context, context.getString(R.string.subscribed_toast), android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    onError = { error ->
                                                        android.widget.Toast.makeText(context, "Помилка: $error", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * Вкладка зі списком чатів + Stories вгорі
 */
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListTabWithStories(
    chats: List<Chat>,
    stories: List<com.worldmates.messenger.data.model.Story>,
    isLoading: Boolean,
    isLoadingStories: Boolean,
    uiStyle: UIStyle,
    onRefresh: () -> Unit,
    onChatClick: (Chat) -> Unit,
    onChatLongPress: (Chat) -> Unit = {},
    onCreateStoryClick: () -> Unit = {},
    isLoadingMore: Boolean = false,
    hasMoreChats: Boolean = false,
    onLoadMore: () -> Unit = {}
) {
    val refreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing || isLoading,
        onRefresh = onRefresh
    )
    val context = LocalContext.current
    val nicknameRepository = remember { ContactNicknameRepository(context) }
    val listState = rememberLazyListState()

    // Infinite scroll: коли останній видимий елемент — передостанній у списку — завантажуємо ще
    val shouldLoadMore = remember {
        androidx.compose.runtime.derivedStateOf {
            val layoutInfo   = listState.layoutInfo
            val totalItems   = layoutInfo.totalItemsCount
            val lastVisible  = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // triggerThreshold: 3 елементи до кінця
            lastVisible >= totalItems - 3 && totalItems > 1
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && hasMoreChats && !isLoadingMore && !isLoading) {
            onLoadMore()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            state    = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Stories row вгорі
            item {
                com.worldmates.messenger.ui.stories.PersonalStoriesRow(
                    stories = stories,
                    onCreateStoryClick = onCreateStoryClick
                )
            }

            // Чати
            items(chats, key = { it.userId }) { chat ->
                val nickname by nicknameRepository.getNickname(chat.userId).collectAsState(initial = null)

                Column {
                    when (uiStyle) {
                        UIStyle.WORLDMATES -> {
                            ModernChatCard(
                                chat = chat,
                                nickname = nickname,
                                onClick = { onChatClick(chat) },
                                onLongPress = { onChatLongPress(chat) }
                            )
                        }
                        UIStyle.TELEGRAM -> {
                            TelegramChatItem(
                                chat = chat,
                                nickname = nickname,
                                onClick = { onChatClick(chat) },
                                onLongPress = { onChatLongPress(chat) }
                            )
                        }
                    }
                    // Теги чату (якщо є)
                    ChatTagsRow(
                        chatId = chat.userId,
                        modifier = Modifier.padding(start = 76.dp, bottom = 2.dp)
                    )
                }
            }

            // Індикатор завантаження наступної сторінки
            if (isLoadingMore) {
                item(key = "loading_more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier  = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text  = stringResource(R.string.chats_loading_more),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else if (!hasMoreChats && chats.isNotEmpty()) {
                // Підказка що всі чати завантажено
                item(key = "all_loaded") {
                    Text(
                        text     = stringResource(R.string.chats_all_loaded),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        PullRefreshIndicator(
            refreshing = refreshing || isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Вкладка з каналами + channel stories (окремі від особистих)
 */
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChannelListTabWithStories(
    channels: List<com.worldmates.messenger.data.model.Channel>,
    stories: List<com.worldmates.messenger.data.model.Story>,
    isLoading: Boolean,
    isLoadingStories: Boolean,
    uiStyle: UIStyle,
    channelsViewModel: com.worldmates.messenger.ui.channels.ChannelsViewModel,
    onRefresh: () -> Unit,
    onChannelClick: (com.worldmates.messenger.data.model.Channel) -> Unit,
    onCreateChannelStoryClick: () -> Unit = {},
    liveChannelIds: Set<Long> = emptySet()
) {
    val refreshing by remember { mutableStateOf(false) }
    val channelViewStyle = rememberChannelViewStyle()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing || isLoading,
        onRefresh = onRefresh
    )

    // Канали, де поточний користувач — адмін
    val adminChannelIds = channels.filter { it.isAdmin }.map { it.id }

    // No local filtering — channels are shown directly

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        if (channels.isEmpty() && !isLoading) {
            // Premium empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "No channels yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Subscribe to channels to see them here\nor create your own",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Channel stories row
                if (stories.isNotEmpty() || adminChannelIds.isNotEmpty()) {
                    item {
                        com.worldmates.messenger.ui.stories.ChannelStoriesRow(
                            stories = stories,
                            adminChannelIds = adminChannelIds,
                            onCreateClick = onCreateChannelStoryClick
                        )
                    }
                }

                // Channel list
                items(channels, key = { it.id }) { channel ->
                    when (channelViewStyle) {
                        com.worldmates.messenger.ui.preferences.ChannelViewStyle.PREMIUM -> {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            PremiumChannelListItem(
                                channel = channel,
                                onClick = { onChannelClick(channel) },
                                onSubscribeToggle = { isCurrentlySubscribed ->
                                    if (isCurrentlySubscribed) {
                                        channelsViewModel.unsubscribeChannel(
                                            channelId = channel.id,
                                            onSuccess = {
                                                android.widget.Toast.makeText(context, context.getString(R.string.unsubscribed_toast), android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { error ->
                                                android.widget.Toast.makeText(context, "Помилка: $error", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        channelsViewModel.subscribeChannel(
                                            channelId = channel.id,
                                            onSuccess = {
                                                android.widget.Toast.makeText(context, context.getString(R.string.subscribed_toast), android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { error ->
                                                android.widget.Toast.makeText(context, "Помилка: $error", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .animateItem()
                            )
                        }
                        com.worldmates.messenger.ui.preferences.ChannelViewStyle.CLASSIC -> {
                            com.worldmates.messenger.ui.channels.TelegramChannelItem(
                                channel = channel,
                                onClick = { onChannelClick(channel) },
                                modifier = Modifier.animateItem(),
                                isLive = channel.id in liveChannelIds
                            )
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = refreshing || isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
