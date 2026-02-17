<?php
/**
 * Test endpoint to debug API initialization
 * URL: /api/v2/?type=test_init
 */

$debug = [];
$debug['step'] = 'start';
$debug['router'] = 'index.php';  // Confirms we're going through our router

try {
    // Check database connections
    $debug['db_pdo_isset'] = isset($db);
    $debug['sqlConnect_isset'] = isset($sqlConnect);

    // Check if $wo is set
    $debug['wo_isset'] = isset($wo);
    $debug['wo_loggedin'] = $wo['loggedin'] ?? false;
    $debug['wo_user_isset'] = isset($wo['user']);

    if (isset($wo['user'])) {
        $debug['user_id'] = $wo['user']['user_id'] ?? 'not set';
        $debug['username'] = $wo['user']['username'] ?? 'not set';
    }

    // Check if functions exist
    $debug['functions'] = [
        'validateAccessToken' => function_exists('validateAccessToken'),
        'Wo_Secure' => function_exists('Wo_Secure'),
        'Wo_UserData' => function_exists('Wo_UserData'),
        'Wo_ShareFile' => function_exists('Wo_ShareFile'),
        'Wo_GetMedia' => function_exists('Wo_GetMedia'),
    ];

    // Check if table constants exist
    $debug['tables'] = [
        'T_APP_SESSIONS' => defined('T_APP_SESSIONS'),
        'T_USERS' => defined('T_USERS'),
        'T_GROUPCHAT' => defined('T_GROUPCHAT'),
    ];

    // Check site URL
    $debug['site_url'] = $wo['site_url'] ?? 'not set';

    $debug['step'] = 'complete';
    $debug['api_status'] = 200;
    $debug['message'] = 'API v2 initialized successfully via index.php router';

} catch (Exception $e) {
    $debug['error'] = $e->getMessage();
    $debug['api_status'] = 500;
}

echo json_encode($debug, JSON_PRETTY_PRINT);
exit();
?>
