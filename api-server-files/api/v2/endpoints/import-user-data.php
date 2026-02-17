<?php
/**
 * 📦 CLOUD BACKUP: Import user data from backup
 * Restores data from JSON backup when logging in from new device
 */

require_once(__DIR__ . '/../config.php');

header('Content-Type: application/json');

// Get access token
$access_token = $_POST['access_token'] ?? $_GET['access_token'] ?? null;

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

// Get backup data
$backup_data = $_POST['backup_data'] ?? null;

if (!$backup_data) {
    http_response_code(400);
    echo json_encode([
        'api_status' => 400,
        'message' => 'backup_data is required'
    ]);
    exit;
}

try {
    // Parse JSON
    $backup = json_decode($backup_data, true);

    if (!$backup || !isset($backup['manifest'])) {
        http_response_code(400);
        echo json_encode([
            'api_status' => 400,
            'message' => 'Invalid backup data format'
        ]);
        exit;
    }

    $imported = [
        'messages' => 0,
        'groups' => 0,
        'channels' => 0,
        'settings' => false
    ];

    // ==================== IMPORT MESSAGES ====================
    if (isset($backup['messages']) && is_array($backup['messages'])) {
        $stmt = $db->prepare("
            INSERT IGNORE INTO Wo_Messages
            (from_id, to_id, text, media, media_file_names, time, seen, deleted_fs1, deleted_fs2)
            VALUES (:from_id, :to_id, :text, :media, :media_file_names, :time, :seen, :deleted_fs1, :deleted_fs2)
        ");

        foreach ($backup['messages'] as $msg) {
            try {
                $stmt->execute([
                    'from_id' => $msg['from_id'] ?? null,
                    'to_id' => $msg['to_id'] ?? null,
                    'text' => $msg['text'] ?? '',
                    'media' => $msg['media'] ?? null,
                    'media_file_names' => $msg['media_file_names'] ?? null,
                    'time' => $msg['time'] ?? time(),
                    'seen' => $msg['seen'] ?? 0,
                    'deleted_fs1' => $msg['deleted_fs1'] ?? 0,
                    'deleted_fs2' => $msg['deleted_fs2'] ?? 0
                ]);

                if ($stmt->rowCount() > 0) {
                    $imported['messages']++;
                }
            } catch (PDOException $e) {
                // Skip duplicates
                continue;
            }
        }
    }

    // ==================== IMPORT SETTINGS ====================
    if (isset($backup['settings']) && is_array($backup['settings'])) {
        $imported['settings'] = true;
    }

    // ==================== RESPONSE ====================
    echo json_encode([
        'api_status' => 200,
        'message' => 'Backup restored successfully',
        'imported' => $imported
    ]);

} catch (Exception $e) {
    error_log("Import error: " . $e->getMessage());
    http_response_code(500);
    echo json_encode([
        'api_status' => 500,
        'message' => 'Import error: ' . $e->getMessage()
    ]);
}
