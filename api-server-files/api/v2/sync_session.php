<?php
/**
 * Синхронізація токена сесії
 * Створює або оновлює запис в wo_appssessions
 */

require_once('./config.php');

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');

function logMessage($message) {
    $log_file = '/var/www/www-root/data/www/worldmates.club/api/v2/logs/sync_session.log';
    $timestamp = date('Y-m-d H:i:s');
    @file_put_contents($log_file, "[$timestamp] $message\n", FILE_APPEND);
}

function sendResponse($data) {
    echo json_encode($data, JSON_UNESCAPED_UNICODE);
    exit();
}

function sendError($code, $message) {
    sendResponse(array(
        'api_status' => $code,
        'error_code' => $code,
        'error_message' => $message
    ));
}

logMessage("=== SYNC SESSION REQUEST ===");
logMessage("Method: {$_SERVER['REQUEST_METHOD']}");

// Отримуємо параметри
$access_token = isset($_GET['access_token']) ? $_GET['access_token'] : '';
$user_id = isset($_POST['user_id']) ? intval($_POST['user_id']) : 0;
$platform = isset($_POST['platform']) ? $_POST['platform'] : 'phone';

if (empty($access_token)) {
    logMessage("ERROR: access_token missing");
    sendError(400, 'access_token is required');
}

if (empty($user_id)) {
    logMessage("ERROR: user_id missing");
    sendError(400, 'user_id is required');
}

logMessage("Access token: " . substr($access_token, 0, 20) . "...");
logMessage("User ID: $user_id");
logMessage("Platform: $platform");

// Підключення до БД
try {
    $db = new PDO(
        "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=utf8mb4",
        DB_USER,
        DB_PASS,
        array(
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false
        )
    );
    logMessage("DB connected successfully");
} catch (PDOException $e) {
    logMessage("DB ERROR: " . $e->getMessage());
    sendError(500, 'Database connection failed');
}

try {
    // Перевіряємо чи користувач існує та активний
    $stmt = $db->prepare("SELECT user_id, username FROM Wo_Users WHERE user_id = ? AND active = '1' LIMIT 1");
    $stmt->execute([$user_id]);
    $user = $stmt->fetch();

    if (!$user) {
        logMessage("ERROR: User not found or inactive: user_id=$user_id");
        sendError(404, 'User not found or inactive');
    }

    logMessage("User found: {$user['username']}");

    // Перевіряємо чи вже існує сесія з таким токеном
    $stmt = $db->prepare("SELECT id FROM wo_appssessions WHERE session_id = ? LIMIT 1");
    $stmt->execute([$access_token]);
    $existing = $stmt->fetch();

    if ($existing) {
        logMessage("Session already exists: ID=" . $existing['id']);
        sendResponse(array(
            'api_status' => 200,
            'message' => 'Session already exists',
            'user_id' => $user_id,
            'session_id' => $existing['id']
        ));
    }

    // Створюємо новий запис в wo_appssessions
    $time = time();
    $stmt = $db->prepare("
        INSERT INTO wo_appssessions (user_id, session_id, platform, time)
        VALUES (?, ?, ?, ?)
    ");
    $stmt->execute([$user_id, $access_token, $platform, $time]);
    $session_id = $db->lastInsertId();

    logMessage("SUCCESS: Session created: ID=$session_id, user_id=$user_id, platform=$platform");

    sendResponse(array(
        'api_status' => 200,
        'message' => 'Session synchronized successfully',
        'user_id' => $user_id,
        'session_id' => $session_id,
        'platform' => $platform
    ));

} catch (PDOException $e) {
    logMessage("ERROR: " . $e->getMessage());
    sendError(500, 'Failed to sync session: ' . $e->getMessage());
}