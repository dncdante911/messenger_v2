<?php
// +------------------------------------------------------------------------+
// | 🔲 GROUPS: Генерація QR коду для групи чату (Wo_GroupChat)
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/../config.php');

header('Content-Type: application/json');

$response_data = array('api_status' => 400);
$error_code = 0;
$error_message = '';

if (empty($_POST['access_token'])) {
    $error_code = 3;
    $error_message = 'access_token is missing';
}

if ($error_code == 0) {
    $user_id = Wo_UserIdFromAccessToken($_POST['access_token']);
    if (empty($user_id) || !is_numeric($user_id) || $user_id < 1) {
        $error_code = 4;
        $error_message = 'Invalid access_token';
    } else {
        $group_id = (!empty($_POST['group_id']) && is_numeric($_POST['group_id'])) ? (int)$_POST['group_id'] : 0;

        if ($group_id < 1) {
            $error_code = 5;
            $error_message = 'group_id is required';
        } else {
            // Перевірити чи існує група в Wo_GroupChat (messenger groups)
            $group_query = mysqli_query($sqlConnect, "
                SELECT g.group_id, g.user_id, g.group_name, g.qr_code
                FROM Wo_GroupChat g
                WHERE g.group_id = {$group_id}
                AND (g.type = 'group' OR g.type IS NULL OR g.type = '')
            ");

            if (mysqli_num_rows($group_query) == 0) {
                $error_code = 7;
                $error_message = 'Group not found';
            } else {
                $group_data = mysqli_fetch_assoc($group_query);

                // Перевірити чи користувач є адміном групи
                $is_admin = ($group_data['user_id'] == $user_id);

                if (!$is_admin) {
                    // Перевірити чи користувач є адміном через Wo_GroupChatUsers
                    $admin_query = mysqli_query($sqlConnect, "
                        SELECT role
                        FROM Wo_GroupChatUsers
                        WHERE group_id = {$group_id}
                        AND user_id = {$user_id}
                        AND role IN ('owner', 'admin')
                    ");
                    $is_admin = (mysqli_num_rows($admin_query) > 0);
                }

                if (!$is_admin) {
                    $error_code = 8;
                    $error_message = 'Only group admins can generate QR codes';
                } else {
                    // Якщо QR код вже існує, повернути його
                    if (!empty($group_data['qr_code'])) {
                        $qr_code = $group_data['qr_code'];
                    } else {
                        // Генеруємо унікальний QR код
                        $qr_code = 'WMG_' . strtoupper(substr(md5(uniqid($group_id, true)), 0, 16));

                        // Перевіряємо чи є колонка qr_code
                        $check_column = mysqli_query($sqlConnect, "SHOW COLUMNS FROM Wo_GroupChat LIKE 'qr_code'");
                        if (mysqli_num_rows($check_column) == 0) {
                            // Додаємо колонку якщо не існує
                            mysqli_query($sqlConnect, "ALTER TABLE Wo_GroupChat ADD COLUMN qr_code VARCHAR(32) NULL");
                        }

                        // Зберігаємо QR код в БД
                        $qr_code_escaped = mysqli_real_escape_string($sqlConnect, $qr_code);
                        $update_query = mysqli_query($sqlConnect, "
                            UPDATE Wo_GroupChat
                            SET qr_code = '{$qr_code_escaped}'
                            WHERE group_id = {$group_id}
                        ");

                        if (!$update_query) {
                            $error_code = 9;
                            $error_message = 'Failed to generate QR code: ' . mysqli_error($sqlConnect);
                        }
                    }

                    if ($error_code == 0) {
                        // Формуємо URL для приєднання
                        $site_url = $wo['config']['site_url'] ?? 'https://worldmates.club';
                        $join_url = rtrim($site_url, '/') . '/join-group/' . $qr_code;

                        $response_data = array(
                            'api_status' => 200,
                            'message' => 'QR code generated successfully',
                            'qr_code' => $qr_code,
                            'join_url' => $join_url,
                            'group_id' => $group_id,
                            'group_name' => $group_data['group_name']
                        );

                        error_log("🔲 Group {$group_id}: User {$user_id} generated QR code: {$qr_code}");
                    }
                }
            }
        }
    }
}

if ($error_code > 0) {
    $response_data = array(
        'api_status' => 400,
        'error_code' => $error_code,
        'error_message' => $error_message
    );
}

echo json_encode($response_data);
?>
