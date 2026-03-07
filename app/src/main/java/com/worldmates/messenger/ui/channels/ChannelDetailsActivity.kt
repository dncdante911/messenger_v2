package com.worldmates.messenger.ui.channels

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
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
import com.worldmates.messenger.R
import com.worldmates.messenger.utils.LanguageManager

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
            Toast.makeText(this, "Помилка: канал не знайдено", Toast.LENGTH_SHORT).show()
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

    // Знаходимо канал — primary source: detailsViewModel (fresh from API)
    val channel = detailsChannel
        ?: subscribedChannels.find { it.id == channelId }
        ?: allChannels.find { it.id == channelId }

    // UI States
    var showMediaViewer by remember { mutableStateOf(false) }
    var mediaViewerUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var mediaViewerInitialPage by remember { mutableStateOf(0) }
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var showChangeAvatarDialog by remember { mutableStateOf(false) }
    var showSubscribersDialog by remember { mutableStateOf(false) }
    var showAddMembersDialog by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showPostOptions by remember { mutableStateOf(false) }
    var showEditPostDialog by remember { mutableStateOf(false) }
    var showStatisticsDialog by remember { mutableStateOf(false) }
    var showAdminsDialog by remember { mutableStateOf(false) }
    var showEditChannelDialog by remember { mutableStateOf(false) }
    var showChannelMenuDialog by remember { mutableStateOf(false) }
    var showChannelSettingsDialog by remember { mutableStateOf(false) }
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

    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = {
            refreshing = true
            detailsViewModel.loadChannelPosts(channelId)
            channelsViewModel.refreshChannel(channelId)
            refreshing = false
        }
    )

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
                    Toast.makeText(context, "Аватар успішно оновлено", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Аватар успішно оновлено", Toast.LENGTH_SHORT).show()
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
                            text = "Канал не знайдено",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackPressed) {
                            Text("Повернутися")
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
                                                    Toast.makeText(context, "Ви відписалися від каналу", Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { error ->
                                                    Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            channelsViewModel.subscribeChannel(
                                                channelId = channelId,
                                                onSuccess = {
                                                    Toast.makeText(context, "Ви підписалися на канал!", Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { error ->
                                                    Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
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
                                        text = "Posts",
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
                                            "No posts yet",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = posts.sortedByDescending { it.createdTime },
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
                                            Toast.makeText(context, "Reaction added!", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { error ->
                                            Toast.makeText(context, "Error: $error", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                val onCommentsClickHandler: () -> Unit = {
                                    detailsViewModel.loadComments(post.id)
                                    showCommentsSheet = true
                                }
                                val onShareClickHandler: () -> Unit = {
                                    val shareText = buildString {
                                        append(post.text)
                                        append("\n\n")
                                        append("By: ${post.authorName ?: post.authorUsername ?: "User #${post.authorId}"}")
                                        append("\n")
                                        append("Channel: ${channel?.name ?: "WorldMates Channel"}")
                                    }
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Share post")
                                    context.startActivity(shareIntent)
                                }
                                val onMoreClickHandler: () -> Unit = {
                                    selectedPostForOptions = post
                                    showPostOptions = true
                                }
                                val onMediaClickHandler: (Int) -> Unit = { index ->
                                    val imageUrls = post.media
                                        ?.filter { it.type == "image" || it.type.isNullOrBlank() }
                                        ?.map { it.url.toFullMediaUrl() }
                                        ?: emptyList()
                                    if (imageUrls.isNotEmpty()) {
                                        mediaViewerUrls = imageUrls
                                        mediaViewerInitialPage = index.coerceIn(0, imageUrls.size - 1)
                                        showMediaViewer = true
                                    }
                                }

                                when (channelViewStyle) {
                                    ChannelViewStyle.PREMIUM -> {
                                        PremiumPostCard(
                                            post = post,
                                            onPostClick = onPostClickHandler,
                                            onReactionClick = onReactionClickHandler,
                                            onCommentsClick = onCommentsClickHandler,
                                            onShareClick = onShareClickHandler,
                                            onMoreClick = onMoreClickHandler,
                                            onMediaClick = onMediaClickHandler,
                                            canEdit = channel.isAdmin,
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                    ChannelViewStyle.CLASSIC -> {
                                        ChannelPostCard(
                                            post = post,
                                            onPostClick = onPostClickHandler,
                                            onReactionClick = onReactionClickHandler,
                                            onCommentsClick = onCommentsClickHandler,
                                            onShareClick = onShareClickHandler,
                                            onMoreClick = onMoreClickHandler,
                                            onMediaClick = onMediaClickHandler,
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
                            Toast.makeText(context, "Post created!", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "Коментар додано!", Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "Коментар видалено", Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onCommentReaction = { commentId, emoji ->
                    detailsViewModel.addCommentReaction(
                        commentId = commentId,
                        emoji = emoji,
                        onSuccess = {
                            Toast.makeText(context, "Реакцію додано!", Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "Реакцію додано!", Toast.LENGTH_SHORT).show()
                                detailsViewModel.loadChannelPosts(channelId)
                            },
                            onError = { error ->
                                Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "Коментар додано!", Toast.LENGTH_SHORT).show()
                                detailsViewModel.loadComments(post.id)
                            },
                            onError = { error ->
                                Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, "Коментар видалено", Toast.LENGTH_SHORT).show()
                                detailsViewModel.loadComments(post.id)
                            },
                            onError = { error ->
                                Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onCommentReaction = { commentId, emoji ->
                    detailsViewModel.addCommentReaction(
                        commentId = commentId,
                        emoji = emoji,
                        onSuccess = {
                            Toast.makeText(context, "Реакцію додано!", Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, "Помилка: $error", Toast.LENGTH_SHORT).show()
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
                            "Channel Management",
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
                            title = "Edit Info",
                            subtitle = "Name, description, username",
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = {
                                showChannelMenuDialog = false
                                showEditChannelDialog = true
                            }
                        )

                        // Settings
                        PremiumMenuItem(
                            icon = Icons.Default.Settings,
                            title = "Settings",
                            subtitle = "Comments, reactions, slow mode",
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            onClick = {
                                showChannelMenuDialog = false
                                showChannelSettingsDialog = true
                            }
                        )

                        // Statistics
                        PremiumMenuItem(
                            icon = Icons.Default.Info,
                            title = "Statistics",
                            subtitle = "Views, growth, engagement",
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
                                title = "Administrators",
                                subtitle = "Manage admin roles",
                                iconTint = Color(0xFFFF9800),
                                onClick = {
                                    showChannelMenuDialog = false
                                    detailsViewModel.loadChannelDetails(channelId)
                                    showAdminsDialog = true
                                }
                            )

                            // Formatting
                            PremiumMenuItem(
                                icon = Icons.Outlined.TextFormat,
                                title = "Message Formatting",
                                subtitle = "Text styling permissions",
                                iconTint = MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    showChannelMenuDialog = false
                                    showFormattingSettings = true
                                }
                            )

                            // Admin Panel
                            PremiumMenuItem(
                                icon = Icons.Outlined.Dashboard,
                                title = "Admin Panel",
                                subtitle = "Full management dashboard",
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
                    TextButton(onClick = { showChannelMenuDialog = false }) {
                        Text("Close")
                    }
                }
            )
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
                            Toast.makeText(context, "Канал оновлено!", Toast.LENGTH_SHORT).show()
                            showEditChannelDialog = false
                            detailsViewModel.loadChannelDetails(channelId)
                        },
                        onError = { error ->
                            Toast.makeText(context, "Помилка: $error", Toast.LENGTH_LONG).show()
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
                            Toast.makeText(context, "Налаштування збережено!", Toast.LENGTH_SHORT).show()
                            showChannelSettingsDialog = false
                            detailsViewModel.loadChannelDetails(channelId)
                        },
                        onError = { error ->
                            Toast.makeText(context, "Помилка: $error", Toast.LENGTH_LONG).show()
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
                    cameraUri?.let { cameraLauncher.launch(it) }
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
                                "Налаштування форматування збережено",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onError = { error ->
                            Toast.makeText(
                                context,
                                "Помилка збереження: $error",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                },
                onDismiss = { showFormattingSettings = false }
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

    AlertDialog(
        onDismissRequest = { if (!isUploadingMedia) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New Post", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (selectedMediaUris.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "${selectedMediaUris.size}/$maxMedia",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Text input
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("What's on your mind?", fontSize = 14.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Media preview grid
                if (selectedMediaUris.isNotEmpty()) {
                    // Horizontal pager for media preview
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
                                    .clip(RoundedCornerShape(12.dp))
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
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .size(24.dp)
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
                                        .size(28.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.6f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Video indicator
                                val mimeType = context.contentResolver.getType(selectedMediaUris[index])
                                if (mimeType?.startsWith("video/") == true) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.6f),
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
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Video", color = Color.White, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Media action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            multiMediaPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isUploadingMedia && selectedMediaUris.size < maxMedia,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gallery", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { cameraUri?.let { cameraLauncher.launch(it) } },
                        modifier = Modifier.weight(1f),
                        enabled = !isUploadingMedia && selectedMediaUris.size < maxMedia,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera", fontSize = 13.sp)
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Uploading $uploadProgress of $uploadTotal...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selectedMediaUris.size >= maxMedia) {
                    Text(
                        text = "Maximum $maxMedia files reached",
                        fontSize = 12.sp,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        },
        confirmButton = {
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
                                        Toast.makeText(context, "Upload error: $error", Toast.LENGTH_SHORT).show()
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
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Publish")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploadingMedia
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
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
                text = "Підписники • ${subscribers.size}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (subscribers.isEmpty()) {
                Text(
                    text = "Немає підписників",
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
                                                "owner" -> "Власник"
                                                "admin" -> "Адміністратор"
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
                                            text = "Вимкнено сповіщення",
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
                Text("Закрити")
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
                "Додати учасників",
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
                    placeholder = { Text("Пошук користувачів...") },
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
                            "Користувачів не знайдено",
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
                                                        "Користувача ${user.username} додано до каналу",
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
                                        Text("Додати")
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
                Text("Закрити")
            }
        }
    )
}
