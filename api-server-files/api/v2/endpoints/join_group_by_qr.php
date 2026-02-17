<?php
// +------------------------------------------------------------------------+
// | 🔲 GROUPS: Приєднання до групи чату за QR кодом (Wo_GroupChat)
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
        $qr_code = (!empty($_POST['qr_code'])) ? mysqli_real_escape_string($sqlConnect, $_POST['qr_code']) : '';

        if (empty($qr_code)) {
            $error_code = 5;
            $error_message = 'qr_code is required';
        } else {
            // Знайти групу за QR кодом в Wo_GroupChat
            $group_query = mysqli_query($sqlConnect, "
                SELECT g.group_id, g.user_id, g.group_name, g.description,
                       g.avatar, g.is_private
                FROM Wo_GroupChat g
                WHERE g.qr_code = '{$qr_code}'
                AND (g.type = 'group' OR g.type IS NULL OR g.type = '')
            ");

            if (!$group_query || mysqli_num_rows($group_query) == 0) {
                $error_code = 6;
                $error_message = 'Invalid QR code or group not found';
            } else {
                $group_data = mysqli_fetch_assoc($group_query);
                $group_id = $group_data['group_id'];

                // Перевірити чи користувач вже є членом групи (Wo_GroupChatUsers)
                $member_query = mysqli_query($sqlConnect, "
                    SELECT id
                    FROM Wo_GroupChatUsers
                    WHERE group_id = {$group_id}
                    AND user_id = {$user_id}
                ");

                if (mysqli_num_rows($member_query) > 0) {
                    $error_code = 7;
                    $error_message = 'You are already a member of this group';
                } else {
                    // Перевірити чи група приватна
                    $is_private = !empty($group_data['is_private']);

                    if ($is_private) {
                        // Для приватних груп - створити запит на вступ
                        // Перевірити чи вже є запит
                        $request_query = mysqli_query($sqlConnect, "
                            SELECT id FROM Wo_GroupJoinRequests
                            WHERE group_id = {$group_id}
                            AND user_id = {$user_id}
                            AND status = 'pending'
                        ");

                        if (mysqli_num_rows($request_query) > 0) {
                            $error_code = 9;
                            $error_message = 'You already have a pending join request';
                        } else {
                            // Перевірити чи існує таблиця Wo_GroupJoinRequests
                            $check_table = mysqli_query($sqlConnect, "SHOW TABLES LIKE 'Wo_GroupJoinRequests'");
                            if (mysqli_num_rows($check_table) == 0) {
                                // Створити таблицю якщо не існує
                                mysqli_query($sqlConnect, "
                                    CREATE TABLE Wo_GroupJoinRequests (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        group_id BIGINT NOT NULL,
                                        user_id BIGINT NOT NULL,
                                        message TEXT,
                                        status ENUM('pending', 'approved', 'rejected') DEFAULT 'pending',
                                        created_time BIGINT,
                                        reviewed_by BIGINT,
                                        reviewed_time BIGINT,
                                        INDEX(group_id),
                                        INDEX(user_id),
                                        INDEX(status)
                                    )
                                ");
                            }

                            // Створити запит на вступ
                            $time = time();
                            $insert_request = mysqli_query($sqlConnect, "
                                INSERT INTO Wo_GroupJoinRequests
                                (group_id, user_id, status, created_time)
                                VALUES ({$group_id}, {$user_id}, 'pending', {$time})
                            ");

                            if ($insert_request) {
                                $response_data = array(
                                    'api_status' => 200,
                                    'message' => 'Join request sent successfully. Waiting for admin approval.',
                                    'status' => 'pending',
                                    'group' => array(
                                        'id' => $group_data['group_id'],
                                        'name' => $group_data['group_name'],
                                        'description' => $group_data['description'],
                                        'avatar' => $group_data['avatar'],
                                        'is_private' => true
                                    )
                                );
                                error_log("🔲 Group {$group_id}: User {$user_id} sent join request via QR");
                            } else {
                                $error_code = 10;
                                $error_message = 'Failed to send join request';
                            }
                        }
                    } else {
                        // Для публічних груп - додати користувача напряму
                        $time = time();
                        $join_query = mysqli_query($sqlConnect, "
                            INSERT INTO Wo_GroupChatUsers
                            (group_id, user_id, role, admin, time)
                            VALUES ({$group_id}, {$user_id}, 'member', '0', {$time})
                        ");

                        if ($join_query) {
                            $response_data = array(
                                'api_status' => 200,
                                'message' => 'Successfully joined the group',
                                'status' => 'joined',
                                'group' => array(
                                    'id' => $group_data['group_id'],
                                    'name' => $group_data['group_name'],
                                    'description' => $group_data['description'],
                                    'avatar' => $group_data['avatar'],
                                    'is_private' => false
                                )
                            );
                            error_log("🔲 Group {$group_id}: User {$user_id} joined via QR code: {$qr_code}");
                        } else {
                            $error_code = 8;
                            $error_message = 'Failed to join group: ' . mysqli_error($sqlConnect);
                        }
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
