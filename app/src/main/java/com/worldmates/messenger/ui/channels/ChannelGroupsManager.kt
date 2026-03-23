package com.worldmates.messenger.ui.channels

import com.worldmates.messenger.data.model.ChannelGroupCreateResponse
import com.worldmates.messenger.data.model.ChannelSubGroupItem
import com.worldmates.messenger.network.NodeChannelApi

/** Alias kept for UI layer convenience */
typealias ChannelSubGroup = ChannelSubGroupItem

// ─── Manager (thin wrapper around NodeChannelApi) ─────────────────────────────

/**
 * Handles all sub-group operations for a private premium channel.
 * Intended to be used from ChannelDetailsViewModel.
 *
 * Max 5 sub-groups per channel — enforced server-side.
 */
class ChannelGroupsManager(private val api: NodeChannelApi) {

    suspend fun loadGroups(channelId: Long): Result<List<ChannelSubGroup>> = runCatching {
        val response = api.getChannelGroups(channelId)
        if (response.apiStatus != 200)
            throw Exception(response.errorMessage ?: "Failed to load sub-groups")
        response.groups ?: emptyList()
    }

    suspend fun createGroup(
        channelId: Long,
        groupName: String,
        description: String? = null
    ): Result<ChannelGroupCreateResponse> = runCatching {
        val response = api.createChannelGroup(channelId, groupName, description)
        if (response.apiStatus != 200)
            throw Exception(response.errorMessage ?: "Failed to create sub-group")
        response
    }

    suspend fun attachGroup(channelId: Long, groupId: Long): Result<Unit> = runCatching {
        val response = api.attachChannelGroup(channelId, groupId)
        if (response.apiStatus != 200)
            throw Exception(response.errorMessage ?: "Failed to attach sub-group")
    }

    suspend fun detachGroup(channelId: Long, groupId: Long): Result<Unit> = runCatching {
        val response = api.detachChannelGroup(channelId, groupId)
        if (response.apiStatus != 200)
            throw Exception(response.errorMessage ?: "Failed to detach sub-group")
    }
}
