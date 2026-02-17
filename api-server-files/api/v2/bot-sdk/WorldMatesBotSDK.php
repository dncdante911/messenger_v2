<?php
/**
 * WorldMates Bot SDK v1.0
 *
 * PHP SDK for building bots on WorldMates Messenger platform.
 * Compatible with Telegram Bot API patterns for easy migration.
 *
 * Usage:
 *   $bot = new WorldMatesBot('your_bot_token');
 *   $bot->sendMessage($chatId, 'Hello!');
 *
 *   // With webhook
 *   $bot->onMessage(function($message) use ($bot) {
 *       $bot->sendMessage($message['chat']['id'], 'Got: ' . $message['text']);
 *   });
 *   $bot->handleWebhook();
 *
 *   // With long polling
 *   $bot->onCommand('start', function($message, $args) use ($bot) {
 *       $bot->sendMessage($message['chat']['id'], 'Welcome!');
 *   });
 *   $bot->startPolling();
 */

class WorldMatesBot {
    private $token;
    private $apiUrl;
    private $lastUpdateId = 0;
    private $commandHandlers = array();
    private $messageHandler = null;
    private $callbackHandler = null;
    private $stateHandlers = array();
    private $pollingTimeout = 5;
    private $isRunning = false;

    const API_BASE = 'https://worldmates.club/api/v2/endpoints/bot_api.php';

    /**
     * Create a new bot instance
     * @param string $token Bot API token (from bot_api.php?type=create_bot)
     * @param string|null $apiUrl Custom API URL (for self-hosted instances)
     */
    public function __construct($token, $apiUrl = null) {
        $this->token = $token;
        $this->apiUrl = $apiUrl ?: self::API_BASE;
    }

    // ==================== API CALLS ====================

    /**
     * Make an API call to the Bot API
     */
    public function apiCall($type, $params = array()) {
        $params['bot_token'] = $this->token;
        $params['type'] = $type;

        $ch = curl_init($this->apiUrl);
        curl_setopt_array($ch, array(
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => http_build_query($params),
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 60,
            CURLOPT_CONNECTTIMEOUT => 10,
            CURLOPT_SSL_VERIFYPEER => true
        ));

        $response = curl_exec($ch);
        $http_code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $error = curl_error($ch);
        curl_close($ch);

        if ($error) {
            return array('api_status' => 0, 'error_message' => 'Connection error: ' . $error);
        }

        return json_decode($response, true) ?: array('api_status' => 0, 'error_message' => 'Invalid JSON response');
    }

    /**
     * Send a text message
     */
    public function sendMessage($chatId, $text, $options = array()) {
        $params = array_merge(array(
            'chat_id' => $chatId,
            'text' => $text,
            'parse_mode' => 'markdown'
        ), $options);

        return $this->apiCall('send_message', $params);
    }

    /**
     * Send a message with inline keyboard
     */
    public function sendMessageWithKeyboard($chatId, $text, $keyboard, $options = array()) {
        $params = array_merge(array(
            'chat_id' => $chatId,
            'text' => $text,
            'reply_markup' => json_encode(array('inline_keyboard' => $keyboard)),
            'parse_mode' => 'markdown'
        ), $options);

        return $this->apiCall('send_message', $params);
    }

    /**
     * Send a message with reply keyboard
     */
    public function sendMessageWithReplyKeyboard($chatId, $text, $keyboard, $options = array()) {
        $params = array_merge(array(
            'chat_id' => $chatId,
            'text' => $text,
            'reply_markup' => json_encode(array(
                'keyboard' => $keyboard,
                'resize_keyboard' => true,
                'one_time_keyboard' => isset($options['one_time']) ? $options['one_time'] : false
            )),
            'parse_mode' => 'markdown'
        ), $options);

        return $this->apiCall('send_message', $params);
    }

    /**
     * Edit an existing message
     */
    public function editMessage($chatId, $messageId, $text, $options = array()) {
        $params = array_merge(array(
            'chat_id' => $chatId,
            'message_id' => $messageId,
            'text' => $text
        ), $options);

        return $this->apiCall('edit_message', $params);
    }

    /**
     * Delete a message
     */
    public function deleteMessage($chatId, $messageId) {
        return $this->apiCall('delete_message', array(
            'chat_id' => $chatId,
            'message_id' => $messageId
        ));
    }

    /**
     * Answer a callback query (inline button press)
     */
    public function answerCallbackQuery($callbackQueryId, $text = '', $showAlert = false) {
        return $this->apiCall('answer_callback_query', array(
            'callback_query_id' => $callbackQueryId,
            'text' => $text,
            'show_alert' => $showAlert ? 1 : 0
        ));
    }

    /**
     * Send a poll
     */
    public function sendPoll($chatId, $question, $options, $pollOptions = array()) {
        $params = array_merge(array(
            'chat_id' => $chatId,
            'question' => $question,
            'options' => json_encode($options)
        ), $pollOptions);

        return $this->apiCall('send_poll', $params);
    }

    /**
     * Stop (close) a poll
     */
    public function stopPoll($pollId) {
        return $this->apiCall('stop_poll', array('poll_id' => $pollId));
    }

    // ==================== COMMANDS ====================

    /**
     * Set bot commands list
     */
    public function setCommands($commands) {
        return $this->apiCall('set_commands', array(
            'commands' => json_encode($commands)
        ));
    }

    /**
     * Get bot commands list
     */
    public function getCommands() {
        return $this->apiCall('get_commands');
    }

