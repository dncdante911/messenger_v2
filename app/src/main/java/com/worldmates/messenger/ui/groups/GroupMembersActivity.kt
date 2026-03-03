package com.worldmates.messenger.ui.groups

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.GroupMember
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.ui.groups.components.MemberRoleBadge
import com.worldmates.messenger.utils.LanguageManager

/**
 * Full-screen members management screen.
 * Shows all members with kick / ban / mute-per-user actions.
 */
class GroupMembersActivity : AppCompatActivity() {

    private lateinit var viewModel: GroupsViewModel
    private var groupId: Long = 0
    private var isAdmin: Boolean = false

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = intent.getLongExtra("group_id", 0)
        isAdmin = intent.getBooleanExtra("is_admin", false)
        if (groupId == 0L) { finish(); return }

        viewModel = ViewModelProvider(this)[GroupsViewModel::class.java]
        viewModel.fetchGroupMembers(groupId)

        setContent {
            WorldMatesThemedApp {
                val members by viewModel.groupMembers.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()
                val context = LocalContext.current

                var searchQuery by remember { mutableStateOf("") }
                var selectedMember by remember { mutableStateOf<GroupMember?>(null) }
                var showActionDialog by remember { mutableStateOf(false) }

                val currentUserId = UserSession.userId ?: 0L

                val filtered = if (searchQuery.isBlank()) members
                else members.filter {
                    it.username.contains(searchQuery, ignoreCase = true)
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.members_count_title, members.size)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                                }
                            },
                            actions = {
                                IconButton(onClick = { viewModel.fetchGroupMembers(groupId) }) {
                                    Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF0084FF),
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor = Color.White
                            )
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(Color(0xFFF5F5F5))
                    ) {
                        // Search bar
                        Surface(color = Color.White, tonalElevation = 2.dp) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.search_members_hint)) },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, null)
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        }

                        if (isLoading && members.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(
                                    filtered.sortedWith(
                                        compareBy {
                                            when (it.role) {
                                                "owner" -> 0
                                                "admin" -> 1
                                                "moderator" -> 2
                                                else -> 3
                                            }
                                        }
                                    )
                                ) { member ->
                                    MemberManagementCard(
                                        member = member,
                                        isCurrentUser = member.userId == currentUserId,
                                        isAdmin = isAdmin,
                                        onClick = {
                                            if (isAdmin && member.userId != currentUserId) {
                                                selectedMember = member
                                                showActionDialog = true
                                            }
                                        }
                                    )
                                }

                                item { Spacer(modifier = Modifier.height(32.dp)) }
                            }
                        }
                    }

                    // Member action dialog
                    if (showActionDialog && selectedMember != null) {
                        MemberActionSheet(
                            member = selectedMember!!,
                            groupId = groupId,
                            viewModel = viewModel,
                            onDismiss = { showActionDialog = false },
                            onOpenProfile = {
                                // Navigate to user profile
                                val intent = Intent(context, com.worldmates.messenger.ui.profile.UserProfileActivity::class.java).apply {
                                    putExtra("user_id", selectedMember!!.userId)
                                }
                                context.startActivity(intent)
                                showActionDialog = false
                            },
                            onActionDone = {
                                showActionDialog = false
                                viewModel.fetchGroupMembers(groupId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberManagementCard(
    member: GroupMember,
    isCurrentUser: Boolean,
    isAdmin: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isAdmin && !isCurrentUser, onClick = onClick),
        color = Color.White,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            AsyncImage(
                model = member.avatarUrl.ifBlank { null },
                contentDescription = member.username,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.username,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.you_suffix), fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberRoleBadge(role = member.role)
                    if (member.isMuted) {
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                stringResource(R.string.member_muted_label),
                                fontSize = 10.sp,
                                color = Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (member.isBlocked) {
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                stringResource(R.string.member_blocked_label),
                                fontSize = 10.sp,
                                color = Color.Red,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Chevron for admins
            if (isAdmin && !isCurrentUser) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    Divider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
}

@Composable
private fun MemberActionSheet(
    member: GroupMember,
    groupId: Long,
    viewModel: GroupsViewModel,
    onDismiss: () -> Unit,
    onOpenProfile: () -> Unit,
    onActionDone: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(member.username, fontWeight = FontWeight.Bold)
                MemberRoleBadge(role = member.role)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // View profile
                TextButton(
                    onClick = onOpenProfile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.view_profile), color = Color(0xFF0084FF))
                }

                Divider()

                // Promote / demote
                if (member.role == "member" || member.role == "moderator") {
                    TextButton(
                        onClick = {
                            viewModel.setGroupRole(groupId, member.userId, "admin",
                                onSuccess = { onActionDone() },
                                onError = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.assign_admin), color = Color(0xFF0084FF))
                    }
                }
                if (member.role == "admin") {
                    TextButton(
                        onClick = {
                            viewModel.setGroupRole(groupId, member.userId, "member",
                                onSuccess = { onActionDone() },
                                onError = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.demote_admin), color = Color(0xFF0084FF))
                    }
                }

                Divider()

                // Mute / unmute
                if (!member.isMuted) {
                    TextButton(
                        onClick = {
                            viewModel.muteGroupMember(groupId, member.userId,
                                onSuccess = { onActionDone() },
                                onError = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VolumeOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.mute_in_group), color = Color(0xFFFF9800))
                    }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.unmuteGroupMember(groupId, member.userId,
                                onSuccess = { onActionDone() },
                                onError = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.unmute_in_group), color = Color(0xFF4CAF50))
                    }
                }

                // Kick
                TextButton(
                    onClick = {
                        viewModel.removeGroupMember(groupId, member.userId)
                        onActionDone()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.remove_from_group), color = Color.Red)
                }

                // Ban / unban
                if (!member.isBlocked) {
                    TextButton(
                        onClick = {
                            viewModel.banGroupMember(groupId, member.userId,
                                onSuccess = { onActionDone() },
                                onError = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ban_member_with_info), color = Color.Red)
                    }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.unbanGroupMember(groupId, member.userId,
                                onSuccess = { onActionDone() },
                                onError = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.unban_member), color = Color(0xFF4CAF50))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
