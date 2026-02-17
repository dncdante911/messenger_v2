<?php
// +------------------------------------------------------------------------+
// | API Endpoint: Update Privacy Settings
// +------------------------------------------------------------------------+
// | Обновление настроек приватности пользователя
// | Обертка над XHR endpoint для единого API интерфейса
// +------------------------------------------------------------------------+

// Helper function to convert string privacy values to numeric
function convertPrivacyToNumeric($value) {
    if (is_numeric($value)) return $value;

    switch (strtolower($value)) {
        case 'everyone': return '0';
        case 'friends': case 'ifollow': return '1';
        case 'followers': return '2';
        case 'me': case 'nobody': return '3';
        default: return $value;
    }
}

if ($error_code == 0) {
    $user_id = Wo_ValidateAccessToken($_POST['access_token']);
    if (empty($user_id) || !is_numeric($user_id) || $user_id < 1) {
        $error_code    = 4;
        $error_message = 'Invalid access_token';
        http_response_code(401);
    } else {
        // Параметры приватности
        $update_data = array();

        // follow_privacy: 0 - everyone, 1 - only me
        if (isset($_POST['follow_privacy'])) {
            $value = convertPrivacyToNumeric($_POST['follow_privacy']);
            if (in_array($value, array('0', '1', 0, 1))) {
                $update_data['follow_privacy'] = Wo_Secure($value);
            }
        }

        // friend_privacy: 0 - everyone, 1 - people I follow, 2 - people follow me, 3 - no one
        if (isset($_POST['friend_privacy'])) {
            $value = convertPrivacyToNumeric($_POST['friend_privacy']);
            if (in_array($value, array('0', '1', '2', '3', 0, 1, 2, 3))) {
                $update_data['friend_privacy'] = Wo_Secure($value);
            }
        }

        // post_privacy: everyone, ifollow, nobody
        if (isset($_POST['post_privacy'])) {
            $value = strtolower($_POST['post_privacy']);
            // Convert to WoWonder format
            if ($value === 'friends') $value = 'ifollow';
            if ($value === 'me') $value = 'nobody';
            if (in_array($value, array('everyone', 'ifollow', 'nobody'))) {
                $update_data['post_privacy'] = Wo_Secure($value);
            }
        }

        // message_privacy: 0 - everyone, 1 - people I follow, 2 - no one
        if (isset($_POST['message_privacy'])) {
            $value = convertPrivacyToNumeric($_POST['message_privacy']);
            if (in_array($value, array('0', '1', '2', 0, 1, 2))) {
                $update_data['message_privacy'] = Wo_Secure($value);
            }
        }

        // confirm_followers: 0 - no, 1 - yes
        if (isset($_POST['confirm_followers'])) {
            $value = convertPrivacyToNumeric($_POST['confirm_followers']);
            if (in_array($value, array('0', '1', 0, 1))) {
                $update_data['confirm_followers'] = Wo_Secure($value);
            }
        }

        // show_activities_privacy: 0 - hide, 1 - show
        if (isset($_POST['show_activities_privacy'])) {
            $value = convertPrivacyToNumeric($_POST['show_activities_privacy']);
            if (in_array($value, array('0', '1', 0, 1))) {
                $update_data['show_activities_privacy'] = Wo_Secure($value);
            }
        }

        // birth_privacy: 0 - everyone, 1 - people I follow, 2 - no one
        if (isset($_POST['birth_privacy'])) {
            $value = convertPrivacyToNumeric($_POST['birth_privacy']);
            if (in_array($value, array('0', '1', '2', 0, 1, 2))) {
                $update_data['birth_privacy'] = Wo_Secure($value);
            }
        }

        // visit_privacy: 0 - public, 1 - private
        if (isset($_POST['visit_privacy'])) {
            $value = convertPrivacyToNumeric($_POST['visit_privacy']);
            if (in_array($value, array('0', '1', 0, 1))) {
                $update_data['visit_privacy'] = Wo_Secure($value);
            }
        }

        // showlastseen: 0 - hide, 1 - show
        if (isset($_POST['showlastseen'])) {
            $value = convertPrivacyToNumeric($_POST['showlastseen']);
            if (in_array($value, array('0', '1', 0, 1))) {
                $update_data['showlastseen'] = Wo_Secure($value);
            }
        }

        // status: 0 - offline, 1 - online
        if (isset($_POST['status'])) {
            $value = convertPrivacyToNumeric($_POST['status']);
            if (in_array($value, array('0', '1', 0, 1))) {
                $update_data['status'] = Wo_Secure($value);
            }
        }

        // share_my_location: 0 - no, 1 - yes
        if (isset($_POST['share_my_location'])) {
            $value = convertPrivacyToNumeric($_POST['share_my_location']);
            if (in_array($value, array('0', '1', 0, 1))) {
                $update_data['share_my_location'] = Wo_Secure($value);
            }
        }

        // share_my_data: 0 - no, 1 - yes
        if (isset($_POST['share_my_data'])) {
            $value = convertPrivacyToNumeric($_POST['share_my_data']);
            if (in_array($value, array('0', '1', 0, 1))) {
                $update_data['share_my_data'] = Wo_Secure($value);
            }
        }

        if (!empty($update_data)) {
            // Обновить настройки в БД
            $update = $db->where('user_id', $user_id)->update(T_USERS, $update_data);

            if ($update) {
                // Очистить кэш пользователя
                cache($user_id, 'users', 'delete');

                $data = array(
                    'api_status' => 200,
                    'message' => 'Privacy settings updated successfully'
                );
            } else {
                $error_code    = 5;
                $error_message = 'Failed to update privacy settings';
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

// Exit to prevent api-v2.php from echoing additional output
exit();
?>
