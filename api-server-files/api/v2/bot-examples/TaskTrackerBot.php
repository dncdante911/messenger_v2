<?php
/**
 * WorldMates Task Tracker Bot
 *
 * Task/todo management bot for personal and team use.
 * Supports task creation, assignment, priorities, due dates, and reminders.
 *
 * Commands:
 *   /start     - Welcome and quick start
 *   /add       - Add a new task
 *   /list      - List tasks (with filters)
 *   /done      - Mark task as completed
 *   /edit      - Edit a task
 *   /delete    - Delete a task
 *   /assign    - Assign task to someone (groups)
 *   /priority  - Set task priority
 *   /due       - Set due date
 *   /remind    - Set a reminder
 *   /stats     - Task statistics
 *   /help      - Commands reference
 */

require_once __DIR__ . '/../bot-sdk/WorldMatesBotSDK.php';

class TaskTrackerBot {
    private $bot;
    private $db;
    private $botId;

    const PRIORITY_EMOJI = array(
        'low' => "\xF0\x9F\x9F\xA2",         // green circle
        'medium' => "\xF0\x9F\x9F\xA1",       // yellow circle
        'high' => "\xF0\x9F\x9F\xA0",         // orange circle
        'urgent' => "\xF0\x9F\x94\xB4"        // red circle
    );

    const STATUS_EMOJI = array(
        'todo' => "\xE2\xAC\x9C",             // white square
        'in_progress' => "\xF0\x9F\x94\xB5",  // blue circle
        'done' => "\xE2\x9C\x85",             // checkmark
        'cancelled' => "\xE2\x9D\x8C"         // X
    );

    public function __construct($botToken, $dbConnection, $botId) {
        $this->bot = new WorldMatesBot($botToken);
        $this->db = $dbConnection;
        $this->botId = $botId;
        $this->registerHandlers();
    }

    private function registerHandlers() {
        $self = $this;

        $this->bot->onCommand('start', function($msg) use ($self) { $self->handleStart($msg); });
        $this->bot->onCommand('help', function($msg) use ($self) { $self->handleHelp($msg); });
        $this->bot->onCommand('add', function($msg, $a) use ($self) { $self->handleAdd($msg, $a); });
        $this->bot->onCommand('list', function($msg, $a) use ($self) { $self->handleList($msg, $a); });
        $this->bot->onCommand('done', function($msg, $a) use ($self) { $self->handleDone($msg, $a); });
        $this->bot->onCommand('delete', function($msg, $a) use ($self) { $self->handleDelete($msg, $a); });
        $this->bot->onCommand('edit', function($msg, $a) use ($self) { $self->handleEdit($msg, $a); });
        $this->bot->onCommand('priority', function($msg, $a) use ($self) { $self->handlePriority($msg, $a); });
        $this->bot->onCommand('due', function($msg, $a) use ($self) { $self->handleDue($msg, $a); });
        $this->bot->onCommand('remind', function($msg, $a) use ($self) { $self->handleRemind($msg, $a); });
        $this->bot->onCommand('stats', function($msg) use ($self) { $self->handleStats($msg); });

        $this->bot->onCallbackQuery(function($cb) use ($self) { $self->handleCallback($cb); });

        $this->bot->onState('adding_task', function($msg) use ($self) { $self->processAddTask($msg); });
        $this->bot->onState('editing_task', function($msg, $data) use ($self) { $self->processEditTask($msg, $data); });

        $this->bot->onMessage(function($msg) use ($self) { $self->handleMessage($msg); });
    }

