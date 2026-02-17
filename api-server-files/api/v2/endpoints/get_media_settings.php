<?php
/**
 * 📦 CLOUD BACKUP: Получение настроек автозагрузки медиа
 *
 * Endpoint: POST /api/v2/endpoints/get_media_settings.php
 * Параметры: НЕТ (использует $wo['user']['id'] из сессии)
 *
 * Возвращает:
 * {
 *   "api_status": 200,
 *   "settings": {
 *     "auto_download_photos": "wifi_only|always|never",
 *     "auto_download_videos": "wifi_only|always|never",
 *     "auto_download_audio": "wifi_only|always|never",
 *     "auto_download_documents": "wifi_only|always|never",
 *     "compress_photos": true|false,
 *     "compress_videos": true|false,
 *     "backup_enabled": true|false,
 *     "last_backup_time": timestamp|null
 *   }
 * }
 */

if (empty($wo['user']['id'])) {
    $error_code = 4;
    $error_message = 'user not found';
} else {
    $user_id = $wo['user']['id'];

    // Получаем настройки пользователя из БД
    $settings = $db->where('user_id', $user_id)->getOne(T_USER_MEDIA_SETTINGS);

    // Если настроек еще нет - создаем дефолтные
    if (empty($settings)) {
        $default_settings = array(
            'user_id' => $user_id,
            'auto_download_photos' => 'wifi_only',
            'auto_download_videos' => 'wifi_only',
            'auto_download_audio' => 'always',
            'auto_download_documents' => 'wifi_only',
            'compress_photos' => 1,
            'compress_videos' => 1,
            'backup_enabled' => 1,
            'last_backup_time' => null,
            'created_at' => time(),
            'updated_at' => time()
        );

        $insert_id = $db->insert(T_USER_MEDIA_SETTINGS, $default_settings);

        if ($insert_id) {
            $settings = $db->where('user_id', $user_id)->getOne(T_USER_MEDIA_SETTINGS);
        } else {
            $error_code = 6;
            $error_message = 'failed to create default settings';
        }
    }

    if (!empty($settings)) {
        // Форматируем ответ
        $response_data = array(
            'api_status' => 200,
            'settings' => array(
                'auto_download_photos' => $settings['auto_download_photos'],
                'auto_download_videos' => $settings['auto_download_videos'],
                'auto_download_audio' => $settings['auto_download_audio'],
                'auto_download_documents' => $settings['auto_download_documents'],
                'compress_photos' => (bool)$settings['compress_photos'],
                'compress_videos' => (bool)$settings['compress_videos'],
                'backup_enabled' => (bool)$settings['backup_enabled'],
                'last_backup_time' => $settings['last_backup_time']
            )
        );
    }
}
