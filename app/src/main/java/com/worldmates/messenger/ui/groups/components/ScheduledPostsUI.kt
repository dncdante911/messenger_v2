package com.worldmates.messenger.ui.groups.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.ScheduledPost
import com.worldmates.messenger.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * Панель запланованих постів
 */
@Composable
fun ScheduledPostsPanel(
    scheduledPosts: List<ScheduledPost>,
    onCreateClick: () -> Unit,
    onEditClick: (ScheduledPost) -> Unit,
    onDeleteClick: (ScheduledPost) -> Unit,
    onPublishNowClick: (ScheduledPost) -> Unit,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.scheduled_posts_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.posts_in_queue, scheduledPosts.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isAdmin) {
                    IconButton(onClick = onCreateClick) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.create),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (scheduledPosts.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EventNote,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.no_scheduled_posts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isAdmin) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onCreateClick) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.create_post))
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                scheduledPosts.sortedBy { it.scheduledTime }.take(5).forEach { post ->
                    ScheduledPostItem(
                        post = post,
                        onEdit = { onEditClick(post) },
                        onDelete = { onDeleteClick(post) },
                        onPublishNow = { onPublishNowClick(post) },
                        isAdmin = isAdmin
                    )
                    if (post != scheduledPosts.last()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                if (scheduledPosts.size > 5) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.and_more_posts, scheduledPosts.size - 5),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledPostItem(
    post: ScheduledPost,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPublishNow: () -> Unit,
    isAdmin: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Time indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(50.dp)
        ) {
            val calendar = Calendar.getInstance().apply { timeInMillis = post.scheduledTime }
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(post.scheduledTime)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(post.scheduledTime)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content preview
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Media indicator
            if (post.mediaUrl != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (post.mediaType) {
                            "image" -> Icons.Default.Image
                            "video" -> Icons.Default.VideoLibrary
                            "audio" -> Icons.Default.AudioFile
                            else -> Icons.Default.AttachFile
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = post.mediaType ?: stringResource(R.string.media_type_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Repeat indicator
            if (post.repeatType != null && post.repeatType != "none") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (post.repeatType) {
                            "daily"   -> stringResource(R.string.schedule_repeat_daily)
                            "weekly"  -> stringResource(R.string.schedule_repeat_weekly)
                            "monthly" -> stringResource(R.string.schedule_repeat_monthly)
                            else      -> post.repeatType ?: ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Status badge
            val statusColor = when (post.status) {
                "scheduled", "pending" -> MaterialTheme.colorScheme.primary
                "published", "sent"    -> Color(0xFF4CAF50)
                "failed"               -> MaterialTheme.colorScheme.error
                "cancelled"            -> MaterialTheme.colorScheme.onSurfaceVariant
                else                   -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val statusScheduled  = stringResource(R.string.status_scheduled)
            val statusPublished  = stringResource(R.string.status_published)
            val statusFailed     = stringResource(R.string.status_failed)
            val statusCancelled  = stringResource(R.string.status_cancelled)
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = statusColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = when (post.status) {
                        "scheduled", "pending" -> statusScheduled
                        "published", "sent"    -> statusPublished
                        "failed"               -> statusFailed
                        "cancelled"            -> statusCancelled
                        else                   -> post.status
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Actions
        if (isAdmin && post.status == "scheduled") {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.options_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.publish_now)) },
                        onClick = {
                            showMenu = false
                            onPublishNow()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Send, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_action)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Діалог створення/редагування запланованого поста
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScheduledPostDialog(
    existingPost: ScheduledPost? = null,
    groupId: Long? = null,
    channelId: Long? = null,
    onDismiss: () -> Unit,
    onSave: (text: String, scheduledTime: Long, mediaUrl: String?, repeatType: String, isPinned: Boolean, notifyMembers: Boolean) -> Unit
) {
    val context = LocalContext.current

    var text by remember { mutableStateOf(existingPost?.text ?: "") }
    var scheduledTime by remember {
        mutableStateOf(existingPost?.scheduledTime ?: (System.currentTimeMillis() + 3600000))
    }
    var repeatType by remember { mutableStateOf(existingPost?.repeatType ?: "none") }
    var isPinned by remember { mutableStateOf(existingPost?.isPinned ?: false) }
    var notifyMembers by remember { mutableStateOf(existingPost?.notifyMembers ?: true) }

    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMediaType by remember { mutableStateOf<String?>(null) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedMediaUri = it
            selectedMediaType = if (FileUtils.isVideo(context, it)) "video" else "image"
        }
    }

    val calendar = remember { Calendar.getInstance().apply { timeInMillis = scheduledTime } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // Header
                TopAppBar(
                    title = {
                        Text(if (existingPost == null) stringResource(R.string.new_scheduled_post_title) else stringResource(R.string.edit_post_title))
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_cd))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                if (text.isNotBlank()) {
                                    onSave(text, scheduledTime, selectedMediaUri?.toString(), repeatType, isPinned, notifyMembers)
                                }
                            },
                            enabled = text.isNotBlank()
                        ) {
                            Text(stringResource(R.string.save_action))
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Text input
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.post_text)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        placeholder = { Text(stringResource(R.string.post_text_hint)) }
                    )

                    // Date/Time picker
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.schedule_time_of_pub),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Date picker
                                OutlinedButton(
                                    onClick = {
                                        showDatePicker(context, calendar) { newCalendar ->
                                            calendar.set(Calendar.YEAR, newCalendar.get(Calendar.YEAR))
                                            calendar.set(Calendar.MONTH, newCalendar.get(Calendar.MONTH))
                                            calendar.set(Calendar.DAY_OF_MONTH, newCalendar.get(Calendar.DAY_OF_MONTH))
                                            scheduledTime = calendar.timeInMillis
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                                            .format(Date(scheduledTime))
                                    )
                                }

                                // Time picker
                                OutlinedButton(
                                    onClick = {
                                        showTimePicker(context, calendar) { newCalendar ->
                                            calendar.set(Calendar.HOUR_OF_DAY, newCalendar.get(Calendar.HOUR_OF_DAY))
                                            calendar.set(Calendar.MINUTE, newCalendar.get(Calendar.MINUTE))
                                            scheduledTime = calendar.timeInMillis
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        SimpleDateFormat("HH:mm", Locale.getDefault())
                                            .format(Date(scheduledTime))
                                    )
                                }
                            }

                            // Quick time buttons
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    stringResource(R.string.schedule_quick_1h)       to 3600000L,
                                    stringResource(R.string.schedule_quick_3h)       to 10800000L,
                                    stringResource(R.string.schedule_quick_tomorrow) to 86400000L,
                                    stringResource(R.string.schedule_quick_week)     to 604800000L
                                ).forEach { (label, offset) ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            scheduledTime = System.currentTimeMillis() + offset
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Repeat options
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.schedule_repeat_label),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val repeatLabels = mapOf(
                                    "none"    to stringResource(R.string.schedule_repeat_none),
                                    "daily"   to stringResource(R.string.schedule_repeat_daily),
                                    "weekly"  to stringResource(R.string.schedule_repeat_weekly),
                                    "monthly" to stringResource(R.string.schedule_repeat_monthly)
                                )
                                repeatLabels.forEach { (type, label) ->
                                    FilterChip(
                                        selected = repeatType == type,
                                        onClick = { repeatType = type },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Options
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.schedule_options_label),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.schedule_pin_label),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(R.string.schedule_pin_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isPinned,
                                    onCheckedChange = { isPinned = it }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.schedule_notify_members),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(R.string.schedule_notify_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = notifyMembers,
                                    onCheckedChange = { notifyMembers = it }
                                )
                            }
                        }
                    }

                    // Media attachment
                    if (selectedMediaUri == null) {
                        OutlinedButton(
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.add_media))
                        }
                    } else {
                        // Preview of selected media
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedMediaType == "video") Icons.Default.VideoLibrary else Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedMediaUri!!.lastPathSegment ?: stringResource(R.string.badge_attachment),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                IconButton(
                                    onClick = {
                                        selectedMediaUri = null
                                        selectedMediaType = null
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.delete_action),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.edit), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Список всіх запланованих постів
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledPostsScreen(
    posts: List<ScheduledPost>,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: (ScheduledPost) -> Unit,
    onDeleteClick: (ScheduledPost) -> Unit,
    onPublishNowClick: (ScheduledPost) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scheduled_posts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = onCreateClick) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (posts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_scheduled_posts),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onCreateClick) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.create_post))
                    }
                }
            }
        } else {
            val todayStr    = stringResource(R.string.today_label)
            val tomorrowStr = stringResource(R.string.tomorrow_label)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Grouped by date
                val groupedPosts = posts.groupBy { post ->
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(post.scheduledTime))
                }

                groupedPosts.forEach { (date, dayPosts) ->
                    item {
                        Text(
                            text = formatDateHeader(date, todayStr, tomorrowStr),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(dayPosts.sortedBy { it.scheduledTime }) { post ->
                        ScheduledPostCard(
                            post = post,
                            onEdit = { onEditClick(post) },
                            onDelete = { onDeleteClick(post) },
                            onPublishNow = { onPublishNowClick(post) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledPostCard(
    post: ScheduledPost,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPublishNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Time
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(post.scheduledTime)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Status & repeat
                Column(modifier = Modifier.weight(1f)) {
                    val statusColor = when (post.status) {
                        "scheduled", "pending" -> MaterialTheme.colorScheme.primary
                        "published", "sent"    -> Color(0xFF4CAF50)
                        "failed"               -> MaterialTheme.colorScheme.error
                        else                   -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = when (post.status) {
                            "scheduled", "pending" -> stringResource(R.string.status_scheduled)
                            "published", "sent"    -> stringResource(R.string.status_published)
                            "failed"               -> stringResource(R.string.status_failed)
                            "cancelled"            -> stringResource(R.string.status_cancelled)
                            else                   -> post.status
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                    if (post.repeatType != null && post.repeatType != "none") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Repeat,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (post.repeatType) {
                                    "daily"   -> stringResource(R.string.schedule_repeat_daily)
                                    "weekly"  -> stringResource(R.string.schedule_repeat_weekly)
                                    "monthly" -> stringResource(R.string.schedule_repeat_monthly)
                                    else      -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Actions
                if (post.status == "scheduled" || post.status == "pending") {
                    IconButton(onClick = onPublishNow) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = stringResource(R.string.publish_cd),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_action),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Media indicator
            if (post.mediaUrl != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (post.mediaType) {
                            "image" -> Icons.Default.Image
                            "video" -> Icons.Default.VideoLibrary
                            else -> Icons.Default.AttachFile
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.badge_attachment),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Options badges
            if (post.isPinned || post.notifyMembers) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (post.isPinned) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.badge_pinned),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    if (post.notifyMembers) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.badge_notifications),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== UTILITY FUNCTIONS ====================

private fun showDatePicker(
    context: Context,
    calendar: Calendar,
    onDateSelected: (Calendar) -> Unit
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val newCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            onDateSelected(newCalendar)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).apply {
        datePicker.minDate = System.currentTimeMillis()
    }.show()
}

private fun showTimePicker(
    context: Context,
    calendar: Calendar,
    onTimeSelected: (Calendar) -> Unit
) {
    TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val newCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
            }
            onTimeSelected(newCalendar)
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true
    ).show()
}

private fun formatDateHeader(dateString: String, todayLabel: String, tomorrowLabel: String): String {
    return try {
        val inputFormat  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("d MMMM", Locale.getDefault())
        val date         = inputFormat.parse(dateString)

        val today    = Calendar.getInstance()
        val postDate = Calendar.getInstance().apply { time = date!! }

        when {
            today.get(Calendar.DAY_OF_YEAR) == postDate.get(Calendar.DAY_OF_YEAR) &&
                    today.get(Calendar.YEAR) == postDate.get(Calendar.YEAR)     -> todayLabel
            today.get(Calendar.DAY_OF_YEAR) + 1 == postDate.get(Calendar.DAY_OF_YEAR) &&
                    today.get(Calendar.YEAR) == postDate.get(Calendar.YEAR)     -> tomorrowLabel
            else -> outputFormat.format(date!!)
        }
    } catch (e: Exception) {
        dateString
    }
}
