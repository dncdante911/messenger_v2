<?php
// +------------------------------------------------------------------------+
// | 🔍 CHATS: Поиск сообщений в личном чате
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
        $chat_id = (!empty($_POST['chat_id']) && is_numeric($_POST['chat_id'])) ? (int)$_POST['chat_id'] : 0;
        $query = (!empty($_POST['query'])) ? Wo_Secure($_POST['query']) : '';
        $limit = (!empty($_POST['limit']) && is_numeric($_POST['limit'])) ? (int)$_POST['limit'] : 50;
        $offset = (!empty($_POST['offset']) && is_numeric($_POST['offset'])) ? (int)$_POST['offset'] : 0;

        if ($chat_id < 1) {
            $error_code    = 5;
            $error_message = 'chat_id is required';
            http_response_code(400);
        } else if (empty($query) || strlen($query) < 2) {
            $error_code    = 6;
            $error_message = 'Search query must be at least 2 characters';
            http_response_code(400);
        } else {
            // Проверить что пользователь участвует в чате
            // chat_id это ID другого пользователя
            $chat_user_query = mysqli_query($sqlConnect, "
                SELECT user_id
                FROM " . T_USERS . "
                WHERE user_id = {$chat_id}
            ");

            if (mysqli_num_rows($chat_user_query) == 0) {
                $error_code    = 7;
                $error_message = 'Chat user not found';
                http_response_code(404);
            } else {
                // Поиск сообщений в личном чате
                // Найти все сообщения между двумя пользователями
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
                    WHERE (
                        (m.from_id = {$user_id} AND m.to_id = {$chat_id})
                        OR (m.from_id = {$chat_id} AND m.to_id = {$user_id})
                    )
                    AND m.group_id = 0
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
                    WHERE (
                        (from_id = {$user_id} AND to_id = {$chat_id})
                        OR (from_id = {$chat_id} AND to_id = {$user_id})
                    )
                    AND group_id = 0
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
                error_log("🔍 Chat {$chat_id}: User {$user_id} searched for '{$query}', found {$total_count} results");
            }
        }
    }
}
?>
