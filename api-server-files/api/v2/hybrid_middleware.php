<?php
// hybrid_middleware.php
class HybridMiddleware {
    
    // Определяем тип клиента
    public static function isWorldMatesRequest() {
        return !empty($_POST['use_gcm']) && $_POST['use_gcm'] == 'true' ||
               !empty($_GET['use_gcm']) && $_GET['use_gcm'] == 'true';
    }
    
    // Получаем правильный текст для клиента
    public static function getMessageTextForClient($message, $is_worldmates = null) {
        if ($is_worldmates === null) {
            $is_worldmates = self::isWorldMatesRequest();
        }
        
        if (is_object($message)) {
            $message = (array) $message;
        }
        
        // Для WorldMates: предпочитаем GCM, но если нет - ECB
        if ($is_worldmates) {
            if (!empty($message['iv']) && !empty($message['tag'])) {
                // Есть GCM поля - возвращаем GCM текст
                return isset($message['text']) ? $message['text'] : '';
            } else {
                // Нет GCM - возвращаем ECB если есть
                return !empty($message['text_ecb']) ? $message['text_ecb'] : 
                       (isset($message['text']) ? $message['text'] : '');
            }
        } 
        // Для WoWonder: всегда ECB
        else {
            if (!empty($message['text_ecb'])) {
                return $message['text_ecb'];
            }
            // Если нет ECB, но есть GCM - конвертируем
            elseif (!empty($message['iv']) && !empty($message['tag']) && 
                    class_exists('CryptoHelper')) {
                try {
                    $decrypted = CryptoHelper::decryptGCM(
                        $message['text'], 
                        $message['time'], 
                        $message['iv'], 
                        $message['tag']
                    );
                    if ($decrypted !== false) {
                        return openssl_encrypt($decrypted, "AES-128-ECB", $message['time']);
                    }
                } catch (Exception $e) {
                    // Ошибка конвертации
                }
            }
            // По умолчанию
            return isset($message['text']) ? $message['text'] : '';
        }
    }
    
    // Обработка массива сообщений
    public static function processMessages(&$messages, $is_worldmates = null) {
        if ($is_worldmates === null) {
            $is_worldmates = self::isWorldMatesRequest();
        }
        
        if (!is_array($messages)) return;
        
        foreach ($messages as &$message) {
            $message['text'] = self::getMessageTextForClient($message, $is_worldmates);
            
            // Очищаем лишние поля для WoWonder
            if (!$is_worldmates) {
                unset($message['iv'], $message['tag'], $message['cipher_version'], $message['text_ecb']);
            }
        }
    }
    
    // Получить превью текста
    public static function getMessagePreview($message) {
        if (is_object($message)) {
            $message = (array) $message;
        }
        
        if (!empty($message['text_preview'])) {
            return $message['text_preview'];
        }
        
        // Пытаемся расшифровать для превью
        if (!empty($message['iv']) && !empty($message['tag']) && 
            class_exists('CryptoHelper')) {
            try {
                $decrypted = CryptoHelper::decryptGCM(
                    $message['text'], 
                    $message['time'], 
                    $message['iv'], 
                    $message['tag']
                );
                if ($decrypted !== false) {
                    return mb_substr($decrypted, 0, 100, 'UTF-8');
                }
            } catch (Exception $e) {
                // Не удалось расшифровать
            }
        }
        
        return '';
    }
}