<?php
// +------------------------------------------------------------------------+
// | 📡 CHANNELS: Підписка на канал за QR кодом
// +------------------------------------------------------------------------+

// Initialize response variables
$error_code = 0;
$error_message = '';
$data = [];

// User is already authenticated by index.php router
$user_id = $wo['user']['user_id'] ?? 0;

if (empty($user_id) || !is_numeric($user_id) || $user_id < 1) {
    $error_code    = 4;
    $error_message = 'Invalid access_token';
    http_response_code(400);
}

if ($error_code == 0) {
        // Отримати параметр
        $qr_code = (!empty($_POST['qr_code'])) ? Wo_Secure($_POST['qr_code']) : '';

        if (empty($qr_code)) {
            $error_code    = 5;
            $error_message = 'qr_code is required';
            http_response_code(400);
        } else {
            // Знайти канал за QR кодом
            $channel_query = mysqli_query($sqlConnect, "
                SELECT c.id, c.user_id, c.channel_name, c.channel_category,
                       c.channel_description, c.channel_username, c.avatar, c.cover
                FROM " . T_CHANNELS . " c
                WHERE c.qr_code = '{$qr_code}'
            ");

            if (mysqli_num_rows($channel_query) == 0) {
                $error_code    = 6;
                $error_message = 'Invalid QR code or channel not found';
                http_response_code(404);
            } else {
                $channel_data = mysqli_fetch_assoc($channel_query);
                $channel_id = $channel_data['id'];

                // Перевірити чи користувач вже підписаний
                $subscriber_query = mysqli_query($sqlConnect, "
                    SELECT COUNT(*) as count
                    FROM " . T_CHANNEL_SUBSCRIBERS . "
                    WHERE channel_id = {$channel_id}
                    AND user_id = {$user_id}
                ");
                $subscriber_data = mysqli_fetch_assoc($subscriber_query);

                if ($subscriber_data['count'] > 0) {
                    $error_code    = 7;
                    $error_message = 'You are already subscribed to this channel';
                    http_response_code(400);
                } else {
                    // Додати користувача як підписника
                    $subscribe_query = mysqli_query($sqlConnect, "
                        INSERT INTO " . T_CHANNEL_SUBSCRIBERS . "
                        (channel_id, user_id, subscribed_at)
                        VALUES ({$channel_id}, {$user_id}, " . time() . ")
                    ");

                    if ($subscribe_query) {
                        // Оновити лічильник підписників
                        mysqli_query($sqlConnect, "
                            UPDATE " . T_CHANNELS . "
                            SET subscribers_count = subscribers_count + 1
                            WHERE id = {$channel_id}
                        ");

                        $data = array(
                            'api_status' => 200,
                            'message' => 'Successfully subscribed to channel',
                            'channel' => array(
                                'id' => $channel_data['id'],
                                'user_id' => $channel_data['user_id'],
                                'channel_name' => $channel_data['channel_name'],
                                'channel_category' => $channel_data['channel_category'],
                                'channel_description' => $channel_data['channel_description'],
                                'channel_username' => $channel_data['channel_username'],
                                'avatar' => $channel_data['avatar'],
                                'cover' => $channel_data['cover']
                            )
                        );

                        // Логування
                        error_log("📡 Channel {$channel_id}: User {$user_id} subscribed via QR code: {$qr_code}");

                        // Відправити сповіщення власнику каналу
                        $notification_data = array(
                            'recipient_id' => $channel_data['user_id'],
                            'type' => 'channel_subscribe',
                            'channel_id' => $channel_id,
                            'user_id' => $user_id,
                            'time' => time()
                        );
                        // TODO: Implement notification system
                    } else {
                        $error_code    = 8;
                        $error_message = 'Failed to subscribe: ' . mysqli_error($sqlConnect);
                        http_response_code(500);
                    }
                }
            }
        }
    }
}

// Send JSON response
header('Content-Type: application/json');
if ($error_code > 0) {
    echo json_encode([
        'api_status' => http_response_code(),
        'error_code' => $error_code,
        'error_message' => $error_message
    ]);
} else if (!empty($data)) {
    echo json_encode($data);
} else {
    http_response_code(500);
    echo json_encode([
        'api_status' => 500,
        'error_message' => 'Unknown error occurred'
    ]);
}
exit();
?>
