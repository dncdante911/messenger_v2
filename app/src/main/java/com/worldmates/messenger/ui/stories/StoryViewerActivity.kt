package com.worldmates.messenger.ui.stories

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "StoryViewer"

// ═══════════════════════════════════════════════════════════════════════════════
// Activity
// ═══════════════════════════════════════════════════════════════════════════════

class StoryViewerActivity : AppCompatActivity() {

    private lateinit var viewModel: StoryViewModel
    private var storyId: Long = 0
    private var userId: Long = 0
    private var isChannelStory: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: let us control insets so content doesn't clip under system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        storyId = intent.getLongExtra("story_id", 0)
        userId = intent.getLongExtra("user_id", 0)
        isChannelStory = intent.getBooleanExtra("is_channel_story", false)

        viewModel = ViewModelProvider(this).get(StoryViewModel::class.java)

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

// ═══════════════════════════════════════════════════════════════════════════════
// Main Screen
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StoryViewerScreen(
    viewModel: StoryViewModel,
    isChannelStory: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val allStories by viewModel.stories.collectAsState()
    val currentStory by viewModel.currentStory.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val viewers by viewModel.viewers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var showComments by remember { mutableStateOf(false) }
    var showViewers by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    // Floating reaction animation state
    var floatingEmoji by remember { mutableStateOf<String?>(null) }
    var floatingEmojiKey by remember { mutableStateOf(0) }

    // User stories filtered by userId
    val userStories = remember(allStories, currentStory) {
        currentStory?.let { story ->
            allStories.filter { it.userId == story.userId }
        } ?: emptyList()
    }

    val initialPage = remember(userStories, currentStory) {
        currentStory?.let { story ->
            userStories.indexOfFirst { it.id == story.id }.coerceAtLeast(0)
        } ?: 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { userStories.size }
    )

    val scope = rememberCoroutineScope()

    // Set initial story
    LaunchedEffect(userStories) {
        if (userStories.isNotEmpty() && currentStory == null) {
            viewModel.setCurrentStory(userStories[initialPage])
        }
    }

    // Update currentStory + mark viewed on page change
    LaunchedEffect(pagerState.currentPage) {
        if (userStories.isNotEmpty() && pagerState.currentPage < userStories.size) {
            val newStory = userStories[pagerState.currentPage]
            if (currentStory?.id != newStory.id) {
                viewModel.setCurrentStory(newStory)
            }
            // Mark as viewed
            viewModel.markStoryViewed(newStory.id)
        }
    }

    // Auto-progress
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(pagerState.currentPage, isPaused, showComments, showViewers) {
        if (userStories.isEmpty() || pagerState.currentPage >= userStories.size) return@LaunchedEffect
        if (showComments || showViewers) return@LaunchedEffect // Pause while sheets are open

        val story = userStories[pagerState.currentPage]
        val mediaType = story.mediaItems.firstOrNull()?.type
        val videoDuration = story.mediaItems.firstOrNull()?.duration ?: 0
        val isPremium = com.worldmates.messenger.data.UserSessionManager.isPremium()

        val maxVideoDur = if (isPremium) 60000L else 30000L
        val photoDur = if (isPremium) 40000L else 20000L

        val duration = if (mediaType == "video") {
            val ms = videoDuration.toLong() * 1000L
            minOf(maxOf(ms, 10000L), maxVideoDur)
        } else photoDur

        val steps = 100
        val stepDelay = duration / steps
        progress = 0f

        for (i in 0..steps) {
            if (!isPaused && !showComments && !showViewers) {
                progress = i.toFloat() / steps
                delay(stepDelay)
            } else {
                // Wait while paused
                while (isPaused || showComments || showViewers) delay(100)
            }
        }

        // Auto-advance
        if (progress >= 0.99f) {
            if (pagerState.currentPage < userStories.size - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                onClose()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val story = userStories.getOrNull(page)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (offset.x < size.width / 3) {
                                    scope.launch {
                                        if (pagerState.currentPage > 0)
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                } else if (offset.x > size.width * 2 / 3) {
                                    scope.launch {
                                        if (pagerState.currentPage < userStories.size - 1)
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        else onClose()
                                    }
                                } else {
                                    isPaused = !isPaused
                                }
                            },
                            onLongPress = { isPaused = !isPaused }
                        )
                    }
            ) {
                story?.let {
                    // Media content
                    story.mediaItems.firstOrNull()?.let { media ->
                        val mediaUrl = if (media.filename.startsWith("http")) media.filename
                        else "${Constants.MEDIA_BASE_URL}${media.filename}"

                        when (media.type) {
                            "image" -> AsyncImage(
                                model = mediaUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            "video" -> VideoPlayer(
                                videoUrl = mediaUrl,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Top gradient + progress bars + header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.7f),
                                        Color.Black.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        // Progress bars
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            userStories.forEachIndexed { index, _ ->
                                LinearProgressIndicator(
                                    progress = {
                                        when {
                                            index < pagerState.currentPage -> 1f
                                            index == pagerState.currentPage -> progress
                                            else -> 0f
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }

                        // Header
                        StoryHeader(
                            story = story,
                            isChannelStory = isChannelStory,
                            onClose = onClose
                        )
                    }

                    // Bottom area
                    StoryBottomBar(
                        story = story,
                        isOwnStory = story.userId == UserSession.userId,
                        onReaction = { emoji, reactionType ->
                            floatingEmoji = emoji
                            floatingEmojiKey++
                            viewModel.reactToStory(story.id, reactionType)
                        },
                        onCommentClick = {
                            viewModel.loadComments(story.id)
                            showComments = true
                        },
                        onShareClick = {
                            val shareUrl = "${Constants.MEDIA_BASE_URL}story/${story.id}"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${story.userData?.name ?: ""}\n$shareUrl")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Story"))
                        },
                        onViewsClick = {
                            viewModel.loadStoryViews(story.id)
                            showViewers = true
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // Floating reaction animation
                floatingEmoji?.let { emoji ->
                    FloatingReactionAnimation(
                        key = floatingEmojiKey,
                        emoji = emoji,
                        onFinished = { floatingEmoji = null }
                    )
                }

                if (isLoading && story == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
            }
        }
    }

    // Comments bottom sheet
    if (showComments) {
        StoryCommentsSheet(
            comments = comments,
            onDismiss = { showComments = false },
            onAddComment = { text ->
                currentStory?.let { viewModel.createComment(it.id, text) }
            },
            onDeleteComment = { viewModel.deleteComment(it) }
        )
    }

    // Viewers bottom sheet
    if (showViewers) {
        ViewersSheet(
            viewers = viewers,
            onDismiss = { showViewers = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Header
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun StoryHeader(
    story: Story,
    isChannelStory: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = story.userData?.avatar,
            contentDescription = null,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = story.userData?.name ?: "Unknown",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (story.userData?.isVerified() == true) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (isChannelStory) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = formatStoryTime(story.time),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }

        // Pause indicator
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Bottom Bar — inline reactions + reply field
// ═══════════════════════════════════════════════════════════════════════════════

private val REACTION_EMOJIS = listOf(
    "\uD83D\uDC4D" to StoryReactionType.LIKE,    // 👍
    "\u2764\uFE0F" to StoryReactionType.LOVE,     // ❤️
    "\uD83D\uDE02" to StoryReactionType.HAHA,     // 😂
    "\uD83D\uDE2E" to StoryReactionType.WOW,      // 😮
    "\uD83D\uDE22" to StoryReactionType.SAD,       // 😢
    "\uD83D\uDE21" to StoryReactionType.ANGRY      // 😡
)

@Composable
fun StoryBottomBar(
    story: Story,
    isOwnStory: Boolean,
    onReaction: (String, StoryReactionType) -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onViewsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navBarInsets = WindowInsets.navigationBars
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.3f),
                        Color.Black.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            .windowInsetsPadding(navBarInsets)
            .padding(bottom = 8.dp)
    ) {
        // Title / description
        if (!story.title.isNullOrEmpty() || !story.description.isNullOrEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                story.title?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                story.description?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Quick reaction emojis
        if (!isOwnStory) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                REACTION_EMOJIS.forEach { (emoji, type) ->
                    val isSelected = story.reactions.isReacted && story.reactions.type == type.value
                    QuickReactionEmoji(
                        emoji = emoji,
                        isSelected = isSelected,
                        onClick = { onReaction(emoji, type) }
                    )
                }
            }
        }

        // Bottom row: reply field (or views for own) + actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isOwnStory) {
                // Own story: views + reactions count
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onViewsClick() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "${story.viewsCount}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "${story.reactions.total}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Other's story: reply input
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onCommentClick() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Написати відгук...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }

            // Comments button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { onCommentClick() },
                contentAlignment = Alignment.Center
            ) {
                BadgedBox(
                    badge = {
                        if (story.commentsCount > 0) {
                            Badge(
                                containerColor = Color(0xFF2196F3),
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = if (story.commentsCount > 99) "99+" else "${story.commentsCount}",
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Share button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { onShareClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Send,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Quick Reaction Emoji (with animation)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun QuickReactionEmoji(
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.5f else if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "emoji_scale",
        finishedListener = { pressed = false }
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.background(Color.White.copy(alpha = 0.2f))
                else Modifier
            )
            .clickable {
                pressed = true
                onClick()
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 28.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Floating Reaction Animation (emoji flies up and fades)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun FloatingReactionAnimation(
    key: Int,
    emoji: String,
    onFinished: () -> Unit
) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(key) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
        onFinished()
    }

    val offsetY = animProgress.value * -300f
    val scaleVal = 1f + animProgress.value * 1.5f
    val alphaVal = 1f - animProgress.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 72.sp,
            modifier = Modifier
                .graphicsLayer {
                    translationY = offsetY
                    scaleX = scaleVal
                    scaleY = scaleVal
                    alpha = alphaVal
                }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Comments Bottom Sheet (modern design + emoji picker)
// ═══════════════════════════════════════════════════════════════════════════════

private val EMOJI_GRID = listOf(
    "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83E\uDD70",
    "\uD83D\uDE0E", "\uD83D\uDE09", "\uD83E\uDD23", "\uD83D\uDE18",
    "\uD83D\uDE4F", "\uD83D\uDC4D", "\uD83D\uDC4F", "\u2764\uFE0F",
    "\uD83D\uDD25", "\uD83C\uDF89", "\uD83D\uDCAF", "\uD83C\uDF1F",
    "\uD83D\uDE22", "\uD83D\uDE2D", "\uD83D\uDE31", "\uD83E\uDD14",
    "\uD83D\uDE21", "\uD83E\uDD2F", "\uD83D\uDE4C", "\uD83D\uDE48",
    "\uD83D\uDC40", "\uD83D\uDCAA", "\uD83C\uDF38", "\uD83C\uDF1E",
    "\uD83C\uDF08", "\uD83D\uDE80", "\uD83C\uDFC6", "\uD83C\uDF82"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCommentsSheet(
    comments: List<StoryComment>,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit,
    onDeleteComment: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commentText by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            // Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Відгуки",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (comments.isNotEmpty()) {
                    Text(
                        text = "${comments.size}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

            // Comments list
            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Поки немає відгуків",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Будь першим!",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        CommentItem(
                            comment = comment,
                            onDelete = { onDeleteComment(comment.id) }
                        )
                    }
                }
            }

            // Emoji picker
            AnimatedVisibility(
                visible = showEmojiPicker,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Color(0xFF2C2C2E))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(EMOJI_GRID) { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    commentText += emoji
                                }
                                .padding(6.dp)
                        )
                    }
                }
            }

            // Input area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2E))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Emoji toggle
                IconButton(
                    onClick = { showEmojiPicker = !showEmojiPicker },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (showEmojiPicker) Icons.Outlined.Keyboard else Icons.Outlined.EmojiEmotions,
                        contentDescription = "Emoji",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Text input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF2196F3)),
                        maxLines = 4,
                        decorationBox = { innerTextField ->
                            if (commentText.isEmpty()) {
                                Text(
                                    text = "Написати відгук...",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                // Send button
                AnimatedVisibility(
                    visible = commentText.isNotBlank(),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                onAddComment(commentText.trim())
                                commentText = ""
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2196F3))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: StoryComment,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = comment.userData?.avatar,
            contentDescription = null,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userData?.name ?: "Unknown",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatStoryTime(comment.time),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
            Text(
                text = comment.text,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        if (comment.userId == UserSession.userId) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Viewers Bottom Sheet
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewersSheet(
    viewers: List<com.worldmates.messenger.data.model.StoryViewer>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Переглянули",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${viewers.size}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (viewers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Поки ніхто не переглянув",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(viewers, key = { it.userId }) { viewer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = viewer.avatar,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = viewer.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatStoryTime(viewer.time),
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Video Player (ExoPlayer)
// ═══════════════════════════════════════════════════════════════════════════════

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var errorState by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember(videoUrl) {
        try {
            val okHttpClient = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply {
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            Log.e(TAG, "ExoPlayer error: ${error.errorCodeName}", error)
                            errorState = error.message
                        }
                    })
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ExoPlayer", e)
            errorState = e.message
            null
        }
    }

    LaunchedEffect(videoUrl) {
        exoPlayer?.apply {
            try {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set media item", e)
                errorState = e.message
            }
        }
    }

    DisposableEffect(videoUrl) {
        onDispose { exoPlayer?.release() }
    }

    Box(modifier = modifier) {
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
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Не вдалося відтворити відео",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        } else if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Utilities
// ═══════════════════════════════════════════════════════════════════════════════

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
