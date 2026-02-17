<?php
/**
 * API Endpoint: Rate User (Like/Dislike)
 * Дозволяє користувачам оцінювати інших користувачів
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
$rater_id = $wo['user']['user_id'] ?? 0;

// If not set by router, validate access_token ourselves
if (empty($rater_id) || $rater_id < 1) {
    $access_token = $_GET['access_token'] ?? $_POST['access_token'] ?? '';
    if (!empty($access_token) && isset($db)) {
        $rater_id = validateAccessToken($pdo, $access_token);
        if ($rater_id) {
            $wo['user']['user_id'] = $rater_id;
            $wo['loggedin'] = true;
        }
    }
}

if (empty($rater_id) || !is_numeric($rater_id) || $rater_id < 1) {
    $error_code = 4;
    $error_message = 'Invalid access_token';
    http_response_code(401);
}

if ($error_code == 0) {
    // Get parameters
        $rated_user_id = (!empty($_POST['user_id']) && is_numeric($_POST['user_id'])) ? (int)$_POST['user_id'] : 0;
        $rating_type = (!empty($_POST['rating_type']) && in_array($_POST['rating_type'], ['like', 'dislike'])) ? $_POST['rating_type'] : '';
        $comment = isset($_POST['comment']) ? Wo_Secure($_POST['comment']) : null;

        // Validation
        if ($rated_user_id < 1) {
            $error_code = 5;
            $error_message = 'user_id is required';
            http_response_code(400);
        } elseif (empty($rating_type)) {
            $error_code = 6;
            $error_message = 'rating_type must be "like" or "dislike"';
            http_response_code(400);
        } elseif ($rater_id == $rated_user_id) {
            $error_code = 7;
            $error_message = 'You cannot rate yourself';
            http_response_code(400);
        } else {
            // Check if rated user exists
            $rated_user = Wo_UserData($rated_user_id);
            if (empty($rated_user)) {
                $error_code = 8;
                $error_message = 'User not found';
                http_response_code(404);
            } else {
                // Check if rating already exists
                $existing_rating_query = mysqli_query($sqlConnect, "
                    SELECT id, rating_type
                    FROM Wo_UserRatings
                    WHERE rater_id = {$rater_id} AND rated_user_id = {$rated_user_id}
                ");

                if (mysqli_num_rows($existing_rating_query) > 0) {
                    // Update existing rating
                    $existing = mysqli_fetch_assoc($existing_rating_query);

                    if ($existing['rating_type'] == $rating_type) {
                        // Same rating - remove it (toggle off)
                        $delete_query = mysqli_query($sqlConnect, "
                            DELETE FROM Wo_UserRatings
                            WHERE id = {$existing['id']}
                        ");

                        if ($delete_query) {
                            $data = [
                                'api_status' => 200,
                                'message' => 'Rating removed successfully',
                                'action' => 'removed',
                                'rating_type' => null
                            ];
                        } else {
                            $error_code = 500;
                            $error_message = 'Database error: ' . mysqli_error($sqlConnect);
                            http_response_code(500);
                        }
                    } else {
                        // Different rating - update it
                        $update_query = mysqli_query($sqlConnect, "
                            UPDATE Wo_UserRatings
                            SET rating_type = '{$rating_type}',
                                comment = " . ($comment ? "'{$comment}'" : "NULL") . ",
                                updated_at = NOW()
                            WHERE id = {$existing['id']}
                        ");

                        if ($update_query) {
                            $data = [
                                'api_status' => 200,
                                'message' => 'Rating updated successfully',
                                'action' => 'updated',
                                'rating_type' => $rating_type
                            ];
                        } else {
                            $error_code = 500;
                            $error_message = 'Database error: ' . mysqli_error($sqlConnect);
                            http_response_code(500);
                        }
                    }
                } else {
                    // Create new rating
                    $insert_query = mysqli_query($sqlConnect, "
                        INSERT INTO Wo_UserRatings (rater_id, rated_user_id, rating_type, comment)
                        VALUES ({$rater_id}, {$rated_user_id}, '{$rating_type}', " . ($comment ? "'{$comment}'" : "NULL") . ")
                    ");

                    if ($insert_query) {
                        $data = [
                            'api_status' => 200,
                            'message' => 'Rating added successfully',
                            'action' => 'added',
                            'rating_type' => $rating_type
                        ];
                    } else {
                        $error_code = 500;
                        $error_message = 'Database error: ' . mysqli_error($sqlConnect);
                        http_response_code(500);
                    }
                }

                // Якщо операція успішна, отримуємо оновлений рейтинг
                if (!empty($data) && $error_code == 0) {
                    // Calculate counts from database directly
                    $likes_query = mysqli_query($sqlConnect, "
                        SELECT COUNT(*) as cnt FROM Wo_UserRatings
                        WHERE rated_user_id = {$rated_user_id} AND rating_type = 'like'
                    ");
                    $likes_count = mysqli_fetch_assoc($likes_query)['cnt'] ?? 0;

                    $dislikes_query = mysqli_query($sqlConnect, "
                        SELECT COUNT(*) as cnt FROM Wo_UserRatings
                        WHERE rated_user_id = {$rated_user_id} AND rating_type = 'dislike'
                    ");
                    $dislikes_count = mysqli_fetch_assoc($dislikes_query)['cnt'] ?? 0;

                    $total_ratings = $likes_count + $dislikes_count;
                    $score = $total_ratings > 0 ? ($likes_count / $total_ratings) * 5.0 : 0;

                    // Determine trust level based on score
                    $trust_level = 'neutral';
                    if ($total_ratings >= 5) {
                        if ($score >= 4.0) $trust_level = 'trusted';
                        else if ($score >= 3.0) $trust_level = 'neutral';
                        else $trust_level = 'untrusted';
                    }

                    // Build user_rating response with my_rating included
                    $data['user_rating'] = [
                        'user_id' => $rated_user_id,
                        'likes' => (int)$likes_count,
                        'dislikes' => (int)$dislikes_count,
                        'score' => (float)$score,
                        'trust_level' => $trust_level,
                        'total_ratings' => (int)$total_ratings,
                        // Include my_rating so UI knows the current user's rating state
                        'my_rating' => ($data['action'] === 'removed') ? null : [
                            'type' => $data['rating_type'],
                            'comment' => $comment,
                            'created_at' => date('Y-m-d H:i:s')
                        ]
                    ];
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
