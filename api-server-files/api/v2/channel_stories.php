<?php
/**
 * WorldMates Messenger - Channel Stories API
 *
 * Эндпоинт для stories каналов (отдельно от личных stories).
 * Использует ту же таблицу Wo_UserStory, но с колонкой page_id для привязки к каналу.
 *
 * Поддерживаемые типы:
 *   - create            — создать story от имени канала (только админ)
 *   - get_channel       — получить stories конкретного канала
 *   - get_subscribed    — получить stories всех подписанных каналов
 *   - delete            — удалить story канала (только админ)
 */

require_once(__DIR__ . '/config.php');

header('Content-Type: application/json');

// ============================================
// АВТОМИГРАЦИЯ: добавляем page_id в Wo_UserStory
// ============================================
$cs_migration_flag = sys_get_temp_dir() . '/wm_channel_stories_migration_v1_done';
if (!file_exists($cs_migration_flag)) {
    try {
        $cols = [];
        $result = $db->query("SHOW COLUMNS FROM Wo_UserStory");
        while ($row = $result->fetch(PDO::FETCH_ASSOC)) {
            $cols[] = $row['Field'];
        }
        if (!in_array('page_id', $cols)) {
            $db->exec("ALTER TABLE Wo_UserStory ADD COLUMN `page_id` INT(11) DEFAULT NULL AFTER `user_id`");
            $db->exec("ALTER TABLE Wo_UserStory ADD INDEX `idx_page_id` (`page_id`)");
            error_log("[channel_stories] Migration: added page_id to Wo_UserStory");
        }
        @file_put_contents($cs_migration_flag, date('Y-m-d H:i:s'));
    } catch (PDOException $e) {
        error_log("[channel_stories] Migration error: " . $e->getMessage());
    }
}

// ============================================
// АУТЕНТИФИКАЦИЯ
// ============================================
$access_token = $_GET['access_token'] ?? $_POST['access_token'] ?? null;
if (!$access_token) {
    echo json_encode(['api_status' => 400, 'error_message' => 'Access token is required']);
    exit;
}

$user_id = validateAccessToken($db, $access_token);
if (!$user_id) {
    echo json_encode(['api_status' => 401, 'error_message' => 'Invalid access token']);
    exit;
}

// Обновляем $wo['user'] для WoWonder-функций (Wo_ShareFile и т.д.)
$wo['user']['user_id'] = $user_id;
$wo['user']['active'] = '1';

// ============================================
// ТИП ЗАПРОСА
// ============================================
$type = $_GET['type'] ?? $_POST['type'] ?? null;
if (!$type) {
    echo json_encode(['api_status' => 400, 'error_message' => 'Type parameter is required']);
    exit;
}

// ============================================
// ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
// ============================================

/**
 * Проверить, является ли пользователь админом канала
 */
