package com.worldmates.messenger.ui.channels

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.util.toFullMediaUrl

/**
 * Channel Admin Panel - unified admin interface with tabs
 */
class ChannelAdminPanelActivity : AppCompatActivity() {

    private lateinit var detailsViewModel: ChannelDetailsViewModel
    private var channelId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        channelId = intent.getLongExtra("channel_id", 0)
        if (channelId == 0L) {
            finish()
            return
        }

        ThemeManager.initialize(this)
        detailsViewModel = ViewModelProvider(this).get(ChannelDetailsViewModel::class.java)

        detailsViewModel.loadChannelDetails(channelId)
        detailsViewModel.loadStatistics(channelId)
        detailsViewModel.loadSubscribers(channelId)

        setContent {
            WorldMatesThemedApp {
                ChannelAdminPanelScreen(
                    channelId = channelId,
                    viewModel = detailsViewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelAdminPanelScreen(
    channelId: Long,
    viewModel: ChannelDetailsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val channel by viewModel.channel.collectAsState()
    val statistics by viewModel.statistics.collectAsState()
    val admins by viewModel.admins.collectAsState()
    val subscribers by viewModel.subscribers.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Info", "Settings", "Stats", "Admins", "Members")

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = channel?.name ?: "Admin Panel",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 13.sp) }
                    )
                }
            }

            // Tab content
            when (selectedTab) {
                0 -> InfoTab(channel, viewModel, channelId)
                1 -> SettingsTab(channel, viewModel, channelId)
                2 -> StatsTab(statistics, channel)
                3 -> AdminsTab(admins, viewModel, channelId)
                4 -> MembersTab(subscribers, viewModel, channelId)
            }
        }
    }
}

// ==================== INFO TAB ====================

