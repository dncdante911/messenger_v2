<?php

// ВКЛЮЧАЕМ ЛОГИРОВАНИЕ ДЛЯ ОТЛАДКИ
error_reporting(E_ALL);
ini_set('display_errors', 1);
ini_set('log_errors', 1);
ini_set('error_log', __DIR__ . '/send-message-debug.log');

error_log("=== send-message.php START ===");

// Підключаємо модуль шифрування AES-256-GCM
if (file_exists(__DIR__ . '/../crypto_helper.php')) {
    require_once(__DIR__ . '/../crypto_helper.php');
    error_log("crypto_helper.php loaded");
} else {
    error_log("crypto_helper.php NOT FOUND");
}

$response_data = array(
    'api_status' => 400
);

$required_fields = array(
    'user_id',
    'message_hash_id'
);

if (empty($_POST['product_id'])) {
    if (empty($_POST['text']) && $_POST['text'] != '0' && empty($_POST['lat']) && empty($_POST['lng'])) {   
        if (empty($_FILES['file']['name']) && empty($_POST['image_url']) && empty($_POST['gif']) && empty($_POST['audio']) && empty($_POST['video'])) {
            $error_code    = 3;
            $error_message = 'file (STREAM FILE) AND text (POST) AND image_url AND gif (POST) are missing, at least one is required';
        }
    }
}

foreach ($required_fields as $key => $value) {
    if (empty($_POST[$value]) && empty($error_code)) {
        $error_code    = 4;
        $error_message = $value . ' (POST) is missing';
    }
}

// Определяем тип клиента
$use_gcm = !empty($_POST['use_gcm']) && $_POST['use_gcm'] == 'true';
$is_worldmates = $use_gcm;
$non_allowed = array('password', 'user_pass', 'email', 'phone', 'verified', 'user_birthday');

