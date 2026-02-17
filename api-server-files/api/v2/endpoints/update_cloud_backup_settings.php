<?php
// +------------------------------------------------------------------------+
// | 📦 CLOUD BACKUP v2: Обновление расширенных настроек облачного бэкапа
// +------------------------------------------------------------------------+

if (empty($_POST['access_token']) || empty($_POST['access_token'])) {
    $error_code    = 3;
    $error_message = 'access_token is missing';
    http_response_code(400);
}

if ($error_code == 0) {
    $user_id = Wo_UserIdFromAccessToken($_POST['access_token']);
    if (empty($user_id) || !is_numeric($user_id) || $user_id < 1) {
        $error_code    = 4;
        $error_message = 'Invalid access_token';
        http_response_code(400);
    } else {
        // Проверить существуют ли настройки
        $check_query = mysqli_query($sqlConnect, "
            SELECT id FROM " . T_USER_CLOUD_BACKUP_SETTINGS . "
            WHERE user_id = {$user_id}
        ");

        if (mysqli_num_rows($check_query) == 0) {
            // Создать дефолтные настройки если не существуют
            mysqli_query($sqlConnect, "
                INSERT INTO " . T_USER_CLOUD_BACKUP_SETTINGS . "
                (user_id) VALUES ({$user_id})
            ");
        }

        // Собрать SET часть SQL запроса для обновления
        $updates = array();

        // АВТОЗАГРУЗКА МЕДИА (Мобильный интернет)
        if (isset($_POST['mobile_photos'])) {
            $value = $_POST['mobile_photos'] === 'true' || $_POST['mobile_photos'] === '1' ? 1 : 0;
            $updates[] = "mobile_photos = {$value}";
        }
        if (isset($_POST['mobile_videos'])) {
            $value = $_POST['mobile_videos'] === 'true' || $_POST['mobile_videos'] === '1' ? 1 : 0;
            $updates[] = "mobile_videos = {$value}";
        }
        if (isset($_POST['mobile_files'])) {
            $value = $_POST['mobile_files'] === 'true' || $_POST['mobile_files'] === '1' ? 1 : 0;
            $updates[] = "mobile_files = {$value}";
        }
        if (isset($_POST['mobile_videos_limit'])) {
            $value = (int)$_POST['mobile_videos_limit'];
            $updates[] = "mobile_videos_limit = {$value}";
        }
        if (isset($_POST['mobile_files_limit'])) {
            $value = (int)$_POST['mobile_files_limit'];
            $updates[] = "mobile_files_limit = {$value}";
        }

        // АВТОЗАГРУЗКА МЕДИА (Wi-Fi)
        if (isset($_POST['wifi_photos'])) {
            $value = $_POST['wifi_photos'] === 'true' || $_POST['wifi_photos'] === '1' ? 1 : 0;
            $updates[] = "wifi_photos = {$value}";
        }
        if (isset($_POST['wifi_videos'])) {
            $value = $_POST['wifi_videos'] === 'true' || $_POST['wifi_videos'] === '1' ? 1 : 0;
            $updates[] = "wifi_videos = {$value}";
        }
        if (isset($_POST['wifi_files'])) {
            $value = $_POST['wifi_files'] === 'true' || $_POST['wifi_files'] === '1' ? 1 : 0;
            $updates[] = "wifi_files = {$value}";
        }
        if (isset($_POST['wifi_videos_limit'])) {
            $value = (int)$_POST['wifi_videos_limit'];
            $updates[] = "wifi_videos_limit = {$value}";
        }
        if (isset($_POST['wifi_files_limit'])) {
            $value = (int)$_POST['wifi_files_limit'];
            $updates[] = "wifi_files_limit = {$value}";
        }

        // АВТОЗАГРУЗКА МЕДИА (Роуминг)
        if (isset($_POST['roaming_photos'])) {
            $value = $_POST['roaming_photos'] === 'true' || $_POST['roaming_photos'] === '1' ? 1 : 0;
            $updates[] = "roaming_photos = {$value}";
        }

        // СОХРАНЯТЬ В ГАЛЕРЕЕ
        if (isset($_POST['save_to_gallery_private_chats'])) {
            $value = $_POST['save_to_gallery_private_chats'] === 'true' || $_POST['save_to_gallery_private_chats'] === '1' ? 1 : 0;
            $updates[] = "save_to_gallery_private_chats = {$value}";
        }
        if (isset($_POST['save_to_gallery_groups'])) {
            $value = $_POST['save_to_gallery_groups'] === 'true' || $_POST['save_to_gallery_groups'] === '1' ? 1 : 0;
            $updates[] = "save_to_gallery_groups = {$value}";
        }
        if (isset($_POST['save_to_gallery_channels'])) {
            $value = $_POST['save_to_gallery_channels'] === 'true' || $_POST['save_to_gallery_channels'] === '1' ? 1 : 0;
            $updates[] = "save_to_gallery_channels = {$value}";
        }

        // СТРИМИНГ
        if (isset($_POST['streaming_enabled'])) {
            $value = $_POST['streaming_enabled'] === 'true' || $_POST['streaming_enabled'] === '1' ? 1 : 0;
            $updates[] = "streaming_enabled = {$value}";
        }

        // УПРАВЛЕНИЕ КЭШЕМ
        if (isset($_POST['cache_size_limit'])) {
            $value = (int)$_POST['cache_size_limit'];
            $updates[] = "cache_size_limit = {$value}";
        }

        // ОБЛАЧНЫЙ БЭКАП
        if (isset($_POST['backup_enabled'])) {
            $value = $_POST['backup_enabled'] === 'true' || $_POST['backup_enabled'] === '1' ? 1 : 0;
            $updates[] = "backup_enabled = {$value}";
        }
        if (isset($_POST['backup_provider'])) {
            $value = Wo_Secure($_POST['backup_provider']);
            $updates[] = "backup_provider = '{$value}'";
        }
        if (isset($_POST['backup_frequency'])) {
            $value = Wo_Secure($_POST['backup_frequency']);
            $updates[] = "backup_frequency = '{$value}'";
        }
        if (isset($_POST['mark_backup_complete']) && $_POST['mark_backup_complete'] === 'true') {
            $timestamp = time() * 1000; // Миллисекунды
            $updates[] = "last_backup_time = {$timestamp}";
        }

        // ПРОКСИ
        if (isset($_POST['proxy_enabled'])) {
            $value = $_POST['proxy_enabled'] === 'true' || $_POST['proxy_enabled'] === '1' ? 1 : 0;
            $updates[] = "proxy_enabled = {$value}";
        }
        if (isset($_POST['proxy_host'])) {
            $value = Wo_Secure($_POST['proxy_host']);
            $updates[] = "proxy_host = '{$value}'";
        }
        if (isset($_POST['proxy_port'])) {
            $value = (int)$_POST['proxy_port'];
            $updates[] = "proxy_port = {$value}";
        }

        // Выполнить UPDATE только если есть что обновлять
        if (!empty($updates)) {
            $update_sql = "
                UPDATE " . T_USER_CLOUD_BACKUP_SETTINGS . "
                SET " . implode(', ', $updates) . "
                WHERE user_id = {$user_id}
            ";

            if (mysqli_query($sqlConnect, $update_sql)) {
                $data = array(
                    'api_status' => 200,
                    'message' => 'Cloud backup settings updated successfully'
                );
            } else {
                $error_code    = 5;
                $error_message = 'Failed to update settings: ' . mysqli_error($sqlConnect);
                http_response_code(500);
            }
        } else {
            $data = array(
                'api_status' => 200,
                'message' => 'No settings to update'
            );
        }
    }
}
?>
