<?php
// +------------------------------------------------------------------------+
// | WorldMates Bot API - Main Endpoint
// | Version: 1.0.0
// | All bot operations via POST ?type=<action>
// +------------------------------------------------------------------------+

// Self-bootstrap: load config if not already loaded (direct access without index.php router)
if (!isset($sqlConnect) || !$sqlConnect) {
    require_once(__DIR__ . '/../config.php');

    $request_type = $_POST['type'] ?? $_GET['type'] ?? '';
    $access_token = $_GET['access_token'] ?? $_POST['access_token'] ?? '';
    $has_bot_token = !empty($_POST['bot_token']);

    // Public discovery endpoints should not require user auth.
    $public_types = array('search_bots', 'get_bot_info');
    $requires_user_auth = !$has_bot_token && !in_array($request_type, $public_types, true);

    if ($requires_user_auth) {
        if (empty($access_token)) {
            header('Content-Type: application/json; charset=UTF-8');
            http_response_code(401);
            echo json_encode(array(
                'api_status' => 401,
                'error_message' => 'access_token is required'
            ));
            exit;
        }

        $user_id = validateAccessToken($pdo, $access_token);
        if (!$user_id) {
            header('Content-Type: application/json; charset=UTF-8');
            http_response_code(401);
            echo json_encode(array(
                'api_status' => 401,
                'error_message' => 'Invalid or missing access_token'
            ));
            exit;
        }

        // Minimal user context is enough for bot owner checks.
        $wo['user'] = array('user_id' => (int)$user_id, 'id' => (int)$user_id);
        $wo['loggedin'] = true;

        // Try to enrich user context, but never allow enrichment failures to crash endpoint.
        if (function_exists('Wo_UserData')) {
            try {
                $user_data = Wo_UserData($user_id);
                if (!empty($user_data)) {
                    $wo['user'] = $user_data;
                    $wo['loggedin'] = true;
                }
            } catch (Throwable $e) {
                error_log('bot_api.php Wo_UserData failed: ' . $e->getMessage());
            }
        }
    }
}

// Set Content-Type header (may already be set by index.php)
if (!headers_sent()) {
    header('Content-Type: application/json; charset=UTF-8');
}

$response_data = array('api_status' => 400);

// ==================== BOT TOKEN AUTHENTICATION ====================
// Bots authenticate via bot_token (not user access_token)
// Users managing bots authenticate via access_token

$bot_token = null;
$bot_data = null;
$is_bot_request = false;

if (!empty($_POST['bot_token'])) {
    $bot_token = Wo_Secure($_POST['bot_token']);
    $bot_data = getBotByToken($bot_token);
    if ($bot_data) {
        $is_bot_request = true;
    } else {
        $error_code = 401;
        $error_message = 'Invalid bot token';
    }
}

$type = !empty($_POST['type']) ? Wo_Secure($_POST['type']) : '';

