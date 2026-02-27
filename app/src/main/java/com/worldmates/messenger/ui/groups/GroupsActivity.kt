// ============ GroupsActivity.kt ============

package com.worldmates.messenger.ui.groups

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.Group
import com.worldmates.messenger.ui.messages.MessagesActivity
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp

class GroupsActivity : AppCompatActivity() {

    private lateinit var viewModel: GroupsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем ThemeManager
        ThemeManager.initialize(this)

        viewModel = ViewModelProvider(this).get(GroupsViewModel::class.java)

        setContent {
            WorldMatesThemedApp {
                GroupsScreenWrapper(
                    viewModel = viewModel,
                    onGroupClick = { group ->
                        navigateToGroupMessages(group)
                    },
                    onBackPressed = { finish() }
                )
            }
        }
    }

    private fun navigateToGroupMessages(group: Group) {
        startActivity(Intent(this, MessagesActivity::class.java).apply {
            putExtra("group_id", group.id)
            putExtra("recipient_name", group.name)
            putExtra("recipient_avatar", group.avatarUrl)
            putExtra("is_group", true)
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreenWrapper(
    viewModel: GroupsViewModel,
    onGroupClick: (Group) -> Unit,
    onBackPressed: () -> Unit
) {
    val groups by viewModel.groupList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val availableUsers by viewModel.availableUsers.collectAsState()
    val isCreatingGroup by viewModel.isCreatingGroup.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var groupToEdit by remember { mutableStateOf<Group?>(null) }

    val context = LocalContext.current

    // Load available users when screen opens
    LaunchedEffect(Unit) {
        viewModel.loadAvailableUsers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Групи") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Color(0xFF0084FF)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Створити групу",
                    tint = Color.White
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Create Group Dialog
            if (showCreateDialog) {
                CreateGroupDialog(
                    onDismiss = { showCreateDialog = false },
                    availableUsers = availableUsers,
                    onCreateGroup = { name, description, memberIds, isPrivate, avatarUri ->
                        viewModel.createGroup(
                            name = name,
                            description = description,
                            memberIds = memberIds,
                            isPrivate = isPrivate,
                            avatarUri = avatarUri,
                            context = context,
                            onSuccess = {
                                showCreateDialog = false
                            }
                        )
                    },
                    isLoading = isCreatingGroup
                )
            }

            // Edit Group Dialog
            groupToEdit?.let { group ->
                EditGroupDialog(
                    group = group,
                    onDismiss = { groupToEdit = null },
                    onUpdate = { newName ->
                        viewModel.updateGroup(
                            groupId = group.id,
                            name = newName
                        )
                        groupToEdit = null
                    },
                    onDelete = {
                        viewModel.deleteGroup(group.id)
                        groupToEdit = null
                    },
                    onUploadAvatar = { uri ->
                        viewModel.uploadGroupAvatar(
                            groupId = group.id,
                            imageUri = uri,
                            context = context
                        )
                    },
                    isLoading = isLoading
                )
            }

            if (isLoading && groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Surface(color = Color(0xFFFFCDD2), modifier = Modifier.fillMaxWidth()) {
                    Text(error!!, color = Color.Red, modifier = Modifier.padding(16.dp))
                }
            } else if (groups.isEmpty()) {
                EmptyGroupsPlaceholder(onCreateClick = { showCreateDialog = true })
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(groups) { group ->
                        ModernGroupCard(
                            group = group,
                            onClick = { onGroupClick(group) },
                            onLongClick = { groupToEdit = group },
                            isPinned = false,
                            unreadCount = group.unreadCount
                        )
                    }
                }
            }
        }
    }
}

// Старые компоненты удалены, используем ModernGroupCard и EmptyGroupsPlaceholder из ModernGroupComponents.kt