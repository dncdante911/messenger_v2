<?php
// +------------------------------------------------------------------------+
// | 📦 CLOUD BACKUP v2: Получение расширенных настроек облачного бэкапа
// +------------------------------------------------------------------------+

if (empty($_GET['access_token']) || empty($_GET['access_token'])) {
    $error_code    = 3;
    $error_message = 'access_token is missing';
    http_response_code(400);
}

if ($error_code == 0) {
    $user_id = Wo_UserIdFromAccessToken($_GET['access_token']);
    if (empty($user_id) || !is_numeric($user_id) || $user_id < 1) {
        $error_code    = 4;
        $error_message = 'Invalid access_token';
        http_response_code(400);
    } else {
        // Получить настройки из БД
        $query = mysqli_query($sqlConnect, "
            SELECT * FROM " . T_USER_CLOUD_BACKUP_SETTINGS . "
            WHERE user_id = {$user_id}
        ");

        if (mysqli_num_rows($query) > 0) {
            // Настройки существуют - возвращаем их
            $settings = mysqli_fetch_assoc($query);

            $data = array(
                'api_status' => 200,
                'settings' => array(
                    // АВТОЗАГРУЗКА МЕДИА (Мобильный интернет)
                    'mobile_photos' => (bool)$settings['mobile_photos'],
                    'mobile_videos' => (bool)$settings['mobile_videos'],
                    'mobile_files' => (bool)$settings['mobile_files'],
                    'mobile_videos_limit' => (int)$settings['mobile_videos_limit'],
                    'mobile_files_limit' => (int)$settings['mobile_files_limit'],

                    // АВТОЗАГРУЗКА МЕДИА (Wi-Fi)
                    'wifi_photos' => (bool)$settings['wifi_photos'],
                    'wifi_videos' => (bool)$settings['wifi_videos'],
                    'wifi_files' => (bool)$settings['wifi_files'],
                    'wifi_videos_limit' => (int)$settings['wifi_videos_limit'],
                    'wifi_files_limit' => (int)$settings['wifi_files_limit'],

                    // АВТОЗАГРУЗКА МЕДИА (Роуминг)
                    'roaming_photos' => (bool)$settings['roaming_photos'],

                    // СОХРАНЯТЬ В ГАЛЕРЕЕ
                    'save_to_gallery_private_chats' => (bool)$settings['save_to_gallery_private_chats'],
                    'save_to_gallery_groups' => (bool)$settings['save_to_gallery_groups'],
                    'save_to_gallery_channels' => (bool)$settings['save_to_gallery_channels'],

                    // СТРИМИНГ
                    'streaming_enabled' => (bool)$settings['streaming_enabled'],

                    // УПРАВЛЕНИЕ КЭШЕМ
                    'cache_size_limit' => (int)$settings['cache_size_limit'],

                    // ОБЛАЧНЫЙ БЭКАП
                    'backup_enabled' => (bool)$settings['backup_enabled'],
                    'backup_provider' => $settings['backup_provider'],
                    'backup_frequency' => $settings['backup_frequency'],
                    'last_backup_time' => $settings['last_backup_time'] ? (int)$settings['last_backup_time'] : null,

                    // ПРОКСИ
                    'proxy_enabled' => (bool)$settings['proxy_enabled'],
                    'proxy_host' => $settings['proxy_host'],
                    'proxy_port' => $settings['proxy_port'] ? (int)$settings['proxy_port'] : null
                )
            );
        } else {
            // Настройки не найдены - создать дефолтные
            $insert_query = "
                INSERT INTO " . T_USER_CLOUD_BACKUP_SETTINGS . "
                (user_id) VALUES ({$user_id})
            ";

            if (mysqli_query($sqlConnect, $insert_query)) {
                // Получить только что созданные настройки
                $settings = mysqli_fetch_assoc(mysqli_query($sqlConnect, "
                    SELECT * FROM " . T_USER_CLOUD_BACKUP_SETTINGS . "
                    WHERE user_id = {$user_id}
                "));

                $data = array(
                    'api_status' => 200,
                    'settings' => array(
                        // АВТОЗАГРУЗКА МЕДИА (Мобильный интернет)
                        'mobile_photos' => (bool)$settings['mobile_photos'],
                        'mobile_videos' => (bool)$settings['mobile_videos'],
                        'mobile_files' => (bool)$settings['mobile_files'],
                        'mobile_videos_limit' => (int)$settings['mobile_videos_limit'],
                        'mobile_files_limit' => (int)$settings['mobile_files_limit'],

                        // АВТОЗАГРУЗКА МЕДИА (Wi-Fi)
                        'wifi_photos' => (bool)$settings['wifi_photos'],
                        'wifi_videos' => (bool)$settings['wifi_videos'],
                        'wifi_files' => (bool)$settings['wifi_files'],
                        'wifi_videos_limit' => (int)$settings['wifi_videos_limit'],
                        'wifi_files_limit' => (int)$settings['wifi_files_limit'],

                        // АВТОЗАГРУЗКА МЕДИА (Роуминг)
                        'roaming_photos' => (bool)$settings['roaming_photos'],

                        // СОХРАНЯТЬ В ГАЛЕРЕЕ
                        'save_to_gallery_private_chats' => (bool)$settings['save_to_gallery_private_chats'],
                        'save_to_gallery_groups' => (bool)$settings['save_to_gallery_groups'],
                        'save_to_gallery_channels' => (bool)$settings['save_to_gallery_channels'],

                        // СТРИМИНГ
                        'streaming_enabled' => (bool)$settings['streaming_enabled'],

                        // УПРАВЛЕНИЕ КЭШЕМ
                        'cache_size_limit' => (int)$settings['cache_size_limit'],

                        // ОБЛАЧНЫЙ БЭКАП
                        'backup_enabled' => (bool)$settings['backup_enabled'],
                        'backup_provider' => $settings['backup_provider'],
                        'backup_frequency' => $settings['backup_frequency'],
                        'last_backup_time' => $settings['last_backup_time'] ? (int)$settings['last_backup_time'] : null,

                        // ПРОКСИ
                        'proxy_enabled' => (bool)$settings['proxy_enabled'],
                        'proxy_host' => $settings['proxy_host'],
                        'proxy_port' => $settings['proxy_port'] ? (int)$settings['proxy_port'] : null
                    )
                );
            } else {
                $error_code    = 5;
                $error_message = 'Failed to create default settings';
                http_response_code(500);
            }
        }
    }
}
?>
