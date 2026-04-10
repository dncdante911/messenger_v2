#!/usr/bin/env node
/**
 * generate_invite_codes.js
 *
 * Generates and inserts invite codes into wm_invite_codes.
 *
 * Standard codes (no blogger prefix):
 *   ULTRA-XXXX-XXXX-XXXX  →  lifetime premium (expires 2099-12-31)
 *   PRO-XXXX-XXXX-XXXX    →  +1 year from activation; code valid 1 year
 *
 * Blogger codes (with prefix):
 *   IVANGAI-ULTRA-XXXX-XXXX-XXXX
 *   IVANGAI-PRO-XXXX-XXXX-XXXX
 *
 * Usage:
 *   # Standard batch (500 ULTRA + 2000 PRO)
 *   node generate_invite_codes.js
 *
 *   # Blogger batch
 *   node generate_invite_codes.js --blogger IVANGAI --ultra 50 --pro 100
 *
 *   # Only PRO for a blogger
 *   node generate_invite_codes.js --blogger WYLSACOM --pro 200
 *
 * Options:
 *   --blogger NAME   Blogger tag (letters/digits, max 20 chars). Default: none.
 *   --ultra  N       How many ULTRA codes to generate.   Default: 500 (0 if --blogger set)
 *   --pro    N       How many PRO codes to generate.     Default: 2000 (0 if --blogger set)
 *
 * Requires mysql2: npm install mysql2
 * DB config: reads ../config/database.js or env vars DB_HOST, DB_USER, DB_PASS, DB_NAME
 */

'use strict';

const crypto = require('crypto');
const mysql  = require('mysql2/promise');

// ── CLI args ──────────────────────────────────────────────────────────────────
const args = process.argv.slice(2);

function getArg(name) {
    const idx = args.indexOf(name);
    return idx !== -1 ? args[idx + 1] : null;
}

const bloggerRaw  = getArg('--blogger');
const bloggerTag  = bloggerRaw ? bloggerRaw.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 20) : null;

const hasExplicitUltra = args.includes('--ultra');
const hasExplicitPro   = args.includes('--pro');

// Default counts: if running with --blogger but no explicit counts → 0 each (must specify)
const ULTRA_COUNT = parseInt(getArg('--ultra') || (bloggerTag ? '0' : '500'));
const PRO_COUNT   = parseInt(getArg('--pro')   || (bloggerTag ? '0' : '2000'));

if (bloggerTag && !hasExplicitUltra && !hasExplicitPro) {
    console.error('ERROR: --blogger requires at least one of --ultra N or --pro N');
    console.error('Example: node generate_invite_codes.js --blogger IVANGAI --ultra 50 --pro 100');
    process.exit(1);
}

if (bloggerTag && !/^[A-Z][A-Z0-9]{1,19}$/.test(bloggerTag)) {
    console.error(`ERROR: blogger tag "${bloggerTag}" is invalid. Use letters and digits only, 2–20 chars.`);
    process.exit(1);
}

// ── DB Config ─────────────────────────────────────────────────────────────────
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

// ── Timestamps ────────────────────────────────────────────────────────────────
const now          = Math.floor(Date.now() / 1000);
const ultraExpires = Math.floor(new Date('2099-12-31T23:59:59Z').getTime() / 1000);
const proExpires   = now + 365 * 24 * 60 * 60;

// ── Helpers ───────────────────────────────────────────────────────────────────

function segment(len) {
    return crypto.randomBytes(Math.ceil(len / 2))
        .toString('hex')
        .toUpperCase()
        .slice(0, len);
}

function makeCode(type) {
    const suffix = `${segment(4)}-${segment(4)}-${segment(4)}`;
    if (bloggerTag) {
        return `${bloggerTag}-${type}-${suffix}`;
    }
    return `${type}-${suffix}`;
}

function generateCodes(type, count) {
    const set = new Set();
    while (set.size < count) {
        set.add(makeCode(type));
    }
    return [...set];
}

// ── Main ──────────────────────────────────────────────────────────────────────
async function main() {
    console.log('─'.repeat(60));
    if (bloggerTag) {
        console.log(`Blogger tag : ${bloggerTag}`);
    } else {
        console.log('Mode        : standard (no blogger prefix)');
    }
    console.log(`ULTRA codes : ${ULTRA_COUNT}`);
    console.log(`PRO codes   : ${PRO_COUNT}`);
    console.log('─'.repeat(60));

    if (ULTRA_COUNT === 0 && PRO_COUNT === 0) {
        console.error('Nothing to generate. Use --ultra N and/or --pro N');
        process.exit(1);
    }

    const connection = await mysql.createConnection(dbConfig);
    console.log('[generate] Connected to database.');

    try {
        await connection.beginTransaction();

        const ultraCodes = generateCodes('ULTRA', ULTRA_COUNT);
        const proCodes   = generateCodes('PRO',   PRO_COUNT);

        // Cross-contamination guard (collision between ULTRA and PRO sets is
        // astronomically unlikely but check anyway)
        const ultraSet = new Set(ultraCodes);
        const proSet   = new Set(proCodes.filter(c => !ultraSet.has(c)));

        const rows = [
            ...ultraCodes.map(c => [c, 'ultra', bloggerTag || null, now, ultraExpires]),
            ...[...proSet].map(c => [c, 'pro',   bloggerTag || null, now, proExpires]),
        ];

        const BATCH = 500;
        let inserted = 0;
        for (let i = 0; i < rows.length; i += BATCH) {
            const batch = rows.slice(i, i + BATCH);
            await connection.query(
                `INSERT IGNORE INTO wm_invite_codes
                    (code, type, blogger_tag, created_at, expires_at)
                 VALUES ?`,
                [batch]
            );
            inserted += batch.length;
            process.stdout.write(`\r[generate] Inserted ${inserted}/${rows.length} codes...`);
        }

        await connection.commit();

        const tag = bloggerTag ? ` [${bloggerTag}]` : '';
        console.log(`\n[generate] Done.${tag} ${ultraCodes.length} ULTRA + ${proSet.size} PRO codes inserted.`);

        if (ultraCodes.length > 0) {
            console.log('\nSample ULTRA codes:');
            ultraCodes.slice(0, 5).forEach(c => console.log('  ', c));
        }
        if (proSet.size > 0) {
            console.log('\nSample PRO codes:');
            [...proSet].slice(0, 5).forEach(c => console.log('  ', c));
        }

    } catch (err) {
        await connection.rollback();
        console.error('\n[generate] Error, rolled back:', err.message);
        process.exit(1);
    } finally {
        await connection.end();
    }
}

main();
