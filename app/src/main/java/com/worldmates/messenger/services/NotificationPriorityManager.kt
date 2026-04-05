package com.worldmates.messenger.services

import android.content.Context

/**
 * Manages per-contact and per-group notification priority overrides.
 *
 * Three levels:
 *   SILENT   — no notification at all (muted contact/group)
 *   NORMAL   — default channel (HIGH importance, sound + vibration)
 *   PRIORITY — VIP channel (MAX importance, custom ringtone, heads-up always)
 *
 * Stored in SharedPreferences so reads are synchronous and safe to call from
 * any thread (notification service background coroutines, UI, etc.).
 */
object NotificationPriorityManager {

    enum class Level { SILENT, NORMAL, PRIORITY }

    private const val PREFS_NAME = "wm_notif_priority"

    // SharedPreferences key prefixes
    private const val PREFIX_CONTACT = "c:"
    private const val PREFIX_GROUP   = "g:"

    // ── Contacts ──────────────────────────────────────────────────────────────

    fun getContactLevel(ctx: Context, contactId: Long): Level =
        readLevel(ctx, "$PREFIX_CONTACT$contactId")

    fun setContactLevel(ctx: Context, contactId: Long, level: Level) {
        writeLevel(ctx, "$PREFIX_CONTACT$contactId", level)
    }

    // ── Groups and channels (pass groupId or channelId) ───────────────────────

    fun getGroupLevel(ctx: Context, chatId: Long): Level =
        readLevel(ctx, "$PREFIX_GROUP$chatId")

    fun setGroupLevel(ctx: Context, chatId: Long, level: Level) {
        writeLevel(ctx, "$PREFIX_GROUP$chatId", level)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun readLevel(ctx: Context, key: String): Level {
        val ordinal = prefs(ctx).getInt(key, Level.NORMAL.ordinal)
        return Level.entries.getOrNull(ordinal) ?: Level.NORMAL
    }

    private fun writeLevel(ctx: Context, key: String, level: Level) {
        prefs(ctx).edit().putInt(key, level.ordinal).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
