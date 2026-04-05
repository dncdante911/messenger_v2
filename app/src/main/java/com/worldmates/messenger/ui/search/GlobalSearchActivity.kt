package com.worldmates.messenger.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.network.GlobalSearchResult
import com.worldmates.messenger.network.SavedSearch
import com.worldmates.messenger.network.SearchSuggestion
import com.worldmates.messenger.network.UserSearchResult
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.ui.messages.MessagesActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen global search.
 * Features:
 *  – Real-time search with 350ms debounce
 *  – Suggestions panel (recent + saved + user hints) when query is empty/short
 *  – Filter chips row: Type (all/text/media/sticker), Date range (from/to)
 *  – Save current query to bookmarks (★ button on results)
 *  – Recent history auto-saved on submit, clearable
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
                        startActivity(
                            Intent(this, MessagesActivity::class.java).apply {
                                putExtra("recipient_id", userId)
                            }
                        )
                    }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, GlobalSearchActivity::class.java)
    }
}

// ─── Root screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalSearchScreen(
    viewModel: GlobalSearchViewModel,
    onBack:       () -> Unit,
    onOpenChat:   (Long) -> Unit,
) {
    val uiState     by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboard       = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            Column {
                // ── Search field ──────────────────────────────────────────────
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
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboard?.hide()
                                viewModel.submitSearch(uiState.query)
                            }),
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
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        // Bookmark current query
                        if (uiState.query.length >= 2) {
                            IconButton(onClick = { viewModel.saveCurrentQuery() }) {
                                Icon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = stringResource(R.string.search_save),
                                    tint = if (uiState.savedSearches.any { it.query == uiState.query })
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                )

                // ── Filter chips row ──────────────────────────────────────────
                SearchFilterChips(
                    filters        = uiState.filters,
                    onOpenSheet    = { showFilterSheet = true },
                    onClearFilters = { viewModel.clearFilters() },
                )

                // ── Tab row (only visible when showing results) ───────────────
                if (uiState.query.length >= 2) {
                    TabRow(selectedTabIndex = selectedTab) {
                        listOf(
                            stringResource(R.string.search_tab_messages),
                            stringResource(R.string.search_tab_users),
                        ).forEachIndexed { i, title ->
                            Tab(
                                selected = selectedTab == i,
                                onClick  = { selectedTab = i },
                                text     = { Text(title) },
                            )
                        }
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
                    // ── Suggestions / empty state ─────────────────────────────
                    SuggestionsPanel(
                        suggestions    = uiState.suggestions,
                        savedSearches  = uiState.savedSearches,
                        isLoading      = uiState.suggestionsLoading,
                        onQueryTap     = { q ->
                            viewModel.onQueryChanged(q)
                            viewModel.submitSearch(q)
                        },
                        onDeleteSaved  = { id -> viewModel.deleteSavedSearch(id)       },
                        onDeleteRecent = { id -> viewModel.deleteRecentSuggestion(id)  },
                        onClearRecent  = { viewModel.clearRecentHistory()              },
                        onUserTap      = { user -> onOpenChat(user.userId)             },
                    )
                }
                selectedTab == 0 -> {
                    MessageResultsList(
                        results     = uiState.messageResults,
                        total       = uiState.totalMessages,
                        query       = uiState.query,
                        filters     = uiState.filters,
                        onItemClick = { result -> onOpenChat(result.chatId) },
                    )
                }
                else -> {
                    UserResultsList(
                        users       = uiState.userResults,
                        onItemClick = { user -> onOpenChat(user.userId) },
                    )
                }
            }
        }
    }

    // ── Filter bottom sheet ───────────────────────────────────────────────────
    if (showFilterSheet) {
        SearchFilterSheet(
            current   = uiState.filters,
            onApply   = { filters ->
                viewModel.updateFilters(filters)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        )
    }
}

// ─── Filter chips row ─────────────────────────────────────────────────────────

