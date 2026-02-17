<?php
/**
 * Phone API Initialization File
 * Lightweight init for mobile API endpoints
 *
 * This file must be included at the top of all phone API files:
 * require_once(__DIR__ . '/api_init.php');
 */

// Prevent direct access
if (!defined('API_INIT_LOADED')) {
    define('API_INIT_LOADED', true);
} else {
    return; // Already loaded
}

// Error reporting for debugging (disable in production)
ini_set('display_errors', 0);
ini_set('log_errors', 1);
ini_set('error_log', __DIR__ . '/../../logs/php_errors.log');
error_reporting(E_ALL);

// Set timezone
date_default_timezone_set('Europe/Kyiv');

// Session configuration
@ini_set('session.cookie_httponly', 1);
@ini_set('session.use_only_cookies', 1);
if (session_status() === PHP_SESSION_NONE) {
    session_start();
}

// Load database configuration from site root
// Path: /config.php (кореневий файл WoWonder)
$config_path = $_SERVER['DOCUMENT_ROOT'] . '/config.php';
if (!file_exists($config_path)) {
    // Fallback: try relative path
    $config_path = __DIR__ . '/../../../config.php';
    if (!file_exists($config_path)) {
        http_response_code(500);
        die(json_encode([
            'api_status' => 500,
            'api_text' => 'failed',
            'errors' => ['error_text' => 'Server configuration error: config.php not found']
        ]));
    }
}
require_once($config_path);

// CRITICAL: Change working directory to site root
// WoWonder functions expect to be executed from root (they use require_once "config.php")
$site_root = $_SERVER['DOCUMENT_ROOT'];
if (getcwd() !== $site_root) {
    chdir($site_root);
}

// Verify database credentials are set
if (!isset($sql_db_host) || !isset($sql_db_user) || !isset($sql_db_pass) || !isset($sql_db_name)) {
    http_response_code(500);
    die(json_encode([
        'api_status' => 500,
        'api_text' => 'failed',
        'errors' => ['error_text' => 'Database credentials not configured']
    ]));
}

// Connect to database
$sqlConnect = @mysqli_connect($sql_db_host, $sql_db_user, $sql_db_pass, $sql_db_name);
if (!$sqlConnect || mysqli_connect_error()) {
    error_log("Database connection failed: " . mysqli_connect_error());
    http_response_code(500);
    die(json_encode([
        'api_status' => 500,
        'api_text' => 'failed',
        'errors' => ['error_text' => 'Database connection failed']
    ]));
}

// Set charset to UTF-8
mysqli_set_charset($sqlConnect, 'utf8mb4');
mysqli_query($sqlConnect, "SET NAMES utf8mb4");

// Initialize $wo global array
if (!isset($wo)) {
    $wo = [];
}
$wo['sqlConnect'] = $sqlConnect;
$wo['site_url'] = $site_url ?? 'https://worldmates.club';

// Load MySQL-Maria Database class (from site root after chdir)
// CRITICAL: Create $mysqlMaria object for Wo_Secure() and other functions
$mysql_maria_path = 'assets/libraries/DB/vendor/joshcam/mysqli-database-class/MySQL-Maria.php';
if (file_exists($mysql_maria_path)) {
    require_once($mysql_maria_path);
    // Create MysqlMaria object (required by Wo_Secure on line 819)
    if (!isset($mysqlMaria)) {
        $mysqlMaria = new Mysql;
    }
} else {
    error_log("MySQL-Maria.php not found at: $mysql_maria_path in " . getcwd());
}

// Load database tables constants (from site root)
$tabels_path = 'assets/includes/tabels.php';
if (!file_exists($tabels_path)) {
    http_response_code(500);
    die(json_encode([
        'api_status' => 500,
        'api_text' => 'failed',
        'errors' => ['error_text' => 'tabels.php not found at ' . getcwd() . '/' . $tabels_path]
    ]));
}
require_once($tabels_path);

// Load required functions (from site root)
$required_functions = [
    'assets/includes/cache.php',
    'assets/includes/functions_general.php',
    'assets/includes/functions_one.php',
    'assets/includes/functions_two.php',
    'assets/includes/functions_three.php'
];

foreach ($required_functions as $func_path) {
    if (!file_exists($func_path)) {
        error_log("Required file not found: $func_path in " . getcwd());
        http_response_code(500);
        die(json_encode([
            'api_status' => 500,
            'api_text' => 'failed',
            'errors' => ['error_text' => 'System files missing: ' . basename($func_path)]
        ]));
    }
    require_once($func_path);
}

// Load phone-specific functions (use absolute path since we changed directory)
$phone_functions_path = $site_root . '/api/phone/core/functions.php';
if (file_exists($phone_functions_path)) {
    require_once($phone_functions_path);
}

// Verify critical functions are loaded
if (!function_exists('Wo_Secure') || !function_exists('Wo_RegisterUser')) {
    error_log("Critical WoWonder functions not loaded");
    http_response_code(500);
    die(json_encode([
        'api_status' => 500,
        'api_text' => 'failed',
        'errors' => ['error_text' => 'System initialization failed']
    ]));
}

// Load site configuration from database
if (function_exists('Wo_GetConfig')) {
    $config = Wo_GetConfig();
    $wo['config'] = $config;
} else {
    error_log("Wo_GetConfig function not found");
}

// Set API version
if (!isset($api_version)) {
    $api_version = '1.3.1';
}

// API initialization complete
// All phone API files can now use: $sqlConnect, $wo, Wo_* functions
