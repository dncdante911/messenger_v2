package com.worldmates.messenger.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.network.GlobalSearchResult
import com.worldmates.messenger.network.UserSearchResult
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.ui.messages.MessagesActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen global search — searches messages (private + group) and users.
 * Opened from the navigation drawer "Search" item.
 */
class GlobalSearchActivity : ComponentActivity() {

    private val viewModel: GlobalSearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorldMatesThemedApp {
                GlobalSearchScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onOpenChat = { userId ->
                        val intent = Intent(this, MessagesActivity::class.java).apply {
                            putExtra("recipient_id", userId)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, GlobalSearchActivity::class.java)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalSearchScreen(
    viewModel: GlobalSearchViewModel,
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.search_tab_messages),
        stringResource(R.string.search_tab_users),
    )
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value         = uiState.query,
                            onValueChange = { viewModel.onQueryChanged(it) },
                            placeholder   = { Text(stringResource(R.string.search_placeholder)) },
                            singleLine    = true,
                            modifier      = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            trailingIcon = {
                                if (uiState.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = null)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor   = Color.Transparent,
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected = selectedTab == i,
                            onClick  = { selectedTab = i },
                            text     = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.query.length < 2 -> {
                    EmptySearchHint()
                }
                selectedTab == 0 -> {
                    MessageResultsList(
                        results    = uiState.messageResults,
                        query      = uiState.query,
                        onItemClick = { result -> onOpenChat(result.chatId) }
                    )
                }
                else -> {
                    UserResultsList(
                        users      = uiState.userResults,
                        onItemClick = { user -> onOpenChat(user.userId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySearchHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text  = stringResource(R.string.search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageResultsList(
    results: List<GlobalSearchResult>,
    query: String,
    onItemClick: (GlobalSearchResult) -> Unit,
) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text  = stringResource(R.string.search_no_results, query),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(results, key = { it.id }) { result ->
            MessageResultItem(result = result, onClick = { onItemClick(result) })
        }
    }
}

@Composable
private fun MessageResultItem(result: GlobalSearchResult, onClick: () -> Unit) {
    val dateStr = remember(result.time) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(result.time * 1000))
    }
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth(),
        headlineContent = {
            Text(
                text     = result.textPreview.ifBlank {
                    if (result.hasMedia) "📎 Media" else if (result.hasSticker) "🎭 Sticker" else "—"
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style    = MaterialTheme.typography.bodyMedium
            )
        },
        supportingContent = {
            Text(
                text  = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape    = CircleShape,
                color    = if (result.chatType == "group")
                    MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text     = if (result.chatType == "group") "G" else "U",
                        fontWeight = FontWeight.Bold,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        trailingContent = {
            Text(
                text  = if (result.chatType == "group") stringResource(R.string.group_label) else stringResource(R.string.chat_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

@Composable
private fun UserResultsList(
    users: List<UserSearchResult>,
    onItemClick: (UserSearchResult) -> Unit,
) {
    if (users.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text  = stringResource(R.string.search_no_users),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(users, key = { it.userId }) { user ->
            UserResultItem(user = user, onClick = { onItemClick(user) })
        }
    }
}

@Composable
private fun UserResultItem(user: UserSearchResult, onClick: () -> Unit) {
    val displayName = "${user.firstName} ${user.lastName}".trim().ifBlank { user.username }
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth(),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = displayName, fontWeight = FontWeight.Medium)
                if (user.isVerified) {
                    Spacer(Modifier.width(4.dp))
                    Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
        },
        supportingContent = {
            if (user.username.isNotBlank()) {
                Text(
                    text  = "@${user.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            if (user.avatar.isNotBlank()) {
                AsyncImage(
                    model              = user.avatar,
                    contentDescription = displayName,
                    modifier           = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale       = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape    = CircleShape,
                    color    = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}