@Composable
private fun SearchFilterChips(
    filters:        SearchFilters,
    onOpenSheet:    () -> Unit,
    onClearFilters: () -> Unit,
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Type chip
        FilterChip(
            selected = filters.msgType != MsgTypeFilter.ALL,
            onClick  = onOpenSheet,
            label    = {
                Text(
                    if (filters.msgType == MsgTypeFilter.ALL)
                        stringResource(R.string.search_filter_type_all)
                    else
                        stringResource(
                            when (filters.msgType) {
                                MsgTypeFilter.TEXT    -> R.string.search_filter_type_text
                                MsgTypeFilter.MEDIA   -> R.string.search_filter_type_media
                                MsgTypeFilter.STICKER -> R.string.search_filter_type_sticker
                                else                  -> R.string.search_filter_type_all
                            }
                        )
                )
            },
            leadingIcon = {
                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
            },
        )

        // Date chip (shown when either date set)
        val hasDate = filters.dateFrom != null || filters.dateTo != null
        FilterChip(
            selected = hasDate,
            onClick  = onOpenSheet,
            label    = {
                Text(
                    if (!hasDate) {
                        stringResource(R.string.search_filter_date)
                    } else {
                        val from = filters.dateFrom?.let { dateFmt.format(Date(it * 1000)) }
                        val to   = filters.dateTo?.let   { dateFmt.format(Date(it * 1000)) }
                        when {
                            from != null && to != null -> "$from – $to"
                            from != null               -> "≥ $from"
                            else                       -> "≤ $to"
                        }
                    }
                )
            },
            leadingIcon = {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
            },
        )

        // Clear all filters
        if (filters.isActive) {
            IconButton(
                onClick  = onClearFilters,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.search_filter_clear),
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ─── Suggestions panel ────────────────────────────────────────────────────────

@Composable
private fun SuggestionsPanel(
    suggestions:    List<SearchSuggestion>,
    savedSearches:  List<SavedSearch>,
    isLoading:      Boolean,
    onQueryTap:     (String) -> Unit,
    onDeleteSaved:  (Long) -> Unit,
    onDeleteRecent: (Long) -> Unit,
    onClearRecent:  () -> Unit,
    onUserTap:      (UserSearchResult) -> Unit,
) {
    if (isLoading && suggestions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val saved  = suggestions.filter { it.type == "saved"  }
    val recent = suggestions.filter { it.type == "recent" }
    val users  = suggestions.filter { it.type == "user"   }

    if (suggestions.isEmpty()) {
        // Completely empty state
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint     = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text  = stringResource(R.string.search_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {

        // ── Saved searches ────────────────────────────────────────────────────
        if (saved.isNotEmpty()) {
            item {
                SuggestionSectionHeader(
                    title     = stringResource(R.string.search_saved_title),
                    icon      = Icons.Default.Bookmark,
                    actionLabel = null,
                    onAction  = null,
                )
            }
            items(saved, key = { "saved_${it.id}" }) { s ->
                SuggestionQueryItem(
                    query     = s.query ?: "",
                    icon      = Icons.Default.Bookmark,
                    iconTint  = MaterialTheme.colorScheme.primary,
                    onTap     = { onQueryTap(s.query ?: "") },
                    onDelete  = { s.id?.let { onDeleteSaved(it) } },
                )
            }
        }

        // ── Recent searches ───────────────────────────────────────────────────
        if (recent.isNotEmpty()) {
            item {
                SuggestionSectionHeader(
                    title       = stringResource(R.string.search_recent_title),
                    icon        = Icons.Default.History,
                    actionLabel = stringResource(R.string.search_recent_clear),
                    onAction    = onClearRecent,
                )
            }
            items(recent, key = { "recent_${it.id}" }) { r ->
                SuggestionQueryItem(
                    query    = r.query ?: "",
                    icon     = Icons.Default.History,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onTap    = { onQueryTap(r.query ?: "") },
                    onDelete = { r.id?.let { onDeleteRecent(it) } },
                )
            }
        }

        // ── User hints ────────────────────────────────────────────────────────
        if (users.isNotEmpty()) {
            item {
                SuggestionSectionHeader(
                    title       = stringResource(R.string.search_tab_users),
                    icon        = Icons.Default.Person,
                    actionLabel = null,
                    onAction    = null,
                )
            }
            items(users, key = { "user_${it.user?.userId}" }) { s ->
                s.user?.let { user ->
                    UserResultItem(user = user, onClick = { onUserTap(user) })
                }
            }
        }
    }
}

@Composable
private fun SuggestionSectionHeader(
    title:       String,
    icon:        androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String?,
    onAction:    (() -> Unit)?,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                text  = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SuggestionQueryItem(
    query:    String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onTap:    () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onTap),
        headlineContent  = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent   = {
            Icon(icon, contentDescription = null,
                modifier = Modifier.size(20.dp), tint = iconTint)
        },
        trailingContent  = {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

// ─── Message results ──────────────────────────────────────────────────────────

@Composable
private fun MessageResultsList(
    results:     List<GlobalSearchResult>,
    total:       Int,
    query:       String,
    filters:     SearchFilters,
    onItemClick: (GlobalSearchResult) -> Unit,
) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text  = stringResource(R.string.search_no_results, query),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        // Active filter summary
        if (filters.isActive) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text     = stringResource(R.string.search_filtered_count, results.size, total),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
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
                style    = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = {
            Text(
                text  = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape    = CircleShape,
                color    = if (result.chatType == "group")
                    MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text       = if (result.chatType == "group") "G" else "U",
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        trailingContent = {
            Text(
                text  = if (result.chatType == "group")
                    stringResource(R.string.group_label)
                else
                    stringResource(R.string.chat_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

// ─── User results ─────────────────────────────────────────────────────────────

@Composable
private fun UserResultsList(
    users:       List<UserSearchResult>,
    onItemClick: (UserSearchResult) -> Unit,
) {
    if (users.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text  = stringResource(R.string.search_no_users),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun UserResultItem(user: UserSearchResult, onClick: () -> Unit) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            if (user.avatar.isNotBlank()) {
                AsyncImage(
                    model              = user.avatar,
                    contentDescription = displayName,
                    modifier           = Modifier.size(44.dp).clip(CircleShape),
                    contentScale       = ContentScale.Crop,
                )
            } else {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape    = CircleShape,
                    color    = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}
