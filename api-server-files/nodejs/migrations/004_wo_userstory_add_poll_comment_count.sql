-- Migration 004: Add poll and comment_count columns to Wo_UserStory
-- poll          — stores poll JSON (question + options + voter IDs) for interactive story polls
-- comment_count — cached count of comments for fast reads (incremented on comment creation)

ALTER TABLE `Wo_UserStory`
    ADD COLUMN IF NOT EXISTS `poll`          TEXT          NULL     DEFAULT NULL    COMMENT 'JSON-encoded poll data: {question, options:[{id,text,votes,voters:[]}]}',
    ADD COLUMN IF NOT EXISTS `comment_count` INT UNSIGNED  NOT NULL DEFAULT 0       COMMENT 'Cached comment count, incremented on every new comment';
