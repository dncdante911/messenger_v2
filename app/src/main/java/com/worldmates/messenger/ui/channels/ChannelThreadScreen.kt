package com.worldmates.messenger.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.ThreadMessage
import com.worldmates.messenger.ui.components.AnimatedStickerView
import com.worldmates.messenger.ui.components.StickerPicker
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen thread (discussion) for a channel post.
 * Shows the post summary at the top, then thread messages, then input at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelThreadScreen(
    postId: Long,
    postText: String,
    postAuthorName: String,
    onBack: () -> Unit,
    viewModel: ChannelThreadViewModel = viewModel()
) {
    val messages    by viewModel.messages.collectAsState()
    val isLoading   by viewModel.isLoading.collectAsState()
    val error       by viewModel.error.collectAsState()
    val total       by viewModel.total.collectAsState()
    val listState   = rememberLazyListState()

    var inputText       by remember { mutableStateOf("") }
    var showStickerPicker by remember { mutableStateOf(false) }
    var replyToMessage  by remember { mutableStateOf<ThreadMessage?>(null) }
    var showDeleteDialog by remember { mutableStateOf<ThreadMessage?>(null) }
    var userActionsMsg  by remember { mutableStateOf<ThreadMessage?>(null) }

    val currentUserId = UserSession.userId ?: 0L

    LaunchedEffect(postId) {
        viewModel.loadThread(postId)
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.thread_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (total > 0) {
                            Text(
                                text = if (total == 1)
                                    stringResource(R.string.thread_replies_one, total)
                                else
                                    stringResource(R.string.thread_replies_count, total),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Reply indicator
                if (replyToMessage != null) {
                    ReplyBanner(
                        message = replyToMessage!!,
                        onDismiss = { replyToMessage = null }
                    )
                }
                // Sticker picker overlay
                if (showStickerPicker) {
                    StickerPicker(
                        onStickerSelected = { stickerUrl ->
                            viewModel.sendMessage(
                                postId    = postId,
                                text      = "",
                                replyToId = replyToMessage?.id,
                                sticker   = stickerUrl
                            ) { replyToMessage = null }
                            showStickerPicker = false
                        },
                        onDismiss = { showStickerPicker = false }
                    )
                }
                // Input row
                ThreadInputBar(
                    text               = inputText,
                    onTextChange       = { inputText = it },
                    showStickerPicker  = showStickerPicker,
                    onToggleSticker    = { showStickerPicker = !showStickerPicker },
                    onSend             = {
                        viewModel.sendMessage(
                            postId    = postId,
                            text      = inputText,
                            replyToId = replyToMessage?.id
                        ) {
                            inputText       = ""
                            replyToMessage  = null
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Original post summary
            ThreadPostHeader(
                text       = postText,
                authorName = postAuthorName
            )

            HorizontalDivider()

            // Error snackbar
            if (error != null) {
                Snackbar(
                    modifier = Modifier.padding(8.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                ) { Text(error!!) }
            }

            // Messages list
            if (isLoading && messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.thread_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.thread_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val replyParent = if (msg.replyToId != null)
                            messages.find { it.id == msg.replyToId }
                        else null

                        ThreadMessageBubble(
                            message        = msg,
                            replyParent    = replyParent,
                            isOwn          = msg.userId == currentUserId,
                            onReplyClick   = { replyToMessage = msg },
                            onDeleteClick  = if (msg.userId == currentUserId)
                                ({ showDeleteDialog = msg })
                            else null,
                            onUserMenu     = if (msg.userId != currentUserId)
                                ({ userActionsMsg = msg })
                            else null
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title   = { Text(stringResource(R.string.thread_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog?.let { msg ->
                            viewModel.deleteMessage(postId, msg.id)
                        }
                        showDeleteDialog = null
                    }
                ) { Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // User actions sheet for thread message author
    userActionsMsg?.let { target ->
        com.worldmates.messenger.ui.components.CommentUserActionsSheet(
            userId = target.userId,
            username = target.author?.name ?: target.author?.username ?: "User",
            avatar = target.author?.avatar,
            isOwnComment = false,
            context = "channel",
            commentText = target.text,
            onDismiss = { userActionsMsg = null },
            onAction = { action ->
                when (action) {
                    is com.worldmates.messenger.ui.components.CommentUserAction.Reply -> {
                        replyToMessage = target
                    }
                    is com.worldmates.messenger.ui.components.CommentUserAction.Mention -> {
                        inputText = "@${target.author?.username ?: ""} $inputText"
                    }
                    else -> {}
                }
            }
        )
    }
}

// ─── Components ───────────────────────────────────────────────────────────────

@Composable
private fun ThreadPostHeader(text: String, authorName: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.thread_from_post),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = authorName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ThreadMessageBubble(
    message: ThreadMessage,
    replyParent: ThreadMessage?,
    isOwn: Boolean,
    onReplyClick: () -> Unit,
    onDeleteClick: (() -> Unit)?,
    onUserMenu: (() -> Unit)? = null
) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwn)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val colorScheme = MaterialTheme.colorScheme

    var offsetX by remember { mutableStateOf(0f) }
    val maxSwipe = 90f

    Box(modifier = Modifier.fillMaxWidth()) {
        // Reply icon (з'являється при свайпі)
        if (offsetX > 16f) {
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = null,
                tint = colorScheme.primary.copy(alpha = (offsetX / maxSwipe).coerceIn(0f, 1f)),
                modifier = Modifier
                    .align(if (isOwn) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 12.dp)
                    .size(20.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > maxSwipe / 2) onReplyClick()
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, drag ->
                            offsetX = (offsetX + drag).coerceIn(0f, maxSwipe)
                        }
                    )
                },
            horizontalAlignment = alignment
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar (only for others)
                if (!isOwn) {
                    AsyncImage(
                        model = message.author?.avatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    )
                    Spacer(Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    // Author name (others only)
                    if (!isOwn) {
                        Text(
                            text = message.author?.name ?: message.author?.username ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 12.dp, topEnd = 12.dp,
                            bottomStart = if (isOwn) 12.dp else 4.dp,
                            bottomEnd   = if (isOwn) 4.dp else 12.dp
                        ),
                        color = bubbleColor,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            // Compact reply quote
                            if (replyParent != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 5.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colorScheme.primary.copy(alpha = 0.08f)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(36.dp)
                                            .background(
                                                colorScheme.primary,
                                                RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
                                            )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(top = 4.dp, bottom = 4.dp, end = 6.dp)
                                    ) {
                                        Text(
                                            text = replyParent.author?.name ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.primary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = replyParent.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Sticker (animated emoji / Telegram sticker)
                            if (!message.sticker.isNullOrEmpty()) {
                                AnimatedStickerView(
                                    url = message.sticker,
                                    size = 120.dp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            if (message.text.isNotBlank()) {
                                Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                text = formatMessageTime(message.time),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }

            // Delete only (reply moved to swipe)
            Row(
                modifier = Modifier.padding(horizontal = if (isOwn) 0.dp else 40.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onDeleteClick != null) {
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = colorScheme.error.copy(alpha = 0.6f)
                        )
                    }
                }
                if (!isOwn && onUserMenu != null) {
                    IconButton(onClick = onUserMenu, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyBanner(message: ThreadMessage, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.author?.name ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ThreadInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    showStickerPicker: Boolean = false,
    onToggleSticker: () -> Unit = {}
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sticker button
            IconButton(onClick = onToggleSticker) {
                Icon(
                    imageVector = if (showStickerPicker) Icons.Default.KeyboardArrowDown else Icons.Default.EmojiEmotions,
                    contentDescription = "Stickers",
                    tint = if (showStickerPicker) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.thread_placeholder)) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = text.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = stringResource(R.string.thread_send))
            }
        }
    }
}

// ─── "Threads" button for channel post cards ──────────────────────────────────

/**
 * Compact thread button shown in post cards (shows count, tapping opens thread).
 */
@Composable
fun PostThreadButton(
    postId: Long,
    replyCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            Icons.Default.Comment,
            contentDescription = stringResource(R.string.thread_open),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (replyCount > 0)
                stringResource(R.string.thread_replies_count, replyCount)
            else
                stringResource(R.string.thread_start),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun formatMessageTime(unixSec: Long): String {
    val ms = if (unixSec > 9999999999L) unixSec else unixSec * 1000L
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
}
