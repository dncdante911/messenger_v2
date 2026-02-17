<?php
/**
 * Upload Group Avatar - Standalone version
 * Не зависит от WoWonder функций - работает автономно
 */

error_reporting(E_ALL);
ini_set('display_errors', 0);
ini_set('log_errors', 1);

header('Content-Type: application/json; charset=UTF-8');

// Логирование
$log_file = '/var/www/www-root/data/www/worldmates.club/api/v2/logs/group_avatar.log';
function log_msg($msg) {
    global $log_file;
    $dir = dirname($log_file);
    if (!is_dir($dir)) @mkdir($dir, 0755, true);
    @file_put_contents($log_file, "[" . date('Y-m-d H:i:s') . "] " . $msg . "\n", FILE_APPEND);
}

log_msg("========== NEW REQUEST ==========");
log_msg("POST: " . json_encode($_POST));
log_msg("FILES: " . json_encode(array_keys($_FILES)));

// Database config - HARDCODED для надежности
$db_config = [
    'host' => 'localhost',
    'name' => 'u2798186_wowd',
    'user' => 'u2798186_wowd',
    'pass' => 'u2798186_wowd'
];

// Попробуем загрузить из root config если есть
$root_config = $_SERVER['DOCUMENT_ROOT'] . '/config.php';
if (file_exists($root_config)) {
    @include_once($root_config);
    if (isset($sql_db_host)) $db_config['host'] = $sql_db_host;
    if (isset($sql_db_name)) $db_config['name'] = $sql_db_name;
    if (isset($sql_db_user)) $db_config['user'] = $sql_db_user;
    if (isset($sql_db_pass)) $db_config['pass'] = $sql_db_pass;
}

// Database connection
try {
    $pdo = new PDO(
        "mysql:host={$db_config['host']};dbname={$db_config['name']};charset=utf8mb4",
        $db_config['user'],
        $db_config['pass'],
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]
    );
    log_msg("DB connected OK");
} catch (PDOException $e) {
    log_msg("DB ERROR: " . $e->getMessage());
    http_response_code(500);
    echo json_encode(['api_status' => 500, 'error_message' => 'Database connection failed']);
    exit;
}

// Get access_token
$access_token = $_POST['access_token'] ?? $_GET['access_token'] ?? '';
log_msg("access_token: " . substr($access_token, 0, 20) . "...");

if (empty($access_token)) {
    http_response_code(401);
    echo json_encode(['api_status' => 401, 'error_message' => 'access_token is required']);
    exit;
}

// Validate token
try {
    $stmt = $pdo->prepare("SELECT user_id FROM Wo_AppsSessions WHERE session_id = ? LIMIT 1");
    $stmt->execute([$access_token]);
    $session = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$session) {
        log_msg("Invalid token - no session found");
        http_response_code(401);
        echo json_encode(['api_status' => 401, 'error_message' => 'Invalid access_token']);
        exit;
    }
    $user_id = (int)$session['user_id'];
    log_msg("User ID: $user_id");
} catch (PDOException $e) {
    log_msg("Token validation error: " . $e->getMessage());
    http_response_code(500);
    echo json_encode(['api_status' => 500, 'error_message' => 'Token validation failed']);
    exit;
}

// Get group_id
$group_id = isset($_POST['group_id']) ? (int)$_POST['group_id'] : 0;
log_msg("Group ID: $group_id");

if ($group_id < 1) {
    http_response_code(400);
    echo json_encode(['api_status' => 400, 'error_message' => 'group_id is required']);
    exit;
}

// Check group exists and user is admin
// Groups are stored in Wo_GroupChat table (same as channels)
try {
    $stmt = $pdo->prepare("SELECT group_id, user_id, group_name FROM Wo_GroupChat WHERE group_id = ? LIMIT 1");
    $stmt->execute([$group_id]);
    $group = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$group) {
        log_msg("Group not found");
        http_response_code(404);
        echo json_encode(['api_status' => 404, 'error_message' => 'Group not found']);
        exit;
    }

    $is_owner = ($group['user_id'] == $user_id);
    log_msg("Is owner: " . ($is_owner ? "yes" : "no"));

    if (!$is_owner) {
        // Check admin in group chat users
        // Support both legacy (admin='1') and new (role='admin'/'owner') fields
        $stmt = $pdo->prepare("
            SELECT user_id FROM Wo_GroupChatUsers
            WHERE group_id = ? AND user_id = ?
            AND (admin = '1' OR role IN ('admin', 'owner', 'moderator'))
            LIMIT 1
        ");
        $stmt->execute([$group_id, $user_id]);
        $is_admin = $stmt->fetch();

        if (!$is_admin) {
            log_msg("User is not admin");
            http_response_code(403);
            echo json_encode(['api_status' => 403, 'error_message' => 'Only group admins can change avatar']);
            exit;
        }
    }
} catch (PDOException $e) {
    log_msg("Group check error: " . $e->getMessage());
    http_response_code(500);
    echo json_encode(['api_status' => 500, 'error_message' => 'Database error']);
    exit;
}

