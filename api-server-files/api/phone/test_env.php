<?php
/**
 * Test environment variables and paths
 */

header('Content-Type: application/json');

$info = [
    'timestamp' => date('Y-m-d H:i:s'),
    'php_version' => PHP_VERSION,
    'server' => [
        'DOCUMENT_ROOT' => $_SERVER['DOCUMENT_ROOT'] ?? 'NOT SET',
        'SCRIPT_FILENAME' => $_SERVER['SCRIPT_FILENAME'] ?? 'NOT SET',
        'REQUEST_URI' => $_SERVER['REQUEST_URI'] ?? 'NOT SET',
        'SERVER_NAME' => $_SERVER['SERVER_NAME'] ?? 'NOT SET',
    ],
    'paths' => [
        '__FILE__' => __FILE__,
        '__DIR__' => __DIR__,
        'getcwd()' => getcwd(),
    ],
    'config_search' => [],
];

// Спробуємо знайти config.php різними шляхами
$search_paths = [
    '$_SERVER[DOCUMENT_ROOT]/config.php' => $_SERVER['DOCUMENT_ROOT'] . '/config.php',
    '__DIR__/../../../config.php' => __DIR__ . '/../../../config.php',
    '/home/user/worldmates_mess_v1.0/config.php' => '/home/user/worldmates_mess_v1.0/config.php',
    'realpath(__DIR__/../../../config.php)' => realpath(__DIR__ . '/../../../config.php'),
];

foreach ($search_paths as $label => $path) {
    $info['config_search'][$label] = [
        'path' => $path,
        'exists' => file_exists($path),
        'readable' => file_exists($path) && is_readable($path),
    ];
}

// Перевіряємо api_init.php
$api_init = __DIR__ . '/api_init.php';
$info['api_init'] = [
    'path' => $api_init,
    'exists' => file_exists($api_init),
    'readable' => file_exists($api_init) && is_readable($api_init),
];

// Перевіряємо assets/includes
$assets_includes = __DIR__ . '/../../assets/includes';
$info['assets_includes'] = [
    'path' => $assets_includes,
    'exists' => file_exists($assets_includes),
    'is_dir' => file_exists($assets_includes) && is_dir($assets_includes),
];

if (file_exists($assets_includes) && is_dir($assets_includes)) {
    $files = scandir($assets_includes);
    $info['assets_includes']['files'] = array_filter($files, function($f) {
        return !in_array($f, ['.', '..']);
    });
}

echo json_encode($info, JSON_PRETTY_PRINT);
?>
