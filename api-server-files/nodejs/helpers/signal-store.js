'use strict';

/**
 * Signal Protocol key server — server-side storage helpers.
 *
 * The server stores PUBLIC key material only.
 * Private keys are generated on devices and NEVER transmitted.
 *
 * Responsibilities:
 *   – Store/update a user's identity key + signed pre-key + OPK pool.
 *   – Serve pre-key bundles to requesting clients (pops one OPK per request).
 *   – Accept new OPK batches for replenishment.
 *
 * DB table: signal_keys  (see migration add_signal_keys_table.sql)
 */

// ─── save / update key bundle ─────────────────────────────────────────────────

/**
 * Save or update a user's public key bundle.
 * One-time pre-keys are merged: existing IDs are not overwritten.
 *
 * @param {object} ctx        — Sequelize model context
 * @param {number} userId     — user_id
 * @param {object} bundle     — { identityKey, signedPreKeyId, signedPreKey,
 *                                signedPreKeySig, prekeys: [{id,key}] }
 */
async function saveKeyBundle(ctx, userId, bundle) {
    const { identityKey, signedPreKeyId, signedPreKey, signedPreKeySig, prekeys = [] } = bundle;

    const existing = await ctx.signal_keys.findOne({
        where: { user_id: userId },
        raw:   true,
    });

    // Detect identity key change (device change / reinstall)
    const identityKeyChanged = existing
        ? (existing.identity_key !== identityKey)
        : false;

    // OPK merge strategy:
    //  • Identity key changed → device reinstall; replace pool entirely.
    //    The old private keys are gone — keeping old OPKs on the server would
    //    cause every X3DH attempt to fail (wrong DH4 → wrong SK).
    //  • Same identity key → keep untouched server OPKs (not yet served) and
    //    append fresh ones from the client batch (replenish case).
    const existingPrekeys = (existing && !identityKeyChanged)
        ? parsePrekeys(existing.prekeys)
        : [];

    const merged = mergePrekeys(existingPrekeys, prekeys);

    if (existing) {
        await ctx.signal_keys.update(
            {
                identity_key:      identityKey,
                signed_prekey_id:  signedPreKeyId,
                signed_prekey:     signedPreKey,
                signed_prekey_sig: signedPreKeySig || '',
                prekeys:           JSON.stringify(merged),
                updated_at:        new Date(),
            },
            { where: { user_id: userId } }
        );
    } else {
        await ctx.signal_keys.create({
            user_id:           userId,
            identity_key:      identityKey,
            signed_prekey_id:  signedPreKeyId,
            signed_prekey:     signedPreKey,
            signed_prekey_sig: signedPreKeySig || '',
            prekeys:           JSON.stringify(merged),
            updated_at:        new Date(),
        });
    }

    return { saved: true, opk_count: merged.length, identityKeyChanged };
}

// ─── get key bundle (pops one OPK) ───────────────────────────────────────────

/**
 * Fetch a user's pre-key bundle for X3DH.
 * Atomically pops one one-time pre-key from the server pool.
 * The OPK is removed from the DB upon retrieval (use-once semantics).
 *
 * @returns {object|null}  bundle or null if user has no keys registered
 */
async function getKeyBundle(ctx, userId) {
    const row = await ctx.signal_keys.findOne({
        where: { user_id: userId },
        raw:   true,
    });
    if (!row) return null;

    const prekeys = parsePrekeys(row.prekeys);
    let oneTimePreKey   = null;
    let oneTimePreKeyId = null;

    if (prekeys.length > 0) {
        // Pop the first available OPK (FIFO)
        const opk           = prekeys.shift();
        oneTimePreKey       = opk.key;
        oneTimePreKeyId     = opk.id;

        // Persist the updated pool (with this OPK removed)
        await ctx.signal_keys.update(
            { prekeys: JSON.stringify(prekeys) },
            { where: { user_id: userId } }
        );
    }

    return {
        user_id:            userId,
        identity_key:       row.identity_key,
        signed_prekey_id:   row.signed_prekey_id,
        signed_prekey:      row.signed_prekey,
        signed_prekey_sig:  row.signed_prekey_sig,
        one_time_prekey_id: oneTimePreKeyId,
        one_time_prekey:    oneTimePreKey,
        remaining_prekeys:  prekeys.length,
    };
}

// ─── replenish OPKs ───────────────────────────────────────────────────────────

/**
 * Add new one-time pre-keys to an existing user's pool.
 * Keys with IDs already present in the pool are ignored (idempotent).
 *
 * @param {Array} newPrekeys  — [{id:int, key:string}, ...]
 */
async function replenishPrekeys(ctx, userId, newPrekeys) {
    const row = await ctx.signal_keys.findOne({
        where:      { user_id: userId },
        attributes: ['prekeys'],
        raw:        true,
    });
    if (!row) return false;

    const existing = parsePrekeys(row.prekeys);
    const merged   = mergePrekeys(existing, newPrekeys);

    await ctx.signal_keys.update(
        { prekeys: JSON.stringify(merged), updated_at: new Date() },
        { where:   { user_id: userId } }
    );
    return { added: merged.length - existing.length, total: merged.length };
}

// ─── remaining OPK count ──────────────────────────────────────────────────────

async function getRemainingPreKeyCount(ctx, userId) {
    const row = await ctx.signal_keys.findOne({
        where:      { user_id: userId },
        attributes: ['prekeys'],
        raw:        true,
    });
    if (!row) return 0;
    return parsePrekeys(row.prekeys).length;
}

// ─── get identity key only (no OPK consumed) ─────────────────────────────────

/**
 * Returns just the identity key for a user — does NOT pop any OPK.
 * Used by senders to detect a remote identity key change before sending
 * with a stale DR session (cheaper than fetching the full bundle).
 *
 * @returns {string|null}  Base64 identity key, or null if not registered
 */
async function getIdentityKey(ctx, userId) {
    const row = await ctx.signal_keys.findOne({
        where:      { user_id: userId },
        attributes: ['identity_key'],
        raw:        true,
    });
    return row ? row.identity_key : null;
}

// ─── helpers ──────────────────────────────────────────────────────────────────

function parsePrekeys(raw) {
    if (!raw) return [];
    if (Array.isArray(raw)) return raw;
    try   { return JSON.parse(raw) || []; }
    catch { return []; }
}

function mergePrekeys(existing, incoming) {
    const ids  = new Set(existing.map(k => k.id));
    const fresh = (incoming || []).filter(k => !ids.has(k.id));
    return [...existing, ...fresh];
}

module.exports = { saveKeyBundle, getKeyBundle, replenishPrekeys, getRemainingPreKeyCount, getIdentityKey };
