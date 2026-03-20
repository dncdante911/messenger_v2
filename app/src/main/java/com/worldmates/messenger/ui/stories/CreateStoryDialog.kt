package com.worldmates.messenger.ui.stories

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.StoryLimits
import com.worldmates.messenger.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Design tokens ─────────────────────────────────────────────────────────────
private val BgDeep        = Color(0xFF0D0D18)
private val BgCard        = Color(0xFF161624)
private val AccentPurple  = Color(0xFF7C3AED)
private val AccentPink    = Color(0xFFEC4899)
private val AccentOrange  = Color(0xFFFF7043)
private val AccentBlue    = Color(0xFF5B87F6)
private val GoldColor     = Color(0xFFFFD700)
private val TextPrimary   = Color.White
private val TextMuted     = Color.White.copy(alpha = 0.45f)
private val SubtleBorder  = Color.White.copy(alpha = 0.08f)

private val StoryGradient = Brush.horizontalGradient(listOf(AccentPurple, AccentPink))
private val PhotoGradient = Brush.horizontalGradient(listOf(AccentPurple, AccentBlue))
private val VideoGradient = Brush.horizontalGradient(listOf(AccentPink, AccentOrange))

// ═════════════════════════════════════════════════════════════════════════════
// Main dialog
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStoryDialog(
    onDismiss: () -> Unit,
    viewModel: StoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var videoDurationSeconds by remember { mutableStateOf<Int?>(null) }

    val userLimits by viewModel.userLimits.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()

    val canCreate = viewModel.canCreateStory()
    val activeStoriesCount = viewModel.getActiveStoriesCount()
    val isPro = UserSession.isPro == 1

    val videoDurationExceedsLimit = isVideo &&
            videoDurationSeconds != null &&
            videoDurationSeconds!! > userLimits.maxVideoDuration

    // Extract video duration on IO thread
    LaunchedEffect(selectedMediaUri, isVideo) {
        if (isVideo && selectedMediaUri != null) {
            videoDurationSeconds = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, selectedMediaUri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.div(1000L)
                        ?.toInt()
                } catch (e: Exception) { null } finally { retriever.release() }
            }
        } else {
            videoDurationSeconds = null
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedMediaUri = it
            isVideo = FileUtils.isVideo(context, it)
            videoDurationSeconds = null
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedMediaUri = it
            isVideo = FileUtils.isVideo(context, it)
            videoDurationSeconds = null
        }
    }

    LaunchedEffect(success) {
        success?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccess()
            onDismiss()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    val publishEnabled = selectedMediaUri != null && !isLoading && canCreate && !videoDurationExceedsLimit

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(BgDeep)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp)
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted
                    )
                }
                Text(
                    text = stringResource(R.string.create_story),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                StoryLimitChip(
                    activeCount = activeStoriesCount,
                    max = userLimits.maxStories,
                    isPro = isPro,
                    canCreate = canCreate
                )
            }

            // ── Media zone ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(310.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .then(
                        if (selectedMediaUri == null)
                            Modifier.border(1.dp, SubtleBorder, RoundedCornerShape(20.dp))
                        else Modifier
                    )
            ) {
                // Empty picker
                AnimatedVisibility(
                    visible = selectedMediaUri == null,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(180))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            AccentPurple.copy(alpha = 0.22f),
                                            Color.Transparent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = AccentPurple,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Оберіть медіа для сторіс",
                            color = TextMuted,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MediaTypeButton(
                                icon = Icons.Default.Image,
                                label = stringResource(R.string.photo),
                                gradient = PhotoGradient,
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            MediaTypeButton(
                                icon = Icons.Default.VideoLibrary,
                                label = stringResource(R.string.video),
                                gradient = VideoGradient,
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                }
                            )
                        }
                    }
                }

                // Media preview
                AnimatedVisibility(
                    visible = selectedMediaUri != null,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(180))
                ) {
                    selectedMediaUri?.let { uri ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Selected media",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Duration error overlay at bottom
                            if (videoDurationExceedsLimit && videoDurationSeconds != null) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color(0xCC000000))
                                            )
                                        )
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.story_video_too_long,
                                            videoDurationSeconds!!,
                                            userLimits.maxVideoDuration
                                        ),
                                        color = Color(0xFFFF6B6B),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (!UserSession.isProActive) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = stringResource(
                                                R.string.story_video_too_long_pro_hint,
                                                StoryLimits.forProUser().maxVideoDuration
                                            ),
                                            color = GoldColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Video badge top-left
                            if (isVideo) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.62f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow, null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (videoDurationSeconds != null)
                                                stringResource(R.string.story_video_duration_badge, videoDurationSeconds!!)
                                            else stringResource(R.string.video),
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            // Remove button top-right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable {
                                        selectedMediaUri = null
                                        videoDurationSeconds = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close, null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Text inputs (shown when media is selected) ────────────────────
            AnimatedVisibility(
                visible = selectedMediaUri != null,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StoryInputField(
                        value = title,
                        placeholder = stringResource(R.string.story_title_hint),
                        onValueChange = { title = it }
                    )
                    StoryInputField(
                        value = description,
                        placeholder = stringResource(R.string.story_description_hint),
                        onValueChange = { description = it },
                        multiline = true
                    )
                }
            }

            // ── PRO hint ──────────────────────────────────────────────────────
            if (!isPro) {
                Text(
                    text = stringResource(R.string.story_pro_upgrade),
                    color = GoldColor.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Publish button ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        if (publishEnabled) StoryGradient
                        else Brush.horizontalGradient(
                            listOf(SubtleBorder, SubtleBorder)
                        )
                    )
                    .clickable(enabled = publishEnabled) {
                        selectedMediaUri?.let { uri ->
                            scope.launch {
                                viewModel.createStory(
                                    mediaUri = uri,
                                    fileType = if (isVideo) "video" else "image",
                                    title = title.ifBlank { null },
                                    description = description.ifBlank { null },
                                    videoDuration = videoDurationSeconds,
                                    coverUri = null
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            tint = if (publishEnabled) Color.White else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.publish_story),
                            color = if (publishEnabled) Color.White else TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Private helpers
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun StoryLimitChip(
    activeCount: Int,
    max: Int,
    isPro: Boolean,
    canCreate: Boolean
) {
    val bg = when {
        !canCreate -> Color(0xFFFF4757).copy(alpha = 0.15f)
        isPro      -> GoldColor.copy(alpha = 0.12f)
        else       -> Color.White.copy(alpha = 0.07f)
    }
    val textColor = when {
        !canCreate -> Color(0xFFFF4757)
        isPro      -> GoldColor
        else       -> Color.White.copy(alpha = 0.7f)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isPro) {
            Text("✦", color = GoldColor, fontSize = 10.sp)
        }
        Text(
            text = "$activeCount / $max",
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MediaTypeButton(
    icon: ImageVector,
    label: String,
    gradient: Brush,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(132.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StoryInputField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    multiline: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
        cursorBrush = SolidColor(AccentPurple),
        maxLines = if (multiline) 4 else 1,
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(text = placeholder, color = TextMuted, fontSize = 15.sp)
            }
            innerTextField()
        }
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// Public composables kept for API compatibility
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Compact limits info row — shows story count, video duration, and expiry as chips.
 * Kept public for use in CreateChannelStoryDialog.
 */
@Composable
fun LimitsInfoCard(
    activeCount: Int,
    maxStories: Int,
    maxVideoDuration: Int,
    expireHours: Int,
    isPro: Boolean,
    canCreate: Boolean
) {
    val storiesColor = when {
        !canCreate -> Color(0xFFFF4757)
        isPro      -> GoldColor
        else       -> AccentPurple
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsChip(
                label = "$activeCount / $maxStories",
                icon = Icons.Default.Image,
                color = storiesColor,
                modifier = Modifier.weight(1f)
            )
            StatsChip(
                label = "${maxVideoDuration}с",
                icon = Icons.Default.VideoLibrary,
                color = AccentBlue,
                modifier = Modifier.weight(1f)
            )
            StatsChip(
                label = "${expireHours}год",
                icon = Icons.Default.PlayArrow,
                color = Color(0xFF22C55E),
                modifier = Modifier.weight(1f)
            )
        }

        if (!isPro) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.story_pro_upgrade),
                color = GoldColor.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatsChip(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Media picker button — kept public for API compatibility.
 */
@Composable
fun MediaPickerButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
            .background(BgCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = AccentPurple, modifier = Modifier.size(24.dp))
            }
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

/**
 * Media preview — kept public for API compatibility.
 */
@Composable
fun MediaPreview(
    uri: Uri,
    isVideo: Boolean,
    onRemove: () -> Unit,
    videoDurationSeconds: Int? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Selected media",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (isVideo) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.62f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(
                        text = if (videoDurationSeconds != null)
                            stringResource(R.string.story_video_duration_badge, videoDurationSeconds)
                        else stringResource(R.string.video),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