    // ==================== WEBHOOKS ====================

    /**
     * Set webhook URL
     */
    public function setWebhook($url, $options = array()) {
        $params = array_merge(array('url' => $url), $options);
        return $this->apiCall('set_webhook', $params);
    }

    /**
     * Delete webhook
     */
    public function deleteWebhook() {
        return $this->apiCall('delete_webhook');
    }

    /**
     * Get webhook info
     */
    public function getWebhookInfo() {
        return $this->apiCall('get_webhook_info');
    }

    // ==================== USER STATE ====================

    /**
     * Set conversation state for a user (FSM pattern)
     */
    public function setUserState($userId, $state, $stateData = null) {
        $params = array('user_id' => $userId, 'state' => $state);
        if ($stateData !== null) {
            $params['state_data'] = json_encode($stateData);
        }
        return $this->apiCall('set_user_state', $params);
    }

    /**
     * Get user's current conversation state
     */
    public function getUserState($userId) {
        return $this->apiCall('get_user_state', array('user_id' => $userId));
    }

    /**
     * Clear user state
     */
    public function clearUserState($userId) {
        return $this->setUserState($userId, null, null);
    }

    // ==================== EVENT HANDLERS ====================

    /**
     * Register a command handler
     */
    public function onCommand($command, $handler) {
        $this->commandHandlers[strtolower($command)] = $handler;
    }

    /**
     * Register a handler for all text messages (non-commands)
     */
    public function onMessage($handler) {
        $this->messageHandler = $handler;
    }

    /**
     * Register a callback query handler (inline button clicks)
     */
    public function onCallbackQuery($handler) {
        $this->callbackHandler = $handler;
    }

    /**
     * Register a state handler for conversation flow
     */
    public function onState($state, $handler) {
        $this->stateHandlers[$state] = $handler;
    }

    // ==================== WEBHOOK HANDLING ====================

    /**
     * Process incoming webhook request
     */
    public function handleWebhook() {
        $input = file_get_contents('php://input');
        if (empty($input)) return;

        // Verify webhook signature
        $signature = isset($_SERVER['HTTP_X_WORLDMATES_BOT_SIGNATURE']) ? $_SERVER['HTTP_X_WORLDMATES_BOT_SIGNATURE'] : '';
        // Signature verification can be added here for security

        $update = json_decode($input, true);
        if (!$update) return;

        $this->processUpdate($update);
    }

    // ==================== LONG POLLING ====================

    /**
     * Start long polling loop
     */
    public function startPolling($timeout = 5) {
        $this->pollingTimeout = $timeout;
        $this->isRunning = true;

        echo "Bot started polling...\n";

        while ($this->isRunning) {
            $result = $this->apiCall('get_updates', array(
                'offset' => $this->lastUpdateId + 1,
                'limit' => 20,
                'timeout' => $this->pollingTimeout
            ));

            if ($result['api_status'] === 200 && !empty($result['updates'])) {
                foreach ($result['updates'] as $update) {
                    $this->lastUpdateId = max($this->lastUpdateId, $update['update_id']);
                    $this->processUpdate($update);
                }
            }

            // Small sleep to prevent hammering API
            usleep(100000); // 100ms
        }
    }

    /**
     * Stop polling loop
     */
    public function stopPolling() {
        $this->isRunning = false;
    }

    // ==================== UPDATE PROCESSING ====================

    /**
     * Process a single update (from webhook or polling)
     */
    private function processUpdate($update) {
        // Handle callback queries
        if (!empty($update['callback_query'])) {
            if ($this->callbackHandler) {
                call_user_func($this->callbackHandler, $update['callback_query']);
            }
            return;
        }

        // Handle messages
        if (!empty($update['message'])) {
            $message = $update['message'];

            // Check for command
            if (!empty($update['command'])) {
                $cmdName = strtolower($update['command']['name']);
                $cmdArgs = $update['command']['args'];

                if (isset($this->commandHandlers[$cmdName])) {
                    call_user_func($this->commandHandlers[$cmdName], $message, $cmdArgs);
                    return;
                }
            }

            // Check user state for conversation flow
            $userId = $message['from']['id'];
            $stateResult = $this->getUserState($userId);
            if ($stateResult['api_status'] === 200 && !empty($stateResult['state'])) {
                $state = $stateResult['state'];
                if (isset($this->stateHandlers[$state])) {
                    call_user_func($this->stateHandlers[$state], $message, $stateResult['state_data']);
                    return;
                }
            }

            // General message handler
            if ($this->messageHandler) {
                call_user_func($this->messageHandler, $message);
            }
        }
    }

    // ==================== UTILITIES ====================

    /**
     * Build inline keyboard from simple array
     * @param array $buttons Format: [['text' => 'Button', 'callback_data' => 'data'], ...]
     * @param int $columns Number of buttons per row
     */
    public static function buildInlineKeyboard($buttons, $columns = 2) {
        $keyboard = array();
        $row = array();
        foreach ($buttons as $button) {
            $row[] = $button;
            if (count($row) >= $columns) {
                $keyboard[] = $row;
                $row = array();
            }
        }
        if (!empty($row)) {
            $keyboard[] = $row;
        }
        return $keyboard;
    }

    /**
     * Build URL button
     */
    public static function urlButton($text, $url) {
        return array('text' => $text, 'url' => $url);
    }

    /**
     * Build callback button
     */
    public static function callbackButton($text, $callbackData) {
        return array('text' => $text, 'callback_data' => $callbackData);
    }
}