if (empty($error_code)) {
    $recipient_id   = Wo_Secure($_POST['user_id']);
    $recipient_data = Wo_UserData($recipient_id);
    if (empty($recipient_data)) {
        $error_code    = 6;
        $error_message = 'Recipient user not found';
    } else {
        $plaintext = ''; // Определяем переменную здесь
        
        if (empty($_POST['product_id'])) {
            $mediaFilename = '';
            $mediaName     = '';
            if (!empty($_FILES['file']['name'])) {
                $fileInfo      = array(
                    'file' => $_FILES["file"]["tmp_name"],
                    'name' => $_FILES['file']['name'],
                    'size' => $_FILES["file"]["size"],
                    'type' => $_FILES["file"]["type"],
                    'types' => 'jpg,png,jpeg,gif,mp4,m4v,webm,flv,mov,mpeg,mp3,wav'
                );
                $media         = Wo_ShareFile($fileInfo);
                $mediaFilename = $media['filename'];
                $mediaName     = $media['name'];
            }
            else if (!empty($_POST['image_url'])) {
                $url     = $_POST['image_url'];
                $query   = parse_url($url);
                if (!empty($query['scheme']) && !empty($query['host'])) {
                    $fileInfo      = array(
                        'file' => $url,
                        'name' => $url,
                        'size' => 0,
                        'type' => 'jpg',
                        'types' => 'jpg,png,jpeg,gif'
                    );
                    $media         = Wo_ShareFile($fileInfo);
                    $mediaFilename = $media['filename'];
                    $mediaName     = $media['name'];
                }
            }
            else if (!empty($_POST['audio'])) {
                // Голосовое / аудио сообщение — URL уже загружен через upload_audio.php
                $audio_url     = Wo_Secure($_POST['audio']);
                $mediaFilename = $audio_url;
                $mediaName     = basename(parse_url($audio_url, PHP_URL_PATH));
            }
            else if (!empty($_POST['video'])) {
                // Видео-сообщение — URL уже загружен через upload_video.php
                $video_url     = Wo_Secure($_POST['video']);
                $mediaFilename = $video_url;
                $mediaName     = basename(parse_url($video_url, PHP_URL_PATH));
            }
            $gif = '';
            if (!empty($_POST['gif'])) {
                $gif = Wo_Secure($_POST['gif']);
            }
            $lng = '';
            $lat = '';
            if (!empty($_POST['lat']) && !empty($_POST['lng'])) {
                $lng = Wo_Secure($_POST['lng']);
                $lat = Wo_Secure($_POST['lat']);
            }

            $message_data = array(
                'from_id' => Wo_Secure($wo['user']['user_id']),
                'to_id' => Wo_Secure($recipient_id),
                'media' => Wo_Secure($mediaFilename),
                'mediaFileName' => Wo_Secure($mediaName),
                'time' => time(),
                'type_two' => (!empty($_POST['contact'])) ? 'contact' : '',
                'text' => '',
                'stickers' => $gif,
                'lng' => $lng,
                'lat' => $lat,
            );

            if (!empty($_POST['text']) || (isset($_POST['text']) && $_POST['text'] === '0') ) {
                $plaintext = Wo_Secure($_POST['text']);
                $preview_text = mb_substr($plaintext, 0, 100, 'UTF-8');
                
                // Простое шифрование без сложной логики
                if ($is_worldmates && class_exists('CryptoHelper')) {
                    // WorldMates: GCM
                    $encrypted = CryptoHelper::encryptGCM($plaintext, $message_data['time']);
                    if ($encrypted !== false) {
                        $message_data['text'] = $encrypted['text'];
                        $gcm_iv = $encrypted['iv'];
                        $gcm_tag = $encrypted['tag'];
                        $gcm_version = $encrypted['cipher_version'];
                        
                        // Сохраняем ECB для совместимости
                        $text_ecb = openssl_encrypt($plaintext, "AES-128-ECB", $message_data['time']);
                    } else {
                        $message_data['text'] = openssl_encrypt($plaintext, "AES-128-ECB", $message_data['time']);
                        $text_ecb = $message_data['text'];
                        $gcm_iv = $gcm_tag = $gcm_version = null;
                    }
                } else {
                    // WoWonder: ECB
                    $message_data['text'] = openssl_encrypt($plaintext, "AES-128-ECB", $message_data['time']);
                    $text_ecb = $message_data['text'];
                    $gcm_iv = $gcm_tag = $gcm_version = null;
                }
            }
            else{
                if (empty($lng) && empty($lat) && empty($_FILES['file']['name']) && empty($_POST['image_url']) && empty($_POST['gif']) && empty($_POST['audio']) && empty($_POST['video'])) {
                    $error_code    = 5;
                    $error_message = 'Please check your details.';
                }
            }
            if (!empty($_POST['message_type'])) {
                $message_data['type_two'] = Wo_Secure($_POST['message_type']);
            }
            // Авто-тип для медиа когда message_type не передан
            if (empty($message_data['type_two'])) {
                if (!empty($_POST['audio'])) {
                    $message_data['type_two'] = 'audio';
                } elseif (!empty($_POST['video'])) {
                    $message_data['type_two'] = 'video';
                }
            }

            if (empty($error_message)) {
                $last_id = Wo_RegisterMessage($message_data);

                // Сохраняем дополнительные поля
                if (!empty($last_id)) {
                    $update_fields = array();
                    
                    if ($gcm_iv !== null) {
                        $update_fields['iv'] = $gcm_iv;
                        $update_fields['tag'] = $gcm_tag;
                        $update_fields['cipher_version'] = $gcm_version;
                    }
                    
                    if (!empty($text_ecb)) {
                        $update_fields['text_ecb'] = $text_ecb;
                    }
                    
                        // Для WorldMates сохраняем GCM
                        if ($is_worldmates && !empty($gcm_iv) && !empty($gcm_tag)) {
                            $update_fields['iv'] = $gcm_iv;
                            $update_fields['tag'] = $gcm_tag;
                            $update_fields['cipher_version'] = $gcm_version;
                          }
                    
                    if (!empty($preview_text)) {
                        $update_fields['text_preview'] = $preview_text;
                    }
                    
                    if (!empty($update_fields)) {
                        $db->where('id', $last_id)->update(T_MESSAGES, $update_fields);
                    }
                }
            }
        }
        else{
            $last_id = Wo_RegisterMessage(array(
                'from_id' => Wo_Secure($wo['user']['user_id']),
                'to_id' => $recipient_id,
                'time' => time(),
                'stickers' => '',
                'product_id' => Wo_Secure($_POST['product_id'])
            ));
        }
        
        if (!empty($last_id)) {
            if (!empty($_POST['reply_id']) && is_numeric($_POST['reply_id']) && $_POST['reply_id'] > 0) {
                $reply_id = Wo_Secure($_POST['reply_id']);
                $db->where('id',$last_id)->update(T_MESSAGES,array('reply_id' => $reply_id));
            }
            if (!empty($_POST['story_id']) && is_numeric($_POST['story_id']) && $_POST['story_id'] > 0) {
                $story_id = Wo_Secure($_POST['story_id']);
                $db->where('id',$last_id)->update(T_MESSAGES,array('story_id' => $story_id));
            }

            // Notifier только для WoWonder
            if (!$is_worldmates) {
                try {
                    Wo_SendMessageNotifier($last_id);
                } catch (Exception $e) {
                    // Игнорируем ошибки notifier
                }
            }

            // Получаем данные сообщения
            $message_info = array(
                'user_id' => $recipient_id,
                'message_id' => $last_id
            );
            $message_info_result = Wo_GetMessages($message_info);
            
            if (!empty($message_info_result)) {
                if (is_object($message_info_result)) {
                    $message = (array) $message_info_result;
                } else if (isset($message_info_result[0])) {
                    $message = (array) $message_info_result[0];
                } else {
                    $error_code = 7;
                    $error_message = 'Message not found';
                }
                
                if (empty($error_code)) {
                    // Получаем дополнительные поля
                    $extra_fields = $db->where('id', $last_id)->getOne(T_MESSAGES, 'iv, tag, cipher_version, text_ecb, text_preview');
                    if ($extra_fields) {
                        $extra_fields = (array) $extra_fields;
                        $message = array_merge($message, $extra_fields);
                    }
                    
                   // УЛУЧШЕННАЯ ГИБРИДНАЯ ЛОГИКА
error_log("send-message: Hybrid logic - is_worldmates=" . ($is_worldmates ? 'true' : 'false'));

// Убедимся, что у нас есть все нужные поля для сообщения
if (empty($message['text'])) {
    $message['text'] = '';
}

// Получаем дополнительные поля из БД
$message_id = $last_id;
$db_fields = $db->where('id', $message_id)->getOne(T_MESSAGES, 'text, iv, tag, cipher_version, text_ecb, text_preview');
if ($db_fields) {
    $db_fields = (array) $db_fields;
    
    // Объединяем с данными сообщения
    $message = array_merge($message, $db_fields);
}

// Имеем ли мы GCM данные?
$has_gcm = !empty($message['iv']) && !empty($message['tag']) && !empty($message['cipher_version']) && $message['cipher_version'] == CryptoHelper::CIPHER_VERSION_GCM;
$has_ecb = !empty($message['text_ecb']);
$has_text = !empty($message['text']);

error_log("send-message: Message analysis - has_gcm=$has_gcm, has_ecb=$has_ecb, has_text=$has_text");

// 🎯 КЛЮЧЕВОЙ ФИКС: ПРАВИЛЬНАЯ ГИБРИДНАЯ ЛОГИКА
if ($is_worldmates) {
    // ЗАПРОС ОТ WORLDMATES (ваше приложение)
    // Возвращаем GCM формат если он есть
    
    if ($has_gcm) {
        // 1. Сообщение уже в GCM формате - возвращаем как есть
        // $message['text'] уже содержит GCM-шифрованный текст
        // $message['iv'], $message['tag'], $message['cipher_version'] уже есть
        error_log("send-message: Returning GCM for WorldMates");
    } 
    elseif ($has_ecb && class_exists('CryptoHelper')) {
        // 2. Есть только ECB (старое сообщение) → конвертируем ECB→GCM для приложения
        try {
            // Расшифровываем ECB
            $decrypted = openssl_decrypt($message['text_ecb'], "AES-128-ECB", $message['time']);
            
            if ($decrypted !== false) {
                // Шифруем в GCM для WorldMates
                $encrypted_gcm = CryptoHelper::encryptGCM($decrypted, $message['time']);
                
                if ($encrypted_gcm !== false) {
                    $message['text'] = $encrypted_gcm['text'];
                    $message['iv'] = $encrypted_gcm['iv'];
                    $message['tag'] = $encrypted_gcm['tag'];
                    $message['cipher_version'] = $encrypted_gcm['cipher_version'];
                    error_log("send-message: Converted ECB→GCM successfully");
                } else {
                    // Если GCM шифрование не удалось, используем ECB как fallback
                    $message['text'] = $message['text_ecb'];
                    unset($message['iv'], $message['tag'], $message['cipher_version']);
                    error_log("send-message: GCM encryption failed, using ECB as fallback");
                }
            } else {
                $message['text'] = $message['text_ecb'];
                unset($message['iv'], $message['tag'], $message['cipher_version']);
                error_log("send-message: ECB decryption failed");
            }
        } catch (Exception $e) {
            $message['text'] = $message['text_ecb'];
            unset($message['iv'], $message['tag'], $message['cipher_version']);
            error_log("send-message: Exception in ECB→GCM: " . $e->getMessage());
        }
    }
    // Сохраняем GCM поля для WorldMates
    // НЕ удаляем их!
    
} else {
    // ЗАПРОС ОТ OFFICIAL WOWONDER (браузер/официальное приложение)
    // Возвращаем ECB формат
    
    if ($has_ecb) {
        // 1. Уже есть ECB версия (самое простое)
        $message['text'] = $message['text_ecb'];
        error_log("send-message: Using existing ECB for WoWonder");
    } 
    elseif ($has_gcm && class_exists('CryptoHelper')) {
        // 🎯 2. КРИТИЧЕСКИЙ ФИКС: Есть GCM (от WorldMates) → конвертируем GCM→ECB для браузера
        error_log("send-message: Converting GCM→ECB for WoWonder");
        error_log("send-message: GCM data: text=" . substr($message['text'], 0, 50) . "...");
        error_log("send-message: iv=" . $message['iv']);
        error_log("send-message: tag=" . $message['tag']);
        error_log("send-message: timestamp=" . $message['time']);
        
        try {
            // ВАЖНО: text содержит GCM-шифрованные данные
            $decrypted = CryptoHelper::decryptGCM(
                $message['text'], 
                $message['time'], 
                $message['iv'], 
                $message['tag']
            );
            
            error_log("send-message: Decrypted from GCM: " . ($decrypted !== false ? "SUCCESS" : "FAILED"));
            
            if ($decrypted !== false) {
                // Шифруем в ECB для WoWonder
                $encrypted_ecb = openssl_encrypt($decrypted, "AES-128-ECB", $message['time']);
                $message['text'] = $encrypted_ecb;
                error_log("send-message: Converted GCM→ECB successfully");
                
                // Сохраняем ECB версию в БД для будущих запросов
                if (!empty($last_id)) {
                    $db->where('id', $last_id)->update(T_MESSAGES, array(
                        'text_ecb' => $encrypted_ecb
                    ));
                }
            } else {
                // Если не удалось расшифровать GCM, возможно поврежденные данные
                error_log("send-message: GCM decryption FAILED for WoWonder");
                $message['text'] = ''; // Очищаем чтобы не показывать мусор
            }
        } catch (Exception $e) {
            error_log("send-message: Exception in GCM→ECB: " . $e->getMessage());
            $message['text'] = '';
        }
    }
    
    // Для WoWonder ВСЕГДА удаляем технические поля GCM
    unset($message['iv'], $message['tag'], $message['cipher_version'], $message['text_ecb']);
    error_log("send-message: Removed GCM fields for WoWonder");
}

// Для превью (список чатов) - всегда показываем читаемый текст
if (empty($message['text_preview']) && isset($plaintext)) {
    $message['text_preview'] = mb_substr($plaintext, 0, 100, 'UTF-8');
}
                    
                    // Формируем ответ
                    $response_data = array(
                        'api_status' => 200,
                        'message_data' => array($message),
                        'typing' => Wo_IsTyping($recipient_id) ? 1 : 0
                    );
                }
            }
        }
    }
}

