<?php
// +------------------------------------------------------------------------+
// | 🔍 GROUPS: Поиск сообщений в группе
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
        $query = (!empty($_POST['query'])) ? Wo_Secure($_POST['query']) : '';
        $limit = (!empty($_POST['limit']) && is_numeric($_POST['limit'])) ? (int)$_POST['limit'] : 50;
        $offset = (!empty($_POST['offset']) && is_numeric($_POST['offset'])) ? (int)$_POST['offset'] : 0;

        if ($group_id < 1) {
            $error_code    = 5;
            $error_message = 'group_id is required';
            http_response_code(400);
        } else if (empty($query) || strlen($query) < 2) {
            $error_code    = 6;
            $error_message = 'Search query must be at least 2 characters';
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
                    // Поиск сообщений в группе
                    // ВАЖНО: Поиск по зашифрованному тексту - ограничение шифрования
                    // В реальном приложении нужен поисковый индекс на клиенте
                    $search_query = mysqli_query($sqlConnect, "
                        SELECT m.id, m.from_id, m.to_id, m.group_id, m.text,
                               m.time, m.media, m.mediaFileName, m.mediaFileNames,
                               m.stickers, m.product_id, m.lat, m.lng, m.reply_id,
                               m.story_id, m.reply, m.forward, m.file_size,
                               u.username as sender_username,
                               CONCAT(u.first_name, ' ', u.last_name) as sender_name,
                               u.avatar as sender_avatar
                        FROM " . T_MESSAGES . " m
                        LEFT JOIN " . T_USERS . " u ON m.from_id = u.user_id
                        WHERE m.group_id = {$group_id}
                        AND (
                            m.text LIKE '%{$query}%'
                            OR m.mediaFileName LIKE '%{$query}%'
                        )
                        ORDER BY m.time DESC
                        LIMIT {$limit} OFFSET {$offset}
                    ");

                    $messages = array();
                    while ($message = mysqli_fetch_assoc($search_query)) {
                        $messages[] = $message;
                    }

                    // Получить общее количество результатов
                    $count_query = mysqli_query($sqlConnect, "
                        SELECT COUNT(*) as total
                        FROM " . T_MESSAGES . "
                        WHERE group_id = {$group_id}
                        AND (
                            text LIKE '%{$query}%'
                            OR mediaFileName LIKE '%{$query}%'
                        )
                    ");
                    $count_data = mysqli_fetch_assoc($count_query);
                    $total_count = $count_data['total'];

                    $data = array(
                        'api_status' => 200,
                        'messages' => $messages,
                        'total_count' => $total_count,
                        'query' => $query,
                        'message' => 'Search completed successfully'
                    );

                    // Логирование
                    error_log("🔍 Group {$group_id}: User {$user_id} searched for '{$query}', found {$total_count} results");
                }
            }
        }
    }
}
?>
