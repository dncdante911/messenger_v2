package com.worldmates.messenger.ui.channels

import android.content.Intent
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
import com.worldmates.messenger.ui.channels.components.ChannelStatisticsCompactCard
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.util.toFullMediaUrl
import java.util.concurrent.TimeUnit
import com.worldmates.messenger.utils.LanguageManager

/**
 * Channel Admin Panel — unified admin interface with tabs
 * Tabs: Info | Settings | Stats | Admins | Members | Banned
 */
class ChannelAdminPanelActivity : AppCompatActivity() {

    private lateinit var detailsViewModel: ChannelDetailsViewModel
    private var channelId: Long = 0

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

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
        detailsViewModel.loadBannedMembers(channelId)

        setContent {
            WorldMatesThemedApp {
                ChannelAdminPanelScreen(
                    channelId = channelId,
                    viewModel = detailsViewModel,
                    onBack = { finish() },
                    onChannelDeleted = { finish() }
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
    onBack: () -> Unit,
    onChannelDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val channel by viewModel.channel.collectAsState()
    val statistics by viewModel.statistics.collectAsState()
    val admins by viewModel.admins.collectAsState()
    val subscribers by viewModel.subscribers.collectAsState()
    val bannedMembers by viewModel.bannedMembers.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Info", "Settings", "Stats", "Admins", "Members", "Banned")

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
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            if (index == 5 && bannedMembers.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${bannedMembers.size}") } }) {
                                    Text(title, fontSize = 13.sp)
                                }
                            } else {
                                Text(title, fontSize = 13.sp)
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> InfoTab(channel, viewModel, channelId, onChannelDeleted)
                1 -> SettingsTab(channel, viewModel, channelId)
                2 -> StatsTab(statistics, channel)
                3 -> AdminsTab(admins, viewModel, channelId)
                4 -> MembersTab(subscribers, viewModel, channelId)
                5 -> BannedTab(bannedMembers, viewModel, channelId)
            }
        }
    }
}

// ==================== INFO TAB ====================

