<?php
/**
 * Shared bootstrap for story endpoints called directly by Android
 * (bypassing the index.php router).
 *
 * Include this at the top of every story endpoint:
 *   require_once(__DIR__ . '/_stories_bootstrap.php');
 *
 * After inclusion the following are guaranteed:
 *   $sqlConnect  – MySQLi connection
 *   $pdoDb       – PDO connection
 *   $wo          – WoWonder global array with $wo['user'] populated
 *   All WoWonder functions (Wo_UserData, Wo_Secure, etc.)
 *   All table constants (T_USER_STORY, T_USER_STORY_MEDIA, etc.)
 */

if (!isset($sqlConnect)) {
    // We are in standalone mode — Android called the endpoint directly
    header('Content-Type: application/json; charset=UTF-8');
    require_once(__DIR__ . '/../config.php');

    $access_token = $_GET['access_token'] ?? $_POST['access_token'] ?? '';
    if (empty($access_token)) {
        http_response_code(401);
        echo json_encode(['api_status' => 401, 'error_message' => 'access_token is required']);
        exit;
    }

    $auth_user_id = validateAccessToken($pdoDb, $access_token);
    if (!$auth_user_id) {
        http_response_code(401);
        echo json_encode(['api_status' => 401, 'error_message' => 'Invalid or expired access_token']);
        exit;
    }

    if (function_exists('Wo_UserData')) {
        $auth_user_data = Wo_UserData($auth_user_id);
        if (!empty($auth_user_data)) {
            $wo['user'] = $auth_user_data;
        }
    }
    if (empty($wo['user']['user_id'])) {
        $wo['user']['user_id'] = $auth_user_id;
    }

    // Create MysqliDb ORM wrapper ($db) used by some WoWonder functions and endpoints
    if (!isset($db)) {
        $mysqlidb_path = __DIR__ . '/../../../assets/libraries/DB/vendor/joshcam/mysqli-database-class/MysqliDb.php';
        if (file_exists($mysqlidb_path)) {
            require_once($mysqlidb_path);
            $db = new MysqliDb($sqlConnect);
        }
    }

    // Flag so endpoints know to echo JSON themselves
    define('STORIES_STANDALONE', true);
} else {
    if (!defined('STORIES_STANDALONE')) {
        define('STORIES_STANDALONE', false);
    }
}

/**
 * Helper: build a clean user_data array for story responses.
 * Strips sensitive fields that should never go to the client.
 */
function stories_build_user_data($user_id) {
    if (!function_exists('Wo_UserData')) {
        return ['user_id' => (int)$user_id];
    }
    $u = Wo_UserData($user_id);
    if (empty($u)) {
        return ['user_id' => (int)$user_id];
    }
    return [
        'user_id'    => (int)($u['user_id'] ?? $user_id),
        'username'   => $u['username'] ?? '',
        'first_name' => $u['first_name'] ?? ($u['name'] ?? ''),
        'last_name'  => $u['last_name'] ?? '',
        'avatar'     => $u['avatar'] ?? '',
        'avatar_org' => $u['avatar_org'] ?? ($u['avatar'] ?? ''),
        'is_pro'     => (int)($u['is_pro'] ?? 0),
        'verified'   => (int)($u['verified'] ?? 0),
    ];
}

/**
 * Helper: fetch media items for a story from Wo_UserStoryMedia.
 * Returns arrays: ['images' => [...], 'videos' => [...], 'mediaItems' => [...]]
 */
function stories_fetch_media($sqlConnect, $story_id) {
    $images = [];
    $videos = [];
    $mediaItems = [];

    $story_id = (int)$story_id;
    $q = mysqli_query($sqlConnect, "SELECT * FROM " . T_USER_STORY_MEDIA . " WHERE story_id = {$story_id} ORDER BY id ASC");
    if ($q && mysqli_num_rows($q) > 0) {
        while ($m = mysqli_fetch_assoc($q)) {
            $item = [
                'id'       => (int)$m['id'],
                'story_id' => (int)$m['story_id'],
                'type'     => $m['type'] ?? 'image',
                'filename' => $m['filename'] ?? '',
                'expire'   => !empty($m['expire']) ? (int)$m['expire'] : 0,
                'duration' => (int)($m['duration'] ?? 0),
            ];
            $mediaItems[] = $item;
            if ($item['type'] === 'video') {
                $videos[] = $item;
            } else {
                $images[] = $item;
            }
        }
    }
    return ['images' => $images, 'videos' => $videos, 'mediaItems' => $mediaItems];
}

