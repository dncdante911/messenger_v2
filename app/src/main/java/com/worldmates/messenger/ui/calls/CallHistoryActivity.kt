package com.worldmates.messenger.ui.calls

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.CallHistoryItem
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import java.text.SimpleDateFormat
import java.util.*

class CallHistoryActivity : ComponentActivity() {

    private lateinit var viewModel: CallHistoryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[CallHistoryViewModel::class.java]

        setContent {
            WorldMatesThemedApp {
                CallHistoryScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onCallUser = { userId, name, avatar, callType ->
                        startActivity(Intent(this, CallsActivity::class.java).apply {
                            putExtra("recipientId", userId)
                            putExtra("recipientName", name)
                            putExtra("recipientAvatar", avatar)
                            putExtra("callType", callType)
                        })
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    viewModel: CallHistoryViewModel,
    onBack: () -> Unit,
    onCallUser: (userId: Long, name: String, avatar: String?, callType: String) -> Unit
) {
    val context = LocalContext.current
    val calls by viewModel.calls.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val error by viewModel.error.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }

    // Error handling
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Дзвінки", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (calls.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Очистити")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter tabs
            CallFilterTabs(
                currentFilter = currentFilter,
                onFilterChange = { viewModel.loadCalls(it) }
            )

            // Call list
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (calls.isEmpty() && !isLoading) {
                    EmptyCallsState(currentFilter)
                } else {
                    CallHistoryList(
                        calls = calls,
                        onCallClick = { call ->
                            if (!call.isGroupCall && call.otherUser != null) {
                                onCallUser(
                                    call.otherUser.userId,
                                    call.otherUser.displayName,
                                    call.otherUser.avatar,
                                    call.callType
                                )
                            }
                        },
                        onDeleteCall = { viewModel.deleteCall(it.id) },
                        onLoadMore = { viewModel.loadMore() }
                    )
                }
            }
        }
    }

    // Clear history dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистити історію") },
            text = { Text("Ви впевнені, що хочете видалити всю історію дзвінків?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("Очистити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }
}

/**
 * Вкладки фільтрів: Всі, Пропущені, Вхідні, Вихідні
 */
@Composable
fun CallFilterTabs(
    currentFilter: String,
    onFilterChange: (String) -> Unit
) {
    val filters = listOf(
        "all" to "Всі",
        "missed" to "Пропущені",
        "incoming" to "Вхідні",
        "outgoing" to "Вихідні"
    )

    ScrollableTabRow(
        selectedTabIndex = filters.indexOfFirst { it.first == currentFilter }.coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 16.dp,
        divider = {}
    ) {
        filters.forEach { (filter, label) ->
            Tab(
                selected = currentFilter == filter,
                onClick = { onFilterChange(filter) },
                text = {
                    Text(
                        text = label,
                        fontWeight = if (currentFilter == filter) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            )
        }
    }

    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
}

/**
 * Список дзвінків
 */
@Composable
fun CallHistoryList(
    calls: List<CallHistoryItem>,
    onCallClick: (CallHistoryItem) -> Unit,
    onDeleteCall: (CallHistoryItem) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()

    // Pagination
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= calls.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && calls.isNotEmpty()) {
            onLoadMore()
        }
    }

    // Group calls by date
    val groupedCalls = calls.groupBy { call ->
        getDateGroup(call.timestamp)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        groupedCalls.forEach { (dateLabel, dayCalls) ->
            // Date header
            item(key = "header_$dateLabel") {
                DateHeader(dateLabel)
            }

            // Call items
            items(
                items = dayCalls,
                key = { "${it.callCategory}_${it.id}" }
            ) { call ->
                CallHistoryItemRow(
                    call = call,
                    onClick = { onCallClick(call) },
                    onDelete = { onDeleteCall(call) }
                )
            }
        }
    }
}

/**
 * Заголовок дати
 */
@Composable
fun DateHeader(label: String) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * Рядок одного дзвінка
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryItemRow(
    call: CallHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Видалити",
                    tint = Color.White
                )
            }
        },
        enableDismissFromStartToEnd = false,
        content = {
            Surface(
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(modifier = Modifier.size(48.dp)) {
                        AsyncImage(
                            model = call.avatarUrl,
                            contentDescription = call.displayName,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        // Call type badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    if (call.isMissed) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (call.isVideoCall) Icons.Default.Videocam
                                else Icons.Default.Call,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Name + status
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = call.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (call.isMissed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Direction icon
                            Icon(
                                imageVector = if (call.isIncoming) Icons.Default.CallReceived
                                else Icons.Default.CallMade,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = when {
                                    call.isMissed -> MaterialTheme.colorScheme.error
                                    call.isIncoming -> Color(0xFF4CAF50)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )

                            Text(
                                text = call.getStatusText(),
                                fontSize = 13.sp,
                                color = if (call.isMissed) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            if (call.isGroupCall) {
                                Text(
                                    text = "Груповий",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Time + call button
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = formatCallTime(call.timestamp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Quick call button
                        if (!call.isGroupCall) {
                            Icon(
                                imageVector = if (call.isVideoCall) Icons.Default.Videocam
                                else Icons.Default.Call,
                                contentDescription = "Зателефонувати",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(onClick = onClick),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * Порожній стан
 */
@Composable
fun EmptyCallsState(filter: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = when (filter) {
                    "missed" -> Icons.Default.PhoneMissed
                    "incoming" -> Icons.Default.CallReceived
                    "outgoing" -> Icons.Default.CallMade
                    else -> Icons.Default.Call
                },
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when (filter) {
                    "missed" -> "Немає пропущених дзвінків"
                    "incoming" -> "Немає вхідних дзвінків"
                    "outgoing" -> "Немає вихідних дзвінків"
                    else -> "Історія дзвінків порожня"
                },
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Зателефонуйте друзям через WorldMates",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

// ==================== HELPER FUNCTIONS ====================

/**
 * Групування за датою
 */
private fun getDateGroup(timestamp: Long): String {
    val callDate = Calendar.getInstance().apply {
        timeInMillis = timestamp * 1000
    }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        isSameDay(callDate, today) -> "Сьогодні"
        isSameDay(callDate, yesterday) -> "Вчора"
        isSameWeek(callDate, today) -> {
            val dayFormat = SimpleDateFormat("EEEE", Locale("uk"))
            dayFormat.format(callDate.time).replaceFirstChar { it.uppercase() }
        }
        callDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> {
            SimpleDateFormat("d MMMM", Locale("uk")).format(callDate.time)
        }
        else -> {
            SimpleDateFormat("d MMMM yyyy", Locale("uk")).format(callDate.time)
        }
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isSameWeek(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR)
}

/**
 * Форматування часу дзвінка
 */
private fun formatCallTime(timestamp: Long): String {
    val callDate = Calendar.getInstance().apply {
        timeInMillis = timestamp * 1000
    }
    val today = Calendar.getInstance()

    return if (isSameDay(callDate, today)) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(callDate.time)
    } else {
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(callDate.time)
    }
}