@Composable
private fun InfoTab(
    channel: Channel?,
    viewModel: ChannelDetailsViewModel,
    channelId: Long,
    onChannelDeleted: () -> Unit
) {
    val context = LocalContext.current
    var editName by remember(channel) { mutableStateOf(channel?.name ?: "") }
    var editDesc by remember(channel) { mutableStateOf(channel?.description ?: "") }
    var editUsername by remember(channel) { mutableStateOf(channel?.username ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
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

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Delete channel — danger zone
        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Danger Zone",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFCC0000),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFCC0000)
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Channel")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFCC0000)) },
            title = { Text("Delete Channel") },
            text = {
                Text("Are you sure you want to permanently delete \"${channel?.name}\"? This action cannot be undone. All posts and subscriber data will be lost.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteChannel(
                            channelId = channelId,
                            onSuccess = {
                                Toast.makeText(context, "Channel deleted", Toast.LENGTH_SHORT).show()
                                onChannelDeleted()
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
            Text("Moderation", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        item { SettingToggle("Comment Moderation", "Comments require approval", moderation) { moderation = it } }
        item { SettingToggle("Author Signature", "Show author name on posts", signature) { signature = it } }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Notifications & Privacy", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
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
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
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
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ==================== STATS TAB ====================

@Composable
private fun StatsTab(statistics: ChannelStatistics?, channel: Channel?) {
    val context = LocalContext.current

    if (statistics == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Quick 4-cell overview
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickStatCell("Підписники", "${statistics.subscribersCount}",
                    trend = if (statistics.growthRate != 0f) "${if (statistics.growthRate > 0) "+" else ""}${statistics.growthRate}%" else null,
                    trendPositive = statistics.growthRate >= 0, modifier = Modifier.weight(1f))
                QuickStatCell("Перегляди", formatLargeNumber(statistics.viewsTotal.toInt()), modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickStatCell("Сер./пост", "${statistics.avgViewsPerPost}", modifier = Modifier.weight(1f))
                QuickStatCell("ER", "${statistics.engagementRate}%",
                    trendPositive = statistics.engagementRate >= 1f, modifier = Modifier.weight(1f))
            }
        }
        item {
            // "Детальна статистика" button → ChannelStatisticsActivity
            Button(
                onClick = {
                    val intent = Intent(context, ChannelStatisticsActivity::class.java).apply {
                        putExtra("channel_id", channel?.id ?: 0L)
                        putExtra("channel_name", channel?.name ?: "")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Детальна статистика")
            }
        }
        // Top posts preview (top 3)
        if (!statistics.topPosts.isNullOrEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Топ публікації", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            items(statistics.topPosts!!.take(3)) { post ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            val preview = post.text.trim().ifEmpty { if (post.hasMedia) "[Медіа]" else "[Пост]" }
                            Text(preview, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.Visibility, contentDescription = null,
                                    modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(formatLargeNumber(post.views), fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            if (post.reactions > 0 || post.comments > 0) {
                                Text("❤️${post.reactions}  💬${post.comments}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatCell(
    label: String,
    value: String,
    trend: String? = null,
    trendPositive: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            if (trend != null) {
                Text(trend, fontSize = 11.sp,
                    color = if (trendPositive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
            }
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

private fun formatLargeNumber(n: Int): String = when {
    n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
    n >= 1_000     -> "${"%.1f".format(n / 1_000.0)}K"
    else           -> n.toString()
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
                FilledTonalButton(onClick = { showAddDialog = true }, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }

        items(admins) { admin ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val context = LocalContext.current
    var memberForAction by remember { mutableStateOf<ChannelSubscriber?>(null) }
    var showBanDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                "Members (${subscribers.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (subscribers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No subscribers yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            items(subscribers, key = { it.userId ?: it.id ?: 0 }) { subscriber ->
                MemberRow(
                    subscriber = subscriber,
                    onBan = {
                        memberForAction = subscriber
                        showBanDialog = true
                    },
                    onKick = {
                        viewModel.kickMember(
                            channelId = channelId,
                            userId = subscriber.userId ?: return@MemberRow,
                            onSuccess = { Toast.makeText(context, "${subscriber.name ?: subscriber.username} kicked", Toast.LENGTH_SHORT).show() },
                            onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        )
                    },
                    onMakeAdmin = {
                        viewModel.addChannelAdmin(
                            channelId = channelId,
                            userSearch = subscriber.username ?: return@MemberRow,
                            role = "admin",
                            onSuccess = {
                                Toast.makeText(context, "${subscriber.name ?: subscriber.username} is now admin", Toast.LENGTH_SHORT).show()
                                viewModel.loadChannelDetails(channelId)
                            },
                            onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        )
                    }
                )
            }
        }
    }

    // Ban duration dialog
    if (showBanDialog && memberForAction != null) {
        BanDurationDialog(
            memberName = memberForAction!!.name ?: memberForAction!!.username ?: "User",
            onDismiss = {
                showBanDialog = false
                memberForAction = null
            },
            onBan = { durationSeconds, reason ->
                val target = memberForAction!!
                showBanDialog = false
                memberForAction = null
                viewModel.banMember(
                    channelId = channelId,
                    userId = target.userId ?: return@BanDurationDialog,
                    reason = reason,
                    durationSeconds = durationSeconds,
                    onSuccess = {
                        val desc = if (durationSeconds == 0) "permanently" else "temporarily"
                        Toast.makeText(context, "${target.name ?: target.username} banned $desc", Toast.LENGTH_SHORT).show()
                        viewModel.loadBannedMembers(channelId)
                    },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                )
            }
        )
    }
}

@Composable
private fun MemberRow(
    subscriber: ChannelSubscriber,
    onBan: () -> Unit,
    onKick: () -> Unit,
    onMakeAdmin: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isAdminOrOwner = subscriber.role == "owner" || subscriber.role == "admin"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { if (!isAdminOrOwner) showMenu = true }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = subscriber.avatarUrl?.toFullMediaUrl(),
            contentDescription = null,
            modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
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
                    color = when (subscriber.role) {
                        "owner" -> Color(0xFF4CAF50)
                        "admin" -> Color(0xFF2196F3)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }

        if (!isAdminOrOwner) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Actions",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Make Admin") },
                        leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onMakeAdmin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Kick") },
                        leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onKick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ban", color = Color(0xFFFF4444)) },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFF4444)) },
                        onClick = {
                            showMenu = false
                            onBan()
                        }
                    )
                }
            }
        }
    }
}

// ==================== BANNED TAB ====================

@Composable
private fun BannedTab(
    bannedMembers: List<ChannelBannedMember>,
    viewModel: ChannelDetailsViewModel,
    channelId: Long
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Banned Members (${bannedMembers.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (bannedMembers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No banned members", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            items(bannedMembers, key = { it.userId }) { banned ->
                BannedMemberRow(
                    banned = banned,
                    onUnban = {
                        viewModel.unbanMember(
                            channelId = channelId,
                            userId = banned.userId,
                            onSuccess = {
                                Toast.makeText(context, "${banned.name ?: banned.username} unbanned", Toast.LENGTH_SHORT).show()
                            },
                            onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BannedMemberRow(
    banned: ChannelBannedMember,
    onUnban: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF4444).copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = banned.avatarUrl?.toFullMediaUrl(),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = banned.name ?: banned.username ?: "User #${banned.userId}",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                val banInfo = if (banned.isPermanent) {
                    "Permanent ban"
                } else {
                    val remainingSecs = banned.expireTime - (System.currentTimeMillis() / 1000)
                    if (remainingSecs > 0) {
                        "Expires in ${formatBanDuration(remainingSecs)}"
                    } else {
                        "Expired"
                    }
                }
                Text(banInfo, fontSize = 12.sp, color = Color(0xFFFF4444))
                if (!banned.reason.isNullOrBlank()) {
                    Text(
                        "Reason: ${banned.reason}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            TextButton(
                onClick = onUnban,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Unban")
            }
        }
    }
}

private fun formatBanDuration(seconds: Long): String {
    return when {
        seconds >= TimeUnit.DAYS.toSeconds(1) -> "${seconds / TimeUnit.DAYS.toSeconds(1)}d"
        seconds >= TimeUnit.HOURS.toSeconds(1) -> "${seconds / TimeUnit.HOURS.toSeconds(1)}h"
        else -> "${seconds / 60}m"
    }
}

// ==================== DIALOGS ====================

/**
 * Dialog to select ban duration and enter optional reason.
 * Durations: 1h | 24h | 7d | 30d | Permanent
 */
@Composable
private fun BanDurationDialog(
    memberName: String,
    onDismiss: () -> Unit,
    onBan: (durationSeconds: Int, reason: String) -> Unit
) {
    data class BanOption(val label: String, val seconds: Int)

    val options = listOf(
        BanOption("1 hour",    3600),
        BanOption("24 hours",  86400),
        BanOption("7 days",    604800),
        BanOption("30 days",   2592000),
        BanOption("Permanent", 0)
    )
    var selectedOption by remember { mutableStateOf(options[1]) } // default: 24h
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFF4444)) },
        title = { Text("Ban $memberName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select ban duration:", fontSize = 14.sp, fontWeight = FontWeight.Medium)

                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedOption = option }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = { selectedOption = option }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            option.label,
                            fontSize = 14.sp,
                            color = if (option.seconds == 0) Color(0xFFFF4444) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onBan(selectedOption.seconds, reason.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
            ) {
                Text("Ban")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// AddAdminDialog is defined in ModernChannelPostComponents.kt
