<?php
// +------------------------------------------------------------------------+
// | 🔔 GROUPS: Увімкнення сповіщень для групи
// +------------------------------------------------------------------------+

if (empty($_POST['access_token'])) {
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
        // Получить параметр
        $group_id = (!empty($_POST['group_id']) && is_numeric($_POST['group_id'])) ? (int)$_POST['group_id'] : 0;

        if ($group_id < 1) {
            $error_code    = 5;
            $error_message = 'group_id is required';
            http_response_code(400);
        } else {
            // Проверить существует ли группа
            $group_query = mysqli_query($sqlConnect, "
                SELECT group_id
                FROM " . T_GROUPS . "
                WHERE group_id = {$group_id}
            ");

            if (mysqli_num_rows($group_query) == 0) {
                $error_code    = 7;
                $error_message = 'Group not found';
                http_response_code(404);
            } else {
                // Проверить является ли пользователь членом группы
                $member_query = mysqli_query($sqlConnect, "
                    SELECT COUNT(*) as count
                    FROM " . T_GROUP_MEMBERS . "
                    WHERE group_id = {$group_id}
                    AND user_id = {$user_id}
                ");
                $member_data = mysqli_fetch_assoc($member_query);

                if ($member_data['count'] == 0) {
                    $error_code    = 8;
                    $error_message = 'User is not a member of this group';
                    http_response_code(403);
                } else {
                    // Установить is_muted = 0 для данного пользователя в группе
                    $update_query = mysqli_query($sqlConnect, "
                        UPDATE " . T_GROUP_MEMBERS . "
                        SET is_muted = 0
                        WHERE group_id = {$group_id}
                        AND user_id = {$user_id}
                    ");

                    if ($update_query) {
                        $data = array(
                            'api_status' => 200,
                            'message' => 'Group unmuted successfully'
                        );

                        // Логирование
                        error_log("🔔 Group {$group_id}: User {$user_id} unmuted notifications");
                    } else {
                        $error_code    = 10;
                        $error_message = 'Failed to unmute group: ' . mysqli_error($sqlConnect);
                        http_response_code(500);
                    }
                }
            }
        }
    }
}
?>
