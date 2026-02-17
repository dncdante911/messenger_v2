<?php
// +------------------------------------------------------------------------+
// | API Endpoint: Update Notification Settings
// +------------------------------------------------------------------------+
// | Обновление настроек уведомлений пользователя
// | Обертка над XHR endpoint для единого API интерфейса
// +------------------------------------------------------------------------+

if ($error_code == 0) {
    $user_id = Wo_ValidateAccessToken($_POST['access_token']);
    if (empty($user_id) || !is_numeric($user_id) || $user_id < 1) {
        $error_code    = 4;
        $error_message = 'Invalid access_token';
        http_response_code(401);
    } else {
        // Получить текущие настройки уведомлений из БД
        $user_query = mysqli_query($sqlConnect, "
            SELECT notification_settings FROM " . T_USERS . "
            WHERE user_id = {$user_id}
        ");

        if (mysqli_num_rows($user_query) > 0) {
            $user_data = mysqli_fetch_assoc($user_query);
            $current_settings = json_decode($user_data['notification_settings'], true);

            // Если текущих настроек нет, создать дефолтные
            if (empty($current_settings) || !is_array($current_settings)) {
                $current_settings = array(
                    'e_liked' => 1,
                    'e_shared' => 1,
                    'e_wondered' => 0,
                    'e_commented' => 1,
                    'e_followed' => 1,
                    'e_accepted' => 1,
                    'e_mentioned' => 1,
                    'e_joined_group' => 1,
                    'e_liked_page' => 1,
                    'e_visited' => 1,
                    'e_profile_wall_post' => 1,
                    'e_memory' => 1
                );
            }

            // Обновить настройки на основе входящих параметров
            $valid_keys = array(
                'e_liked', 'e_shared', 'e_wondered', 'e_commented',
                'e_followed', 'e_accepted', 'e_mentioned', 'e_joined_group',
                'e_liked_page', 'e_visited', 'e_profile_wall_post', 'e_memory'
            );

            $updated = false;
            foreach ($valid_keys as $key) {
                if (isset($_POST[$key])) {
                    $value = intval($_POST[$key]);
                    if ($value === 0 || $value === 1) {
                        $current_settings[$key] = $value;
                        $updated = true;
                    }
                }
            }

            // Email notification setting
            $update_data = array();
            if (isset($_POST['email_notification'])) {
                $value = intval($_POST['email_notification']);
                if ($value === 0 || $value === 1) {
                    $update_data['emailNotification'] = $value;
                }
            }

            if ($updated) {
                $update_data['notification_settings'] = json_encode($current_settings);
            }

            if (!empty($update_data)) {
                // Обновить в БД
                $update_query = "UPDATE " . T_USERS . " SET ";
                $updates = array();
                foreach ($update_data as $key => $value) {
                    if ($key === 'notification_settings') {
                        $updates[] = "{$key} = '" . Wo_Secure($value) . "'";
                    } else {
                        $updates[] = "{$key} = {$value}";
                    }
                }
                $update_query .= implode(', ', $updates);
                $update_query .= " WHERE user_id = {$user_id}";

                if (mysqli_query($sqlConnect, $update_query)) {
                    // Очистить кэш
                    cache($user_id, 'users', 'delete');

                    $data = array(
                        'api_status' => 200,
                        'message' => 'Notification settings updated successfully',
                        'settings' => $current_settings
                    );
                } else {
                    $error_code    = 5;
                    $error_message = 'Failed to update notification settings: ' . mysqli_error($sqlConnect);
                    http_response_code(500);
                }
            } else {
                $data = array(
                    'api_status' => 200,
                    'message' => 'No settings to update',
                    'settings' => $current_settings
                );
            }
        } else {
            $error_code    = 6;
            $error_message = 'User not found';
            http_response_code(404);
        }
    }
}

// Exit to prevent api-v2.php from echoing additional output
exit();
?>
