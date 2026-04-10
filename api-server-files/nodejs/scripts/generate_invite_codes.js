#!/usr/bin/env node
/**
 * generate_invite_codes.js
 *
 * Generates and inserts invite codes into wm_invite_codes:
 *   500 × ULTRA-XXXX-XXXX-XXXX  →  lifetime premium (expires_at = 2099-12-31)
 *  2000 × PRO-XXXX-XXXX-XXXX    →  +1 year from activation; code expires 1 year from generation
 *
 * Usage:
 *   node generate_invite_codes.js
 *
 * Requires the same database config as the main server (reads from ../config/database.js
 * or falls back to env variables DB_HOST, DB_USER, DB_PASS, DB_NAME).
 */

'use strict';

const crypto = require('crypto');
const mysql  = require('mysql2/promise');

// ── Config ────────────────────────────────────────────────────────────────────
let dbConfig;
try {
    dbConfig = require('../config/database.js');
} catch {
    dbConfig = {
        host:     process.env.DB_HOST || '127.0.0.1',
        user:     process.env.DB_USER || 'root',
        password: process.env.DB_PASS || '',
        database: process.env.DB_NAME || 'worldmates',
    };
}

const ULTRA_COUNT = 500;
const PRO_COUNT   = 2000;

// ULTRA codes never expire; PRO codes expire 1 year from now
const now          = Math.floor(Date.now() / 1000);
const ultraExpires = Math.floor(new Date('2099-12-31T23:59:59Z').getTime() / 1000);
const proExpires   = now + 365 * 24 * 60 * 60;   // code validity window: 1 year

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Generate a random uppercase hex segment of `len` chars. */
function segment(len) {
    return crypto.randomBytes(Math.ceil(len / 2))
        .toString('hex')
        .toUpperCase()
        .slice(0, len);
}

/** Generate one invite code with the given prefix (ULTRA / PRO). */
function makeCode(prefix) {
    return `${prefix}-${segment(4)}-${segment(4)}-${segment(4)}`;
}

/** Generate `count` unique codes, retrying on collision (extremely unlikely). */
function generateCodes(prefix, count) {
    const set = new Set();
    while (set.size < count) {
        set.add(makeCode(prefix));
    }
    return [...set];
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
    const connection = await mysql.createConnection(dbConfig);
    console.log('[generate_invite_codes] Connected to database.');

    try {
        await connection.beginTransaction();

        const ultraCodes = generateCodes('ULTRA', ULTRA_COUNT);
        const proCodes   = generateCodes('PRO',   PRO_COUNT);

        // Remove any code that was accidentally shared between sets (collision safety)
        const ultraSet = new Set(ultraCodes);
        const proSet   = new Set(proCodes.filter(c => !ultraSet.has(c)));

        const rows = [
            ...ultraCodes.map(code => [code, 'ultra', now, ultraExpires]),
            ...[...proSet].map(code  => [code, 'pro',   now, proExpires]),
        ];

        // Insert in batches of 500 to avoid oversized packets
        const BATCH = 500;
        let inserted = 0;
        for (let i = 0; i < rows.length; i += BATCH) {
            const batch = rows.slice(i, i + BATCH);
            await connection.query(
                'INSERT IGNORE INTO wm_invite_codes (code, type, created_at, expires_at) VALUES ?',
                [batch]
            );
            inserted += batch.length;
            process.stdout.write(`\r[generate_invite_codes] Inserted ${inserted}/${rows.length} codes...`);
        }

        await connection.commit();
        console.log(`\n[generate_invite_codes] Done. ${ultraCodes.length} ULTRA + ${proSet.size} PRO codes inserted.`);

        // Print a few sample codes for verification
        console.log('\nSample ULTRA codes:');
        ultraCodes.slice(0, 3).forEach(c => console.log(' ', c));
        console.log('Sample PRO codes:');
        [...proSet].slice(0, 3).forEach(c => console.log(' ', c));

    } catch (err) {
        await connection.rollback();
        console.error('[generate_invite_codes] Error, rolled back:', err.message);
        process.exit(1);
    } finally {
        await connection.end();
    }
}

main();