/**
 * Helper: check if the current user has viewed a story.
 */
function stories_is_viewed($sqlConnect, $story_id, $user_id) {
    $story_id = (int)$story_id;
    $user_id  = (int)$user_id;
    $q = mysqli_query($sqlConnect, "SELECT id FROM " . T_STORY_SEEN . " WHERE story_id = {$story_id} AND user_id = {$user_id} LIMIT 1");
    return ($q && mysqli_num_rows($q) > 0) ? 1 : 0;
}

/**
 * Helper: count views for a story.
 */
function stories_view_count($sqlConnect, $story_id) {
    $story_id = (int)$story_id;
    $q = mysqli_query($sqlConnect, "SELECT COUNT(*) AS cnt FROM " . T_STORY_SEEN . " WHERE story_id = {$story_id}");
    if ($q && $row = mysqli_fetch_assoc($q)) {
        return (int)$row['cnt'];
    }
    return 0;
}

/**
 * Helper: get reaction counts and user's own reaction for a story.
 */
function stories_get_reactions($sqlConnect, $story_id, $user_id) {
    $story_id = (int)$story_id;
    $user_id  = (int)$user_id;

    $result = [
        'like' => 0, 'love' => 0, 'haha' => 0,
        'wow' => 0, 'sad' => 0, 'angry' => 0,
        'is_reacted' => false, 'type' => null,
    ];

    // Count reactions by type
    $q = mysqli_query($sqlConnect, "SELECT reaction, COUNT(*) as cnt FROM Wo_StoryReactions WHERE story_id = {$story_id} GROUP BY reaction");
    if ($q) {
        while ($row = mysqli_fetch_assoc($q)) {
            $r = strtolower($row['reaction']);
            if (isset($result[$r])) {
                $result[$r] = (int)$row['cnt'];
            }
        }
    }

    // Check user's own reaction
    $q2 = mysqli_query($sqlConnect, "SELECT reaction FROM Wo_StoryReactions WHERE story_id = {$story_id} AND user_id = {$user_id} LIMIT 1");
    if ($q2 && $row2 = mysqli_fetch_assoc($q2)) {
        $result['is_reacted'] = true;
        $result['type'] = $row2['reaction'];
    }

    return $result;
}

/**
 * Helper: build a full story array for API response.
 */
function stories_build_story($sqlConnect, $story_row, $logged_user_id, $user_data = null) {
    $story_id    = (int)$story_row['id'];
    $story_uid   = (int)$story_row['user_id'];
    $current_time = time();

    if ($user_data === null) {
        $user_data = stories_build_user_data($story_uid);
    }

    $posted_ts = !empty($story_row['posted']) ? (int)$story_row['posted'] : $current_time;
    $expire_ts = !empty($story_row['expire']) ? (int)$story_row['expire'] : ($posted_ts + 86400);

    $media = stories_fetch_media($sqlConnect, $story_id);

    $story = [
        'id'            => $story_id,
        'user_id'       => $story_uid,
        'page_id'       => !empty($story_row['page_id']) ? (int)$story_row['page_id'] : null,
        'title'         => $story_row['title'] ?? '',
        'description'   => $story_row['description'] ?? '',
        'posted'        => $posted_ts,
        'expire'        => $expire_ts,
        'thumbnail'     => $story_row['thumbnail'] ?? '',
        'user_data'     => $user_data,
        'images'        => $media['images'],
        'videos'        => $media['videos'],
        'mediaItems'    => $media['mediaItems'],
        'is_owner'      => ($story_uid === (int)$logged_user_id),
        'is_viewed'     => stories_is_viewed($sqlConnect, $story_id, $logged_user_id),
        'view_count'    => stories_view_count($sqlConnect, $story_id),
        'comment_count' => (int)($story_row['comment_count'] ?? 0),
        'reaction'      => stories_get_reactions($sqlConnect, $story_id, $logged_user_id),
    ];

    return $story;
}

/**
 * Helper: output JSON in standalone mode.
 * In router mode the WoWonder shutdown handler outputs $response_data.
 */
function stories_output($response_data) {
    if (STORIES_STANDALONE) {
        echo json_encode($response_data);
        exit;
    }
}
