<?php
// +------------------------------------------------------------------------+
// | API Endpoint: Get Blocked Users
// +------------------------------------------------------------------------+
// | Get list of users blocked by the current user
// +------------------------------------------------------------------------+

// Initialize error variables (these are set by the router)
$error_code = 0;
$error_message = '';
$data = array();

// Validate access token
$access_token = $_GET['access_token'] ?? $_POST['access_token'] ?? '';

if (empty($access_token)) {
    $error_code = 4;
    $error_message = 'Missing access_token';
    http_response_code(401);
} else {
    $user_id = Wo_ValidateAccessToken($access_token);

    if (empty($user_id) || !is_numeric($user_id) || $user_id < 1) {
        $error_code = 4;
        $error_message = 'Invalid access_token';
        http_response_code(401);
    } else {
        // Get list of blocked users
        $blocked_users = array();

        // Query to get blocked users
        $query = mysqli_query($sqlConnect, "
            SELECT u.user_id, u.username, u.first_name, u.last_name, u.avatar
            FROM Wo_Blocks b
            INNER JOIN Wo_Users u ON b.blocked_id = u.user_id
            WHERE b.blocker_id = {$user_id}
            ORDER BY b.time DESC
        ");

        if ($query) {
            while ($user = mysqli_fetch_assoc($query)) {
                $blocked_users[] = array(
                    'user_id' => (int)$user['user_id'],
                    'username' => $user['username'],
                    'name' => trim($user['first_name'] . ' ' . $user['last_name']) ?: $user['username'],
                    'first_name' => $user['first_name'] ?: '',
                    'last_name' => $user['last_name'] ?: '',
                    'avatar' => Wo_GetMedia($user['avatar'])
                );
            }

            $data = array(
                'api_status' => 200,
                'blocked_users' => $blocked_users,
                'count' => count($blocked_users)
            );
        } else {
            $error_code = 5;
            $error_message = 'Database query failed: ' . mysqli_error($sqlConnect);
            http_response_code(500);
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

// Exit to prevent additional output
exit();
?>
