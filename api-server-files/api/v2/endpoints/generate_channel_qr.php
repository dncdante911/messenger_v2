<?php
// +------------------------------------------------------------------------+
// | 📡 CHANNELS: Генерація QR коду для каналу
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
        $channel_id = (!empty($_POST['channel_id']) && is_numeric($_POST['channel_id'])) ? (int)$_POST['channel_id'] : 0;

        if ($channel_id < 1) {
            $error_code    = 5;
            $error_message = 'channel_id is required';
            http_response_code(400);
        } else {
            // Перевірити чи існує канал
            $channel_query = mysqli_query($sqlConnect, "
                SELECT c.id, c.user_id, c.channel_name, c.qr_code
                FROM " . T_CHANNELS . " c
                WHERE c.id = {$channel_id}
            ");

            if (mysqli_num_rows($channel_query) == 0) {
                $error_code    = 7;
                $error_message = 'Channel not found';
                http_response_code(404);
            } else {
                $channel_data = mysqli_fetch_assoc($channel_query);

                // Перевірити чи користувач є власником або адміном каналу
                $is_admin = ($channel_data['user_id'] == $user_id);

                if (!$is_admin) {
                    // Перевірити чи користувач є адміністратором
                    $admin_query = mysqli_query($sqlConnect, "
                        SELECT COUNT(*) as count
                        FROM " . T_CHANNEL_ADMINS . "
                        WHERE channel_id = {$channel_id}
                        AND user_id = {$user_id}
                    ");
                    $admin_data = mysqli_fetch_assoc($admin_query);
                    $is_admin = ($admin_data['count'] > 0);
                }

                if (!$is_admin) {
                    $error_code    = 8;
                    $error_message = 'Only channel admins can generate QR codes';
                    http_response_code(403);
                } else {
                    // Якщо QR код вже існує, повернути його
                    if (!empty($channel_data['qr_code'])) {
                        $qr_code = $channel_data['qr_code'];
                    } else {
                        // Генеруємо унікальний QR код
                        $qr_code = 'WMC_' . strtoupper(substr(md5(uniqid($channel_id, true)), 0, 16));

                        // Зберігаємо QR код в БД
                        $update_query = mysqli_query($sqlConnect, "
                            UPDATE " . T_CHANNELS . "
                            SET qr_code = '{$qr_code}'
                            WHERE id = {$channel_id}
                        ");

                        if (!$update_query) {
                            $error_code    = 9;
                            $error_message = 'Failed to generate QR code: ' . mysqli_error($sqlConnect);
                            http_response_code(500);
                        }
                    }

                    if ($error_code == 0) {
                        // Формуємо URL для підписки
                        $join_url = $wo['config']['site_url'] . '/join-channel/' . $qr_code;

                        $data = array(
                            'api_status' => 200,
                            'message' => 'QR code generated successfully',
                            'qr_code' => $qr_code,
                            'join_url' => $join_url,
                            'channel_id' => $channel_id,
                            'channel_name' => $channel_data['channel_name']
                        );

                        // Логування
                        error_log("📡 Channel {$channel_id}: User {$user_id} generated QR code: {$qr_code}");
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