@Composable
private fun InfoTab(
    channel: Channel?,
    viewModel: ChannelDetailsViewModel,
    channelId: Long
) {
    val context = LocalContext.current
    var editName by remember(channel) { mutableStateOf(channel?.name ?: "") }
    var editDesc by remember(channel) { mutableStateOf(channel?.description ?: "") }
    var editUsername by remember(channel) { mutableStateOf(channel?.username ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Channel avatar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = channel?.avatarUrl?.toFullMediaUrl(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Column {
                    Text(
                        text = channel?.name ?: "",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${channel?.subscribersCount ?: 0} subscribers",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${channel?.postsCount ?: 0} posts",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("Channel Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = editUsername,
                onValueChange = { editUsername = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                label = { Text("Username (@)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = { Text("@") }
            )
        }

        item {
            OutlinedTextField(
                value = editDesc,
                onValueChange = { editDesc = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )
        }

        item {
            Button(
                onClick = {
                    isSaving = true
                    viewModel.updateChannel(
                        channelId = channelId,
                        name = editName.takeIf { it.isNotBlank() },
                        description = editDesc,
                        username = editUsername.takeIf { it.isNotBlank() },
                        onSuccess = {
                            isSaving = false
                            Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            isSaving = false
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && editName.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save Changes")
            }
        }
    }
}

// ==================== SETTINGS TAB ====================

@Composable
private fun SettingsTab(
    channel: Channel?,
    viewModel: ChannelDetailsViewModel,
    channelId: Long
) {
    val context = LocalContext.current
    val currentSettings = channel?.settings ?: ChannelSettings()

    var allowComments by remember(currentSettings) { mutableStateOf(currentSettings.allowComments) }
    var allowReactions by remember(currentSettings) { mutableStateOf(currentSettings.allowReactions) }
    var allowShares by remember(currentSettings) { mutableStateOf(currentSettings.allowShares) }
    var showStats by remember(currentSettings) { mutableStateOf(currentSettings.showStatistics) }
    var notifyNew by remember(currentSettings) { mutableStateOf(currentSettings.notifySubscribersNewPost) }
    var signature by remember(currentSettings) { mutableStateOf(currentSettings.signatureEnabled) }
    var moderation by remember(currentSettings) { mutableStateOf(currentSettings.commentsModeration) }
    var allowForwarding by remember(currentSettings) { mutableStateOf(currentSettings.allowForwarding) }
    var isSaving by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                "Content Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        item { SettingToggle("Allow Comments", "Subscribers can comment on posts", allowComments) { allowComments = it } }
        item { SettingToggle("Allow Reactions", "Subscribers can react to posts", allowReactions) { allowReactions = it } }
        item { SettingToggle("Allow Shares", "Posts can be forwarded", allowShares) { allowShares = it } }
        item { SettingToggle("Allow Forwarding", "Posts can be forwarded to chats", allowForwarding) { allowForwarding = it } }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Moderation",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        item { SettingToggle("Comment Moderation", "Comments require approval", moderation) { moderation = it } }
        item { SettingToggle("Author Signature", "Show author name on posts", signature) { signature = it } }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Notifications & Privacy",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        item { SettingToggle("Notify on New Post", "Push notifications for new posts", notifyNew) { notifyNew = it } }
        item { SettingToggle("Show Statistics", "Subscribers can see view counts", showStats) { showStats = it } }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    isSaving = true
                    val settings = ChannelSettings(
                        allowComments = allowComments,
                        allowReactions = allowReactions,
                        allowShares = allowShares,
                        showStatistics = showStats,
                        notifySubscribersNewPost = notifyNew,
                        signatureEnabled = signature,
                        commentsModeration = moderation,
                        allowForwarding = allowForwarding
                    )
                    viewModel.updateChannelSettings(
                        channelId = channelId,
                        settings = settings,
                        onSuccess = {
                            isSaving = false
                            Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            isSaving = false
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save Settings")
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ==================== STATS TAB ====================

@Composable
private fun StatsTab(statistics: ChannelStatistics?, channel: Channel?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Channel Statistics",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (statistics == null) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("Subscribers", "${statistics.subscribersCount}", Icons.Default.People, Modifier.weight(1f))
                    StatCard("Posts", "${statistics.postsCount}", Icons.Default.Article, Modifier.weight(1f))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("This Week", "${statistics.postsLastWeek}", Icons.Default.DateRange, Modifier.weight(1f))
                    StatCard("Active 24h", "${statistics.activeSubscribers24h}", Icons.Default.Visibility, Modifier.weight(1f))
                }
            }

            if (!statistics.topPosts.isNullOrEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Top Posts", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(statistics.topPosts!!.take(5)) { topPost ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = topPost.text.take(80) + if (topPost.text.length > 80) "..." else "",
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${topPost.views}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).padding(start = 4.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

// ==================== ADMINS TAB ====================

@Composable
private fun AdminsTab(
    admins: List<ChannelAdmin>,
    viewModel: ChannelDetailsViewModel,
    channelId: Long
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Admins (${admins.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }

        items(admins) { admin ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = admin.avatarUrl.toFullMediaUrl(),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(admin.username, fontWeight = FontWeight.Medium)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (admin.role) {
                                "owner" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                "admin" -> Color(0xFF2196F3).copy(alpha = 0.15f)
                                else -> Color(0xFFFF9800).copy(alpha = 0.15f)
                            }
                        ) {
                            Text(
                                text = admin.role.replaceFirstChar { it.uppercase() },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when (admin.role) {
                                    "owner" -> Color(0xFF4CAF50)
                                    "admin" -> Color(0xFF2196F3)
                                    else -> Color(0xFFFF9800)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (admin.role != "owner") {
                        IconButton(onClick = {
                            viewModel.removeChannelAdmin(
                                channelId = channelId,
                                userId = admin.userId,
                                onSuccess = {
                                    Toast.makeText(context, "Removed", Toast.LENGTH_SHORT).show()
                                    viewModel.loadChannelDetails(channelId)
                                },
                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            )
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFFF4444))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAdminDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { search: String, role: String ->
                viewModel.addChannelAdmin(
                    channelId = channelId,
                    userSearch = search,
                    role = role,
                    onSuccess = {
                        Toast.makeText(context, "Admin added!", Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                        viewModel.loadChannelDetails(channelId)
                    },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                )
            }
        )
    }
}

// ==================== MEMBERS TAB ====================

@Composable
private fun MembersTab(
    subscribers: List<ChannelSubscriber>,
    viewModel: ChannelDetailsViewModel,
    channelId: Long
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text("Subscribers (${subscribers.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (subscribers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No subscribers yet",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            items(subscribers) { subscriber ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = subscriber.avatarUrl?.toFullMediaUrl(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subscriber.name ?: subscriber.username ?: "User #${subscriber.userId}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        if (subscriber.role != null && subscriber.role != "member") {
                            Text(
                                text = subscriber.role.replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (subscriber.isBanned) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF4444).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Banned",
                                fontSize = 11.sp,
                                color = Color(0xFFFF4444),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
