<?php
// +------------------------------------------------------------------------+
// | 🎨 GROUP CUSTOMIZATION: Кастомні теми для групових чатів
// | Endpoint: /api/v2/endpoints/group_customization.php
// | Операції: get_customization, update_customization, reset_customization
// +------------------------------------------------------------------------+

require_once(__DIR__ . '/../config.php');

header('Content-Type: application/json');

$response_data = array('api_status' => 400);
$error_code = 0;
$error_message = '';

if (empty($_POST['access_token'])) {
    $error_code = 3;
    $error_message = 'access_token is missing';
}

if ($error_code == 0) {
    $user_id = Wo_UserIdFromAccessToken($_POST['access_token']);
    if (empty($user_id) || !is_numeric($user_id) || $user_id < 1) {
        $error_code = 4;
        $error_message = 'Invalid access_token';
    }
}

if ($error_code == 0) {
    // Переконуємося що колонки кастомізації існують
    ensureGroupCustomizationColumns($sqlConnect);

    $type = !empty($_POST['type']) ? $_POST['type'] : '';

    switch ($type) {
        // ==================== GET CUSTOMIZATION ====================
        case 'get_customization':
            $group_id = (int)($_POST['group_id'] ?? 0);
            if ($group_id < 1) {
                $response_data = array('api_status' => 400, 'error_message' => 'group_id is required');
                break;
            }

            // Перевіряємо чи група існує
            $group_query = mysqli_query($sqlConnect, "
                SELECT group_id FROM Wo_GroupChat WHERE group_id = {$group_id}
            ");

            if (mysqli_num_rows($group_query) == 0) {
                $response_data = array('api_status' => 404, 'error_message' => 'Group not found');
                break;
            }

            // Отримуємо налаштування кастомізації
            $query = mysqli_query($sqlConnect, "
                SELECT
                    group_id,
                    theme_bubble_style,
                    theme_preset_background,
                    theme_accent_color,
                    theme_enabled_by_admin,
                    theme_updated_at,
                    theme_updated_by
                FROM Wo_GroupChat
                WHERE group_id = {$group_id}
            ");

            $customization = mysqli_fetch_assoc($query);

            // Формуємо відповідь
            $response_data = array(
                'api_status' => 200,
                'customization' => array(
                    'group_id' => (int)$customization['group_id'],
                    'bubble_style' => $customization['theme_bubble_style'] ?: 'STANDARD',
                    'preset_background' => $customization['theme_preset_background'] ?: 'ocean',
                    'accent_color' => $customization['theme_accent_color'] ?: '#2196F3',
                    'enabled_by_admin' => (bool)$customization['theme_enabled_by_admin'],
                    'updated_at' => (int)($customization['theme_updated_at'] ?: 0),
                    'updated_by' => (int)($customization['theme_updated_by'] ?: 0)
                )
            );
            break;

        // ==================== UPDATE CUSTOMIZATION ====================
        case 'update_customization':
            $group_id = (int)($_POST['group_id'] ?? 0);
            if ($group_id < 1) {
                $response_data = array('api_status' => 400, 'error_message' => 'group_id is required');
                break;
            }

            // Тільки адмін може змінювати тему
            if (!isGroupCustomizationAdmin($sqlConnect, $group_id, $user_id)) {
                $response_data = array('api_status' => 403, 'error_message' => 'Only admins can update group theme');
                break;
            }

            $updates = array();
            $time = time();

            // Стиль бульбашок
            if (isset($_POST['bubble_style'])) {
                $bubble_style = mysqli_real_escape_string($sqlConnect, $_POST['bubble_style']);
                $updates[] = "theme_bubble_style = '{$bubble_style}'";
            }

            // Фон-пресет
            if (isset($_POST['preset_background'])) {
                $preset_bg = mysqli_real_escape_string($sqlConnect, $_POST['preset_background']);
                $updates[] = "theme_preset_background = '{$preset_bg}'";
            }

            // Акцентний колір
            if (isset($_POST['accent_color'])) {
                $accent = mysqli_real_escape_string($sqlConnect, $_POST['accent_color']);
                $updates[] = "theme_accent_color = '{$accent}'";
            }

            // Ввімкнення/вимкнення теми адміном
            if (isset($_POST['enabled_by_admin'])) {
                $enabled = $_POST['enabled_by_admin'] == '1' ? 1 : 0;
                $updates[] = "theme_enabled_by_admin = {$enabled}";
            }

            if (empty($updates)) {
                $response_data = array('api_status' => 400, 'error_message' => 'No customization fields to update');
                break;
            }

            // Додаємо мета-інформацію
            $updates[] = "theme_updated_at = {$time}";
            $updates[] = "theme_updated_by = {$user_id}";

            $update_sql = "UPDATE Wo_GroupChat SET " . implode(', ', $updates) . " WHERE group_id = {$group_id}";

            if (mysqli_query($sqlConnect, $update_sql)) {
                // Повертаємо оновлені дані
                $query = mysqli_query($sqlConnect, "
                    SELECT
                        group_id,
                        theme_bubble_style,
                        theme_preset_background,
                        theme_accent_color,
                        theme_enabled_by_admin,
                        theme_updated_at,
                        theme_updated_by
                    FROM Wo_GroupChat
                    WHERE group_id = {$group_id}
                ");

                $updated = mysqli_fetch_assoc($query);

                $response_data = array(
                    'api_status' => 200,
                    'message' => 'Group theme updated successfully',
                    'customization' => array(
                        'group_id' => (int)$updated['group_id'],
                        'bubble_style' => $updated['theme_bubble_style'] ?: 'STANDARD',
                        'preset_background' => $updated['theme_preset_background'] ?: 'ocean',
                        'accent_color' => $updated['theme_accent_color'] ?: '#2196F3',
                        'enabled_by_admin' => (bool)$updated['theme_enabled_by_admin'],
                        'updated_at' => (int)$updated['theme_updated_at'],
                        'updated_by' => (int)$updated['theme_updated_by']
                    )
                );
            } else {
                $response_data = array(
                    'api_status' => 500,
                    'error_message' => 'Failed to update group theme: ' . mysqli_error($sqlConnect)
                );
            }
            break;

        // ==================== RESET CUSTOMIZATION ====================
        case 'reset_customization':
            $group_id = (int)($_POST['group_id'] ?? 0);
            if ($group_id < 1) {
                $response_data = array('api_status' => 400, 'error_message' => 'group_id is required');
                break;
            }

            // Тільки адмін може скинути тему
            if (!isGroupCustomizationAdmin($sqlConnect, $group_id, $user_id)) {
                $response_data = array('api_status' => 403, 'error_message' => 'Only admins can reset group theme');
                break;
            }

            $time = time();
            $reset_sql = "UPDATE Wo_GroupChat SET
                theme_bubble_style = 'STANDARD',
                theme_preset_background = 'ocean',
                theme_accent_color = '#2196F3',
                theme_enabled_by_admin = 1,
                theme_updated_at = {$time},
                theme_updated_by = {$user_id}
                WHERE group_id = {$group_id}";

            if (mysqli_query($sqlConnect, $reset_sql)) {
                $response_data = array(
                    'api_status' => 200,
                    'message' => 'Group theme reset to defaults'
                );
            } else {
                $response_data = array(
                    'api_status' => 500,
                    'error_message' => 'Failed to reset group theme'
                );
            }
            break;

        default:
            $response_data = array(
                'api_status' => 400,
                'error_message' => 'Invalid type: ' . $type . '. Supported: get_customization, update_customization, reset_customization'
            );
    }
}

if ($error_code > 0) {
    $response_data = array(
        'api_status' => 400,
        'error_code' => $error_code,
        'error_message' => $error_message
    );
}

echo json_encode($response_data);

// ==================== HELPER FUNCTIONS ====================

/**
 * Перевіряє чи користувач є адміном або власником групи
 */
function isGroupCustomizationAdmin($sqlConnect, $group_id, $user_id) {
    // Перевіряємо чи користувач - власник групи
    $owner_query = mysqli_query($sqlConnect, "
        SELECT user_id FROM Wo_GroupChat WHERE group_id = {$group_id} AND user_id = {$user_id}
    ");
    if (mysqli_num_rows($owner_query) > 0) return true;

    // Перевіряємо чи користувач має роль адміна
    $admin_query = mysqli_query($sqlConnect, "
        SELECT role FROM Wo_GroupChatUsers
        WHERE group_id = {$group_id} AND user_id = {$user_id} AND role IN ('owner', 'admin')
    ");
    return mysqli_num_rows($admin_query) > 0;
}

/**
 * Додає колонки кастомізації до таблиці Wo_GroupChat, якщо їх немає
 */
function ensureGroupCustomizationColumns($sqlConnect) {
    $columns = array(
        'theme_bubble_style' => "VARCHAR(50) DEFAULT 'STANDARD'",
        'theme_preset_background' => "VARCHAR(50) DEFAULT 'ocean'",
        'theme_accent_color' => "VARCHAR(10) DEFAULT '#2196F3'",
        'theme_enabled_by_admin' => "TINYINT(1) DEFAULT 1",
        'theme_updated_at' => "INT DEFAULT 0",
        'theme_updated_by' => "INT DEFAULT 0"
    );

    foreach ($columns as $column => $definition) {
        $check = mysqli_query($sqlConnect, "SHOW COLUMNS FROM Wo_GroupChat LIKE '{$column}'");
        if (mysqli_num_rows($check) == 0) {
            mysqli_query($sqlConnect, "ALTER TABLE Wo_GroupChat ADD COLUMN {$column} {$definition}");
        }
    }
}
?>
