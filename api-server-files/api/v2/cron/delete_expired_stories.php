<?php
// +------------------------------------------------------------------------+
// | @author Deen Doughouz (DoughouzForest)
// | @author_url 1: http://www.wowonder.com
// | @author_url 2: http://codecanyon.net/user/doughouzforest
// | @author_email: wowondersocial@gmail.com
// +------------------------------------------------------------------------+
// | WoWonder - The Ultimate Social Networking Platform
// | Copyright (c) 2018 WoWonder. All rights reserved.
// +------------------------------------------------------------------------+
// Cron job to delete expired stories
// Run this script every hour: 0 * * * * php /path/to/delete_expired_stories.php

if (php_sapi_name() !== 'cli') {
    die('This script can only be run from the command line.');
}

// Використовуємо __DIR__ для абсолютного шляху
// Файл: /var/www/www-root/data/www/worldmates.club/api/v2/cron/delete_expired_stories.php
// init.php: /var/www/www-root/data/www/worldmates.club/assets/init.php
require_once(__DIR__ . '/../../../assets/init.php');

global $sqlConnect, $db;

$current_time = time();

// Get all expired stories
$expired_stories = $db->where('expire', $current_time, '<')->get(T_USER_STORY);

$deleted_count = 0;
$deleted_media_count = 0;
$deleted_comments_count = 0;

if (!empty($expired_stories)) {
    foreach ($expired_stories as $story) {
        $story_id = $story->id;

        // Delete story media files
        $media_files = $db->where('story_id', $story_id)->get(T_USER_STORY_MEDIA);
        if (!empty($media_files)) {
            foreach ($media_files as $media) {
                // Delete physical file
                if (!empty($media->filename) && file_exists($media->filename)) {
                    @unlink($media->filename);
                }
                $deleted_media_count++;
            }
        }

        // Delete media records from database
        $db->where('story_id', $story_id)->delete(T_USER_STORY_MEDIA);

        // Delete story comments
        $comment_delete = $db->where('story_id', $story_id)->delete(T_STORY_COMMENTS);
        if ($comment_delete) {
            $deleted_comments_count += $comment_delete;
        }

        // Delete story views
        $db->where('story_id', $story_id)->delete(T_STORY_SEEN);

        // Delete story reactions
        $db->where('story_id', $story_id)->delete(T_REACTIONS);

        // Delete story mutes
        $db->where('story_user_id', $story->user_id)->delete(T_MUTE_STORY);

        // Delete the story itself
        $delete = $db->where('id', $story_id)->delete(T_USER_STORY);

        if ($delete) {
            $deleted_count++;
        }
    }
}

$log_message = sprintf(
    "[%s] Deleted %d expired stories, %d media files, %d comments\n",
    date('Y-m-d H:i:s'),
    $deleted_count,
    $deleted_media_count,
    $deleted_comments_count
);

// Log the result
$log_file = dirname(__FILE__) . '/logs/expired_stories.log';
$log_dir = dirname($log_file);

if (!is_dir($log_dir)) {
    mkdir($log_dir, 0755, true);
}

file_put_contents($log_file, $log_message, FILE_APPEND);

echo $log_message;