// Обработка ошибок
if (!empty($error_code)) {
    $response_data['api_status'] = $error_code;
    $response_data['errors'] = $error_message;
}

// --- УЛУЧШЕННЫЙ ФИКС REDIS ---
if ($response_data['api_status'] == 200 && !empty($last_id)) {
    try {
        $redis = new Redis();
        if ($redis->connect('127.0.0.1', 6379)) {
            
            // Берем данные сообщения
            $msg_data = $response_data['message_data'][0];
            
            // ВАЖНО: Добавляем данные отправителя, чтобы Node.js не ругался на 'avatar'
            if (empty($msg_data['user_data'])) {
                $msg_data['user_data'] = Wo_UserData($wo['user']['user_id']);
            }

            $socket_data = array(
                'event'   => 'new_message',
                'data'    => $msg_data,
                'to_id'   => (int)$recipient_id,
                'from_id' => (int)$wo['user']['user_id']
            );

            $redis->publish('messages', json_encode($socket_data));
            error_log("Redis Success: Msg ID $last_id with UserData sent to socket.");
        }
    } catch (Exception $e) {
        error_log("Redis Error: " . $e->getMessage());
    }
}

// Выводим ответ
header("Content-type: application/json");
echo json_encode($response_data);

error_log("=== send-message.php END ===");
exit();
