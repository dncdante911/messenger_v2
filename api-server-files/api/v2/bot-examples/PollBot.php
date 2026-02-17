<?php
/**
 * WorldMates Poll/Voting Bot
 *
 * Create polls, quizzes, and voting in chats and groups.
 * Supports anonymous/public voting, multiple answers, auto-close, results display.
 *
 * Commands:
 *   /start      - Welcome
 *   /poll       - Create a new poll
 *   /quiz       - Create a quiz
 *   /results    - Show poll results
 *   /close      - Close a poll
 *   /mypolls    - List my polls
 *   /help       - Commands reference
 */

require_once __DIR__ . '/../bot-sdk/WorldMatesBotSDK.php';

class PollBot {
    private $bot;
    private $db;
    private $botId;

    const BAR_FILLED = "\xE2\x96\x93";
    const BAR_EMPTY = "\xE2\x96\x91";

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
        $this->bot->onCommand('poll', function($msg, $a) use ($self) { $self->handlePoll($msg, $a); });
        $this->bot->onCommand('quiz', function($msg, $a) use ($self) { $self->handleQuiz($msg, $a); });
        $this->bot->onCommand('results', function($msg, $a) use ($self) { $self->handleResults($msg, $a); });
        $this->bot->onCommand('close', function($msg, $a) use ($self) { $self->handleClose($msg, $a); });
        $this->bot->onCommand('mypolls', function($msg) use ($self) { $self->handleMyPolls($msg); });

        $this->bot->onCallbackQuery(function($cb) use ($self) { $self->handleCallback($cb); });

        // Conversation states for poll creation wizard
        $this->bot->onState('poll_question', function($msg) use ($self) { $self->processQuestion($msg); });
        $this->bot->onState('poll_options', function($msg, $data) use ($self) { $self->processOptions($msg, $data); });
        $this->bot->onState('poll_settings', function($msg, $data) use ($self) { $self->processSettings($msg, $data); });
        $this->bot->onState('quiz_answer', function($msg, $data) use ($self) { $self->processQuizAnswer($msg, $data); });

