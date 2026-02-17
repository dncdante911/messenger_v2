<?php
// api/v2/check_messages_output.php
error_reporting(E_ALL);
ini_set('display_errors', 1);

// Подключаем БД
$mysqli = new mysqli('localhost', 'social', '3344Frzaq0607DmC157', 'socialhub');

// Получаем сообщение через Wo_GetMessages
require_once('/var/www/www-root/data/www/worldmates.club/assets/includes/functions_one.php');

$test_data = array('user_id' => 8); // ID пользователя
$messages = Wo_GetMessages($test_data, 1);

echo "<h3>Проверка вывода Wo_GetMessages</h3>";
if (!empty($messages[0])) {
    $msg = $messages[0];
    echo "ID: {$msg['id']}<br>";
    echo "Текст: " . htmlspecialchars($msg['text']) . "<br>";
    echo "Длина текста: " . strlen($msg['text']) . "<br>";
    echo "Has IV: " . (!empty($msg['iv']) ? 'YES' : 'NO') . "<br>";
    echo "Has Tag: " . (!empty($msg['tag']) ? 'YES' : 'NO') . "<br>";
    echo "Cipher Version: " . ($msg['cipher_version'] ?? '1') . "<br>";
    
    // Если это Base64 GCM
    if (strlen($msg['text']) == 24 && !empty($msg['iv']) && !empty($msg['tag'])) {
        echo "<br><strong style='color:red;'>⚠️ Wo_GetMessages вернул GCM без конвертации!</strong><br>";
        
        // Попробуем сконвертировать
        require_once('crypto_helper.php');
        $ecb = CryptoHelper::convertGCMtoECB($msg['text'], $msg['time'], $msg['iv'], $msg['tag']);
        if ($ecb) {
            echo "Конвертировано в ECB: " . $ecb . "<br>";
        }
    }
}