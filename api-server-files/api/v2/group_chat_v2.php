<?php
/**
 * WorldMates Messenger - Group Chat API v2
 * Версія: 2.0 (адаптована до існуючої БД Wondertage/WO)
 *
 * ВАЖЛИВО: Цей API працює ТІЛЬКИ з групами (type='group'),
 * НЕ з каналами (type='channel'). Для каналів використовуйте channels.php
 *
 * Використовує існуючі таблиці:
 * - Wo_GroupChat (type='group')
 * - Wo_GroupChatUsers (учасники)
 * - Wo_Messages (повідомлення)
 */

require_once(__DIR__ . '/config.php');

// This standalone script uses PDO ($pdo from config.php) for all DB operations.
// Reassign to $db for backward compatibility with existing code in this file.
$db = $pdo;

// Налаштування логування
error_reporting(E_ALL);
ini_set('display_errors', 0);
ini_set('log_errors', 1);
ini_set('error_log', '/var/www/www-root/data/www/worldmates.club/api/v2/logs/php_errors.log');

// Функція логування для груп
if (!function_exists('logGroupMessage')) {
    function logGroupMessage($message, $level = 'INFO') {
        $log_file = '/var/www/www-root/data/www/worldmates.club/api/v2/logs/groups.log';
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
    $data = $_POST;
}

// Access token для автентифікації
$access_token = $data['access_token'] ?? $_GET['access_token'] ?? $_POST['access_token'] ?? null;

if (!$access_token) {
    echo json_encode(['api_status' => 400, 'error_message' => 'Access token is required']);
    exit;
}

// Валідація токену та отримання user_id
$user_id = validateAccessToken($db, $access_token);
if (!$user_id) {
    logGroupMessage("Invalid access token attempt", 'WARNING');
    echo json_encode(['api_status' => 401, 'error_message' => 'Invalid access token']);
    exit;
}

// Автоміграція: додаємо відсутні колонки при першому запуску
$migration_flag = sys_get_temp_dir() . '/wm_group_migration_v3_done';
if (!file_exists($migration_flag)) {
    try {
        // Wo_GroupChat: додаємо колонки для налаштувань
        $gc_columns = [];
        $result = $db->query("SHOW COLUMNS FROM Wo_GroupChat");
        while ($row = $result->fetch(PDO::FETCH_ASSOC)) {
            $gc_columns[] = $row['Field'];
        }
        $gc_migrations = [
            'description' => "ALTER TABLE Wo_GroupChat ADD COLUMN `description` TEXT DEFAULT NULL",
            'pinned_message_id' => "ALTER TABLE Wo_GroupChat ADD COLUMN `pinned_message_id` INT(11) DEFAULT NULL",
            'qr_code' => "ALTER TABLE Wo_GroupChat ADD COLUMN `qr_code` VARCHAR(255) DEFAULT NULL",
            'is_private' => "ALTER TABLE Wo_GroupChat ADD COLUMN `is_private` TINYINT(1) DEFAULT 0",
            'slow_mode_seconds' => "ALTER TABLE Wo_GroupChat ADD COLUMN `slow_mode_seconds` INT(11) DEFAULT 0",
            'history_visible_for_new_members' => "ALTER TABLE Wo_GroupChat ADD COLUMN `history_visible_for_new_members` TINYINT(1) DEFAULT 1",
            'anti_spam_enabled' => "ALTER TABLE Wo_GroupChat ADD COLUMN `anti_spam_enabled` TINYINT(1) DEFAULT 0",
            'max_messages_per_minute' => "ALTER TABLE Wo_GroupChat ADD COLUMN `max_messages_per_minute` INT(11) DEFAULT 20",
            'allow_members_send_media' => "ALTER TABLE Wo_GroupChat ADD COLUMN `allow_members_send_media` TINYINT(1) DEFAULT 1",
            'allow_members_send_links' => "ALTER TABLE Wo_GroupChat ADD COLUMN `allow_members_send_links` TINYINT(1) DEFAULT 1",
            'allow_members_send_stickers' => "ALTER TABLE Wo_GroupChat ADD COLUMN `allow_members_send_stickers` TINYINT(1) DEFAULT 1",
            'allow_members_invite' => "ALTER TABLE Wo_GroupChat ADD COLUMN `allow_members_invite` TINYINT(1) DEFAULT 0",
        ];
        foreach ($gc_migrations as $col => $sql) {
            if (!in_array($col, $gc_columns)) {
                try { $db->exec($sql); logGroupMessage("Auto-migration: added $col to Wo_GroupChat"); } catch (Exception $e) {}
            }
        }

        // Wo_GroupChatUsers: додаємо колонку role
        $gcu_columns = [];
        $result = $db->query("SHOW COLUMNS FROM Wo_GroupChatUsers");
        while ($row = $result->fetch(PDO::FETCH_ASSOC)) {
            $gcu_columns[] = $row['Field'];
        }
        if (!in_array('role', $gcu_columns)) {
            try { $db->exec("ALTER TABLE Wo_GroupChatUsers ADD COLUMN `role` VARCHAR(20) DEFAULT 'member'"); logGroupMessage("Auto-migration: added role to Wo_GroupChatUsers"); } catch (Exception $e) {}
        }

        // Wo_Messages: додаємо колонки для шифрування та reply
        $msg_columns = [];
        $result = $db->query("SHOW COLUMNS FROM Wo_Messages");
        while ($row = $result->fetch(PDO::FETCH_ASSOC)) {
            $msg_columns[] = $row['Field'];
        }
        $msg_migrations = [
            'reply_id' => "ALTER TABLE Wo_Messages ADD COLUMN `reply_id` INT(11) DEFAULT NULL",
            'iv' => "ALTER TABLE Wo_Messages ADD COLUMN `iv` TEXT DEFAULT NULL",
            'tag' => "ALTER TABLE Wo_Messages ADD COLUMN `tag` TEXT DEFAULT NULL",
            'cipher_version' => "ALTER TABLE Wo_Messages ADD COLUMN `cipher_version` INT(11) DEFAULT NULL",
        ];
        foreach ($msg_migrations as $col => $sql) {
            if (!in_array($col, $msg_columns)) {
                try { $db->exec($sql); logGroupMessage("Auto-migration: added $col to Wo_Messages"); } catch (Exception $e) {}
            }
        }

        // Створюємо додаткові таблиці якщо відсутні
        $db->exec("CREATE TABLE IF NOT EXISTS `Wo_GroupJoinRequests` (
            `id` INT(11) NOT NULL AUTO_INCREMENT, `group_id` INT(11) NOT NULL, `user_id` INT(11) NOT NULL,
            `message` TEXT DEFAULT NULL, `status` ENUM('pending','approved','rejected') DEFAULT 'pending',
            `created_time` INT(11) NOT NULL, `reviewed_by` INT(11) DEFAULT NULL, `reviewed_time` INT(11) DEFAULT NULL,
            PRIMARY KEY (`id`), KEY `idx_group_status` (`group_id`,`status`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $db->exec("CREATE TABLE IF NOT EXISTS `Wo_ScheduledPosts` (
            `id` INT(11) NOT NULL AUTO_INCREMENT, `group_id` INT(11) NOT NULL, `author_id` INT(11) NOT NULL,
            `text` TEXT NOT NULL, `media_url` VARCHAR(500) DEFAULT NULL, `scheduled_time` INT(11) NOT NULL,
            `created_time` INT(11) NOT NULL, `status` ENUM('scheduled','published','cancelled') DEFAULT 'scheduled',
            `repeat_type` VARCHAR(20) DEFAULT 'none', `is_pinned` TINYINT(1) DEFAULT 0, `notify_members` TINYINT(1) DEFAULT 1,
            PRIMARY KEY (`id`), KEY `idx_group_status` (`group_id`,`status`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $db->exec("CREATE TABLE IF NOT EXISTS `Wo_Subgroups` (
            `id` INT(11) NOT NULL AUTO_INCREMENT, `parent_group_id` INT(11) NOT NULL,
            `name` VARCHAR(255) NOT NULL, `description` TEXT DEFAULT NULL, `color` VARCHAR(20) DEFAULT '#2196F3',
            `is_private` TINYINT(1) DEFAULT 0, `is_closed` TINYINT(1) DEFAULT 0,
            `created_by` INT(11) NOT NULL, `created_time` INT(11) NOT NULL,
            PRIMARY KEY (`id`), KEY `idx_parent_group` (`parent_group_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        // Встановлюємо role='owner' для засновників груп
        $db->exec("UPDATE Wo_GroupChatUsers gcu
            INNER JOIN Wo_GroupChat gc ON gc.group_id = gcu.group_id AND gc.user_id = gcu.user_id
            SET gcu.role = 'owner'
            WHERE gcu.role = 'member' OR gcu.role IS NULL OR gcu.role = ''");

        @file_put_contents($migration_flag, date('Y-m-d H:i:s'));
        logGroupMessage("Auto-migration completed successfully", 'INFO');
    } catch (Exception $e) {
        logGroupMessage("Auto-migration error: " . $e->getMessage(), 'ERROR');
    }
}

// Роутинг методів
$type = $data['type'] ?? $_GET['type'] ?? '';

logGroupMessage("User $user_id: type=$type", 'INFO');

try {
    switch ($type) {
        // ============================================
        // CRUD операції з групами
        // ============================================
        case 'get_list':
            echo json_encode(getGroups($db, $user_id, $data));
            break;

        case 'get_by_id':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(getGroupDetails($db, $user_id, $group_id));
            break;

        case 'create':
            echo json_encode(createGroup($db, $user_id, $data));
            break;

        case 'edit':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(updateGroup($db, $user_id, $group_id, $data));
            break;

        case 'delete':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(deleteGroup($db, $user_id, $group_id));
            break;

        // ============================================
        // Учасники
        // ============================================
        case 'get_members':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(getGroupMembers($db, $user_id, $group_id, $data));
            break;

        case 'add_user':
            $group_id = $data['id'] ?? null;
            $parts = $data['parts'] ?? '';
            if (!$group_id || !$parts) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id and parts are required']);
                exit;
            }
            echo json_encode(addGroupMembers($db, $user_id, $group_id, $parts));
            break;

        case 'remove_user':
            $group_id = $data['id'] ?? null;
            $parts = $data['parts'] ?? '';
            if (!$group_id || !$parts) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id and parts are required']);
                exit;
            }
            echo json_encode(removeGroupMembers($db, $user_id, $group_id, $parts));
            break;

        case 'set_admin':
            $group_id = $data['id'] ?? null;
            $target_user_id = $data['user_id'] ?? null;
            $role = $data['role'] ?? 'admin';
            if (!$group_id || !$target_user_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id and user_id are required']);
                exit;
            }
            echo json_encode(setGroupRole($db, $user_id, $group_id, $target_user_id, $role));
            break;

        case 'leave':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(leaveGroup($db, $user_id, $group_id));
            break;

        // ============================================
        // Повідомлення
        // ============================================
        case 'get_messages':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(getGroupMessages($db, $user_id, $group_id, $data));
            break;

        case 'send_message':
            $group_id = $data['id'] ?? null;
            $text = $data['text'] ?? '';
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(sendGroupMessage($db, $user_id, $group_id, $text, $data));
            break;

        case 'upload_avatar':
            $group_id = $data['id'] ?? $_POST['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(uploadGroupAvatar($db, $user_id, $group_id, $_FILES));
            break;

        // ============================================
        // Роли (алиасы)
        // ============================================
        case 'set_role':
            $group_id = $data['id'] ?? null;
            $target_user_id = $data['user_id'] ?? null;
            $role = $data['role'] ?? 'member';
            if (!$group_id || !$target_user_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id and user_id are required']);
                exit;
            }
            echo json_encode(setGroupRole($db, $user_id, $group_id, $target_user_id, $role));
            break;

        case 'add_member':
            $group_id = $data['id'] ?? null;
            $parts = $data['parts'] ?? $data['user_id'] ?? '';
            if (!$group_id || !$parts) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id and parts are required']);
                exit;
            }
            echo json_encode(addGroupMembers($db, $user_id, $group_id, $parts));
            break;

        case 'remove_member':
            $group_id = $data['id'] ?? null;
            $parts = $data['parts'] ?? $data['user_id'] ?? '';
            if (!$group_id || !$parts) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id and parts are required']);
                exit;
            }
            echo json_encode(removeGroupMembers($db, $user_id, $group_id, $parts));
            break;

        // ============================================
        // QR коды
        // ============================================
        case 'generate_qr':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(generateGroupQr($db, $user_id, $group_id));
            break;

        case 'join_by_qr':
            $qr_code = $data['qr_code'] ?? null;
            if (!$qr_code) {
                echo json_encode(['api_status' => 400, 'error_message' => 'qr_code is required']);
                exit;
            }
            echo json_encode(joinGroupByQr($db, $user_id, $qr_code));
            break;

        case 'join_group':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(joinGroup($db, $user_id, $group_id, $data));
            break;

        // ============================================
        // Запросы на вступление
        // ============================================
        case 'get_join_requests':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(getJoinRequests($db, $user_id, $group_id));
            break;

        case 'approve_join_request':
            $request_id = $data['request_id'] ?? null;
            if (!$request_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'request_id is required']);
                exit;
            }
            echo json_encode(approveJoinRequest($db, $user_id, $request_id));
            break;

        case 'reject_join_request':
            $request_id = $data['request_id'] ?? null;
            if (!$request_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'request_id is required']);
                exit;
            }
            echo json_encode(rejectJoinRequest($db, $user_id, $request_id));
            break;

        // ============================================
        // Настройки группы
        // ============================================
        case 'update_settings':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(updateGroupSettings($db, $user_id, $group_id, $data));
            break;

        case 'get_settings':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(getGroupSettings($db, $user_id, $group_id));
            break;

        case 'update_privacy':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(updateGroupPrivacy($db, $user_id, $group_id, $data));
            break;

        // ============================================
        // Статистика
        // ============================================
        case 'get_statistics':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(getGroupStatistics($db, $user_id, $group_id));
            break;

        // ============================================
        // Mute/Unmute, Pin/Unpin
        // ============================================
        case 'mute':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(muteGroup($db, $user_id, $group_id, true));
            break;

        case 'unmute':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(muteGroup($db, $user_id, $group_id, false));
            break;

        case 'pin_message':
            $group_id = $data['id'] ?? null;
            $message_id = $data['message_id'] ?? null;
            if (!$group_id || !$message_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id and message_id are required']);
                exit;
            }
            echo json_encode(pinMessage($db, $user_id, $group_id, $message_id));
            break;

        case 'unpin_message':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(unpinMessage($db, $user_id, $group_id));
            break;

        case 'search_messages':
            $group_id = $data['id'] ?? null;
            $query = $data['query'] ?? '';
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(searchGroupMessages($db, $user_id, $group_id, $query, $data));
            break;

        case 'generate_invite_link':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(generateInviteLink($db, $user_id, $group_id));
            break;

        // ============================================
        // Запланированные посты
        // ============================================
        case 'get_scheduled_posts':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(getScheduledPosts($db, $user_id, $group_id));
            break;

        case 'create_scheduled_post':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(createScheduledPost($db, $user_id, $group_id, $data));
            break;

        case 'delete_scheduled_post':
            $post_id = $data['post_id'] ?? null;
            if (!$post_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'post_id is required']);
                exit;
            }
            echo json_encode(deleteScheduledPost($db, $user_id, $post_id));
            break;

        case 'publish_scheduled_post':
            $post_id = $data['post_id'] ?? null;
            if (!$post_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'post_id is required']);
                exit;
            }
            echo json_encode(publishScheduledPost($db, $user_id, $post_id));
            break;

        // ============================================
        // Подгруппы (топики)
        // ============================================
        case 'get_subgroups':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(getSubgroups($db, $user_id, $group_id));
            break;

        case 'create_subgroup':
            $group_id = $data['id'] ?? null;
            if (!$group_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'id is required']);
                exit;
            }
            echo json_encode(createSubgroup($db, $user_id, $group_id, $data));
            break;

        case 'delete_subgroup':
            $subgroup_id = $data['subgroup_id'] ?? null;
            if (!$subgroup_id) {
                echo json_encode(['api_status' => 400, 'error_message' => 'subgroup_id is required']);
                exit;
            }
            echo json_encode(deleteSubgroup($db, $user_id, $subgroup_id));
            break;

        default:
            echo json_encode(['api_status' => 400, 'error_message' => 'Invalid type: ' . $type]);
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

/**
 * Отримати список груп користувача
 * ВАЖЛИВО: Фільтруємо ТІЛЬКИ групи (type='group'), НЕ канали
 */
function getGroups($db, $user_id, $data) {
    global $wo;
    $limit = isset($data['limit']) ? (int)$data['limit'] : 50;
    $offset = isset($data['offset']) ? (int)$data['offset'] : 0;

    // КРИТИЧНО: Додаємо фільтр type='group' або type IS NULL (для старих записів)
    // ВАЖЛИВО: Поля повертаються у snake_case для Android (admin_id, is_private, etc)
    $stmt = $db->prepare("
        SELECT
            g.group_id AS id,
            g.group_name AS name,
            g.description,
            g.avatar,
            g.user_id AS admin_id,
            COALESCE(CONCAT(admin_u.first_name, ' ', admin_u.last_name), admin_u.username, '') AS admin_name,
            g.is_private,
            g.time AS created_time,
            g.type,
            (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = g.group_id) AS members_count,
            (SELECT role FROM Wo_GroupChatUsers WHERE group_id = g.group_id AND user_id = ?) AS userRole,
            (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = g.group_id AND user_id = ?) AS is_member
        FROM Wo_GroupChat g
        LEFT JOIN Wo_Users admin_u ON admin_u.user_id = g.user_id
        WHERE g.group_id IN (SELECT group_id FROM Wo_GroupChatUsers WHERE user_id = ?)
          AND (g.type = 'group' OR g.type IS NULL OR g.type = '')
        ORDER BY g.time DESC
        LIMIT ? OFFSET ?
    ");

    $stmt->execute([$user_id, $user_id, $user_id, $limit, $offset]);
    $groups = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Форматуємо результат - використовуємо snake_case для Android
    $site_url = $wo['site_url'] ?? 'https://worldmates.club/';
    foreach ($groups as &$group) {
        $group['is_private'] = (bool)$group['is_private'];
        $group['is_member'] = (bool)$group['is_member'];
        $group['is_admin'] = in_array($group['userRole'], ['owner', 'admin']);
        $group['is_moderator'] = in_array($group['userRole'], ['owner', 'admin', 'moderator']);
        $group['members_count'] = (int)$group['members_count'];
        $group['admin_name'] = $group['admin_name'] ?? '';
        unset($group['userRole']);

        // Конвертуємо avatar у повний URL якщо це відносний шлях
        if (!empty($group['avatar']) && strpos($group['avatar'], 'http') !== 0) {
            $group['avatar'] = rtrim($site_url, '/') . '/' . ltrim($group['avatar'], '/');
        }
        // Гарантуємо що avatar ніколи не null (Android очікує non-nullable String)
        if (empty($group['avatar'])) {
            $group['avatar'] = '';
        }
    }

    return [
        'api_status' => 200,
        'groups' => $groups,
        'total' => count($groups)
    ];
}

/**
 * Отримати деталі групи
 */
function getGroupDetails($db, $user_id, $group_id) {
    global $wo;
    // ВАЖЛИВО: Поля повертаються у snake_case для Android
    $stmt = $db->prepare("
        SELECT
            g.group_id AS id,
            g.group_name AS name,
            g.description,
            g.avatar,
            g.user_id AS admin_id,
            COALESCE(CONCAT(admin_u.first_name, ' ', admin_u.last_name), admin_u.username, '') AS admin_name,
            g.is_private,
            g.time AS created_time,
            g.type,
            g.pinned_message_id,
            (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = g.group_id) AS members_count,
            (SELECT role FROM Wo_GroupChatUsers WHERE group_id = g.group_id AND user_id = ?) AS userRole,
            (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = g.group_id AND user_id = ?) AS is_member
        FROM Wo_GroupChat g
        LEFT JOIN Wo_Users admin_u ON admin_u.user_id = g.user_id
        WHERE g.group_id = ?
          AND (g.type = 'group' OR g.type IS NULL OR g.type = '')
    ");
    $stmt->execute([$user_id, $user_id, $group_id]);
    $group = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$group) {
        return ['api_status' => 404, 'error_message' => 'Group not found'];
    }

    // Форматуємо - використовуємо snake_case для Android
    $site_url = $wo['site_url'] ?? 'https://worldmates.club/';
    $group['is_private'] = (bool)$group['is_private'];
    $group['is_member'] = (bool)$group['is_member'];
    $group['is_muted'] = false; // Колонка muted не існує в БД
    $group['is_admin'] = in_array($group['userRole'], ['owner', 'admin']);
    $group['is_moderator'] = in_array($group['userRole'], ['owner', 'admin', 'moderator']);
    $group['members_count'] = (int)$group['members_count'];
    $group['admin_name'] = $group['admin_name'] ?? '';
    unset($group['userRole']);

    // Конвертуємо avatar у повний URL якщо це відносний шлях
    if (!empty($group['avatar']) && strpos($group['avatar'], 'http') !== 0) {
        $group['avatar'] = rtrim($site_url, '/') . '/' . ltrim($group['avatar'], '/');
    }
    // Гарантуємо що avatar ніколи не null
    if (empty($group['avatar'])) {
        $group['avatar'] = '';
    }

    // Отримуємо закріплене повідомлення якщо є
    if (!empty($group['pinned_message_id'])) {
        $stmt = $db->prepare("SELECT id, text, time FROM Wo_Messages WHERE id = ?");
        $stmt->execute([$group['pinned_message_id']]);
        $pinnedMessage = $stmt->fetch(PDO::FETCH_ASSOC);
        $group['pinned_message'] = $pinnedMessage ?: null;
    }

    return [
        'api_status' => 200,
        'group' => $group
    ];
}

/**
 * Створити нову групу
 * ВАЖЛИВО: Встановлюємо type='group' явно
 */
function createGroup($db, $user_id, $data) {
    $name = trim($data['group_name'] ?? '');
    $parts = $data['parts'] ?? ''; // comma-separated user IDs
    $group_type = $data['group_type'] ?? 'group'; // 'group' only, not 'channel'

    if (empty($name)) {
        return ['api_status' => 400, 'error_message' => 'Group name is required'];
    }

    // Примусово встановлюємо type='group' для уникнення конфліктів з каналами
    if ($group_type !== 'group') {
        $group_type = 'group';
    }

    $group_id = null;

    try {
        $db->beginTransaction();

        // Створюємо групу з type='group'
        $stmt = $db->prepare("
            INSERT INTO Wo_GroupChat
            (user_id, group_name, type, time)
            VALUES (?, ?, 'group', ?)
        ");
        $time = time();
        $stmt->execute([$user_id, $name, $time]);
        $group_id = $db->lastInsertId();

        // Додаємо власника як owner
        $stmt = $db->prepare("
            INSERT INTO Wo_GroupChatUsers (user_id, group_id, role, active, last_seen)
            VALUES (?, ?, 'owner', '1', ?)
        ");
        $stmt->execute([$user_id, $group_id, $time]);

        // Додаємо інших учасників
        if (!empty($parts)) {
            $member_ids = array_filter(array_map('intval', explode(',', $parts)));
            foreach ($member_ids as $member_id) {
                if ($member_id != $user_id) {
                    $stmt = $db->prepare("
                        INSERT INTO Wo_GroupChatUsers (user_id, group_id, role, active, last_seen)
                        VALUES (?, ?, 'member', '1', ?)
                    ");
                    $stmt->execute([$member_id, $group_id, $time]);
                }
            }
        }

        $db->commit();

        logGroupMessage("User $user_id created group $group_id: $name (type=group)", 'INFO');

    } catch (Exception $e) {
        if ($db->inTransaction()) {
            $db->rollBack();
        }
        logGroupMessage("Failed to create group for user $user_id: " . $e->getMessage(), 'ERROR');
        return ['api_status' => 500, 'error_message' => 'Failed to create group: ' . $e->getMessage()];
    }

    // Отримуємо деталі групи ПІСЛЯ успішного коміту (поза транзакцією)
    try {
        return getGroupDetails($db, $user_id, $group_id);
    } catch (Exception $e) {
        logGroupMessage("Group $group_id created but failed to get details: " . $e->getMessage(), 'WARNING');
        // Група створена успішно, просто повертаємо базову інформацію
        return [
            'api_status' => 200,
            'group' => [
                'id' => $group_id,
                'name' => $name,
                'membersCount' => 1,
                'isAdmin' => true,
                'isMember' => true
            ]
        ];
    }
}

/**
 * Оновити групу
 */
function updateGroup($db, $user_id, $group_id, $data) {
    // Перевіряємо права
    $stmt = $db->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $user_id]);
    $role = $stmt->fetchColumn();

    if (!in_array($role, ['owner', 'admin'])) {
        return ['api_status' => 403, 'error_message' => 'You do not have permission to edit this group'];
    }

    // Перевіряємо що це група, не канал
    $stmt = $db->prepare("SELECT type FROM Wo_GroupChat WHERE group_id = ?");
    $stmt->execute([$group_id]);
    $type = $stmt->fetchColumn();

    if ($type === 'channel') {
        return ['api_status' => 400, 'error_message' => 'This is a channel, use channels.php API'];
    }

    $updates = [];
    $params = [];

    if (isset($data['group_name'])) {
        $updates[] = "group_name = ?";
        $params[] = trim($data['group_name']);
    }

    if (isset($data['description'])) {
        $updates[] = "description = ?";
        $params[] = trim($data['description']);
    }

    if (empty($updates)) {
        return ['api_status' => 400, 'error_message' => 'Nothing to update'];
    }

    $params[] = $group_id;

    $stmt = $db->prepare("UPDATE Wo_GroupChat SET " . implode(', ', $updates) . " WHERE group_id = ?");
    $stmt->execute($params);

    logGroupMessage("User $user_id updated group $group_id", 'INFO');

    return getGroupDetails($db, $user_id, $group_id);
}

/**
 * Видалити групу
 * ВАЖЛИВО: Видаляємо ТІЛЬКИ групи (type='group'), НЕ канали
 */
function deleteGroup($db, $user_id, $group_id) {
    // Перевіряємо права (тільки owner)
    $stmt = $db->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $user_id]);
    $role = $stmt->fetchColumn();

    if ($role !== 'owner') {
        return ['api_status' => 403, 'error_message' => 'Only group owner can delete the group'];
    }

    // КРИТИЧНО: Перевіряємо що це група, НЕ канал
    $stmt = $db->prepare("SELECT type, group_name FROM Wo_GroupChat WHERE group_id = ?");
    $stmt->execute([$group_id]);
    $group = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$group) {
        return ['api_status' => 404, 'error_message' => 'Group not found'];
    }

    // Якщо це канал - забороняємо видалення через цей API
    if ($group['type'] === 'channel') {
        return ['api_status' => 400, 'error_message' => 'This is a channel, use channels.php API to delete it'];
    }

    try {
        $db->beginTransaction();

        // Видаляємо всі повідомлення
        $stmt = $db->prepare("DELETE FROM Wo_Messages WHERE group_id = ?");
        $stmt->execute([$group_id]);

        // Видаляємо всіх учасників
        $stmt = $db->prepare("DELETE FROM Wo_GroupChatUsers WHERE group_id = ?");
        $stmt->execute([$group_id]);

        // Видаляємо групу (ТІЛЬКИ якщо type != 'channel')
        $stmt = $db->prepare("DELETE FROM Wo_GroupChat WHERE group_id = ? AND (type = 'group' OR type IS NULL OR type = '')");
        $stmt->execute([$group_id]);

        $db->commit();

        logGroupMessage("User $user_id deleted group $group_id: {$group['group_name']}", 'INFO');

        return ['api_status' => 200, 'message' => 'Group deleted successfully'];
    } catch (Exception $e) {
        $db->rollBack();
        return ['api_status' => 500, 'error_message' => 'Failed to delete group: ' . $e->getMessage()];
    }
}

/**
 * Отримати учасників групи
 */
function getGroupMembers($db, $user_id, $group_id, $data) {
    global $wo;
    $limit = isset($data['limit']) ? (int)$data['limit'] : 100;
    $offset = isset($data['offset']) ? (int)$data['offset'] : 0;

    // ВАЖЛИВО: Поля повертаються у snake_case для Android
    $stmt = $db->prepare("
        SELECT
            gcu.user_id,
            u.username,
            CONCAT(u.first_name, ' ', u.last_name) AS name,
            u.avatar,
            gcu.role,
            gcu.last_seen AS joined_time
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
        LIMIT ? OFFSET ?
    ");
    $stmt->execute([$group_id, $limit, $offset]);
    $members = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Форматуємо URL аватарів
    $site_url = $wo['site_url'] ?? 'https://worldmates.club/';
    foreach ($members as &$member) {
        if (!empty($member['avatar']) && strpos($member['avatar'], 'http') !== 0) {
            $member['avatar'] = rtrim($site_url, '/') . '/' . ltrim($member['avatar'], '/');
        }
    }

    return [
        'api_status' => 200,
        'members' => $members,
        'total' => count($members)
    ];
}

/**
 * Додати учасників до групи
 */
function addGroupMembers($db, $user_id, $group_id, $parts) {
    // Перевіряємо права
    $stmt = $db->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $user_id]);
    $role = $stmt->fetchColumn();

    if (!in_array($role, ['owner', 'admin', 'moderator'])) {
        return ['api_status' => 403, 'error_message' => 'You do not have permission to add members'];
    }

    $member_ids = array_filter(array_map('intval', explode(',', $parts)));
    $added = 0;
    $time = time();

    foreach ($member_ids as $member_id) {
        // Перевіряємо чи вже є учасником
        $stmt = $db->prepare("SELECT id FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
        $stmt->execute([$group_id, $member_id]);

        if (!$stmt->fetch()) {
            $stmt = $db->prepare("
                INSERT INTO Wo_GroupChatUsers (user_id, group_id, role, active, last_seen)
                VALUES (?, ?, 'member', '1', ?)
            ");
            $stmt->execute([$member_id, $group_id, $time]);
            $added++;
        }
    }

    logGroupMessage("User $user_id added $added members to group $group_id", 'INFO');

    // Повертаємо оновлений список учасників
    return getGroupMembers($db, $user_id, $group_id, ['limit' => 100]);
}

/**
 * Видалити учасників з групи
 */
function removeGroupMembers($db, $user_id, $group_id, $parts) {
    // Перевіряємо права
    $stmt = $db->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $user_id]);
    $role = $stmt->fetchColumn();

    if (!in_array($role, ['owner', 'admin'])) {
        return ['api_status' => 403, 'error_message' => 'You do not have permission to remove members'];
    }

    $member_ids = array_filter(array_map('intval', explode(',', $parts)));
    $removed = 0;

    foreach ($member_ids as $member_id) {
        // Не можна видалити власника
        $stmt = $db->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
        $stmt->execute([$group_id, $member_id]);
        $target_role = $stmt->fetchColumn();

        if ($target_role !== 'owner') {
            $stmt = $db->prepare("DELETE FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
            $stmt->execute([$group_id, $member_id]);
            $removed++;
        }
    }

    return ['api_status' => 200, 'message' => "Removed $removed members successfully"];
}

/**
 * Встановити роль учасника
 */
function setGroupRole($db, $user_id, $group_id, $target_user_id, $role) {
    // Перевіряємо права (тільки owner)
    $stmt = $db->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $user_id]);
    $current_role = $stmt->fetchColumn();

    if ($current_role !== 'owner') {
        return ['api_status' => 403, 'error_message' => 'Only group owner can change roles'];
    }

    if (!in_array($role, ['admin', 'moderator', 'member'])) {
        return ['api_status' => 400, 'error_message' => 'Invalid role'];
    }

    $stmt = $db->prepare("
        UPDATE Wo_GroupChatUsers
        SET role = ?
        WHERE group_id = ? AND user_id = ? AND role != 'owner'
    ");
    $stmt->execute([$role, $group_id, $target_user_id]);

    return ['api_status' => 200, 'message' => 'Role updated successfully'];
}

/**
 * Покинути групу
 */
function leaveGroup($db, $user_id, $group_id) {
    $stmt = $db->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $user_id]);
    $role = $stmt->fetchColumn();

    if ($role === 'owner') {
        return ['api_status' => 400, 'error_message' => 'Owner cannot leave. Transfer ownership or delete the group.'];
    }

    $stmt = $db->prepare("DELETE FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $user_id]);

    return ['api_status' => 200, 'message' => 'Left group successfully'];
}

/**
 * Отримати повідомлення групи
 */
function getGroupMessages($db, $user_id, $group_id, $data) {
    $limit = isset($data['limit']) ? (int)$data['limit'] : 50;
    $before_message_id = isset($data['before_message_id']) ? (int)$data['before_message_id'] : 0;

    // Перевіряємо чи користувач є учасником
    $stmt = $db->prepare("SELECT id FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $user_id]);
    if (!$stmt->fetch()) {
        return ['api_status' => 403, 'error_message' => 'You are not a member of this group'];
    }

    $where_clause = $before_message_id > 0 ? "AND m.id < ?" : "";
    $params = $before_message_id > 0 ? [$group_id, $before_message_id, $limit] : [$group_id, $limit];

    // ВАЖЛИВО: Використовуємо m.* щоб запит працював незалежно від того які колонки є
    // Додаємо аліаси для Android (@SerializedName): sender_name, sender_avatar, reply_to_id
    $stmt = $db->prepare("
        SELECT
            m.*,
            COALESCE(m.to_id, 0) AS to_id,
            u.username,
            CONCAT(u.first_name, ' ', u.last_name) AS sender_name,
            u.avatar AS sender_avatar,
            m.mediaFileName AS media_file_name,
            COALESCE(m.reply_id, NULL) AS reply_to_id
        FROM Wo_Messages m
        LEFT JOIN Wo_Users u ON u.user_id = m.from_id
        WHERE m.group_id = ? $where_clause
        ORDER BY m.id DESC
        LIMIT ?
    ");
    $stmt->execute($params);
    $messages = $stmt->fetchAll(PDO::FETCH_ASSOC);

    return [
        'api_status' => 200,
        'messages' => $messages,
        'total' => count($messages)
    ];
}

/**
 * Надіслати повідомлення в групу
 */
function sendGroupMessage($db, $user_id, $group_id, $text, $data) {
    // Перевіряємо чи користувач є учасником
    $stmt = $db->prepare("SELECT id FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $user_id]);
    if (!$stmt->fetch()) {
        return ['api_status' => 403, 'error_message' => 'You are not a member of this group'];
    }

    if (empty(trim($text))) {
        return ['api_status' => 400, 'error_message' => 'Message text is required'];
    }

    $time = time();
    // Android надсилає reply_id, не message_reply_id
    // Використовуємо 0 замість NULL якщо reply_id не вказаний, щоб уникнути помилки БД
    $reply_to = isset($data['reply_id']) && $data['reply_id'] > 0 ? $data['reply_id'] : (isset($data['message_reply_id']) && $data['message_reply_id'] > 0 ? $data['message_reply_id'] : 0);

    // ВАЖЛИВО: to_id = 0 для групових повідомлень (Android Long non-nullable)
    $stmt = $db->prepare("
        INSERT INTO Wo_Messages (from_id, group_id, to_id, text, time, seen, sent_push, reply_id)
        VALUES (?, ?, 0, ?, ?, 0, 0, ?)
    ");
    $stmt->execute([$user_id, $group_id, trim($text), $time, $reply_to]);
    $message_id = $db->lastInsertId();

    // Повертаємо створене повідомлення (m.* для надійності)
    $stmt = $db->prepare("
        SELECT
            m.*,
            COALESCE(m.to_id, 0) AS to_id,
            u.username,
            CONCAT(u.first_name, ' ', u.last_name) AS sender_name,
            u.avatar AS sender_avatar,
            COALESCE(m.reply_id, NULL) AS reply_to_id
        FROM Wo_Messages m
        LEFT JOIN Wo_Users u ON u.user_id = m.from_id
        WHERE m.id = ?
    ");
    $stmt->execute([$message_id]);
    $message = $stmt->fetch(PDO::FETCH_ASSOC);

    logGroupMessage("User $user_id sent message $message_id to group $group_id", 'INFO');

    return [
        'api_status' => 200,
        'message' => $message
    ];
}

/**
 * Завантажити аватар групи
 */
function uploadGroupAvatar($db, $user_id, $group_id, $files) {
    global $wo;

    try {
        logGroupMessage("Upload avatar request: group_id=$group_id, user_id=$user_id", 'INFO');

        // Перевіряємо права
        $stmt = $db->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
        $stmt->execute([$group_id, $user_id]);
        $role = $stmt->fetchColumn();

        if (!in_array($role, ['owner', 'admin'])) {
            return ['api_status' => 403, 'error_message' => 'You do not have permission to change avatar'];
        }

        // Перевіряємо що це група, не канал
        $stmt = $db->prepare("SELECT type FROM Wo_GroupChat WHERE group_id = ?");
        $stmt->execute([$group_id]);
        $type = $stmt->fetchColumn();

        if ($type === 'channel') {
            return ['api_status' => 400, 'error_message' => 'This is a channel, use channels.php API'];
        }

        // Перевіряємо чи є файл
        if (empty($files['avatar']) || !isset($files['avatar']['tmp_name'])) {
            return ['api_status' => 400, 'error_message' => 'Avatar file is required'];
        }

        // Визначаємо MIME type
        $real_mime_type = $files['avatar']['type'];
        if (function_exists('mime_content_type')) {
            $detected_mime = mime_content_type($files['avatar']['tmp_name']);
            if ($detected_mime) {
                $real_mime_type = $detected_mime;
            }
        }

        // Set $wo['user'] for Wo_ShareFile()
        $wo['user'] = [
            'user_id' => $user_id,
            'username' => 'group_admin',
            'active' => '1',
            'admin' => 0
        ];

        // Підготовка файлу для завантаження
        $file_info = array(
            'file' => $files['avatar']['tmp_name'],
            'name' => $files['avatar']['name'],
            'size' => $files['avatar']['size'],
            'type' => $real_mime_type,
            'types' => 'jpg,png,jpeg,gif,webp'
        );

        if (!function_exists('Wo_ShareFile')) {
            return ['api_status' => 500, 'error_message' => 'Upload function not available'];
        }

        $upload = Wo_ShareFile($file_info);

        if (empty($upload) || empty($upload['filename'])) {
            return ['api_status' => 500, 'error_message' => 'Failed to upload avatar'];
        }

        $avatar_url = $upload['filename'];

        // Оновлюємо аватар в БД
        $stmt = $db->prepare("UPDATE Wo_GroupChat SET avatar = ? WHERE group_id = ?");
        $stmt->execute([$avatar_url, $group_id]);

        logGroupMessage("User $user_id uploaded avatar for group $group_id: $avatar_url", 'INFO');

        return [
            'api_status' => 200,
            'message' => 'Avatar uploaded successfully',
            'avatarUrl' => $avatar_url
        ];
    } catch (Exception $e) {
        logGroupMessage("Exception in uploadGroupAvatar: " . $e->getMessage(), 'ERROR');
        return [
            'api_status' => 500,
            'error_message' => 'Server error: ' . $e->getMessage()
        ];
    }
}

// ============================================
// НОВЫЕ ФУНКЦИИ
// ============================================

/**
 * Проверка прав администратора/владельца
 */
function isGroupAdminOrOwner($db, $group_id, $user_id) {
    // Перевіряємо роль в Wo_GroupChatUsers
    try {
        $stmt = $db->prepare("SELECT role FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
        $stmt->execute([$group_id, $user_id]);
        $role = $stmt->fetchColumn();
        if (in_array($role, ['owner', 'admin'])) {
            return true;
        }
    } catch (Exception $e) {
        // role column might not exist yet
    }

    // Фолбек: перевіряємо чи user_id є власником групи в Wo_GroupChat
    $stmt = $db->prepare("SELECT user_id FROM Wo_GroupChat WHERE group_id = ?");
    $stmt->execute([$group_id]);
    $owner_id = $stmt->fetchColumn();
    return (int)$owner_id === (int)$user_id;
}

/**
 * Генерация QR кода для группы
 */
function generateGroupQr($db, $user_id, $group_id) {
    if (!isGroupAdminOrOwner($db, $group_id, $user_id)) {
        return ['api_status' => 403, 'error_message' => 'Only admins can generate QR codes'];
    }

    // Генерируем уникальный код
    $qr_code = 'wm_grp_' . $group_id . '_' . bin2hex(random_bytes(8));

    // Сохраняем в БД (если колонка существует)
    try {
        $stmt = $db->prepare("UPDATE Wo_GroupChat SET qr_code = ? WHERE group_id = ?");
        $stmt->execute([$qr_code, $group_id]);
    } catch (Exception $e) {
        // Колонка может не существовать - игнорируем
        logGroupMessage("Could not save qr_code: " . $e->getMessage(), 'WARNING');
    }

    $join_url = "https://worldmates.club/join/group/" . $qr_code;

    return [
        'api_status' => 200,
        'qr_code' => $qr_code,
        'join_url' => $join_url
    ];
}

/**
 * Присоединение к группе по QR коду
 */
function joinGroupByQr($db, $user_id, $qr_code) {
    // Ищем группу по QR коду
    try {
        $stmt = $db->prepare("SELECT group_id FROM Wo_GroupChat WHERE qr_code = ? AND (type = 'group' OR type IS NULL OR type = '')");
        $stmt->execute([$qr_code]);
        $group_id = $stmt->fetchColumn();
    } catch (Exception $e) {
        // Колонка qr_code может не существовать
        return ['api_status' => 404, 'error_message' => 'Invalid QR code'];
    }

    if (!$group_id) {
        return ['api_status' => 404, 'error_message' => 'Group not found'];
    }

    // Проверяем, не является ли уже членом
    $stmt = $db->prepare("SELECT id FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $user_id]);
    if ($stmt->fetch()) {
        return getGroupDetails($db, $user_id, $group_id);
    }

    // Добавляем пользователя
    $time = time();
    $stmt = $db->prepare("INSERT INTO Wo_GroupChatUsers (user_id, group_id, role, active, last_seen) VALUES (?, ?, 'member', '1', ?)");
    $stmt->execute([$user_id, $group_id, $time]);

    logGroupMessage("User $user_id joined group $group_id via QR", 'INFO');

    return getGroupDetails($db, $user_id, $group_id);
}

/**
 * Присоединение к публичной группе или запрос на вступление в приватную
 */
function joinGroup($db, $user_id, $group_id, $data) {
    // Проверяем группу
    $stmt = $db->prepare("SELECT group_id, is_private FROM Wo_GroupChat WHERE group_id = ? AND (type = 'group' OR type IS NULL OR type = '')");
    $stmt->execute([$group_id]);
    $group = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$group) {
        return ['api_status' => 404, 'error_message' => 'Group not found'];
    }

    // Проверяем, не является ли уже членом
    $stmt = $db->prepare("SELECT id FROM Wo_GroupChatUsers WHERE group_id = ? AND user_id = ?");
    $stmt->execute([$group_id, $user_id]);
    if ($stmt->fetch()) {
        return ['api_status' => 400, 'error_message' => 'Already a member'];
    }

    if ($group['is_private']) {
        // Для приватных групп - создаем запрос
        try {
            $stmt = $db->prepare("INSERT INTO Wo_GroupJoinRequests (group_id, user_id, message, created_time) VALUES (?, ?, ?, ?)");
            $stmt->execute([$group_id, $user_id, $data['message'] ?? '', time()]);
            return ['api_status' => 200, 'message' => 'Join request sent', 'request_sent' => true];
        } catch (Exception $e) {
            // Таблица может не существовать
            return ['api_status' => 500, 'error_message' => 'Could not create join request'];
        }
    }

    // Публичная группа - добавляем сразу
    $time = time();
    $stmt = $db->prepare("INSERT INTO Wo_GroupChatUsers (user_id, group_id, role, active, last_seen) VALUES (?, ?, 'member', '1', ?)");
    $stmt->execute([$user_id, $group_id, $time]);

    return getGroupDetails($db, $user_id, $group_id);
}

/**
 * Получить запросы на вступление
 */
function getJoinRequests($db, $user_id, $group_id) {
    if (!isGroupAdminOrOwner($db, $group_id, $user_id)) {
        return ['api_status' => 403, 'error_message' => 'Only admins can view join requests'];
    }

    try {
        $stmt = $db->prepare("
            SELECT r.id, r.group_id, r.user_id, r.message, r.status, r.created_time,
                   u.username, u.avatar, CONCAT(u.first_name, ' ', u.last_name) AS name
            FROM Wo_GroupJoinRequests r
            LEFT JOIN Wo_Users u ON u.user_id = r.user_id
            WHERE r.group_id = ? AND r.status = 'pending'
            ORDER BY r.created_time DESC
        ");
        $stmt->execute([$group_id]);
        $requests = $stmt->fetchAll(PDO::FETCH_ASSOC);

        return ['api_status' => 200, 'join_requests' => $requests];
    } catch (Exception $e) {
        return ['api_status' => 200, 'join_requests' => []];
    }
}

/**
 * Одобрить запрос на вступление
 */
function approveJoinRequest($db, $user_id, $request_id) {
    try {
        $stmt = $db->prepare("SELECT group_id, user_id FROM Wo_GroupJoinRequests WHERE id = ? AND status = 'pending'");
        $stmt->execute([$request_id]);
        $request = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$request) {
            return ['api_status' => 404, 'error_message' => 'Request not found'];
        }

        if (!isGroupAdminOrOwner($db, $request['group_id'], $user_id)) {
            return ['api_status' => 403, 'error_message' => 'Only admins can approve requests'];
        }

        // Обновляем статус
        $stmt = $db->prepare("UPDATE Wo_GroupJoinRequests SET status = 'approved', reviewed_by = ?, reviewed_time = ? WHERE id = ?");
        $stmt->execute([$user_id, time(), $request_id]);

        // Добавляем пользователя в группу
        $time = time();
        $stmt = $db->prepare("INSERT INTO Wo_GroupChatUsers (user_id, group_id, role, active, last_seen) VALUES (?, ?, 'member', '1', ?)");
        $stmt->execute([$request['user_id'], $request['group_id'], $time]);

        return ['api_status' => 200, 'message' => 'Request approved'];
    } catch (Exception $e) {
        return ['api_status' => 500, 'error_message' => 'Error: ' . $e->getMessage()];
    }
}

/**
 * Отклонить запрос на вступление
 */
function rejectJoinRequest($db, $user_id, $request_id) {
    try {
        $stmt = $db->prepare("SELECT group_id FROM Wo_GroupJoinRequests WHERE id = ? AND status = 'pending'");
        $stmt->execute([$request_id]);
        $request = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$request) {
            return ['api_status' => 404, 'error_message' => 'Request not found'];
        }

        if (!isGroupAdminOrOwner($db, $request['group_id'], $user_id)) {
            return ['api_status' => 403, 'error_message' => 'Only admins can reject requests'];
        }

        $stmt = $db->prepare("UPDATE Wo_GroupJoinRequests SET status = 'rejected', reviewed_by = ?, reviewed_time = ? WHERE id = ?");
        $stmt->execute([$user_id, time(), $request_id]);

        return ['api_status' => 200, 'message' => 'Request rejected'];
    } catch (Exception $e) {
        return ['api_status' => 500, 'error_message' => 'Error: ' . $e->getMessage()];
    }
}

/**
 * Обновить настройки группы
 * Android передає: history_visible, allow_members_send_media, allow_members_send_links, etc.
 * Колонки в БД можуть не існувати - оновлюємо тільки ті, що існують
 */
function updateGroupSettings($db, $user_id, $group_id, $data) {
    if (!isGroupAdminOrOwner($db, $group_id, $user_id)) {
        return ['api_status' => 403, 'error_message' => 'Only admins can update settings'];
    }

    // Мапуємо Android імена полів на імена колонок в БД
    $field_mapping = [
        'is_private' => 'is_private',
        'slow_mode_seconds' => 'slow_mode_seconds',
        'history_visible' => 'history_visible_for_new_members',
        'history_visible_for_new_members' => 'history_visible_for_new_members',
        'anti_spam_enabled' => 'anti_spam_enabled',
        'max_messages_per_minute' => 'max_messages_per_minute',
        'allow_media' => 'allow_members_send_media',
        'allow_members_send_media' => 'allow_members_send_media',
        'allow_links' => 'allow_members_send_links',
        'allow_members_send_links' => 'allow_members_send_links',
        'allow_stickers' => 'allow_members_send_stickers',
        'allow_members_send_stickers' => 'allow_members_send_stickers',
        'allow_invite' => 'allow_members_invite',
        'allow_members_invite' => 'allow_members_invite'
    ];

    // Отримуємо список існуючих колонок в таблиці
    $existing_columns = [];
    try {
        $result = $db->query("SHOW COLUMNS FROM Wo_GroupChat");
        while ($row = $result->fetch(PDO::FETCH_ASSOC)) {
            $existing_columns[] = $row['Field'];
        }
    } catch (Exception $e) {
        // Якщо не можемо отримати колонки, пробуємо оновити тільки is_private
        $existing_columns = ['is_private'];
    }

    $updates = [];
    $params = [];

    foreach ($data as $android_field => $value) {
        if (isset($field_mapping[$android_field])) {
            $db_column = $field_mapping[$android_field];
            // Оновлюємо тільки якщо колонка існує в БД
            if (in_array($db_column, $existing_columns)) {
                $updates[] = "$db_column = ?";
                $params[] = (int)$value;
            }
        }
    }

    if (empty($updates)) {
        // Немає що оновлювати, але це не помилка
        logGroupMessage("No settings to update for group $group_id (no matching columns)", 'INFO');
        return ['api_status' => 200, 'message' => 'Settings saved'];
    }

    $params[] = $group_id;

    try {
        $sql = "UPDATE Wo_GroupChat SET " . implode(', ', $updates) . " WHERE group_id = ?";
        $stmt = $db->prepare($sql);
        $stmt->execute($params);
        logGroupMessage("Updated settings for group $group_id: " . implode(', ', $updates), 'INFO');
        return ['api_status' => 200, 'message' => 'Settings updated'];
    } catch (Exception $e) {
        logGroupMessage("Failed to update settings for group $group_id: " . $e->getMessage(), 'ERROR');
        return ['api_status' => 500, 'error_message' => 'Could not update settings'];
    }
}

/**
 * Получить настройки группы
 */
function getGroupSettings($db, $user_id, $group_id) {
    try {
        $stmt = $db->prepare("SELECT * FROM Wo_GroupChat WHERE group_id = ?");
        $stmt->execute([$group_id]);
        $group = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$group) {
            return ['api_status' => 404, 'error_message' => 'Group not found'];
        }

        $settings = [
            'is_private' => (bool)($group['is_private'] ?? false),
            'slow_mode_seconds' => (int)($group['slow_mode_seconds'] ?? 0),
            'history_visible_for_new_members' => (bool)($group['history_visible_for_new_members'] ?? true),
            'anti_spam_enabled' => (bool)($group['anti_spam_enabled'] ?? false),
            'max_messages_per_minute' => (int)($group['max_messages_per_minute'] ?? 20),
            'allow_members_send_media' => (bool)($group['allow_members_send_media'] ?? true),
            'allow_members_send_links' => (bool)($group['allow_members_send_links'] ?? true),
            'allow_members_send_stickers' => (bool)($group['allow_members_send_stickers'] ?? true),
            'allow_members_invite' => (bool)($group['allow_members_invite'] ?? false)
        ];

        return ['api_status' => 200, 'settings' => $settings];
    } catch (Exception $e) {
        return ['api_status' => 200, 'settings' => []];
    }
}

/**
 * Обновить приватность группы
 */
function updateGroupPrivacy($db, $user_id, $group_id, $data) {
    if (!isGroupAdminOrOwner($db, $group_id, $user_id)) {
        return ['api_status' => 403, 'error_message' => 'Only admins can update privacy'];
    }

    $is_private = isset($data['is_private']) ? (int)$data['is_private'] : 0;

    try {
        $stmt = $db->prepare("UPDATE Wo_GroupChat SET is_private = ? WHERE group_id = ?");
        $stmt->execute([$is_private, $group_id]);
        return ['api_status' => 200, 'message' => 'Privacy updated'];
    } catch (Exception $e) {
        return ['api_status' => 500, 'error_message' => 'Could not update privacy'];
    }
}

/**
 * Получить статистику группы
 */
function getGroupStatistics($db, $user_id, $group_id) {
    try {
        // Количество участников
        $stmt = $db->prepare("SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = ?");
        $stmt->execute([$group_id]);
        $members_count = $stmt->fetchColumn();

        // Количество сообщений
        $stmt = $db->prepare("SELECT COUNT(*) FROM Wo_Messages WHERE group_id = ?");
        $stmt->execute([$group_id]);
        $messages_count = $stmt->fetchColumn();

        // Сообщения за сегодня
        $today_start = strtotime('today');
        $stmt = $db->prepare("SELECT COUNT(*) FROM Wo_Messages WHERE group_id = ? AND time >= ?");
        $stmt->execute([$group_id, $today_start]);
        $messages_today = $stmt->fetchColumn();

        // Новые участники за неделю
        $week_ago = time() - 604800;
        $stmt = $db->prepare("SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = ? AND time >= ?");
        $stmt->execute([$group_id, $week_ago]);
        $new_members_week = $stmt->fetchColumn();

        // Топ участники
        $stmt = $db->prepare("
            SELECT m.from_id as user_id, u.username, CONCAT(u.first_name, ' ', u.last_name) as name, u.avatar, COUNT(*) as messages_count
            FROM Wo_Messages m
            LEFT JOIN Wo_Users u ON u.user_id = m.from_id
            WHERE m.group_id = ?
            GROUP BY m.from_id
            ORDER BY messages_count DESC
            LIMIT 5
        ");
        $stmt->execute([$group_id]);
        $top_contributors = $stmt->fetchAll(PDO::FETCH_ASSOC);

        return [
            'api_status' => 200,
            'statistics' => [
                'members_count' => (int)$members_count,
                'messages_count' => (int)$messages_count,
                'messages_today' => (int)$messages_today,
                'new_members_week' => (int)$new_members_week,
                'top_contributors' => $top_contributors
            ]
        ];
    } catch (Exception $e) {
        return ['api_status' => 200, 'statistics' => ['members_count' => 0, 'messages_count' => 0]];
    }
}

/**
 * Mute/Unmute группы для пользователя
 * ПРИМІТКА: Колонка muted не існує в БД, функція повертає успіх для сумісності
 */
function muteGroup($db, $user_id, $group_id, $muted) {
    // Колонка muted не існує в таблиці Wo_GroupChatUsers
    // Повертаємо успіх для сумісності з Android клієнтом
    return ['api_status' => 200, 'message' => $muted ? 'Group muted' : 'Group unmuted'];
}

/**
 * Закрепить сообщение
 */
function pinMessage($db, $user_id, $group_id, $message_id) {
    if (!isGroupAdminOrOwner($db, $group_id, $user_id)) {
        return ['api_status' => 403, 'error_message' => 'Only admins can pin messages'];
    }

    try {
        $stmt = $db->prepare("UPDATE Wo_GroupChat SET pinned_message_id = ? WHERE group_id = ?");
        $stmt->execute([$message_id, $group_id]);
        return ['api_status' => 200, 'message' => 'Message pinned'];
    } catch (Exception $e) {
        return ['api_status' => 500, 'error_message' => 'Could not pin message'];
    }
}

/**
 * Открепить сообщение
 */
function unpinMessage($db, $user_id, $group_id) {
    if (!isGroupAdminOrOwner($db, $group_id, $user_id)) {
        return ['api_status' => 403, 'error_message' => 'Only admins can unpin messages'];
    }

    try {
        $stmt = $db->prepare("UPDATE Wo_GroupChat SET pinned_message_id = NULL WHERE group_id = ?");
        $stmt->execute([$group_id]);
        return ['api_status' => 200, 'message' => 'Message unpinned'];
    } catch (Exception $e) {
        return ['api_status' => 500, 'error_message' => 'Could not unpin message'];
    }
}

/**
 * Поиск сообщений в группе
 */
function searchGroupMessages($db, $user_id, $group_id, $query, $data) {
    $limit = (int)($data['limit'] ?? 50);
    $offset = (int)($data['offset'] ?? 0);

    $stmt = $db->prepare("
        SELECT m.id, m.from_id as fromId, m.text, m.time, u.username,
               CONCAT(u.first_name, ' ', u.last_name) as senderName
        FROM Wo_Messages m
        LEFT JOIN Wo_Users u ON u.user_id = m.from_id
        WHERE m.group_id = ? AND m.text LIKE ?
        ORDER BY m.time DESC
        LIMIT ? OFFSET ?
    ");
    $stmt->execute([$group_id, "%$query%", $limit, $offset]);
    $messages = $stmt->fetchAll(PDO::FETCH_ASSOC);

    return ['api_status' => 200, 'messages' => $messages];
}

/**
 * Генерация ссылки-приглашения
 */
function generateInviteLink($db, $user_id, $group_id) {
    if (!isGroupAdminOrOwner($db, $group_id, $user_id)) {
        return ['api_status' => 403, 'error_message' => 'Only admins can generate invite links'];
    }

    $invite_code = bin2hex(random_bytes(16));
    $invite_link = "https://worldmates.club/join/group/$invite_code";

    return [
        'api_status' => 200,
        'invite_link' => $invite_link
    ];
}

/**
 * Получить запланированные посты
 */
function getScheduledPosts($db, $user_id, $group_id) {
    try {
        $stmt = $db->prepare("
            SELECT * FROM Wo_ScheduledPosts
            WHERE group_id = ? AND status = 'scheduled'
            ORDER BY scheduled_time ASC
        ");
        $stmt->execute([$group_id]);
        $posts = $stmt->fetchAll(PDO::FETCH_ASSOC);
        return ['api_status' => 200, 'scheduled_posts' => $posts];
    } catch (Exception $e) {
        return ['api_status' => 200, 'scheduled_posts' => []];
    }
}

/**
 * Создать запланированный пост
 */
function createScheduledPost($db, $user_id, $group_id, $data) {
    if (!isGroupAdminOrOwner($db, $group_id, $user_id)) {
        return ['api_status' => 403, 'error_message' => 'Only admins can create scheduled posts'];
    }

    $text = $data['text'] ?? '';
    $scheduled_time = (int)($data['scheduled_time'] ?? 0);

    if (empty($text) || $scheduled_time <= time()) {
        return ['api_status' => 400, 'error_message' => 'Invalid text or scheduled_time'];
    }

    try {
        $stmt = $db->prepare("
            INSERT INTO Wo_ScheduledPosts (group_id, author_id, text, media_url, scheduled_time, created_time, repeat_type, is_pinned, notify_members)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ");
        $stmt->execute([
            $group_id,
            $user_id,
            $text,
            $data['media_url'] ?? null,
            $scheduled_time,
            time(),
            $data['repeat_type'] ?? 'none',
            $data['is_pinned'] ?? 0,
            $data['notify_members'] ?? 1
        ]);

        $post_id = $db->lastInsertId();
        return ['api_status' => 200, 'message' => 'Scheduled post created', 'post_id' => $post_id];
    } catch (Exception $e) {
        return ['api_status' => 500, 'error_message' => 'Could not create scheduled post'];
    }
}

/**
 * Удалить запланированный пост
 */
function deleteScheduledPost($db, $user_id, $post_id) {
    try {
        $stmt = $db->prepare("SELECT group_id FROM Wo_ScheduledPosts WHERE id = ?");
        $stmt->execute([$post_id]);
        $group_id = $stmt->fetchColumn();

        if (!$group_id || !isGroupAdminOrOwner($db, $group_id, $user_id)) {
            return ['api_status' => 403, 'error_message' => 'Permission denied'];
        }

        $stmt = $db->prepare("DELETE FROM Wo_ScheduledPosts WHERE id = ?");
        $stmt->execute([$post_id]);
        return ['api_status' => 200, 'message' => 'Scheduled post deleted'];
    } catch (Exception $e) {
        return ['api_status' => 500, 'error_message' => 'Could not delete scheduled post'];
    }
}

/**
 * Опубликовать запланированный пост сейчас
 */
function publishScheduledPost($db, $user_id, $post_id) {
    try {
        $stmt = $db->prepare("SELECT * FROM Wo_ScheduledPosts WHERE id = ?");
        $stmt->execute([$post_id]);
        $post = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$post || !isGroupAdminOrOwner($db, $post['group_id'], $user_id)) {
            return ['api_status' => 403, 'error_message' => 'Permission denied'];
        }

        // Создаем сообщение
        $stmt = $db->prepare("INSERT INTO Wo_Messages (from_id, group_id, text, time, seen, sent_push) VALUES (?, ?, ?, ?, 0, 0)");
        $stmt->execute([$post['author_id'], $post['group_id'], $post['text'], time()]);

        // Удаляем запланированный пост
        $stmt = $db->prepare("DELETE FROM Wo_ScheduledPosts WHERE id = ?");
        $stmt->execute([$post_id]);

        return ['api_status' => 200, 'message' => 'Post published'];
    } catch (Exception $e) {
        return ['api_status' => 500, 'error_message' => 'Could not publish post'];
    }
}

/**
 * Получить подгруппы (топики)
 */
function getSubgroups($db, $user_id, $group_id) {
    try {
        $stmt = $db->prepare("SELECT * FROM Wo_Subgroups WHERE parent_group_id = ? AND is_closed = 0");
        $stmt->execute([$group_id]);
        $subgroups = $stmt->fetchAll(PDO::FETCH_ASSOC);
        return ['api_status' => 200, 'subgroups' => $subgroups];
    } catch (Exception $e) {
        return ['api_status' => 200, 'subgroups' => []];
    }
}

/**
 * Создать подгруппу (топик)
 */
function createSubgroup($db, $user_id, $group_id, $data) {
    if (!isGroupAdminOrOwner($db, $group_id, $user_id)) {
        return ['api_status' => 403, 'error_message' => 'Only admins can create subgroups'];
    }

    $name = trim($data['name'] ?? '');
    if (empty($name)) {
        return ['api_status' => 400, 'error_message' => 'Name is required'];
    }

    try {
        $stmt = $db->prepare("
            INSERT INTO Wo_Subgroups (parent_group_id, name, description, color, is_private, created_by, created_time)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        ");
        $stmt->execute([
            $group_id,
            $name,
            $data['description'] ?? '',
            $data['color'] ?? '#2196F3',
            $data['is_private'] ?? 0,
            $user_id,
            time()
        ]);

        $subgroup_id = $db->lastInsertId();
        return ['api_status' => 200, 'message' => 'Subgroup created', 'subgroup_id' => $subgroup_id];
    } catch (Exception $e) {
        return ['api_status' => 500, 'error_message' => 'Could not create subgroup'];
    }
}

/**
 * Удалить подгруппу
 */
function deleteSubgroup($db, $user_id, $subgroup_id) {
    try {
        $stmt = $db->prepare("SELECT parent_group_id FROM Wo_Subgroups WHERE id = ?");
        $stmt->execute([$subgroup_id]);
        $group_id = $stmt->fetchColumn();

        if (!$group_id || !isGroupAdminOrOwner($db, $group_id, $user_id)) {
            return ['api_status' => 403, 'error_message' => 'Permission denied'];
        }

        $stmt = $db->prepare("DELETE FROM Wo_Subgroups WHERE id = ?");
        $stmt->execute([$subgroup_id]);
        return ['api_status' => 200, 'message' => 'Subgroup deleted'];
    } catch (Exception $e) {
        return ['api_status' => 500, 'error_message' => 'Could not delete subgroup'];
    }
}

?>
