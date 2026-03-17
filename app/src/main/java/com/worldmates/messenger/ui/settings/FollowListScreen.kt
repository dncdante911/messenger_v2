package com.worldmates.messenger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.User
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── ViewModel ───────────────────────────────────────────────────────────────

class FollowListViewModel : ViewModel() {

    private val api = NodeRetrofitClient.profileApi

    private val _followers = MutableStateFlow<List<User>>(emptyList())
    val followers: StateFlow<List<User>> = _followers.asStateFlow()

    private val _following = MutableStateFlow<List<User>>(emptyList())
    val following: StateFlow<List<User>> = _following.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadFollowers(userId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val resp = api.getFollowers(userId)
                if (resp.apiStatus == 200) {
                    _followers.value = resp.followers.orEmpty()
                } else {
                    _error.value = resp.errorMessage ?: "Error ${resp.apiStatus}"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadFollowing(userId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val resp = api.getFollowing(userId)
                if (resp.apiStatus == 200) {
                    _following.value = resp.following.orEmpty()
                } else {
                    _error.value = resp.errorMessage ?: "Error ${resp.apiStatus}"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    showFollowers: Boolean,          // true = Followers, false = Following
    viewModel: FollowListViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val myUserId = UserSession.userId
    val followers by viewModel.followers.collectAsState()
    val following by viewModel.following.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val title = if (showFollowers) stringResource(R.string.followers_settings)
                else stringResource(R.string.following_settings)
    val users = if (showFollowers) followers else following

    LaunchedEffect(showFollowers) {
        if (showFollowers) viewModel.loadFollowers(myUserId)
        else viewModel.loadFollowing(myUserId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
                users.isEmpty() -> Text(
                    text = if (showFollowers) stringResource(R.string.followers_count_fmt, "0")
                           else stringResource(R.string.following_count_fmt, "0"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(users, key = { it.userId }) { user ->
                        FollowUserRow(user)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowUserRow(user: User) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: open profile */ }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = user.avatar?.takeIf { it.isNotBlank() }
                ?: "https://worldmates.club/upload/photos/d-avatar.jpg",
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!user.about.isNullOrBlank()) {
                Text(
                    text = user.about,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
