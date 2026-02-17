<?php
/**
 * Конфігурація підключення до бази даних
 * для group_chat_v2.php та channels.php API
 *
 * Завантажує кореневий config.php та додає PDO підключення
 */

// Load root config.php (contains $sql_db_host, $sql_db_user, $sql_db_pass, $sql_db_name, $site_url)
$root_config = $_SERVER['DOCUMENT_ROOT'] . '/config.php';
if (!file_exists($root_config)) {
    // Fallback: try relative path
    $root_config = __DIR__ . '/../../config.php';
    if (!file_exists($root_config)) {
        http_response_code(500);
        die(json_encode(['api_status' => 500, 'error_message' => 'config.php not found']));
    }
}
require_once($root_config);

// Use variables from root config.php
define('DB_HOST', $sql_db_host);
define('DB_NAME', $sql_db_name);
define('DB_USER', $sql_db_user);
define('DB_PASS', $sql_db_pass);
define('DB_CHARSET', 'utf8mb4');

// Налаштування PDO
define('PDO_OPTIONS', [
    PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES   => false,
]);

// Шлях до лог-файлу
define('LOG_FILE', '/var/www/www-root/data/www/worldmates.club/api/v2/logs/group_chat_v2.log');

// Timezone
date_default_timezone_set('Europe/Kyiv');

// ============================================
// PDO підключення (для channels.php та інших API v2)
// ============================================
try {
    $dsn = "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=" . DB_CHARSET;
    $db = new PDO($dsn, DB_USER, DB_PASS, PDO_OPTIONS);
} catch (PDOException $e) {
    error_log("Database connection failed: " . $e->getMessage());
    http_response_code(500);
    echo json_encode([
        'api_status' => 500,
        'error_message' => 'Database connection failed'
    ]);
    exit;
}

// ============================================
// MySQLi підключення (для WoWonder функцій типу Wo_ShareFile)
// ============================================
$sqlConnect = @mysqli_connect(DB_HOST, DB_USER, DB_PASS, DB_NAME);
if (!$sqlConnect || mysqli_connect_error()) {
    error_log("MySQLi connection failed: " . mysqli_connect_error());
    http_response_code(500);
    echo json_encode([
        'api_status' => 500,
        'error_message' => 'Database connection failed'
    ]);
    exit;
}
mysqli_set_charset($sqlConnect, 'utf8mb4');
mysqli_query($sqlConnect, "SET NAMES utf8mb4");

// Initialize $wo global array for WoWonder compatibility
if (!isset($wo)) {
    $wo = [];
}
$wo['sqlConnect'] = $sqlConnect;
$wo['site_url'] = $site_url; // From root config.php

// CRITICAL: Set $wo['loggedin'] for API requests
// WoWonder functions like Wo_ShareFile() check this (line 5714 in functions_one.php)
// For API endpoints, we consider user "logged in" if they have valid access_token
// The validateAccessToken() function will be called before any protected endpoints
$wo['loggedin'] = true;

// Set minimal user data for API context (will be updated per request)
$wo['user'] = [
    'user_id' => 0,
    'username' => 'api_user',
    'active' => '1',
    'admin' => 0  // Required by Wo_IsAdmin() which is called by Wo_ShareFile()
];

// ============================================
// Initialize MysqlMaria object (CRITICAL for Wo_Secure())
// ============================================
// WoWonder functions use global $mysqlMaria object
// Wo_Secure() calls $mysqlMaria->setSQLType($sqlConnect) on line 819
if (!isset($mysqlMaria)) {
    // Load MysqlDb class first
    $mysqldb_path = __DIR__ . '/../../assets/libraries/DB/vendor/joshcam/mysqli-database-class/MySQL-Maria.php';
    if (file_exists($mysqldb_path)) {
        require_once($mysqldb_path);
        $mysqlMaria = new Mysql;
    } else {
        error_log("MySQL-Maria.php not found at: $mysqldb_path");
        // Create minimal stub to prevent fatal errors
        $mysqlMaria = new stdClass();
        $mysqlMaria->setSQLType = function($conn) {};
    }
}

// ============================================
// Load WoWonder functions for avatar upload and other features
// ============================================
$assets_path = __DIR__ . '/../../assets/includes/';

