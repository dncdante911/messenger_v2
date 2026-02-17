<?php
/**
 * Browser Compatibility Middleware
 * Конвертирует GCM-шифрованные сообщения в ECB для совместимости с браузером WoWonder
 */

class BrowserCompatibility {
    
    /**
     * Конвертирует GCM-шифрованное сообщение в ECB для браузера
     * 
     * @param array $message Данные сообщения
     * @return array Обработанное сообщение
     */
    public static function convertForBrowser(&$message) {
        // Если это не сообщение или нет текста, ничего не делаем
        if (empty($message) || !is_array($message) || !isset($message['text'])) {
            return $message;
        }
        
        global $db;
        
        // Проверяем, есть ли GCM данные
        $has_gcm = !empty($message['iv']) && !empty($message['tag']) && 
                   isset($message['cipher_version']) && $message['cipher_version'] == 2;
        
        $has_ecb = !empty($message['text_ecb']);
        
        // Если есть GCM, но нет ECB - конвертируем
        if ($has_gcm && !$has_ecb) {
            try {
                // Подключаем crypto_helper если нужно
                if (!class_exists('CryptoHelper') && file_exists(__DIR__ . '/crypto_helper.php')) {
                    require_once(__DIR__ . '/crypto_helper.php');
                }
                
                if (class_exists('CryptoHelper')) {
                    // Расшифровываем GCM
                    $decrypted = CryptoHelper::decryptGCM(
                        $message['text'],
                        $message['time'],
                        $message['iv'],
                        $message['tag']
                    );
                    
                    if ($decrypted !== false) {
                        // Шифруем в ECB для браузера
                        $ecb_text = openssl_encrypt($decrypted, "AES-128-ECB", $message['time']);
                        $message['text'] = $ecb_text;
                        
                        // Сохраняем ECB в БД для будущих запросов
                        if (!empty($message['id']) && $db) {
                            $db->where('id', $message['id'])->update(T_MESSAGES, array(
                                'text_ecb' => $ecb_text
                            ));
                        }
                    }
                }
            } catch (Exception $e) {
                // Логируем ошибку, но не прерываем выполнение
                error_log("BrowserCompatibility: Error converting GCM→ECB: " . $e->getMessage());
            }
        }
        
        // Если уже есть ECB версия, используем её
        if ($has_ecb) {
            $message['text'] = $message['text_ecb'];
        }
        
        // Удаляем GCM поля для браузера
        unset($message['iv'], $message['tag'], $message['cipher_version'], $message['text_ecb']);
        
        return $message;
    }
    
    /**
     * Обрабатывает массив сообщений для браузера
     */
    public static function processMessagesForBrowser(&$messages) {
        if (!is_array($messages)) return;
        
        foreach ($messages as &$message) {
            self::convertForBrowser($message);
        }
    }
    
    /**
     * Определяет, является ли запрос от браузера WoWonder
     */
    public static function isBrowserRequest() {
        // Браузер WoWonder НЕ отправляет use_gcm
        if (isset($_POST['use_gcm']) && $_POST['use_gcm'] == 'true') {
            return false; // Это WorldMates
        }
        
        if (isset($_GET['use_gcm']) && $_GET['use_gcm'] == 'true') {
            return false; // Это WorldMates
        }
        
        // Проверяем User-Agent
        if (isset($_SERVER['HTTP_USER_AGENT'])) {
            $ua = strtolower($_SERVER['HTTP_USER_AGENT']);
            
            // Это браузер, но НЕ WorldMates приложение
            if (strpos($ua, 'worldmates') === false && 
                strpos($ua, 'okhttp') === false &&
                (strpos($ua, 'mozilla') !== false || 
                 strpos($ua, 'chrome') !== false ||
                 strpos($ua, 'safari') !== false)) {
                return true;
            }
        }
        
        // По умолчанию считаем, что это браузер (для обратной совместимости)
        return true;
    }
}