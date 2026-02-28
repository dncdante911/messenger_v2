package com.worldmates.messenger.ui.groups

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.network.SearchUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    availableUsers: List<SearchUser>,
    onCreateGroup: (name: String, description: String, selectedUserIds: List<Long>, isPrivate: Boolean, avatarUri: Uri?) -> Unit,
    isLoading: Boolean = false
) {
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedUsers by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var currentStep by remember { mutableStateOf(CreateGroupStep.GROUP_INFO) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    val filteredUsers = remember(availableUsers, searchQuery) {
        if (searchQuery.isBlank()) {
            availableUsers
        } else {
            availableUsers.filter {
                (it.name?.contains(searchQuery, ignoreCase = true) == true) ||
                        it.username.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Bar
                TopAppBar(
                    title = {
                        Text(
                            when (currentStep) {
                                CreateGroupStep.GROUP_INFO -> stringResource(R.string.new_group)
                                CreateGroupStep.SELECT_MEMBERS -> stringResource(R.string.add_members)
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentStep == CreateGroupStep.SELECT_MEMBERS) {
                                currentStep = CreateGroupStep.GROUP_INFO
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                when (currentStep) {
                                    CreateGroupStep.GROUP_INFO -> {
                                        if (groupName.isNotBlank()) {
                                            currentStep = CreateGroupStep.SELECT_MEMBERS
                                        }
                                    }
                                    CreateGroupStep.SELECT_MEMBERS -> {
                                        if (groupName.isNotBlank()) {
                                            onCreateGroup(
                                                groupName.trim(),
                                                groupDescription.trim(),
                                                selectedUsers.toList(),
                                                isPrivate,
                                                selectedAvatarUri
                                            )
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading && groupName.isNotBlank()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    when (currentStep) {
                                        CreateGroupStep.GROUP_INFO -> stringResource(R.string.next)
                                        CreateGroupStep.SELECT_MEMBERS -> stringResource(R.string.create)
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Divider()

                // Content
                when (currentStep) {
                    CreateGroupStep.GROUP_INFO -> {
                        GroupInfoStep(
                            groupName = groupName,
                            onGroupNameChange = { groupName = it },
                            groupDescription = groupDescription,
                            onGroupDescriptionChange = { groupDescription = it },
                            isPrivate = isPrivate,
                            onPrivateChange = { isPrivate = it },
                            selectedAvatarUri = selectedAvatarUri,
                            onAvatarSelected = { selectedAvatarUri = it }
                        )
                    }
                    CreateGroupStep.SELECT_MEMBERS -> {
                        SelectMembersStep(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            filteredUsers = filteredUsers,
                            selectedUsers = selectedUsers,
                            onUserToggle = { userId ->
                                selectedUsers = if (selectedUsers.contains(userId)) {
                                    selectedUsers - userId
                                } else {
                                    selectedUsers + userId
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupInfoStep(
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    groupDescription: String,
    onGroupDescriptionChange: (String) -> Unit,
    isPrivate: Boolean,
    onPrivateChange: (Boolean) -> Unit,
    selectedAvatarUri: Uri?,
    onAvatarSelected: (Uri?) -> Unit
) {
    // Launcher для вибору зображення
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onAvatarSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Group Avatar Placeholder
        Box(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.CenterHorizontally)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable {
                    imagePickerLauncher.launch("image/*")
                },
            contentAlignment = Alignment.Center
        ) {
            if (selectedAvatarUri != null) {
                AsyncImage(
                    model = selectedAvatarUri,
                    contentDescription = stringResource(R.string.group_avatar),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(R.string.add_photo),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Text(
            text = if (selectedAvatarUri != null) stringResource(R.string.tap_to_change_photo) else stringResource(R.string.tap_to_add_group_photo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Group Name
        OutlinedTextField(
            value = groupName,
            onValueChange = { if (it.length <= 255) onGroupNameChange(it) },
            label = { Text(stringResource(R.string.group_name_hint)) },
            placeholder = { Text(stringResource(R.string.enter_group_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text("${groupName.length}/255")
            }
        )

        // Group Description
        OutlinedTextField(
            value = groupDescription,
            onValueChange = { if (it.length <= 500) onGroupDescriptionChange(it) },
            label = { Text(stringResource(R.string.group_description_hint)) },
            placeholder = { Text(stringResource(R.string.describe_group)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 4,
            supportingText = {
                Text("${groupDescription.length}/500")
            }
        )

        // Private Group Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPrivateChange(!isPrivate) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPrivate) stringResource(R.string.private_group_label) else stringResource(R.string.public_group_label),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (isPrivate)
                        stringResource(R.string.only_members_can_see_group)
                    else
                        stringResource(R.string.everyone_can_see_group),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isPrivate,
                onCheckedChange = onPrivateChange
            )
        }
    }
}

@Composable
private fun SelectMembersStep(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filteredUsers: List<SearchUser>,
    selectedUsers: Set<Long>,
    onUserToggle: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text(stringResource(R.string.search_users_hint)) },
            leadingIcon = {
                Icon(Icons.Default.Search, stringResource(R.string.search))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true
        )

        // Selected count
        if (selectedUsers.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.selected_count, selectedUsers.size),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // User list
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredUsers) { user ->
                UserSelectItem(
                    user = user,
                    isSelected = selectedUsers.contains(user.userId),
                    onToggle = { onUserToggle(user.userId) }
                )
            }
        }
    }
}

@Composable
private fun UserSelectItem(
    user: SearchUser,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = user.name ?: user.username,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // User info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name ?: user.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Checkbox
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
    }
}

enum class CreateGroupStep {
    GROUP_INFO,
    SELECT_MEMBERS
}
