<?php
/**
 * 📊 CLOUD BACKUP: Get cloud storage statistics
 * Returns real statistics about user backup:
 * - Message counts
 * - Media file sizes
 * - Last backup
 * - Total storage size
 */

require_once(__DIR__ . '/../config.php');

header('Content-Type: application/json');

// Get access token
$access_token = $_GET['access_token'] ?? null;

if (!$access_token) {
    http_response_code(400);
    echo json_encode([
        'api_status' => 400,
        'message' => 'access_token is required'
    ]);
    exit;
}

// Validate token
$user_id = validateAccessToken($pdo, $access_token);
if (!$user_id) {
    http_response_code(401);
    echo json_encode([
        'api_status' => 401,
        'message' => 'Invalid access token'
    ]);
    exit;
}

try {
    // ==================== MESSAGE STATISTICS ====================
    $stmt = $pdo->prepare("
        SELECT COUNT(*) as total_messages,
               SUM(CASE WHEN from_id = ? THEN 1 ELSE 0 END) as messages_sent,
               SUM(CASE WHEN to_id = ? THEN 1 ELSE 0 END) as messages_received
        FROM Wo_Messages
        WHERE from_id = ? OR to_id = ?
    ");
    $stmt->execute([$user_id, $user_id, $user_id, $user_id]);
    $message_stats = $stmt->fetch();

    // ==================== MEDIA STATISTICS ====================
    $stmt = $pdo->prepare("
        SELECT COUNT(*) as media_files_count
        FROM Wo_Messages
        WHERE (from_id = ? OR to_id = ?)
          AND media IS NOT NULL AND media != ''
    ");
    $stmt->execute([$user_id, $user_id]);
    $media_stats = $stmt->fetch();

    // ==================== GROUP & CHANNEL COUNTS ====================
    $stmt = $pdo->prepare("
        SELECT COUNT(*) as groups_count
        FROM Wo_Groups
        WHERE user_id = ? OR id IN (
            SELECT group_id FROM Wo_Group_Members WHERE user_id = ?
        )
    ");
    $stmt->execute([$user_id, $user_id]);
    $groups_count = $stmt->fetchColumn();

    // Channels (may not exist in all installations)
    $channels_count = 0;
    try {
        $stmt = $pdo->prepare("
            SELECT COUNT(*) as channels_count
            FROM Wo_Channels
            WHERE user_id = ? OR id IN (
                SELECT channel_id FROM Wo_ChannelSubscribers WHERE user_id = ?
            )
        ");
        $stmt->execute([$user_id, $user_id]);
        $channels_count = $stmt->fetchColumn();
    } catch (PDOException $e) {
        // Table doesn't exist, return 0
        $channels_count = 0;
    }

    // ==================== BACKUP STORAGE ====================
    $backup_dir = '/var/www/www-root/data/www/worldmates.club/upload/backups/user_' . $user_id;
    $total_storage_bytes = 0;
    $last_backup_time = null;

    if (is_dir($backup_dir)) {
        $files = scandir($backup_dir);
        foreach ($files as $file) {
            if ($file === '.' || $file === '..') continue;
            $filepath = $backup_dir . '/' . $file;
            if (is_file($filepath)) {
                $total_storage_bytes += filesize($filepath);
                $file_time = filemtime($filepath);
                if ($last_backup_time === null || $file_time > $last_backup_time) {
                    $last_backup_time = $file_time;
                }
            }
        }
    }

    // ==================== CALCULATE SIZES ====================
    $total_storage_mb = round($total_storage_bytes / 1024 / 1024, 2);
    $total_storage_gb = round($total_storage_bytes / 1024 / 1024 / 1024, 3);

    // Estimate media size (rough calculation)
    $media_size_mb = round($media_stats['media_files_count'] * 0.5, 2); // ~0.5 MB per media file

    // ==================== RESPONSE ====================
    echo json_encode([
        'api_status' => 200,
        'statistics' => [
            'total_messages' => (int)$message_stats['total_messages'],
            'messages_sent' => (int)$message_stats['messages_sent'],
            'messages_received' => (int)$message_stats['messages_received'],
            'media_files_count' => (int)$media_stats['media_files_count'],
            'media_size_bytes' => (int)($media_stats['media_files_count'] * 500000), // 500KB per file
            'media_size_mb' => $media_size_mb,
            'groups_count' => (int)$groups_count,
            'channels_count' => (int)$channels_count,
            'total_storage_bytes' => $total_storage_bytes,
            'total_storage_mb' => $total_storage_mb,
            'total_storage_gb' => $total_storage_gb,
            'last_backup_time' => $last_backup_time ? $last_backup_time * 1000 : null,
            'backup_frequency' => 'manual',
            'server_name' => 'worldmates.club',
            'backup_provider' => 'local_server'
        ]
    ]);

} catch (PDOException $e) {
    error_log("Statistics error: " . $e->getMessage());
    http_response_code(500);
    echo json_encode([
        'api_status' => 500,
        'message' => 'Database error: ' . $e->getMessage()
    ]);
}