        $this->bot->onMessage(function($msg) use ($self) { $self->handleMessage($msg); });
    }

    public function handleStart($message) {
        $chatId = $message['chat']['id'];

        $text = "*Poll & Voting Bot*\n\n";
        $text .= "Створюйте опитування та голосування!\n\n";
        $text .= "Можливості:\n";
        $text .= "- Звичайні опитування (декілька варіантів)\n";
        $text .= "- Квізи з правильною відповіддю\n";
        $text .= "- Анонімне/публічне голосування\n";
        $text .= "- Автоматичне закриття\n";
        $text .= "- Візуальні результати\n\n";
        $text .= "Швидкий старт:";

        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('Створити опитування', 'new_poll'),
            WorldMatesBot::callbackButton('Створити квіз', 'new_quiz'),
            WorldMatesBot::callbackButton('Мої опитування', 'my_polls'),
            WorldMatesBot::callbackButton('Допомога', 'help')
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function handleHelp($message) {
        $chatId = $message['chat']['id'];
        $text = "*Команди Poll Bot:*\n\n";
        $text .= "*Створення:*\n";
        $text .= "/poll - Створити опитування (покроково)\n";
        $text .= "/poll `Питання? | Варіант1 | Варіант2 | ...` - Швидке створення\n";
        $text .= "/quiz - Створити квіз\n\n";
        $text .= "*Управління:*\n";
        $text .= "/results `<id>` - Результати опитування\n";
        $text .= "/close `<id>` - Закрити опитування\n";
        $text .= "/mypolls - Мої опитування\n";

        $this->bot->sendMessage($chatId, $text);
    }

    public function handlePoll($message, $args) {
        $chatId = $message['chat']['id'];

        if (!empty($args) && strpos($args, '|') !== false) {
            // Quick create: /poll Question? | Option1 | Option2 | Option3
            $parts = array_map('trim', explode('|', $args));
            if (count($parts) >= 3) {
                $question = array_shift($parts);
                $this->createPoll($chatId, $question, $parts, 'regular');
                return;
            }
        }

        // Start wizard
        $this->bot->setUserState($chatId, 'poll_question', array('type' => 'regular'));
        $this->bot->sendMessage($chatId, "*Створення опитування*\n\nКрок 1/3: Напишіть питання:");
    }

    public function handleQuiz($message, $args) {
        $chatId = $message['chat']['id'];

        $this->bot->setUserState($chatId, 'poll_question', array('type' => 'quiz'));
        $this->bot->sendMessage($chatId, "*Створення квізу*\n\nКрок 1/4: Напишіть питання:");
    }

    public function processQuestion($message) {
        $chatId = $message['chat']['id'];
        $question = $message['text'];

        $stateResult = $this->bot->getUserState($chatId);
        $data = $stateResult['state_data'] ?? array();
        $data['question'] = $question;
        $data['options'] = array();

        $this->bot->setUserState($chatId, 'poll_options', $data);
        $this->bot->sendMessage($chatId, "Крок 2: Надішліть варіанти відповідей *по одному*.\nКоли закінчите, надішліть /done.\n\n(Мінімум 2, максимум 10 варіантів)");
    }

    public function processOptions($message, $stateData) {
        $chatId = $message['chat']['id'];
        $text = trim($message['text']);
        $data = $stateData ?: array();

        if ($text === '/done') {
            $options = $data['options'] ?? array();
            if (count($options) < 2) {
                $this->bot->sendMessage($chatId, "Потрібно мінімум 2 варіанти. Додайте ще.");
                return;
            }

            if (($data['type'] ?? 'regular') === 'quiz') {
                // For quiz, ask for correct answer
                $optionsList = '';
                foreach ($options as $idx => $opt) {
                    $optionsList .= ($idx + 1) . ". {$opt}\n";
                }
                $this->bot->setUserState($chatId, 'quiz_answer', $data);
                $this->bot->sendMessage($chatId, "Крок 3: Який варіант правильний? (номер 1-" . count($options) . ")\n\n{$optionsList}");
                return;
            }

            // Show settings before creating
            $this->showPollSettings($chatId, $data);
            return;
        }

        if (!isset($data['options'])) $data['options'] = array();

        if (count($data['options']) >= 10) {
            $this->bot->sendMessage($chatId, "Максимум 10 варіантів. Надішліть /done для завершення.");
            return;
        }

        $data['options'][] = $text;
        $count = count($data['options']);
        $this->bot->setUserState($chatId, 'poll_options', $data);
        $this->bot->sendMessage($chatId, "Варіант {$count} додано: *{$text}*\n\nДодайте ще або надішліть /done.");
    }

    public function processQuizAnswer($message, $stateData) {
        $chatId = $message['chat']['id'];
        $answer = (int)trim($message['text']) - 1;
        $data = $stateData ?: array();
        $optionsCount = count($data['options'] ?? array());

        if ($answer < 0 || $answer >= $optionsCount) {
            $this->bot->sendMessage($chatId, "Невірний номер. Введіть число від 1 до {$optionsCount}.");
            return;
        }

        $data['correct_option'] = $answer;
        $this->showPollSettings($chatId, $data);
    }

    private function showPollSettings($chatId, $data) {
        $data['anonymous'] = true;
        $data['multiple'] = false;

        $this->bot->setUserState($chatId, 'poll_settings', $data);

        $type = ($data['type'] ?? 'regular') === 'quiz' ? 'квіз' : 'опитування';
        $text = "Налаштування {$type}:";

        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('Анонімне (ON)', 'toggle_anon'),
            WorldMatesBot::callbackButton('Мульти-відповідь (OFF)', 'toggle_multi'),
            WorldMatesBot::callbackButton('Створити', 'confirm_poll'),
            WorldMatesBot::callbackButton('Скасувати', 'cancel_poll')
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    private function createPoll($chatId, $question, $options, $type = 'regular', $anonymous = true, $multiple = false, $correctOption = null) {
        $safe_q = mysqli_real_escape_string($this->db, $question);
        $correct_val = ($correctOption !== null) ? (int)$correctOption : 'NULL';
        $anon = $anonymous ? 1 : 0;
        $multi = $multiple ? 1 : 0;

        mysqli_query($this->db, "INSERT INTO Wo_Bot_Polls
            (bot_id, chat_id, question, poll_type, is_anonymous, allows_multiple_answers, correct_option_id, created_at)
            VALUES ('{$this->botId}', '{$chatId}', '{$safe_q}', '{$type}', {$anon}, {$multi}, {$correct_val}, NOW())");

        $pollId = mysqli_insert_id($this->db);

        // Insert options
        foreach ($options as $idx => $opt) {
            $safe_opt = mysqli_real_escape_string($this->db, $opt);
            mysqli_query($this->db, "INSERT INTO Wo_Bot_Poll_Options (poll_id, option_text, option_index)
                VALUES ({$pollId}, '{$safe_opt}', {$idx})");
        }

        // Build poll message
        $typeLabel = $type === 'quiz' ? 'QUIZ' : 'POLL';
        $anonLabel = $anonymous ? 'Anonimne' : 'Publichne';
        $text = "*[{$typeLabel}] {$question}*\n_{$anonLabel} golosuvannya_\n\n";

        $buttons = array();
        foreach ($options as $idx => $opt) {
            $text .= ($idx + 1) . ". {$opt}\n";
            $buttons[] = array(WorldMatesBot::callbackButton(($idx + 1) . ". " . $opt, "vote_{$pollId}_{$idx}"));
        }

        $text .= "\n0 golosiv. Golosuite!";

        // Add management buttons at the bottom
        $buttons[] = array(
            WorldMatesBot::callbackButton('Rezultaty', "poll_results_{$pollId}"),
            WorldMatesBot::callbackButton('Zakriti', "poll_close_{$pollId}")
        );

        $this->bot->sendMessageWithKeyboard($chatId, $text, $buttons);
        $this->bot->clearUserState($chatId);
    }

    public function handleResults($message, $args) {
        $chatId = $message['chat']['id'];
        $pollId = (int)trim($args);

        if ($pollId <= 0) {
            $this->bot->sendMessage($chatId, "Вкажіть ID опитування: /results 1");
            return;
        }

        $this->showResults($chatId, $pollId);
    }

    private function showResults($chatId, $pollId) {
        $poll = $this->getPoll($pollId);
        if (!$poll) {
            $this->bot->sendMessage($chatId, "Опитування не знайдено.");
            return;
        }

        $options = $this->getPollOptions($pollId);
        $totalVotes = array_sum(array_column($options, 'voter_count'));

        $text = "*Результати: {$poll['question']}*\n";
        $text .= $poll['is_closed'] ? "_[Закрито]_\n\n" : "_[Активне]_\n\n";

        foreach ($options as $opt) {
            $percent = $totalVotes > 0 ? round(($opt['voter_count'] / $totalVotes) * 100) : 0;
            $barLen = (int)($percent / 10);
            $bar = str_repeat(self::BAR_FILLED, $barLen) . str_repeat(self::BAR_EMPTY, 10 - $barLen);

            $correctMark = '';
            if ($poll['poll_type'] === 'quiz' && $opt['option_index'] == $poll['correct_option_id']) {
                $correctMark = ' *';
            }

            $text .= "{$opt['option_text']}{$correctMark}\n";
            $text .= "[{$bar}] {$percent}% ({$opt['voter_count']})\n\n";
        }

        $text .= "Всього голосів: *{$totalVotes}* | Учасників: *{$poll['total_voters']}*";

        $this->bot->sendMessage($chatId, $text);
    }

    public function handleClose($message, $args) {
        $chatId = $message['chat']['id'];
        $pollId = (int)trim($args);

        if ($pollId <= 0) {
            $this->bot->sendMessage($chatId, "Вкажіть ID: /close 1");
            return;
        }

        $poll = $this->getPoll($pollId);
        if (!$poll || $poll['chat_id'] != $chatId) {
            $this->bot->sendMessage($chatId, "Опитування не знайдено або ви не автор.");
            return;
        }

        mysqli_query($this->db, "UPDATE Wo_Bot_Polls SET is_closed = 1, closed_at = NOW() WHERE id = {$pollId}");
        $this->bot->sendMessage($chatId, "Опитування #{$pollId} закрито.");
        $this->showResults($chatId, $pollId);
    }

    public function handleMyPolls($message) {
        $chatId = $message['chat']['id'];

        $result = mysqli_query($this->db, "SELECT p.*, COUNT(DISTINCT v.user_id) as vote_count
            FROM Wo_Bot_Polls p
            LEFT JOIN Wo_Bot_Poll_Votes v ON p.id = v.poll_id
            WHERE p.bot_id = '{$this->botId}' AND p.chat_id = '{$chatId}'
            GROUP BY p.id
            ORDER BY p.created_at DESC LIMIT 20");

        $text = "*Мої опитування:*\n\n";
        $found = false;
        while ($poll = mysqli_fetch_assoc($result)) {
            $found = true;
            $status = $poll['is_closed'] ? 'CLOSED' : 'ACTIVE';
            $type = $poll['poll_type'] === 'quiz' ? 'QUIZ' : 'POLL';
            $text .= "[{$type}|{$status}] #{$poll['id']}: {$poll['question']}\n";
            $text .= "  Голосів: {$poll['vote_count']} | " . date('d.m.Y', strtotime($poll['created_at'])) . "\n\n";
        }

        if (!$found) {
            $text .= "Немає опитувань. Створіть: /poll";
        }

        $this->bot->sendMessage($chatId, $text);
    }

    public function handleCallback($callback) {
        $chatId = $callback['from']['id'];
        $userId = $callback['from']['id'];
        $data = $callback['data'];

        // Vote
        if (strpos($data, 'vote_') === 0) {
            preg_match('/vote_(\d+)_(\d+)/', $data, $m);
            $pollId = (int)$m[1];
            $optionIndex = (int)$m[2];

            $poll = $this->getPoll($pollId);
            if (!$poll) {
                $this->bot->answerCallbackQuery($callback['id'], 'Опитування не знайдено');
                return;
            }
            if ($poll['is_closed']) {
                $this->bot->answerCallbackQuery($callback['id'], 'Опитування закрито');
                return;
            }

            // Get option ID
            $opt_result = mysqli_query($this->db, "SELECT id FROM Wo_Bot_Poll_Options
                WHERE poll_id = {$pollId} AND option_index = {$optionIndex} LIMIT 1");
            $option = mysqli_fetch_assoc($opt_result);
            if (!$option) {
                $this->bot->answerCallbackQuery($callback['id'], 'Невірний варіант');
                return;
            }

            // Check if already voted
            if (!$poll['allows_multiple_answers']) {
                $existing = mysqli_query($this->db, "SELECT id FROM Wo_Bot_Poll_Votes
                    WHERE poll_id = {$pollId} AND user_id = '{$userId}'");
                if (mysqli_num_rows($existing) > 0) {
                    $this->bot->answerCallbackQuery($callback['id'], 'Ви вже голосували!', true);
                    return;
                }
            } else {
                // Check duplicate for this specific option
                $existing = mysqli_query($this->db, "SELECT id FROM Wo_Bot_Poll_Votes
                    WHERE poll_id = {$pollId} AND option_id = {$option['id']} AND user_id = '{$userId}'");
                if (mysqli_num_rows($existing) > 0) {
                    $this->bot->answerCallbackQuery($callback['id'], 'Ви вже голосували за цей варіант');
                    return;
                }
            }

            // Record vote
            mysqli_query($this->db, "INSERT INTO Wo_Bot_Poll_Votes (poll_id, option_id, user_id)
                VALUES ({$pollId}, {$option['id']}, '{$userId}')");

            // Update counters
            mysqli_query($this->db, "UPDATE Wo_Bot_Poll_Options SET voter_count = voter_count + 1
                WHERE id = {$option['id']}");
            mysqli_query($this->db, "UPDATE Wo_Bot_Polls SET total_voters = (
                SELECT COUNT(DISTINCT user_id) FROM Wo_Bot_Poll_Votes WHERE poll_id = {$pollId}
            ) WHERE id = {$pollId}");

            // For quiz, show if correct
            if ($poll['poll_type'] === 'quiz') {
                $isCorrect = ($optionIndex == $poll['correct_option_id']);
                $msg = $isCorrect ? 'Correct!' : 'Wrong!';
                if ($poll['explanation'] && !$isCorrect) {
                    $msg .= "\n" . $poll['explanation'];
                }
                $this->bot->answerCallbackQuery($callback['id'], $msg, true);
            } else {
                $options = $this->getPollOptions($pollId);
                $votedOption = $options[$optionIndex]['option_text'] ?? '';
                $this->bot->answerCallbackQuery($callback['id'], "Ваш голос: {$votedOption}");
            }

        } elseif (strpos($data, 'poll_results_') === 0) {
            $pollId = (int)substr($data, 13);
            $this->showResults($chatId, $pollId);
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif (strpos($data, 'poll_close_') === 0) {
            $pollId = (int)substr($data, 11);
            $this->handleClose(array('chat' => array('id' => $chatId), 'from' => $callback['from']), (string)$pollId);
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'new_poll') {
            $this->handlePoll(array('chat' => array('id' => $chatId), 'from' => $callback['from']), '');
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'new_quiz') {
            $this->handleQuiz(array('chat' => array('id' => $chatId), 'from' => $callback['from']), '');
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'my_polls') {
            $this->handleMyPolls(array('chat' => array('id' => $chatId), 'from' => $callback['from']));
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'help') {
            $this->handleHelp(array('chat' => array('id' => $chatId), 'from' => $callback['from']));
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'toggle_anon' || $data === 'toggle_multi') {
            $stateResult = $this->bot->getUserState($chatId);
            $stateData = $stateResult['state_data'] ?? array();

            if ($data === 'toggle_anon') {
                $stateData['anonymous'] = !($stateData['anonymous'] ?? true);
            } else {
                $stateData['multiple'] = !($stateData['multiple'] ?? false);
            }

            $this->bot->setUserState($chatId, 'poll_settings', $stateData);
            $this->bot->answerCallbackQuery($callback['id'], 'Оновлено');

        } elseif ($data === 'confirm_poll') {
            $stateResult = $this->bot->getUserState($chatId);
            $stateData = $stateResult['state_data'] ?? array();

            if (!empty($stateData['question']) && !empty($stateData['options'])) {
                $this->createPoll(
                    $chatId,
                    $stateData['question'],
                    $stateData['options'],
                    $stateData['type'] ?? 'regular',
                    $stateData['anonymous'] ?? true,
                    $stateData['multiple'] ?? false,
                    $stateData['correct_option'] ?? null
                );
            }
            $this->bot->answerCallbackQuery($callback['id'], 'Створено!');

        } elseif ($data === 'cancel_poll') {
            $this->bot->clearUserState($chatId);
            $this->bot->sendMessage($chatId, "Створення скасовано.");
            $this->bot->answerCallbackQuery($callback['id']);
        }
    }

    public function handleMessage($message) {
        $chatId = $message['chat']['id'];
        $this->bot->sendMessage($chatId, "Використайте /poll для створення опитування або /help для довідки.");
    }

    // ==================== HELPERS ====================

    private function getPoll($pollId) {
        $result = mysqli_query($this->db, "SELECT * FROM Wo_Bot_Polls
            WHERE id = {$pollId} AND bot_id = '{$this->botId}' LIMIT 1");
        return mysqli_fetch_assoc($result);
    }

    private function getPollOptions($pollId) {
        $result = mysqli_query($this->db, "SELECT * FROM Wo_Bot_Poll_Options
            WHERE poll_id = {$pollId} ORDER BY option_index");
        $options = array();
        while ($row = mysqli_fetch_assoc($result)) {
            $options[] = $row;
        }
        return $options;
    }

    /**
     * Auto-close polls that have expired (called by cron)
     */
    public function autoClosePolls() {
        $result = mysqli_query($this->db, "UPDATE Wo_Bot_Polls SET is_closed = 1, closed_at = NOW()
            WHERE bot_id = '{$this->botId}' AND is_closed = 0 AND close_date IS NOT NULL AND close_date <= NOW()");
        return mysqli_affected_rows($this->db);
    }

    public function start() { $this->bot->startPolling(5); }
    public function handleWebhook() { $this->bot->handleWebhook(); }
}