function isChannelAdmin($db, $channel_id, $user_id) {
    // Проверяем: owner канала (user_id в Wo_GroupChat) или role='admin' в Wo_GroupChatUsers
    $stmt = $db->prepare("
        SELECT 1 FROM Wo_GroupChat
        WHERE group_id = :channel_id AND user_id = :user_id AND type = 'channel'
        UNION
        SELECT 1 FROM Wo_GroupChatUsers
        WHERE group_id = :channel_id2 AND user_id = :user_id2 AND role = 'admin'
        LIMIT 1
    ");
    $stmt->execute([
        'channel_id' => $channel_id, 'user_id' => $user_id,
        'channel_id2' => $channel_id, 'user_id2' => $user_id
    ]);
    return $stmt->fetch() !== false;
}

/**
 * Получить данные канала (для ответа)
 */
function getChannelData($db, $channel_id) {
    $stmt = $db->prepare("
        SELECT group_id, group_name, avatar, user_id as owner_id
        FROM Wo_GroupChat
        WHERE group_id = :id AND type = 'channel'
        LIMIT 1
    ");
    $stmt->execute(['id' => $channel_id]);
    return $stmt->fetch();
}

/**
 * Форматировать story для ответа API
 */
function formatStory($db, $story, $site_url) {
    // Получаем медиа
    $stmt = $db->prepare("
        SELECT id, story_id, type, filename, expire
        FROM Wo_UserStoryMedia
        WHERE story_id = :story_id
    ");
    $stmt->execute(['story_id' => $story['id']]);
    $media_items = $stmt->fetchAll();

    // Форматируем URL-ы медиа
    foreach ($media_items as &$item) {
        if (!empty($item['filename']) && strpos($item['filename'], 'http') !== 0) {
            $item['filename'] = rtrim($site_url, '/') . '/' . ltrim($item['filename'], '/');
        }
    }

    // Получаем данные канала
    $channel_data = null;
    if (!empty($story['page_id'])) {
        $channel_data = getChannelData($db, $story['page_id']);
        if ($channel_data && !empty($channel_data['avatar']) && strpos($channel_data['avatar'], 'http') !== 0) {
            $channel_data['avatar'] = rtrim($site_url, '/') . '/' . ltrim($channel_data['avatar'], '/');
        }
    }

    // Получаем user_data автора
    $stmt2 = $db->prepare("
        SELECT user_id, username, first_name, last_name, avatar, avatar_org, is_pro, verified
        FROM Wo_Users WHERE user_id = :uid LIMIT 1
    ");
    $stmt2->execute(['uid' => $story['user_id']]);
    $user_data = $stmt2->fetch();
    if ($user_data && !empty($user_data['avatar']) && strpos($user_data['avatar'], 'http') !== 0) {
        $user_data['avatar'] = rtrim($site_url, '/') . '/' . ltrim($user_data['avatar'], '/');
    }

    // Количество просмотров
    $stmt3 = $db->prepare("SELECT COUNT(*) as cnt FROM Wo_Story_Seen WHERE story_id = :sid");
    $stmt3->execute(['sid' => $story['id']]);
    $view_count = (int)$stmt3->fetchColumn();

    // Thumbnail
    $thumbnail = $story['thumbnail'] ?? '';
    if (!empty($thumbnail) && strpos($thumbnail, 'http') !== 0) {
        $thumbnail = rtrim($site_url, '/') . '/' . ltrim($thumbnail, '/');
    }

    return [
        'id' => (int)$story['id'],
        'user_id' => (int)$story['user_id'],
        'page_id' => !empty($story['page_id']) ? (int)$story['page_id'] : null,
        'title' => $story['title'] ?? null,
        'description' => $story['description'] ?? null,
        'posted' => (int)$story['posted'],
        'expire' => (int)$story['expire'],
        'thumbnail' => $thumbnail,
        'user_data' => $user_data ?: null,
        'channel_data' => $channel_data ?: null,
        'mediaItems' => $media_items,
        'is_owner' => false,
        'is_viewed' => 0,
        'view_count' => $view_count,
        'comment_count' => 0
    ];
}

// ============================================
// ОБРАБОТКА ЗАПРОСОВ
// ============================================

switch ($type) {

    // ========== СОЗДАТЬ STORY КАНАЛА ==========
    case 'create':
        $channel_id = isset($_POST['channel_id']) ? (int)$_POST['channel_id'] : 0;
        if ($channel_id <= 0) {
            echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
            exit;
        }

        // Проверка прав
        if (!isChannelAdmin($db, $channel_id, $user_id)) {
            echo json_encode(['api_status' => 403, 'error_message' => 'Only channel admins can create stories']);
            exit;
        }

        // Проверка файла
        if (empty($_FILES['file']['tmp_name'])) {
            echo json_encode(['api_status' => 400, 'error_message' => 'File is required']);
            exit;
        }

        $file_type = $_POST['file_type'] ?? 'image';
        if (!in_array($file_type, ['image', 'video'])) {
            echo json_encode(['api_status' => 400, 'error_message' => 'file_type must be image or video']);
            exit;
        }

        $title = isset($_POST['story_title']) ? substr(trim($_POST['story_title']), 0, 100) : null;
        $description = isset($_POST['story_description']) ? substr(trim($_POST['story_description']), 0, 300) : null;

        try {
            $now = time();
            $expire = $now + (60 * 60 * 48); // 48 часов для каналов

            // Загружаем файл через WoWonder
            $fileInfo = [
                'file' => $_FILES['file']['tmp_name'],
                'name' => $_FILES['file']['name'],
                'size' => $_FILES['file']['size'],
                'type' => $_FILES['file']['type'],
                'types' => 'jpg,png,mp4,gif,jpeg,mov,webm'
            ];

            $filename = '';
            if (function_exists('Wo_ShareFile')) {
                $media = Wo_ShareFile($fileInfo);
                if (!empty($media)) {
                    $filename = $media['filename'];
                }
            } else {
                // Fallback: сохраняем файл вручную
                $upload_dir = $_SERVER['DOCUMENT_ROOT'] . '/upload/stories/';
                if (!is_dir($upload_dir)) {
                    @mkdir($upload_dir, 0755, true);
                }
                $ext = pathinfo($_FILES['file']['name'], PATHINFO_EXTENSION);
                $new_name = 'ch_' . $channel_id . '_' . $now . '_' . uniqid() . '.' . $ext;
                $dest = $upload_dir . $new_name;
                if (move_uploaded_file($_FILES['file']['tmp_name'], $dest)) {
                    $filename = 'upload/stories/' . $new_name;
                }
            }

            if (empty($filename)) {
                echo json_encode(['api_status' => 500, 'error_message' => 'Failed to upload file']);
                exit;
            }

            // Вставляем story
            $stmt = $db->prepare("
                INSERT INTO Wo_UserStory (user_id, page_id, title, description, posted, expire, thumbnail)
                VALUES (:user_id, :page_id, :title, :description, :posted, :expire, :thumbnail)
            ");
            $stmt->execute([
                'user_id' => $user_id,
                'page_id' => $channel_id,
                'title' => $title,
                'description' => $description,
                'posted' => $now,
                'expire' => $expire,
                'thumbnail' => $filename
            ]);
            $story_id = $db->lastInsertId();

            // Вставляем медиа
            $stmt2 = $db->prepare("
                INSERT INTO Wo_UserStoryMedia (story_id, type, filename, expire)
                VALUES (:story_id, :type, :filename, :expire)
            ");
            $stmt2->execute([
                'story_id' => $story_id,
                'type' => $file_type,
                'filename' => $filename,
                'expire' => $expire
            ]);

            echo json_encode([
                'api_status' => 200,
                'message' => 'Channel story created',
                'story_id' => (int)$story_id
            ]);
        } catch (PDOException $e) {
            error_log("[channel_stories] Create error: " . $e->getMessage());
            echo json_encode(['api_status' => 500, 'error_message' => 'Database error']);
        }
        break;

    // ========== ПОЛУЧИТЬ STORIES КАНАЛА ==========
    case 'get_channel':
        $channel_id = isset($_POST['channel_id']) ? (int)$_POST['channel_id'] :
                      (isset($_GET['channel_id']) ? (int)$_GET['channel_id'] : 0);
        $limit = min((int)($_POST['limit'] ?? $_GET['limit'] ?? 20), 50);

        if ($channel_id <= 0) {
            echo json_encode(['api_status' => 400, 'error_message' => 'channel_id is required']);
            exit;
        }

        try {
            $now = time();
            $stmt = $db->prepare("
                SELECT * FROM Wo_UserStory
                WHERE page_id = :page_id AND expire > :now
                ORDER BY posted DESC
                LIMIT :limit
            ");
            $stmt->bindValue('page_id', $channel_id, PDO::PARAM_INT);
            $stmt->bindValue('now', $now, PDO::PARAM_INT);
            $stmt->bindValue('limit', $limit, PDO::PARAM_INT);
            $stmt->execute();
            $stories = $stmt->fetchAll();

            $formatted = [];
            foreach ($stories as $story) {
                $formatted[] = formatStory($db, $story, $wo['site_url']);
            }

            echo json_encode([
                'api_status' => 200,
                'stories' => $formatted
            ]);
        } catch (PDOException $e) {
            error_log("[channel_stories] Get channel error: " . $e->getMessage());
            echo json_encode(['api_status' => 500, 'error_message' => 'Database error']);
        }
        break;

    // ========== ПОЛУЧИТЬ STORIES ПОДПИСАННЫХ КАНАЛОВ ==========
    case 'get_subscribed':
        $limit = min((int)($_POST['limit'] ?? $_GET['limit'] ?? 30), 50);

        try {
            $now = time();
            // Находим все каналы, на которые подписан пользователь
            $stmt = $db->prepare("
                SELECT s.* FROM Wo_UserStory s
                INNER JOIN Wo_GroupChatUsers gcu ON s.page_id = gcu.group_id
                INNER JOIN Wo_GroupChat gc ON gc.group_id = s.page_id AND gc.type = 'channel'
                WHERE gcu.user_id = :user_id
                  AND s.page_id IS NOT NULL
                  AND s.expire > :now
                ORDER BY s.posted DESC
                LIMIT :limit
            ");
            $stmt->bindValue('user_id', $user_id, PDO::PARAM_INT);
            $stmt->bindValue('now', $now, PDO::PARAM_INT);
            $stmt->bindValue('limit', $limit, PDO::PARAM_INT);
            $stmt->execute();
            $stories = $stmt->fetchAll();

            $formatted = [];
            foreach ($stories as $story) {
                $f = formatStory($db, $story, $wo['site_url']);
                // Помечаем как "свои" если пользователь — админ
                $f['is_owner'] = isChannelAdmin($db, $story['page_id'], $user_id);
                $formatted[] = $f;
            }

            echo json_encode([
                'api_status' => 200,
                'stories' => $formatted
            ]);
        } catch (PDOException $e) {
            error_log("[channel_stories] Get subscribed error: " . $e->getMessage());
            echo json_encode(['api_status' => 500, 'error_message' => 'Database error']);
        }
        break;

    // ========== УДАЛИТЬ STORY КАНАЛА ==========
    case 'delete':
        $story_id = isset($_POST['story_id']) ? (int)$_POST['story_id'] : 0;
        if ($story_id <= 0) {
            echo json_encode(['api_status' => 400, 'error_message' => 'story_id is required']);
            exit;
        }

        try {
            // Проверяем story и права
            $stmt = $db->prepare("SELECT * FROM Wo_UserStory WHERE id = :id LIMIT 1");
            $stmt->execute(['id' => $story_id]);
            $story = $stmt->fetch();

            if (!$story) {
                echo json_encode(['api_status' => 404, 'error_message' => 'Story not found']);
                exit;
            }

            if (empty($story['page_id'])) {
                echo json_encode(['api_status' => 400, 'error_message' => 'This is not a channel story']);
                exit;
            }

            if (!isChannelAdmin($db, $story['page_id'], $user_id)) {
                echo json_encode(['api_status' => 403, 'error_message' => 'Only channel admins can delete stories']);
                exit;
            }

            // Удаляем медиа и story
            $db->prepare("DELETE FROM Wo_UserStoryMedia WHERE story_id = :sid")->execute(['sid' => $story_id]);
            $db->prepare("DELETE FROM Wo_Story_Seen WHERE story_id = :sid")->execute(['sid' => $story_id]);
            $db->prepare("DELETE FROM Wo_UserStory WHERE id = :id")->execute(['id' => $story_id]);

            echo json_encode([
                'api_status' => 200,
                'message' => 'Channel story deleted'
            ]);
        } catch (PDOException $e) {
            error_log("[channel_stories] Delete error: " . $e->getMessage());
            echo json_encode(['api_status' => 500, 'error_message' => 'Database error']);
        }
        break;

    default:
        echo json_encode(['api_status' => 400, 'error_message' => "Unknown type: $type"]);
        break;
}
