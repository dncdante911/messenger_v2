package com.worldmates.messenger.ui.groups

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmates.messenger.ui.groups.components.SubgroupsSection

/**
 * Connects SubgroupsSection to GroupTopicsViewModel for live data from Node.js API.
 * Usage: replace SubgroupsSection(...) with GroupTopicsSection(groupId, canCreate, onClick)
 */
@Composable
fun GroupTopicsSection(
    groupId: Long,
    canCreateTopic: Boolean,
    onTopicClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    topicsViewModel: GroupTopicsViewModel = viewModel()
) {
    val topics by topicsViewModel.topics.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) {
        topicsViewModel.loadTopics(groupId)
    }

    var showCreateDialog by remember { mutableStateOf(false) }

    SubgroupsSection(
        subgroups = topics,
        canCreateSubgroup = canCreateTopic,
        onSubgroupClick = { subgroup -> onTopicClick(subgroup.id) },
        onCreateSubgroupClick = { showCreateDialog = true },
        modifier = modifier
    )

    if (showCreateDialog) {
        com.worldmates.messenger.ui.groups.components.CreateSubgroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, isPrivate, color ->
                topicsViewModel.createTopic(name, desc, color, isPrivate)
                showCreateDialog = false
            }
        )
    }
}
