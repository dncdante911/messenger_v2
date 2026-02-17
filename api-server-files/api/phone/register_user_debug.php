<?php
/**
 * DEBUG VERSION OF register_user.php
 * Додає логування на кожному кроці щоб знайти де падає
 */

// Ініціалізація логування
$debug_log = '/tmp/register_debug.log';
file_put_contents($debug_log, "\n\n=== NEW REQUEST " . date('Y-m-d H:i:s') . " ===\n", FILE_APPEND);

function debug_log($message) {
    global $debug_log;
    file_put_contents($debug_log, "[" . date('H:i:s') . "] " . $message . "\n", FILE_APPEND);
}

debug_log("Step 1: Script started");

// Включаємо показ помилок
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

debug_log("Step 2: Error reporting enabled");

// Спробуємо завантажити api_init.php
try {
    debug_log("Step 3: Loading api_init.php");
    $api_init_path = __DIR__ . '/api_init.php';
    debug_log("Step 3.1: api_init.php path: $api_init_path");

    if (!file_exists($api_init_path)) {
        debug_log("ERROR: api_init.php not found!");
        die(json_encode(['error' => 'api_init.php not found']));
    }

    debug_log("Step 3.2: api_init.php exists, requiring...");
    require_once($api_init_path);
    debug_log("Step 4: api_init.php loaded successfully");
} catch (Exception $e) {
    debug_log("EXCEPTION in api_init.php: " . $e->getMessage());
    debug_log("Stack trace: " . $e->getTraceAsString());
    die(json_encode(['error' => 'Exception: ' . $e->getMessage()]));
} catch (Error $e) {
    debug_log("ERROR in api_init.php: " . $e->getMessage());
    debug_log("Stack trace: " . $e->getTraceAsString());
    die(json_encode(['error' => 'Fatal error: ' . $e->getMessage()]));
}

debug_log("Step 5: Checking variables...");
debug_log("Step 5.1: \$sqlConnect = " . (isset($sqlConnect) ? 'SET' : 'NOT SET'));
debug_log("Step 5.2: \$wo = " . (isset($wo) ? 'SET' : 'NOT SET'));
debug_log("Step 5.3: \$api_version = " . (isset($api_version) ? $api_version : 'NOT SET'));
debug_log("Step 5.4: Wo_Secure function = " . (function_exists('Wo_Secure') ? 'EXISTS' : 'NOT EXISTS'));
debug_log("Step 5.5: Wo_RegisterUser function = " . (function_exists('Wo_RegisterUser') ? 'EXISTS' : 'NOT EXISTS'));

debug_log("Step 6: Checking GET parameters");
debug_log("Step 6.1: GET params: " . json_encode($_GET));
debug_log("Step 6.2: POST params: " . json_encode(array_keys($_POST)));

// Перевіряємо type
if (empty($_GET['type'])) {
    debug_log("ERROR: type parameter is missing");
    echo json_encode([
        'api_status' => 400,
        'error' => 'type parameter is missing',
        'debug_log' => file_get_contents($debug_log)
    ]);
    exit;
}

$type = $_GET['type'];
debug_log("Step 7: type = $type");

if ($type != 'user_registration') {
    debug_log("ERROR: Invalid type: $type");
    echo json_encode([
        'api_status' => 400,
        'error' => 'Invalid type',
        'debug_log' => file_get_contents($debug_log)
    ]);
    exit;
}

debug_log("Step 8: Checking POST data");
$required_fields = ['username', 'email', 'password', 'confirm_password', 's'];
foreach ($required_fields as $field) {
    $isset = isset($_POST[$field]) ? 'YES' : 'NO';
    debug_log("Step 8.$field: $isset");
}

// Якщо дійшли сюди - все ОК
debug_log("Step 9: All checks passed! Returning success");

echo json_encode([
    'api_status' => 200,
    'message' => 'Debug successful! Check /tmp/register_debug.log for details',
    'checks' => [
        'api_init_loaded' => true,
        'sqlConnect' => isset($sqlConnect),
        'wo' => isset($wo),
        'api_version' => $api_version ?? 'not set',
        'Wo_Secure' => function_exists('Wo_Secure'),
        'Wo_RegisterUser' => function_exists('Wo_RegisterUser'),
    ],
    'debug_log' => file_get_contents($debug_log)
]);

debug_log("Step 10: Response sent");
?>
