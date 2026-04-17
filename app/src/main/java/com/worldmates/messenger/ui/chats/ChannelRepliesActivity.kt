package com.worldmates.messenger.ui.chats

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.ChannelReply
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.ui.channels.ChannelDetailsActivity
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ChannelRepliesViewModel : ViewModel() {

    private val _replies = MutableStateFlow<List<ChannelReply>>(emptyList())
    val replies: StateFlow<List<ChannelReply>> = _replies

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    private var offset = 0
    private val pageSize = 30

    init { load(reset = true) }

    fun load(reset: Boolean = false) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            if (reset) offset = 0
            try {
                val resp = NodeRetrofitClient.channelApi.getReplyInbox(limit = pageSize, offset = offset)
                if (resp.apiStatus == 200) {
                    val list = resp.replies ?: emptyList()
                    _replies.value = if (reset) list else _replies.value + list
                    offset += list.size
                    _hasMore.value = list.size == pageSize
                }
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    fun sendReply(postId: Long, replyToId: Long, text: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                NodeRetrofitClient.channelApi.sendThreadReply(postId = postId, replyToId = replyToId, text = text)
            } catch (_: Exception) {}
            onDone()
        }
    }
}

// ── Activity ──────────────────────────────────────────────────────────────────

class ChannelRepliesActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        val viewModel = ViewModelProvider(this)[ChannelRepliesViewModel::class.java]

        setContent {
            WorldMatesThemedApp {
                ChannelRepliesScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onOpenChannel = { channelId ->
                        val intent = Intent(this, ChannelDetailsActivity::class.java)
                        intent.putExtra("channel_id", channelId)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelRepliesScreen(
    viewModel: ChannelRepliesViewModel,
    onBack: () -> Unit,
    onOpenChannel: (Long) -> Unit
) {
    val replies by viewModel.replies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val listState = rememberLazyListState()

    // Active reply composing state: which reply are we currently replying to?
    var replyTarget by remember { mutableStateOf<ChannelReply?>(null) }
    var replyText by remember { mutableStateOf("") }

    // Infinite scroll
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3 && info.totalItemsCount > 1
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !isLoading) viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.channel_replies_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            replyTarget?.let { target ->
                ReplyComposer(
                    target = target,
                    text = replyText,
                    onTextChange = { replyText = it },
                    onSend = {
                        val txt = replyText.trim()
                        if (txt.isNotEmpty()) {
                            viewModel.sendReply(
                                postId = target.postId,
                                replyToId = target.originalCommentId,
                                text = txt
                            ) { replyText = ""; replyTarget = null }
                        }
                    },
                    onDismiss = { replyTarget = null; replyText = "" }
                )
            }
        }
    ) { paddingValues ->
        if (replies.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📬",
                        fontSize = 48.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.channel_replies_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(replies, key = { it.id }) { reply ->
                    ChannelReplyItem(
                        reply = reply,
                        onReply = { replyTarget = reply },
                        onOpenChannel = { onOpenChannel(reply.channelId) }
                    )
                }
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Single reply card ─────────────────────────────────────────────────────────

@Composable
private fun ChannelReplyItem(
    reply: ChannelReply,
    onReply: () -> Unit,
    onOpenChannel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Channel header ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenChannel() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = reply.channelAvatar.ifBlank { null },
                    contentDescription = reply.channelName,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = reply.channelName.ifBlank { stringResource(R.string.channel_replies_unknown_channel) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatReplyTime(reply.time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Original comment (my comment) ─────────────────────────────────
            if (reply.originalCommentText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.channel_replies_your_comment),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = reply.originalCommentText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Reply from sender ─────────────────────────────────────────────
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = reply.senderAvatar.ifBlank { null },
                    contentDescription = reply.senderName,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reply.senderName.ifBlank { reply.senderUsername },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = reply.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Actions ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onOpenChannel) {
                    Text(
                        text = stringResource(R.string.channel_replies_open_channel),
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onReply) {
                    Text(
                        text = stringResource(R.string.channel_replies_reply),
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ── Reply composer bottom bar ─────────────────────────────────────────────────

@Composable
private fun ReplyComposer(
    target: ChannelReply,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column {
            // Reply context strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.channel_replies_replying_to, target.senderName.ifBlank { target.senderUsername }),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelSmall)
                }
            }
            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text(stringResource(R.string.channel_replies_type_hint), fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = stringResource(R.string.send),
                        tint = if (text.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// ── helpers ───────────────────────────────────────────────────────────────────

private fun formatReplyTime(ts: Long): String {
    if (ts <= 0) return ""
    val tsMs  = if (ts > 1_000_000_000_000L) ts else ts * 1000L
    val date  = Date(tsMs)
    val now   = System.currentTimeMillis()
    val diff  = now - tsMs
    return when {
        diff < 60_000L               -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        diff < 86_400_000L           -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        diff < 7 * 86_400_000L       -> SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(date)
        else                         -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(date)
    }
}
