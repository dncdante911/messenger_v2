<?php
// +------------------------------------------------------------------------+
// | WorldMates Bot API - Webhook Processor
// | Delivers bot events to registered webhook URLs
// | Should be called periodically via cron job or queue worker
// +------------------------------------------------------------------------+

$response_data = array('api_status' => 400);

$type = !empty($_POST['type']) ? Wo_Secure($_POST['type']) : '';

switch ($type) {

    case 'process_webhooks':
        // Process pending webhook deliveries
        // Should be called by cron: every 5-10 seconds
        $limit = !empty($_POST['limit']) ? min((int)$_POST['limit'], 100) : 50;

        // Get bots with enabled webhooks
        $bots_result = mysqli_query($sqlConnect, "SELECT bot_id, webhook_url, webhook_secret, webhook_max_connections, webhook_allowed_updates
            FROM Wo_Bots WHERE webhook_enabled = 1 AND webhook_url IS NOT NULL AND status = 'active'");

        $total_delivered = 0;
        $total_failed = 0;

        while ($bot = mysqli_fetch_assoc($bots_result)) {
            $bid = $bot['bot_id'];
            $allowed_updates = $bot['webhook_allowed_updates'] ? json_decode($bot['webhook_allowed_updates'], true) : null;

            // Get unprocessed incoming messages for this bot
            $messages = mysqli_query($sqlConnect, "
                SELECT m.id, m.chat_id, m.chat_type, m.text, m.media_type, m.media_url,
                       m.is_command, m.command_name, m.command_args, m.callback_data,
                       m.reply_to_message_id, m.created_at,
                       u.username, u.first_name, u.last_name, u.avatar
                FROM Wo_Bot_Messages m
                LEFT JOIN Wo_Users u ON m.chat_id = CAST(u.user_id AS CHAR)
                WHERE m.bot_id = '{$bid}' AND m.direction = 'incoming' AND m.processed = 0
                ORDER BY m.id ASC LIMIT {$limit}
            ");

            while ($msg = mysqli_fetch_assoc($messages)) {
                // Build webhook payload (Telegram-compatible format)
                $payload = buildWebhookPayload($msg, $bid);

                // Filter by allowed_updates if configured
                if ($allowed_updates && !empty($payload['update_type'])) {
                    if (!in_array($payload['update_type'], $allowed_updates)) {
                        // Mark as processed but skip delivery
                        mysqli_query($sqlConnect, "UPDATE Wo_Bot_Messages SET processed = 1, processed_at = NOW()
                            WHERE id = {$msg['id']}");
                        continue;
                    }
                }

                // Sign the payload
                $json_payload = json_encode($payload);
                $signature = hash_hmac('sha256', $json_payload, $bot['webhook_secret']);

                // Deliver webhook
                $delivery_result = deliverWebhook($bot['webhook_url'], $json_payload, $signature, $bid);

                // Log delivery
                $safe_payload = Wo_Secure($json_payload);
                $safe_url = Wo_Secure($bot['webhook_url']);
                $status = $delivery_result['success'] ? 'delivered' : 'failed';
                $resp_code = (int)$delivery_result['http_code'];
                $resp_body = Wo_Secure(substr($delivery_result['response'] ?? '', 0, 1000));

                mysqli_query($sqlConnect, "INSERT INTO Wo_Bot_Webhook_Log
                    (bot_id, event_type, payload, webhook_url, response_code, response_body, delivery_status, attempts, delivered_at, created_at)
                    VALUES ('{$bid}', '{$payload['update_type']}', '{$safe_payload}', '{$safe_url}',
                            {$resp_code}, '{$resp_body}', '{$status}', 1,
                            " . ($delivery_result['success'] ? 'NOW()' : 'NULL') . ", NOW())");

                // Mark message as processed
                mysqli_query($sqlConnect, "UPDATE Wo_Bot_Messages SET processed = 1, processed_at = NOW()
                    WHERE id = {$msg['id']}");

                if ($delivery_result['success']) {
                    $total_delivered++;
                } else {
                    $total_failed++;

                    // Schedule retry if not exceeded max attempts
                    $log_id = mysqli_insert_id($sqlConnect);
                    scheduleWebhookRetry($sqlConnect, $log_id);
                }
            }
        }

        $response_data = array(
            'api_status' => 200,
            'delivered' => $total_delivered,
            'failed' => $total_failed,
            'processed_at' => date('Y-m-d H:i:s')
        );
        break;

    case 'retry_failed':
        // Retry failed webhook deliveries
        $result = mysqli_query($sqlConnect, "SELECT wl.*, b.webhook_url, b.webhook_secret
            FROM Wo_Bot_Webhook_Log wl
            JOIN Wo_Bots b ON wl.bot_id = b.bot_id
            WHERE wl.delivery_status = 'retrying' AND wl.next_retry_at <= NOW() AND wl.attempts < wl.max_attempts
            ORDER BY wl.next_retry_at ASC LIMIT 50");

        $retried = 0;
        while ($entry = mysqli_fetch_assoc($result)) {
            $json_payload = $entry['payload'];
            $signature = hash_hmac('sha256', $json_payload, $entry['webhook_secret']);

            $delivery_result = deliverWebhook($entry['webhook_url'], $json_payload, $signature, $entry['bot_id']);

            $new_status = $delivery_result['success'] ? 'delivered' : 'retrying';
            $attempts = $entry['attempts'] + 1;
            if ($attempts >= $entry['max_attempts'] && !$delivery_result['success']) {
                $new_status = 'failed';
            }

            $resp_code = (int)$delivery_result['http_code'];
            $resp_body = Wo_Secure(substr($delivery_result['response'] ?? '', 0, 1000));

            $next_retry = 'NULL';
            if ($new_status === 'retrying') {
                // Exponential backoff: 10s, 30s, 90s, 270s, 810s
                $delay = pow(3, $attempts) * 10;
                $next_retry = "'" . date('Y-m-d H:i:s', time() + $delay) . "'";
            }

            mysqli_query($sqlConnect, "UPDATE Wo_Bot_Webhook_Log SET
                delivery_status = '{$new_status}', attempts = {$attempts},
                response_code = {$resp_code}, response_body = '{$resp_body}',
                next_retry_at = {$next_retry},
                delivered_at = " . ($delivery_result['success'] ? 'NOW()' : 'NULL') . "
                WHERE id = {$entry['id']}");

            $retried++;
        }

        $response_data = array('api_status' => 200, 'retried' => $retried);
        break;

    case 'cleanup':
        // Clean up old webhook logs and rate limit entries (run daily)
        $days_keep = !empty($_POST['days']) ? (int)$_POST['days'] : 7;

        // Delete old webhook logs
        $del_result = mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Webhook_Log
            WHERE created_at < DATE_SUB(NOW(), INTERVAL {$days_keep} DAY) AND delivery_status IN ('delivered', 'failed')");
        $deleted_webhooks = mysqli_affected_rows($sqlConnect);

        // Delete old rate limit entries
        mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Rate_Limits WHERE window_start < DATE_SUB(NOW(), INTERVAL 1 HOUR)");
        $deleted_rates = mysqli_affected_rows($sqlConnect);

        // Delete old processed bot messages (keep last 30 days)
        mysqli_query($sqlConnect, "DELETE FROM Wo_Bot_Messages
            WHERE processed = 1 AND created_at < DATE_SUB(NOW(), INTERVAL 30 DAY)");
        $deleted_messages = mysqli_affected_rows($sqlConnect);

        // Update active_users_24h for all bots
        mysqli_query($sqlConnect, "UPDATE Wo_Bots b SET active_users_24h = (
            SELECT COUNT(DISTINCT user_id) FROM Wo_Bot_Users bu
            WHERE bu.bot_id = b.bot_id AND bu.last_interaction_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
        )");

        $response_data = array(
            'api_status' => 200,
            'deleted_webhook_logs' => $deleted_webhooks,
            'deleted_rate_limits' => $deleted_rates,
            'deleted_old_messages' => $deleted_messages
        );
        break;

    default:
        $error_code = 1;
        $error_message = 'Unknown type. Available: process_webhooks, retry_failed, cleanup';
        break;
}

if (!empty($error_code)) {
    $response_data = array('api_status' => 400, 'error_code' => $error_code, 'error_message' => $error_message);
}

// ==================== HELPER FUNCTIONS ====================

function buildWebhookPayload($message, $bot_id) {
    $update_type = 'message';
    if ($message['is_command']) $update_type = 'command';
    if ($message['callback_data']) $update_type = 'callback_query';

    $payload = array(
        'update_id' => (int)$message['id'],
        'update_type' => $update_type,
        'bot_id' => $bot_id,
        'message' => array(
            'message_id' => (int)$message['id'],
            'from' => array(
                'id' => (int)$message['chat_id'],
                'username' => $message['username'],
                'first_name' => $message['first_name'],
                'last_name' => $message['last_name'],
                'avatar' => $message['avatar']
            ),
            'chat' => array(
                'id' => $message['chat_id'],
                'type' => $message['chat_type']
            ),
            'date' => strtotime($message['created_at']),
            'text' => $message['text']
        )
    );

    // Add command details
    if ($message['is_command']) {
        $payload['command'] = array(
            'name' => $message['command_name'],
            'args' => $message['command_args']
        );
    }

    // Add callback query
    if ($message['callback_data']) {
        $payload['callback_query'] = array(
            'id' => (string)$message['id'],
            'from' => $payload['message']['from'],
            'data' => $message['callback_data']
        );
    }

    // Add media
    if ($message['media_type']) {
        $payload['message']['media'] = array(
            'type' => $message['media_type'],
            'url' => $message['media_url']
        );
    }

    return $payload;
}

function deliverWebhook($url, $json_payload, $signature, $bot_id) {
    $ch = curl_init($url);
    curl_setopt_array($ch, array(
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => $json_payload,
        CURLOPT_HTTPHEADER => array(
            'Content-Type: application/json',
            'X-WorldMates-Bot-Signature: sha256=' . $signature,
            'X-WorldMates-Bot-Id: ' . $bot_id,
            'User-Agent: WorldMatesBot/1.0'
        ),
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT => 10,
        CURLOPT_CONNECTTIMEOUT => 5,
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_FOLLOWLOCATION => false
    ));

    $response = curl_exec($ch);
    $http_code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $error = curl_error($ch);
    curl_close($ch);

    return array(
        'success' => $http_code >= 200 && $http_code < 300,
        'http_code' => $http_code,
        'response' => $response ?: $error
    );
}

function scheduleWebhookRetry($sqlConnect, $log_id) {
    $result = mysqli_query($sqlConnect, "SELECT attempts FROM Wo_Bot_Webhook_Log WHERE id = {$log_id}");
    $entry = mysqli_fetch_assoc($result);
    if (!$entry) return;

    $delay = pow(3, $entry['attempts']) * 10; // 10s, 30s, 90s, 270s, 810s
    $next_retry = date('Y-m-d H:i:s', time() + $delay);

    mysqli_query($sqlConnect, "UPDATE Wo_Bot_Webhook_Log SET delivery_status = 'retrying', next_retry_at = '{$next_retry}' WHERE id = {$log_id}");
}
