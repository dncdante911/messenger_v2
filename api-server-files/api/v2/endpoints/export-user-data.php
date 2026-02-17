<?php
/**
 * 📦 CLOUD BACKUP: Export all user data for backup
 * Returns JSON with all messages, contacts, groups, channels
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
$user_id = validateAccessToken($db, $access_token);
if (!$user_id) {
    http_response_code(401);
    echo json_encode([
        'api_status' => 401,
        'message' => 'Invalid access token'
    ]);
    exit;
}

try {
    $export_data = [];

    // ==================== MANIFEST ====================
    $export_data['manifest'] = [
        'version' => '2.0',
        'created_at' => time() * 1000, // milliseconds
        'user_id' => $user_id,
        'app_version' => '2.0-EDIT-FIX',
        'encryption' => 'none',
        'total_messages' => 0
    ];

    // ==================== USER ====================
    $stmt = $db->prepare("
        SELECT user_id, username, first_name, last_name, email, phone_number,
               avatar, cover, about, gender, birthday, country_id, city, zip
        FROM Wo_Users
        WHERE user_id = :user_id
    ");
    $stmt->execute(['user_id' => $user_id]);
    $export_data['user'] = $stmt->fetch();

    // ==================== MESSAGES ====================
    $stmt = $db->prepare("
        SELECT id, from_id, to_id, text, media, mediaFileNames,
               time, seen, deleted_one, deleted_two,
               product_id, lat, lng, reply_id, story_id
        FROM Wo_Messages
        WHERE from_id = ? OR to_id = ?
        ORDER BY id ASC
    ");
    $stmt->execute([$user_id, $user_id]);
    $messages = $stmt->fetchAll();
    $export_data['messages'] = $messages;
    $export_data['manifest']['total_messages'] = count($messages);

    // ==================== CONTACTS ====================
    $stmt = $db->prepare("
        SELECT user_id, username, first_name, last_name, avatar, verified
        FROM Wo_Users
        WHERE user_id IN (
            SELECT following_id FROM Wo_Followers WHERE follower_id = :user_id
        )
    ");
    $stmt->execute(['user_id' => $user_id]);
    $export_data['contacts'] = $stmt->fetchAll();

    // ==================== GROUPS ====================
    $stmt = $db->prepare("
        SELECT id, user_id, group_name, group_title, avatar, cover, about, category
        FROM Wo_Groups
        WHERE user_id = ? OR id IN (
            SELECT group_id FROM Wo_Group_Members WHERE user_id = ?
        )
    ");
    $stmt->execute([$user_id, $user_id]);
    $export_data['groups'] = $stmt->fetchAll();

    // ==================== CHANNELS ====================
    // Channels table may not exist in all installations
    $export_data['channels'] = [];
    try {
        $stmt = $db->prepare("
            SELECT id, user_id, channel_name, channel_username as channel_title,
                   avatar, cover, channel_description as description
            FROM Wo_Channels
            WHERE user_id = ? OR id IN (
                SELECT channel_id FROM Wo_ChannelSubscribers WHERE user_id = ?
            )
        ");
        $stmt->execute([$user_id, $user_id]);
        $export_data['channels'] = $stmt->fetchAll();
    } catch (PDOException $e) {
        // Table doesn't exist, return empty array
        $export_data['channels'] = [];
    }

    // ==================== SETTINGS ====================
    // Settings are stored directly in Wo_Users table, not in separate table
    // User settings already exported in 'user' section above
    $export_data['settings'] = [];

    // ==================== BLOCKED USERS ====================
    $stmt = $db->prepare("
        SELECT blocked FROM Wo_Blocks WHERE blocker = ?
    ");
    $stmt->execute([$user_id]);
    $blocked = $stmt->fetchAll(PDO::FETCH_COLUMN);
    $export_data['blocked_users'] = $blocked;

    // ==================== SAVE TO SERVER ====================
    $backup_dir = '/var/www/www-root/data/www/worldmates.club/upload/backups/user_' . $user_id;
    if (!is_dir($backup_dir)) {
        mkdir($backup_dir, 0755, true);
    }

    $filename = 'backup_' . date('Y-m-d_H-i-s') . '.json';
    $filepath = $backup_dir . '/' . $filename;

    $json = json_encode($export_data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
    file_put_contents($filepath, $json);

    $backup_size = filesize($filepath);
    $backup_url = 'https://worldmates.club/upload/backups/user_' . $user_id . '/' . $filename;

    // ==================== RESPONSE ====================
    echo json_encode([
        'api_status' => 200,
        'message' => 'Backup created successfully',
        'backup_file' => $filename,
        'backup_url' => $backup_url,
        'backup_size' => $backup_size,
        'export_data' => $export_data
    ]);

} catch (PDOException $e) {
    error_log("Export error: " . $e->getMessage());
    http_response_code(500);
    echo json_encode([
        'api_status' => 500,
        'message' => 'Database error: ' . $e->getMessage()
    ]);
}
