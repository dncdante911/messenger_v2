// ============================================================
// WorldMates Messenger — Profile API
// Wraps all user-profile, follow/block, and avatar endpoints.
// ============================================================

import { nodeGet, nodePost, nodePut, nodeDelete } from './apiClient';
import {
  NODE_PROFILE_ME,
  NODE_PROFILE_USER,
  NODE_PROFILE_FOLLOW,
  NODE_PROFILE_BLOCK,
  NODE_PROFILE_FOLLOWERS,
  NODE_PROFILE_FOLLOWING,
  NODE_AVATAR_LIST,
  NODE_AVATAR_UPLOAD,
  NODE_AVATAR_DELETE,
} from '../constants/api';
import type { User } from './types';

// ─────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────

function userUrl(template: string, userId: string): string {
  return template.replace('{id}', userId).replace('{userId}', userId);
}

function unwrapUser(data: unknown): User {
  if (data && typeof data === 'object' && 'user' in data) {
    return (data as { user: User }).user;
  }
  return data as User;
}

// ─────────────────────────────────────────────────────────────
// PROFILE API
// ─────────────────────────────────────────────────────────────

export const profileApi = {
  /**
   * GET api/node/users/me
   * Returns the authenticated user's own profile.
   */
  async getMyProfile(): Promise<User> {
    const res = await nodeGet<User>(NODE_PROFILE_ME);
    return unwrapUser(res.data);
  },

  /**
   * PUT api/node/users/me
   * Updates the authenticated user's profile fields.
   */
  async updateMyProfile(data: Partial<User>): Promise<User> {
    const res = await nodePut<User>(NODE_PROFILE_ME, data);
    return unwrapUser(res.data);
  },

  /**
   * GET api/node/users/{id}
   * Returns another user's public profile.
   */
  async getUserProfile(userId: string): Promise<User> {
    const url = userUrl(NODE_PROFILE_USER, userId);
    const res = await nodeGet<User>(url);
    return unwrapUser(res.data);
  },

  /**
   * POST api/node/users/{id}/follow
   * Follow a user.
   */
  async followUser(userId: string): Promise<void> {
    const url = userUrl(NODE_PROFILE_FOLLOW, userId);
    await nodePost(url);
  },

  /**
   * DELETE api/node/users/{id}/follow
   * Unfollow a user.
   */
  async unfollowUser(userId: string): Promise<void> {
    const url = userUrl(NODE_PROFILE_FOLLOW, userId);
    await nodeDelete(url);
  },

  /**
   * POST api/node/users/{id}/block
   * Block a user.
   */
  async blockUser(userId: string): Promise<void> {
    const url = userUrl(NODE_PROFILE_BLOCK, userId);
    await nodePost(url);
  },

  /**
   * DELETE api/node/users/{id}/block
   * Unblock a user.
   */
  async unblockUser(userId: string): Promise<void> {
    const url = userUrl(NODE_PROFILE_BLOCK, userId);
    await nodeDelete(url);
  },

  /**
   * GET api/node/users/{id}/followers
   * Returns list of users who follow the given user.
   */
  async getFollowers(userId: string): Promise<User[]> {
    const url = userUrl(NODE_PROFILE_FOLLOWERS, userId);
    const res = await nodeGet<User[]>(url);
    return (res.data as unknown[]).map(unwrapUser);
  },

  /**
   * GET api/node/users/{id}/following
   * Returns list of users the given user follows.
   */
  async getFollowing(userId: string): Promise<User[]> {
    const url = userUrl(NODE_PROFILE_FOLLOWING, userId);
    const res = await nodeGet<User[]>(url);
    return (res.data as unknown[]).map(unwrapUser);
  },

  /**
   * POST api/node/user/avatars/upload
   * Upload a new avatar image. Sends as multipart/form-data.
   */
  async uploadAvatar(formData: FormData): Promise<{ avatarUrl: string }> {
    const res = await nodePost<{ avatarUrl: string }>(NODE_AVATAR_UPLOAD, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data as { avatarUrl: string };
  },

  /**
   * GET api/node/user/avatars/{userId}
   * Returns all avatars for a given user.
   */
  async getMyAvatars(
    userId: string,
  ): Promise<Array<{ id: string; url: string; isMain: boolean }>> {
    const url = NODE_AVATAR_LIST.replace('{userId}', userId);
    const res = await nodeGet<Array<{ id: string; url: string; isMain: boolean }>>(url);
    return (res.data ?? []) as Array<{ id: string; url: string; isMain: boolean }>;
  },

  /**
   * DELETE api/node/user/avatars/{id}
   * Delete an avatar by its ID.
   */
  async deleteAvatar(avatarId: string): Promise<void> {
    const url = NODE_AVATAR_DELETE.replace('{id}', avatarId);
    await nodeDelete(url);
  },
};
