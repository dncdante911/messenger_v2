<?php
/**
 * ========================================
 * WORLDMATES GROUP CHAT API V2
 * ========================================
 *
 * Власний API для групових чатів, написаний з нуля
 * Простий, зрозумілий, надійний код
 *
 * ЕНДПОІНТИ:
 * - create        - створити групу
 * - get_list      - список груп користувача
 * - get_by_id     - деталі групи
 * - send_message  - надіслати повідомлення
 * - get_messages  - отримати повідомлення
 * - add_member    - додати учасника
 * - remove_member - видалити учасника
 * - leave         - вийти з групи
 * - delete        - видалити групу
 *
 * @author WorldMates Team
 * @version 2.0
 */

// Підключаємо модуль шифрування AES-256-GCM
require_once(__DIR__ . '/crypto_helper.php');

// Налаштування БД (використовуємо ті самі константи що в config.php)
if (!defined('DB_HOST')) define('DB_HOST', 'localhost');
if (!defined('DB_NAME')) define('DB_NAME', 'socialhub');
if (!defined('DB_USER')) define('DB_USER', 'social');
if (!defined('DB_PASS')) define('DB_PASS', '3344Frzaq0607DmC157');
if (!defined('DB_CHARSET')) define('DB_CHARSET', 'utf8mb4');

// Налаштування для розробки (вимкніть на продакшені!)
error_reporting(E_ALL);
ini_set('display_errors', 0); // Не показувати помилки в браузері
ini_set('log_errors', 1);
ini_set('error_log', '/var/www/www-root/data/www/worldmates.club/api/v2/logs/php_errors.log');

// Заголовки
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

// Функції для group_chat_v2.php
// Перевіряємо чи не оголошені вже в config.php

if (!function_exists('logMessage')) {
    function logMessage($message, $level = 'INFO') {
        $log_file = '/var/www/www-root/data/www/worldmates.club/api/v2/logs/group_chat_v2.log';
        $timestamp = date('Y-m-d H:i:s');
        $log_entry = "[{$timestamp}] [{$level}] {$message}\n";
        @file_put_contents($log_file, $log_entry, FILE_APPEND);
    }
}

// sendError з порядком параметрів для group_chat_v2.php: ($code, $message)
if (!function_exists('sendError')) {
    function sendError($code, $message) {
        http_response_code($code);
        echo json_encode([
            'api_status' => $code,
            'error_code' => $code,
            'error_message' => $message
        ], JSON_UNESCAPED_UNICODE);
        exit;
    }
}

// sendResponse - специфічна для group_chat_v2.php
if (!function_exists('sendResponse')) {
    function sendResponse($data) {
        echo json_encode($data, JSON_UNESCAPED_UNICODE);
        exit();
    }
}

// ==============================================
// АВТОРИЗАЦІЯ
// ==============================================

logMessage("=== NEW REQUEST ===");
logMessage("Method: {$_SERVER['REQUEST_METHOD']}");
logMessage("URI: {$_SERVER['REQUEST_URI']}");
logMessage("POST: " . json_encode($_POST));
logMessage("GET: " . json_encode($_GET));

// Перевірка access_token
if (empty($_GET['access_token'])) {
    logMessage("ERROR: access_token missing");
    sendError(401, 'access_token is required');
}

$access_token = $_GET['access_token'];
logMessage("Access token: " . substr($access_token, 0, 10) . "...");

// Підключення до БД
try {
    $pdoDb = new PDO(
        "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=utf8mb4",
        DB_USER,
        DB_PASS,
        array(
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false
        )
    );
    logMessage("DB connected successfully");
} catch (PDOException $e) {
    logMessage("DB ERROR: " . $e->getMessage());
    sendError(500, 'Database connection failed');
}

