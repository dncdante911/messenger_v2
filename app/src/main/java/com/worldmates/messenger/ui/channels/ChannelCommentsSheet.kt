package com.worldmates.messenger.ui.channels

import android.content.Context
import com.worldmates.messenger.util.toFullMediaUrl
import com.worldmates.messenger.ui.components.formatting.FormattedText
import com.worldmates.messenger.ui.components.formatting.FormattingSettings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.R

// ==================== COMMENT CARD ====================

@Composable
fun CommentCard(
    comment: ChannelComment,
    onReactionClick: (String) -> Unit = {},
    onReplyClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    canDelete: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                // Author Avatar
                Box(
                    modifier = Modifier
                        .size(32.dp)
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
                        text = "U",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Author name and time
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "User",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatPostTime(comment.time),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    // Comment text
                    Text(
                        text = comment.text,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // Actions
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TextButton(
                            onClick = { onReactionClick("") },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                stringResource(R.string.ch_reaction_action),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        TextButton(
                            onClick = onReplyClick,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                stringResource(R.string.ch_reply_action),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            if (canDelete) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SmallReactionChip(
    emoji: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = emoji, fontSize = 12.sp)
            Text(
                text = count.toString(),
                fontSize = 11.sp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== COMMENTS COMPONENTS ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    post: ChannelPost?,
    comments: List<ChannelComment>,
    isLoading: Boolean,
    currentUserId: Long,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit,
    onAddCommentWithReply: ((String, Long?) -> Unit)? = null,
    onDeleteComment: (Long) -> Unit,
    onCommentReaction: (commentId: Long, emoji: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var commentText by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var replyingToComment by remember { mutableStateOf<ChannelComment?>(null) }
    var userActionsComment by remember { mutableStateOf<ChannelComment?>(null) }
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = cs.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cs.onSurfaceVariant.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 8.dp, top = 2.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ch_comments),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                    if (comments.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = cs.error.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "${comments.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        modifier = Modifier.size(20.dp),
                        tint = cs.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))

            // ── Comment list (fills remaining space) ──
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            repeat(4) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(cs.surfaceVariant.copy(alpha = 0.5f))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            Modifier.width(90.dp).height(10.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(cs.surfaceVariant.copy(alpha = 0.5f))
                                        )
                                        Box(
                                            Modifier.fillMaxWidth(0.65f).height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(cs.surfaceVariant.copy(alpha = 0.35f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                    comments.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = cs.onSurface.copy(alpha = 0.25f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.ch_no_comments),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = stringResource(R.string.ch_no_comments_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurface.copy(alpha = 0.35f)
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(comments, key = { it.id }) { comment ->
                                PremiumCommentItem(
                                    comment = comment,
                                    canDelete = isAdmin || comment.userId == currentUserId,
                                    onDeleteClick = { onDeleteComment(comment.id) },
                                    onReactionClick = { emoji -> onCommentReaction(comment.id, emoji) },
                                    onReply = { replyingToComment = it },
                                    onUserMenu = { userActionsComment = it },
                                    replyToComment = comment.replyToCommentId?.let { rid ->
                                        comments.find { it.id == rid }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Reply banner (above input) ──
            AnimatedVisibility(
                visible = replyingToComment != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                replyingToComment?.let { replying ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cs.primaryContainer.copy(alpha = 0.2f))
                            .padding(start = 16.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cs.primary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = replying.userName ?: replying.username ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.primary
                            )
                            Text(
                                text = replying.text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = cs.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(
                            onClick = { replyingToComment = null },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // ── Emoji picker (slides in above input bar) ──
            AnimatedVisibility(
                visible = showEmojiPicker,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
                com.worldmates.messenger.ui.components.EmojiPicker(
                    onEmojiSelected = { emoji -> commentText += emoji },
                    onDismiss = { showEmojiPicker = false }
                )
            }

            // ── Input bar (always pinned at bottom) ──
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.25f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emoji toggle
                IconButton(
                    onClick = { showEmojiPicker = !showEmojiPicker },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (showEmojiPicker) Icons.Default.Keyboard
                        else Icons.Outlined.EmojiEmotions,
                        contentDescription = null,
                        tint = if (showEmojiPicker) cs.primary
                               else cs.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Text input — flat, no outline border
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = cs.onSurface
                        ),
                        maxLines = 4,
                        decorationBox = { inner ->
                            if (commentText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.ch_comment_placeholder),
                                    fontSize = 14.sp,
                                    color = cs.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            inner()
                        }
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Send button — blue circle when text present, muted mic when empty
                val sendEnabled = commentText.isNotBlank()
                val sendBg by animateColorAsState(
                    targetValue = if (sendEnabled) cs.primary else cs.surfaceVariant,
                    label = "sendBg"
                )
                Surface(
                    onClick = {
                        if (sendEnabled) {
                            if (onAddCommentWithReply != null) {
                                onAddCommentWithReply(commentText, replyingToComment?.id)
                            } else {
                                onAddComment(commentText)
                            }
                            commentText = ""
                            replyingToComment = null
                            showEmojiPicker = false
                        }
                    },
                    enabled = sendEnabled,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = sendBg
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (sendEnabled) Icons.Default.Send else Icons.Outlined.Mic,
                            contentDescription = null,
                            tint = if (sendEnabled) Color.White
                                   else cs.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // User actions sheet
    userActionsComment?.let { target ->
        com.worldmates.messenger.ui.components.CommentUserActionsSheet(
            userId = target.userId,
            username = target.userName ?: target.username ?: "User",
            avatar = target.userAvatar,
            isOwnComment = target.userId == currentUserId,
            isAdmin = isAdmin,
            context = "channel",
            commentText = target.text,
            onDismiss = { userActionsComment = null },
            onAction = { action ->
                when (action) {
                    is com.worldmates.messenger.ui.components.CommentUserAction.Reply ->
                        replyingToComment = target
                    is com.worldmates.messenger.ui.components.CommentUserAction.Mention ->
                        commentText = "@${target.username ?: ""} $commentText"
                    else -> {}
                }
            }
        )
    }
}

@Composable
fun PremiumCommentItem(
    comment: ChannelComment,
    canDelete: Boolean,
    onDeleteClick: () -> Unit,
    onReactionClick: (String) -> Unit = {},
    onReply: ((ChannelComment) -> Unit)? = null,
    onUserMenu: ((ChannelComment) -> Unit)? = null,
    replyToComment: ChannelComment? = null,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayName = comment.userName ?: comment.username ?: "User #${comment.userId}"
    var showActionMenu by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    val maxSwipe = 64f

    val nameColor = remember(comment.userId) {
        val palette = listOf(
            Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935),
            Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFFF4511E),
            Color(0xFF3949AB), Color(0xFF00897B)
        )
        palette[(comment.userId % palette.size).toInt().coerceAtLeast(0)]
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (offsetX > 10f) {
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = null,
                tint = cs.primary.copy(alpha = (offsetX / maxSwipe).coerceIn(0.15f, 0.9f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(20.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(comment.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > maxSwipe / 2) onReply?.invoke(comment)
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, drag ->
                            offsetX = (offsetX + drag).coerceIn(0f, maxSwipe)
                        }
                    )
                }
                .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar 34dp
            if (!comment.userAvatar.isNullOrEmpty()) {
                AsyncImage(
                    model = comment.userAvatar.toFullMediaUrl(),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(nameColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        color = nameColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Bubble
            Surface(
                modifier = Modifier
                    .widthIn(min = 140.dp, max = 290.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true)
                    ) { showActionMenu = true },
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = 4.dp, bottomEnd = 16.dp
                ),
                color = cs.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    // Name + optional badge in same row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = nameColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (comment.writtenAsChannel) {
                            Spacer(Modifier.width(5.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = nameColor.copy(alpha = 0.13f)
                            ) {
                                Text(
                                    text = stringResource(R.string.ch_identity_as_channel_badge),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = nameColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    if (!comment.writtenAsChannel && !comment.channelName.isNullOrEmpty()) {
                        Text(
                            text = "@ ${comment.channelName}",
                            fontSize = 11.sp,
                            color = cs.primary.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Reply quote
                    if (replyToComment != null) {
                        val replyName = replyToComment.userName
                            ?: replyToComment.username
                            ?: stringResource(R.string.ch_user_fallback)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 5.dp, bottom = 3.dp)
                                .height(IntrinsicSize.Min)
                                .clip(RoundedCornerShape(6.dp))
                                .background(cs.surface.copy(alpha = 0.45f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(cs.primary)
                            )
                            Column(
                                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(replyName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
                                Text(replyToComment.text, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = cs.onSurface.copy(alpha = 0.55f))
                            }
                        }
                    }

                    // Comment text
                    Text(
                        text = comment.text,
                        fontSize = 14.sp,
                        color = cs.onSurface,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )

                    // Bottom row: reactions (left) + time (right)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                        horizontalArrangement = if (comment.reactionsCount > 0)
                            Arrangement.SpaceBetween else Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (comment.reactionsCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = cs.primary.copy(alpha = 0.10f),
                                onClick = { onReactionClick("❤️") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("❤️", fontSize = 11.sp)
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        "${comment.reactionsCount}",
                                        fontSize = 11.sp,
                                        color = cs.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Text(
                            text = formatPostTime(comment.time, context),
                            fontSize = 10.sp,
                            color = cs.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }

    // Action dialog
    if (showActionMenu) {
        AlertDialog(
            onDismissRequest = { showActionMenu = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = cs.surface,
            title = null,
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("👍", "❤️", "🔥", "😂", "😮", "😢").forEach { emoji ->
                            Surface(
                                onClick = { onReactionClick(emoji); showActionMenu = false },
                                shape = CircleShape,
                                color = cs.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = cs.outlineVariant.copy(alpha = 0.3f))
                    TextButton(onClick = { onReply?.invoke(comment); showActionMenu = false }, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Reply, null, modifier = Modifier.size(18.dp), tint = cs.onSurface.copy(alpha = 0.7f))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.action_reply), color = cs.onSurface)
                        }
                    }
                    if (onUserMenu != null) {
                        TextButton(onClick = { onUserMenu.invoke(comment); showActionMenu = false }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Person, null, modifier = Modifier.size(18.dp), tint = cs.onSurface.copy(alpha = 0.7f))
                                Spacer(Modifier.width(12.dp))
                                Text(displayName, color = cs.onSurface)
                            }
                        }
                    }
                    if (canDelete) {
                        TextButton(onClick = { onDeleteClick(); showActionMenu = false }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(18.dp), tint = cs.error)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.action_delete), color = cs.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun CommentItem(
    comment: ChannelComment,
    canDelete: Boolean,
    onDeleteClick: () -> Unit,
    onReactionClick: (String) -> Unit = {},
    onReply: ((ChannelComment) -> Unit)? = null,
    onUserMenu: ((ChannelComment) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    PremiumCommentItem(
        comment = comment,
        canDelete = canDelete,
        onDeleteClick = onDeleteClick,
        onReactionClick = onReactionClick,
        onReply = onReply,
        onUserMenu = onUserMenu,
        modifier = modifier
    )
}
