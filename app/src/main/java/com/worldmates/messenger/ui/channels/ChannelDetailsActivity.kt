package com.worldmates.messenger.ui.channels

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import com.worldmates.messenger.ui.preferences.ChannelViewStyle
import com.worldmates.messenger.ui.preferences.rememberChannelViewStyle
import androidx.compose.material.icons.outlined.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.Channel
import com.worldmates.messenger.data.model.ChannelPost
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.ui.theme.BackgroundImage
import com.worldmates.messenger.ui.theme.rememberThemeState
import com.worldmates.messenger.ui.groups.FormattingSettingsPanel
import com.worldmates.messenger.ui.groups.GroupFormattingPermissions
import com.worldmates.messenger.util.toFullMediaUrl
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worldmates.messenger.R
import com.worldmates.messenger.utils.LanguageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke

/**
 * Активність для перегляду деталей каналу та його постів
 */
class ChannelDetailsActivity : AppCompatActivity() {

    private lateinit var channelsViewModel: ChannelsViewModel
    private lateinit var detailsViewModel: ChannelDetailsViewModel
    private var channelId: Long = 0

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Отримуємо channelId з Intent
        channelId = intent.getLongExtra("channel_id", 0)
        if (channelId == 0L) {
            Toast.makeText(this, getString(R.string.error_channel_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Ініціалізуємо ThemeManager
        ThemeManager.initialize(this)

        // Ініціалізуємо ViewModels
        channelsViewModel = ViewModelProvider(this).get(ChannelsViewModel::class.java)
        detailsViewModel = ViewModelProvider(this).get(ChannelDetailsViewModel::class.java)

        setContent {
            WorldMatesThemedApp {
                ChannelDetailsScreen(
                    channelId = channelId,
                    channelsViewModel = channelsViewModel,
                    detailsViewModel = detailsViewModel,
                    onBackPressed = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Завантажуємо дані каналу та пости
        channelsViewModel.refreshChannel(channelId)
        detailsViewModel.loadChannelDetails(channelId)
        detailsViewModel.loadChannelPosts(channelId)
        detailsViewModel.loadRecordings(channelId)
        // Connect Socket.IO for real-time updates
        detailsViewModel.connectSocket(this, channelId)
    }

    override fun onPause() {
        super.onPause()
        // Disconnect Socket.IO when leaving
        detailsViewModel.disconnectSocket()
    }
}

/**
 * Екран деталей каналу з постами
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChannelDetailsScreen(
    channelId: Long,
    channelsViewModel: ChannelsViewModel,
    detailsViewModel: ChannelDetailsViewModel,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val channelViewStyle = rememberChannelViewStyle()

    // States from ViewModels
    val detailsChannel by detailsViewModel.channel.collectAsState()
    val subscribedChannels by channelsViewModel.subscribedChannels.collectAsState()
    val allChannels by channelsViewModel.channelList.collectAsState()
    val posts by detailsViewModel.posts.collectAsState()
    val isLoadingPosts by detailsViewModel.isLoading.collectAsState()
    val error by detailsViewModel.error.collectAsState()
    val liveChannelIds by LiveChannelTracker.liveChannelIds.collectAsState()
    val isChannelLive = channelId in liveChannelIds

    // Знаходимо канал — primary source: detailsViewModel (fresh from API)
    val channel = detailsChannel
        ?: subscribedChannels.find { it.id == channelId }
        ?: allChannels.find { it.id == channelId }

    // UI States
    var showMediaViewer by remember { mutableStateOf(false) }
    var mediaViewerUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var mediaViewerInitialPage by remember { mutableStateOf(0) }
    var showVideoPlayer by remember { mutableStateOf(false) }
    var videoPlayerUrl by remember { mutableStateOf("") }
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var showCreatePollDialog by remember { mutableStateOf(false) }
    var showChangeAvatarDialog by remember { mutableStateOf(false) }
    var showSubscribersDialog by remember { mutableStateOf(false) }
    var showAddMembersDialog by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showPostOptions by remember { mutableStateOf(false) }
    var showEditPostDialog by remember { mutableStateOf(false) }
    var showPostAnalyticsSheet by remember { mutableStateOf(false) }
    var showStatisticsDialog by remember { mutableStateOf(false) }
    var showAdminsDialog by remember { mutableStateOf(false) }
    var showEditChannelDialog by remember { mutableStateOf(false) }
    var showChannelMenuDialog by remember { mutableStateOf(false) }
    var showChannelSettingsDialog by remember { mutableStateOf(false) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var showSubGroupsDialog by remember { mutableStateOf(false) }
    var showPostDetailDialog by remember { mutableStateOf(false) }
    var selectedPostForOptions by remember { mutableStateOf<ChannelPost?>(null) }
    var selectedPostForDetail by remember { mutableStateOf<ChannelPost?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    // 📝 Formatting settings panel state
    var showFormattingSettings by remember { mutableStateOf(false) }
    var formattingPermissions by remember {
        mutableStateOf(detailsViewModel.loadFormattingPermissions(channelId))
    }

    // Завантажуємо підписників, коментарі, статистику, адмінів
    val subscribers by detailsViewModel.subscribers.collectAsState()
    val comments by detailsViewModel.comments.collectAsState()
    val selectedPost by detailsViewModel.selectedPost.collectAsState()
    val isLoadingComments by detailsViewModel.isLoadingComments.collectAsState()
    val statistics by detailsViewModel.statistics.collectAsState()
    val admins by detailsViewModel.admins.collectAsState()
    val postAnalytics by detailsViewModel.postAnalytics.collectAsState()
    val isLoadingPostAnalytics by detailsViewModel.isLoadingPostAnalytics.collectAsState()
    val recordings by detailsViewModel.recordings.collectAsState()

    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = {
            refreshing = true
            detailsViewModel.loadChannelPosts(channelId)
            channelsViewModel.refreshChannel(channelId)
            refreshing = false
        }
    )

    // Saved messages state
    val savedMessages by com.worldmates.messenger.data.SavedMessagesManager.savedMessages.collectAsState()

    // URI для вибраного зображення аватара
    var selectedAvatarUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Лаунчер для вибору з галереї
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        uri?.let {
            selectedAvatarUri = it
            channelsViewModel.uploadChannelAvatar(
                channelId = channelId,
                imageUri = it,
                context = context,
                onSuccess = {
                    Toast.makeText(context, context.getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                    showChangeAvatarDialog = false
                    // Refresh channel details to pick up new avatar
                    detailsViewModel.loadChannelDetails(channelId)
                },
                onError = { error ->
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // URI для фото з камери
    val cameraUri = remember {
        android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.TITLE, "channel_avatar_${System.currentTimeMillis()}")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }.let {
            context.contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                it
            )
        }
    }

    // Лаунчер для камери
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            channelsViewModel.uploadChannelAvatar(
                channelId = channelId,
                imageUri = cameraUri,
                context = context,
                onSuccess = {
                    Toast.makeText(context, context.getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                    showChangeAvatarDialog = false
                    // Refresh channel details to pick up new avatar
                    detailsViewModel.loadChannelDetails(channelId)
                },
                onError = { error ->
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // Permission launcher for the avatar camera
    val requestAvatarCameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraUri?.let { cameraLauncher.launch(it) }
        else Toast.makeText(context, context.getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
    }

    // Показуємо помилки через Toast
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    // Fallback polling: Socket.IO handles real-time, REST polls less frequently as backup
    LaunchedEffect(channelId) {
        while (true) {
            kotlinx.coroutines.delay(60000) // 60 seconds (Socket.IO provides real-time)
            detailsViewModel.loadChannelPosts(channelId)
        }
    }

    // Отримуємо стан теми для фону
    val themeState = rememberThemeState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Застосовуємо фон з налаштувань тем
        BackgroundImage(
            backgroundImageUri = themeState.backgroundImageUri,
            presetBackgroundId = themeState.presetBackgroundId
        )

        Scaffold(
            containerColor = Color.Transparent, // Прозорий фон щоб був видно BackgroundImage
            floatingActionButton = {
                if (channel?.isAdmin == true) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = { showCreatePollDialog = true },
                            containerColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Icon(Icons.Default.HowToVote, contentDescription = "Create Poll")
                        }
                        if (channelViewStyle == ChannelViewStyle.PREMIUM) {
                            PremiumFAB(
                                onClick = { showCreatePostDialog = true }
                            )
                        } else {
                            FloatingActionButton(
                                onClick = { showCreatePostDialog = true },
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Create Post")
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (channel == null && !isLoadingPosts) {
                    // Канал не знайдено (показуємо тільки після завантаження)
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.error_channel_not_found),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackPressed) {
                            Text(stringResource(R.string.back))
                        }
                    }
                } else if (channel == null && isLoadingPosts) {
                    // Показуємо індикатор завантаження поки завантажується канал
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (channel != null) {
                    // Відображаємо канал
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .pullRefresh(pullRefreshState)
                    ) {
                        // Шапка каналу — залежить від стилю
                        item {
                            when (channelViewStyle) {
                                ChannelViewStyle.PREMIUM -> {
                                    PremiumChannelHeader(
                                        channel = channel,
                                        onBackClick = onBackPressed,
                                        onSettingsClick = if (channel.isAdmin) {
                                            { showChannelMenuDialog = true }
                                        } else null,
                                        onSubscribersClick = {
                                            detailsViewModel.loadSubscribers(channelId)
                                            showSubscribersDialog = true
                                        },
                                        onAddMembersClick = if (channel.isAdmin) {
                                            { showAddMembersDialog = true }
                                        } else null,
                                        onAvatarClick = if (channel.isAdmin) {
                                            { showChangeAvatarDialog = true }
                                        } else null
                                    )
                                }
                                ChannelViewStyle.CLASSIC -> {
                                    ChannelHeader(
                                        channel = channel,
                                        onBackClick = onBackPressed,
                                        onSettingsClick = if (channel.isAdmin) {
                                            { showChannelMenuDialog = true }
                                        } else null,
                                        onSubscribersClick = {
                                            detailsViewModel.loadSubscribers(channelId)
                                            showSubscribersDialog = true
                                        },
                                        onAddMembersClick = if (channel.isAdmin) {
                                            { showAddMembersDialog = true }
                                        } else null,
                                        onAvatarClick = if (channel.isAdmin) {
                                            { showChangeAvatarDialog = true }
                                        } else null
                                    )
                                }
                            }
                        }

                        // Кнопка підписки (якщо не адмін)
                        if (!channel.isAdmin) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    val subscribeToggle: () -> Unit = {
                                        if (channel.isSubscribed) {
                                            channelsViewModel.unsubscribeChannel(
                                                channelId = channelId,
                                                onSuccess = {
                                                    Toast.makeText(context, context.getString(R.string.unsubscribe_success), Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { error ->
                                                    Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            channelsViewModel.subscribeChannel(
                                                channelId = channelId,
                                                onSuccess = {
                                                    Toast.makeText(context, context.getString(R.string.subscribe_success), Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { error ->
                                                    Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                    when (channelViewStyle) {
                                        ChannelViewStyle.PREMIUM -> PremiumSubscribeButton(
                                            isSubscribed = channel.isSubscribed,
                                            onToggle = subscribeToggle,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        ChannelViewStyle.CLASSIC -> ModernSubscribeButton(
                                            isSubscribed = channel.isSubscribed,
                                            onToggle = subscribeToggle
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // LIVE pinned banner — shown when the channel is currently streaming
                        if (isChannelLive) {
                            item(key = "live_banner") {
                                ChannelLiveBanner(
                                    isAdmin = channel.isAdmin,
                                    channelId = channelId,
                                    channelName = channel.name,
                                    isPremium = channel.isPrivate
                                )
                            }
                        }

                        // Recordings archive — shown when there are past streams
                        if (recordings.isNotEmpty()) {
                            item(key = "recordings_header") {
                                ChannelRecordingsSection(
                                    recordings = recordings,
                                    channelId = channelId,
                                    onPlayRecording = { url ->
                                        videoPlayerUrl = url
                                        showVideoPlayer = true
                                    }
                                )
                            }
                        }

                        // Заголовок секції постів
                        item {
                            if (channelViewStyle == ChannelViewStyle.PREMIUM) {
                                PremiumSectionHeader(
                                    title = stringResource(R.string.channel_detail_posts).replaceFirstChar { it.uppercase() },
                                    count = posts.size
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.ch_posts),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (posts.isNotEmpty()) {
                                        Text(
                                            text = "${posts.size}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Список постів — залежить від стилю
                        if (posts.isEmpty() && !isLoadingPosts) {
                            item {
                                if (channelViewStyle == ChannelViewStyle.PREMIUM) {
                                    PremiumEmptyState(
                                        icon = Icons.Outlined.Article,
                                        title = stringResource(R.string.channel_detail_no_posts),
                                        subtitle = if (channel.isAdmin) stringResource(R.string.channel_detail_create_first) else null,
                                        action = if (channel.isAdmin) {
                                            {
                                                Button(
                                                    onClick = { showCreatePostDialog = true },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary
                                                    )
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(stringResource(R.string.channel_detail_create_post))
                                                }
                                            }
                                        } else null
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(48.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Outlined.Article,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            stringResource(R.string.ch_no_posts),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = posts.sortedWith(
                                    compareByDescending<ChannelPost> { it.isPinned }
                                        .thenByDescending { it.createdTime }
                                ),
                                key = { it.id }
                            ) { post ->
                                val onPostClickHandler: () -> Unit = {
                                    selectedPostForDetail = post
                                    detailsViewModel.loadComments(post.id)
                                    detailsViewModel.registerPostView(
                                        postId = post.id,
                                        onSuccess = { /* View registered */ },
                                        onError = { /* Silent fail */ }
                                    )
                                    showPostDetailDialog = true
                                }
                                val onReactionClickHandler: (String) -> Unit = { emoji ->
                                    detailsViewModel.addPostReaction(
                                        postId = post.id,
                                        emoji = emoji,
                                        onSuccess = {
                                            Toast.makeText(context, context.getString(R.string.ch_reaction_added), Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { error ->
                                            Toast.makeText(context, context.getString(R.string.ch_error_prefix, error), Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                val onCommentsClickHandler: () -> Unit = {
                                    detailsViewModel.loadComments(post.id)
                                    showCommentsSheet = true
                                }
                                val onShareClickHandler: () -> Unit = {
                                    val shareUrl = "https://worldmates.club:449/share/channel/${channelId}/post/${post.id}"
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
                                        type = "text/plain"
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, context.getString(R.string.ch_share_post))
                                    context.startActivity(shareIntent)
                                }
                                val onMoreClickHandler: () -> Unit = {
                                    selectedPostForOptions = post
                                    showPostOptions = true
                                }
                                val isPostSaved = savedMessages.any {
                                    it.messageId == post.id && it.chatType == "channel"
                                }
                                val onSaveClickHandler: () -> Unit = {
                                    if (isPostSaved) {
                                        com.worldmates.messenger.data.SavedMessagesManager.removeSaved(post.id, "channel")
                                    } else {
                                        com.worldmates.messenger.data.SavedMessagesManager.saveMessage(
                                            com.worldmates.messenger.data.SavedMessageItem(
                                                messageId   = post.id,
                                                chatType    = "channel",
                                                chatId      = channelId,
                                                chatName    = channel?.name ?: "",
                                                senderName  = channel?.name ?: "",
                                                text        = post.text,
                                                mediaUrl    = post.media?.firstOrNull()?.url,
                                                mediaType   = post.media?.firstOrNull()?.type,
                                                originalTime = post.createdTime
                                            )
                                        )
                                    }
                                }
                                val onMediaClickHandler: (Int) -> Unit = { index ->
                                    val allMedia = post.media ?: emptyList()
                                    val clicked = allMedia.getOrNull(index)
                                    if (clicked?.type == "video") {
                                        val url = clicked.url.toFullMediaUrl()
                                        if (!url.isNullOrBlank()) {
                                            videoPlayerUrl = url
                                            showVideoPlayer = true
                                        }
                                    } else {
                                        val imageUrls = allMedia
                                            .filter { it.type == "image" || it.type.isNullOrBlank() }
                                            .map { it.url.toFullMediaUrl() }
                                            .filterNotNull()
                                        if (imageUrls.isNotEmpty()) {
                                            mediaViewerUrls = imageUrls
                                            mediaViewerInitialPage = index.coerceIn(0, imageUrls.size - 1)
                                            showMediaViewer = true
                                        }
                                    }
                                }

                                val onPollVoteHandler: (Long, Long) -> Unit = { pollId, optionId ->
                                    detailsViewModel.voteOnChannelPoll(pollId, optionId)
                                }

                                when (channelViewStyle) {
                                    ChannelViewStyle.PREMIUM -> {
                                        PremiumPostCard(
                                            post = post,
                                            channelName = channel.name,
                                            channelAvatarUrl = channel.avatarUrl,
                                            channelCustomization = channel.premiumCustomization,
                                            onPostClick = onPostClickHandler,
                                            onReactionClick = onReactionClickHandler,
                                            onCommentsClick = onCommentsClickHandler,
                                            onShareClick = onShareClickHandler,
                                            onMoreClick = onMoreClickHandler,
                                            onMediaClick = onMediaClickHandler,
                                            onPollVote = onPollVoteHandler,
                                            canEdit = channel.isAdmin,
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                    ChannelViewStyle.CLASSIC -> {
                                        ChannelPostCard(
                                            post = post,
                                            channelName = channel.name,
                                            channelAvatarUrl = channel.avatarUrl,
                                            onPostClick = onPostClickHandler,
                                            onReactionClick = onReactionClickHandler,
                                            onCommentsClick = onCommentsClickHandler,
                                            onShareClick = onShareClickHandler,
                                            onSaveClick = onSaveClickHandler,
                                            isSaved = isPostSaved,
                                            onMoreClick = onMoreClickHandler,
                                            onMediaClick = onMediaClickHandler,
                                            onPollVote = onPollVoteHandler,
                                            canEdit = channel.isAdmin,
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        }

                        if (isLoadingPosts && posts.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Нижній відступ
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }

                    // Pull-to-refresh індикатор
                    PullRefreshIndicator(
                        refreshing = refreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }

        // Діалог створення поста (для адмінів)
        if (showCreatePostDialog && channel?.isAdmin == true) {
            CreatePostDialog(
                channelId = channelId,
                onDismiss = { showCreatePostDialog = false },
                onCreate = { text, mediaList ->
                    val media = mediaList?.takeIf { it.isNotEmpty() }

                    detailsViewModel.createPost(
                        channelId = channelId,
                        text = text,
                        media = media,
                        onSuccess = {
                            Toast.makeText(context, context.getString(R.string.ch_post_created), Toast.LENGTH_SHORT).show()
                            detailsViewModel.loadChannelPosts(channelId)
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    )
                    showCreatePostDialog = false
                }
            )
        }

        // Діалог створення опитування (для адмінів)
        if (showCreatePollDialog && channel?.isAdmin == true) {
            CreateChannelPollDialog(
                channelId = channelId,
                onDismiss = { showCreatePollDialog = false },
                onCreate = { question, options, isAnonymous, allowsMultiple ->
                    detailsViewModel.createChannelPoll(
                        channelId = channelId,
                        question = question,
                        options = options,
                        isAnonymous = isAnonymous,
                        allowsMultiple = allowsMultiple,
                        onSuccess = {
                            Toast.makeText(context, context.getString(R.string.ch_poll_created), Toast.LENGTH_SHORT).show()
                            detailsViewModel.loadChannelPosts(channelId)
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    )
                    showCreatePollDialog = false
                }
            )
        }

        // Діалог підписників
        if (showSubscribersDialog) {
            SubscribersDialog(
                subscribers = subscribers,
                onDismiss = { showSubscribersDialog = false }
            )
        }

        // Діалог додавання учасників
        if (showAddMembersDialog && channel?.isAdmin == true) {
            AddMembersDialog(
                channelId = channelId,
                channelsViewModel = channelsViewModel,
                onDismiss = { showAddMembersDialog = false }
            )
        }

        // Bottom sheet коментарів
        if (showCommentsSheet && selectedPost != null) {
            CommentsBottomSheet(
                post = selectedPost,
                comments = comments,
                isLoading = isLoadingComments,
                currentUserId = UserSession.userId ?: 0L,
                isAdmin = channel?.isAdmin ?: false,
                onDismiss = { showCommentsSheet = false },
                onAddComment = { text ->
                    selectedPost?.let { post ->
                        detailsViewModel.addComment(
                            postId = post.id,
                            text = text,
                            onSuccess = {
                                Toast.makeText(context, context.getString(R.string.comment_added), Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onAddCommentWithReply = { text, replyToId ->
                    selectedPost?.let { post ->
                        detailsViewModel.addComment(
                            postId = post.id,
                            text = text,
                            replyToId = replyToId,
                            onSuccess = {
                                Toast.makeText(context, context.getString(R.string.comment_added), Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onDeleteComment = { commentId ->
                    selectedPost?.let { post ->
                        detailsViewModel.deleteComment(
                            commentId = commentId,
                            postId = post.id,
                            onSuccess = {
                                Toast.makeText(context, context.getString(R.string.comment_deleted), Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onCommentReaction = { commentId, emoji ->
                    detailsViewModel.addCommentReaction(
                        commentId = commentId,
                        emoji = emoji,
                        onSuccess = {
                            Toast.makeText(context, context.getString(R.string.reaction_added), Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }

        // Діалог детального перегляду поста
        if (showPostDetailDialog && selectedPostForDetail != null) {
            PostDetailDialog(
                post = selectedPostForDetail!!,
                comments = comments,
                isLoadingComments = isLoadingComments,
                currentUserId = UserSession.userId ?: 0L,
                isAdmin = channel?.isAdmin ?: false,
                onDismiss = { showPostDetailDialog = false },
                onReactionClick = { emoji ->
                    selectedPostForDetail?.let { post ->
                        detailsViewModel.addPostReaction(
                            postId = post.id,
                            emoji = emoji,
                            onSuccess = {
                                Toast.makeText(context, context.getString(R.string.reaction_added), Toast.LENGTH_SHORT).show()
                                detailsViewModel.loadChannelPosts(channelId)
                            },
                            onError = { error ->
                                Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onAddComment = { text ->
                    selectedPostForDetail?.let { post ->
                        detailsViewModel.addComment(
                            postId = post.id,
                            text = text,
                            onSuccess = {
                                Toast.makeText(context, context.getString(R.string.comment_added), Toast.LENGTH_SHORT).show()
                                detailsViewModel.loadComments(post.id)
                            },
                            onError = { error ->
                                Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onAddCommentWithReply = { text, replyToId ->
                    selectedPostForDetail?.let { post ->
                        detailsViewModel.addComment(
                            postId = post.id,
                            text = text,
                            replyToId = replyToId,
                            onSuccess = {
                                Toast.makeText(context, context.getString(R.string.comment_added), Toast.LENGTH_SHORT).show()
                                detailsViewModel.loadComments(post.id)
                            },
                            onError = { error ->
                                Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onDeleteComment = { commentId ->
                    selectedPostForDetail?.let { post ->
                        detailsViewModel.deleteComment(
                            commentId = commentId,
                            postId = post.id,
                            onSuccess = {
                                Toast.makeText(context, context.getString(R.string.comment_deleted), Toast.LENGTH_SHORT).show()
                                detailsViewModel.loadComments(post.id)
                            },
                            onError = { error ->
                                Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onCommentReaction = { commentId, emoji ->
                    detailsViewModel.addCommentReaction(
                        commentId = commentId,
                        emoji = emoji,
                        onSuccess = {
                            Toast.makeText(context, context.getString(R.string.reaction_added), Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }

        // Bottom sheet опцій поста
        if (showPostOptions && selectedPostForOptions != null) {
            PostOptionsBottomSheet(
                post = selectedPostForOptions!!,
                onDismiss = { showPostOptions = false },
                onPinClick = {
                    selectedPostForOptions?.let { post ->
                        detailsViewModel.togglePinPost(
                            postId = post.id,
                            isPinned = post.isPinned,
                            onSuccess = {
                                val message = if (post.isPinned) "Пост відкріплено" else "Пост закріплено"
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onEditClick = {
                    showEditPostDialog = true
                },
                onDeleteClick = {
                    selectedPostForOptions?.let { post ->
                        detailsViewModel.deletePost(
                            postId = post.id,
                            onSuccess = {
                                Toast.makeText(context, "Пост видалено", Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                isSaved = selectedPostForOptions?.let { p ->
                    savedMessages.any { it.messageId == p.id && it.chatType == "channel" }
                } ?: false,
                onSaveClick = {
                    selectedPostForOptions?.let { post ->
                        val alreadySaved = savedMessages.any { it.messageId == post.id && it.chatType == "channel" }
                        if (alreadySaved) {
                            com.worldmates.messenger.data.SavedMessagesManager.removeSaved(post.id, "channel")
                        } else {
                            com.worldmates.messenger.data.SavedMessagesManager.saveMessage(
                                com.worldmates.messenger.data.SavedMessageItem(
                                    messageId    = post.id,
                                    chatType     = "channel",
                                    chatId       = channelId,
                                    chatName     = channel?.name ?: "",
                                    senderName   = channel?.name ?: "",
                                    text         = post.text,
                                    mediaUrl     = post.media?.firstOrNull()?.url,
                                    mediaType    = post.media?.firstOrNull()?.type,
                                    originalTime = post.createdTime
                                )
                            )
                        }
                    }
                },
                onAnalyticsClick = if (channel?.isAdmin == true) ({
                    selectedPostForOptions?.let { post ->
                        detailsViewModel.loadPostAnalytics(channelId, post.id)
                        showPostAnalyticsSheet = true
                    }
                }) else null
            )
        }

        // Per-post analytics sheet
        if (showPostAnalyticsSheet) {
            PostAnalyticsSheet(
                analytics = postAnalytics,
                isLoading = isLoadingPostAnalytics,
                onDismiss = {
                    showPostAnalyticsSheet = false
                    detailsViewModel.clearPostAnalytics()
                }
            )
        }

        // Діалог редагування поста
        if (showEditPostDialog && selectedPostForOptions != null) {
            EditPostDialog(
                post = selectedPostForOptions!!,
                onDismiss = {
                    showEditPostDialog = false
                    selectedPostForOptions = null
                },
                onSave = { newText ->
                    selectedPostForOptions?.let { post ->
                        detailsViewModel.updatePost(
                            postId = post.id,
                            text = newText,
                            onSuccess = {
                                Toast.makeText(context, "Пост оновлено!", Toast.LENGTH_SHORT).show()
                                showEditPostDialog = false
                                selectedPostForOptions = null
                            },
                            onError = { error ->
                                Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            )
        }

        // Діалог статистики (тільки для адмінів)
        if (showStatisticsDialog && channel?.isAdmin == true) {
            StatisticsDialog(
                statistics = statistics,
                onDismiss = { showStatisticsDialog = false }
            )
        }

        // Діалог управління адмінами (тільки для власника)
        if (showAdminsDialog && channel?.isAdmin == true) {
            ManageAdminsDialog(
                admins = admins,
                onDismiss = { showAdminsDialog = false },
                onAddAdmin = { searchText, role ->
                    detailsViewModel.addChannelAdmin(
                        channelId = channelId,
                        userSearch = searchText,
                        role = role,
                        onSuccess = {
                            Toast.makeText(context, "Адміністратора додано!", Toast.LENGTH_SHORT).show()
                            detailsViewModel.loadChannelDetails(channelId)
                        },
                        onError = { error ->
                            Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onRemoveAdmin = { userId ->
                    detailsViewModel.removeChannelAdmin(
                        channelId = channelId,
                        userId = userId,
                        onSuccess = {
                            Toast.makeText(context, "Адміністратора видалено", Toast.LENGTH_SHORT).show()
                            detailsViewModel.loadChannelDetails(channelId)
                        },
                        onError = { error ->
                            Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }

        // Premium channel management menu (bottom sheet style dialog)
        if (showChannelMenuDialog && channel?.isAdmin == true) {
            AlertDialog(
                onDismissRequest = { showChannelMenuDialog = false },
                title = {
                    Column {
                        Text(
                            stringResource(R.string.channel_management),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            channel.name,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Edit info
                        PremiumMenuItem(
                            icon = Icons.Default.Edit,
                            title = stringResource(R.string.edit),
                            subtitle = stringResource(R.string.channel_edit_info_subtitle),
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = {
                                showChannelMenuDialog = false
                                showEditChannelDialog = true
                            }
                        )

                        // Settings
                        PremiumMenuItem(
                            icon = Icons.Default.Settings,
                            title = stringResource(R.string.settings),
                            subtitle = stringResource(R.string.channel_settings_subtitle),
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            onClick = {
                                showChannelMenuDialog = false
                                showChannelSettingsDialog = true
                            }
                        )

                        // Statistics
                        PremiumMenuItem(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.channel_statistics),
                            subtitle = stringResource(R.string.channel_stats_subtitle),
                            iconTint = Color(0xFF00C853),
                            onClick = {
                                showChannelMenuDialog = false
                                detailsViewModel.loadStatistics(channelId)
                                showStatisticsDialog = true
                            }
                        )

                        if (channel.isAdmin) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )

                            // Administrators
                            PremiumMenuItem(
                                icon = Icons.Default.AccountCircle,
                                title = stringResource(R.string.admins),
                                subtitle = stringResource(R.string.channel_admins_subtitle),
                                iconTint = Color(0xFFFF9800),
                                onClick = {
                                    showChannelMenuDialog = false
                                    detailsViewModel.loadChannelDetails(channelId)
                                    showAdminsDialog = true
                                }
                            )

                            // Sub-groups (only for private channels with premium)
                            if (channel.isPrivate) {
                                PremiumMenuItem(
                                    icon = Icons.Default.Group,
                                    title = stringResource(R.string.channel_subgroups_title),
                                    subtitle = stringResource(R.string.channel_subgroups_subtitle),
                                    iconTint = Color(0xFF7C4DFF),
                                    onClick = {
                                        showChannelMenuDialog = false
                                        detailsViewModel.loadSubGroups(channelId)
                                        showSubGroupsDialog = true
                                    }
                                )
                            }

                            // Formatting
                            PremiumMenuItem(
                                icon = Icons.Outlined.TextFormat,
                                title = stringResource(R.string.message_formatting_label),
                                subtitle = stringResource(R.string.message_formatting_subtitle),
                                iconTint = MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    showChannelMenuDialog = false
                                    showFormattingSettings = true
                                }
                            )

                            // Admin Panel
                            PremiumMenuItem(
                                icon = Icons.Outlined.Dashboard,
                                title = stringResource(R.string.admin_panel),
                                subtitle = stringResource(R.string.admin_panel_subtitle),
                                iconTint = MaterialTheme.colorScheme.error,
                                onClick = {
                                    showChannelMenuDialog = false
                                    context.startActivity(
                                        Intent(context, ChannelAdminPanelActivity::class.java).apply {
                                            putExtra("channel_id", channelId)
                                        }
                                    )
                                }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            showChannelMenuDialog = false
                            showBackupSheet = true
                        }) { Text(stringResource(R.string.channel_backup_export)) }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showChannelMenuDialog = false }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            )
        }

        // Backup export sheet
        if (showBackupSheet && channel?.isAdmin == true) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showBackupSheet = false },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                    ChannelBackupCard(
                        channelId   = channelId,
                        channelName = channel.name
                    )
                }
            }
        }

        // Діалог редагування інформації про канал
        if (showEditChannelDialog && channel?.isAdmin == true) {
            EditChannelInfoDialog(
                channel = channel,
                onDismiss = { showEditChannelDialog = false },
                onSave = { name, description, username ->
                    detailsViewModel.updateChannel(
                        channelId = channelId,
                        name = name,
                        description = description,
                        username = username,
                        onSuccess = { updatedChannel ->
                            Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                            showEditChannelDialog = false
                            detailsViewModel.loadChannelDetails(channelId)
                        },
                        onError = { error ->
                            Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        // Діалог налаштувань каналу
        if (showChannelSettingsDialog && channel?.isAdmin == true) {
            ChannelSettingsDialog(
                currentSettings = channel.settings,
                onDismiss = { showChannelSettingsDialog = false },
                onSave = { settings ->
                    detailsViewModel.updateChannelSettings(
                        channelId = channelId,
                        settings = settings,
                        onSuccess = {
                            Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                            showChannelSettingsDialog = false
                            detailsViewModel.loadChannelDetails(channelId)
                        },
                        onError = { error ->
                            Toast.makeText(context, context.getString(R.string.error_generic_msg, error), Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        // Діалог зміни аватара каналу
        if (showChangeAvatarDialog) {
            ChannelAvatarDialog(
                onDismiss = { showChangeAvatarDialog = false },
                onCameraClick = {
                    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        cameraUri?.let { cameraLauncher.launch(it) }
                    } else {
                        requestAvatarCameraPermission.launch(android.Manifest.permission.CAMERA)
                    }
                },
                onGalleryClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }

        // 📝 Formatting Settings Panel
        if (showFormattingSettings) {
            FormattingSettingsPanel(
                currentSettings = formattingPermissions,
                isChannel = true,
                onSettingsChange = { newSettings ->
                    formattingPermissions = newSettings
                    detailsViewModel.saveFormattingPermissions(
                        channelId = channelId,
                        permissions = newSettings,
                        onSuccess = {
                            Toast.makeText(
                                context,
                                context.getString(R.string.formatting_settings_saved),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onError = { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.error_save_msg, error),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                },
                onDismiss = { showFormattingSettings = false }
            )
        }

        // Sub-groups management dialog (private channels, admin only)
        if (showSubGroupsDialog && channel?.isAdmin == true && channel.isPrivate) {
            val subGroups by detailsViewModel.subGroups.collectAsState()
            val subGroupsLoading by detailsViewModel.subGroupsLoading.collectAsState()
            ChannelSubGroupsDialog(
                channelId = channelId,
                subGroups = subGroups,
                isLoading = subGroupsLoading,
                onDismiss = { showSubGroupsDialog = false },
                onCreateGroup = { name, desc ->
                    detailsViewModel.createSubGroup(
                        channelId = channelId,
                        groupName = name,
                        description = desc.ifBlank { null },
                        onSuccess = {
                            Toast.makeText(context, context.getString(R.string.channel_subgroup_created), Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                onDetachGroup = { groupId ->
                    detailsViewModel.detachSubGroup(channelId, groupId)
                }
            )
        }

        // Full-screen media viewer with swipe
        if (showMediaViewer && mediaViewerUrls.isNotEmpty()) {
            com.worldmates.messenger.ui.media.ImageGalleryViewer(
                imageUrls = mediaViewerUrls,
                initialPage = mediaViewerInitialPage,
                onDismiss = { showMediaViewer = false }
            )
        }

        // Full-screen video player
        if (showVideoPlayer && videoPlayerUrl.isNotEmpty()) {
            com.worldmates.messenger.ui.media.FullscreenVideoPlayer(
                videoUrl = videoPlayerUrl,
                onDismiss = { showVideoPlayer = false }
            )
        }
    }
}

/**
 * Pinned LIVE banner shown at top of channel feed when a stream is active.
 */
@Composable
private fun ChannelLiveBanner(
    isAdmin: Boolean,
    channelId: Long,
    channelName: String,
    isPremium: Boolean = false
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "live_banner_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_banner_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE53935).copy(alpha = pulseAlpha))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935).copy(alpha = pulseAlpha))
                )
                Column {
                    Text(
                        text = stringResource(R.string.channel_live_now),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                    Text(
                        text = channelName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Button(
                onClick = {
                    val intent = ChannelLivestreamActivity.createIntent(
                        context     = context,
                        channelId   = channelId,
                        isHost      = isAdmin,
                        isPremium   = isPremium,
                        channelName = channelName
                    )
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isAdmin) stringResource(R.string.channel_live_hosting)
                           else stringResource(R.string.channel_live_watch),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Horizontally-scrollable recordings archive section for the channel feed.
 */
@Composable
private fun ChannelRecordingsSection(
    recordings: List<RecordingItem>,
    channelId: Long,
    onPlayRecording: (url: String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.channel_recordings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${recordings.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(recordings) { rec ->
                RecordingCard(recording = rec, onPlay = { onPlayRecording(it) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun RecordingCard(
    recording: RecordingItem,
    onPlay: (url: String) -> Unit
) {
    val context = LocalContext.current
    val baseUrl = com.worldmates.messenger.data.Constants.NODE_BASE_URL
        .trimEnd('/')
    val token   = com.worldmates.messenger.data.UserSession.accessToken ?: ""
    val videoUrl = "$baseUrl/api/node/recordings/file/${recording.id}?access_token=$token"

    val durationStr = remember(recording.duration) {
        val m = recording.duration / 60
        val s = recording.duration % 60
        "%d:%02d".format(m, s)
    }
    val dateStr = remember(recording.created_at) {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            val d = sdf.parse(recording.created_at)
            if (d != null) java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(d)
            else recording.created_at.take(10)
        } catch (_: Exception) { recording.created_at.take(10) }
    }

    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { onPlay(videoUrl) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                   MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.PlayCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                if (recording.duration > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = durationStr,
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                val sizeMb = recording.file_size / 1_048_576f
                Text(
                    text = "%.1f MB".format(sizeMb),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Діалог для створення нового поста (тільки для адмінів)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostDialog(
    channelId: Long,
    onDismiss: () -> Unit,
    onCreate: (text: String, mediaList: List<com.worldmates.messenger.data.model.PostMedia>?) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var selectedMediaUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var uploadTotal by remember { mutableIntStateOf(0) }
    var currentMediaIndex by remember { mutableIntStateOf(0) }

    val maxMedia = 15

    // Multi-media picker (images + videos)
    val multiMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxMedia)
    ) { uris: List<android.net.Uri> ->
        val remaining = maxMedia - selectedMediaUris.size
        val newUris = uris.take(remaining)
        selectedMediaUris = selectedMediaUris + newUris
    }

    // Camera
    val cameraUri = remember {
        android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.TITLE, "post_image_${System.currentTimeMillis()}")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }.let {
            context.contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null && selectedMediaUris.size < maxMedia) {
            selectedMediaUris = selectedMediaUris + cameraUri
        }
    }

    val requestPostCameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraUri?.let { cameraLauncher.launch(it) }
        else Toast.makeText(context, context.getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = { if (!isUploadingMedia) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isUploadingMedia,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Top bar ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.post_new_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedMediaUris.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                stringResource(R.string.post_media_count, selectedMediaUris.size, maxMedia),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = { if (!isUploadingMedia) onDismiss() },
                        enabled = !isUploadingMedia
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.post_cancel))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // ── Body ─────────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    // Text input
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.post_placeholder),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        maxLines = 8,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Media preview grid
                    if (selectedMediaUris.isNotEmpty()) {
                        val pagerItemSize = if (selectedMediaUris.size == 1) 200.dp else 160.dp

                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(pagerItemSize),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedMediaUris.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(pagerItemSize)
                                        .clip(RoundedCornerShape(14.dp))
                                ) {
                                    AsyncImage(
                                        model = selectedMediaUris[index],
                                        contentDescription = "Media ${index + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Index badge
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.55f),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                            .size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "${index + 1}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Remove button
                                    IconButton(
                                        onClick = {
                                            selectedMediaUris = selectedMediaUris
                                                .toMutableList()
                                                .apply { removeAt(index) }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(30.dp)
                                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.post_remove_media),
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Video indicator
                                    val mimeType = context.contentResolver.getType(selectedMediaUris[index])
                                    if (mimeType?.startsWith("video/") == true) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.55f),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Videocam,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    stringResource(R.string.post_video_label),
                                                    color = Color.White,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Gallery / Camera buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                multiMediaPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            enabled = !isUploadingMedia && selectedMediaUris.size < maxMedia,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Icon(
                                Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.post_gallery), fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                                        == PackageManager.PERMISSION_GRANTED) {
                                    cameraUri?.let { cameraLauncher.launch(it) }
                                } else {
                                    requestPostCameraPermission.launch(android.Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            enabled = !isUploadingMedia && selectedMediaUris.size < maxMedia,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Icon(
                                Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.post_camera), fontSize = 13.sp)
                        }
                    }

                    // Upload progress
                    if (isUploadingMedia) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { if (uploadTotal > 0) uploadProgress.toFloat() / uploadTotal else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.post_uploading, uploadProgress, uploadTotal),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Max files warning
                    if (selectedMediaUris.size >= maxMedia) {
                        Text(
                            text = stringResource(R.string.post_max_files, maxMedia),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // ── Action buttons ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isUploadingMedia
                    ) {
                        Text(stringResource(R.string.post_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (text.isNotBlank() || selectedMediaUris.isNotEmpty()) {
                                if (selectedMediaUris.isNotEmpty()) {
                                    isUploadingMedia = true
                                    uploadTotal = selectedMediaUris.size
                                    uploadProgress = 0
                                    val uploadedMedia = mutableListOf<com.worldmates.messenger.data.model.PostMedia>()

                                    fun uploadNext(index: Int) {
                                        if (index >= selectedMediaUris.size) {
                                            isUploadingMedia = false
                                            onCreate(text.trim(), uploadedMedia)
                                            return
                                        }
                                        uploadMediaFile(
                                            context = context,
                                            uri = selectedMediaUris[index],
                                            onSuccess = { url ->
                                                val mimeType = context.contentResolver.getType(selectedMediaUris[index])
                                                val mediaType = when {
                                                    mimeType?.startsWith("video/") == true -> "video"
                                                    else -> "image"
                                                }
                                                uploadedMedia.add(
                                                    com.worldmates.messenger.data.model.PostMedia(
                                                        url = url,
                                                        type = mediaType,
                                                        filename = null
                                                    )
                                                )
                                                uploadProgress = index + 1
                                                uploadNext(index + 1)
                                            },
                                            onError = { error ->
                                                isUploadingMedia = false
                                                Toast.makeText(context, context.getString(R.string.post_upload_error, error), Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                    uploadNext(0)
                                } else {
                                    onCreate(text.trim(), null)
                                }
                            }
                        },
                        enabled = (text.isNotBlank() || selectedMediaUris.isNotEmpty()) && !isUploadingMedia,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.post_publish))
                    }
                }
            }
        }
    }
}

/**
 * Допоміжна функція для завантаження медіа файлу
 */
private fun uploadMediaFile(
    context: android.content.Context,
    uri: android.net.Uri,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: run {
                withContext(Dispatchers.Main) {
                    onError("Не вдалося відкрити файл")
                }
                return@launch
            }

            val bytes = inputStream.readBytes()
            inputStream.close()

            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val mediaType = when {
                mimeType.startsWith("image/") -> "image"
                mimeType.startsWith("video/") -> "video"
                else -> "file"
            }

            val requestFile = okhttp3.RequestBody.create(
                mimeType.toMediaTypeOrNull(),
                bytes
            )

            val filePart = okhttp3.MultipartBody.Part.createFormData(
                "file",
                "media_${System.currentTimeMillis()}.${mimeType.split("/").last()}",
                requestFile
            )

            val mediaTypePart = okhttp3.RequestBody.create(
                "text/plain".toMediaTypeOrNull(),
                mediaType
            )

            val response = com.worldmates.messenger.network.NodeRetrofitClient.channelUploadApi.uploadMedia(
                mediaType = mediaTypePart,
                file = filePart
            )

            withContext(Dispatchers.Main) {
                if (response.apiStatus == 200 && response.url != null) {
                    onSuccess(response.url)
                } else {
                    onError(response.errorMessage ?: "Помилка завантаження")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Помилка: ${e.localizedMessage}")
            }
        }
    }
}

/**
 * Діалог для відображення списку підписників
 */
@Composable
fun SubscribersDialog(
    subscribers: List<com.worldmates.messenger.data.model.ChannelSubscriber>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.ch_subscribers_count_fmt, subscribers.size),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (subscribers.isEmpty()) {
                Text(
                    text = stringResource(R.string.channel_no_subscribers),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                val context = LocalContext.current
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(subscribers) { subscriber ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Відкриваємо профіль користувача
                                    val profileIntent = Intent(context, com.worldmates.messenger.ui.profile.UserProfileActivity::class.java)
                                    profileIntent.putExtra("user_id", subscriber.userId ?: subscriber.id ?: 0L)
                                    context.startActivity(profileIntent)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Аватар
                            if (!subscriber.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = subscriber.avatarUrl,
                                    contentDescription = subscriber.username,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                // Placeholder
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.secondary
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (subscriber.username?.take(1) ?: "U").uppercase(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Інфо
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subscriber.name ?: subscriber.username ?: "User #${subscriber.id ?: subscriber.userId ?: "?"}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (subscriber.role != null) {
                                        Text(
                                            text = when(subscriber.role) {
                                                "owner" -> stringResource(R.string.ch_role_owner)
                                                "admin" -> stringResource(R.string.ch_role_admin)
                                                else -> subscriber.role!!
                                            },
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (subscriber.isMuted) {
                                        if (subscriber.role != null) {
                                            Text(
                                                text = "•",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        Text(
                                            text = stringResource(R.string.muted_notifications),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }

                        if (subscriber != subscribers.last()) {
                            Divider(
                                modifier = Modifier.padding(start = 52.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ch_close))
            }
        }
    )
}

/**
 * Діалог для додавання учасників в канал
 */
@Composable
fun AddMembersDialog(
    channelId: Long,
    channelsViewModel: ChannelsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.worldmates.messenger.network.SearchUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Виконуємо пошук з debounce
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }

        kotlinx.coroutines.delay(500) // debounce
        isSearching = true
        channelsViewModel.searchUsers(
            query = searchQuery,
            onSuccess = { users ->
                searchResults = users
                isSearching = false
            },
            onError = { error ->
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                isSearching = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                stringResource(R.string.add_members),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
            ) {
                // Поле пошуку
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    placeholder = { Text(stringResource(R.string.search_users_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                // Результати пошуку
                if (isSearching) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.users_not_found),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(searchResults) { user ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Аватар користувача
                                        if (user.avatarUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = user.avatarUrl.toFullMediaUrl(),
                                                contentDescription = "Avatar",
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.radialGradient(
                                                            colors = listOf(
                                                                MaterialTheme.colorScheme.primary,
                                                                MaterialTheme.colorScheme.tertiary
                                                            )
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = (user.name?.firstOrNull() ?: user.username.firstOrNull() ?: 'U')
                                                        .uppercaseChar().toString(),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = user.name ?: user.username,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "@${user.username}",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    // Кнопка додавання
                                    FilledTonalButton(
                                        onClick = {
                                            channelsViewModel.addChannelMember(
                                                channelId = channelId,
                                                userId = user.userId,
                                                onSuccess = {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.user_added_to_channel, user.username),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    // Видаляємо користувача зі списку результатів пошуку
                                                    searchResults = searchResults.filter { it.userId != user.userId }
                                                },
                                                onError = { error ->
                                                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.btn_add))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ch_close))
            }
        }
    )
}

/**
 * Діалог для створення опитування в каналі (тільки для адмінів)
 */
@Composable
fun CreateChannelPollDialog(
    channelId: Long,
    onDismiss: () -> Unit,
    onCreate: (question: String, options: List<String>, isAnonymous: Boolean, allowsMultiple: Boolean) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var isAnonymous by remember { mutableStateOf(true) }
    var allowsMultiple by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.HowToVote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                stringResource(R.string.ch_create_poll),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Питання
                OutlinedTextField(
                    value = question,
                    onValueChange = { if (it.length <= 255) question = it },
                    placeholder = { Text(stringResource(R.string.ch_poll_question), fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    label = { Text(stringResource(R.string.ch_poll_question_label)) },
                    supportingText = { Text("${question.length}/255", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Варіанти відповідей
                Text(
                    text = stringResource(R.string.poll_options_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )

                options.forEachIndexed { index, option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Номер варіанту
                        Surface(
                            color = if (option.isNotBlank()) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (option.isNotBlank()) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedTextField(
                            value = option,
                            onValueChange = { value ->
                                if (value.length <= 100)
                                    options = options.toMutableList().also { it[index] = value }
                            },
                            placeholder = { Text(stringResource(R.string.ch_poll_option_hint, index + 1), fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        if (options.size > 2) {
                            IconButton(
                                onClick = { options = options.toMutableList().also { it.removeAt(index) } },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ch_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (options.size < 10) {
                    OutlinedButton(
                        onClick = { options = options + "" },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ch_poll_add_option), fontSize = 14.sp)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Налаштування опитування
                Text(
                    text = stringResource(R.string.ch_settings_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )

                // Анонімне голосування
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAnonymous = !isAnonymous }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.poll_anonymous_toggle),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Switch(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
                    }
                }

                // Кілька відповідей
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { allowsMultiple = !allowsMultiple }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckBox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.poll_multiple_toggle),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Switch(checked = allowsMultiple, onCheckedChange = { allowsMultiple = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
                    if (question.isBlank() || validOptions.size < 2) return@Button
                    onCreate(question.trim(), validOptions, isAnonymous, allowsMultiple)
                    onDismiss()
                },
                enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.HowToVote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.ch_create_poll))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.post_cancel)) }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Channel Sub-Groups Dialog
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Dialog for managing channel sub-groups (private premium channels only).
 * Admins can create new groups (up to 5) or detach existing ones.
 * Subscribers see the list and can navigate to any sub-group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSubGroupsDialog(
    channelId: Long,
    subGroups: List<ChannelSubGroup>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreateGroup: (name: String, description: String) -> Unit,
    onDetachGroup: (groupId: Long) -> Unit
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupDesc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    tint = Color(0xFF7C4DFF),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    stringResource(R.string.channel_subgroups_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color(0xFF7C4DFF))
                    }
                } else if (subGroups.isEmpty()) {
                    Text(
                        stringResource(R.string.channel_subgroups_empty),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    subGroups.forEach { group ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        group.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${group.membersCount} ${stringResource(R.string.members_count)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                }
                                IconButton(
                                    onClick = { onDetachGroup(group.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.channel_subgroup_detach),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // "Add sub-group" button — only shown when below the 5-group limit
                if (subGroups.size < 5) {
                    if (showCreateSheet) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { if (it.length <= 255) newGroupName = it },
                            label = { Text(stringResource(R.string.group_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newGroupDesc,
                            onValueChange = { if (it.length <= 500) newGroupDesc = it },
                            label = { Text(stringResource(R.string.group_description)) },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showCreateSheet = false; newGroupName = ""; newGroupDesc = "" }) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    if (newGroupName.isNotBlank()) {
                                        onCreateGroup(newGroupName.trim(), newGroupDesc.trim())
                                        showCreateSheet = false
                                        newGroupName = ""
                                        newGroupDesc = ""
                                    }
                                },
                                enabled = newGroupName.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                            ) {
                                Text(stringResource(R.string.create))
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showCreateSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C4DFF))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.channel_subgroup_create), color = Color(0xFF7C4DFF))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