// Отримуємо користувача з токену (session_id з Wo_AppsSessions)
try {
    // Крок 1: Перевіряємо сесію в Wo_AppsSessions
    $stmt = $pdoDb->prepare("
        SELECT user_id, platform, time
        FROM Wo_AppsSessions
        WHERE session_id = ?
        LIMIT 1
    ");
    $stmt->execute([$access_token]);
    $session = $stmt->fetch();

    if (!$session) {
        logMessage("ERROR: Invalid session_id (access_token)");
        sendError(401, 'Invalid access_token - session not found');
    }

    $current_user_id = $session['user_id'];
    logMessage("Session found: user_id={$current_user_id}, platform={$session['platform']}");

    // Крок 2: Отримуємо дані користувача
    $stmt = $pdoDb->prepare("
        SELECT user_id, username, email, first_name, last_name, avatar, active
        FROM Wo_Users
        WHERE user_id = ? AND active = '1'
        LIMIT 1
    ");
    $stmt->execute([$current_user_id]);
    $user = $stmt->fetch();

    if (!$user) {
        logMessage("ERROR: User not found or inactive: user_id={$current_user_id}");
        sendError(401, 'User not found or inactive');
    }

    $user['name'] = trim($user['first_name'] . ' ' . $user['last_name']);
    logMessage("User authenticated: ID={$current_user_id}, username={$user['username']}");

} catch (PDOException $e) {
    logMessage("AUTH ERROR: " . $e->getMessage());
    sendError(500, 'Authentication failed');
}

// ==============================================
// РОУТИНГ
// ==============================================

$type = isset($_POST['type']) ? $_POST['type'] : '';
logMessage("Action type: $type");

switch ($type) {

    // ==========================================
    // CREATE - Створити групу
    // ==========================================
    case 'create':
        logMessage("--- CREATE GROUP ---");

        // Валідація
        if (empty($_POST['group_name'])) {
            sendError(400, 'group_name is required');
        }

        $group_name = trim($_POST['group_name']);
        $group_type = isset($_POST['group_type']) ? $_POST['group_type'] : 'group';
        $parts = isset($_POST['parts']) ? $_POST['parts'] : '';

        // Перевірка довжини назви
        $name_length = mb_strlen($group_name, 'UTF-8');
        if ($name_length < 4 || $name_length > 25) {
            sendError(400, 'group_name must be between 4 and 25 characters');
        }

        logMessage("Group name: $group_name");
        logMessage("Group type: $group_type");
        logMessage("Parts: $parts");

        try {
            // Створюємо групу
            $time = time();
            $stmt = $pdoDb->prepare("
                INSERT INTO Wo_GroupChat (user_id, group_name, avatar, time, type)
                VALUES (?, ?, 'upload/photos/d-group.jpg', ?, ?)
            ");
            $stmt->execute([$current_user_id, $group_name, $time, $group_type]);
            $group_id = $pdoDb->lastInsertId();

            logMessage("Group created: ID=$group_id");

            // Додаємо учасників
            $member_ids = array_filter(array_map('trim', explode(',', $parts)));

            // Завжди додаємо створювача
            if (!in_array($current_user_id, $member_ids)) {
                $member_ids[] = $current_user_id;
            }

            logMessage("Adding members: " . implode(',', $member_ids));

            $stmt = $pdoDb->prepare("
                INSERT INTO Wo_GroupChatUsers (user_id, group_id, active)
                VALUES (?, ?, '1')
            ");

            foreach ($member_ids as $member_id) {
                if (!empty($member_id) && is_numeric($member_id)) {
                    $stmt->execute([$member_id, $group_id]);
                }
            }

            // Отримуємо створену групу
            $stmt = $pdoDb->prepare("
                SELECT g.*, u.username as creator_username, u.avatar as creator_avatar
                FROM Wo_GroupChat g
                LEFT JOIN Wo_Users u ON g.user_id = u.user_id
                WHERE g.group_id = ?
            ");
            $stmt->execute([$group_id]);
            $group = $stmt->fetch();

            // Отримуємо учасників
            $stmt = $pdoDb->prepare("
                SELECT u.user_id, u.username, u.avatar,
                       CONCAT(u.first_name, ' ', u.last_name) as name
                FROM Wo_GroupChatUsers gcu
                LEFT JOIN Wo_Users u ON gcu.user_id = u.user_id
                WHERE gcu.group_id = ? AND gcu.active = '1'
            ");
            $stmt->execute([$group_id]);
            $members = $stmt->fetchAll();

            $group['members'] = $members;
            $group['members_count'] = count($members);

            logMessage("SUCCESS: Group created with " . count($members) . " members");

            sendResponse(array(
                'api_status' => 200,
                'message' => 'Group created successfully',
                'data' => $group
            ));

        } catch (PDOException $e) {
            logMessage("CREATE ERROR: " . $e->getMessage());
            sendError(500, 'Failed to create group: ' . $e->getMessage());
        }
        break;

    // ==========================================
    // GET_LIST - Список груп користувача
    // ==========================================
    case 'get_list':
        logMessage("--- GET LIST ---");

        $limit = isset($_POST['limit']) ? intval($_POST['limit']) : 50;
        $offset = isset($_POST['offset']) ? intval($_POST['offset']) : 0;

        try {
            $stmt = $pdoDb->prepare("
                SELECT
                    g.group_id as id,
                    g.group_name as name,
                    g.avatar,
                    g.description,
                    g.user_id as admin_id,
                    CONCAT(u.first_name, ' ', u.last_name) as admin_name,
                    g.time as created_time,
                    g.time as updated_time,
                    (SELECT COUNT(*) FROM Wo_GroupChatUsers WHERE group_id = g.group_id AND active = '1') as members_count,
                    (g.user_id = ?) as is_admin,
                    0 as is_private,
                    1 as is_member
                FROM Wo_GroupChat g
                LEFT JOIN Wo_Users u ON g.user_id = u.user_id
                WHERE g.group_id IN (
                    SELECT group_id FROM Wo_GroupChatUsers
                    WHERE user_id = ? AND active = '1'
                )
                ORDER BY g.time DESC
                LIMIT ? OFFSET ?
            ");
            $stmt->execute([$current_user_id, $current_user_id, $limit, $offset]);
            $groups = $stmt->fetchAll();

            // Конвертуємо числові поля в boolean для правильного JSON
            foreach ($groups as &$group) {
                $group['is_admin'] = (bool)$group['is_admin'];
                $group['is_private'] = (bool)$group['is_private'];
                $group['is_member'] = (bool)$group['is_member'];
                $group['members_count'] = (int)$group['members_count'];
            }

            logMessage("Found " . count($groups) . " groups");

            sendResponse(array(
                'api_status' => 200,
                'groups' => $groups
            ));

        } catch (PDOException $e) {
            logMessage("GET_LIST ERROR: " . $e->getMessage());
            sendError(500, 'Failed to get groups');
        }
        break;

    // ==========================================
    // GET_BY_ID - Деталі групи
    // ==========================================
    case 'get_by_id':
        logMessage("--- GET BY ID ---");

        if (empty($_POST['id'])) {
            sendError(400, 'id is required');
        }

        $group_id = intval($_POST['id']);

        try {
            // Перевіряємо чи користувач є учасником
            $stmt = $pdoDb->prepare("
                SELECT * FROM Wo_GroupChatUsers
                WHERE group_id = ? AND user_id = ? AND active = '1'
            ");
            $stmt->execute([$group_id, $current_user_id]);
            if (!$stmt->fetch()) {
                sendError(403, 'You are not a member of this group');
            }

            // Отримуємо групу
            $stmt = $pdoDb->prepare("
                SELECT g.*, u.username as creator_username, u.avatar as creator_avatar
                FROM Wo_GroupChat g
                LEFT JOIN Wo_Users u ON g.user_id = u.user_id
                WHERE g.group_id = ?
            ");
            $stmt->execute([$group_id]);
            $group = $stmt->fetch();

            if (!$group) {
                sendError(404, 'Group not found');
            }

            // Отримуємо учасників
            $stmt = $pdoDb->prepare("
                SELECT u.user_id, u.username, u.avatar,
                       CONCAT(u.first_name, ' ', u.last_name) as name
                FROM Wo_GroupChatUsers gcu
                LEFT JOIN Wo_Users u ON gcu.user_id = u.user_id
                WHERE gcu.group_id = ? AND gcu.active = '1'
            ");
            $stmt->execute([$group_id]);
            $members = $stmt->fetchAll();

            $group['members'] = $members;
            $group['members_count'] = count($members);

            // 📌 PINNED MESSAGE: Отримуємо закріплене повідомлення якщо є
            $group['pinned_message'] = null;
            if (!empty($group['pinned_message_id'])) {
                $stmt = $pdoDb->prepare("
                    SELECT m.id, m.from_id, m.text, m.time, m.media,
                           u.username as sender_username,
                           CONCAT(u.first_name, ' ', u.last_name) as sender_name,
                           u.avatar as sender_avatar
                    FROM Wo_Messages m
                    LEFT JOIN Wo_Users u ON m.from_id = u.user_id
                    WHERE m.id = ?
                ");
                $stmt->execute([$group['pinned_message_id']]);
                $pinned_msg = $stmt->fetch();

                if ($pinned_msg) {
                    $group['pinned_message'] = $pinned_msg;
                    logMessage("📌 Group {$group_id} has pinned message: {$pinned_msg['id']}");
                }
            }

            sendResponse(array(
                'api_status' => 200,
                'data' => $group
            ));

        } catch (PDOException $e) {
            logMessage("GET_BY_ID ERROR: " . $e->getMessage());
            sendError(500, 'Failed to get group');
        }
        break;

    // ==========================================
    // SEND_MESSAGE - Надіслати повідомлення
    // ==========================================
    case 'send_message':
        logMessage("--- SEND MESSAGE ---");

        if (empty($_POST['id'])) {
            sendError(400, 'id (group_id) is required');
        }

        if (empty($_POST['text']) && empty($_POST['media'])) {
            sendError(400, 'text or media is required');
        }

        $group_id = intval($_POST['id']);
        $text = isset($_POST['text']) ? $_POST['text'] : '';
        $media = isset($_POST['media']) ? $_POST['media'] : '';

        try {
            // Перевіряємо чи користувач є учасником
            $stmt = $pdoDb->prepare("
                SELECT * FROM Wo_GroupChatUsers
                WHERE group_id = ? AND user_id = ? AND active = '1'
            ");
            $stmt->execute([$group_id, $current_user_id]);
            if (!$stmt->fetch()) {
                sendError(403, 'You are not a member of this group');
            }

            // Створюємо повідомлення
            $time = time();

            // HYBRID: Визначаємо тип клієнта (WorldMates GCM або офіційний WoWonder ECB)
            $use_gcm = !empty($_POST['use_gcm']) && $_POST['use_gcm'] == 'true';

            $encrypted_text = $text;
            $iv = null;
            $tag = null;
            $cipher_version = 1; // За замовчуванням ECB

            if (!empty($text)) {
                if ($use_gcm) {
                    // WorldMates: AES-256-GCM
                    $encrypted_data = CryptoHelper::encryptGCM($text, $time);
                    if ($encrypted_data !== false) {
                        $encrypted_text = $encrypted_data['text'];
                        $iv = $encrypted_data['iv'];
                        $tag = $encrypted_data['tag'];
                        $cipher_version = $encrypted_data['cipher_version'];
                        logMessage("Message encrypted with GCM (WorldMates), IV: " . substr($iv, 0, 10) . "...");
                    } else {
                        logMessage("WARNING: GCM encryption failed");
                    }
                } else {
                    // Офіційний WoWonder: AES-128-ECB (старий метод)
                    $encrypted_text = openssl_encrypt($text, "AES-128-ECB", $time);
                    $cipher_version = 1;
                    logMessage("Message encrypted with ECB (WoWonder official)");
                }
            }

            $stmt = $pdoDb->prepare("
                INSERT INTO Wo_Messages (from_id, group_id, to_id, text, media, time, seen, iv, tag, cipher_version)
                VALUES (?, ?, 0, ?, ?, ?, 0, ?, ?, ?)
            ");
            $stmt->execute([$current_user_id, $group_id, $encrypted_text, $media, $time, $iv, $tag, $cipher_version]);
            $message_id = $pdoDb->lastInsertId();

            logMessage("Message sent: ID=$message_id");

            sendResponse(array(
                'api_status' => 200,
                'message' => 'Message sent successfully',
                'message_id' => $message_id
            ));

        } catch (PDOException $e) {
            logMessage("SEND_MESSAGE ERROR: " . $e->getMessage());
            sendError(500, 'Failed to send message');
        }
        break;

    // ==========================================
    // GET_MESSAGES - Отримати повідомлення
    // ==========================================
    case 'get_messages':
        logMessage("--- GET MESSAGES ---");

        if (empty($_POST['id'])) {
            sendError(400, 'id (group_id) is required');
        }

        $group_id = intval($_POST['id']);
        $limit = isset($_POST['limit']) ? intval($_POST['limit']) : 50;
        $before_message_id = isset($_POST['before_message_id']) ? intval($_POST['before_message_id']) : 0;

        try {
            // Перевіряємо чи користувач є учасником
            $stmt = $pdoDb->prepare("
                SELECT * FROM Wo_GroupChatUsers
                WHERE group_id = ? AND user_id = ? AND active = '1'
            ");
            $stmt->execute([$group_id, $current_user_id]);
            if (!$stmt->fetch()) {
                sendError(403, 'You are not a member of this group');
            }

            // Отримуємо повідомлення
            $sql = "
                SELECT m.*, u.username, u.avatar,
                       CONCAT(u.first_name, ' ', u.last_name) as user_name,
                       CASE
                           WHEN m.media != '' THEN 'media'
                           WHEN m.text LIKE 'http%' THEN 'media'
                           ELSE 'text'
                       END as type
                FROM Wo_Messages m
                LEFT JOIN Wo_Users u ON m.from_id = u.user_id
                WHERE m.group_id = ?
            ";

            $params = [$group_id];

            if ($before_message_id > 0) {
                $sql .= " AND m.id < ?";
                $params[] = $before_message_id;
            }

            $sql .= " ORDER BY m.id DESC LIMIT ?";
            $params[] = $limit;

            $stmt = $pdoDb->prepare($sql);
            $stmt->execute($params);
            $messages = $stmt->fetchAll();

            // Розвертаємо для хронологічного порядку
            $messages = array_reverse($messages);

            logMessage("Found " . count($messages) . " messages");

            sendResponse(array(
                'api_status' => 200,
                'messages' => $messages
            ));

        } catch (PDOException $e) {
            logMessage("GET_MESSAGES ERROR: " . $e->getMessage());
            sendError(500, 'Failed to get messages');
        }
        break;

    // ==========================================
    // ADD_MEMBER - Додати учасника
    // ==========================================
    case 'add_member':
    case 'add_user':  // Сумісність з Android
        logMessage("--- ADD MEMBER ---");

        if (empty($_POST['id'])) {
            sendError(400, 'id (group_id) is required');
        }

        // Підтримка як 'user_id', так і 'parts' для сумісності
        $user_to_add = null;
        if (!empty($_POST['user_id'])) {
            $user_to_add = $_POST['user_id'];
        } elseif (!empty($_POST['parts'])) {
            $user_to_add = $_POST['parts'];
        }

        if (empty($user_to_add)) {
            sendError(400, 'user_id or parts is required');
        }

        $group_id = intval($_POST['id']);

        // Обробка одного або кількох користувачів
        $user_ids = array_filter(array_map('trim', explode(',', $user_to_add)));
        if (empty($user_ids)) {
            sendError(400, 'Invalid user_id or parts');
        }

        $new_user_id = intval($user_ids[0]); // Для поточної логіки беремо першого

        try {
            // Перевіряємо чи користувач вже є учасником
            $stmt = $pdoDb->prepare("
                SELECT * FROM Wo_GroupChatUsers
                WHERE group_id = ? AND user_id = ?
            ");
            $stmt->execute([$group_id, $new_user_id]);

            if ($stmt->fetch()) {
                // Оновлюємо active якщо був деактивований
                $stmt = $pdoDb->prepare("
                    UPDATE Wo_GroupChatUsers
                    SET active = '1'
                    WHERE group_id = ? AND user_id = ?
                ");
                $stmt->execute([$group_id, $new_user_id]);
            } else {
                // Додаємо нового учасника
                $stmt = $pdoDb->prepare("
                    INSERT INTO Wo_GroupChatUsers (user_id, group_id, active, time)
                    VALUES (?, ?, '1', ?)
                ");
                $stmt->execute([$new_user_id, $group_id, time()]);
            }

            logMessage("Member added: user_id=$new_user_id to group_id=$group_id");

            sendResponse(array(
                'api_status' => 200,
                'message' => 'Member added successfully'
            ));

        } catch (PDOException $e) {
            logMessage("ADD_MEMBER ERROR: " . $e->getMessage());
            sendError(500, 'Failed to add member');
        }
        break;

    // ==========================================
    // REMOVE_MEMBER - Видалити учасника
    // ==========================================
    case 'remove_member':
    case 'remove_user':  // Сумісність з Android
        logMessage("--- REMOVE MEMBER ---");

        if (empty($_POST['id'])) {
            sendError(400, 'id (group_id) is required');
        }

        // Підтримка як 'user_id', так і 'parts' для сумісності
        $user_to_remove = null;
        if (!empty($_POST['user_id'])) {
            $user_to_remove = $_POST['user_id'];
        } elseif (!empty($_POST['parts'])) {
            $user_to_remove = $_POST['parts'];
        }

        if (empty($user_to_remove)) {
            sendError(400, 'user_id or parts is required');
        }

        $group_id = intval($_POST['id']);

        // Обробка одного або кількох користувачів
        $user_ids = array_filter(array_map('trim', explode(',', $user_to_remove)));
        if (empty($user_ids)) {
            sendError(400, 'Invalid user_id or parts');
        }

        $remove_user_id = intval($user_ids[0]); // Для поточної логіки беремо першого

        try {
            // Перевіряємо чи поточний користувач є створювачем групи
            $stmt = $pdoDb->prepare("SELECT user_id FROM Wo_GroupChat WHERE group_id = ?");
            $stmt->execute([$group_id]);
            $group = $stmt->fetch();

            if ($group['user_id'] != $current_user_id) {
                sendError(403, 'Only group creator can remove members');
            }

            // Видаляємо учасника
            $stmt = $pdoDb->prepare("
                UPDATE Wo_GroupChatUsers
                SET active = '0'
                WHERE group_id = ? AND user_id = ?
            ");
            $stmt->execute([$group_id, $remove_user_id]);

            logMessage("Member removed: user_id=$remove_user_id from group_id=$group_id");

            sendResponse(array(
                'api_status' => 200,
                'message' => 'Member removed successfully'
            ));

        } catch (PDOException $e) {
            logMessage("REMOVE_MEMBER ERROR: " . $e->getMessage());
            sendError(500, 'Failed to remove member');
        }
        break;

    // ==========================================
    // LEAVE - Вийти з групи
    // ==========================================
    case 'leave':
        logMessage("--- LEAVE GROUP ---");

        if (empty($_POST['id'])) {
            sendError(400, 'id (group_id) is required');
        }

        $group_id = intval($_POST['id']);

        try {
            $stmt = $pdoDb->prepare("
                UPDATE Wo_GroupChatUsers
                SET active = '0'
                WHERE group_id = ? AND user_id = ?
            ");
            $stmt->execute([$group_id, $current_user_id]);

            logMessage("User left group: user_id=$current_user_id from group_id=$group_id");

            sendResponse(array(
                'api_status' => 200,
                'message' => 'Left group successfully'
            ));

        } catch (PDOException $e) {
            logMessage("LEAVE ERROR: " . $e->getMessage());
            sendError(500, 'Failed to leave group');
        }
        break;

    // ==========================================
    // DELETE - Видалити групу
    // ==========================================
    case 'delete':
        logMessage("--- DELETE GROUP ---");

        if (empty($_POST['id'])) {
            sendError(400, 'id (group_id) is required');
        }

        $group_id = intval($_POST['id']);

        try {
            // Перевіряємо чи користувач є створювачем
            $stmt = $pdoDb->prepare("SELECT user_id FROM Wo_GroupChat WHERE group_id = ?");
            $stmt->execute([$group_id]);
            $group = $stmt->fetch();

            if (!$group) {
                sendError(404, 'Group not found');
            }

            if ($group['user_id'] != $current_user_id) {
                sendError(403, 'Only group creator can delete the group');
            }

            // Видаляємо групу
            $stmt = $pdoDb->prepare("DELETE FROM Wo_GroupChat WHERE group_id = ?");
            $stmt->execute([$group_id]);

            // Видаляємо всіх учасників
            $stmt = $pdoDb->prepare("DELETE FROM Wo_GroupChatUsers WHERE group_id = ?");
            $stmt->execute([$group_id]);

            // Видаляємо повідомлення
            $stmt = $pdoDb->prepare("DELETE FROM Wo_Messages WHERE group_id = ?");
            $stmt->execute([$group_id]);

            logMessage("Group deleted: group_id=$group_id");

            sendResponse(array(
                'api_status' => 200,
                'message' => 'Group deleted successfully'
            ));

        } catch (PDOException $e) {
            logMessage("DELETE ERROR: " . $e->getMessage());
            sendError(500, 'Failed to delete group');
        }
        break;

    // ==========================================
    // UPLOAD_AVATAR - Завантажити аватарку групи
    // ==========================================
    case 'upload_avatar':
        logMessage("--- UPLOAD AVATAR ---");

        if (empty($_POST['id'])) {
            sendError(400, 'id (group_id) is required');
        }

        $group_id = intval($_POST['id']);

        try {
            // Перевіряємо чи користувач є адміном
            $stmt = $pdoDb->prepare("SELECT user_id FROM Wo_GroupChat WHERE group_id = ?");
            $stmt->execute([$group_id]);
            $group = $stmt->fetch();

            if (!$group) {
                sendError(404, 'Group not found');
            }

            if ($group['user_id'] != $current_user_id) {
                sendError(403, 'Only group admin can change avatar');
            }

            // Перевіряємо чи файл завантажено
            if (!isset($_FILES['avatar']) || $_FILES['avatar']['error'] != UPLOAD_ERR_OK) {
                sendError(400, 'Avatar file is required');
            }

            $file = $_FILES['avatar'];

            // Перевіряємо тип файлу
            $allowed_types = array('image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp');
            $file_type = mime_content_type($file['tmp_name']);

            if (!in_array($file_type, $allowed_types)) {
                sendError(400, 'Invalid file type. Only images allowed');
            }

            // Перевіряємо розмір (макс 5MB)
            if ($file['size'] > 5 * 1024 * 1024) {
                sendError(400, 'File too large. Maximum 5MB');
            }

            // Створюємо директорію якщо не існує
            $upload_dir = '../upload/photos/' . date('Y/m') . '/';
            if (!file_exists($upload_dir)) {
                mkdir($upload_dir, 0777, true);
            }

            // Генеруємо унікальне ім'я файлу
            $file_extension = pathinfo($file['name'], PATHINFO_EXTENSION);
            $new_filename = 'group_' . $group_id . '_' . time() . '.' . $file_extension;
            $relative_path = 'upload/photos/' . date('Y/m') . '/' . $new_filename;
            $absolute_path = $upload_dir . $new_filename;

            // Переміщуємо файл
            if (!move_uploaded_file($file['tmp_name'], $absolute_path)) {
                sendError(500, 'Failed to upload file');
            }

            // Видаляємо стару аватарку якщо існує
            if (!empty($group['avatar']) && file_exists('../' . $group['avatar'])) {
                @unlink('../' . $group['avatar']);
            }

            // Оновлюємо avatar в БД
            $stmt = $pdoDb->prepare("UPDATE Wo_GroupChat SET avatar = ? WHERE group_id = ?");
            $stmt->execute([$relative_path, $group_id]);

            logMessage("Avatar uploaded: $relative_path");

            sendResponse(array(
                'api_status' => 200,
                'message' => 'Avatar uploaded successfully',
                'avatar' => $relative_path
            ));

        } catch (PDOException $e) {
            logMessage("UPLOAD_AVATAR ERROR: " . $e->getMessage());
            sendError(500, 'Failed to upload avatar');
        }
        break;

    // ==========================================
    // UNKNOWN TYPE
    // ==========================================
    default:
        logMessage("ERROR: Unknown type: $type");
        sendError(400, "Unknown action type: $type");
        break;
}
