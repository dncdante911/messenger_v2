package com.worldmates.messenger.ui.stories

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import coil.compose.AsyncImage
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.Story
import com.worldmates.messenger.data.model.StoryComment
import com.worldmates.messenger.network.StoryReactionType
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.ui.preferences.UIStyle
import com.worldmates.messenger.ui.preferences.rememberUIStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity для перегляду Stories (як особистих, так і канальних)
 * Підтримує два стилі UI: WORLDMATES (карточний з градієнтами) та TELEGRAM (мінімалістичний)
 */
class StoryViewerActivity : AppCompatActivity() {

    private lateinit var viewModel: StoryViewModel
    private var storyId: Long = 0
    private var userId: Long = 0
    private var isChannelStory: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storyId = intent.getLongExtra("story_id", 0)
        userId = intent.getLongExtra("user_id", 0)
        isChannelStory = intent.getBooleanExtra("is_channel_story", false)

        viewModel = ViewModelProvider(this).get(StoryViewModel::class.java)

        // Завантажуємо story
        if (storyId > 0) {
            viewModel.loadStoryById(storyId)
        } else if (userId > 0) {
            viewModel.loadUserStories(userId)
        }

        setContent {
            WorldMatesThemedApp {
                StoryViewerScreen(
                    viewModel = viewModel,
                    isChannelStory = isChannelStory,
                    onClose = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StoryViewerScreen(
    viewModel: StoryViewModel,
    isChannelStory: Boolean,
    onClose: () -> Unit
) {
    val uiStyle = rememberUIStyle()
    val allStories by viewModel.stories.collectAsState()
    val currentStory by viewModel.currentStory.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val viewers by viewModel.viewers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var showComments by remember { mutableStateOf(false) }
    var showViewers by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    // Получаем stories того же пользователя что и currentStory
    val userStories = remember(allStories, currentStory) {
        android.util.Log.d("StoryViewer", "📊 allStories count: ${allStories.size}")
        allStories.forEachIndexed { index, story ->
            android.util.Log.d("StoryViewer", "  allStories[$index]: id=${story.id}, userId=${story.userId}")
        }

        val filtered = currentStory?.let { story ->
            allStories.filter { it.userId == story.userId }
        } ?: emptyList()

        android.util.Log.d("StoryViewer", "📋 userStories count: ${filtered.size}")
        filtered.forEachIndexed { index, story ->
            android.util.Log.d("StoryViewer", "  userStories[$index]: id=${story.id}, userId=${story.userId}")
        }

        filtered
    }

    // Индекс текущей story
    val initialPage = remember(userStories, currentStory) {
        currentStory?.let { story ->
            userStories.indexOfFirst { it.id == story.id }.coerceAtLeast(0)
        } ?: 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { userStories.size }
    )

    // Устанавливаем первую story при инициализации
    LaunchedEffect(userStories) {
        if (userStories.isNotEmpty() && currentStory == null) {
            android.util.Log.d("StoryViewer", "Setting initial story at page $initialPage: ${userStories[initialPage].id}")
            viewModel.setCurrentStory(userStories[initialPage])
        }
    }

    // Обновляем currentStory при изменении страницы
    LaunchedEffect(pagerState.currentPage) {
        if (userStories.isNotEmpty() && pagerState.currentPage < userStories.size) {
            val newStory = userStories[pagerState.currentPage]
            if (currentStory?.id != newStory.id) {
                android.util.Log.d("StoryViewer", "Page changed to ${pagerState.currentPage}, setting story: ${newStory.id}")
                viewModel.setCurrentStory(newStory)
            }
        }
    }

    // Прогрес перегляду story
    var progress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    // Автоматичний прогрес story
    LaunchedEffect(pagerState.currentPage, isPaused) {
        if (userStories.isNotEmpty() && pagerState.currentPage < userStories.size) {
            val story = userStories[pagerState.currentPage]
            val mediaType = story.mediaItems.firstOrNull()?.type
            val videoDuration = story.mediaItems.firstOrNull()?.duration ?: 0

            // Перевіряємо premium статус
            val isPremiumUser = com.worldmates.messenger.data.UserSessionManager.isPremium()

            // Максимальна тривалість для відео та фото
            val maxVideoDuration = if (isPremiumUser) 60000L else 30000L  // 60/30 секунд
            val photoDuration = if (isPremiumUser) 40000L else 20000L     // 40/20 секунд

            val duration = if (mediaType == "video") {
                // Для видео: використовуємо duration з медіа, але обмежуємо максимумом
                val durationMs = videoDuration.toLong() * 1000L
                minOf(maxOf(durationMs, 10000L), maxVideoDuration)
            } else {
                photoDuration // Тривалість для фото
            }

            android.util.Log.d("StoryViewer", "Progress duration: ${duration}ms for type=$mediaType (premium=$isPremiumUser)")

            val steps = 100
            val stepDelay = duration / steps

            // Reset progress when story changes
            progress = 0f

            for (i in 0..steps) {
                if (!isPaused) {
                    progress = i.toFloat() / steps
                    delay(stepDelay)
                }
            }

            // Автопереход на следующую story или закрытие
            if (progress >= 0.99f && !isPaused) {
                if (pagerState.currentPage < userStories.size - 1) {
                    // Переход на следующую story
                    android.util.Log.d("StoryViewer", "Auto-advancing to next story")
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                } else {
                    // Последняя story - закрываем
                    android.util.Log.d("StoryViewer", "Last story completed, closing")
                    onClose()
                }
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val story = userStories.getOrNull(page)

        android.util.Log.d("StoryViewer", "HorizontalPager page=$page, story=${story?.id}, userStories size=${userStories.size}")

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            // Тап зліва - попередня story, справа - наступна
                            if (offset.x < size.width / 3) {
                                // Previous story
                                scope.launch {
                                    if (pagerState.currentPage > 0) {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            } else if (offset.x > size.width * 2 / 3) {
                                // Next story
                                scope.launch {
                                    if (pagerState.currentPage < userStories.size - 1) {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    } else {
                                        onClose()
                                    }
                                }
                            } else {
                                // Center tap - pause/unpause
                                isPaused = !isPaused
                            }
                        },
                        onLongPress = {
                            isPaused = !isPaused
                        }
                    )
                }
        ) {
            if (story != null) {
                android.util.Log.d("StoryViewer", "✅ Rendering story: id=${story.id}, mediaItems count=${story.mediaItems.size}")
            } else {
                android.util.Log.e("StoryViewer", "❌ Story is NULL for page $page")
            }

            story?.let {
                android.util.Log.d("StoryViewer", "Displaying story: id=${story.id}, mediaItems count=${story.mediaItems.size}")

                // Медіа контент
                story.mediaItems.firstOrNull()?.let { media ->
                    // Build full URL for media
                    val mediaUrl = if (media.filename.startsWith("http")) {
                        media.filename // Already full URL
                    } else {
                        "${Constants.MEDIA_BASE_URL}${media.filename}" // Add base URL
                    }

                    android.util.Log.d("StoryViewer", "Loading media: type=${media.type}, filename=${media.filename}")
                    android.util.Log.d("StoryViewer", "Full URL: $mediaUrl")

                    when (media.type) {
                        "image" -> {
                            // Show image with AsyncImage
                            AsyncImage(
                                model = mediaUrl,
                                contentDescription = "Story image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        "video" -> {
                            // Show video with ExoPlayer
                            VideoPlayer(
                                videoUrl = mediaUrl,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            // Unknown media type
                            Text(
                                text = "Непідтримуваний тип медіа: ${media.type}",
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                } ?: run {
                    android.util.Log.e("StoryViewer", "No media items found for story id=${story.id}")
                }

                // Прогрес-бари для всех stories (как в Instagram)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    userStories.forEachIndexed { index, _ ->
                        LinearProgressIndicator(
                            progress = {
                                when {
                                    index < pagerState.currentPage -> 1f // Завершенные
                                    index == pagerState.currentPage -> progress // Текущая
                                    else -> 0f // Еще не просмотренные
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }

                // Header з інфо про автора
                StoryHeader(
                    story = story,
                    isChannelStory = isChannelStory,
                    onClose = onClose,
                    uiStyle = uiStyle,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp)
                )

                // Footer з діями
                StoryFooter(
                    story = story,
                    onReactionClick = {
                        android.util.Log.d("StoryViewer", "Reactions clicked for story: ${story.id}")
                        showReactions = true
                    },
                    onCommentClick = {
                        android.util.Log.d("StoryViewer", "Comments clicked for story: ${story.id}")
                        viewModel.loadComments(story.id)
                        showComments = true
                    },
                    onShareClick = {
                        android.util.Log.d("StoryViewer", "Share clicked for story: ${story.id}")
                        // TODO: Implement share functionality
                    },
                    onViewsClick = {
                        android.util.Log.d("StoryViewer", "Views clicked for story: ${story.id}, isOwn: ${story.userId == UserSession.userId}")
                        if (story.userId == UserSession.userId) {
                            viewModel.loadStoryViews(story.id)
                            showViewers = true
                        }
                    },
                    isOwnStory = story.userId == UserSession.userId,
                    uiStyle = uiStyle,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            } // End story?.let

            // Loading
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            // Error
            error?.let {
                Text(
                    text = it,
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
        }
    }

    // Bottom sheets - они должны быть вне HorizontalPager
    if (showComments) {
        StoryCommentsBottomSheet(
            comments = comments,
            onDismiss = { showComments = false },
            onAddComment = { text ->
                currentStory?.let {
                    viewModel.createComment(it.id, text)
                }
            },
            onDeleteComment = { commentId ->
                viewModel.deleteComment(commentId)
            },
            uiStyle = uiStyle
        )
    }

    if (showViewers) {
        ViewersBottomSheet(
            viewers = viewers,
            onDismiss = { showViewers = false },
            uiStyle = uiStyle
        )
    }

    if (showReactions) {
        ReactionsBottomSheet(
            onDismiss = { showReactions = false },
            onReactionSelect = { reactionType ->
                currentStory?.let {
                    viewModel.reactToStory(it.id, reactionType)
                }
                showReactions = false
            },
            uiStyle = uiStyle
        )
    }
}

@Composable
fun StoryHeader(
    story: Story,
    isChannelStory: Boolean,
    onClose: () -> Unit,
    uiStyle: UIStyle = UIStyle.WORLDMATES,
    modifier: Modifier = Modifier
) {
    // WORLDMATES: карточний стиль з красивим градієнтом
    // TELEGRAM: мінімалістичний плоский стиль
    val backgroundBrush = when (uiStyle) {
        UIStyle.WORLDMATES -> Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.9f),
                Color.Black.copy(alpha = 0.75f),
                Color.Black.copy(alpha = 0.4f),
                Color.Transparent
            )
        )
        UIStyle.TELEGRAM -> Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.5f),
                Color.Black.copy(alpha = 0.3f),
                Color.Transparent
            )
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundBrush)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар
        AsyncImage(
            model = story.userData?.avatar,
            contentDescription = "Avatar",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Ім'я та час
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = story.userData?.name ?: "Unknown",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isChannelStory) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Channel",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = formatStoryTime(story.time),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }

        // Кнопка закриття
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}

@Composable
fun StoryFooter(
    story: Story,
    onReactionClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onViewsClick: () -> Unit,
    isOwnStory: Boolean,
    uiStyle: UIStyle = UIStyle.WORLDMATES,
    modifier: Modifier = Modifier
) {
    // WORLDMATES: карточний стиль з красивим градієнтом
    // TELEGRAM: мінімалістичний плоский стиль
    val backgroundBrush = when (uiStyle) {
        UIStyle.WORLDMATES -> Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.4f),
                Color.Black.copy(alpha = 0.75f),
                Color.Black.copy(alpha = 0.9f)
            ),
            startY = 0f,
            endY = Float.POSITIVE_INFINITY
        )
        UIStyle.TELEGRAM -> Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.3f),
                Color.Black.copy(alpha = 0.5f)
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundBrush)
            .padding(16.dp)
    ) {
        // Опис story
        if (!story.title.isNullOrEmpty() || !story.description.isNullOrEmpty()) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                story.title?.let {
                    Text(
                        text = it,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                story.description?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Дії - FIXED: вирівнювання кнопок
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,  // Changed from SpaceBetween to Start
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Реакції
            StoryActionButton(
                icon = Icons.Default.Favorite,
                text = "${story.reactions.total}",
                onClick = onReactionClick,
                uiStyle = uiStyle
            )

            Spacer(modifier = Modifier.width(16.dp))  // Optimized spacing

            // Коментарі
            StoryActionButton(
                icon = Icons.Default.Chat,
                text = "${story.commentsCount}",
                onClick = onCommentClick,
                uiStyle = uiStyle
            )

            Spacer(modifier = Modifier.width(16.dp))  // Optimized spacing

            // Поширити
            StoryActionButton(
                icon = Icons.Default.Share,
                text = "",
                onClick = onShareClick,
                uiStyle = uiStyle
            )

            // Перегляди (тільки для власних stories)
            if (isOwnStory) {
                Spacer(modifier = Modifier.width(16.dp))  // Optimized spacing
                StoryActionButton(
                    icon = Icons.Default.Visibility,
                    text = "${story.viewsCount}",
                    onClick = onViewsClick,
                    uiStyle = uiStyle
                )
            }
        }
    }
}

@Composable
fun StoryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    uiStyle: UIStyle = UIStyle.WORLDMATES
) {
    // WORLDMATES: карточний стиль з анімаціями та ефектами
    // TELEGRAM: мінімалістичний плоский стиль

    // FIXED: Використовуємо InteractionSource для відслідковування стану натискання
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    val iconSize = if (uiStyle == UIStyle.WORLDMATES) 26.dp else 24.dp
    val fontSize = if (uiStyle == UIStyle.WORLDMATES) 13.sp else 12.sp
    val fontWeight = if (uiStyle == UIStyle.WORLDMATES) FontWeight.SemiBold else FontWeight.Normal

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple()
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Іконка з легким background та glow ефектом
        Box(
            modifier = Modifier
                .size(iconSize + 16.dp)
                .then(
                    if (uiStyle == UIStyle.WORLDMATES) {
                        Modifier.background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }

        if (text.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = fontWeight,
                style = if (uiStyle == UIStyle.WORLDMATES) {
                    MaterialTheme.typography.labelMedium.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            offset = Offset(0f, 1f),
                            blurRadius = 2f
                        )
                    )
                } else {
                    MaterialTheme.typography.labelMedium
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCommentsBottomSheet(
    comments: List<StoryComment>,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit,
    onDeleteComment: (Long) -> Unit,
    uiStyle: UIStyle = UIStyle.WORLDMATES
) {
    val sheetState = rememberModalBottomSheetState()
    var commentText by remember { mutableStateOf("") }

    // WORLDMATES: карточний стиль
    // TELEGRAM: мінімалістичний стиль
    val containerColor = when (uiStyle) {
        UIStyle.WORLDMATES -> MaterialTheme.colorScheme.surface
        UIStyle.TELEGRAM -> MaterialTheme.colorScheme.background
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Коментарі (${comments.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Список коментарів
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 400.dp)
            ) {
                items(
                    items = comments,
                    key = { comment -> comment.id }
                ) { comment ->
                    StoryCommentItem(
                        comment = comment,
                        onDelete = { onDeleteComment(comment.id) },
                        uiStyle = uiStyle
                    )
                }
            }

            // Поле введення коментаря
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Додати коментар...") },
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            onAddComment(commentText)
                            commentText = ""
                        }
                    },
                    enabled = commentText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (commentText.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Gray
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StoryCommentItem(
    comment: StoryComment,
    onDelete: () -> Unit,
    uiStyle: UIStyle = UIStyle.WORLDMATES
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = comment.userData?.avatar,
            contentDescription = "Avatar",
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.userData?.name ?: "Unknown",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = comment.text,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = formatStoryTime(comment.time),
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Видалення (тільки для власних коментарів)
        if (comment.userId == UserSession.userId) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewersBottomSheet(
    viewers: List<com.worldmates.messenger.data.model.StoryViewer>,
    onDismiss: () -> Unit,
    uiStyle: UIStyle = UIStyle.WORLDMATES
) {
    val sheetState = rememberModalBottomSheetState()

    val containerColor = when (uiStyle) {
        UIStyle.WORLDMATES -> MaterialTheme.colorScheme.surface
        UIStyle.TELEGRAM -> MaterialTheme.colorScheme.background
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Переглянули (${viewers.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 500.dp)
            ) {
                items(
                    items = viewers,
                    key = { viewer -> viewer.userId }
                ) { viewer ->
                    ViewerItem(viewer = viewer, uiStyle = uiStyle)
                }
            }
        }
    }
}

@Composable
fun ViewerItem(
    viewer: com.worldmates.messenger.data.model.StoryViewer,
    uiStyle: UIStyle = UIStyle.WORLDMATES
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = viewer.avatar,
            contentDescription = "Avatar",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = viewer.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatStoryTime(viewer.time),
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionsBottomSheet(
    onDismiss: () -> Unit,
    onReactionSelect: (StoryReactionType) -> Unit,
    uiStyle: UIStyle = UIStyle.WORLDMATES
) {
    val sheetState = rememberModalBottomSheetState()

    val containerColor = when (uiStyle) {
        UIStyle.WORLDMATES -> MaterialTheme.colorScheme.surface
        UIStyle.TELEGRAM -> MaterialTheme.colorScheme.background
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Виберіть реакцію",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    "👍" to StoryReactionType.LIKE,
                    "❤️" to StoryReactionType.LOVE,
                    "😂" to StoryReactionType.HAHA,
                    "😮" to StoryReactionType.WOW,
                    "😢" to StoryReactionType.SAD,
                    "😡" to StoryReactionType.ANGRY
                ).forEach { (emoji, type) ->
                    Text(
                        text = emoji,
                        fontSize = if (uiStyle == UIStyle.WORLDMATES) 44.sp else 40.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onReactionSelect(type) }
                            .padding(if (uiStyle == UIStyle.WORLDMATES) 14.dp else 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var errorState by remember { mutableStateOf<String?>(null) }

    // Create ExoPlayer instance with error handling - use videoUrl as key for proper lifecycle
    val exoPlayer = remember(videoUrl) {
        try {
            android.util.Log.d("VideoPlayer", "🎬 Creating ExoPlayer for URL: $videoUrl")

            // Create OkHttp client for HTTP datasource
            val okHttpClient = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            // Create datasource factory with OkHttp
            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

            // Build ExoPlayer with custom datasource
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply {
                    // Add error listener
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            val errorMsg = "ExoPlayer error: ${error.errorCodeName} - ${error.message}"
                            android.util.Log.e("VideoPlayer", "❌ $errorMsg", error)
                            errorState = errorMsg
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_IDLE -> android.util.Log.d("VideoPlayer", "⏸️ State: IDLE")
                                Player.STATE_BUFFERING -> android.util.Log.d("VideoPlayer", "⏳ State: BUFFERING")
                                Player.STATE_READY -> android.util.Log.d("VideoPlayer", "✅ State: READY - video loaded successfully")
                                Player.STATE_ENDED -> android.util.Log.d("VideoPlayer", "🏁 State: ENDED")
                            }
                        }
                    })

                    android.util.Log.d("VideoPlayer", "🎥 ExoPlayer instance created successfully")
                }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayer", "❌ Failed to create ExoPlayer", e)
            errorState = "Failed to initialize player: ${e.message}"
            null
        }
    }

    // Set media source when URL changes
    LaunchedEffect(videoUrl) {
        android.util.Log.d("VideoPlayer", "📹 Setting media source: $videoUrl")
        exoPlayer?.apply {
            try {
                val mediaItem = MediaItem.fromUri(videoUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE // Loop video
                android.util.Log.d("VideoPlayer", "▶️ ExoPlayer prepared and starting playback...")
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayer", "❌ Failed to set media item", e)
                errorState = "Failed to load video: ${e.message}"
            }
        }
    }

    // Clean up player when composable is disposed or videoUrl changes
    DisposableEffect(videoUrl) {
        onDispose {
            android.util.Log.d("VideoPlayer", "🗑️ Disposing ExoPlayer for $videoUrl")
            exoPlayer?.release()
        }
    }

    Box(modifier = modifier) {
        // Show error if player creation failed or playback error occurred
        if (errorState != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Не вдалося відтворити відео",
                    color = Color.White,
                    fontSize = 16.sp
                )
                Text(
                    text = errorState ?: "",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                )
            }
        } else if (exoPlayer != null) {
            // ExoPlayer UI
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // Hide default controls for Stories
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Loading state
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }
}

private fun formatStoryTime(timestamp: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestamp

    return when {
        diff < 60 -> "щойно"
        diff < 3600 -> "${diff / 60} хв тому"
        diff < 86400 -> "${diff / 3600} год тому"
        else -> SimpleDateFormat("dd MMM", Locale("uk")).format(Date(timestamp * 1000))
    }
}