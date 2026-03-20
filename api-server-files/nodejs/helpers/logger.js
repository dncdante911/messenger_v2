'use strict';

/**
 * Centralized Winston logger.
 *
 * Transports:
 *   Console  — all levels, colorized text (PM2 captures stdout → pm2-out.log)
 *   File     — error level only, JSON, max 10 MB × 5 rotated files
 *
 * Usage (explicit):
 *   const logger = require('./helpers/logger');
 *   logger.info('Server started');
 *   logger.error('DB connection failed', { err: error.message });
 *
 * Usage (implicit – console override in main.js):
 *   console.log/info/warn/error/debug are globally redirected through this
 *   logger so no other file needs to be changed.
 *
 * Environment variables:
 *   LOG_LEVEL  — 'debug' | 'info' | 'warn' | 'error'  (default: 'info')
 *   LOG_DIR    — override log file directory
 */

const { createLogger, format, transports } = require('winston');
const path = require('path');
const fs   = require('fs');

// ── Log directory ─────────────────────────────────────────────────────────────
const LOG_DIR = process.env.LOG_DIR || (
    process.env.NODE_ENV === 'production'
        ? '/www/wwwroot/worldmates.club/nodejs/logs'
        : path.resolve(__dirname, '../../logs')
);
try { fs.mkdirSync(LOG_DIR, { recursive: true }); } catch (_) {}

// ── Formats ───────────────────────────────────────────────────────────────────
const timestampFmt  = format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' });
const errorsWithStack = format.errors({ stack: true });

const consoleFormat = format.combine(
    timestampFmt,
    format.colorize({ all: true }),
    format.printf(({ timestamp, level, message, stack }) =>
        `${timestamp} [${level}]: ${stack || message}`
    )
);

const fileFormat = format.combine(
    timestampFmt,
    errorsWithStack,
    format.json()
);

// ── Logger instance ────────────────────────────────────────────────────────────
const logger = createLogger({
    level: process.env.LOG_LEVEL || 'info',
    transports: [
        // ── stdout (all levels) ───────────────────────────────────────────────
        new transports.Console({ format: consoleFormat }),

        // ── error.log (errors only, auto-rotated) ────────────────────────────
        new transports.File({
            filename: path.join(LOG_DIR, 'error.log'),
            level:    'error',
            format:   fileFormat,
            maxsize:  10 * 1024 * 1024,  // 10 MB per file
            maxFiles: 5,
            tailable: true,
        }),
    ],
});

module.exports = logger;
