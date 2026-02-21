<?php
/**
 * WorldMates Messenger - Channels API Endpoint
 * Версія: 2.0 (адаптована до існуючої БД Wondertage/WO)
 *
 * Використовує існуючі таблиці:
 * - Wo_GroupChat (type='channel')
 * - Wo_GroupChatUsers (підписники)
 * - Wo_Messages (пости)
 * - wo_reactions (реакції)
 * - Wo_MessageComments (коментарі)
 */

require_once(__DIR__ . '/config.php');
require_once(__DIR__ . '/crypto_helper.php');

// Налаштування логування
error_reporting(E_ALL);
ini_set('display_errors', 0);
ini_set('log_errors', 1);
ini_set('error_log', '/var/www/www-root/data/www/worldmates.club/api/v2/logs/php_errors.log');

// Функція логування для каналів
if (!function_exists('logChannelMessage')) {
    function logChannelMessage($message, $level = 'INFO') {
        $log_file = '/var/www/www-root/data/www/worldmates.club/api/v2/logs/channels.log';
        $timestamp = date('Y-m-d H:i:s');
        $log_entry = "[{$timestamp}] [{$level}] {$message}\n";
        @file_put_contents($log_file, $log_entry, FILE_APPEND);
    }
}

header('Content-Type: application/json');

// Отримуємо дані запиту
$request_method = $_SERVER['REQUEST_METHOD'];

// Підтримка як JSON, так і form-encoded даних
$content_type = $_SERVER['CONTENT_TYPE'] ?? '';
if (strpos($content_type, 'application/json') !== false) {
    $data = json_decode(file_get_contents('php://input'), true) ?? [];
} else {
    // Для application/x-www-form-urlencoded використовуємо $_POST
    $data = $_POST;
}

// Access token для автентифікації
// Для multipart/form-data access_token може бути в $_POST
$access_token = $data['access_token'] ?? $_GET['access_token'] ?? $_POST['access_token'] ?? null;

if (!$access_token) {
    echo json_encode(['api_status' => 400, 'error_message' => 'Access token is required']);
    exit;
}

// Валідація токену та отримання user_id
$user_id = validateAccessToken($pdoDb, $access_token);
if (!$user_id) {
    logChannelMessage("Invalid access token attempt", 'WARNING');
    echo json_encode(['api_status' => 401, 'error_message' => 'Invalid access token']);
    exit;
}

