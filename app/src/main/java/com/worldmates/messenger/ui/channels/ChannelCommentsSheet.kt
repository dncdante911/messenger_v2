package com.worldmates.messenger.ui.channels

import android.content.Context
import com.worldmates.messenger.util.toFullMediaUrl
import com.worldmates.messenger.ui.components.formatting.FormattedText
import com.worldmates.messenger.ui.components.formatting.FormattingSettings
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.PremiumTheme
import com.worldmates.messenger.ui.channels.premium.design.current
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.components.AnimatedStickerView

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
                        val listState = rememberLazyListState()
                        LaunchedEffect(comments.size) {
                            if (comments.isNotEmpty()) listState.animateScrollToItem(comments.size - 1)
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(comments, key = { it.id }) { comment ->
                                WMCommentBubble(
                                    comment = comment,
                                    isOwn = comment.userId == currentUserId,
                                    canDelete = isAdmin || comment.userId == currentUserId,
                                    replyToComment = comment.replyToCommentId?.let { rid ->
                                        comments.find { it.id == rid }
                                    },
                                    onReply = { replyingToComment = it },
                                    onDelete = { onDeleteComment(comment.id) },
                                    onReaction = { emoji -> onCommentReaction(comment.id, emoji) },
                                    onUserMenu = { userActionsComment = comment }
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
                    onDismiss = { showEmojiPicker = false },
                    onBackspace = { if (commentText.isNotEmpty()) commentText = commentText.dropLast(1) }
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
                .padding(start = 8.dp, end = 8.dp, top = 1.dp, bottom = 1.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar 28dp (compact)
            if (!comment.userAvatar.isNullOrEmpty()) {
                AsyncImage(
                    model = comment.userAvatar.toFullMediaUrl(),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(nameColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        color = nameColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Bubble (more visible, compacter)
            Surface(
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 280.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true)
                    ) { showActionMenu = true },
                shape = RoundedCornerShape(
                    topStart = 14.dp, topEnd = 14.dp,
                    bottomStart = 4.dp, bottomEnd = 14.dp
                ),
                color = cs.surfaceVariant.copy(alpha = 0.85f),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
                ) {
                    // Name + optional badge in same row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            fontSize = 12.sp,
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

                    // Sticker (animated emoji / Telegram sticker)
                    if (!comment.sticker.isNullOrEmpty()) {
                        AnimatedStickerView(
                            url = comment.sticker,
                            size = 120.dp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                    // Comment text
                    if (comment.text.isNotBlank()) {
                        Text(
                            text = comment.text,
                            fontSize = 13.sp,
                            color = cs.onSurface,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Bottom row: reactions (left) + time (right)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp),
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

// ==================== WM COMMENTS — WORLDMATES THEME (TELEGRAM-LIKE) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WMCommentsBottomSheet(
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
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(comments.size - 1)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = cs.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cs.onSurfaceVariant.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .navigationBarsPadding()
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.ch_comments),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                    if (comments.isNotEmpty()) {
                        Text(
                            text = "${comments.size}",
                            fontSize = 12.sp,
                            color = cs.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.3f))

            // ── Messages list ──
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> WMCommentsLoadingState()
                    comments.isEmpty() -> WMCommentsEmptyState()
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(comments, key = { it.id }) { comment ->
                                val isOwn = comment.userId == currentUserId
                                val replyTo = comment.replyToCommentId?.let { rid ->
                                    comments.find { it.id == rid }
                                }
                                WMCommentBubble(
                                    comment = comment,
                                    isOwn = isOwn,
                                    canDelete = isAdmin || isOwn,
                                    replyToComment = replyTo,
                                    onReply = { replyingToComment = it },
                                    onDelete = { onDeleteComment(comment.id) },
                                    onReaction = { emoji -> onCommentReaction(comment.id, emoji) },
                                    onUserMenu = { userActionsComment = comment }
                                )
                            }
                        }
                    }
                }
            }

            // ── Reply preview banner ──
            AnimatedVisibility(
                visible = replyingToComment != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                replyingToComment?.let { replying ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cs.primaryContainer.copy(alpha = 0.18f))
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cs.primary)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = replying.userName ?: replying.username ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
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
                        IconButton(onClick = { replyingToComment = null }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Close,
                                null,
                                tint = cs.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ── Emoji picker ──
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

            // ── Input bar ──
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.2f))
            WMCommentInputBar(
                text = commentText,
                onTextChange = { commentText = it },
                showEmojiPicker = showEmojiPicker,
                onEmojiToggle = { showEmojiPicker = !showEmojiPicker },
                onSend = {
                    if (commentText.isNotBlank()) {
                        if (onAddCommentWithReply != null) {
                            onAddCommentWithReply(commentText, replyingToComment?.id)
                        } else {
                            onAddComment(commentText)
                        }
                        commentText = ""
                        replyingToComment = null
                        showEmojiPicker = false
                    }
                }
            )
        }
    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WMCommentBubble(
    comment: ChannelComment,
    isOwn: Boolean,
    canDelete: Boolean,
    replyToComment: ChannelComment?,
    onReply: (ChannelComment) -> Unit,
    onDelete: () -> Unit,
    onReaction: (String) -> Unit,
    onUserMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val displayName = comment.userName ?: comment.username ?: "User #${comment.userId}"
    var showActions by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    val maxSwipe = 60f

    val nameColor = remember(comment.userId) {
        val palette = listOf(
            Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935),
            Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFFF4511E),
            Color(0xFF3949AB), Color(0xFF00897B)
        )
        palette[(comment.userId % palette.size).toInt().coerceAtLeast(0)]
    }

    val ownBubbleColor = Color(0xFF0A84FF)
    val bubbleBg = if (isOwn) ownBubbleColor else cs.surfaceVariant.copy(alpha = 0.7f)
    val textColor = if (isOwn) Color.White else cs.onSurface
    val timeColor = if (isOwn) Color.White.copy(alpha = 0.6f) else cs.onSurface.copy(alpha = 0.42f)

    Box(modifier = modifier.fillMaxWidth()) {
        // Swipe reply hint icon
        if (offsetX > 8f) {
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = null,
                tint = cs.primary.copy(alpha = (offsetX / maxSwipe).coerceIn(0.2f, 0.85f)),
                modifier = Modifier
                    .align(if (isOwn) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 10.dp)
                    .size(20.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(if (isOwn) -offsetX.roundToInt() else offsetX.roundToInt(), 0) }
                .pointerInput(comment.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > maxSwipe * 0.5f) onReply(comment)
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, drag ->
                            offsetX = (offsetX + if (isOwn) -drag else drag).coerceIn(0f, maxSwipe)
                        }
                    )
                }
                .padding(horizontal = 2.dp, vertical = 3.dp),
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            // Left avatar (others only)
            if (!isOwn) {
                if (!comment.userAvatar.isNullOrEmpty()) {
                    AsyncImage(
                        model = comment.userAvatar.toFullMediaUrl(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(nameColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            displayName.take(1).uppercase(),
                            color = nameColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(7.dp))
            }

            Column(
                horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 288.dp)
            ) {
                Surface(
                    onClick = { showActions = true },
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isOwn) 16.dp else 4.dp,
                        bottomEnd = if (isOwn) 4.dp else 16.dp
                    ),
                    color = bubbleBg,
                    shadowElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 11.dp, end = 11.dp, top = 7.dp, bottom = 6.dp
                        )
                    ) {
                        // Sender name — always shown in comment threads
                        Text(
                            text = displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOwn) Color.White.copy(alpha = 0.85f) else nameColor,
                            modifier = Modifier.padding(bottom = 1.dp)
                        )

                        // Reply quote
                        if (replyToComment != null) {
                            val replyName = replyToComment.userName
                                ?: replyToComment.username ?: "User"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 5.dp)
                                    .height(IntrinsicSize.Min)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isOwn) Color.White.copy(alpha = 0.12f)
                                        else cs.surface.copy(alpha = 0.5f)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(
                                            if (isOwn) Color.White.copy(alpha = 0.8f) else cs.primary
                                        )
                                )
                                Column(
                                    modifier = Modifier.padding(
                                        start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp
                                    )
                                ) {
                                    Text(
                                        text = replyName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOwn) Color.White.copy(alpha = 0.9f) else cs.primary
                                    )
                                    Text(
                                        text = replyToComment.text,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isOwn) Color.White.copy(alpha = 0.6f)
                                               else cs.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        // Text + timestamp in same row (Telegram style)
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = comment.text,
                                fontSize = 14.sp,
                                color = textColor,
                                lineHeight = 20.sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = formatPostTime(comment.time, context),
                                fontSize = 10.sp,
                                color = timeColor,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                    }
                }

                // Reaction chip below bubble
                if (comment.reactionsCount > 0) {
                    Spacer(Modifier.height(3.dp))
                    Surface(
                        onClick = { onReaction("❤️") },
                        shape = RoundedCornerShape(50),
                        color = cs.primary.copy(alpha = 0.10f),
                        modifier = Modifier.padding(
                            start = if (isOwn) 0.dp else 4.dp,
                            end = if (isOwn) 4.dp else 0.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("❤️", fontSize = 12.sp)
                            Text(
                                "${comment.reactionsCount}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.primary
                            )
                        }
                    }
                }
            }

            // Right avatar (own messages)
            if (isOwn) {
                Spacer(Modifier.width(7.dp))
                if (!comment.userAvatar.isNullOrEmpty()) {
                    AsyncImage(
                        model = comment.userAvatar.toFullMediaUrl(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0A84FF).copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            displayName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Action bottom sheet
    if (showActions) {
        val actionsSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            sheetState = actionsSheetState,
            containerColor = cs.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                // Quick emoji reactions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("👍", "❤️", "🔥", "😂", "😮", "😢").forEach { emoji ->
                        Surface(
                            onClick = { onReaction(emoji); showActions = false },
                            shape = CircleShape,
                            color = cs.surfaceVariant,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = cs.outlineVariant.copy(alpha = 0.3f)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_reply)) },
                    leadingContent = {
                        Icon(
                            Icons.Default.Reply,
                            null,
                            tint = cs.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    modifier = Modifier.clickable { onReply(comment); showActions = false }
                )

                if (!isOwn) {
                    ListItem(
                        headlineContent = { Text(displayName) },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Person,
                                null,
                                tint = cs.onSurface.copy(alpha = 0.65f),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        modifier = Modifier.clickable { onUserMenu(); showActions = false }
                    )
                }

                if (canDelete) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.action_delete), color = cs.error)
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                null,
                                tint = cs.error,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        modifier = Modifier.clickable { onDelete(); showActions = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun WMCommentInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    showEmojiPicker: Boolean,
    onEmojiToggle: () -> Unit,
    onSend: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val hasText = text.isNotBlank()
    val sendBg by animateColorAsState(
        targetValue = if (hasText) cs.primary else Color.Transparent,
        label = "sendBg"
    )
    val sendIconTint by animateColorAsState(
        targetValue = if (hasText) cs.onPrimary else cs.primary,
        label = "sendTint"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Emoji button — full primary color, no alpha fade
        IconButton(
            onClick = onEmojiToggle,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = if (showEmojiPicker) Icons.Default.Keyboard
                              else Icons.Outlined.EmojiEmotions,
                contentDescription = null,
                tint = if (showEmojiPicker) cs.primary else cs.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(26.dp)
            )
        }

        // Text input field
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp, max = 130.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(cs.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) {
                Text(
                    text = stringResource(R.string.ch_comment_placeholder),
                    fontSize = 15.sp,
                    color = cs.onSurface.copy(alpha = 0.38f)
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 15.sp,
                    color = cs.onSurface
                ),
                maxLines = 5
            )
        }

        Spacer(Modifier.width(8.dp))

        // Send / Mic button with animation
        Surface(
            onClick = onSend,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = sendBg,
            border = if (!hasText) androidx.compose.foundation.BorderStroke(
                1.5.dp, cs.primary.copy(alpha = 0.35f)
            ) else null
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = hasText,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.75f)) togetherWith
                                (fadeOut() + scaleOut(targetScale = 0.75f))
                    },
                    label = "sendMicSwitch"
                ) { textPresent ->
                    Icon(
                        imageVector = if (textPresent) Icons.Default.Send else Icons.Default.Mic,
                        contentDescription = null,
                        tint = sendIconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WMCommentsLoadingState() {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        repeat(5) { i ->
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (i % 2 == 0) Arrangement.Start else Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (i % 2 == 0) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .background(cs.surfaceVariant.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier
                        .width((120 + (i * 22)).dp)
                        .height(46.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 14.dp, topEnd = 14.dp,
                                bottomStart = if (i % 2 == 0) 4.dp else 14.dp,
                                bottomEnd = if (i % 2 == 0) 14.dp else 4.dp
                            )
                        )
                        .background(cs.surfaceVariant.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun WMCommentsEmptyState() {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(cs.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = cs.primary.copy(alpha = 0.5f)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ch_no_comments),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.ch_no_comments_sub),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurface.copy(alpha = 0.35f)
        )
    }
}

// ==================== WM CHANNEL COMMENTS FULL-SCREEN (CLASSIC THEME) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WMChannelCommentsScreen(
    post: com.worldmates.messenger.data.model.ChannelPost?,
    channelName: String,
    channelAvatarUrl: String?,
    comments: List<ChannelComment>,
    isLoading: Boolean,
    currentUserId: Long,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onAddCommentWithReply: (String, Long?) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onCommentReaction: (commentId: Long, emoji: String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var commentText by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var replyingToComment by remember { mutableStateOf<ChannelComment?>(null) }
    var userActionsComment by remember { mutableStateOf<ChannelComment?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(comments.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.ch_comments),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isLoading && comments.isNotEmpty()) {
                            Text(
                                text = "${comments.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = cs.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.surface,
                    titleContentColor = cs.onSurface
                )
            )
        },
        containerColor = cs.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Post preview pinned at top
            WMPostPreviewCard(post = post, channelName = channelName, channelAvatarUrl = channelAvatarUrl)
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.3f))

            // Comment list
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> WMCommentsLoadingState()
                    comments.isEmpty() -> WMCommentsEmptyState()
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(comments, key = { it.id }) { comment ->
                                val isOwn = comment.userId == currentUserId
                                val replyTo = comment.replyToCommentId?.let { rid ->
                                    comments.find { it.id == rid }
                                }
                                WMCommentBubble(
                                    comment = comment,
                                    isOwn = isOwn,
                                    canDelete = isAdmin || isOwn,
                                    replyToComment = replyTo,
                                    onReply = { replyingToComment = it },
                                    onDelete = { onDeleteComment(comment.id) },
                                    onReaction = { emoji -> onCommentReaction(comment.id, emoji) },
                                    onUserMenu = { userActionsComment = comment }
                                )
                            }
                        }
                    }
                }
            }

            // Reply banner
            AnimatedVisibility(
                visible = replyingToComment != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                replyingToComment?.let { replying ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cs.primaryContainer.copy(alpha = 0.18f))
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cs.primary)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = replying.userName ?: replying.username ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
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
                        IconButton(onClick = { replyingToComment = null }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Close,
                                null,
                                tint = cs.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Emoji picker
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

            // Input bar
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.2f))
            WMCommentInputBar(
                text = commentText,
                onTextChange = { commentText = it },
                showEmojiPicker = showEmojiPicker,
                onEmojiToggle = { showEmojiPicker = !showEmojiPicker },
                onSend = {
                    if (commentText.isNotBlank()) {
                        onAddCommentWithReply(commentText, replyingToComment?.id)
                        commentText = ""
                        replyingToComment = null
                        showEmojiPicker = false
                    }
                }
            )
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
private fun WMPostPreviewCard(
    post: com.worldmates.messenger.data.model.ChannelPost?,
    channelName: String,
    channelAvatarUrl: String?
) {
    if (post == null) return
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cs.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!channelAvatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = channelAvatarUrl.toFullMediaUrl(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cs.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channelName.take(1).uppercase(),
                        color = cs.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channelName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                when {
                    post.text.isNotEmpty() -> Text(
                        text = post.text,
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.55f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    !post.media.isNullOrEmpty() -> Text(
                        text = "📷 ${post.media!!.size} photo(s)",
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

// ==================== PREMIUM POST DETAIL SCREEN ====================

/**
 * Full-screen premium post detail + comments.
 * Obsidian dark background, gold accents, Telegram-style bubbles.
 * Layout:
 *   TopBar (back + title + views)
 *   LazyColumn:
 *     • Post hero (avatar, media, text, reactions)
 *     • Gold hairline
 *     • Comments header with count chip
 *     • PremiumCommentBubble items (own right/blue-gold, other left/glass)
 *   Reply banner (animated)
 *   PremiumCommentInputBar (always pinned at bottom)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumPostDetailScreen(
    post: ChannelPost,
    channelName: String,
    channelAvatarUrl: String?,
    comments: List<ChannelComment>,
    isLoadingComments: Boolean,
    currentUserId: Long,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onReactionClick: (String) -> Unit,
    onAddCommentWithReply: (String, Long?) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onCommentReaction: (commentId: Long, emoji: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    PremiumTheme {
        val design = PremiumDesign.current
        var commentText by remember { mutableStateOf("") }
        var showEmojiPicker by remember { mutableStateOf(false) }
        var replyingToComment by remember { mutableStateOf<ChannelComment?>(null) }
        var userActionsComment by remember { mutableStateOf<ChannelComment?>(null) }
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(comments.size) {
            if (comments.isNotEmpty()) {
                coroutineScope.launch { listState.animateScrollToItem(comments.size - 1) }
            }
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(design.colors.background)
                    .systemBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxSize().imePadding()) {

                    // ── TopBar ──────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(design.colors.surface)
                            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = null,
                                tint = design.colors.onPrimary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channelName.ifEmpty { stringResource(R.string.post_detail_title) },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = design.colors.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (post.viewsCount > 0) {
                                Text(
                                    text = "${post.viewsCount} views",
                                    fontSize = 12.sp,
                                    color = design.colors.onMuted
                                )
                            }
                        }
                        if (post.commentsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(PremiumBrushes.matteGoldLinear())
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${post.commentsCount}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = design.colors.onAccent
                                )
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(0.6.dp)
                            .background(PremiumBrushes.goldHairline())
                    )

                    // ── Content + comments ──────────────────────────────────
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        // Post header (avatar + name + time)
                        item {
                            PremiumDetailPostHeader(post = post, channelName = channelName, channelAvatarUrl = channelAvatarUrl, design = design)
                        }

                        // Media
                        if (!post.media.isNullOrEmpty()) {
                            item { PremiumDetailMediaGallery(media = post.media!!, design = design) }
                        }

                        // Post text
                        if (post.text.isNotBlank()) {
                            item {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    com.worldmates.messenger.ui.components.formatting.FormattedText(
                                        text = post.text,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        color = design.colors.onPrimary,
                                        quoteBarColor = design.colors.accent,
                                        quoteBackgroundColor = design.colors.glassFill,
                                        quoteBarWidth = 3.dp,
                                        quoteCornerRadius = 6.dp,
                                        settings = com.worldmates.messenger.ui.components.formatting.FormattingSettings(allowQuotes = true)
                                    )
                                }
                            }
                        }

                        // Reaction chips row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("👍", "❤️", "🔥", "😂", "😮", "😢").forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(design.colors.glassFill)
                                            .border(0.6.dp, PremiumBrushes.goldHairline(), RoundedCornerShape(50))
                                            .clickable { onReactionClick(emoji) }
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 18.sp)
                                    }
                                }
                            }
                        }

                        // Gold hairline + comments header
                        item {
                            Box(
                                Modifier.fillMaxWidth().height(0.6.dp).padding(top = 4.dp)
                                    .background(PremiumBrushes.goldHairline())
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    stringResource(R.string.ch_comments),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = design.colors.onPrimary
                                )
                                if (comments.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(PremiumBrushes.matteGoldLinear())
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            "${comments.size}", fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold, color = design.colors.onAccent
                                        )
                                    }
                                }
                            }
                        }

                        // Loading / empty / comments
                        when {
                            isLoadingComments -> item { PremiumCommentsLoadingState(design) }
                            comments.isEmpty() -> item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier.size(64.dp).clip(CircleShape)
                                            .background(design.colors.glassFill)
                                            .border(1.dp, PremiumBrushes.goldHairline(), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.ChatBubbleOutline, null,
                                            modifier = Modifier.size(30.dp), tint = design.colors.accent
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        stringResource(R.string.ch_no_comments),
                                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                        color = design.colors.onPrimary
                                    )
                                    Text(
                                        stringResource(R.string.ch_no_comments_sub),
                                        fontSize = 13.sp, color = design.colors.onMuted
                                    )
                                }
                            }
                            else -> items(comments, key = { it.id }) { comment ->
                                PremiumCommentBubble(
                                    comment = comment,
                                    isOwn = comment.userId == currentUserId,
                                    canDelete = isAdmin || comment.userId == currentUserId,
                                    replyToComment = comment.replyToCommentId?.let { rid ->
                                        comments.find { it.id == rid }
                                    },
                                    design = design,
                                    onReply = { replyingToComment = it },
                                    onDelete = { onDeleteComment(comment.id) },
                                    onReaction = { emoji -> onCommentReaction(comment.id, emoji) },
                                    onUserMenu = { userActionsComment = comment }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // ── Reply banner ─────────────────────────────────────────
                    AnimatedVisibility(
                        visible = replyingToComment != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        replyingToComment?.let { replying ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(design.colors.surface)
                                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.width(3.dp).height(34.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(design.colors.accent)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        replying.userName ?: replying.username ?: "",
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        color = design.colors.accent
                                    )
                                    Text(
                                        replying.text, fontSize = 12.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, color = design.colors.onMuted
                                    )
                                }
                                IconButton(onClick = { replyingToComment = null }, Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, null, tint = design.colors.onMuted, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }

                    // ── Emoji picker ─────────────────────────────────────────
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

                    // ── Input bar (always visible, never scrolls away) ───────
                    Box(
                        Modifier.fillMaxWidth().height(0.6.dp)
                            .background(PremiumBrushes.goldHairline())
                    )
                    PremiumCommentInputBar(
                        text = commentText,
                        onTextChange = { commentText = it },
                        design = design,
                        showEmojiPicker = showEmojiPicker,
                        onEmojiToggle = { showEmojiPicker = !showEmojiPicker },
                        onSend = {
                            if (commentText.isNotBlank()) {
                                onAddCommentWithReply(commentText, replyingToComment?.id)
                                commentText = ""
                                replyingToComment = null
                                showEmojiPicker = false
                            }
                        }
                    )
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
}

@Composable
private fun PremiumDetailPostHeader(
    post: ChannelPost,
    channelName: String,
    channelAvatarUrl: String?,
    design: PremiumDesign
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val displayAvatar = if (post.isAd) post.authorAvatar else channelAvatarUrl
        val displayName = channelName.ifEmpty { post.authorName ?: post.authorUsername ?: "" }

        if (!displayAvatar.isNullOrEmpty()) {
            AsyncImage(
                model = displayAvatar.toFullMediaUrl(),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, PremiumBrushes.goldHairline(), CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PremiumBrushes.matteGoldLinear()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    displayName.take(1).uppercase(),
                    color = design.colors.onAccent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = design.colors.onPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatPostTime(post.createdTime, context),
                fontSize = 12.sp, color = design.colors.onMuted
            )
        }

        if (post.isPinned) {
            Icon(
                Icons.Default.PushPin, null,
                tint = design.colors.accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun PremiumDetailMediaGallery(
    media: List<com.worldmates.messenger.data.model.PostMedia>,
    design: PremiumDesign
) {
    when (media.size) {
        1 -> AsyncImage(
            model = media[0].url.toFullMediaUrl(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            contentScale = ContentScale.Crop
        )
        2 -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            media.forEach { item ->
                AsyncImage(
                    model = item.url.toFullMediaUrl(), contentDescription = null,
                    modifier = Modifier.weight(1f).height(190.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
        else -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AsyncImage(
                model = media[0].url.toFullMediaUrl(), contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(190.dp),
                contentScale = ContentScale.Crop
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                media.drop(1).take(3).forEachIndexed { i, item ->
                    Box(modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = item.url.toFullMediaUrl(), contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                            contentScale = ContentScale.Crop
                        )
                        if (i == 2 && media.size > 4) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+${media.size - 4}", fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold, color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== PREMIUM COMMENTS SHEET ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumCommentsSheet(
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
    PremiumTheme {
        val design = PremiumDesign.current
        var commentText by remember { mutableStateOf("") }
        var showEmojiPicker by remember { mutableStateOf(false) }
        var replyingToComment by remember { mutableStateOf<ChannelComment?>(null) }
        var userActionsComment by remember { mutableStateOf<ChannelComment?>(null) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(comments.size) {
            if (comments.isNotEmpty()) {
                coroutineScope.launch { listState.animateScrollToItem(comments.size - 1) }
            }
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier,
            containerColor = design.colors.background,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PremiumBrushes.matteGoldLinear())
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f)
                    .navigationBarsPadding()
            ) {
                // Compact post preview with gold accent bar
                if (post != null) {
                    PremiumPostPreviewCompact(post = post, design = design)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.6.dp)
                            .background(PremiumBrushes.goldHairline())
                    )
                }

                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.ch_comments),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = design.colors.onPrimary
                        )
                        if (comments.isNotEmpty()) {
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(PremiumBrushes.matteGoldLinear())
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${comments.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = design.colors.onAccent
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = design.colors.onMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Comment list
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isLoading -> PremiumCommentsLoadingState(design)
                        comments.isEmpty() -> PremiumCommentsEmptyState(design)
                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(comments, key = { it.id }) { comment ->
                                PremiumCommentBubble(
                                    comment = comment,
                                    isOwn = comment.userId == currentUserId,
                                    canDelete = isAdmin || comment.userId == currentUserId,
                                    replyToComment = comment.replyToCommentId?.let { rid ->
                                        comments.find { it.id == rid }
                                    },
                                    design = design,
                                    onReply = { replyingToComment = it },
                                    onDelete = { onDeleteComment(comment.id) },
                                    onReaction = { emoji -> onCommentReaction(comment.id, emoji) },
                                    onUserMenu = { userActionsComment = comment }
                                )
                            }
                        }
                    }
                }

                // Reply banner
                AnimatedVisibility(
                    visible = replyingToComment != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    replyingToComment?.let { replying ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(design.colors.surface)
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(design.colors.accent)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    replying.userName ?: replying.username ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = design.colors.accent
                                )
                                Text(
                                    replying.text,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = design.colors.onMuted
                                )
                            }
                            IconButton(onClick = { replyingToComment = null }, Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Close, null,
                                    tint = design.colors.onMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // Emoji picker
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

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(PremiumBrushes.goldHairline())
                )
                PremiumCommentInputBar(
                    text = commentText,
                    onTextChange = { commentText = it },
                    design = design,
                    showEmojiPicker = showEmojiPicker,
                    onEmojiToggle = { showEmojiPicker = !showEmojiPicker },
                    onSend = {
                        if (commentText.isNotBlank()) {
                            if (onAddCommentWithReply != null) {
                                onAddCommentWithReply(commentText, replyingToComment?.id)
                            } else {
                                onAddComment(commentText)
                            }
                            commentText = ""
                            replyingToComment = null
                            showEmojiPicker = false
                        }
                    }
                )
            }
        }

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumCommentBubble(
    comment: ChannelComment,
    isOwn: Boolean,
    canDelete: Boolean,
    replyToComment: ChannelComment?,
    design: PremiumDesign,
    onReply: (ChannelComment) -> Unit,
    onDelete: () -> Unit,
    onReaction: (String) -> Unit,
    onUserMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayName = comment.userName ?: comment.username ?: "User #${comment.userId}"
    var showActions by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    val maxSwipe = 60f

    val nameColor = remember(comment.userId) {
        listOf(
            Color(0xFFE8B84B), Color(0xFFDAA520), Color(0xFFFFD700),
            Color(0xFFC5A028), Color(0xFFD4AF37), Color(0xFFF0C040),
            Color(0xFFE5C100), Color(0xFFB8860B)
        )[(comment.userId % 8).toInt().coerceAtLeast(0)]
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (offsetX > 8f) {
            Icon(
                Icons.Default.Reply, null,
                tint = design.colors.accent.copy(alpha = (offsetX / maxSwipe).coerceIn(0.2f, 0.9f)),
                modifier = Modifier
                    .align(if (isOwn) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 10.dp)
                    .size(20.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(if (isOwn) -offsetX.roundToInt() else offsetX.roundToInt(), 0) }
                .pointerInput(comment.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > maxSwipe * 0.5f) onReply(comment)
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, drag ->
                            offsetX = (offsetX + if (isOwn) -drag else drag).coerceIn(0f, maxSwipe)
                        }
                    )
                }
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            // Other's avatar (left)
            if (!isOwn) {
                if (!comment.userAvatar.isNullOrEmpty()) {
                    AsyncImage(
                        model = comment.userAvatar.toFullMediaUrl(),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(nameColor.copy(alpha = 0.2f))
                            .border(1.dp, nameColor.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            displayName.take(1).uppercase(),
                            color = nameColor, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(7.dp))
            }

            Column(
                horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 290.dp)
            ) {
                val bubbleShape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isOwn) 16.dp else 4.dp,
                    bottomEnd = if (isOwn) 4.dp else 16.dp
                )
                Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(if (isOwn) design.colors.accentDeep else design.colors.glassFill)
                        .then(
                            if (!isOwn) Modifier.border(0.6.dp, PremiumBrushes.goldHairline(), bubbleShape)
                            else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = design.colors.accent)
                        ) { showActions = true }
                        .padding(start = 11.dp, end = 11.dp, top = 7.dp, bottom = 6.dp)
                ) {
                    Column {
                        if (!isOwn) {
                            Text(
                                displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = nameColor, modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        // Reply quote
                        if (replyToComment != null) {
                            val replyName = replyToComment.userName ?: replyToComment.username ?: "User"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 5.dp)
                                    .height(IntrinsicSize.Min)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(design.colors.glassFillStrong)
                            ) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(design.colors.accent)
                                )
                                Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                                    Text(replyName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = design.colors.accent)
                                    Text(
                                        replyToComment.text, fontSize = 11.sp, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis, color = design.colors.onMuted
                                    )
                                }
                            }
                        }

                        if (!comment.sticker.isNullOrEmpty()) {
                            AnimatedStickerView(url = comment.sticker, size = 120.dp, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                        }

                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (comment.text.isNotBlank()) {
                                Text(
                                    comment.text, fontSize = 14.sp, lineHeight = 20.sp,
                                    color = design.colors.onPrimary,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            Text(
                                formatPostTime(comment.time, context), fontSize = 10.sp,
                                color = if (isOwn) design.colors.onAccent.copy(alpha = 0.6f) else design.colors.onMuted,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                    }
                }

                if (comment.reactionsCount > 0) {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(design.colors.accentSoft)
                            .border(0.5.dp, design.colors.accent.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .clickable { onReaction("❤️") }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("❤️", fontSize = 12.sp)
                            Text(
                                "${comment.reactionsCount}", fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold, color = design.colors.accent
                            )
                        }
                    }
                }
            }

            // Own avatar (right)
            if (isOwn) {
                Spacer(Modifier.width(7.dp))
                if (!comment.userAvatar.isNullOrEmpty()) {
                    AsyncImage(
                        model = comment.userAvatar.toFullMediaUrl(),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(design.colors.accentDeep),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(displayName.take(1).uppercase(), color = design.colors.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showActions) {
        val actionsSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            sheetState = actionsSheetState,
            containerColor = design.colors.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("👍", "❤️", "🔥", "😂", "😮", "😢").forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(design.colors.glassFill)
                                .border(0.6.dp, PremiumBrushes.goldHairline(), CircleShape)
                                .clickable { onReaction(emoji); showActions = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 24.sp)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.6.dp).background(PremiumBrushes.goldHairline()))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_reply), color = design.colors.onPrimary) },
                    leadingContent = { Icon(Icons.Default.Reply, null, tint = design.colors.accent, modifier = Modifier.size(20.dp)) },
                    colors = ListItemDefaults.colors(containerColor = design.colors.surface),
                    modifier = Modifier.clickable { onReply(comment); showActions = false }
                )
                if (!isOwn) {
                    ListItem(
                        headlineContent = { Text(displayName, color = design.colors.onPrimary) },
                        leadingContent = { Icon(Icons.Outlined.Person, null, tint = design.colors.onMuted, modifier = Modifier.size(20.dp)) },
                        colors = ListItemDefaults.colors(containerColor = design.colors.surface),
                        modifier = Modifier.clickable { onUserMenu(); showActions = false }
                    )
                }
                if (canDelete) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.action_delete), color = design.colors.danger) },
                        leadingContent = { Icon(Icons.Outlined.DeleteOutline, null, tint = design.colors.danger, modifier = Modifier.size(20.dp)) },
                        colors = ListItemDefaults.colors(containerColor = design.colors.surface),
                        modifier = Modifier.clickable { onDelete(); showActions = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumPostPreviewCompact(post: ChannelPost, design: PremiumDesign) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(design.colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(design.colors.accent)
        )
        Column(modifier = Modifier.weight(1f)) {
            when {
                post.text.isNotEmpty() -> Text(
                    post.text, fontSize = 13.sp, lineHeight = 18.sp,
                    color = design.colors.onPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                !post.media.isNullOrEmpty() -> Text(
                    "📷 ${post.media!!.size} media",
                    fontSize = 13.sp, color = design.colors.onMuted
                )
            }
        }
        if (!post.media.isNullOrEmpty()) {
            AsyncImage(
                model = post.media!!.first().url.toFullMediaUrl(),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.6.dp, PremiumBrushes.goldHairline(), RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun PremiumCommentInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    design: PremiumDesign,
    showEmojiPicker: Boolean,
    onEmojiToggle: () -> Unit,
    onSend: () -> Unit
) {
    val hasText = text.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(design.colors.background)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onEmojiToggle, modifier = Modifier.size(44.dp)) {
            Icon(
                if (showEmojiPicker) Icons.Default.Keyboard else Icons.Outlined.EmojiEmotions,
                null,
                tint = if (showEmojiPicker) design.colors.accent else design.colors.onMuted,
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp, max = 130.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(design.colors.glassFill)
                .border(0.8.dp, PremiumBrushes.goldHairline(), RoundedCornerShape(21.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) {
                Text(stringResource(R.string.ch_comment_placeholder), fontSize = 15.sp, color = design.colors.onMuted)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 15.sp,
                    color = design.colors.onPrimary
                ),
                maxLines = 5,
                cursorBrush = Brush.verticalGradient(listOf(design.colors.accent, design.colors.accent))
            )
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .then(
                    if (hasText) Modifier.background(PremiumBrushes.matteGoldLinear())
                    else Modifier
                        .background(design.colors.glassFill)
                        .border(1.dp, PremiumBrushes.goldHairline(), CircleShape)
                )
                .clickable { onSend() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = hasText,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.75f)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 0.75f))
                },
                label = "premiumSend"
            ) { present ->
                Icon(
                    if (present) Icons.Default.Send else Icons.Default.Mic,
                    null,
                    tint = if (present) design.colors.onAccent else design.colors.onMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumCommentsLoadingState(design: PremiumDesign) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(4) { i ->
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (i % 2 == 0) Arrangement.Start else Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (i % 2 == 0) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(design.colors.glassFill)
                            .border(1.dp, PremiumBrushes.goldHairline(), CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier
                        .width((100 + i * 30).dp)
                        .height(44.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (i % 2 == 0) 4.dp else 16.dp,
                                bottomEnd = if (i % 2 == 0) 16.dp else 4.dp
                            )
                        )
                        .background(design.colors.glassFill)
                        .border(0.6.dp, PremiumBrushes.goldHairline(), RoundedCornerShape(16.dp))
                )
            }
        }
    }
}

@Composable
private fun PremiumCommentsEmptyState(design: PremiumDesign) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(design.colors.glassFill)
                .border(1.dp, PremiumBrushes.goldHairline(), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline, null,
                modifier = Modifier.size(34.dp),
                tint = design.colors.accent
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.ch_no_comments),
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = design.colors.onPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.ch_no_comments_sub),
            fontSize = 13.sp, color = design.colors.onMuted
        )
    }
}

// ==================== WM EXPANDABLE FAB ====================

@Composable
fun WMExpandableFab(
    onCreatePost: () -> Unit,
    onCreatePoll: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(250),
        label = "fab_rot"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 }
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Create Post option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cs.surface,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = stringResource(R.string.create_post),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = { expanded = false; onCreatePost() },
                        containerColor = cs.primaryContainer,
                        contentColor = cs.onPrimaryContainer
                    ) {
                        Icon(Icons.Outlined.Article, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }

                // Create Poll option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cs.surface,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = stringResource(R.string.ch_create_poll),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = { expanded = false; onCreatePoll() },
                        containerColor = cs.secondaryContainer,
                        contentColor = cs.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.HowToVote, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Main FAB — rotates to X when expanded
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = cs.primary,
            contentColor = cs.onPrimary
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}
