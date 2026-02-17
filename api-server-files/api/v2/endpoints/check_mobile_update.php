<?php
/**
 * Lightweight endpoint for Android in-app update checks.
 * Response example:
 * {
 *   "success": true,
 *   "data": {
 *     "latest_version": "2.1.0",
 *     "version_code": 3,
 *     "apk_url": "https://worldmates.club/sources-app/worldmates-latest.apk",
 *     "changelog": ["Новые one-click темы", "Автообновление с сервера"],
 *     "is_mandatory": false,
 *     "published_at": "2026-02-07T09:00:00Z"
 *   }
 * }
 */

header('Content-Type: application/json; charset=utf-8');

$configPath = __DIR__ . '/mobile_update_config.json';
if (!file_exists($configPath)) {
    http_response_code(200);
    echo json_encode([
        'success' => true,
        'data' => [
            'latest_version' => '2.0.0',
            'version_code' => 2,
            'apk_url' => 'https://worldmates.club/sources-app/worldmates-latest.apk',
            'changelog' => ['Первый стабильный релиз'],
            'is_mandatory' => false,
            'published_at' => gmdate('c'),
        ],
        'message' => 'Using default update config'
    ], JSON_UNESCAPED_UNICODE);
    exit;
}

$raw = file_get_contents($configPath);
$data = json_decode($raw, true);
if (!is_array($data)) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'Invalid mobile_update_config.json'
    ], JSON_UNESCAPED_UNICODE);
    exit;
}

echo json_encode([
    'success' => true,
    'data' => [
        'latest_version' => $data['latest_version'] ?? '2.0.0',
        'version_code' => intval($data['version_code'] ?? 2),
        'apk_url' => $data['apk_url'] ?? 'https://worldmates.club/sources-app/worldmates-latest.apk',
        'changelog' => is_array($data['changelog'] ?? null) ? $data['changelog'] : ['Update available'],
        'is_mandatory' => boolval($data['is_mandatory'] ?? false),
        'published_at' => $data['published_at'] ?? gmdate('c'),
    ]
], JSON_UNESCAPED_UNICODE);