    public function handleStart($message) {
        $chatId = $message['chat']['id'];
        $name = $message['from']['first_name'] ?? 'User';

        $text = "*Task Tracker Bot*\n\n";
        $text .= "Привіт, {$name}! Я допоможу відстежувати завдання.\n\n";
        $text .= "Швидкий старт:\n";
        $text .= "- /add Купити молоко - швидке додавання\n";
        $text .= "- /list - всі завдання\n";
        $text .= "- /done 1 - завершити завдання #1\n\n";
        $text .= "Або натисніть кнопку нижче:";

        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('+ Додати завдання', 'quick_add'),
            WorldMatesBot::callbackButton('Мої завдання', 'list_all'),
            WorldMatesBot::callbackButton('Статистика', 'stats'),
            WorldMatesBot::callbackButton('Допомога', 'help')
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function handleHelp($message) {
        $chatId = $message['chat']['id'];
        $text = "*Команди Task Tracker:*\n\n";
        $text .= "*Основні:*\n";
        $text .= "/add `<назва>` - Додати завдання\n";
        $text .= "/list - Всі завдання\n";
        $text .= "/list `todo` - Тільки невиконані\n";
        $text .= "/list `done` - Виконані\n";
        $text .= "/done `<id>` - Завершити завдання\n";
        $text .= "/delete `<id>` - Видалити\n\n";
        $text .= "*Деталі:*\n";
        $text .= "/edit `<id> <новий текст>` - Редагувати\n";
        $text .= "/priority `<id> <low|medium|high|urgent>` - Пріоритет\n";
        $text .= "/due `<id> <дата>` - Дедлайн (формат: 2026-02-15)\n";
        $text .= "/remind `<id> <дата>` - Нагадування\n";
        $text .= "/stats - Статистика\n";

        $this->bot->sendMessage($chatId, $text);
    }

    public function handleAdd($message, $args) {
        $chatId = $message['chat']['id'];

        if (empty($args)) {
            $this->bot->setUserState($chatId, 'adding_task');
            $this->bot->sendMessage($chatId, "Напишіть назву завдання:");
            return;
        }

        // Parse inline priority: /add !high Finish report
        $priority = 'medium';
        $title = $args;
        if (preg_match('/^!(low|medium|high|urgent)\s+(.+)$/i', $args, $matches)) {
            $priority = strtolower($matches[1]);
            $title = $matches[2];
        }

        $this->createTask($chatId, $title, $priority);
    }

    public function processAddTask($message) {
        $chatId = $message['chat']['id'];
        $title = $message['text'];

        $this->bot->clearUserState($chatId);
        $this->createTask($chatId, $title);
    }

    private function createTask($chatId, $title, $priority = 'medium', $dueDate = null) {
        $safe_title = mysqli_real_escape_string($this->db, $title);
        $due_val = $dueDate ? "'{$dueDate}'" : 'NULL';

        mysqli_query($this->db, "INSERT INTO Wo_Bot_Tasks
            (bot_id, user_id, chat_id, title, status, priority, due_date, created_at)
            VALUES ('{$this->botId}', '{$chatId}', '{$chatId}', '{$safe_title}', 'todo', '{$priority}', {$due_val}, NOW())");

        $taskId = mysqli_insert_id($this->db);
        $emoji = self::PRIORITY_EMOJI[$priority];

        $text = "{$emoji} Завдання #{$taskId} створено!\n*{$title}*\nПріоритет: {$priority}";
        if ($dueDate) $text .= "\nДедлайн: {$dueDate}";

        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('Done', 'task_done_' . $taskId),
            WorldMatesBot::callbackButton('Пріоритет', 'task_priority_' . $taskId),
            WorldMatesBot::callbackButton('Дедлайн', 'task_due_' . $taskId),
            WorldMatesBot::callbackButton('Видалити', 'task_delete_' . $taskId)
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function handleList($message, $args = null) {
        $chatId = $message['chat']['id'];
        $filter = trim($args ?? '');

        $where = "bot_id = '{$this->botId}' AND user_id = '{$chatId}'";
        $title = "Всі завдання";

        if ($filter === 'todo' || $filter === 'active') {
            $where .= " AND status IN ('todo', 'in_progress')";
            $title = "Активні завдання";
        } elseif ($filter === 'done') {
            $where .= " AND status = 'done'";
            $title = "Виконані завдання";
        } elseif ($filter === 'urgent' || $filter === 'high') {
            $where .= " AND priority IN ('urgent', 'high') AND status != 'done'";
            $title = "Важливі завдання";
        }

        $result = mysqli_query($this->db, "SELECT * FROM Wo_Bot_Tasks
            WHERE {$where} ORDER BY
                CASE status WHEN 'in_progress' THEN 0 WHEN 'todo' THEN 1 WHEN 'done' THEN 2 ELSE 3 END,
                CASE priority WHEN 'urgent' THEN 0 WHEN 'high' THEN 1 WHEN 'medium' THEN 2 ELSE 3 END,
                due_date ASC, created_at DESC
            LIMIT 30");

        $tasks = array();
        while ($row = mysqli_fetch_assoc($result)) {
            $tasks[] = $row;
        }

        if (empty($tasks)) {
            $text = "*{$title}:*\n\nСписок порожній. Додайте завдання: /add";
            $this->bot->sendMessage($chatId, $text);
            return;
        }

        $text = "*{$title} (" . count($tasks) . "):*\n\n";
        foreach ($tasks as $task) {
            $status_emoji = self::STATUS_EMOJI[$task['status']];
            $priority_emoji = self::PRIORITY_EMOJI[$task['priority']];
            $due = $task['due_date'] ? ' | ' . date('d.m', strtotime($task['due_date'])) : '';
            $strikethrough = $task['status'] === 'done' ? '~' : '';
            $text .= "{$status_emoji} {$priority_emoji} #{$task['id']} {$strikethrough}{$task['title']}{$strikethrough}{$due}\n";
        }

        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('Активні', 'list_todo'),
            WorldMatesBot::callbackButton('Виконані', 'list_done'),
            WorldMatesBot::callbackButton('Важливі', 'list_urgent'),
            WorldMatesBot::callbackButton('+ Додати', 'quick_add')
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function handleDone($message, $args) {
        $chatId = $message['chat']['id'];
        $taskId = (int)trim($args);

        if ($taskId <= 0) {
            // Show list to pick from
            $this->bot->sendMessage($chatId, "Вкажіть номер завдання: /done 1\nАбо перегляньте список: /list");
            return;
        }

        $result = mysqli_query($this->db, "SELECT * FROM Wo_Bot_Tasks
            WHERE id = {$taskId} AND bot_id = '{$this->botId}' AND user_id = '{$chatId}' LIMIT 1");
        $task = mysqli_fetch_assoc($result);

        if (!$task) {
            $this->bot->sendMessage($chatId, "Завдання #{$taskId} не знайдено.");
            return;
        }

        mysqli_query($this->db, "UPDATE Wo_Bot_Tasks SET status = 'done', completed_at = NOW()
            WHERE id = {$taskId}");

        $text = self::STATUS_EMOJI['done'] . " Завдання #{$taskId} виконано!\n~{$task['title']}~";
        $this->bot->sendMessage($chatId, $text);
    }

    public function handleDelete($message, $args) {
        $chatId = $message['chat']['id'];
        $taskId = (int)trim($args);

        if ($taskId <= 0) {
            $this->bot->sendMessage($chatId, "Вкажіть номер: /delete 1");
            return;
        }

        $result = mysqli_query($this->db, "DELETE FROM Wo_Bot_Tasks
            WHERE id = {$taskId} AND bot_id = '{$this->botId}' AND user_id = '{$chatId}'");

        if (mysqli_affected_rows($this->db) > 0) {
            $this->bot->sendMessage($chatId, "Завдання #{$taskId} видалено.");
        } else {
            $this->bot->sendMessage($chatId, "Завдання #{$taskId} не знайдено.");
        }
    }

    public function handleEdit($message, $args) {
        $chatId = $message['chat']['id'];

        if (preg_match('/^(\d+)\s+(.+)$/', trim($args), $matches)) {
            $taskId = (int)$matches[1];
            $newTitle = $matches[2];

            $safe_title = mysqli_real_escape_string($this->db, $newTitle);
            mysqli_query($this->db, "UPDATE Wo_Bot_Tasks SET title = '{$safe_title}'
                WHERE id = {$taskId} AND bot_id = '{$this->botId}' AND user_id = '{$chatId}'");

            if (mysqli_affected_rows($this->db) > 0) {
                $this->bot->sendMessage($chatId, "Завдання #{$taskId} оновлено: *{$newTitle}*");
            } else {
                $this->bot->sendMessage($chatId, "Завдання #{$taskId} не знайдено.");
            }
        } else {
            $this->bot->sendMessage($chatId, "Формат: /edit `<id> <новий текст>`\nПриклад: /edit 1 Купити хліб і молоко");
        }
    }

    public function handlePriority($message, $args) {
        $chatId = $message['chat']['id'];

        if (preg_match('/^(\d+)\s+(low|medium|high|urgent)$/i', trim($args), $matches)) {
            $taskId = (int)$matches[1];
            $priority = strtolower($matches[2]);

            mysqli_query($this->db, "UPDATE Wo_Bot_Tasks SET priority = '{$priority}'
                WHERE id = {$taskId} AND bot_id = '{$this->botId}' AND user_id = '{$chatId}'");

            $emoji = self::PRIORITY_EMOJI[$priority];
            $this->bot->sendMessage($chatId, "{$emoji} Пріоритет #{$taskId} змінено на *{$priority}*");
        } else {
            $this->bot->sendMessage($chatId, "Формат: /priority `<id> <low|medium|high|urgent>`");
        }
    }

    public function handleDue($message, $args) {
        $chatId = $message['chat']['id'];

        if (preg_match('/^(\d+)\s+(\d{4}-\d{2}-\d{2})$/', trim($args), $matches)) {
            $taskId = (int)$matches[1];
            $dueDate = $matches[2];

            mysqli_query($this->db, "UPDATE Wo_Bot_Tasks SET due_date = '{$dueDate}'
                WHERE id = {$taskId} AND bot_id = '{$this->botId}' AND user_id = '{$chatId}'");

            $this->bot->sendMessage($chatId, "Дедлайн #{$taskId}: *{$dueDate}*");
        } else {
            $this->bot->sendMessage($chatId, "Формат: /due `<id> <YYYY-MM-DD>`\nПриклад: /due 1 2026-02-20");
        }
    }

    public function handleRemind($message, $args) {
        $chatId = $message['chat']['id'];

        if (preg_match('/^(\d+)\s+(.+)$/', trim($args), $matches)) {
            $taskId = (int)$matches[1];
            $reminder = $matches[2];

            // Parse relative time: "1h", "30m", "2d", or absolute "2026-02-15 10:00"
            $reminderTime = $this->parseReminderTime($reminder);
            if (!$reminderTime) {
                $this->bot->sendMessage($chatId, "Невірний формат часу. Приклади: `30m`, `1h`, `2d`, `2026-02-15 10:00`");
                return;
            }

            $safe_time = date('Y-m-d H:i:s', $reminderTime);
            mysqli_query($this->db, "UPDATE Wo_Bot_Tasks SET reminder_at = '{$safe_time}'
                WHERE id = {$taskId} AND bot_id = '{$this->botId}' AND user_id = '{$chatId}'");

            $this->bot->sendMessage($chatId, "Нагадування #{$taskId}: *" . date('d.m.Y H:i', $reminderTime) . "*");
        } else {
            $this->bot->sendMessage($chatId, "Формат: /remind `<id> <час>`\nПриклади: /remind 1 30m | /remind 1 2h | /remind 1 2026-02-15 10:00");
        }
    }

    public function handleStats($message) {
        $chatId = $message['chat']['id'];

        // Get task statistics
        $result = mysqli_query($this->db, "SELECT
            COUNT(*) as total,
            SUM(status = 'todo') as todo,
            SUM(status = 'in_progress') as in_progress,
            SUM(status = 'done') as done,
            SUM(status = 'cancelled') as cancelled,
            SUM(priority = 'urgent' AND status != 'done') as urgent,
            SUM(priority = 'high' AND status != 'done') as high_priority,
            SUM(due_date IS NOT NULL AND due_date < NOW() AND status NOT IN ('done','cancelled')) as overdue
            FROM Wo_Bot_Tasks
            WHERE bot_id = '{$this->botId}' AND user_id = '{$chatId}'");

        $stats = mysqli_fetch_assoc($result);

        // Get completion rate for last 7 days
        $weekly = mysqli_query($this->db, "SELECT COUNT(*) as cnt FROM Wo_Bot_Tasks
            WHERE bot_id = '{$this->botId}' AND user_id = '{$chatId}'
            AND status = 'done' AND completed_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)");
        $weeklyDone = mysqli_fetch_assoc($weekly)['cnt'];

        $total = (int)$stats['total'];
        $done = (int)$stats['done'];
        $rate = $total > 0 ? round(($done / $total) * 100) : 0;

        $text = "*Статистика завдань:*\n\n";
        $text .= "Всього: *{$total}*\n";
        $text .= self::STATUS_EMOJI['todo'] . " Очікують: *{$stats['todo']}*\n";
        $text .= self::STATUS_EMOJI['in_progress'] . " В роботі: *{$stats['in_progress']}*\n";
        $text .= self::STATUS_EMOJI['done'] . " Виконано: *{$done}*\n";
        $text .= self::STATUS_EMOJI['cancelled'] . " Скасовано: *{$stats['cancelled']}*\n\n";
        $text .= self::PRIORITY_EMOJI['urgent'] . " Термінових: *{$stats['urgent']}*\n";
        $text .= self::PRIORITY_EMOJI['high'] . " Важливих: *{$stats['high_priority']}*\n";

        if ((int)$stats['overdue'] > 0) {
            $text .= "\n*Прострочено: {$stats['overdue']}*\n";
        }

        $text .= "\nЗа тиждень виконано: *{$weeklyDone}*\n";
        $text .= "Загальний прогрес: *{$rate}%*\n";

        // Simple progress bar
        $filled = (int)($rate / 10);
        $bar = str_repeat("\xE2\x96\x93", $filled) . str_repeat("\xE2\x96\x91", 10 - $filled);
        $text .= "[{$bar}]";

        $this->bot->sendMessage($chatId, $text);
    }

    public function handleCallback($callback) {
        $chatId = $callback['from']['id'];
        $data = $callback['data'];

        if (strpos($data, 'task_done_') === 0) {
            $taskId = (int)substr($data, 10);
            $this->handleDone(array('chat' => array('id' => $chatId), 'from' => $callback['from']), (string)$taskId);
            $this->bot->answerCallbackQuery($callback['id'], 'Виконано!');

        } elseif (strpos($data, 'task_delete_') === 0) {
            $taskId = (int)substr($data, 12);
            $this->handleDelete(array('chat' => array('id' => $chatId), 'from' => $callback['from']), (string)$taskId);
            $this->bot->answerCallbackQuery($callback['id'], 'Видалено');

        } elseif (strpos($data, 'task_priority_') === 0) {
            $taskId = (int)substr($data, 14);
            $keyboard = WorldMatesBot::buildInlineKeyboard(array(
                WorldMatesBot::callbackButton('Low', 'set_priority_' . $taskId . '_low'),
                WorldMatesBot::callbackButton('Medium', 'set_priority_' . $taskId . '_medium'),
                WorldMatesBot::callbackButton('High', 'set_priority_' . $taskId . '_high'),
                WorldMatesBot::callbackButton('Urgent', 'set_priority_' . $taskId . '_urgent')
            ), 4);
            $this->bot->sendMessageWithKeyboard($chatId, "Оберіть пріоритет для #{$taskId}:", $keyboard);
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif (strpos($data, 'set_priority_') === 0) {
            preg_match('/set_priority_(\d+)_(\w+)/', $data, $m);
            $this->handlePriority(array('chat' => array('id' => $chatId), 'from' => $callback['from']), "{$m[1]} {$m[2]}");
            $this->bot->answerCallbackQuery($callback['id'], 'Оновлено!');

        } elseif ($data === 'quick_add') {
            $this->bot->setUserState($chatId, 'adding_task');
            $this->bot->sendMessage($chatId, "Напишіть назву завдання:");
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'list_all') {
            $this->handleList(array('chat' => array('id' => $chatId), 'from' => $callback['from']));
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif (strpos($data, 'list_') === 0) {
            $filter = substr($data, 5);
            $this->handleList(array('chat' => array('id' => $chatId), 'from' => $callback['from']), $filter);
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'stats') {
            $this->handleStats(array('chat' => array('id' => $chatId), 'from' => $callback['from']));
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'help') {
            $this->handleHelp(array('chat' => array('id' => $chatId), 'from' => $callback['from']));
            $this->bot->answerCallbackQuery($callback['id']);
        }
    }

    public function handleMessage($message) {
        $chatId = $message['chat']['id'];
        $text = trim($message['text'] ?? '');

        // Quick add: any text is treated as a new task
        if (!empty($text) && strlen($text) > 2 && strlen($text) < 500) {
            $this->createTask($chatId, $text);
            return;
        }

        $this->bot->sendMessage($chatId, "Напишіть назву завдання для швидкого додавання, або /help для команд.");
    }

    // ==================== REMINDER SYSTEM ====================

    /**
     * Check and send due reminders (called by cron)
     */
    public function processReminders() {
        $result = mysqli_query($this->db, "SELECT * FROM Wo_Bot_Tasks
            WHERE bot_id = '{$this->botId}' AND reminder_at IS NOT NULL
            AND reminder_at <= NOW() AND status NOT IN ('done', 'cancelled')
            LIMIT 50");

        $sent = 0;
        while ($task = mysqli_fetch_assoc($result)) {
            $emoji = self::PRIORITY_EMOJI[$task['priority']];
            $text = "*Нагадування!*\n\n{$emoji} #{$task['id']}: {$task['title']}";
            if ($task['due_date']) {
                $text .= "\nДедлайн: " . date('d.m.Y', strtotime($task['due_date']));
            }

            $keyboard = WorldMatesBot::buildInlineKeyboard(array(
                WorldMatesBot::callbackButton('Done', 'task_done_' . $task['id']),
                WorldMatesBot::callbackButton('+1h', 'snooze_' . $task['id'] . '_1h'),
                WorldMatesBot::callbackButton('+1d', 'snooze_' . $task['id'] . '_1d')
            ), 3);

            $this->bot->sendMessageWithKeyboard($task['chat_id'], $text, $keyboard);

            // Clear reminder
            mysqli_query($this->db, "UPDATE Wo_Bot_Tasks SET reminder_at = NULL WHERE id = {$task['id']}");
            $sent++;
        }

        // Also check overdue tasks
        $overdue = mysqli_query($this->db, "SELECT * FROM Wo_Bot_Tasks
            WHERE bot_id = '{$this->botId}' AND due_date IS NOT NULL
            AND due_date < CURDATE() AND status NOT IN ('done', 'cancelled')
            AND (reminder_at IS NULL OR reminder_at < DATE_SUB(NOW(), INTERVAL 24 HOUR))
            LIMIT 20");

        while ($task = mysqli_fetch_assoc($overdue)) {
            $text = "*Прострочено!*\n#{$task['id']}: {$task['title']}\nДедлайн був: " . date('d.m.Y', strtotime($task['due_date']));
            $this->bot->sendMessage($task['chat_id'], $text);
        }

        return $sent;
    }

    private function parseReminderTime($input) {
        $input = trim($input);

        // Relative time: 30m, 1h, 2d
        if (preg_match('/^(\d+)(m|h|d)$/i', $input, $matches)) {
            $amount = (int)$matches[1];
            $unit = strtolower($matches[2]);
            $seconds = array('m' => 60, 'h' => 3600, 'd' => 86400);
            return time() + ($amount * $seconds[$unit]);
        }

        // Absolute time: 2026-02-15 or 2026-02-15 10:00
        $ts = strtotime($input);
        return ($ts && $ts > time()) ? $ts : null;
    }

    public function start() { $this->bot->startPolling(5); }
    public function handleWebhook() { $this->bot->handleWebhook(); }
}