// Автоміграція: додаємо відсутні колонки для каналів при першому запуску
$ch_migration_flag = sys_get_temp_dir() . '/wm_channels_migration_v2_done';
if (!file_exists($ch_migration_flag)) {
    try {
        // Wo_GroupChat: додаємо колонки потрібні для каналів
        $gc_columns = [];
        $result = $pdoDb->query("SHOW COLUMNS FROM Wo_GroupChat");
        while ($row = $result->fetch(PDO::FETCH_ASSOC)) {
            $gc_columns[] = $row['Field'];
        }
        $ch_migrations = [
            'username' => "ALTER TABLE Wo_GroupChat ADD COLUMN `username` VARCHAR(255) DEFAULT NULL",
            'description' => "ALTER TABLE Wo_GroupChat ADD COLUMN `description` TEXT DEFAULT NULL",
            'is_private' => "ALTER TABLE Wo_GroupChat ADD COLUMN `is_private` TINYINT(1) DEFAULT 0",
            'category' => "ALTER TABLE Wo_GroupChat ADD COLUMN `category` VARCHAR(100) DEFAULT NULL",
            'settings' => "ALTER TABLE Wo_GroupChat ADD COLUMN `settings` TEXT DEFAULT NULL",
            'subscribers_count' => "ALTER TABLE Wo_GroupChat ADD COLUMN `subscribers_count` INT(11) DEFAULT 0",
            'posts_count' => "ALTER TABLE Wo_GroupChat ADD COLUMN `posts_count` INT(11) DEFAULT 0",
            'is_verified' => "ALTER TABLE Wo_GroupChat ADD COLUMN `is_verified` TINYINT(1) DEFAULT 0",
        ];
        foreach ($ch_migrations as $col => $sql) {
            if (!in_array($col, $gc_columns)) {
                try { $pdoDb->exec($sql); logChannelMessage("Auto-migration: added $col to Wo_GroupChat"); } catch (Exception $e) {}
            }
        }

        // Wo_GroupChatUsers: додаємо role якщо немає
        $gcu_columns = [];
        $result = $pdoDb->query("SHOW COLUMNS FROM Wo_GroupChatUsers");
        while ($row = $result->fetch(PDO::FETCH_ASSOC)) {
            $gcu_columns[] = $row['Field'];
        }
        if (!in_array('role', $gcu_columns)) {
            try { $pdoDb->exec("ALTER TABLE Wo_GroupChatUsers ADD COLUMN `role` VARCHAR(20) DEFAULT 'member'"); } catch (Exception $e) {}
        }

        // Wo_MessageComments
        $pdoDb->exec("CREATE TABLE IF NOT EXISTS `Wo_MessageComments` (
            `id` INT(11) NOT NULL AUTO_INCREMENT, `message_id` INT(11) NOT NULL, `user_id` INT(11) NOT NULL,
            `text` TEXT NOT NULL, `time` INT(11) NOT NULL, `edited_time` INT(11) DEFAULT NULL,
            `reply_to_comment_id` INT(11) DEFAULT NULL,
            PRIMARY KEY (`id`), KEY `idx_message` (`message_id`), KEY `idx_user` (`user_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        // Wo_MessageViews
        $pdoDb->exec("CREATE TABLE IF NOT EXISTS `Wo_MessageViews` (
            `id` INT(11) NOT NULL AUTO_INCREMENT, `message_id` INT(11) NOT NULL, `user_id` INT(11) NOT NULL,
            `time` INT(11) NOT NULL,
            PRIMARY KEY (`id`), UNIQUE KEY `idx_msg_user` (`message_id`,`user_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        // Wo_MessageCommentReactions
        $pdoDb->exec("CREATE TABLE IF NOT EXISTS `Wo_MessageCommentReactions` (
            `id` INT(11) NOT NULL AUTO_INCREMENT, `comment_id` INT(11) NOT NULL, `user_id` INT(11) NOT NULL,
            `reaction` VARCHAR(50) NOT NULL, `time` INT(11) NOT NULL,
            PRIMARY KEY (`id`), KEY `idx_comment` (`comment_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        // Встановлюємо role='owner' для засновників каналів
        $pdoDb->exec("UPDATE Wo_GroupChatUsers gcu
            INNER JOIN Wo_GroupChat gc ON gc.group_id = gcu.group_id AND gc.user_id = gcu.user_id
            SET gcu.role = 'owner'
            WHERE (gcu.role = 'member' OR gcu.role IS NULL OR gcu.role = '') AND gc.type = 'channel'");

        @file_put_contents($ch_migration_flag, date('Y-m-d H:i:s'));
        logChannelMessage("Auto-migration completed successfully", 'INFO');
    } catch (Exception $e) {
        logChannelMessage("Auto-migration error: " . $e->getMessage(), 'ERROR');
    }
}

// Роутинг методів
// Android клієнт надсилає 'type', веб клієнт може надсилати 'action'
$action = $data['type'] ?? $data['action'] ?? $_GET['type'] ?? $_GET['action'] ?? '';

logChannelMessage("User $user_id: action=$action", 'INFO');

// Мапінг старих type на нові action для сумісності з Android
$type_mapping = [
    'get_subscribed' => 'get_channels',
    'get_list' => 'get_channels',
    'get_all' => 'get_channels',
    'get_by_id' => 'get_channel_details',
];

// Якщо є маппінг, використовуємо його
$mapped_action = $type_mapping[$action] ?? $action;

// Якщо використовується get_subscribed, додаємо filter
if ($action === 'get_subscribed') {
    $data['filter'] = 'subscribed';
} elseif ($action === 'get_list' || $action === 'get_all') {
    $data['filter'] = 'all';
}

try {
    switch ($mapped_action) {
        // ============================================
        // CRUD операції з каналами
        // ============================================
        case 'get_channels':
            echo json_encode(getChannels($pdoDb, $user_id, $data));
            break;

        case 'search':
            // Пошук каналів за назвою/описом
            echo json_encode(searchChannels($pdoDb, $user_id, $data));
            break;

        case 'get_channel_details':
            $channel_id = $data['channel_id'] ?? null;
            if (!$channel_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
                exit;
            }
            echo json_encode(getChannelDetails($pdoDb, $user_id, $channel_id));
            break;

        case 'create_channel':
            echo json_encode(createChannel($pdoDb, $user_id, $data));
            break;

        case 'update_channel':
            $channel_id = $data['channel_id'] ?? null;
            if (!$channel_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
                exit;
            }
            echo json_encode(updateChannel($pdoDb, $user_id, $channel_id, $data));
            break;

        case 'delete_channel':
            $channel_id = $data['channel_id'] ?? null;
            if (!$channel_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
                exit;
            }
            echo json_encode(deleteChannel($pdoDb, $user_id, $channel_id));
            break;

        // ============================================
        // Підписки
        // ============================================
        case 'subscribe_channel':
            $channel_id = $data['channel_id'] ?? null;
            if (!$channel_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
                exit;
            }
            echo json_encode(subscribeChannel($pdoDb, $user_id, $channel_id));
            break;

        case 'unsubscribe_channel':
            $channel_id = $data['channel_id'] ?? null;
            if (!$channel_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
                exit;
            }
            echo json_encode(unsubscribeChannel($pdoDb, $user_id, $channel_id));
            break;

        case 'get_channel_subscribers':
            $channel_id = $data['channel_id'] ?? null;
            if (!$channel_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
                exit;
            }
            echo json_encode(getChannelSubscribers($pdoDb, $user_id, $channel_id));
            break;

        // ============================================
        // Пости
        // ============================================
        case 'get_channel_posts':
            $channel_id = $data['channel_id'] ?? null;
            if (!$channel_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
                exit;
            }
            echo json_encode(getChannelPosts($pdoDb, $user_id, $channel_id, $data));
            break;

        case 'create_post':
            echo json_encode(createPost($pdoDb, $user_id, $data));
            break;

        case 'update_post':
            $post_id = $data['post_id'] ?? null;
            if (!$post_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'post_id is required']);
                exit;
            }
            echo json_encode(updatePost($pdoDb, $user_id, $post_id, $data));
            break;

        case 'delete_post':
            $post_id = $data['post_id'] ?? null;
            if (!$post_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'post_id is required']);
                exit;
            }
            echo json_encode(deletePost($pdoDb, $user_id, $post_id));
            break;

        case 'pin_post':
            $post_id = $data['post_id'] ?? null;
            if (!$post_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'post_id is required']);
                exit;
            }
            echo json_encode(pinPost($pdoDb, $user_id, $post_id, true));
            break;

        case 'unpin_post':
            $post_id = $data['post_id'] ?? null;
            if (!$post_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'post_id is required']);
                exit;
            }
            echo json_encode(pinPost($pdoDb, $user_id, $post_id, false));
            break;

        // ============================================
        // Коментарі
        // ============================================
        case 'get_comments':
            $post_id = $data['post_id'] ?? null;
            if (!$post_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'post_id is required']);
                exit;
            }
            echo json_encode(getComments($pdoDb, $user_id, $post_id));
            break;

        case 'add_comment':
            echo json_encode(addComment($pdoDb, $user_id, $data));
            break;

        case 'delete_comment':
            $comment_id = $data['comment_id'] ?? null;
            if (!$comment_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'comment_id is required']);
                exit;
            }
            echo json_encode(deleteComment($pdoDb, $user_id, $comment_id));
            break;

        // ============================================
        // Реакції
        // ============================================
        case 'add_post_reaction':
            echo json_encode(addPostReaction($pdoDb, $user_id, $data));
            break;

        case 'remove_post_reaction':
            echo json_encode(removePostReaction($pdoDb, $user_id, $data));
            break;

        case 'add_comment_reaction':
            echo json_encode(addCommentReaction($pdoDb, $user_id, $data));
            break;

        case 'register_post_view':
            $post_id = $data['post_id'] ?? null;
            if (!$post_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'post_id is required']);
                exit;
            }
            echo json_encode(registerPostView($pdoDb, $user_id, $post_id));
            break;

        // ============================================
        // Адміністрування
        // ============================================
        case 'add_channel_admin':
            echo json_encode(addChannelAdmin($pdoDb, $user_id, $data));
            break;

        case 'remove_channel_admin':
            echo json_encode(removeChannelAdmin($pdoDb, $user_id, $data));
            break;

        case 'add_channel_member':
            // Додавання учасника до каналу (тільки для адмінів)
            $channel_id = $data['channel_id'] ?? null;
            $target_user_id = $data['user_id'] ?? null;

            if (!$channel_id || !$target_user_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'channel_id and user_id are required']);
                exit;
            }

            echo json_encode(addChannelMember($pdoDb, $user_id, $channel_id, $target_user_id));
            break;

        // ============================================
        // Налаштування та статистика
        // ============================================
        case 'update_channel_settings':
            $channel_id = $data['channel_id'] ?? null;
            if (!$channel_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
                exit;
            }
            echo json_encode(updateChannelSettings($pdoDb, $user_id, $channel_id, $data));
            break;

        case 'get_channel_statistics':
            $channel_id = $data['channel_id'] ?? null;
            if (!$channel_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
                exit;
            }
            echo json_encode(getChannelStatistics($pdoDb, $user_id, $channel_id));
            break;

        case 'upload_channel_avatar':
            // Для multipart/form-data channel_id може бути в $_POST
            $channel_id = $data['channel_id'] ?? $_POST['channel_id'] ?? null;
            if (!$channel_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
                exit;
            }
            echo json_encode(uploadChannelAvatar($pdoDb, $user_id, $channel_id, $_FILES));
            break;

        default:
            echo json_encode(['api_status' => 400, 'error_message' => 'Invalid action']);
    }
} catch (Exception $e) {
    echo json_encode([
        'api_status' => 500,
        'error_message' => 'Internal server error: ' . $e->getMessage()
    ]);
}

// ============================================
// ФУНКЦІЇ
// ============================================
// Функція validateAccessToken оголошена в config.php

/**
 * Отримати список каналів
 */
function getChannels($pdoDb, $user_id, $data) {
    $filter = $data['filter'] ?? 'all'; // 'all', 'subscribed', 'owned'
    $limit = isset($data['limit']) ? (int)$data['limit'] : 20;
    $offset = isset($data['offset']) ? (int)$data['offset'] : 0;
    $query = isset($data['query']) ? trim($data['query']) : '';

    $where = "WHERE g.type = 'channel'";
    $params = [];

    if ($filter === 'subscribed') {
        $where .= " AND g.group_id IN (SELECT group_id FROM Wo_GroupChatUsers WHERE user_id = ?)";
        $params[] = $user_id;
    } elseif ($filter === 'owned') {
        $where .= " AND g.user_id = ?";
        $params[] = $user_id;
    }

    // Пошук по назві (username може не існувати в БД)
    if (!empty($query)) {
        $where .= " AND g.group_name LIKE ?";
        $searchTerm = "%{$query}%";
        $params[] = $searchTerm;
    }

    try {
        $stmt = $pdoDb->prepare("
            SELECT
                g.group_id AS id,
                g.group_name AS name,
                COALESCE(g.username, g.group_name) AS username,
                COALESCE(g.description, '') AS description,
                g.avatar AS avatar_url,
                g.user_id AS owner_id,
                COALESCE(g.is_private, 0) AS is_private,
                COALESCE(g.is_verified, 0) AS is_verified,
                (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = g.group_id) AS subscribers_count,
                (SELECT COUNT(*) FROM Wo_Messages WHERE group_id = g.group_id) AS posts_count,
                COALESCE(g.category, '') AS category,
                g.time AS created_time,
                (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = g.group_id AND user_id = ?) AS is_subscribed,
                (SELECT role FROM Wo_GroupChatUsers WHERE group_id = g.group_id AND user_id = ?) AS user_role
            FROM Wo_GroupChat g
            $where
            ORDER BY g.group_id DESC
            LIMIT ? OFFSET ?
        ");

        $params = array_merge([$user_id, $user_id], $params, [$limit, $offset]);
        $stmt->execute($params);
        $channels = $stmt->fetchAll(PDO::FETCH_ASSOC);
    } catch (Exception $e) {
        logChannelMessage("Error fetching channels: " . $e->getMessage(), 'ERROR');
        return ['api_status' => 200, 'channels' => []];
    }

    // Форматуємо результати - ВАЖЛИВО: cast до bool для Gson
    foreach ($channels as &$channel) {
        $channel['is_private'] = (bool)$channel['is_private'];
        $channel['is_subscribed'] = (bool)$channel['is_subscribed'];
        $channel['is_verified'] = (bool)$channel['is_verified'];
        $channel['is_admin'] = in_array($channel['user_role'], ['owner', 'admin', 'moderator']);
        $channel['settings'] = null;
        $channel['subscribers_count'] = (int)$channel['subscribers_count'];
        $channel['posts_count'] = (int)$channel['posts_count'];
        unset($channel['user_role']);
    }

    return [
        'api_status' => 200,
        'channels' => $channels,
        'total' => count($channels)
    ];
}

/**
 * Пошук каналів за назвою/описом
 * Обгортка над getChannels() для сумісності з Android
 */
function searchChannels($pdoDb, $user_id, $data) {
    // Просто викликаємо getChannels() з параметром query
    // getChannels() вже має логіку пошуку (lines 354-360)
    return getChannels($pdoDb, $user_id, $data);
}

/**
 * Отримати деталі каналу
 */
function getChannelDetails($pdoDb, $user_id, $channel_id) {
    try {
        $stmt = $pdoDb->prepare("
            SELECT
                g.group_id AS id,
                g.group_name AS name,
                COALESCE(g.username, g.group_name) AS username,
                COALESCE(g.description, '') AS description,
                g.avatar AS avatar_url,
                g.user_id AS owner_id,
                COALESCE(g.is_private, 0) AS is_private,
                COALESCE(g.is_verified, 0) AS is_verified,
                (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = g.group_id) AS subscribers_count,
                (SELECT COUNT(*) FROM Wo_Messages WHERE group_id = g.group_id) AS posts_count,
                COALESCE(g.category, '') AS category,
                g.time AS created_time,
                (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = g.group_id AND user_id = ?) AS is_subscribed,
                (SELECT role FROM Wo_GroupChatUsers WHERE group_id = g.group_id AND user_id = ?) AS user_role
            FROM Wo_GroupChat g
            WHERE g.group_id = ? AND g.type = 'channel'
        ");
        $stmt->execute([$user_id, $user_id, $channel_id]);
        $channel = $stmt->fetch(PDO::FETCH_ASSOC);
    } catch (Exception $e) {
        logChannelMessage("Error getting channel details: " . $e->getMessage(), 'ERROR');
        return ['api_status' => 500, 'error_message' => 'Database error'];
    }

    if (!$channel) {
        return ['api_status' => 404, 'error_message' => 'Channel not found'];
    }

    // Форматуємо результати - ВАЖЛИВО: cast до bool для Gson
    $channel['is_private'] = (bool)$channel['is_private'];
    $channel['is_subscribed'] = (bool)$channel['is_subscribed'];
    $channel['is_verified'] = (bool)$channel['is_verified'];
    $channel['is_admin'] = in_array($channel['user_role'], ['owner', 'admin', 'moderator']);
    $channel['subscribers_count'] = (int)$channel['subscribers_count'];
    $channel['posts_count'] = (int)$channel['posts_count'];
    $channel['settings'] = null;
    unset($channel['user_role']);

    return [
        'api_status' => 200,
        'channel' => $channel
    ];
}

/**
 * Створити новий канал
 */
function createChannel($pdoDb, $user_id, $data) {
    $name = trim($data['name'] ?? '');
    $username = trim($data['username'] ?? '');
    $description = trim($data['description'] ?? '');
    $is_private = isset($data['is_private']) ? ($data['is_private'] ? '1' : '0') : '0';
    $category = trim($data['category'] ?? '');

    // Валідація
    if (empty($name)) {
        return ['api_status' => 400, 'error_message' => 'Channel name is required'];
    }

    if ($is_private === '0' && empty($username)) {
        return ['api_status' => 400, 'error_message' => 'Username is required for public channels'];
    }

    // Перевірка унікальності username
    if (!empty($username)) {
        if (!preg_match('/^[a-zA-Z0-9_]+$/', $username)) {
            return ['api_status' => 400, 'error_message' => 'Username can only contain letters, numbers and underscore'];
        }

        $stmt = $pdoDb->prepare("SELECT group_id FROM Wo_GroupChat WHERE username = ?");
        $stmt->execute([$username]);
        if ($stmt->fetch()) {
            return ['api_status' => 400, 'error_message' => 'Username already taken'];
        }
    }

    // Дефолтні налаштування
    $settings = json_encode([
        'allow_comments' => true,
        'allow_reactions' => true,
        'allow_shares' => true,
        'show_statistics' => true,
        'notify_subscribers_new_post' => true,
        'auto_delete_posts_days' => null,
        'signature_enabled' => false,
        'comments_moderation' => false,
        'slow_mode_seconds' => 0
    ]);

    try {
        $pdoDb->beginTransaction();

        // Створюємо канал
        $stmt = $pdoDb->prepare("
            INSERT INTO Wo_GroupChat
            (user_id, group_name, username, description, is_private, category, type, settings, time, subscribers_count, posts_count, is_verified)
            VALUES (?, ?, ?, ?, ?, ?, 'channel', ?, ?, 0, 0, 0)
        ");

        $time = time();
        $stmt->execute([
            $user_id,
            $name,
            $username ?: null,
            $description ?: null,
            $is_private,
            $category ?: null,
            $settings,
            $time
        ]);

        $channel_id = $pdoDb->lastInsertId();

        // Автоматично підписуємо власника як owner
        $stmt_sub = $pdoDb->prepare("
            INSERT INTO Wo_GroupChatUsers (user_id, group_id, role, active, last_seen)
            VALUES (?, ?, 'owner', '1', ?)
        ");
        $stmt_sub->execute([$user_id, $channel_id, $time]);

        $pdoDb->commit();

        logChannelMessage("User $user_id created channel $channel_id: $name (private=$is_private)", 'INFO');

        // Повертаємо створений канал
        return getChannelDetails($pdoDb, $user_id, $channel_id);

    } catch (Exception $e) {
        $pdoDb->rollBack();
        logChannelMessage("Failed to create channel for user $user_id: " . $e->getMessage(), 'ERROR');
        return ['api_status' => 500, 'error_message' => 'Failed to create channel: ' . $e->getMessage()];
    }
}

/**
 * Оновити канал
 */
function updateChannel($pdoDb, $user_id, $channel_id, $data) {
    // Перевіряємо права
    $stmt = $pdoDb->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$channel_id, $user_id]);
    $role = $stmt->fetchColumn();

    if (!in_array($role, ['owner', 'admin'])) {
        return ['api_status' => 403, 'error_message' => 'You do not have permission to edit this channel'];
    }

    $updates = [];
    $params = [];

    if (isset($data['name'])) {
        $updates[] = "group_name = ?";
        $params[] = trim($data['name']);
    }

    if (isset($data['description'])) {
        $updates[] = "description = ?";
        $params[] = trim($data['description']);
    }

    if (isset($data['username'])) {
        $username = trim($data['username']);
        if (!empty($username) && !preg_match('/^[a-zA-Z0-9_]+$/', $username)) {
            return ['api_status' => 400, 'error_message' => 'Invalid username format'];
        }

        // Перевіряємо унікальність
        $stmt = $pdoDb->prepare("SELECT group_id FROM Wo_GroupChat WHERE username = ? AND group_id != ?");
        $stmt->execute([$username, $channel_id]);
        if ($stmt->fetch()) {
            return ['api_status' => 400, 'error_message' => 'Username already taken'];
        }

        $updates[] = "username = ?";
        $params[] = $username ?: null;
    }

    if (isset($data['category'])) {
        $updates[] = "category = ?";
        $params[] = trim($data['category']);
    }

    if (empty($updates)) {
        return ['api_status' => 400, 'error_message' => 'Nothing to update'];
    }

    $params[] = $channel_id;

    $stmt = $pdoDb->prepare("UPDATE Wo_GroupChat SET " . implode(', ', $updates) . " WHERE group_id = ? AND type = 'channel'");
    $stmt->execute($params);

    return getChannelDetails($pdoDb, $user_id, $channel_id);
}

/**
 * Видалити канал
 */
function deleteChannel($pdoDb, $user_id, $channel_id) {
    // Перевіряємо права (тільки owner)
    $stmt = $pdoDb->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$channel_id, $user_id]);
    $role = $stmt->fetchColumn();

    if ($role !== 'owner') {
        return ['api_status' => 403, 'error_message' => 'Only channel owner can delete the channel'];
    }

    try {
        $pdoDb->beginTransaction();

        // Видаляємо всі повідомлення
        $stmt = $pdoDb->prepare("DELETE FROM Wo_Messages WHERE group_id = ?");
        $stmt->execute([$channel_id]);

        // Видаляємо всіх підписників
        $stmt = $pdoDb->prepare("DELETE FROM Wo_GroupChatUsers WHERE group_id = ?");
        $stmt->execute([$channel_id]);

        // Видаляємо канал
        $stmt = $pdoDb->prepare("DELETE FROM Wo_GroupChat WHERE group_id = ? AND type = 'channel'");
        $stmt->execute([$channel_id]);

        $pdoDb->commit();

        return ['api_status' => 200, 'message' => 'Channel deleted successfully'];
    } catch (Exception $e) {
        $pdoDb->rollBack();
        return ['api_status' => 500, 'error_message' => 'Failed to delete channel: ' . $e->getMessage()];
    }
}

/**
 * Підписатися на канал
 */
function subscribeChannel($pdoDb, $user_id, $channel_id) {
    // Перевіряємо чи існує канал
    $stmt = $pdoDb->prepare("SELECT group_id, is_private FROM Wo_GroupChat WHERE group_id = ? AND type = 'channel'");
    $stmt->execute([$channel_id]);
    $channel = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$channel) {
        return ['api_status' => 404, 'error_message' => 'Channel not found'];
    }

    // Перевіряємо чи вже підписаний
    $stmt = $pdoDb->prepare("SELECT id FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$channel_id, $user_id]);
    if ($stmt->fetch()) {
        return ['api_status' => 400, 'error_message' => 'Already subscribed'];
    }

    // Підписуємося
    $stmt = $pdoDb->prepare("
        INSERT INTO Wo_GroupChatUsers (user_id, group_id, role, active, last_seen)
        VALUES (?, ?, 'member', '1', ?)
    ");
    $stmt->execute([$user_id, $channel_id, time()]);

    return ['api_status' => 200, 'message' => 'Subscribed successfully'];
}

/**
 * Відписатися від каналу
 */
function unsubscribeChannel($pdoDb, $user_id, $channel_id) {
    $stmt = $pdoDb->prepare("DELETE FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ? AND role != 'owner'");
    $stmt->execute([$channel_id, $user_id]);

    if ($stmt->rowCount() === 0) {
        return ['api_status' => 400, 'error_message' => 'Not subscribed or cannot unsubscribe (you are owner)'];
    }

    return ['api_status' => 200, 'message' => 'Unsubscribed successfully'];
}

/**
 * Отримати підписників каналу
 */
function getChannelSubscribers($pdoDb, $user_id, $channel_id) {
    // Перевіряємо чи є адміном
    $stmt = $pdoDb->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$channel_id, $user_id]);
    $role = $stmt->fetchColumn();

    if (!in_array($role, ['owner', 'admin'])) {
        return ['api_status' => 403, 'error_message' => 'Only admins can view subscribers'];
    }

    $stmt = $pdoDb->prepare("
        SELECT
            gcu.user_id AS id,
            u.username,
            CONCAT(u.first_name, ' ', u.last_name) AS name,
            u.avatar,
            gcu.role,
            gcu.last_seen
        FROM Wo_GroupChatUsers gcu
        LEFT JOIN Wo_Users u ON u.user_id = gcu.user_id
        WHERE gcu.group_id = ?
        ORDER BY
            CASE gcu.role
                WHEN 'owner' THEN 1
                WHEN 'admin' THEN 2
                WHEN 'moderator' THEN 3
                ELSE 4
            END,
            gcu.id ASC
    ");
    $stmt->execute([$channel_id]);
    $subscribers = $stmt->fetchAll(PDO::FETCH_ASSOC);

    return [
        'api_status' => 200,
        'subscribers' => $subscribers,
        'total' => count($subscribers)
    ];
}

/**
 * Отримати пости каналу
 */
function getChannelPosts($pdoDb, $user_id, $channel_id, $data) {
    $limit = isset($data['limit']) ? (int)$data['limit'] : 20;
    $offset = isset($data['offset']) ? (int)$data['offset'] : 0;

    // Перевіряємо чи підписаний на канал або це публічний канал
    $stmt = $pdoDb->prepare("
        SELECT is_private,
               (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?) AS is_subscribed
        FROM Wo_GroupChat
        WHERE group_id = ? AND type = 'channel'
    ");
    $stmt->execute([$channel_id, $user_id, $channel_id]);
    $channel = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$channel) {
        return ['api_status' => 404, 'error_message' => 'Channel not found'];
    }

    if ($channel['is_private'] === '1' && $channel['is_subscribed'] == 0) {
        return ['api_status' => 403, 'error_message' => 'Subscribe to view posts'];
    }

    // Отримуємо пости з інформацією про автора
    $stmt = $pdoDb->prepare("
        SELECT
            m.id,
            m.from_id AS author_id,
            u.username AS author_username,
            CONCAT(u.first_name, ' ', u.last_name) AS author_name,
            u.avatar AS author_avatar,
            m.text,
            m.iv,
            m.tag,
            m.media,
            m.mediaFileName,
            m.time AS created_time,
            m.type_two AS is_pinned,
            (SELECT COUNT(*) FROM wo_reactions WHERE message_id = m.id) AS reactions_count,
            (SELECT COUNT(*) FROM Wo_MessageComments WHERE message_id = m.id) AS comments_count,
            (SELECT COUNT(*) FROM Wo_MessageViews WHERE message_id = m.id) AS views_count
        FROM Wo_Messages m
        LEFT JOIN Wo_Users u ON u.user_id = m.from_id
        WHERE m.group_id = ?
        ORDER BY
            CASE WHEN m.type_two = 'pinned' THEN 0 ELSE 1 END,
            m.time DESC
        LIMIT ? OFFSET ?
    ");
    $stmt->execute([$channel_id, $limit, $offset]);
    $posts = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Форматуємо пости
    foreach ($posts as &$post) {
        $post['is_pinned'] = ($post['is_pinned'] === 'pinned');
        $post['is_edited'] = false; // TODO: додати підтримку edited_time

        // Дешифруємо текст для приватних каналів
        if ($channel['is_private'] === '1' && !empty($post['iv']) && !empty($post['tag'])) {
            $decrypted = CryptoHelper::decryptGCM(
                $post['text'],
                $post['created_time'],
                $post['iv'],
                $post['tag']
            );
            if ($decrypted !== false) {
                $post['text'] = $decrypted;
            }
        }

        // Видаляємо технічні поля
        unset($post['iv'], $post['tag']);

        // Перетворюємо media у масив
        if (!empty($post['media'])) {
            $post['media'] = [[
                'url' => $post['media'],
                'type' => 'image',
                'filename' => $post['mediaFileName']
            ]];
        } else {
            $post['media'] = [];
        }
        unset($post['mediaFileName']);

        // Отримуємо реакції до поста з інформацією про користувачів
        $stmt = $pdoDb->prepare("
            SELECT
                r.reaction AS emoji,
                r.user_id,
                u.username,
                u.avatar
            FROM wo_reactions r
            LEFT JOIN Wo_Users u ON u.user_id = r.user_id
            WHERE r.message_id = ?
            ORDER BY r.id ASC
        ");
        $stmt->execute([$post['id']]);
        $all_reactions = $stmt->fetchAll(PDO::FETCH_ASSOC);

        // Групуємо реакції по емодзі
        $reactions_grouped = [];
        foreach ($all_reactions as $reaction) {
            $emoji = $reaction['emoji'];
            if (!isset($reactions_grouped[$emoji])) {
                $reactions_grouped[$emoji] = [
                    'emoji' => $emoji,
                    'count' => 0,
                    'user_reacted' => false,
                    'recent_users' => []
                ];
            }
            $reactions_grouped[$emoji]['count']++;

            // Перевіряємо чи поточний користувач поставив цю реакцію
            if ($reaction['user_id'] == $user_id) {
                $reactions_grouped[$emoji]['user_reacted'] = true;
            }

            // Додаємо перших 3 користувачів
            if (count($reactions_grouped[$emoji]['recent_users']) < 3) {
                $reactions_grouped[$emoji]['recent_users'][] = [
                    'user_id' => $reaction['user_id'],
                    'username' => $reaction['username'],
                    'avatar' => $reaction['avatar']
                ];
            }
        }

        $post['reactions'] = array_values($reactions_grouped);
    }

    return [
        'api_status' => 200,
        'posts' => $posts,
        'total' => count($posts)
    ];
}

/**
 * Створити пост у каналі
 */
function createPost($pdoDb, $user_id, $data) {
    $channel_id = $data['channel_id'] ?? null;
    $text = trim($data['text'] ?? '');
    $media_url = trim($data['media_url'] ?? '');

    if (!$channel_id) {
        return ['api_status' => 400, 'error_message' => 'channel_id is required'];
    }

    if (empty($text) && empty($media_url)) {
        return ['api_status' => 400, 'error_message' => 'Post text or media is required'];
    }

    // Перевіряємо права на публікацію та чи канал приватний
    $stmt = $pdoDb->prepare("
        SELECT gcu.role, gc.is_private
        FROM Wo_GroupChatUsers gcu
        JOIN Wo_GroupChat gc ON gc.group_id = gcu.group_id
        WHERE gcu.group_id = ? AND gcu.user_id = ?
    ");
    $stmt->execute([$channel_id, $user_id]);
    $channel_info = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$channel_info || !in_array($channel_info['role'], ['owner', 'admin', 'moderator'])) {
        logChannelMessage("User $user_id attempted to post in channel $channel_id without permission", 'WARNING');
        return ['api_status' => 403, 'error_message' => 'You do not have permission to post in this channel'];
    }

    $time = time();
    $encrypted_text = $text;
    $iv = '';
    $tag = '';

    // Шифруємо текст для приватних каналів
    if ($channel_info['is_private'] === '1' && !empty($text)) {
        $encryption_result = CryptoHelper::encryptGCM($text, $time);
        if ($encryption_result) {
            $encrypted_text = $encryption_result['ciphertext'];
            $iv = $encryption_result['iv'];
            $tag = $encryption_result['tag'];
            logChannelMessage("Post text encrypted for private channel $channel_id", 'DEBUG');
        }
    }

    // Створюємо пост
    $stmt = $pdoDb->prepare("
        INSERT INTO Wo_Messages
        (from_id, group_id, text, media, mediaFileName, time, seen, sent_push, iv, tag)
        VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?)
    ");

    $filename = $media_url ? basename($media_url) : '';

    $stmt->execute([
        $user_id,
        $channel_id,
        $encrypted_text,
        $media_url ?: '',
        $filename,
        $time,
        $iv,
        $tag
    ]);

    logChannelMessage("User $user_id created post in channel $channel_id (private=" . $channel_info['is_private'] . ")", 'INFO');

    $post_id = $pdoDb->lastInsertId();

    // Оновлюємо лічильник постів в каналі
    $stmt = $pdoDb->prepare("UPDATE Wo_GroupChat SET posts_count = posts_count + 1 WHERE group_id = ?");
    $stmt->execute([$channel_id]);

    // Повертаємо створений пост з повними даними
    $stmt = $pdoDb->prepare("
        SELECT
            m.id,
            m.from_id AS author_id,
            u.username AS author_username,
            CONCAT(u.first_name, ' ', u.last_name) AS author_name,
            u.avatar AS author_avatar,
            m.text,
            m.media,
            m.mediaFileName,
            m.time AS created_time,
            m.type_two AS is_pinned,
            0 AS reactions_count,
            0 AS comments_count,
            0 AS views_count
        FROM Wo_Messages m
        LEFT JOIN Wo_Users u ON u.user_id = m.from_id
        WHERE m.id = ?
    ");
    $stmt->execute([$post_id]);
    $post = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($post) {
        $post['is_pinned'] = ($post['is_pinned'] === 'pinned');
        $post['is_edited'] = false;

        // Форматуємо media
        if (!empty($post['media'])) {
            $post['media'] = [[
                'url' => $post['media'],
                'type' => 'image',
                'filename' => $post['mediaFileName']
            ]];
        } else {
            $post['media'] = [];
        }
        unset($post['mediaFileName']);
    }

    return [
        'api_status' => 200,
        'message' => 'Post created successfully',
        'post_id' => $post_id,
        'post' => $post
    ];
}

/**
 * Оновити пост
 */
function updatePost($pdoDb, $user_id, $post_id, $data) {
    // Перевіряємо права
    $stmt = $pdoDb->prepare("
        SELECT m.from_id, m.group_id, gcu.role
        FROM Wo_Messages m
        LEFT JOIN Wo_GroupChatUsers gcu ON gcu.group_id = m.group_id AND gcu.user_id = ?
        WHERE m.id = ?
    ");
    $stmt->execute([$user_id, $post_id]);
    $post = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$post) {
        return ['api_status' => 404, 'error_message' => 'Post not found'];
    }

    if ($post['from_id'] != $user_id && !in_array($post['role'], ['owner', 'admin'])) {
        return ['api_status' => 403, 'error_message' => 'You do not have permission to edit this post'];
    }

    $updates = [];
    $params = [];

    if (isset($data['text'])) {
        $updates[] = "text = ?";
        $params[] = trim($data['text']);
    }

    if (empty($updates)) {
        return ['api_status' => 400, 'error_message' => 'Nothing to update'];
    }

    $params[] = $post_id;

    $stmt = $pdoDb->prepare("UPDATE Wo_Messages SET " . implode(', ', $updates) . " WHERE id = ?");
    $stmt->execute($params);

    return ['api_status' => 200, 'message' => 'Post updated successfully'];
}

/**
 * Видалити пост
 */
function deletePost($pdoDb, $user_id, $post_id) {
    // Перевіряємо права
    $stmt = $pdoDb->prepare("
        SELECT m.from_id, m.group_id, gcu.role
        FROM Wo_Messages m
        LEFT JOIN Wo_GroupChatUsers gcu ON gcu.group_id = m.group_id AND gcu.user_id = ?
        WHERE m.id = ?
    ");
    $stmt->execute([$user_id, $post_id]);
    $post = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$post) {
        return ['api_status' => 404, 'error_message' => 'Post not found'];
    }

    if ($post['from_id'] != $user_id && !in_array($post['role'], ['owner', 'admin'])) {
        return ['api_status' => 403, 'error_message' => 'You do not have permission to delete this post'];
    }

    $stmt = $pdoDb->prepare("DELETE FROM Wo_Messages WHERE id = ?");
    $stmt->execute([$post_id]);

    return ['api_status' => 200, 'message' => 'Post deleted successfully'];
}

/**
 * Закріпити/відкріпити пост
 */
function pinPost($pdoDb, $user_id, $post_id, $pin = true) {
    // Перевіряємо права
    $stmt = $pdoDb->prepare("
        SELECT m.group_id, gcu.role
        FROM Wo_Messages m
        LEFT JOIN Wo_GroupChatUsers gcu ON gcu.group_id = m.group_id AND gcu.user_id = ?
        WHERE m.id = ?
    ");
    $stmt->execute([$user_id, $post_id]);
    $post = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$post) {
        return ['api_status' => 404, 'error_message' => 'Post not found'];
    }

    if (!in_array($post['role'], ['owner', 'admin', 'moderator'])) {
        return ['api_status' => 403, 'error_message' => 'You do not have permission to pin posts'];
    }

    $type_two = $pin ? 'pinned' : '';

    $stmt = $pdoDb->prepare("UPDATE Wo_Messages SET type_two = ? WHERE id = ?");
    $stmt->execute([$type_two, $post_id]);

    return [
        'api_status' => 200,
        'message' => $pin ? 'Post pinned successfully' : 'Post unpinned successfully'
    ];
}

/**
 * Отримати коментарі до поста
 */
function getComments($pdoDb, $user_id, $post_id) {
    $stmt = $pdoDb->prepare("
        SELECT
            mc.id,
            mc.user_id,
            u.username,
            CONCAT(u.first_name, ' ', u.last_name) AS user_name,
            u.avatar AS user_avatar,
            mc.text,
            mc.time,
            mc.edited_time,
            mc.reply_to_comment_id,
            (SELECT COUNT(*) FROM Wo_MessageCommentReactions WHERE comment_id = mc.id) AS reactions_count
        FROM Wo_MessageComments mc
        LEFT JOIN Wo_Users u ON u.user_id = mc.user_id
        WHERE mc.message_id = ?
        ORDER BY mc.time ASC
    ");
    $stmt->execute([$post_id]);
    $comments = $stmt->fetchAll(PDO::FETCH_ASSOC);

    return [
        'api_status' => 200,
        'comments' => $comments,
        'total' => count($comments)
    ];
}

/**
 * Додати коментар до поста
 */
function addComment($pdoDb, $user_id, $data) {
    $post_id = $data['post_id'] ?? null;
    $text = trim($data['text'] ?? '');
    $reply_to = $data['reply_to_comment_id'] ?? null;

    if (!$post_id || empty($text)) {
        return ['api_status' => 400, 'error_message' => 'post_id and text are required'];
    }

    // Перевіряємо чи існує пост (settings колонка може не існувати)
    $stmt = $pdoDb->prepare("
        SELECT m.group_id
        FROM Wo_Messages m
        JOIN Wo_GroupChat g ON g.group_id = m.group_id
        WHERE m.id = ? AND g.type = 'channel'
    ");
    $stmt->execute([$post_id]);
    $post = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$post) {
        return ['api_status' => 404, 'error_message' => 'Post not found'];
    }

    // Коментарі дозволені за замовчуванням (settings колонка може не існувати)
    // if (isset($settings['allow_comments']) && !$settings['allow_comments']) {
    //     return ['api_status' => 403, 'error_message' => 'Comments are disabled for this channel'];
    // }

    // Додаємо коментар
    $stmt = $pdoDb->prepare("
        INSERT INTO Wo_MessageComments (message_id, user_id, text, time, reply_to_comment_id)
        VALUES (?, ?, ?, ?, ?)
    ");
    $stmt->execute([$post_id, $user_id, $text, time(), $reply_to]);

    return [
        'api_status' => 200,
        'message' => 'Comment added successfully',
        'comment_id' => $pdoDb->lastInsertId()
    ];
}

/**
 * Видалити коментар
 */
function deleteComment($pdoDb, $user_id, $comment_id) {
    // Перевіряємо права
    $stmt = $pdoDb->prepare("
        SELECT mc.user_id, m.group_id, gcu.role
        FROM Wo_MessageComments mc
        JOIN Wo_Messages m ON m.id = mc.message_id
        LEFT JOIN Wo_GroupChatUsers gcu ON gcu.group_id = m.group_id AND gcu.user_id = ?
        WHERE mc.id = ?
    ");
    $stmt->execute([$user_id, $comment_id]);
    $comment = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$comment) {
        return ['api_status' => 404, 'error_message' => 'Comment not found'];
    }

    if ($comment['user_id'] != $user_id && !in_array($comment['role'], ['owner', 'admin', 'moderator'])) {
        return ['api_status' => 403, 'error_message' => 'You do not have permission to delete this comment'];
    }

    $stmt = $pdoDb->prepare("DELETE FROM Wo_MessageComments WHERE id = ?");
    $stmt->execute([$comment_id]);

    return ['api_status' => 200, 'message' => 'Comment deleted successfully'];
}

/**
 * Додати реакцію на пост
 */
function addPostReaction($pdoDb, $user_id, $data) {
    $post_id = $data['post_id'] ?? null;
    $reaction = trim($data['reaction'] ?? '');

    if (!$post_id || empty($reaction)) {
        return ['api_status' => 400, 'error_message' => 'post_id and reaction are required'];
    }

    // Перевіряємо чи існує пост (settings колонка може не існувати)
    $stmt = $pdoDb->prepare("
        SELECT m.id
        FROM Wo_Messages m
        JOIN Wo_GroupChat g ON g.group_id = m.group_id
        WHERE m.id = ? AND g.type = 'channel'
    ");
    $stmt->execute([$post_id]);
    $post = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$post) {
        return ['api_status' => 404, 'error_message' => 'Post not found'];
    }

    // Реакції дозволені за замовчуванням (settings колонка може не існувати)

    // Видаляємо попередню реакцію цього користувача
    $stmt = $pdoDb->prepare("DELETE FROM wo_reactions WHERE message_id = ? AND user_id = ?");
    $stmt->execute([$post_id, $user_id]);

    // Додаємо нову реакцію
    $stmt = $pdoDb->prepare("
        INSERT INTO wo_reactions (user_id, message_id, reaction)
        VALUES (?, ?, ?)
    ");
    $stmt->execute([$user_id, $post_id, $reaction]);

    return ['api_status' => 200, 'message' => 'Reaction added successfully'];
}

/**
 * Видалити реакцію з поста
 */
function removePostReaction($pdoDb, $user_id, $data) {
    $post_id = $data['post_id'] ?? null;

    if (!$post_id) {
        return ['api_status' => 400, 'error_message' => 'post_id is required'];
    }

    $stmt = $pdoDb->prepare("DELETE FROM wo_reactions WHERE message_id = ? AND user_id = ?");
    $stmt->execute([$post_id, $user_id]);

    return ['api_status' => 200, 'message' => 'Reaction removed successfully'];
}

/**
 * Додати реакцію на коментар
 */
function addCommentReaction($pdoDb, $user_id, $data) {
    $comment_id = $data['comment_id'] ?? null;
    $reaction = trim($data['reaction'] ?? '');

    if (!$comment_id || empty($reaction)) {
        return ['api_status' => 400, 'error_message' => 'comment_id and reaction are required'];
    }

    // Видаляємо попередню реакцію
    $stmt = $pdoDb->prepare("DELETE FROM Wo_MessageCommentReactions WHERE comment_id = ? AND user_id = ?");
    $stmt->execute([$comment_id, $user_id]);

    // Додаємо нову
    $stmt = $pdoDb->prepare("
        INSERT INTO Wo_MessageCommentReactions (comment_id, user_id, reaction, time)
        VALUES (?, ?, ?, ?)
    ");
    $stmt->execute([$comment_id, $user_id, $reaction, time()]);

    return ['api_status' => 200, 'message' => 'Reaction added successfully'];
}

/**
 * Зареєструвати перегляд поста
 */
function registerPostView($pdoDb, $user_id, $post_id) {
    // Перевіряємо чи існує пост
    $stmt = $pdoDb->prepare("SELECT id FROM Wo_Messages WHERE id = ?");
    $stmt->execute([$post_id]);
    if (!$stmt->fetch()) {
        return ['api_status' => 404, 'error_message' => 'Post not found'];
    }

    // Перевіряємо чи вже є запис про перегляд від цього користувача
    $stmt = $pdoDb->prepare("SELECT id FROM Wo_MessageViews WHERE message_id = ? AND user_id = ?");
    $stmt->execute([$post_id, $user_id]);

    if (!$stmt->fetch()) {
        // Додаємо новий перегляд
        $stmt = $pdoDb->prepare("
            INSERT INTO Wo_MessageViews (message_id, user_id, time)
            VALUES (?, ?, ?)
        ");
        $stmt->execute([$post_id, $user_id, time()]);

        logChannelMessage("Post view registered: post_id=$post_id, user_id=$user_id", 'DEBUG');
    }

    // Повертаємо оновлену кількість переглядів
    $stmt = $pdoDb->prepare("SELECT COUNT(*) as views_count FROM Wo_MessageViews WHERE message_id = ?");
    $stmt->execute([$post_id]);
    $result = $stmt->fetch(PDO::FETCH_ASSOC);

    return [
        'api_status' => 200,
        'message' => 'View registered',
        'views_count' => $result['views_count'] ?? 0
    ];
}

/**
 * Додати адміністратора каналу
 */
function addChannelAdmin($pdoDb, $user_id, $data) {
    $channel_id = $data['channel_id'] ?? null;
    $admin_user_id = $data['user_id'] ?? null;
    $user_search = $data['user_search'] ?? null; // ID, username або ім'я
    $role = $data['role'] ?? 'admin'; // admin, moderator

    if (!$channel_id) {
        return ['api_status' => 400, 'error_message' => 'channel_id is required'];
    }

    // Якщо передано user_search замість user_id, шукаємо користувача
    if (!$admin_user_id && $user_search) {
        $search_term = trim($user_search);

        // Спочатку пробуємо як ID
        if (is_numeric($search_term)) {
            $stmt = $pdoDb->prepare("SELECT user_id FROM Wo_Users WHERE user_id = ?");
            $stmt->execute([$search_term]);
            $admin_user_id = $stmt->fetchColumn();
        }

        // Якщо не знайдено, шукаємо по username
        if (!$admin_user_id) {
            // Видаляємо @ якщо є
            $username = ltrim($search_term, '@');
            $stmt = $pdoDb->prepare("SELECT user_id FROM Wo_Users WHERE username = ?");
            $stmt->execute([$username]);
            $admin_user_id = $stmt->fetchColumn();
        }

        // Якщо не знайдено, шукаємо по імені
        if (!$admin_user_id) {
            $stmt = $pdoDb->prepare("
                SELECT user_id FROM Wo_Users
                WHERE CONCAT(first_name, ' ', last_name) LIKE ?
                   OR first_name LIKE ?
                   OR last_name LIKE ?
                LIMIT 1
            ");
            $like_term = "%{$search_term}%";
            $stmt->execute([$like_term, $like_term, $like_term]);
            $admin_user_id = $stmt->fetchColumn();
        }

        if (!$admin_user_id) {
            return ['api_status' => 404, 'error_message' => 'User not found'];
        }
    }

    if (!$admin_user_id) {
        return ['api_status' => 400, 'error_message' => 'user_id or user_search is required'];
    }

    if (!in_array($role, ['admin', 'moderator'])) {
        return ['api_status' => 400, 'error_message' => 'Invalid role'];
    }

    // Перевіряємо права (тільки owner)
    $stmt = $pdoDb->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$channel_id, $user_id]);
    $current_role = $stmt->fetchColumn();

    if ($current_role !== 'owner') {
        return ['api_status' => 403, 'error_message' => 'Only channel owner can add admins'];
    }

    // Оновлюємо роль користувача
    $stmt = $pdoDb->prepare("
        UPDATE Wo_GroupChatUsers
        SET role = ?
        WHERE group_id = ? AND user_id = ?
    ");
    $stmt->execute([$role, $channel_id, $admin_user_id]);

    if ($stmt->rowCount() === 0) {
        return ['api_status' => 404, 'error_message' => 'User is not a member of this channel'];
    }

    return ['api_status' => 200, 'message' => 'Admin added successfully'];
}

/**
 * Додати учасника до каналу (для адмінів/модераторів)
 */
function addChannelMember($pdoDb, $admin_user_id, $channel_id, $target_user_id) {
    // Перевіряємо чи існує канал
    $stmt = $pdoDb->prepare("SELECT group_id, is_private FROM Wo_GroupChat WHERE group_id = ? AND type = 'channel'");
    $stmt->execute([$channel_id]);
    $channel = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$channel) {
        return ['api_status' => 404, 'error_message' => 'Channel not found'];
    }

    // Перевіряємо права поточного користувача (admin, moderator, або owner)
    $stmt = $pdoDb->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$channel_id, $admin_user_id]);
    $admin_role = $stmt->fetchColumn();

    if (!in_array($admin_role, ['owner', 'admin', 'moderator'])) {
        return ['api_status' => 403, 'error_message' => 'Only channel admins can add members'];
    }

    // Перевіряємо чи користувач існує
    $stmt = $pdoDb->prepare("SELECT user_id, username FROM Wo_Users WHERE user_id = ?");
    $stmt->execute([$target_user_id]);
    $target_user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$target_user) {
        return ['api_status' => 404, 'error_message' => 'User not found'];
    }

    // Перевіряємо чи вже є учасником
    $stmt = $pdoDb->prepare("SELECT id FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$channel_id, $target_user_id]);
    if ($stmt->fetch()) {
        return ['api_status' => 400, 'error_message' => 'User is already a member of this channel'];
    }

    // Додаємо користувача як member
    $stmt = $pdoDb->prepare("
        INSERT INTO Wo_GroupChatUsers (user_id, group_id, role, active, last_seen)
        VALUES (?, ?, 'member', '1', ?)
    ");
    $stmt->execute([$target_user_id, $channel_id, time()]);

    logChannelMessage("Admin $admin_user_id added user $target_user_id to channel $channel_id", 'INFO');

    return [
        'api_status' => 200,
        'message' => 'Member added successfully',
        'user' => [
            'user_id' => $target_user['user_id'],
            'username' => $target_user['username']
        ]
    ];
}

/**
 * Видалити адміністратора каналу
 */
function removeChannelAdmin($pdoDb, $user_id, $data) {
    $channel_id = $data['channel_id'] ?? null;
    $admin_user_id = $data['user_id'] ?? null;

    if (!$channel_id || !$admin_user_id) {
        return ['api_status' => 400, 'error_message' => 'channel_id and user_id are required'];
    }

    // Перевіряємо права (тільки owner)
    $stmt = $pdoDb->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$channel_id, $user_id]);
    $current_role = $stmt->fetchColumn();

    if ($current_role !== 'owner') {
        return ['api_status' => 403, 'error_message' => 'Only channel owner can remove admins'];
    }

    // Змінюємо роль на member
    $stmt = $pdoDb->prepare("
        UPDATE Wo_GroupChatUsers
        SET role = 'member'
        WHERE group_id = ? AND user_id = ? AND role != 'owner'
    ");
    $stmt->execute([$channel_id, $admin_user_id]);

    if ($stmt->rowCount() === 0) {
        return ['api_status' => 404, 'error_message' => 'Admin not found or cannot remove owner'];
    }

    return ['api_status' => 200, 'message' => 'Admin removed successfully'];
}

/**
 * Оновити налаштування каналу
 */
function updateChannelSettings($pdoDb, $user_id, $channel_id, $data) {
    // Перевіряємо права
    $stmt = $pdoDb->prepare("SELECT role, settings FROM Wo_GroupChatUsers gcu JOIN Wo_GroupChat g ON g.group_id = gcu.group_id WHERE gcu.group_id = ? AND gcu.user_id = ?");
    $stmt->execute([$channel_id, $user_id]);
    $result = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$result || !in_array($result['role'], ['owner', 'admin'])) {
        return ['api_status' => 403, 'error_message' => 'You do not have permission to change settings'];
    }

    // Отримуємо поточні налаштування
    $current_settings = json_decode($result['settings'] ?? '{}', true);

    // Якщо надіслано JSON налаштувань
    if (isset($data['settings_json'])) {
        $new_settings = json_decode($data['settings_json'], true);
        if ($new_settings !== null) {
            // Оновлюємо існуючі налаштування новими
            $current_settings = array_merge($current_settings, $new_settings);
        }
    } else {
        // Підтримка старого формату (окремі параметри)
        $settings_to_update = [
            'allow_comments',
            'allow_reactions',
            'allow_shares',
            'show_statistics',
            'notify_subscribers_new_post',
            'auto_delete_posts_days',
            'signature_enabled',
            'comments_moderation',
            'slow_mode_seconds'
        ];

        foreach ($settings_to_update as $key) {
            if (isset($data[$key])) {
                $current_settings[$key] = $data[$key];
            }
        }
    }

    $stmt = $pdoDb->prepare("UPDATE Wo_GroupChat SET settings = ? WHERE group_id = ?");
    $stmt->execute([json_encode($current_settings), $channel_id]);

    return ['api_status' => 200, 'message' => 'Settings updated successfully'];
}

/**
 * Отримати статистику каналу
 */
function getChannelStatistics($pdoDb, $user_id, $channel_id) {
    // Перевіряємо права
    $stmt = $pdoDb->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$channel_id, $user_id]);
    $role = $stmt->fetchColumn();

    if (!in_array($role, ['owner', 'admin'])) {
        return ['api_status' => 403, 'error_message' => 'Only admins can view statistics'];
    }

    // Загальна статистика
    $stmt = $pdoDb->prepare("
        SELECT
            subscribers_count,
            posts_count,
            (SELECT COUNT(*) FROM Wo_Messages WHERE group_id = ? AND time > UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 7 DAY))) AS posts_last_week,
            (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = ? AND last_seen > UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 1 DAY))) AS active_subscribers_24h
        FROM Wo_GroupChat
        WHERE group_id = ?
    ");
    $stmt->execute([$channel_id, $channel_id, $channel_id]);
    $stats = $stmt->fetch(PDO::FETCH_ASSOC);

    // Топ-3 поста за переглядами
    $stmt = $pdoDb->prepare("
        SELECT
            m.id,
            m.text,
            (SELECT COUNT(*) FROM Wo_MessageViews WHERE message_id = m.id) AS views
        FROM Wo_Messages m
        WHERE m.group_id = ?
        ORDER BY views DESC
        LIMIT 3
    ");
    $stmt->execute([$channel_id]);
    $top_posts = $stmt->fetchAll(PDO::FETCH_ASSOC);

    return [
        'api_status' => 200,
        'statistics' => [
            'subscribers_count' => $stats['subscribers_count'],
            'posts_count' => $stats['posts_count'],
            'posts_last_week' => $stats['posts_last_week'],
            'active_subscribers_24h' => $stats['active_subscribers_24h'],
            'top_posts' => $top_posts
        ]
    ];
}

/**
 * Завантажити аватар каналу
 */
function uploadChannelAvatar($pdoDb, $user_id, $channel_id, $files) {
    global $sqlConnect, $wo;

    try {
        logChannelMessage("Upload avatar request: channel_id=$channel_id, user_id=$user_id", 'INFO');

        // Перевіряємо права
        $stmt = $pdoDb->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
        $stmt->execute([$channel_id, $user_id]);
        $role = $stmt->fetchColumn();

        logChannelMessage("User role: $role", 'DEBUG');

        if (!in_array($role, ['owner', 'admin'])) {
            return ['api_status' => 403, 'error_message' => 'You do not have permission to change avatar'];
        }

        // Перевіряємо чи є файл
        if (empty($files['avatar']) || !isset($files['avatar']['tmp_name'])) {
            logChannelMessage("Avatar file missing in upload", 'ERROR');
            return ['api_status' => 400, 'error_message' => 'Avatar file is required'];
        }

        logChannelMessage("File received: " . $files['avatar']['name'] . ", size: " . $files['avatar']['size'], 'DEBUG');

        // CRITICAL: Set $wo['user'] with actual user data for Wo_ShareFile()
        // Wo_ShareFile() checks $wo['user']['user_id'] and other fields
        // Wo_IsAdmin() (called by Wo_ShareFile on line 5477) checks $wo['user']['admin'] (line 5729)
        $wo['user'] = [
            'user_id' => $user_id,
            'username' => 'channel_admin',
            'active' => '1',
            'admin' => 0  // Required by Wo_IsAdmin()
        ];

        // Визначаємо реальний MIME type файлу (Android може надсилати generic "image/*")
        $real_mime_type = $files['avatar']['type'];

        // Якщо MIME type generic (image/*, application/*) або некоректний, визначаємо його з файлу
        if (strpos($real_mime_type, '*') !== false || empty($real_mime_type)) {
            if (function_exists('mime_content_type')) {
                $detected_mime = mime_content_type($files['avatar']['tmp_name']);
                if ($detected_mime) {
                    $real_mime_type = $detected_mime;
                    logChannelMessage("Detected MIME type from file: $detected_mime", 'DEBUG');
                }
            } elseif (function_exists('finfo_open')) {
                $finfo = finfo_open(FILEINFO_MIME_TYPE);
                $detected_mime = finfo_file($finfo, $files['avatar']['tmp_name']);
                finfo_close($finfo);
                if ($detected_mime) {
                    $real_mime_type = $detected_mime;
                    logChannelMessage("Detected MIME type from finfo: $detected_mime", 'DEBUG');
                }
            } else {
                // Fallback: визначаємо по розширенню файлу
                $extension = strtolower(pathinfo($files['avatar']['name'], PATHINFO_EXTENSION));
                $mime_map = [
                    'jpg' => 'image/jpeg',
                    'jpeg' => 'image/jpeg',
                    'png' => 'image/png',
                    'gif' => 'image/gif',
                    'webp' => 'image/webp'
                ];
                if (isset($mime_map[$extension])) {
                    $real_mime_type = $mime_map[$extension];
                    logChannelMessage("Detected MIME type from extension: $real_mime_type", 'DEBUG');
                }
            }
        }

        logChannelMessage("Original MIME: {$files['avatar']['type']}, Real MIME: $real_mime_type", 'DEBUG');

        // Підготовка файлу для завантаження
        $file_info = array(
            'file' => $files['avatar']['tmp_name'],
            'name' => $files['avatar']['name'],
            'size' => $files['avatar']['size'],
            'type' => $real_mime_type,  // Використовуємо реальний MIME type
            'types' => 'jpg,png,jpeg,gif,webp'
        );

        // Перевіряємо чи функція Wo_ShareFile доступна
        if (!function_exists('Wo_ShareFile')) {
            logChannelMessage("Wo_ShareFile function not loaded", 'ERROR');
            return ['api_status' => 500, 'error_message' => 'Server configuration error: upload function not available'];
        }

        // ============================================
        // DEBUGGING: Log all parameters for Wo_ShareFile()
        // ============================================
        logChannelMessage("=== Wo_ShareFile() Debug Info ===", 'DEBUG');
        logChannelMessage("User ID: $user_id", 'DEBUG');
        logChannelMessage("File info: " . json_encode($file_info), 'DEBUG');
        logChannelMessage("\$wo['user']: " . json_encode($wo['user']), 'DEBUG');
        logChannelMessage("\$wo['loggedin']: " . ($wo['loggedin'] ? 'true' : 'false'), 'DEBUG');

        // Log critical config values that Wo_ShareFile() checks
        if (isset($wo['config'])) {
            logChannelMessage("Config loaded: YES", 'DEBUG');
            logChannelMessage("fileSharing: " . ($wo['config']['fileSharing'] ?? 'NOT SET'), 'DEBUG');
            logChannelMessage("maxUpload: " . ($wo['config']['maxUpload'] ?? 'NOT SET'), 'DEBUG');
            logChannelMessage("allowedExtenstion: " . ($wo['config']['allowedExtenstion'] ?? 'NOT SET'), 'DEBUG');
            logChannelMessage("mime_types: " . ($wo['config']['mime_types'] ?? 'NOT SET'), 'DEBUG');
        } else {
            logChannelMessage("Config loaded: NO - \$wo['config'] is not set!", 'ERROR');
        }

        // Check file exists and is readable
        if (!file_exists($file_info['file'])) {
            logChannelMessage("ERROR: Temp file does not exist: " . $file_info['file'], 'ERROR');
        } else {
            logChannelMessage("Temp file exists: " . $file_info['file'], 'DEBUG');
            logChannelMessage("Temp file size: " . filesize($file_info['file']) . " bytes", 'DEBUG');
        }
        logChannelMessage("=== End Debug Info ===", 'DEBUG');

        // Використовуємо функцію WoWonder для завантаження
        $upload = Wo_ShareFile($file_info);

        logChannelMessage("Wo_ShareFile() returned: " . json_encode($upload), 'DEBUG');

        if (empty($upload) || empty($upload['filename'])) {
            logChannelMessage("Wo_ShareFile failed: " . json_encode($upload), 'ERROR');
            return ['api_status' => 500, 'error_message' => 'Failed to upload avatar'];
        }

        $avatar_url = $upload['filename'];
        logChannelMessage("File uploaded successfully: $avatar_url", 'INFO');

        // Оновлюємо аватар в БД
        // NOTE: Wo_GroupChat uses 'group_id' as PRIMARY KEY, not 'id'
        $stmt = $pdoDb->prepare("UPDATE Wo_GroupChat SET avatar = ? WHERE group_id = ?");
        if (!$stmt->execute([$avatar_url, $channel_id])) {
            logChannelMessage("Database update failed", 'ERROR');
            return ['api_status' => 500, 'error_message' => 'Failed to update avatar in database'];
        }

        logChannelMessage("User $user_id uploaded avatar for channel $channel_id: $avatar_url", 'INFO');

        return [
            'api_status' => 200,
            'message' => 'Avatar uploaded successfully',
            'avatar_url' => $avatar_url
        ];
    } catch (Exception $e) {
        logChannelMessage("Exception in uploadChannelAvatar: " . $e->getMessage(), 'ERROR');
        return [
            'api_status' => 500,
            'error_message' => 'Server error: ' . $e->getMessage()
        ];
    }
}

?>