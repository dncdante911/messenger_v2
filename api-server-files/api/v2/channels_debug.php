<?php
/**
 * DEBUG VERSION OF channels.php
 * Тестує підключення до БД, завантаження функцій, тощо
 */

$debug_log = '/tmp/channels_debug.log';
file_put_contents($debug_log, "\n\n=== NEW REQUEST " . date('Y-m-d H:i:s') . " ===\n", FILE_APPEND);

function debug_log($message) {
    global $debug_log;
    file_put_contents($debug_log, "[" . date('H:i:s') . "] " . $message . "\n", FILE_APPEND);
}

debug_log("Step 1: Script started");

// Включаємо показ помилок
ini_set('display_errors', 1);
error_reporting(E_ALL);

debug_log("Step 2: Loading config.php");

try {
    $config_path = __DIR__ . '/config.php';
    debug_log("Step 2.1: config.php path: $config_path");

    if (!file_exists($config_path)) {
        debug_log("ERROR: config.php not found!");
        die(json_encode(['error' => 'config.php not found']));
    }

    debug_log("Step 2.2: config.php exists, requiring...");
    require_once($config_path);
    debug_log("Step 3: config.php loaded");
} catch (Exception $e) {
    debug_log("EXCEPTION in config.php: " . $e->getMessage());
    die(json_encode(['error' => 'Exception: ' . $e->getMessage()]));
} catch (Error $e) {
    debug_log("ERROR in config.php: " . $e->getMessage());
    die(json_encode(['error' => 'Fatal error: ' . $e->getMessage()]));
}

debug_log("Step 4: Checking variables...");
debug_log("Step 4.1: \$db (PDO) = " . (isset($db) ? 'SET' : 'NOT SET'));
debug_log("Step 4.2: \$sqlConnect (MySQLi) = " . (isset($sqlConnect) ? 'SET' : 'NOT SET'));
debug_log("Step 4.3: \$wo = " . (isset($wo) ? 'SET' : 'NOT SET'));

if (isset($db)) {
    try {
        $stmt = $pdo->query("SELECT 1");
        debug_log("Step 4.4: PDO connection WORKING");
    } catch (Exception $e) {
        debug_log("Step 4.4: PDO connection FAILED: " . $e->getMessage());
    }
}

if (isset($sqlConnect)) {
    $test = mysqli_query($sqlConnect, "SELECT 1");
    debug_log("Step 4.5: MySQLi connection " . ($test ? 'WORKING' : 'FAILED'));
}

debug_log("Step 5: Checking functions...");
debug_log("Step 5.1: Wo_ShareFile = " . (function_exists('Wo_ShareFile') ? 'EXISTS' : 'NOT EXISTS'));
debug_log("Step 5.2: Wo_Secure = " . (function_exists('Wo_Secure') ? 'EXISTS' : 'NOT EXISTS'));
debug_log("Step 5.3: Wo_GetConfig = " . (function_exists('Wo_GetConfig') ? 'EXISTS' : 'NOT EXISTS'));
debug_log("Step 5.4: validateAccessToken = " . (function_exists('validateAccessToken') ? 'EXISTS' : 'NOT EXISTS'));

debug_log("Step 6: Checking constants...");
debug_log("Step 6.1: DB_HOST = " . (defined('DB_HOST') ? DB_HOST : 'NOT DEFINED'));
debug_log("Step 6.2: DB_NAME = " . (defined('DB_NAME') ? DB_NAME : 'NOT DEFINED'));
debug_log("Step 6.3: T_USERS = " . (defined('T_USERS') ? T_USERS : 'NOT DEFINED'));

// Тест запиту до БД
debug_log("Step 7: Testing database query...");
try {
    if (isset($db)) {
        $stmt = $pdo->query("SELECT COUNT(*) as cnt FROM Wo_Users");
        $result = $stmt->fetch();
        debug_log("Step 7.1: Users in database: " . $result['cnt']);
    }
} catch (Exception $e) {
    debug_log("Step 7.1: Database query FAILED: " . $e->getMessage());
}

// Тест завантаження файлів
debug_log("Step 8: Testing file upload simulation...");
if (function_exists('Wo_ShareFile')) {
    debug_log("Step 8.1: Wo_ShareFile function exists");

    // Створимо тестовий файл
    $test_file = '/tmp/test_avatar.txt';
    file_put_contents($test_file, 'test');

    $file_info = [
        'file' => $test_file,
        'name' => 'test.txt',
        'size' => 4,
        'type' => 'text/plain',
        'types' => 'txt'
    ];

    try {
        // Не викликаємо реально, тільки перевіряємо чи функція існує
        debug_log("Step 8.2: Wo_ShareFile callable: " . (is_callable('Wo_ShareFile') ? 'YES' : 'NO'));
    } catch (Exception $e) {
        debug_log("Step 8.2: Error checking Wo_ShareFile: " . $e->getMessage());
    }

    unlink($test_file);
} else {
    debug_log("Step 8.1: Wo_ShareFile NOT EXISTS");
}

debug_log("Step 9: All checks completed");

// Повертаємо результати
echo json_encode([
    'api_status' => 200,
    'message' => 'Debug successful! Check /tmp/channels_debug.log for details',
    'checks' => [
        'config_loaded' => true,
        'db_pdo' => isset($db),
        'db_mysqli' => isset($sqlConnect),
        'wo_array' => isset($wo),
        'Wo_ShareFile' => function_exists('Wo_ShareFile'),
        'Wo_Secure' => function_exists('Wo_Secure'),
        'validateAccessToken' => function_exists('validateAccessToken'),
        'DB_HOST' => defined('DB_HOST') ? DB_HOST : null,
        'DB_NAME' => defined('DB_NAME') ? DB_NAME : null,
    ],
    'debug_log' => file_get_contents($debug_log)
]);

debug_log("Step 10: Response sent");
?>
