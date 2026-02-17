<?php
// +------------------------------------------------------------------------+
// | 📌 GROUPS: Закрепление сообщения в группе
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
        // Получить параметры
        $group_id = (!empty($_POST['group_id']) && is_numeric($_POST['group_id'])) ? (int)$_POST['group_id'] : 0;
        $message_id = (!empty($_POST['message_id']) && is_numeric($_POST['message_id'])) ? (int)$_POST['message_id'] : 0;

        if ($group_id < 1) {
            $error_code    = 5;
            $error_message = 'group_id is required';
            http_response_code(400);
        } elseif ($message_id < 1) {
            $error_code    = 6;
            $error_message = 'message_id is required';
            http_response_code(400);
        } else {
            // Проверить существует ли группа
            $group_query = mysqli_query($sqlConnect, "
                SELECT group_id, user_id
                FROM " . T_GROUPS . "
                WHERE group_id = {$group_id}
            ");

            if (mysqli_num_rows($group_query) == 0) {
                $error_code    = 7;
                $error_message = 'Group not found';
                http_response_code(404);
            } else {
                $group_data = mysqli_fetch_assoc($group_query);

                // Проверить права: только админ/модератор может закреплять
                $is_admin = ($group_data['user_id'] == $user_id);
                $is_moderator = false;

                if (!$is_admin) {
                    // Проверить является ли модератором
                    $mod_query = mysqli_query($sqlConnect, "
                        SELECT COUNT(*) as count
                        FROM " . T_GROUP_MEMBERS . "
                        WHERE group_id = {$group_id}
                        AND user_id = {$user_id}
                        AND (role = 'admin' OR role = 'moderator')
                    ");
                    $mod_data = mysqli_fetch_assoc($mod_query);
                    $is_moderator = ($mod_data['count'] > 0);
                }

                if (!$is_admin && !$is_moderator) {
                    $error_code    = 8;
                    $error_message = 'Only admins and moderators can pin messages';
                    http_response_code(403);
                } else {
                    // Проверить существует ли сообщение в этой группе
                    $msg_query = mysqli_query($sqlConnect, "
                        SELECT id
                        FROM " . T_MESSAGES . "
                        WHERE id = {$message_id}
                        AND group_id = {$group_id}
                    ");

                    if (mysqli_num_rows($msg_query) == 0) {
                        $error_code    = 9;
                        $error_message = 'Message not found in this group';
                        http_response_code(404);
                    } else {
                        // Закрепить сообщение (обновить pinned_message_id в группе)
                        $update_query = mysqli_query($sqlConnect, "
                            UPDATE " . T_GROUPS . "
                            SET pinned_message_id = {$message_id}
                            WHERE group_id = {$group_id}
                        ");

                        if ($update_query) {
                            $data = array(
                                'api_status' => 200,
                                'message' => 'Message pinned successfully',
                                'pinned_message_id' => $message_id
                            );

                            // Логирование
                            error_log("📌 Group {$group_id}: User {$user_id} pinned message {$message_id}");
                        } else {
                            $error_code    = 10;
                            $error_message = 'Failed to pin message: ' . mysqli_error($sqlConnect);
                            http_response_code(500);
                        }
                    }
                }
            }
        }
    }
}
?>