if (empty($error_code)) {
    switch ($type) {

        // ==================== BOT MANAGEMENT (User endpoints) ====================

        case 'create_bot':
            // Creates a new bot. Requires user access_token.
            $required = array('username', 'display_name');
            foreach ($required as $field) {
                if (empty($_POST[$field]) && empty($error_code)) {
                    $error_code = 3;
                    $error_message = $field . ' is required';
                }
            }
            if (empty($error_code)) {
                $username = Wo_Secure($_POST['username']);
                $display_name = Wo_Secure($_POST['display_name']);
                $description = !empty($_POST['description']) ? Wo_Secure($_POST['description']) : '';
                $about = !empty($_POST['about']) ? Wo_Secure($_POST['about']) : '';
                $category = !empty($_POST['category']) ? Wo_Secure($_POST['category']) : 'general';
                $can_join_groups = isset($_POST['can_join_groups']) ? (int)$_POST['can_join_groups'] : 1;
                $is_public = isset($_POST['is_public']) ? (int)$_POST['is_public'] : 1;

                // Validate username format (alphanumeric + underscore, ends with _bot)
                if (!preg_match('/^[a-zA-Z][a-zA-Z0-9_]{2,30}_bot$/', $username)) {
                    $error_code = 10;
                    $error_message = 'Bot username must be 3-31 chars, alphanumeric/underscore, end with _bot';
                    break;
                }

                // Check username uniqueness
                $existing = mysqli_query($sqlConnect, "SELECT id FROM Wo_Bots WHERE username = '{$username}' LIMIT 1");
                if (mysqli_num_rows($existing) > 0) {
                    $error_code = 11;
                    $error_message = 'Bot username already taken';
                    break;
                }

                // Check bot limit per user (max 20 bots)
                $bot_count = mysqli_query($sqlConnect, "SELECT COUNT(*) as cnt FROM Wo_Bots WHERE owner_id = '{$wo['user']['user_id']}'");
                $cnt_row = mysqli_fetch_assoc($bot_count);
                if ($cnt_row['cnt'] >= 20) {
                    $error_code = 12;
                    $error_message = 'Maximum 20 bots per user';
                    break;
                }

                // Generate bot ID and token
                $bot_id = 'bot_' . bin2hex(random_bytes(16));
                $new_token = generateBotToken($bot_id);

                $insert = mysqli_query($sqlConnect, "INSERT INTO Wo_Bots
                    (bot_id, owner_id, bot_token, username, display_name, description, about, category, can_join_groups, is_public, created_at)
                    VALUES (
                        '{$bot_id}',
                        '{$wo['user']['user_id']}',
                        '{$new_token}',
                        '{$username}',
                        '{$display_name}',
                        '{$description}',
                        '{$about}',
                        '{$category}',
                        {$can_join_groups},
                        {$is_public},
                        NOW()
                    )");

                if ($insert) {
                    // Register default commands
                    registerDefaultCommands($sqlConnect, $bot_id);

                    $response_data = array(
                        'api_status' => 200,
                        'bot' => array(
                            'bot_id' => $bot_id,
                            'bot_token' => $new_token,
                            'username' => $username,
                            'display_name' => $display_name,
                            'description' => $description,
                            'category' => $category,
                            'status' => 'active'
                        ),
                        'message' => 'Bot created successfully. Save your bot_token - it will not be shown again.'
                    );
                } else {
                    $error_code = 500;
                    $error_message = 'Failed to create bot';
                }
            }
            break;

        case 'get_my_bots':
            // List all bots owned by the authenticated user
            $limit = !empty($_POST['limit']) ? min((int)$_POST['limit'], 50) : 20;
            $offset = !empty($_POST['offset']) ? (int)$_POST['offset'] : 0;

            $result = mysqli_query($sqlConnect, "SELECT bot_id, username, display_name, avatar, description, about,
                bot_type, status, is_public, category, messages_sent, messages_received, total_users, active_users_24h,
                created_at, last_active_at, webhook_url, webhook_enabled
                FROM Wo_Bots
                WHERE owner_id = '{$wo['user']['user_id']}'
                ORDER BY created_at DESC
                LIMIT {$offset}, {$limit}");

            $bots = array();
            while ($row = mysqli_fetch_assoc($result)) {
                // Get command count
                $cmd_res = mysqli_query($sqlConnect, "SELECT COUNT(*) as cnt FROM Wo_Bot_Commands WHERE bot_id = '{$row['bot_id']}'");
                $cmd_count = mysqli_fetch_assoc($cmd_res)['cnt'];
                $row['commands_count'] = (int)$cmd_count;
                $bots[] = $row;
            }

            $response_data = array(
                'api_status' => 200,
                'bots' => $bots,
                'count' => count($bots)
            );
            break;

        case 'update_bot':
            // Update bot settings. Requires user access_token + bot_id
            if (empty($_POST['bot_id'])) {
                $error_code = 3;
                $error_message = 'bot_id is required';
                break;
            }
            $target_bot_id = Wo_Secure($_POST['bot_id']);

            // Verify ownership
            $bot = getBotByIdForOwner($sqlConnect, $target_bot_id, $wo['user']['user_id']);
            if (!$bot) {
                $error_code = 403;
                $error_message = 'Bot not found or access denied';
                break;
            }

            $updates = array();
            $allowed_fields = array('display_name', 'description', 'about', 'category', 'is_public', 'can_join_groups',
                                    'can_read_all_group_messages', 'is_inline', 'supports_commands');
            foreach ($allowed_fields as $field) {
                if (isset($_POST[$field])) {
                    $val = Wo_Secure($_POST[$field]);
                    $updates[] = "`{$field}` = '{$val}'";
                }
            }

            if (!empty($updates)) {
                $update_sql = implode(', ', $updates);
                mysqli_query($sqlConnect, "UPDATE Wo_Bots SET {$update_sql}, updated_at = NOW() WHERE bot_id = '{$target_bot_id}'");
            }

            $response_data = array('api_status' => 200, 'message' => 'Bot updated successfully');
            break;

        case 'delete_bot':
            if (empty($_POST['bot_id'])) {
                $error_code = 3;
                $error_message = 'bot_id is required';
                break;
            }
            $target_bot_id = Wo_Secure($_POST['bot_id']);
            $bot = getBotByIdForOwner($sqlConnect, $target_bot_id, $wo['user']['user_id']);
            if (!$bot) {
                $error_code = 403;
                $error_message = 'Bot not found or access denied';
                break;
            }

            // Cascade delete all bot data
            mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Commands WHERE bot_id = '{$target_bot_id}'");
            mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Messages WHERE bot_id = '{$target_bot_id}'");
            mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Users WHERE bot_id = '{$target_bot_id}'");
            mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Keyboards WHERE bot_id = '{$target_bot_id}'");
            mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Callbacks WHERE bot_id = '{$target_bot_id}'");
            mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Webhook_Log WHERE bot_id = '{$target_bot_id}'");
            mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Rate_Limits WHERE bot_id = '{$target_bot_id}'");
            mysqli_query($sqlConnect, "DELETE FROM Wo_Bots WHERE bot_id = '{$target_bot_id}'");

            $response_data = array('api_status' => 200, 'message' => 'Bot deleted successfully');
            break;

        case 'regenerate_token':
            if (empty($_POST['bot_id'])) {
                $error_code = 3;
                $error_message = 'bot_id is required';
                break;
            }
            $target_bot_id = Wo_Secure($_POST['bot_id']);
            $bot = getBotByIdForOwner($sqlConnect, $target_bot_id, $wo['user']['user_id']);
            if (!$bot) {
                $error_code = 403;
                $error_message = 'Bot not found or access denied';
                break;
            }

            $new_token = generateBotToken($target_bot_id);
            mysqli_query($sqlConnect, "UPDATE Wo_Bots SET bot_token = '{$new_token}', updated_at = NOW() WHERE bot_id = '{$target_bot_id}'");

            $response_data = array(
                'api_status' => 200,
                'bot_token' => $new_token,
                'message' => 'Token regenerated. Old token is now invalid.'
            );
            break;

        // ==================== COMMAND MANAGEMENT ====================

        case 'set_commands':
            // Set bot commands. Requires bot_token.
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }
            if (empty($_POST['commands'])) {
                $error_code = 3;
                $error_message = 'commands (JSON array) is required';
                break;
            }

            $commands = json_decode($_POST['commands'], true);
            if (!is_array($commands)) {
                $error_code = 10;
                $error_message = 'commands must be a valid JSON array';
                break;
            }

            if (count($commands) > 100) {
                $error_code = 11;
                $error_message = 'Maximum 100 commands per bot';
                break;
            }

            // Clear existing commands and insert new ones
            $bid = $bot_data['bot_id'];
            mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Commands WHERE bot_id = '{$bid}'");

            $order = 0;
            foreach ($commands as $cmd) {
                if (empty($cmd['command']) || empty($cmd['description'])) continue;

                $command = Wo_Secure(strtolower($cmd['command']));
                $desc = Wo_Secure($cmd['description']);
                $hint = !empty($cmd['usage_hint']) ? Wo_Secure($cmd['usage_hint']) : '';
                $hidden = !empty($cmd['is_hidden']) ? 1 : 0;
                $scope = !empty($cmd['scope']) ? Wo_Secure($cmd['scope']) : 'all';

                // Remove leading slash if present
                $command = ltrim($command, '/');

                mysqli_query($sqlConnect, "INSERT INTO Wo_Bot_Commands
                    (bot_id, command, description, usage_hint, is_hidden, scope, sort_order)
                    VALUES ('{$bid}', '{$command}', '{$desc}', '{$hint}', {$hidden}, '{$scope}', {$order})");
                $order++;
            }

            $response_data = array('api_status' => 200, 'message' => 'Commands updated', 'count' => $order);
            break;

        case 'get_commands':
            // Get bot commands. Works with bot_token or bot_id param
            $target_bot_id = '';
            if ($is_bot_request) {
                $target_bot_id = $bot_data['bot_id'];
            } elseif (!empty($_POST['bot_id'])) {
                $target_bot_id = Wo_Secure($_POST['bot_id']);
            } else {
                $error_code = 3;
                $error_message = 'bot_token or bot_id required';
                break;
            }

            $result = mysqli_query($sqlConnect, "SELECT command, description, usage_hint, scope
                FROM Wo_Bot_Commands
                WHERE bot_id = '{$target_bot_id}' AND is_hidden = 0
                ORDER BY sort_order ASC");

            $commands = array();
            while ($row = mysqli_fetch_assoc($result)) {
                $commands[] = $row;
            }

            $response_data = array('api_status' => 200, 'commands' => $commands);
            break;

        // ==================== MESSAGING (Bot endpoints) ====================

        case 'user_to_bot':
            // User sends message to bot (bridges Messenger chat -> Bot update queue)
            if (empty($wo['user']) || empty($wo['user']['user_id'])) {
                $error_code = 401;
                $error_message = 'User auth required';
                break;
            }

            if (empty($_POST['bot_id'])) {
                $error_code = 3;
                $error_message = 'bot_id is required';
                break;
            }

            $target_bot_id = Wo_Secure($_POST['bot_id']);
            $text = isset($_POST['text']) ? trim($_POST['text']) : '';
            $chat_type = !empty($_POST['chat_type']) ? Wo_Secure($_POST['chat_type']) : 'private';

            if ($text === '' && empty($_POST['callback_data'])) {
                $error_code = 10;
                $error_message = 'text or callback_data is required';
                break;
            }

            $bot_exists = mysqli_query($sqlConnect, "SELECT bot_id, status FROM Wo_Bots WHERE bot_id = '{$target_bot_id}' LIMIT 1");
            $bot_row = $bot_exists ? mysqli_fetch_assoc($bot_exists) : null;
            if (!$bot_row || $bot_row['status'] !== 'active') {
                $error_code = 404;
                $error_message = 'Bot not found or inactive';
                break;
            }

            $user_id = (int)$wo['user']['user_id'];
            $callback_data = !empty($_POST['callback_data']) ? Wo_Secure($_POST['callback_data']) : null;

            // Auto-detect slash command if client didn't provide explicit flags
            $is_command = 0;
            $command_name = null;
            $command_args = null;
            if (preg_match('/^\/([a-zA-Z0-9_]+)(?:\s+(.*))?$/u', $text, $m)) {
                $is_command = 1;
                $command_name = strtolower($m[1]);
                $command_args = isset($m[2]) ? trim($m[2]) : null;
            }

            $safe_text = Wo_Secure($text);
            $safe_chat_type = Wo_Secure($chat_type);
            $safe_callback = $callback_data ? "'" . Wo_Secure($callback_data) . "'" : 'NULL';
            $safe_cmd_name = $command_name ? "'" . Wo_Secure($command_name) . "'" : 'NULL';
            $safe_cmd_args = $command_args ? "'" . Wo_Secure($command_args) . "'" : 'NULL';

            $insert = mysqli_query($sqlConnect, "INSERT INTO Wo_Bot_Messages
                (bot_id, chat_id, chat_type, direction, text, callback_data, is_command, command_name, command_args, processed, created_at)
                VALUES ('{$target_bot_id}', '{$user_id}', '{$safe_chat_type}', 'incoming', '{$safe_text}', {$safe_callback}, {$is_command}, {$safe_cmd_name}, {$safe_cmd_args}, 0, NOW())");

            if (!$insert) {
                $error_code = 500;
                $error_message = 'Failed to queue message for bot: ' . mysqli_error($sqlConnect);
                break;
            }

            mysqli_query($sqlConnect, "UPDATE Wo_Bots SET messages_received = messages_received + 1, last_active_at = NOW() WHERE bot_id = '{$target_bot_id}'");
            updateBotUser($sqlConnect, $target_bot_id, $user_id);

            $response_data = array(
                'api_status' => 200,
                'message' => 'Message queued for bot',
                'bot_id' => $target_bot_id,
                'is_command' => $is_command,
                'command_name' => $command_name,
                'command_args' => $command_args
            );
            break;

        case 'send_message':
            // Bot sends a message to a user or group
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }

            if (empty($_POST['chat_id'])) {
                $error_code = 3;
                $error_message = 'chat_id is required';
                break;
            }

            // Rate limiting check
            if (!checkBotRateLimit($sqlConnect, $bot_data['bot_id'], 'send_message', $bot_data['rate_limit_per_second'], $bot_data['rate_limit_per_minute'])) {
                $error_code = 429;
                $error_message = 'Rate limit exceeded. Please slow down.';
                break;
            }

            $chat_id = Wo_Secure($_POST['chat_id']);
            $chat_type = !empty($_POST['chat_type']) ? Wo_Secure($_POST['chat_type']) : 'private';
            $text = !empty($_POST['text']) ? $_POST['text'] : null;
            $media_type = !empty($_POST['media_type']) ? Wo_Secure($_POST['media_type']) : null;
            $media_url = !empty($_POST['media_url']) ? Wo_Secure($_POST['media_url']) : null;
            $reply_to = !empty($_POST['reply_to_message_id']) ? (int)$_POST['reply_to_message_id'] : null;
            $reply_markup = !empty($_POST['reply_markup']) ? $_POST['reply_markup'] : null;
            $disable_notification = !empty($_POST['disable_notification']) ? 1 : 0;
            $parse_mode = !empty($_POST['parse_mode']) ? Wo_Secure($_POST['parse_mode']) : 'text';

            if (empty($text) && empty($media_url) && empty($_FILES['media'])) {
                $error_code = 10;
                $error_message = 'Message must contain text, media_url, or media file';
                break;
            }

            // Handle file upload
            if (!empty($_FILES['media'])) {
                $media_url = handleBotMediaUpload($_FILES['media'], $bot_data['bot_id']);
                if (!$media_url) {
                    $error_code = 13;
                    $error_message = 'Failed to upload media file';
                    break;
                }
                if (empty($media_type)) {
                    $media_type = detectMediaType($_FILES['media']['type']);
                }
            }

            // Validate reply_markup JSON
            if ($reply_markup) {
                $markup_decoded = json_decode($reply_markup, true);
                if (!$markup_decoded) {
                    $error_code = 14;
                    $error_message = 'reply_markup must be valid JSON';
                    break;
                }
            }

            // Parse message entities (bold, italic, links, code, etc.)
            $entities = null;
            $clean_text = $text;
            if ($text && $parse_mode !== 'text') {
                $parsed = parseMessageEntities($text, $parse_mode);
                $clean_text = $parsed['text'];
                $entities = $parsed['entities'] ? json_encode($parsed['entities']) : null;
            }

            // Check if user has blocked the bot
            $is_blocked = checkUserBlockedBot($sqlConnect, $chat_id, $bot_data['bot_id']);
            if ($is_blocked) {
                $error_code = 403;
                $error_message = 'Bot was blocked by the user';
                break;
            }

            // Insert bot message record
            $safe_text = $clean_text ? Wo_Secure($clean_text) : '';
            $safe_entities = $entities ? Wo_Secure($entities) : '';
            $safe_markup = $reply_markup ? Wo_Secure($reply_markup) : '';
            $safe_media = $media_url ? Wo_Secure($media_url) : '';
            $safe_media_type = $media_type ? Wo_Secure($media_type) : '';
            $reply_to_val = $reply_to ? $reply_to : 'NULL';

            $insert = mysqli_query($sqlConnect, "INSERT INTO Wo_Bot_Messages
                (bot_id, chat_id, chat_type, direction, text, media_type, media_url,
                 reply_to_message_id, reply_markup, entities, processed, processed_at, created_at)
                VALUES (
                    '{$bot_data['bot_id']}', '{$chat_id}', '{$chat_type}', 'outgoing',
                    '{$safe_text}', '{$safe_media_type}', '{$safe_media}',
                    {$reply_to_val}, '{$safe_markup}', '{$safe_entities}', 1, NOW(), NOW()
                )");

            if ($insert) {
                $message_id = mysqli_insert_id($sqlConnect);

                // Also send via the real messaging system (Wo_Messages)
                $real_message_id = sendBotMessageToUser($sqlConnect, $bot_data, $chat_id, $chat_type, $clean_text, $media_type, $media_url, $reply_to);

                // Update bot stats
                mysqli_query($sqlConnect, "UPDATE Wo_Bots SET messages_sent = messages_sent + 1, last_active_at = NOW() WHERE bot_id = '{$bot_data['bot_id']}'");

                // Update/create bot-user relationship
                updateBotUser($sqlConnect, $bot_data['bot_id'], $chat_id);

                $response_data = array(
                    'api_status' => 200,
                    'message_id' => $message_id,
                    'real_message_id' => $real_message_id,
                    'chat_id' => $chat_id,
                    'text' => $clean_text,
                    'date' => time()
                );

                // Notify via Socket.IO if possible
                notifyViaBotSocket($bot_data['bot_id'], $chat_id, $clean_text, $message_id, $reply_markup);
            } else {
                $error_code = 500;
                $error_message = 'Failed to send message';
            }
            break;

        case 'edit_message':
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }
            if (empty($_POST['message_id']) || empty($_POST['chat_id'])) {
                $error_code = 3;
                $error_message = 'message_id and chat_id required';
                break;
            }

            $msg_id = (int)$_POST['message_id'];
            $chat_id = Wo_Secure($_POST['chat_id']);
            $new_text = !empty($_POST['text']) ? Wo_Secure($_POST['text']) : null;
            $new_markup = !empty($_POST['reply_markup']) ? Wo_Secure($_POST['reply_markup']) : null;

            $updates = array();
            if ($new_text) $updates[] = "text = '{$new_text}'";
            if ($new_markup) $updates[] = "reply_markup = '{$new_markup}'";

            if (!empty($updates)) {
                $update_sql = implode(', ', $updates);
                mysqli_query($sqlConnect, "UPDATE Wo_Bot_Messages SET {$update_sql}
                    WHERE id = {$msg_id} AND bot_id = '{$bot_data['bot_id']}' AND chat_id = '{$chat_id}' AND direction = 'outgoing'");

                $response_data = array('api_status' => 200, 'message' => 'Message updated');
            } else {
                $error_code = 10;
                $error_message = 'Nothing to update';
            }
            break;

        case 'delete_message':
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }
            if (empty($_POST['message_id']) || empty($_POST['chat_id'])) {
                $error_code = 3;
                $error_message = 'message_id and chat_id required';
                break;
            }

            $msg_id = (int)$_POST['message_id'];
            $chat_id = Wo_Secure($_POST['chat_id']);

            mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Messages
                WHERE id = {$msg_id} AND bot_id = '{$bot_data['bot_id']}' AND chat_id = '{$chat_id}'");

            $response_data = array('api_status' => 200, 'message' => 'Message deleted');
            break;

        case 'get_updates':
            // Long polling - get new messages/events for the bot
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }

            $offset = !empty($_POST['offset']) ? (int)$_POST['offset'] : 0;
            $limit = !empty($_POST['limit']) ? min((int)$_POST['limit'], 100) : 20;
            $timeout = !empty($_POST['timeout']) ? min((int)$_POST['timeout'], 30) : 0;
            $allowed_updates = !empty($_POST['allowed_updates']) ? json_decode($_POST['allowed_updates'], true) : null;

            $bid = $bot_data['bot_id'];

            // Build WHERE clause for update types
            $type_filter = '';
            if ($allowed_updates && is_array($allowed_updates)) {
                $types = array_map(function($t) { return "'" . Wo_Secure($t) . "'"; }, $allowed_updates);
                // This maps to: messages, callback_query, command, etc
            }

            // Get unprocessed incoming messages
            $where_offset = $offset > 0 ? "AND m.id > {$offset}" : '';
            $result = mysqli_query($sqlConnect, "
                SELECT m.id as update_id, m.chat_id, m.chat_type, m.text, m.media_type, m.media_url,
                       m.is_command, m.command_name, m.command_args, m.reply_to_message_id,
                       m.callback_data, m.created_at,
                       u.username as sender_username, u.first_name as sender_first_name,
                       u.last_name as sender_last_name, u.avatar as sender_avatar
                FROM Wo_Bot_Messages m
                LEFT JOIN Wo_Users u ON m.chat_id = CAST(u.user_id AS CHAR)
                WHERE m.bot_id = '{$bid}' AND m.direction = 'incoming' AND m.processed = 0
                {$where_offset}
                ORDER BY m.id ASC
                LIMIT {$limit}
            ");

            $updates = array();
            while ($row = mysqli_fetch_assoc($result)) {
                $update = array(
                    'update_id' => (int)$row['update_id'],
                    'message' => array(
                        'message_id' => (int)$row['update_id'],
                        'from' => array(
                            'id' => (int)$row['chat_id'],
                            'username' => $row['sender_username'],
                            'first_name' => $row['sender_first_name'],
                            'last_name' => $row['sender_last_name'],
                            'avatar' => $row['sender_avatar']
                        ),
                        'chat' => array(
                            'id' => $row['chat_id'],
                            'type' => $row['chat_type']
                        ),
                        'date' => strtotime($row['created_at']),
                        'text' => $row['text']
                    )
                );

                // Add command info if applicable
                if ($row['is_command'] && $row['command_name']) {
                    $update['message']['entities'] = array(
                        array('type' => 'bot_command', 'offset' => 0, 'length' => strlen($row['command_name']) + 1)
                    );
                    $update['command'] = array(
                        'name' => $row['command_name'],
                        'args' => $row['command_args']
                    );
                }

                // Add callback query if present
                if ($row['callback_data']) {
                    $update['callback_query'] = array(
                        'id' => $row['update_id'],
                        'from' => $update['message']['from'],
                        'data' => $row['callback_data'],
                        'message' => $update['message']
                    );
                }

                // Add media info
                if ($row['media_type']) {
                    $update['message']['media'] = array(
                        'type' => $row['media_type'],
                        'url' => $row['media_url']
                    );
                }

                $updates[] = $update;
            }

            // Mark as processed
            if (!empty($updates)) {
                $max_id = end($updates)['update_id'];
                mysqli_query($sqlConnect, "UPDATE Wo_Bot_Messages SET processed = 1, processed_at = NOW()
                    WHERE bot_id = '{$bid}' AND direction = 'incoming' AND id <= {$max_id}");
            }

            $response_data = array('api_status' => 200, 'updates' => $updates);
            break;

        case 'answer_callback_query':
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }
            if (empty($_POST['callback_query_id'])) {
                $error_code = 3;
                $error_message = 'callback_query_id required';
                break;
            }

            $cb_id = (int)$_POST['callback_query_id'];
            $answer_text = !empty($_POST['text']) ? Wo_Secure($_POST['text']) : '';
            $show_alert = !empty($_POST['show_alert']) ? 1 : 0;

            mysqli_query($sqlConnect, "UPDATE Wo_Bot_Callbacks SET answered = 1, answer_text = '{$answer_text}',
                answer_show_alert = {$show_alert} WHERE id = {$cb_id} AND bot_id = '{$bot_data['bot_id']}'");

            $response_data = array('api_status' => 200, 'message' => 'Callback answered');
            break;

        // ==================== WEBHOOKS ====================

        case 'set_webhook':
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }
            if (empty($_POST['url'])) {
                $error_code = 3;
                $error_message = 'url is required';
                break;
            }

            $url = Wo_Secure($_POST['url']);
            $secret = !empty($_POST['secret']) ? Wo_Secure($_POST['secret']) : bin2hex(random_bytes(32));
            $max_connections = !empty($_POST['max_connections']) ? min((int)$_POST['max_connections'], 100) : 40;
            $allowed_updates = !empty($_POST['allowed_updates']) ? Wo_Secure($_POST['allowed_updates']) : null;

            // Validate URL (must be HTTPS)
            if (!filter_var($url, FILTER_VALIDATE_URL) || strpos($url, 'https://') !== 0) {
                $error_code = 10;
                $error_message = 'Webhook URL must be a valid HTTPS URL';
                break;
            }

            $bid = $bot_data['bot_id'];
            $allowed_json = $allowed_updates ? "'{$allowed_updates}'" : 'NULL';

            mysqli_query($sqlConnect, "UPDATE Wo_Bots SET
                webhook_url = '{$url}',
                webhook_secret = '{$secret}',
                webhook_enabled = 1,
                webhook_max_connections = {$max_connections},
                webhook_allowed_updates = {$allowed_json},
                updated_at = NOW()
                WHERE bot_id = '{$bid}'");

            $response_data = array(
                'api_status' => 200,
                'message' => 'Webhook set successfully',
                'webhook_url' => $url,
                'webhook_secret' => $secret,
                'has_custom_certificate' => false,
                'max_connections' => $max_connections
            );
            break;

        case 'delete_webhook':
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }

            $bid = $bot_data['bot_id'];
            mysqli_query($sqlConnect, "UPDATE Wo_Bots SET
                webhook_url = NULL, webhook_secret = NULL, webhook_enabled = 0,
                webhook_allowed_updates = NULL, updated_at = NOW()
                WHERE bot_id = '{$bid}'");

            $response_data = array('api_status' => 200, 'message' => 'Webhook removed');
            break;

        case 'get_webhook_info':
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }

            $bid = $bot_data['bot_id'];
            $result = mysqli_query($sqlConnect, "SELECT webhook_url, webhook_enabled, webhook_max_connections,
                webhook_allowed_updates FROM Wo_Bots WHERE bot_id = '{$bid}'");
            $info = mysqli_fetch_assoc($result);

            // Get pending webhook count
            $pending = mysqli_query($sqlConnect, "SELECT COUNT(*) as cnt FROM Wo_Bot_Webhook_Log
                WHERE bot_id = '{$bid}' AND delivery_status IN ('pending','retrying')");
            $pending_count = mysqli_fetch_assoc($pending)['cnt'];

            // Get last error
            $last_err = mysqli_query($sqlConnect, "SELECT response_code, response_body, created_at FROM Wo_Bot_Webhook_Log
                WHERE bot_id = '{$bid}' AND delivery_status = 'failed' ORDER BY id DESC LIMIT 1");
            $last_error = mysqli_fetch_assoc($last_err);

            $response_data = array(
                'api_status' => 200,
                'url' => $info['webhook_url'],
                'has_custom_certificate' => false,
                'pending_update_count' => (int)$pending_count,
                'max_connections' => (int)$info['webhook_max_connections'],
                'allowed_updates' => $info['webhook_allowed_updates'] ? json_decode($info['webhook_allowed_updates']) : [],
                'last_error_date' => $last_error ? strtotime($last_error['created_at']) : null,
                'last_error_message' => $last_error ? $last_error['response_body'] : null
            );
            break;

        // ==================== POLLS ====================

        case 'send_poll':
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }
            $required = array('chat_id', 'question', 'options');
            foreach ($required as $field) {
                if (empty($_POST[$field]) && empty($error_code)) {
                    $error_code = 3;
                    $error_message = $field . ' is required';
                }
            }
            if (!empty($error_code)) break;

            $chat_id = Wo_Secure($_POST['chat_id']);
            $question = Wo_Secure($_POST['question']);
            $options = json_decode($_POST['options'], true);
            $poll_type = !empty($_POST['poll_type']) ? Wo_Secure($_POST['poll_type']) : 'regular';
            $is_anonymous = isset($_POST['is_anonymous']) ? (int)$_POST['is_anonymous'] : 1;
            $allows_multiple = isset($_POST['allows_multiple_answers']) ? (int)$_POST['allows_multiple_answers'] : 0;
            $correct_option = isset($_POST['correct_option_id']) ? (int)$_POST['correct_option_id'] : 'NULL';
            $explanation = !empty($_POST['explanation']) ? Wo_Secure($_POST['explanation']) : '';

            if (!is_array($options) || count($options) < 2 || count($options) > 10) {
                $error_code = 10;
                $error_message = 'Poll must have 2-10 options';
                break;
            }

            $bid = $bot_data['bot_id'];
            $correct_val = is_int($correct_option) ? $correct_option : 'NULL';

            mysqli_query($sqlConnect, "INSERT INTO Wo_Bot_Polls
                (bot_id, chat_id, question, poll_type, is_anonymous, allows_multiple_answers, correct_option_id, explanation, created_at)
                VALUES ('{$bid}', '{$chat_id}', '{$question}', '{$poll_type}', {$is_anonymous}, {$allows_multiple},
                         {$correct_val}, '{$explanation}', NOW())");

            $poll_id = mysqli_insert_id($sqlConnect);

            // Insert options
            foreach ($options as $idx => $option_text) {
                $safe_opt = Wo_Secure($option_text);
                mysqli_query($sqlConnect, "INSERT INTO Wo_Bot_Poll_Options (poll_id, option_text, option_index)
                    VALUES ({$poll_id}, '{$safe_opt}', {$idx})");
            }

            // Send poll as a bot message
            $poll_text = "📊 *{$question}*\n\n";
            foreach ($options as $idx => $opt) {
                $emoji_num = chr(0x31 + $idx) . "\xE2\x83\xA3"; // 1️⃣, 2️⃣, etc.
                $poll_text .= ($idx + 1) . ". {$opt}\n";
            }
            $poll_text .= "\nВідповідайте натиснувши кнопку нижче.";

            // Create inline keyboard for voting
            $keyboard_buttons = array();
            foreach ($options as $idx => $opt) {
                $keyboard_buttons[] = array(
                    array('text' => ($idx + 1) . ". " . $opt, 'callback_data' => "poll_vote_{$poll_id}_{$idx}")
                );
            }
            $reply_markup = json_encode(array('inline_keyboard' => $keyboard_buttons));

            // Insert as outgoing message with poll
            $safe_poll_text = Wo_Secure($poll_text);
            $safe_markup = Wo_Secure($reply_markup);
            mysqli_query($sqlConnect, "INSERT INTO Wo_Bot_Messages
                (bot_id, chat_id, chat_type, direction, text, reply_markup, processed, processed_at, created_at)
                VALUES ('{$bid}', '{$chat_id}', 'private', 'outgoing', '{$safe_poll_text}', '{$safe_markup}', 1, NOW(), NOW())");

            $msg_id = mysqli_insert_id($sqlConnect);
            mysqli_query($sqlConnect, "UPDATE Wo_Bot_Polls SET message_id = {$msg_id} WHERE id = {$poll_id}");

            $response_data = array(
                'api_status' => 200,
                'poll' => array(
                    'poll_id' => $poll_id,
                    'message_id' => $msg_id,
                    'question' => $question,
                    'options' => $options,
                    'type' => $poll_type,
                    'is_anonymous' => $is_anonymous,
                    'total_voters' => 0
                )
            );
            break;

        case 'stop_poll':
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }
            if (empty($_POST['poll_id'])) {
                $error_code = 3;
                $error_message = 'poll_id is required';
                break;
            }

            $poll_id = (int)$_POST['poll_id'];
            mysqli_query($sqlConnect, "UPDATE Wo_Bot_Polls SET is_closed = 1, closed_at = NOW()
                WHERE id = {$poll_id} AND bot_id = '{$bot_data['bot_id']}'");

            // Get results
            $result = mysqli_query($sqlConnect, "SELECT o.option_text, o.voter_count, o.option_index
                FROM Wo_Bot_Poll_Options o WHERE o.poll_id = {$poll_id} ORDER BY o.option_index");

            $poll_results = array();
            while ($row = mysqli_fetch_assoc($result)) {
                $poll_results[] = $row;
            }

            $response_data = array('api_status' => 200, 'results' => $poll_results, 'is_closed' => true);
            break;

        // ==================== BOT INFO (Public) ====================

        case 'get_bot_info':
            // Get public bot info (works without auth)
            $target = '';
            if ($is_bot_request) {
                $target = $bot_data['bot_id'];
            } elseif (!empty($_POST['bot_id'])) {
                $target = Wo_Secure($_POST['bot_id']);
            } elseif (!empty($_POST['username'])) {
                $target = Wo_Secure($_POST['username']);
            } else {
                $error_code = 3;
                $error_message = 'bot_id or username required';
                break;
            }

            $where = preg_match('/^bot_/', $target) ? "bot_id = '{$target}'" : "username = '{$target}'";
            $result = mysqli_query($sqlConnect, "SELECT bot_id, username, display_name, avatar, description, about,
                bot_type, is_public, is_inline, can_join_groups, supports_commands, category, tags,
                total_users, created_at
                FROM Wo_Bots WHERE {$where} AND status = 'active' LIMIT 1");

            $bot_info = mysqli_fetch_assoc($result);
            if (!$bot_info) {
                $error_code = 404;
                $error_message = 'Bot not found';
                break;
            }

            // Get commands
            $cmds_result = mysqli_query($sqlConnect, "SELECT command, description, usage_hint FROM Wo_Bot_Commands
                WHERE bot_id = '{$bot_info['bot_id']}' AND is_hidden = 0 ORDER BY sort_order");
            $cmds = array();
            while ($c = mysqli_fetch_assoc($cmds_result)) {
                $cmds[] = $c;
            }
            $bot_info['commands'] = $cmds;

            $response_data = array('api_status' => 200, 'bot' => $bot_info);
            break;

        case 'search_bots':
            // Search public bots
            $query = !empty($_POST['query']) ? Wo_Secure($_POST['query']) : '';
            $category = !empty($_POST['category']) ? Wo_Secure($_POST['category']) : '';
            $limit = !empty($_POST['limit']) ? min((int)$_POST['limit'], 50) : 20;
            $offset = !empty($_POST['offset']) ? (int)$_POST['offset'] : 0;

            $where = "status = 'active' AND is_public = 1";
            if ($query) {
                $where .= " AND (username LIKE '%{$query}%' OR display_name LIKE '%{$query}%' OR description LIKE '%{$query}%' OR tags LIKE '%{$query}%')";
            }
            if ($category) {
                $where .= " AND category = '{$category}'";
            }

            $result = mysqli_query($sqlConnect, "SELECT bot_id, username, display_name, avatar, description, about,
                category, total_users, bot_type
                FROM Wo_Bots WHERE {$where}
                ORDER BY total_users DESC, created_at DESC
                LIMIT {$offset}, {$limit}");

            if (!$result) {
                $error_code = 500;
                $error_message = 'search_bots query failed: ' . mysqli_error($sqlConnect);
                break;
            }

            $bots = array();
            while ($row = mysqli_fetch_assoc($result)) {
                $bots[] = $row;
            }

            // Get categories
            $cats_result = mysqli_query($sqlConnect, "SELECT DISTINCT category, COUNT(*) as count
                FROM Wo_Bots WHERE status = 'active' AND is_public = 1 AND category IS NOT NULL
                GROUP BY category ORDER BY count DESC");

            if (!$cats_result) {
                $error_code = 500;
                $error_message = 'search_bots categories query failed: ' . mysqli_error($sqlConnect);
                break;
            }

            $categories = array();
            while ($cat = mysqli_fetch_assoc($cats_result)) {
                $categories[] = $cat;
            }

            $response_data = array(
                'api_status' => 200,
                'bots' => $bots,
                'categories' => $categories,
                'total' => count($bots)
            );
            break;

        // ==================== BOT USER MANAGEMENT ====================

        case 'get_chat_member':
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }
            if (empty($_POST['user_id'])) {
                $error_code = 3;
                $error_message = 'user_id required';
                break;
            }

            $uid = Wo_Secure($_POST['user_id']);
            $bid = $bot_data['bot_id'];

            $result = mysqli_query($sqlConnect, "SELECT bu.*, u.username, u.first_name, u.last_name, u.avatar
                FROM Wo_Bot_Users bu
                LEFT JOIN Wo_Users u ON bu.user_id = u.user_id
                WHERE bu.bot_id = '{$bid}' AND bu.user_id = '{$uid}' LIMIT 1");

            $member = mysqli_fetch_assoc($result);
            if (!$member) {
                $error_code = 404;
                $error_message = 'User not found in bot users';
                break;
            }

            $response_data = array('api_status' => 200, 'user' => $member);
            break;

        case 'set_user_state':
            // Set conversation state for a user (FSM)
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }
            if (empty($_POST['user_id'])) {
                $error_code = 3;
                $error_message = 'user_id required';
                break;
            }

            $uid = Wo_Secure($_POST['user_id']);
            $state = !empty($_POST['state']) ? Wo_Secure($_POST['state']) : null;
            $state_data = !empty($_POST['state_data']) ? Wo_Secure($_POST['state_data']) : null;

            $bid = $bot_data['bot_id'];
            $state_val = $state ? "'{$state}'" : 'NULL';
            $data_val = $state_data ? "'{$state_data}'" : 'NULL';

            mysqli_query($sqlConnect, "UPDATE Wo_Bot_Users SET state = {$state_val}, state_data = {$data_val}
                WHERE bot_id = '{$bid}' AND user_id = '{$uid}'");

            $response_data = array('api_status' => 200, 'message' => 'User state updated');
            break;

        case 'get_user_state':
            if (!$is_bot_request) {
                $error_code = 401;
                $error_message = 'Bot token required';
                break;
            }
            if (empty($_POST['user_id'])) {
                $error_code = 3;
                $error_message = 'user_id required';
                break;
            }

            $uid = Wo_Secure($_POST['user_id']);
            $bid = $bot_data['bot_id'];

            $result = mysqli_query($sqlConnect, "SELECT state, state_data, custom_data FROM Wo_Bot_Users
                WHERE bot_id = '{$bid}' AND user_id = '{$uid}' LIMIT 1");
            $user_state = mysqli_fetch_assoc($result);

            $response_data = array(
                'api_status' => 200,
                'state' => $user_state ? $user_state['state'] : null,
                'state_data' => $user_state ? json_decode($user_state['state_data']) : null,
                'custom_data' => $user_state ? json_decode($user_state['custom_data']) : null
            );
            break;

        default:
            $error_code = 1;
            $error_message = 'Unknown type: ' . $type . '. Available types: create_bot, get_my_bots, update_bot, delete_bot, regenerate_token, set_commands, get_commands, user_to_bot, send_message, edit_message, delete_message, get_updates, answer_callback_query, set_webhook, delete_webhook, get_webhook_info, send_poll, stop_poll, get_bot_info, search_bots, get_chat_member, set_user_state, get_user_state';
            break;
    }
}

if (!empty($error_code)) {
    $response_data = array(
        'api_status' => 400,
        'error_code' => $error_code,
        'error_message' => $error_message
    );
}

// ==================== HELPER FUNCTIONS ====================

function generateBotToken($bot_id) {
    return $bot_id . ':' . bin2hex(random_bytes(32));
}

function getBotByToken($token) {
    global $sqlConnect;
    $safe_token = mysqli_real_escape_string($sqlConnect, $token);
    $result = mysqli_query($sqlConnect, "SELECT * FROM Wo_Bots WHERE bot_token = '{$safe_token}' AND status = 'active' LIMIT 1");
    return mysqli_fetch_assoc($result);
}

function getBotByIdForOwner($sqlConnect, $bot_id, $owner_id) {
    $result = mysqli_query($sqlConnect, "SELECT * FROM Wo_Bots WHERE bot_id = '{$bot_id}' AND owner_id = '{$owner_id}' LIMIT 1");
    return mysqli_fetch_assoc($result);
}

function registerDefaultCommands($sqlConnect, $bot_id) {
    $defaults = array(
        array('start', 'Почати взаємодію з ботом', '/start'),
        array('help', 'Показати довідку по командах', '/help'),
        array('settings', 'Налаштування бота', '/settings')
    );
    foreach ($defaults as $idx => $cmd) {
        mysqli_query($sqlConnect, "INSERT INTO Wo_Bot_Commands (bot_id, command, description, usage_hint, sort_order)
            VALUES ('{$bot_id}', '{$cmd[0]}', '{$cmd[1]}', '{$cmd[2]}', {$idx})");
    }
}

function checkBotRateLimit($sqlConnect, $bot_id, $endpoint, $per_second, $per_minute) {
    $now = date('Y-m-d H:i:s');
    $second_window = date('Y-m-d H:i:s', strtotime('-1 second'));
    $minute_window = date('Y-m-d H:i:s', strtotime('-1 minute'));

    // Check per-second limit
    $result = mysqli_query($sqlConnect, "SELECT SUM(requests_count) as cnt FROM Wo_Bot_Rate_Limits
        WHERE bot_id = '{$bot_id}' AND endpoint = '{$endpoint}' AND window_type = 'second' AND window_start >= '{$second_window}'");
    $row = mysqli_fetch_assoc($result);
    if ($row && $row['cnt'] >= $per_second) return false;

    // Check per-minute limit
    $result = mysqli_query($sqlConnect, "SELECT SUM(requests_count) as cnt FROM Wo_Bot_Rate_Limits
        WHERE bot_id = '{$bot_id}' AND endpoint = '{$endpoint}' AND window_type = 'minute' AND window_start >= '{$minute_window}'");
    $row = mysqli_fetch_assoc($result);
    if ($row && $row['cnt'] >= $per_minute) return false;

    // Record this request
    $current_second = date('Y-m-d H:i:s');
    mysqli_query($sqlConnect, "INSERT INTO Wo_Bot_Rate_Limits (bot_id, endpoint, requests_count, window_start, window_type)
        VALUES ('{$bot_id}', '{$endpoint}', 1, '{$current_second}', 'second')
        ON DUPLICATE KEY UPDATE requests_count = requests_count + 1");

    $current_minute = date('Y-m-d H:i:00');
    mysqli_query($sqlConnect, "INSERT INTO Wo_Bot_Rate_Limits (bot_id, endpoint, requests_count, window_start, window_type)
        VALUES ('{$bot_id}', '{$endpoint}', 1, '{$current_minute}', 'minute')
        ON DUPLICATE KEY UPDATE requests_count = requests_count + 1");

    return true;
}

function checkUserBlockedBot($sqlConnect, $user_id, $bot_id) {
    $result = mysqli_query($sqlConnect, "SELECT is_blocked FROM Wo_Bot_Users
        WHERE bot_id = '{$bot_id}' AND user_id = '{$user_id}' AND is_blocked = 1 LIMIT 1");
    return mysqli_num_rows($result) > 0;
}

function updateBotUser($sqlConnect, $bot_id, $user_id) {
    $result = mysqli_query($sqlConnect, "SELECT id FROM Wo_Bot_Users WHERE bot_id = '{$bot_id}' AND user_id = '{$user_id}' LIMIT 1");
    if (mysqli_num_rows($result) == 0) {
        mysqli_query($sqlConnect, "INSERT INTO Wo_Bot_Users (bot_id, user_id, first_interaction_at, last_interaction_at, messages_count)
            VALUES ('{$bot_id}', '{$user_id}', NOW(), NOW(), 1)");
        // Update bot total users counter
        mysqli_query($sqlConnect, "UPDATE Wo_Bots SET total_users = total_users + 1 WHERE bot_id = '{$bot_id}'");
    } else {
        mysqli_query($sqlConnect, "UPDATE Wo_Bot_Users SET last_interaction_at = NOW(), messages_count = messages_count + 1
            WHERE bot_id = '{$bot_id}' AND user_id = '{$user_id}'");
    }
}

function sendBotMessageToUser($sqlConnect, $bot_data, $chat_id, $chat_type, $text, $media_type, $media_url, $reply_to) {
    // This bridges bot messages to the real messaging system (Wo_Messages)
    // Bot uses a special system user account or the bot's virtual user
    $bot_user_id = getBotVirtualUserId($sqlConnect, $bot_data['bot_id']);
    if (!$bot_user_id) return null;

    $safe_text = mysqli_real_escape_string($sqlConnect, $text ?: '');
    $time = time();
    $media_file = $media_url ? mysqli_real_escape_string($sqlConnect, $media_url) : '';
    $reply_val = $reply_to ? $reply_to : 0;

    if ($chat_type === 'group') {
        // Send as group message
        mysqli_query($sqlConnect, "INSERT INTO Wo_GroupChatMessages
            (from_id, group_id, text, media, time, reply_id)
            VALUES ('{$bot_user_id}', '{$chat_id}', '{$safe_text}', '{$media_file}', '{$time}', '{$reply_val}')");
    } else {
        // Send as private message
        $hash = md5($bot_user_id . $chat_id . $time . rand(1000, 9999));
        mysqli_query($sqlConnect, "INSERT INTO Wo_Messages
            (from_id, to_id, text, media, time, message_hash_id)
            VALUES ('{$bot_user_id}', '{$chat_id}', '{$safe_text}', '{$media_file}', '{$time}', '{$hash}')");
    }

    return mysqli_insert_id($sqlConnect);
}

function getBotVirtualUserId($sqlConnect, $bot_id) {
    // Each bot has a virtual user account for the messaging system
    // Check if bot has a linked user_id
    $result = mysqli_query($sqlConnect, "SELECT b.owner_id FROM Wo_Bots b WHERE b.bot_id = '{$bot_id}' LIMIT 1");
    $row = mysqli_fetch_assoc($result);
    return $row ? $row['owner_id'] : null;
}

function handleBotMediaUpload($file, $bot_id) {
    $allowed_types = array(
        'image/jpeg', 'image/png', 'image/gif', 'image/webp',
        'video/mp4', 'video/webm',
        'audio/mpeg', 'audio/ogg', 'audio/wav',
        'application/pdf', 'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    );

    if (!in_array($file['type'], $allowed_types)) {
        return null;
    }

    $max_size = 50 * 1024 * 1024; // 50MB for bots
    if ($file['size'] > $max_size) {
        return null;
    }

    $ext = pathinfo($file['name'], PATHINFO_EXTENSION);
    $filename = 'bot_' . bin2hex(random_bytes(16)) . '.' . $ext;
    $upload_dir = '../../../../upload/bot_files/';

    if (!is_dir($upload_dir)) {
        mkdir($upload_dir, 0755, true);
    }

    $target = $upload_dir . $filename;
    if (move_uploaded_file($file['tmp_name'], $target)) {
        return 'upload/bot_files/' . $filename;
    }

    return null;
}

function detectMediaType($mime_type) {
    if (strpos($mime_type, 'image') === 0) return 'image';
    if (strpos($mime_type, 'video') === 0) return 'video';
    if (strpos($mime_type, 'audio') === 0) return 'audio';
    return 'file';
}

function parseMessageEntities($text, $parse_mode) {
    $entities = array();

    if ($parse_mode === 'markdown' || $parse_mode === 'MarkdownV2') {
        // Bold: *text*
        preg_match_all('/\*([^*]+)\*/', $text, $bold_matches, PREG_OFFSET_CAPTURE);
        foreach ($bold_matches[0] as $match) {
            $entities[] = array('type' => 'bold', 'offset' => $match[1], 'length' => strlen($match[0]));
        }

        // Italic: _text_
        preg_match_all('/_([^_]+)_/', $text, $italic_matches, PREG_OFFSET_CAPTURE);
        foreach ($italic_matches[0] as $match) {
            $entities[] = array('type' => 'italic', 'offset' => $match[1], 'length' => strlen($match[0]));
        }

        // Code: `text`
        preg_match_all('/`([^`]+)`/', $text, $code_matches, PREG_OFFSET_CAPTURE);
        foreach ($code_matches[0] as $match) {
            $entities[] = array('type' => 'code', 'offset' => $match[1], 'length' => strlen($match[0]));
        }

        // Links: [text](url)
        preg_match_all('/\[([^\]]+)\]\(([^)]+)\)/', $text, $link_matches, PREG_OFFSET_CAPTURE);
        foreach ($link_matches[0] as $idx => $match) {
            $entities[] = array(
                'type' => 'text_link',
                'offset' => $match[1],
                'length' => strlen($match[0]),
                'url' => $link_matches[2][$idx][0]
            );
        }
    } elseif ($parse_mode === 'HTML') {
        // Bold: <b>text</b>
        preg_match_all('/<b>([^<]+)<\/b>/', $text, $bold_matches, PREG_OFFSET_CAPTURE);
        foreach ($bold_matches[0] as $match) {
            $entities[] = array('type' => 'bold', 'offset' => $match[1], 'length' => strlen($match[0]));
        }

        // Italic: <i>text</i>
        preg_match_all('/<i>([^<]+)<\/i>/', $text, $italic_matches, PREG_OFFSET_CAPTURE);
        foreach ($italic_matches[0] as $match) {
            $entities[] = array('type' => 'italic', 'offset' => $match[1], 'length' => strlen($match[0]));
        }

        // Code: <code>text</code>
        preg_match_all('/<code>([^<]+)<\/code>/', $text, $code_matches, PREG_OFFSET_CAPTURE);
        foreach ($code_matches[0] as $match) {
            $entities[] = array('type' => 'code', 'offset' => $match[1], 'length' => strlen($match[0]));
        }

        // Links: <a href="url">text</a>
        preg_match_all('/<a href="([^"]+)">([^<]+)<\/a>/', $text, $link_matches, PREG_OFFSET_CAPTURE);
        foreach ($link_matches[0] as $idx => $match) {
            $entities[] = array(
                'type' => 'text_link',
                'offset' => $match[1],
                'length' => strlen($match[0]),
                'url' => $link_matches[1][$idx][0]
            );
        }
    }

    return array('text' => $text, 'entities' => $entities);
}

function notifyViaBotSocket($bot_id, $chat_id, $text, $message_id, $reply_markup) {
    // Notify via Redis/Socket for real-time delivery
    // This will be handled by the Node.js bot socket handler
    $payload = json_encode(array(
        'event' => 'bot_message',
        'bot_id' => $bot_id,
        'chat_id' => $chat_id,
        'message_id' => $message_id,
        'text' => $text,
        'reply_markup' => $reply_markup ? json_decode($reply_markup) : null,
        'timestamp' => time()
    ));

    // Try to push to Redis for Socket.IO relay
    try {
        if (class_exists('Redis')) {
            $redis = new Redis();
            $redis->connect('127.0.0.1', 6379);
            $redis->publish('bot_messages', $payload);
            $redis->close();
        }
    } catch (Exception $e) {
        // Redis not available, Socket.IO will pick up on next poll
    }
}

// ==================== OUTPUT RESPONSE ====================
// Output JSON response (works both for direct access and index.php router)
echo json_encode($response_data);
