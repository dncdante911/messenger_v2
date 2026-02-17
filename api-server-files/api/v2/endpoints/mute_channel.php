<?php
// +------------------------------------------------------------------------+
// | 📡 CHANNELS: Вимкнення сповіщень для каналу
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
        $channel_id = (!empty($_POST['channel_id']) && is_numeric($_POST['channel_id'])) ? (int)$_POST['channel_id'] : 0;

        if ($channel_id < 1) {
            $error_code    = 5;
            $error_message = 'channel_id is required';
            http_response_code(400);
        } else {
            // Перевірити чи користувач підписаний на канал
            $subscriber_query = mysqli_query($sqlConnect, "
                SELECT id
                FROM " . T_CHANNEL_SUBSCRIBERS . "
                WHERE channel_id = {$channel_id}
                AND user_id = {$user_id}
            ");

            if (mysqli_num_rows($subscriber_query) == 0) {
                $error_code    = 6;
                $error_message = 'You are not subscribed to this channel';
                http_response_code(400);
            } else {
                // Оновити статус is_muted
                $update_query = mysqli_query($sqlConnect, "
                    UPDATE " . T_CHANNEL_SUBSCRIBERS . "
                    SET is_muted = 1
                    WHERE channel_id = {$channel_id}
                    AND user_id = {$user_id}
                ");

                if ($update_query) {
                    $data = array(
                        'api_status' => 200,
                        'message' => 'Channel muted successfully'
                    );

                    error_log("📡 Channel {$channel_id}: User {$user_id} muted notifications");
                } else {
                    $error_code    = 7;
                    $error_message = 'Failed to mute channel: ' . mysqli_error($sqlConnect);
                    http_response_code(500);
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
