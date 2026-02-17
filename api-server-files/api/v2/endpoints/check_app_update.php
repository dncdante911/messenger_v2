<?php
/**
 * check_app_update.php - Check for Android app updates
 *
 * Server structure (ISPmanager 6):
 *   Site root:    /var/www/worldmates.club/  (or wherever ISPmanager puts it)
 *   APK storage:  {site_root}/sources-app/
 *   This file:    {site_root}/api/v2/endpoints/check_app_update.php
 *   Config:       {site_root}/sources-app/version.json
 *
 * Parameters:
 *   - current_version_code (int) - Current app versionCode
 *   - current_version_name (string) - Current app versionName
 *   - platform (string) - "android" (for future iOS support)
 *
 * Returns:
 *   - update_available (bool)
 *   - latest_version_code (int)
 *   - latest_version_name (string)
 *   - download_url (string) - Direct APK download URL
 *   - changelog (string) - What's new
 *   - force_update (bool) - Must update to continue
 *   - file_size (int) - APK file size in bytes
 */

header('Content-Type: application/json; charset=UTF-8');

// Determine site root: go up from api/v2/endpoints/ -> site root
// __DIR__ = {site_root}/api/v2/endpoints
$site_root = realpath(__DIR__ . '/../../../') . '/';

// APK storage directory: {site_root}/sources-app/
$apk_directory = $site_root . 'sources-app/';
$version_config_file = $apk_directory . 'version.json';

// Create directory and default config if not exists
if (!is_dir($apk_directory)) {
    @mkdir($apk_directory, 0755, true);
}

if (!file_exists($version_config_file)) {
    $default_config = [
        'android' => [
            'latest_version_code' => 2,
            'latest_version_name' => '2.0-EDIT-FIX',
            'min_version_code' => 1,
            'download_url' => '',
            'changelog' => '',
            'apk_filename' => '',
            'file_size' => 0
        ]
    ];
    file_put_contents($version_config_file, json_encode($default_config, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
}

// Read version config
$version_config = json_decode(file_get_contents($version_config_file), true);
if (!$version_config) {
    echo json_encode([
        'api_status' => 500,
        'error_message' => 'Failed to read version configuration'
    ]);
    exit;
}

$platform = $_POST['platform'] ?? $_GET['platform'] ?? 'android';
$current_version_code = intval($_POST['current_version_code'] ?? $_GET['current_version_code'] ?? 0);
$current_version_name = $_POST['current_version_name'] ?? $_GET['current_version_name'] ?? '';

if (!isset($version_config[$platform])) {
    echo json_encode([
        'api_status' => 404,
        'error_message' => "Platform '$platform' not configured"
    ]);
    exit;
}

$platform_config = $version_config[$platform];
$latest_version_code = intval($platform_config['latest_version_code'] ?? 0);
$latest_version_name = $platform_config['latest_version_name'] ?? '';
$min_version_code = intval($platform_config['min_version_code'] ?? 1);
$changelog = $platform_config['changelog'] ?? '';
$apk_filename = $platform_config['apk_filename'] ?? '';
$file_size = intval($platform_config['file_size'] ?? 0);

// Build download URL
$download_url = '';
if (!empty($apk_filename) && file_exists($apk_directory . $apk_filename)) {
    $protocol = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? 'worldmates.club';
    // URL path: /sources-app/{filename}
    $download_url = $protocol . '://' . $host . '/sources-app/' . $apk_filename;

    // Update file size from actual file
    $file_size = filesize($apk_directory . $apk_filename);
} elseif (!empty($platform_config['download_url'])) {
    // Fallback to manually specified URL
    $download_url = $platform_config['download_url'];
}

$update_available = ($latest_version_code > $current_version_code) && !empty($download_url);
$force_update = ($current_version_code < $min_version_code) && $update_available;

echo json_encode([
    'api_status' => 200,
    'update_available' => $update_available,
    'force_update' => $force_update,
    'latest_version_code' => $latest_version_code,
    'latest_version_name' => $latest_version_name,
    'min_version_code' => $min_version_code,
    'download_url' => $download_url,
    'changelog' => $changelog,
    'file_size' => $file_size
], JSON_PRETTY_PRINT);
