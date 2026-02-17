<?php
/**
 * Call History API v2
 * Ендпоінт для отримання історії дзвінків
 *
 * Типи запитів:
 * - get_history: Отримати історію дзвінків (1-on-1 + групові)
 * - delete_call: Видалити запис дзвінка з історії
 * - clear_history: Очистити всю історію дзвінків
 *
 * Таблиці: wo_calls, wo_group_calls, wo_group_call_participants
 */

require_once __DIR__ . '/config.php';

header('Content-Type: application/json; charset=utf-8');

// ============================================
// AUTO-MIGRATION: ensure wo_calls and related tables exist
// ============================================
$migration_flag = '/tmp/wm_call_history_migration_v1_done';
if (!file_exists($migration_flag)) {
    try {
        // wo_calls table (1-on-1 calls)
        $db->exec("
            CREATE TABLE IF NOT EXISTS wo_calls (
                id INT(11) NOT NULL AUTO_INCREMENT,
                from_id INT(11) NOT NULL COMMENT 'Caller user ID',
                to_id INT(11) NOT NULL COMMENT 'Recipient user ID',
                call_type ENUM('audio','video') NOT NULL DEFAULT 'audio',
                status ENUM('ringing','connected','ended','missed','rejected','failed') NOT NULL DEFAULT 'ringing',
                room_name VARCHAR(100) NOT NULL,
                sdp_offer LONGTEXT DEFAULT NULL,
                sdp_answer LONGTEXT DEFAULT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                accepted_at DATETIME DEFAULT NULL,
                ended_at DATETIME DEFAULT NULL,
                duration INT(11) DEFAULT NULL COMMENT 'Duration in seconds',
                PRIMARY KEY (id),
                UNIQUE KEY idx_room_name (room_name),
                KEY idx_from_id (from_id),
                KEY idx_to_id (to_id),
                KEY idx_status (status),
                KEY idx_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        ");

        // wo_group_calls table
        $db->exec("
            CREATE TABLE IF NOT EXISTS wo_group_calls (
                id INT(11) NOT NULL AUTO_INCREMENT,
                group_id INT(11) NOT NULL,
                initiated_by INT(11) NOT NULL,
                call_type ENUM('audio','video') NOT NULL DEFAULT 'audio',
                status ENUM('ringing','active','ended') NOT NULL DEFAULT 'ringing',
                room_name VARCHAR(100) NOT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                started_at DATETIME DEFAULT NULL,
                ended_at DATETIME DEFAULT NULL,
                max_participants INT(11) DEFAULT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY idx_room_name (room_name),
                KEY idx_group_id (group_id),
                KEY idx_initiated_by (initiated_by),
                KEY idx_status (status),
                KEY idx_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        ");

        // wo_group_call_participants table
        $db->exec("
            CREATE TABLE IF NOT EXISTS wo_group_call_participants (
                id INT(11) NOT NULL AUTO_INCREMENT,
                call_id INT(11) NOT NULL,
                user_id INT(11) NOT NULL,
                joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                left_at DATETIME DEFAULT NULL,
                duration INT(11) DEFAULT NULL,
                audio_enabled TINYINT(1) DEFAULT 1,
                video_enabled TINYINT(1) DEFAULT 0,
                PRIMARY KEY (id),
                KEY idx_call_id (call_id),
                KEY idx_user_id (user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        ");

        // Add deleted_by_from and deleted_by_to columns for soft delete
        try {
            $db->exec("ALTER TABLE wo_calls ADD COLUMN deleted_by_from TINYINT(1) DEFAULT 0 AFTER duration");
            $db->exec("ALTER TABLE wo_calls ADD COLUMN deleted_by_to TINYINT(1) DEFAULT 0 AFTER deleted_by_from");
        } catch (Exception $e) {
            // columns may already exist
        }

        file_put_contents($migration_flag, date('Y-m-d H:i:s'));
        logMessage("Call history migration v1 completed", "INFO");
    } catch (Exception $e) {
        logMessage("Call history migration error: " . $e->getMessage(), "ERROR");
    }
}

// ============================================
// AUTHENTICATION
// ============================================
$access_token = $_POST['access_token'] ?? $_GET['access_token'] ?? '';
$user_id = validateAccessToken($db, $access_token);

if (!$user_id) {
    sendError('Invalid access token', 401);
}

// ============================================
// REQUEST ROUTING
// ============================================
$type = $_POST['type'] ?? $_GET['type'] ?? '';

switch ($type) {
    case 'get_history':
        getCallHistory($db, $user_id);
        break;
    case 'delete_call':
        deleteCallFromHistory($db, $user_id);
        break;
    case 'clear_history':
        clearCallHistory($db, $user_id);
        break;
    default:
        sendError('Invalid type. Use: get_history, delete_call, clear_history');
}

// ============================================
// FUNCTIONS
// ============================================

/**
 * Get call history for user (both 1-on-1 and group calls)
 */
function getCallHistory($db, $user_id) {
    $limit = min((int)($_POST['limit'] ?? 50), 100);
    $offset = max((int)($_POST['offset'] ?? 0), 0);
    $filter = $_POST['filter'] ?? 'all'; // all, missed, incoming, outgoing

    try {
        // Build WHERE clause based on filter
        $where_conditions = [];
        $params = [
            'user_id_1' => $user_id,
            'user_id_2' => $user_id
        ];

        switch ($filter) {
            case 'missed':
                $where_conditions[] = "(c.to_id = :user_id_2 AND c.status IN ('missed', 'rejected'))";
                break;
            case 'incoming':
                $where_conditions[] = "c.to_id = :user_id_2";
                break;
            case 'outgoing':
                $where_conditions[] = "c.from_id = :user_id_1";
                break;
            default: // 'all'
                $where_conditions[] = "(c.from_id = :user_id_1 OR c.to_id = :user_id_2)";
                break;
        }

        // Soft delete filter
        $where_conditions[] = "NOT (c.from_id = :user_id_1 AND c.deleted_by_from = 1)";
        $where_conditions[] = "NOT (c.to_id = :user_id_2 AND c.deleted_by_to = 1)";

        // Since we reference user_id_1 and user_id_2 multiple times, use separate params
        $params = [
            'user_id_1' => $user_id,
            'user_id_2' => $user_id,
            'user_id_3' => $user_id,
            'user_id_4' => $user_id
        ];

        // Rebuild with unique param names
        $where_sql = "";
        switch ($filter) {
            case 'missed':
                $where_sql = "c.to_id = :user_id_1 AND c.status IN ('missed', 'rejected')";
                break;
            case 'incoming':
                $where_sql = "c.to_id = :user_id_1";
                break;
            case 'outgoing':
                $where_sql = "c.from_id = :user_id_1";
                break;
            default:
                $where_sql = "(c.from_id = :user_id_1 OR c.to_id = :user_id_2)";
                break;
        }

        // Add soft delete filter
        $delete_filter = "AND NOT (c.from_id = :user_id_3 AND c.deleted_by_from = 1)
                          AND NOT (c.to_id = :user_id_4 AND c.deleted_by_to = 1)";

        // 1-on-1 calls with user data
        $sql = "
            SELECT
                c.id,
                'personal' AS call_category,
                c.from_id,
                c.to_id,
                c.call_type,
                c.status,
                c.created_at,
                c.accepted_at,
                c.ended_at,
                c.duration,
                CASE WHEN c.from_id = :user_id_5 THEN 'outgoing' ELSE 'incoming' END AS direction,
                CASE WHEN c.from_id = :user_id_6 THEN c.to_id ELSE c.from_id END AS other_user_id,
                u.username AS other_username,
                u.name AS other_name,
                u.avatar AS other_avatar,
                u.verified AS other_verified
            FROM wo_calls c
            LEFT JOIN Wo_Users u ON u.user_id = CASE WHEN c.from_id = :user_id_7 THEN c.to_id ELSE c.from_id END
            WHERE $where_sql
            $delete_filter
            ORDER BY c.created_at DESC
            LIMIT :limit OFFSET :offset
        ";

        $params = [
            'user_id_1' => $user_id,
            'user_id_2' => $user_id,
            'user_id_3' => $user_id,
            'user_id_4' => $user_id,
            'user_id_5' => $user_id,
            'user_id_6' => $user_id,
            'user_id_7' => $user_id,
        ];

        $stmt = $db->prepare($sql);
        foreach ($params as $key => $val) {
            $stmt->bindValue($key, $val, PDO::PARAM_INT);
        }
        $stmt->bindValue('limit', $limit, PDO::PARAM_INT);
        $stmt->bindValue('offset', $offset, PDO::PARAM_INT);
        $stmt->execute();

        $calls = $stmt->fetchAll();

        // Also fetch group calls
        $group_sql = "
            SELECT
                gc.id,
                'group' AS call_category,
                gc.initiated_by AS from_id,
                0 AS to_id,
                gc.call_type,
                gc.status,
                gc.created_at,
                gc.started_at AS accepted_at,
                gc.ended_at,
                0 AS duration,
                CASE WHEN gc.initiated_by = :user_id_g1 THEN 'outgoing' ELSE 'incoming' END AS direction,
                gc.group_id,
                g.group_name,
                g.avatar AS group_avatar,
                gc.max_participants
            FROM wo_group_calls gc
            INNER JOIN wo_group_call_participants gcp ON gcp.call_id = gc.id AND gcp.user_id = :user_id_g2
            LEFT JOIN Wo_GroupChat g ON g.group_id = gc.group_id
            ORDER BY gc.created_at DESC
            LIMIT :g_limit OFFSET :g_offset
        ";

        $stmt2 = $db->prepare($group_sql);
        $stmt2->bindValue('user_id_g1', $user_id, PDO::PARAM_INT);
        $stmt2->bindValue('user_id_g2', $user_id, PDO::PARAM_INT);
        $stmt2->bindValue('g_limit', $limit, PDO::PARAM_INT);
        $stmt2->bindValue('g_offset', $offset, PDO::PARAM_INT);
        $stmt2->execute();
        $group_calls = $stmt2->fetchAll();

        // Merge and sort by created_at desc
        $all_calls = array_merge($calls, $group_calls);
        usort($all_calls, function($a, $b) {
            return strtotime($b['created_at']) - strtotime($a['created_at']);
        });

        // Limit combined result
        $all_calls = array_slice($all_calls, 0, $limit);

        // Format response
        $formatted = [];
        foreach ($all_calls as $call) {
            $item = [
                'id' => (int)$call['id'],
                'call_category' => $call['call_category'],
                'call_type' => $call['call_type'],
                'status' => $call['status'],
                'direction' => $call['direction'],
                'created_at' => $call['created_at'],
                'accepted_at' => $call['accepted_at'] ?? null,
                'ended_at' => $call['ended_at'] ?? null,
                'duration' => (int)($call['duration'] ?? 0),
                'timestamp' => strtotime($call['created_at'])
            ];

            if ($call['call_category'] === 'personal') {
                $item['other_user'] = [
                    'user_id' => (int)$call['other_user_id'],
                    'username' => $call['other_username'] ?? '',
                    'name' => $call['other_name'] ?? '',
                    'avatar' => $call['other_avatar'] ?? '',
                    'verified' => (int)($call['other_verified'] ?? 0)
                ];
            } else {
                $item['group_data'] = [
                    'group_id' => (int)$call['group_id'],
                    'group_name' => $call['group_name'] ?? '',
                    'avatar' => $call['group_avatar'] ?? '',
                    'max_participants' => (int)($call['max_participants'] ?? 0)
                ];
            }

            $formatted[] = $item;
        }

        // Get total count
        $count_sql = "
            SELECT COUNT(*) as total FROM wo_calls c
            WHERE (c.from_id = :uid1 OR c.to_id = :uid2)
            AND NOT (c.from_id = :uid3 AND c.deleted_by_from = 1)
            AND NOT (c.to_id = :uid4 AND c.deleted_by_to = 1)
        ";
        $stmt3 = $db->prepare($count_sql);
        $stmt3->execute(['uid1' => $user_id, 'uid2' => $user_id, 'uid3' => $user_id, 'uid4' => $user_id]);
        $total = (int)$stmt3->fetch()['total'];

        sendSuccess([
            'calls' => $formatted,
            'total' => $total,
            'offset' => $offset,
            'limit' => $limit
        ]);

    } catch (PDOException $e) {
        logMessage("getCallHistory error: " . $e->getMessage(), "ERROR");
        sendError('Failed to fetch call history', 500);
    }
}

/**
 * Delete single call from history (soft delete)
 */
function deleteCallFromHistory($db, $user_id) {
    $call_id = (int)($_POST['call_id'] ?? 0);
    if ($call_id <= 0) {
        sendError('call_id is required');
    }

    try {
        // Check if user is participant
        $stmt = $db->prepare("SELECT from_id, to_id FROM wo_calls WHERE id = :call_id LIMIT 1");
        $stmt->execute(['call_id' => $call_id]);
        $call = $stmt->fetch();

        if (!$call) {
            sendError('Call not found', 404);
        }

        if ((int)$call['from_id'] === $user_id) {
            $db->prepare("UPDATE wo_calls SET deleted_by_from = 1 WHERE id = :call_id")
               ->execute(['call_id' => $call_id]);
        } elseif ((int)$call['to_id'] === $user_id) {
            $db->prepare("UPDATE wo_calls SET deleted_by_to = 1 WHERE id = :call_id")
               ->execute(['call_id' => $call_id]);
        } else {
            sendError('Not authorized', 403);
        }

        sendSuccess(['message' => 'Call deleted from history']);

    } catch (PDOException $e) {
        logMessage("deleteCallFromHistory error: " . $e->getMessage(), "ERROR");
        sendError('Failed to delete call', 500);
    }
}

/**
 * Clear entire call history for user (soft delete all)
 */
function clearCallHistory($db, $user_id) {
    try {
        $db->prepare("UPDATE wo_calls SET deleted_by_from = 1 WHERE from_id = :uid")
           ->execute(['uid' => $user_id]);
        $db->prepare("UPDATE wo_calls SET deleted_by_to = 1 WHERE to_id = :uid")
           ->execute(['uid' => $user_id]);

        sendSuccess(['message' => 'Call history cleared']);

    } catch (PDOException $e) {
        logMessage("clearCallHistory error: " . $e->getMessage(), "ERROR");
        sendError('Failed to clear history', 500);
    }
}