// Check file
if (empty($_FILES['avatar']['tmp_name'])) {
    log_msg("No file uploaded. FILES: " . print_r($_FILES, true));
    http_response_code(400);
    echo json_encode(['api_status' => 400, 'error_message' => 'avatar file is required']);
    exit;
}

$file = $_FILES['avatar'];
log_msg("File: name={$file['name']}, size={$file['size']}, type={$file['type']}, error={$file['error']}");

// Validate file
$allowed_types = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
$max_size = 5 * 1024 * 1024; // 5MB

if ($file['error'] !== UPLOAD_ERR_OK) {
    log_msg("Upload error code: {$file['error']}");
    http_response_code(400);
    echo json_encode(['api_status' => 400, 'error_message' => 'File upload failed with error ' . $file['error']]);
    exit;
}

if ($file['size'] > $max_size) {
    http_response_code(400);
    echo json_encode(['api_status' => 400, 'error_message' => 'File too large (max 5MB)']);
    exit;
}

// Check file type by content
$finfo = finfo_open(FILEINFO_MIME_TYPE);
$mime_type = finfo_file($finfo, $file['tmp_name']);
finfo_close($finfo);
log_msg("Detected MIME: $mime_type");

if (!in_array($mime_type, $allowed_types)) {
    http_response_code(400);
    echo json_encode(['api_status' => 400, 'error_message' => 'Invalid file type. Allowed: jpg, png, gif, webp']);
    exit;
}

// Generate filename
$ext = pathinfo($file['name'], PATHINFO_EXTENSION) ?: 'jpg';
$filename = 'group_' . $group_id . '_' . time() . '_' . bin2hex(random_bytes(4)) . '.' . $ext;

// Upload directory
$upload_base = $_SERVER['DOCUMENT_ROOT'] . '/upload/photos/' . date('Y') . '/' . date('m') . '/';
if (!is_dir($upload_base)) {
    if (!@mkdir($upload_base, 0755, true)) {
        log_msg("Failed to create directory: $upload_base");
        http_response_code(500);
        echo json_encode(['api_status' => 500, 'error_message' => 'Failed to create upload directory']);
        exit;
    }
}

$upload_path = $upload_base . $filename;
$relative_path = 'upload/photos/' . date('Y') . '/' . date('m') . '/' . $filename;

log_msg("Saving to: $upload_path");

// Move file
if (!move_uploaded_file($file['tmp_name'], $upload_path)) {
    log_msg("Failed to move uploaded file");
    http_response_code(500);
    echo json_encode(['api_status' => 500, 'error_message' => 'Failed to save file']);
    exit;
}

log_msg("File saved successfully");

// Update database
try {
    $stmt = $pdo->prepare("UPDATE Wo_GroupChat SET avatar = ? WHERE group_id = ?");
    $stmt->execute([$relative_path, $group_id]);
    log_msg("Database updated");
} catch (PDOException $e) {
    log_msg("DB update error: " . $e->getMessage());
    // File was uploaded, but DB failed - still return partial success
}

// Get site URL
$site_url = 'https://worldmates.club/';
if (isset($GLOBALS['site_url'])) {
    $site_url = rtrim($GLOBALS['site_url'], '/') . '/';
}

$full_url = $site_url . $relative_path;

log_msg("SUCCESS: $full_url");
log_msg("========== REQUEST COMPLETED ==========");

echo json_encode([
    'api_status' => 200,
    'message' => 'Avatar uploaded successfully',
    'url' => $relative_path,
    'avatar_url' => $full_url
]);
exit;
