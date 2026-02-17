<?php
/**
 * API Endpoint: Get User Rating
 * Отримання рейтингу користувача та історії оцінок
 */

// Load config if not already loaded (for direct access to this file)
if (!isset($db) || !isset($sqlConnect)) {
    require_once(__DIR__ . '/../config.php');
}

header('Content-Type: application/json; charset=UTF-8');

// Initialize response
$error_code = 0;
$error_message = '';
$data = [];

// Get user_id - either from router or validate access_token directly
$requester_id = $wo['user']['user_id'] ?? 0;

// If not set by router, validate access_token ourselves
if (empty($requester_id) || $requester_id < 1) {
    $access_token = $_GET['access_token'] ?? $_POST['access_token'] ?? '';
    if (!empty($access_token) && isset($db)) {
        $requester_id = validateAccessToken($pdo, $access_token);
        if ($requester_id) {
            $wo['user']['user_id'] = $requester_id;
            $wo['loggedin'] = true;
        }
    }
}

if (empty($requester_id) || !is_numeric($requester_id) || $requester_id < 1) {
    $error_code = 4;
    $error_message = 'Invalid access_token';
    http_response_code(401);
}

if ($error_code == 0) {
    // Get user_id parameter
        $user_id = (!empty($_POST['user_id']) && is_numeric($_POST['user_id'])) ? (int)$_POST['user_id'] : 0;
        $include_details = (!empty($_POST['include_details']) && $_POST['include_details'] == '1');

        if ($user_id < 1) {
            $error_code = 5;
            $error_message = 'user_id is required';
            http_response_code(400);
        } else {
            // Get user data
            $user = Wo_UserData($user_id);

            if (empty($user)) {
                $error_code = 6;
                $error_message = 'User not found';
                http_response_code(404);
            } else {
                // Get rating summary
                $rating_summary = [
                    'user_id' => $user_id,
                    'username' => $user['username'],
                    'name' => trim(($user['first_name'] ?? '') . ' ' . ($user['last_name'] ?? '')) ?: $user['username'],
                    'avatar' => Wo_GetMedia($user['avatar'] ?? ''),
                    'likes' => (int)($user['rating_likes'] ?? 0),
                    'dislikes' => (int)($user['rating_dislikes'] ?? 0),
                    'score' => (float)($user['rating_score'] ?? 0),
                    'trust_level' => $user['trust_level'] ?? 'neutral',
                    'total_ratings' => ((int)($user['rating_likes'] ?? 0) + (int)($user['rating_dislikes'] ?? 0))
                ];

                // Add trust level label and color
                switch ($rating_summary['trust_level']) {
                    case 'verified':
                        $rating_summary['trust_level_label'] = 'Перевірений';
                        $rating_summary['trust_level_emoji'] = '✅';
                        $rating_summary['trust_level_color'] = '#4CAF50';
                        break;
                    case 'trusted':
                        $rating_summary['trust_level_label'] = 'Надійний';
                        $rating_summary['trust_level_emoji'] = '⭐';
                        $rating_summary['trust_level_color'] = '#2196F3';
                        break;
                    case 'untrusted':
                        $rating_summary['trust_level_label'] = 'Ненадійний';
                        $rating_summary['trust_level_emoji'] = '⚠️';
                        $rating_summary['trust_level_color'] = '#F44336';
                        break;
                    default:
                        $rating_summary['trust_level_label'] = 'Нейтральний';
                        $rating_summary['trust_level_emoji'] = '🔵';
                        $rating_summary['trust_level_color'] = '#9E9E9E';
                }

                // Calculate percentage
                if ($rating_summary['total_ratings'] > 0) {
                    $rating_summary['like_percentage'] = round(($rating_summary['likes'] / $rating_summary['total_ratings']) * 100, 1);
                    $rating_summary['dislike_percentage'] = round(($rating_summary['dislikes'] / $rating_summary['total_ratings']) * 100, 1);
                } else {
                    $rating_summary['like_percentage'] = 0;
                    $rating_summary['dislike_percentage'] = 0;
                }

                // Check if current user has rated this user
                $my_rating_query = mysqli_query($sqlConnect, "
                    SELECT rating_type, comment, created_at
                    FROM Wo_UserRatings
                    WHERE rater_id = {$requester_id} AND rated_user_id = {$user_id}
                ");

                if (mysqli_num_rows($my_rating_query) > 0) {
                    $my_rating = mysqli_fetch_assoc($my_rating_query);
                    $rating_summary['my_rating'] = [
                        'type' => $my_rating['rating_type'],
                        'comment' => $my_rating['comment'],
                        'created_at' => $my_rating['created_at']
                    ];
                } else {
                    $rating_summary['my_rating'] = null;
                }

                $data = [
                    'api_status' => 200,
                    'rating' => $rating_summary
                ];

                // Include detailed ratings if requested
                if ($include_details) {
                    $ratings_query = mysqli_query($sqlConnect, "
                        SELECT
                            r.id,
                            r.rater_id,
                            r.rating_type,
                            r.comment,
                            r.created_at,
                            u.username,
                            u.first_name,
                            u.last_name,
                            u.avatar
                        FROM Wo_UserRatings r
                        LEFT JOIN Wo_Users u ON u.user_id = r.rater_id
                        WHERE r.rated_user_id = {$user_id}
                        ORDER BY r.created_at DESC
                        LIMIT 50
                    ");

                    $ratings_list = [];
                    while ($row = mysqli_fetch_assoc($ratings_query)) {
                        $ratings_list[] = [
                            'id' => (int)$row['id'],
                            'rater_id' => (int)$row['rater_id'],
                            'rater_username' => $row['username'],
                            'rater_name' => trim(($row['first_name'] ?? '') . ' ' . ($row['last_name'] ?? '')) ?: $row['username'],
                            'rater_avatar' => Wo_GetMedia($row['avatar'] ?? ''),
                            'rating_type' => $row['rating_type'],
                            'comment' => $row['comment'],
                            'created_at' => $row['created_at']
                        ];
                    }

                    $data['ratings_list'] = $ratings_list;
                    $data['ratings_count'] = count($ratings_list);
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
