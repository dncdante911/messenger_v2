<?php
// +------------------------------------------------------------------------+
// | 🔧 GROUP MANAGEMENT: Запросы на вступление, настройки, приватность
// | Endpoint: /api/v2/endpoints/group_management.php
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
    }
}

if ($error_code == 0) {
    $type = !empty($_POST['type']) ? $_POST['type'] : '';

    switch ($type) {
        // ==================== JOIN REQUESTS ====================

        case 'get_join_requests':
            $group_id = (int)($_POST['group_id'] ?? 0);
            if ($group_id < 1) {
                $response_data = array('api_status' => 400, 'error_message' => 'group_id is required');
                break;
            }

            // Verify user is admin
            if (!isGroupAdmin($sqlConnect, $group_id, $user_id)) {
                $response_data = array('api_status' => 403, 'error_message' => 'Only admins can view join requests');
                break;
            }

            $query = mysqli_query($sqlConnect, "
                SELECT
                    jr.id,
                    jr.group_id,
                    jr.user_id,
                    u.username,
                    CONCAT(u.first_name, ' ', u.last_name) AS user_name,
                    u.avatar AS user_avatar,
                    jr.message,
                    jr.status,
                    jr.created_time
                FROM Wo_GroupJoinRequests jr
                LEFT JOIN Wo_Users u ON u.user_id = jr.user_id
                WHERE jr.group_id = {$group_id}
                AND jr.status = 'pending'
                ORDER BY jr.created_time DESC
            ");

            $requests = array();
            while ($row = mysqli_fetch_assoc($query)) {
                $requests[] = $row;
            }

            $response_data = array(
                'api_status' => 200,
                'join_requests' => $requests
            );
            break;

        case 'approve_join_request':
            $request_id = (int)($_POST['request_id'] ?? 0);
            if ($request_id < 1) {
                $response_data = array('api_status' => 400, 'error_message' => 'request_id is required');
                break;
            }

            // Get request info
            $req_query = mysqli_query($sqlConnect, "
                SELECT group_id, user_id FROM Wo_GroupJoinRequests
                WHERE id = {$request_id} AND status = 'pending'
            ");

            if (mysqli_num_rows($req_query) == 0) {
                $response_data = array('api_status' => 404, 'error_message' => 'Request not found or already processed');
                break;
            }

            $request_data = mysqli_fetch_assoc($req_query);
            $group_id = $request_data['group_id'];
            $new_user_id = $request_data['user_id'];

            // Verify admin rights
            if (!isGroupAdmin($sqlConnect, $group_id, $user_id)) {
                $response_data = array('api_status' => 403, 'error_message' => 'Only admins can approve requests');
                break;
            }

            $time = time();

            // Update request status
            mysqli_query($sqlConnect, "
                UPDATE Wo_GroupJoinRequests
                SET status = 'approved', reviewed_by = {$user_id}, reviewed_time = {$time}
                WHERE id = {$request_id}
            ");

            // Add user to group
            mysqli_query($sqlConnect, "
                INSERT INTO Wo_GroupChatUsers (group_id, user_id, role, admin, time)
                VALUES ({$group_id}, {$new_user_id}, 'member', '0', {$time})
            ");

            $response_data = array(
                'api_status' => 200,
                'message' => 'Join request approved successfully'
            );
            break;

        case 'reject_join_request':
            $request_id = (int)($_POST['request_id'] ?? 0);
            if ($request_id < 1) {
                $response_data = array('api_status' => 400, 'error_message' => 'request_id is required');
                break;
            }

            // Get request info
            $req_query = mysqli_query($sqlConnect, "
                SELECT group_id FROM Wo_GroupJoinRequests
                WHERE id = {$request_id} AND status = 'pending'
            ");

            if (mysqli_num_rows($req_query) == 0) {
                $response_data = array('api_status' => 404, 'error_message' => 'Request not found or already processed');
                break;
            }

            $request_data = mysqli_fetch_assoc($req_query);
            $group_id = $request_data['group_id'];

            // Verify admin rights
            if (!isGroupAdmin($sqlConnect, $group_id, $user_id)) {
                $response_data = array('api_status' => 403, 'error_message' => 'Only admins can reject requests');
                break;
            }

            $time = time();
            mysqli_query($sqlConnect, "
                UPDATE Wo_GroupJoinRequests
                SET status = 'rejected', reviewed_by = {$user_id}, reviewed_time = {$time}
                WHERE id = {$request_id}
            ");

            $response_data = array(
                'api_status' => 200,
                'message' => 'Join request rejected'
            );
            break;

        // ==================== GROUP SETTINGS ====================

        case 'update_settings':
            $group_id = (int)($_POST['group_id'] ?? 0);
            if ($group_id < 1) {
                $response_data = array('api_status' => 400, 'error_message' => 'group_id is required');
                break;
            }

            // Verify admin rights
            if (!isGroupAdmin($sqlConnect, $group_id, $user_id)) {
                $response_data = array('api_status' => 403, 'error_message' => 'Only admins can update settings');
                break;
            }

            // Check if settings columns exist, add if not
            ensureGroupSettingsColumns($sqlConnect);

            $updates = array();

            // Privacy
            if (isset($_POST['is_private'])) {
                $is_private = $_POST['is_private'] == '1' ? 1 : 0;
                $updates[] = "is_private = {$is_private}";
            }

            // Slow mode (seconds between messages)
            if (isset($_POST['slow_mode_seconds'])) {
                $slow_mode = (int)$_POST['slow_mode_seconds'];
                $updates[] = "slow_mode_seconds = {$slow_mode}";
            }

            // History for new members
            if (isset($_POST['history_visible'])) {
                $history_visible = $_POST['history_visible'] == '1' ? 1 : 0;
                $updates[] = "history_visible_for_new_members = {$history_visible}";
            }

            // Anti-spam
            if (isset($_POST['anti_spam_enabled'])) {
                $anti_spam = $_POST['anti_spam_enabled'] == '1' ? 1 : 0;
                $updates[] = "anti_spam_enabled = {$anti_spam}";
            }

            // Max messages per minute (anti-spam)
            if (isset($_POST['max_messages_per_minute'])) {
                $max_msg = (int)$_POST['max_messages_per_minute'];
                $updates[] = "max_messages_per_minute = {$max_msg}";
            }

            // Member permissions
            if (isset($_POST['allow_members_send_media'])) {
                $allow_media = $_POST['allow_members_send_media'] == '1' ? 1 : 0;
                $updates[] = "allow_members_send_media = {$allow_media}";
            }

            if (isset($_POST['allow_members_send_links'])) {
                $allow_links = $_POST['allow_members_send_links'] == '1' ? 1 : 0;
                $updates[] = "allow_members_send_links = {$allow_links}";
            }

            if (isset($_POST['allow_members_send_stickers'])) {
                $allow_stickers = $_POST['allow_members_send_stickers'] == '1' ? 1 : 0;
                $updates[] = "allow_members_send_stickers = {$allow_stickers}";
            }

            if (isset($_POST['allow_members_invite'])) {
                $allow_invite = $_POST['allow_members_invite'] == '1' ? 1 : 0;
                $updates[] = "allow_members_invite = {$allow_invite}";
            }

            if (empty($updates)) {
                $response_data = array('api_status' => 400, 'error_message' => 'No settings to update');
                break;
            }

            $update_sql = "UPDATE Wo_GroupChat SET " . implode(', ', $updates) . " WHERE group_id = {$group_id}";

            if (mysqli_query($sqlConnect, $update_sql)) {
                $response_data = array(
                    'api_status' => 200,
                    'message' => 'Settings updated successfully'
                );
            } else {
                $response_data = array(
                    'api_status' => 500,
                    'error_message' => 'Failed to update settings: ' . mysqli_error($sqlConnect)
                );
            }
            break;

        case 'get_settings':
            $group_id = (int)($_POST['group_id'] ?? 0);
            if ($group_id < 1) {
                $response_data = array('api_status' => 400, 'error_message' => 'group_id is required');
                break;
            }

            ensureGroupSettingsColumns($sqlConnect);

            $query = mysqli_query($sqlConnect, "
                SELECT
                    group_id,
                    is_private,
                    slow_mode_seconds,
                    history_visible_for_new_members,
                    anti_spam_enabled,
                    max_messages_per_minute,
                    allow_members_send_media,
                    allow_members_send_links,
                    allow_members_send_stickers,
                    allow_members_invite
                FROM Wo_GroupChat
                WHERE group_id = {$group_id}
            ");

            if (mysqli_num_rows($query) == 0) {
                $response_data = array('api_status' => 404, 'error_message' => 'Group not found');
                break;
            }

            $settings = mysqli_fetch_assoc($query);

            // Convert to booleans
            $settings['is_private'] = (bool)$settings['is_private'];
            $settings['history_visible_for_new_members'] = (bool)$settings['history_visible_for_new_members'];
            $settings['anti_spam_enabled'] = (bool)$settings['anti_spam_enabled'];
            $settings['allow_members_send_media'] = (bool)$settings['allow_members_send_media'];
            $settings['allow_members_send_links'] = (bool)$settings['allow_members_send_links'];
            $settings['allow_members_send_stickers'] = (bool)$settings['allow_members_send_stickers'];
            $settings['allow_members_invite'] = (bool)$settings['allow_members_invite'];
            $settings['slow_mode_seconds'] = (int)$settings['slow_mode_seconds'];
            $settings['max_messages_per_minute'] = (int)$settings['max_messages_per_minute'];

            $response_data = array(
                'api_status' => 200,
                'settings' => $settings
            );
            break;

        // ==================== GROUP STATISTICS ====================

        case 'get_statistics':
            $group_id = (int)($_POST['group_id'] ?? 0);
            if ($group_id < 1) {
                $response_data = array('api_status' => 400, 'error_message' => 'group_id is required');
                break;
            }

            // Get members count
            $members_query = mysqli_query($sqlConnect, "
                SELECT COUNT(*) as count FROM Wo_GroupChatUsers WHERE group_id = {$group_id}
            ");
            $members_count = mysqli_fetch_assoc($members_query)['count'];

            // Get messages count
            $messages_query = mysqli_query($sqlConnect, "
                SELECT COUNT(*) as count FROM Wo_Messages WHERE group_id = {$group_id}
            ");
            $messages_count = mysqli_fetch_assoc($messages_query)['count'];

            // Get messages today
            $today_start = strtotime('today');
            $today_messages_query = mysqli_query($sqlConnect, "
                SELECT COUNT(*) as count FROM Wo_Messages
                WHERE group_id = {$group_id} AND time >= {$today_start}
            ");
            $today_messages = mysqli_fetch_assoc($today_messages_query)['count'];

            // Get new members this week
            $week_start = strtotime('-7 days');
            $new_members_query = mysqli_query($sqlConnect, "
                SELECT COUNT(*) as count FROM Wo_GroupChatUsers
                WHERE group_id = {$group_id} AND time >= {$week_start}
            ");
            $new_members_week = mysqli_fetch_assoc($new_members_query)['count'];

            // Get admins count
            $admins_query = mysqli_query($sqlConnect, "
                SELECT COUNT(*) as count FROM Wo_GroupChatUsers
                WHERE group_id = {$group_id} AND role IN ('owner', 'admin')
            ");
            $admins_count = mysqli_fetch_assoc($admins_query)['count'];

            // Top contributors (last 30 days)
            $month_start = strtotime('-30 days');
            $top_contributors_query = mysqli_query($sqlConnect, "
                SELECT
                    m.from_id AS user_id,
                    u.username,
                    CONCAT(u.first_name, ' ', u.last_name) AS name,
                    u.avatar,
                    COUNT(*) AS messages_count
                FROM Wo_Messages m
                LEFT JOIN Wo_Users u ON u.user_id = m.from_id
                WHERE m.group_id = {$group_id} AND m.time >= {$month_start}
                GROUP BY m.from_id
                ORDER BY messages_count DESC
                LIMIT 10
            ");

            $top_contributors = array();
            while ($row = mysqli_fetch_assoc($top_contributors_query)) {
                $top_contributors[] = $row;
            }

            $response_data = array(
                'api_status' => 200,
                'statistics' => array(
                    'members_count' => (int)$members_count,
                    'messages_count' => (int)$messages_count,
                    'messages_today' => (int)$today_messages,
                    'new_members_week' => (int)$new_members_week,
                    'admins_count' => (int)$admins_count,
                    'top_contributors' => $top_contributors
                )
            );
            break;

        default:
            $response_data = array(
                'api_status' => 400,
                'error_message' => 'Invalid type: ' . $type
            );
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

// ==================== HELPER FUNCTIONS ====================

function isGroupAdmin($sqlConnect, $group_id, $user_id) {
    // Check if user is group owner
    $owner_query = mysqli_query($sqlConnect, "
        SELECT user_id FROM Wo_GroupChat WHERE group_id = {$group_id} AND user_id = {$user_id}
    ");
    if (mysqli_num_rows($owner_query) > 0) return true;

    // Check if user has admin role
    $admin_query = mysqli_query($sqlConnect, "
        SELECT role FROM Wo_GroupChatUsers
        WHERE group_id = {$group_id} AND user_id = {$user_id} AND role IN ('owner', 'admin')
    ");
    return mysqli_num_rows($admin_query) > 0;
}

function ensureGroupSettingsColumns($sqlConnect) {
    $columns = array(
        'is_private' => "TINYINT(1) DEFAULT 0",
        'slow_mode_seconds' => "INT DEFAULT 0",
        'history_visible_for_new_members' => "TINYINT(1) DEFAULT 1",
        'anti_spam_enabled' => "TINYINT(1) DEFAULT 0",
        'max_messages_per_minute' => "INT DEFAULT 20",
        'allow_members_send_media' => "TINYINT(1) DEFAULT 1",
        'allow_members_send_links' => "TINYINT(1) DEFAULT 1",
        'allow_members_send_stickers' => "TINYINT(1) DEFAULT 1",
        'allow_members_invite' => "TINYINT(1) DEFAULT 0"
    );

    foreach ($columns as $column => $definition) {
        $check = mysqli_query($sqlConnect, "SHOW COLUMNS FROM Wo_GroupChat LIKE '{$column}'");
        if (mysqli_num_rows($check) == 0) {
            mysqli_query($sqlConnect, "ALTER TABLE Wo_GroupChat ADD COLUMN {$column} {$definition}");
        }
    }
}
?>
