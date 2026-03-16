'use strict';

/**
 * Group Theme Customization API
 *
 * Endpoints:
 *   POST /api/node/group/customization/get    – get group theme
 *   POST /api/node/group/customization/update – update group theme (admin only)
 *   POST /api/node/group/customization/reset  – reset group theme to defaults (admin only)
 *
 * Fields stored in Wo_GroupChat:
 *   theme_bubble_style, theme_preset_background, theme_accent_color,
 *   theme_enabled_by_admin, theme_updated_at, theme_updated_by
 */

const { QueryTypes } = require('sequelize');

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function isGroupAdmin(ctx, groupId, userId) {
    // Owner check
    const [owner] = await ctx.sequelize.query(
        'SELECT user_id FROM Wo_GroupChat WHERE group_id = ? AND user_id = ?',
        { replacements: [groupId, userId], type: QueryTypes.SELECT }
    );
    if (owner) return true;

    // Admin role check
    const [admin] = await ctx.sequelize.query(
        "SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ? AND role IN ('owner', 'admin')",
        { replacements: [groupId, userId], type: QueryTypes.SELECT }
    );
    return !!admin;
}

async function getGroupTheme(ctx, groupId) {
    const [row] = await ctx.sequelize.query(
        `SELECT group_id,
                COALESCE(theme_bubble_style, 'STANDARD')   AS bubble_style,
                COALESCE(theme_preset_background, 'ocean') AS preset_background,
                COALESCE(theme_accent_color, '#2196F3')    AS accent_color,
                COALESCE(theme_enabled_by_admin, 1)        AS enabled_by_admin,
                COALESCE(theme_updated_at, 0)              AS updated_at,
                COALESCE(theme_updated_by, 0)              AS updated_by
         FROM Wo_GroupChat WHERE group_id = ?`,
        { replacements: [groupId], type: QueryTypes.SELECT }
    );
    return row || null;
}

// ─── POST /api/node/group/customization/get ───────────────────────────────────

function getCustomization(ctx) {
    return async (req, res) => {
        const groupId = parseInt(req.body.group_id);
        if (!groupId || groupId < 1) {
            return res.json({ api_status: 400, error_message: 'group_id is required' });
        }

        try {
            const row = await getGroupTheme(ctx, groupId);
            if (!row) {
                return res.json({ api_status: 404, error_message: 'Group not found' });
            }

            return res.json({
                api_status: 200,
                customization: {
                    group_id:          row.group_id,
                    bubble_style:      row.bubble_style,
                    preset_background: row.preset_background,
                    accent_color:      row.accent_color,
                    enabled_by_admin:  Boolean(row.enabled_by_admin),
                    updated_at:        Number(row.updated_at),
                    updated_by:        Number(row.updated_by),
                },
            });
        } catch (err) {
            console.error('[Group/customization/get]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── POST /api/node/group/customization/update ────────────────────────────────

function updateCustomization(ctx) {
    return async (req, res) => {
        const userId  = req.userId;
        const groupId = parseInt(req.body.group_id);
        if (!groupId || groupId < 1) {
            return res.json({ api_status: 400, error_message: 'group_id is required' });
        }

        try {
            const isAdmin = await isGroupAdmin(ctx, groupId, userId);
            if (!isAdmin) {
                return res.json({ api_status: 403, error_message: 'Only admins can update group theme' });
            }

            const updates = [];
            const now = Math.floor(Date.now() / 1000);

            if (req.body.bubble_style      !== undefined) updates.push(`theme_bubble_style = '${req.body.bubble_style.replace(/'/g, "''")}'`);
            if (req.body.preset_background !== undefined) updates.push(`theme_preset_background = '${req.body.preset_background.replace(/'/g, "''")}'`);
            if (req.body.accent_color      !== undefined) updates.push(`theme_accent_color = '${req.body.accent_color.replace(/'/g, "''")}'`);
            if (req.body.enabled_by_admin  !== undefined) updates.push(`theme_enabled_by_admin = ${req.body.enabled_by_admin === '1' || req.body.enabled_by_admin === true ? 1 : 0}`);

            if (updates.length === 0) {
                return res.json({ api_status: 400, error_message: 'No fields to update' });
            }

            updates.push(`theme_updated_at = ${now}`, `theme_updated_by = ${userId}`);

            await ctx.sequelize.query(
                `UPDATE Wo_GroupChat SET ${updates.join(', ')} WHERE group_id = ?`,
                { replacements: [groupId], type: QueryTypes.UPDATE }
            );

            const row = await getGroupTheme(ctx, groupId);
            return res.json({
                api_status: 200,
                message: 'Group theme updated successfully',
                customization: {
                    group_id:          row.group_id,
                    bubble_style:      row.bubble_style,
                    preset_background: row.preset_background,
                    accent_color:      row.accent_color,
                    enabled_by_admin:  Boolean(row.enabled_by_admin),
                    updated_at:        Number(row.updated_at),
                    updated_by:        Number(row.updated_by),
                },
            });
        } catch (err) {
            console.error('[Group/customization/update]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

// ─── POST /api/node/group/customization/reset ─────────────────────────────────

function resetCustomization(ctx) {
    return async (req, res) => {
        const userId  = req.userId;
        const groupId = parseInt(req.body.group_id);
        if (!groupId || groupId < 1) {
            return res.json({ api_status: 400, error_message: 'group_id is required' });
        }

        try {
            const isAdmin = await isGroupAdmin(ctx, groupId, userId);
            if (!isAdmin) {
                return res.json({ api_status: 403, error_message: 'Only admins can reset group theme' });
            }

            const now = Math.floor(Date.now() / 1000);
            await ctx.sequelize.query(
                `UPDATE Wo_GroupChat SET
                    theme_bubble_style = 'STANDARD',
                    theme_preset_background = 'ocean',
                    theme_accent_color = '#2196F3',
                    theme_enabled_by_admin = 1,
                    theme_updated_at = ?,
                    theme_updated_by = ?
                WHERE group_id = ?`,
                { replacements: [now, userId, groupId], type: QueryTypes.UPDATE }
            );

            return res.json({ api_status: 200, message: 'Group theme reset to defaults' });
        } catch (err) {
            console.error('[Group/customization/reset]', err.message);
            return res.json({ api_status: 500, error_message: 'Server error' });
        }
    };
}

module.exports = { getCustomization, updateCustomization, resetCustomization };
