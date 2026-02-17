<?php
/**
 * API Endpoint: Get Channel Subscribers
 * Returns list of channel subscribers with proper name fallback
 */

// Initialize response
$error_code = 0;
$error_message = '';
$data = [];

// User is already authenticated by index.php router
$user_id = $wo['user']['user_id'] ?? 0;

if (empty($user_id) || !is_numeric($user_id) || $user_id < 1) {
    $error_code = 4;
    $error_message = 'Invalid access_token';
    http_response_code(401);
}

if ($error_code == 0) {
    // Get channel_id parameter
    $channel_id = (!empty($_POST['channel_id']) && is_numeric($_POST['channel_id'])) ? (int)$_POST['channel_id'] : 0;
    $limit = (!empty($_POST['limit']) && is_numeric($_POST['limit']) && $_POST['limit'] > 0) ? (int)$_POST['limit'] : 100;
    $offset = (!empty($_POST['offset']) && is_numeric($_POST['offset']) && $_POST['offset'] >= 0) ? (int)$_POST['offset'] : 0;

    if ($channel_id < 1) {
        $error_code = 5;
        $error_message = 'channel_id is required';
        http_response_code(400);
    } else {
        // Check if channel exists
        $channel_query = mysqli_query($sqlConnect, "
            SELECT g.id, g.group_id, g.group_name
            FROM " . T_GROUPCHAT . " g
            WHERE g.id = {$channel_id}
        ");

        if (mysqli_num_rows($channel_query) == 0) {
            $error_code = 6;
            $error_message = 'Channel not found';
            http_response_code(404);
        } else {
            // Get channel subscribers with proper name fallback
            $subscribers_query = mysqli_query($sqlConnect, "
                SELECT
                    gcu.user_id AS id,
                    gcu.user_id AS userId,
                    u.username,
                    CASE
                        WHEN TRIM(CONCAT(IFNULL(u.first_name, ''), ' ', IFNULL(u.last_name, ''))) != ''
                        THEN TRIM(CONCAT(u.first_name, ' ', u.last_name))
                        ELSE u.username
                    END AS name,
                    u.avatar,
                    u.avatar_org AS avatarUrl,
                    gcu.role,
                    gcu.last_seen,
                    gcu.muted AS isMuted
                FROM " . T_GROUPCHATUSERS . " gcu
                LEFT JOIN " . T_USERS . " u ON u.user_id = gcu.user_id
                WHERE gcu.group_id = {$channel_id}
                ORDER BY
                    CASE gcu.role
                        WHEN 'owner' THEN 1
                        WHEN 'admin' THEN 2
                        WHEN 'moderator' THEN 3
                        ELSE 4
                    END,
                    gcu.id ASC
                LIMIT {$limit} OFFSET {$offset}
            ");

            if ($subscribers_query) {
                $subscribers = [];
                while ($row = mysqli_fetch_assoc($subscribers_query)) {
                    // Process avatar URL
                    if (!empty($row['avatar'])) {
                        $row['avatarUrl'] = Wo_GetMedia($row['avatar']);
                    } else {
                        $row['avatarUrl'] = null;
                    }

                    // Convert muted to boolean
                    $row['isMuted'] = ($row['isMuted'] == 1);

                    // Remove fields not needed by client
                    if (isset($non_allowed) && is_array($non_allowed)) {
                        foreach ($non_allowed as $field) {
                            unset($row[$field]);
                        }
                    }

                    $subscribers[] = $row;
                }

                $data = [
                    'api_status' => 200,
                    'subscribers' => $subscribers,
                    'total' => count($subscribers)
                ];
            } else {
                $error_code = 7;
                $error_message = 'Database error: ' . mysqli_error($sqlConnect);
                http_response_code(500);
            }
        }
    }
}

// Send response
if ($error_code > 0) {
    http_response_code($error_code >= 100 ? $error_code : 400);
    echo json_encode([
        'api_status' => $error_code >= 100 ? $error_code : 400,
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

// Exit to prevent api-v2.php from echoing additional output
exit();
?>
