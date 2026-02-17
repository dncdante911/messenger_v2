<?php
/**
 * 📦 CLOUD BACKUP: Обновление настроек автозагрузки медиа
 *
 * Endpoint: POST /api/v2/endpoints/update_media_settings.php
 *
 * Параметры (все опциональные):
 * - auto_download_photos: "wifi_only"|"always"|"never"
 * - auto_download_videos: "wifi_only"|"always"|"never"
 * - auto_download_audio: "wifi_only"|"always"|"never"
 * - auto_download_documents: "wifi_only"|"always"|"never"
 * - compress_photos: "true"|"false"
 * - compress_videos: "true"|"false"
 * - backup_enabled: "true"|"false"
 *
 * Возвращает:
 * {
 *   "api_status": 200,
 *   "message": "settings updated successfully"
 * }
 */

if (empty($wo['user']['id'])) {
    $error_code = 4;
    $error_message = 'user not found';
} else {
    $user_id = $wo['user']['id'];

    // Проверяем существуют ли настройки
    $settings = $db->where('user_id', $user_id)->getOne(T_USER_MEDIA_SETTINGS);

    // Если настроек нет - создаем дефолтные
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

        $db->insert(T_USER_MEDIA_SETTINGS, $default_settings);
    }

    // Подготавливаем данные для обновления
    $update_data = array(
        'updated_at' => time()
    );

    // Валидация и добавление полей автозагрузки
    $allowed_download_values = array('wifi_only', 'always', 'never');

    if (!empty($_POST['auto_download_photos']) && in_array($_POST['auto_download_photos'], $allowed_download_values)) {
        $update_data['auto_download_photos'] = Wo_Secure($_POST['auto_download_photos']);
    }

    if (!empty($_POST['auto_download_videos']) && in_array($_POST['auto_download_videos'], $allowed_download_values)) {
        $update_data['auto_download_videos'] = Wo_Secure($_POST['auto_download_videos']);
    }

    if (!empty($_POST['auto_download_audio']) && in_array($_POST['auto_download_audio'], $allowed_download_values)) {
        $update_data['auto_download_audio'] = Wo_Secure($_POST['auto_download_audio']);
    }

    if (!empty($_POST['auto_download_documents']) && in_array($_POST['auto_download_documents'], $allowed_download_values)) {
        $update_data['auto_download_documents'] = Wo_Secure($_POST['auto_download_documents']);
    }

    // Булевые значения
    if (isset($_POST['compress_photos'])) {
        $update_data['compress_photos'] = ($_POST['compress_photos'] === 'true' || $_POST['compress_photos'] === '1') ? 1 : 0;
    }

    if (isset($_POST['compress_videos'])) {
        $update_data['compress_videos'] = ($_POST['compress_videos'] === 'true' || $_POST['compress_videos'] === '1') ? 1 : 0;
    }

    if (isset($_POST['backup_enabled'])) {
        $update_data['backup_enabled'] = ($_POST['backup_enabled'] === 'true' || $_POST['backup_enabled'] === '1') ? 1 : 0;
    }

    // Обновление last_backup_time (автоматически при успешном бэкапе)
    if (!empty($_POST['mark_backup_complete']) && $_POST['mark_backup_complete'] === 'true') {
        $update_data['last_backup_time'] = time();
    }

    // Обновляем настройки в БД
    $db->where('user_id', $user_id);
    $update_result = $db->update(T_USER_MEDIA_SETTINGS, $update_data);

    if ($update_result) {
        $response_data = array(
            'api_status' => 200,
            'message' => 'settings updated successfully'
        );
    } else {
        $error_code = 7;
        $error_message = 'failed to update settings';
    }
}
