-- Migration: add_web_app_url_to_bots
-- Date: 2026-03-25
-- Description: Adds web_app_url column to Wo_Bots table for Mini Apps support.
--              This column stores the HTTPS URL of the bot's Mini App (Web App).
--
-- Usage: mysql -u <user> -p <database> < add_web_app_url_to_bots.sql

ALTER TABLE `Wo_Bots`
    ADD COLUMN `web_app_url` VARCHAR(512) NULL DEFAULT NULL
    COMMENT 'HTTPS URL of the Mini App (Web App) for this bot'
    AFTER `last_active_at`;