// Load database tables constants (T_USERS, T_POSTS, etc.)
if (file_exists($assets_path . 'tabels.php')) {
    require_once($assets_path . 'tabels.php');
}

// Load required WoWonder functions
$required_functions = [
    'cache.php',           // Cache functions
    'functions_general.php', // Wo_Secure, Wo_GetConfig, etc.
    'functions_one.php',   // Wo_ShareFile, Wo_RegisterUser, etc.
];

foreach ($required_functions as $func_file) {
    $func_path = $assets_path . $func_file;
    if (file_exists($func_path)) {
        require_once($func_path);
    } else {
        error_log("Warning: $func_file not found at $func_path");
    }
}

// Load site configuration from database if possible
if (function_exists('Wo_GetConfig')) {
    try {
        $config = Wo_GetConfig();
        $wo['config'] = $config;
    } catch (Exception $e) {
        error_log("Warning: Failed to load Wo_GetConfig: " . $e->getMessage());
        $wo['config'] = [];
    }
}

// ============================================
// AUTHENTICATION FUNCTIONS (для channels.php)
// ============================================

/**
 * Validate access token and return user_id
 *
 * @param PDO $db Database connection
 * @param string $access_token Access token from request
 * @return int|false User ID if valid, false otherwise
 */
function validateAccessToken($db, $access_token) {
    if (empty($access_token)) {
        return false;
    }

    try {
        // Query Wo_AppsSessions table for access token
        $stmt = $db->prepare("
            SELECT user_id, time
            FROM Wo_AppsSessions
            WHERE session_id = :access_token
            LIMIT 1
        ");
        $stmt->execute(['access_token' => $access_token]);
        $session = $stmt->fetch();

        if (!$session) {
            return false;
        }

        // Optional: Check if session is still valid (not expired)
        // Uncomment if you want to enforce session expiration
        /*
        $session_lifetime = 30 * 24 * 60 * 60; // 30 days in seconds
        if (time() - $session['time'] > $session_lifetime) {
            return false; // Session expired
        }
        */

        return (int)$session['user_id'];
    } catch (PDOException $e) {
        error_log("validateAccessToken error: " . $e->getMessage());
        return false;
    }
}

/**
 * Get user data by user_id
 *
 * @param PDO $db Database connection
 * @param int $user_id User ID
 * @return array|false User data array if found, false otherwise
 */
function getUserById($db, $user_id) {
    if (empty($user_id)) {
        return false;
    }

    try {
        $stmt = $db->prepare("
            SELECT user_id, username, name, avatar, verified, lastseen, lastseen_status
            FROM Wo_Users
            WHERE user_id = :user_id
            LIMIT 1
        ");
        $stmt->execute(['user_id' => $user_id]);
        return $stmt->fetch();
    } catch (PDOException $e) {
        error_log("getUserById error: " . $e->getMessage());
        return false;
    }
}

/**
 * Secure input data (prevent SQL injection)
 * Note: This is a legacy function. Use PDO prepared statements instead.
 *
 * @param string $data Input data to secure
 * @return string Secured data
 */
function secureInput($data) {
    return htmlspecialchars(strip_tags(trim($data)), ENT_QUOTES, 'UTF-8');
}

/**
 * Log message to file
 *
 * @param string $message Message to log
 * @param string $level Log level (INFO, ERROR, WARNING)
 */
if (!function_exists('logMessage')) {
    function logMessage($message, $level = 'INFO') {
        if (defined('LOG_FILE')) {
            $timestamp = date('Y-m-d H:i:s');
            $log_entry = "[{$timestamp}] [{$level}] {$message}\n";
            file_put_contents(LOG_FILE, $log_entry, FILE_APPEND);
        }
    }
}

/**
 * Send JSON error response
 * NOTE: channels.php uses ($message, $code), group_chat_v2.php uses ($code, $message)
 *
 * @param string $message Error message
 * @param int $code HTTP status code
 */
if (!function_exists('sendError')) {
    function sendError($message, $code = 400) {
        http_response_code($code);
        echo json_encode([
            'api_status' => $code,
            'error_message' => $message
        ]);
        exit;
    }
}

/**
 * Send JSON success response
 *
 * @param array $data Response data
 */
if (!function_exists('sendSuccess')) {
    function sendSuccess($data) {
        echo json_encode(array_merge(['api_status' => 200], $data));
        exit;
    }
}
