<?php
/**
 * 📦 CLOUD BACKUP: List all user backups on server
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
    $backup_dir = '/var/www/www-root/data/www/worldmates.club/upload/backups/user_' . $user_id;
    $backups = [];

    if (is_dir($backup_dir)) {
        $files = scandir($backup_dir);

        foreach ($files as $file) {
            if ($file === '.' || $file === '..') continue;
            if (pathinfo($file, PATHINFO_EXTENSION) !== 'json') continue;

            $filepath = $backup_dir . '/' . $file;
            $size = filesize($filepath);
            $created_at = filemtime($filepath) * 1000; // milliseconds

            $backups[] = [
                'filename' => $file,
                'url' => 'https://worldmates.club/upload/backups/user_' . $user_id . '/' . $file,
                'size' => $size,
                'size_mb' => round($size / 1024 / 1024, 2),
                'created_at' => $created_at,
                'provider' => 'local_server'
            ];
        }

        // Sort by creation date (newest first)
        usort($backups, function($a, $b) {
            return $b['created_at'] - $a['created_at'];
        });
    }

    echo json_encode([
        'api_status' => 200,
        'backups' => $backups,
        'total_backups' => count($backups)
    ]);

} catch (Exception $e) {
    error_log("List backups error: " . $e->getMessage());
    http_response_code(500);
    echo json_encode([
        'api_status' => 500,
        'message' => 'Server error: ' . $e->getMessage()
    ]);
}
