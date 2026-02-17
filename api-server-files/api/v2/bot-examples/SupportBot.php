<?php
/**
 * WorldMates Support Bot
 *
 * Customer support bot with FAQ, ticket system, and live agent handoff.
 *
 * Commands:
 *   /start    - Welcome, show main menu
 *   /help     - FAQ categories
 *   /ticket   - Create a support ticket
 *   /status   - Check ticket status
 *   /feedback - Leave feedback
 *   /contact  - Contact live support
 */

require_once __DIR__ . '/../bot-sdk/WorldMatesBotSDK.php';

class SupportBot {
    private $bot;
    private $db;
    private $botId;

    const FAQ = array(
        'account' => array(
            'title' => 'Акаунт та реєстрація',
            'items' => array(
                'register' => array(
                    'q' => 'Як зареєструватися?',
                    'a' => "Для реєстрації:\n1. Завантажте WorldMates з Play Store\n2. Натисніть 'Реєстрація'\n3. Введіть ім'я користувача, email або телефон\n4. Підтвердіть email/номер кодом\n5. Готово!"
                ),
                'password' => array(
                    'q' => 'Забув пароль. Що робити?',
                    'a' => "Для відновлення паролю:\n1. На екрані входу натисніть 'Забули пароль?'\n2. Введіть email або номер телефону\n3. Отримайте код відновлення\n4. Створіть новий пароль"
                ),
                'delete' => array(
                    'q' => 'Як видалити акаунт?',
                    'a' => "Для видалення акаунту:\n1. Налаштування -> Акаунт -> Видалити акаунт\n2. Підтвердіть дію паролем\n\n*Увага: Це незворотна дія!*"
                ),
                '2fa' => array(
                    'q' => 'Як увімкнути двофакторну автентифікацію?',
                    'a' => "Налаштування -> Безпека -> Двофакторна автентифікація\n\n1. Встановіть Google Authenticator\n2. Відскануйте QR-код\n3. Введіть 6-значний код\n4. Збережіть резервні коди (10 штук)"
                )
            )
        ),
        'messaging' => array(
            'title' => 'Повідомлення та чати',
            'items' => array(
                'encryption' => array(
                    'q' => 'Чи зашифровані мої повідомлення?',
                    'a' => "Так! WorldMates використовує AES-256-GCM шифрування для всіх повідомлень.\n\nКлюч шифрування генерується на основі вашого сеансу. Ніхто, включаючи сервер, не може прочитати ваші повідомлення."
                ),
                'delete_msg' => array(
                    'q' => 'Як видалити повідомлення?',
                    'a' => "1. Довге натискання на повідомлення\n2. Виберіть 'Видалити'\n3. Оберіть: 'Тільки для мене' або 'Для всіх'"
                ),
                'groups' => array(
                    'q' => 'Як створити групу?',
                    'a' => "1. На головному екрані натисніть '+'\n2. Оберіть 'Нова група'\n3. Додайте учасників\n4. Вкажіть назву групи\n5. Натисніть 'Створити'"
                )
            )
        ),
        'calls' => array(
            'title' => 'Дзвінки',
            'items' => array(
                'quality' => array(
                    'q' => 'Погана якість дзвінків',
                    'a' => "Рекомендації:\n1. Перевірте Wi-Fi/мобільний інтернет\n2. Закрийте інші додатки\n3. Перезапустіть WorldMates\n4. Перевірте дозволи мікрофону\n\nМінімальна швидкість: 1 Мбіт/с для аудіо, 3 Мбіт/с для відео."
                ),
                'video' => array(
                    'q' => 'Не працює відеодзвінок',
                    'a' => "1. Дайте дозвіл на камеру (Налаштування -> Додатки -> WorldMates -> Дозволи)\n2. Перевірте інтернет-з'єднання\n3. Оновіть додаток до останньої версії"
                )
            )
        ),
        'privacy' => array(
            'title' => 'Конфіденційність',
            'items' => array(
                'lastseen' => array(
                    'q' => 'Як сховати час останнього входу?',
                    'a' => "Налаштування -> Конфіденційність -> Останній вхід\nОберіть: Всі / Мої контакти / Ніхто"
                ),
                'block' => array(
                    'q' => 'Як заблокувати користувача?',
                    'a' => "1. Відкрийте профіль користувача\n2. Натисніть '...' (три крапки)\n3. Оберіть 'Заблокувати'\n\nЗаблокований не зможе надсилати вам повідомлення та бачити ваш профіль."
                )
            )
        )
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
        $this->bot->onCommand('ticket', function($msg, $a) use ($self) { $self->handleTicket($msg, $a); });
        $this->bot->onCommand('status', function($msg, $a) use ($self) { $self->handleStatus($msg, $a); });
        $this->bot->onCommand('feedback', function($msg) use ($self) { $self->handleFeedback($msg); });
        $this->bot->onCommand('contact', function($msg) use ($self) { $self->handleContact($msg); });

        $this->bot->onCallbackQuery(function($cb) use ($self) { $self->handleCallback($cb); });

        // State-based handlers for conversation flow
        $this->bot->onState('creating_ticket', function($msg, $data) use ($self) { $self->processTicketDescription($msg, $data); });
        $this->bot->onState('awaiting_feedback', function($msg) use ($self) { $self->processFeedback($msg); });
        $this->bot->onState('awaiting_feedback_text', function($msg) use ($self) { $self->processFeedbackText($msg); });

        $this->bot->onMessage(function($msg) use ($self) { $self->handleMessage($msg); });
    }

    public function handleStart($message) {
        $chatId = $message['chat']['id'];
        $name = $message['from']['first_name'] ?? 'User';

        $text = "*Vitayemo, {$name}!*\n\n";
        $text .= "Я бот підтримки WorldMates. Чим можу допомогти?\n\n";
        $text .= "Оберіть тему або напишіть ваше питання:";

        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('FAQ', 'faq_menu'),
            WorldMatesBot::callbackButton('Створити тікет', 'create_ticket'),
            WorldMatesBot::callbackButton('Статус тікета', 'check_status'),
            WorldMatesBot::callbackButton('Зворотній зв\'язок', 'feedback_start')
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function handleHelp($message) {
        $chatId = $message['chat']['id'];
        $text = "*FAQ - Часті питання:*\n\nОберіть категорію:";

        $buttons = array();
        foreach (self::FAQ as $key => $category) {
            $buttons[] = WorldMatesBot::callbackButton($category['title'], 'faq_cat_' . $key);
        }

        $keyboard = WorldMatesBot::buildInlineKeyboard($buttons, 2);
        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function handleTicket($message, $args) {
        $chatId = $message['chat']['id'];

        if (!empty($args)) {
            // Direct ticket creation with description
            $this->createTicket($chatId, $args);
            return;
        }

        $text = "Оберіть категорію проблеми:";
        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('Баг/Помилка', 'ticket_cat_bug'),
            WorldMatesBot::callbackButton('Акаунт', 'ticket_cat_account'),
            WorldMatesBot::callbackButton('Оплата', 'ticket_cat_payment'),
            WorldMatesBot::callbackButton('Інше', 'ticket_cat_other')
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function processTicketDescription($message, $stateData) {
        $chatId = $message['chat']['id'];
        $description = $message['text'];
        $category = $stateData['category'] ?? 'other';

        $this->createTicket($chatId, $description, $category);
        $this->bot->clearUserState($chatId);
    }

    private function createTicket($chatId, $description, $category = 'other') {
        // Store ticket as a task in bot_tasks
        $safe_desc = mysqli_real_escape_string($this->db, $description);
        $safe_cat = mysqli_real_escape_string($this->db, $category);
        $title = 'Support Ticket: ' . substr($description, 0, 50);
        $safe_title = mysqli_real_escape_string($this->db, $title);

        mysqli_query($this->db, "INSERT INTO Wo_Bot_Tasks
            (bot_id, user_id, chat_id, title, description, status, priority, created_at)
            VALUES ('{$this->botId}', '{$chatId}', '{$chatId}',
                    '[{$safe_cat}] {$safe_title}', '{$safe_desc}', 'todo', 'medium', NOW())");

        $ticketId = mysqli_insert_id($this->db);

        $text = "*Ticket #{$ticketId} створено!*\n\n";
        $text .= "Категорія: {$category}\n";
        $text .= "Опис: {$description}\n\n";
        $text .= "Ми відповімо протягом 24 годин.\n";
        $text .= "Перевірити статус: /status {$ticketId}";

        $this->bot->sendMessage($chatId, $text);
    }

    public function handleStatus($message, $args) {
        $chatId = $message['chat']['id'];
        $ticketId = !empty($args) ? (int)$args : 0;

        if ($ticketId > 0) {
            // Show specific ticket
            $result = mysqli_query($this->db, "SELECT * FROM Wo_Bot_Tasks
                WHERE id = {$ticketId} AND bot_id = '{$this->botId}' AND user_id = '{$chatId}' LIMIT 1");
            $ticket = mysqli_fetch_assoc($result);

            if (!$ticket) {
                $this->bot->sendMessage($chatId, "Тікет #{$ticketId} не знайдено.");
                return;
            }

            $statusEmoji = array('todo' => 'OPEN', 'in_progress' => 'IN PROGRESS', 'done' => 'RESOLVED', 'cancelled' => 'CLOSED');
            $status = $statusEmoji[$ticket['status']] ?? $ticket['status'];

            $text = "*Тікет #{$ticketId}*\n\n";
            $text .= "Статус: *{$status}*\n";
            $text .= "Тема: {$ticket['title']}\n";
            $text .= "Опис: {$ticket['description']}\n";
            $text .= "Створено: " . date('d.m.Y H:i', strtotime($ticket['created_at'])) . "\n";

            $this->bot->sendMessage($chatId, $text);
        } else {
            // Show all user tickets
            $result = mysqli_query($this->db, "SELECT * FROM Wo_Bot_Tasks
                WHERE bot_id = '{$this->botId}' AND user_id = '{$chatId}'
                ORDER BY created_at DESC LIMIT 10");

            $text = "*Ваші тікети:*\n\n";
            $found = false;
            while ($ticket = mysqli_fetch_assoc($result)) {
                $found = true;
                $statusMap = array('todo' => 'OPEN', 'in_progress' => 'IN PROGRESS', 'done' => 'RESOLVED', 'cancelled' => 'CLOSED');
                $status = $statusMap[$ticket['status']] ?? $ticket['status'];
                $text .= "#{$ticket['id']} [{$status}] {$ticket['title']}\n";
            }

            if (!$found) {
                $text .= "Тікетів не знайдено. Створити: /ticket";
            }

            $this->bot->sendMessage($chatId, $text);
        }
    }

    public function handleFeedback($message) {
        $chatId = $message['chat']['id'];

        $text = "*Оцініть WorldMates:*\n\nОберіть оцінку:";
        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('1', 'rate_1'),
            WorldMatesBot::callbackButton('2', 'rate_2'),
            WorldMatesBot::callbackButton('3', 'rate_3'),
            WorldMatesBot::callbackButton('4', 'rate_4'),
            WorldMatesBot::callbackButton('5', 'rate_5')
        ), 5);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function processFeedback($message) {
        $chatId = $message['chat']['id'];
        // User entered rating via callback, now waiting for text feedback
        $this->bot->setUserState($chatId, 'awaiting_feedback_text');
        $this->bot->sendMessage($chatId, "Дякуємо за оцінку! Напишіть ваш відгук (або /skip щоб пропустити):");
    }

    public function processFeedbackText($message) {
        $chatId = $message['chat']['id'];
        $text = $message['text'];

        if ($text !== '/skip') {
            $safe_text = mysqli_real_escape_string($this->db, $text);
            mysqli_query($this->db, "INSERT INTO Wo_Bot_Tasks
                (bot_id, user_id, chat_id, title, description, status, priority, created_at)
                VALUES ('{$this->botId}', '{$chatId}', '{$chatId}', 'Feedback', '{$safe_text}', 'done', 'low', NOW())");
        }

        $this->bot->clearUserState($chatId);
        $this->bot->sendMessage($chatId, "Дякуємо за ваш відгук! Він допоможе нам стати кращими.");
    }

    public function handleContact($message) {
        $chatId = $message['chat']['id'];

        $text = "*Контакти підтримки:*\n\n";
        $text .= "Email: support@worldmates.club\n";
        $text .= "Telegram: @worldmates_support\n";
        $text .= "Робочі години: Пн-Пт, 09:00-18:00 (UTC+2)\n\n";
        $text .= "Або створіть тікет: /ticket";

        $this->bot->sendMessage($chatId, $text);
    }

    public function handleCallback($callback) {
        $chatId = $callback['from']['id'];
        $data = $callback['data'];

        // FAQ navigation
        if ($data === 'faq_menu') {
            $this->handleHelp(array('chat' => array('id' => $chatId), 'from' => $callback['from']));
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif (strpos($data, 'faq_cat_') === 0) {
            $catKey = substr($data, 8);
            if (isset(self::FAQ[$catKey])) {
                $cat = self::FAQ[$catKey];
                $text = "*{$cat['title']}:*\n\nОберіть питання:";
                $buttons = array();
                foreach ($cat['items'] as $key => $item) {
                    $buttons[] = WorldMatesBot::callbackButton($item['q'], 'faq_item_' . $catKey . '_' . $key);
                }
                $buttons[] = WorldMatesBot::callbackButton('< Назад', 'faq_menu');
                $keyboard = WorldMatesBot::buildInlineKeyboard($buttons, 1);
                $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
            }
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif (strpos($data, 'faq_item_') === 0) {
            $parts = explode('_', substr($data, 9), 2);
            $catKey = $parts[0];
            $itemKey = $parts[1] ?? '';
            if (isset(self::FAQ[$catKey]['items'][$itemKey])) {
                $item = self::FAQ[$catKey]['items'][$itemKey];
                $text = "*{$item['q']}*\n\n{$item['a']}";

                $keyboard = WorldMatesBot::buildInlineKeyboard(array(
                    WorldMatesBot::callbackButton('Допомогло', 'faq_helpful_yes'),
                    WorldMatesBot::callbackButton('Не допомогло', 'faq_helpful_no'),
                    WorldMatesBot::callbackButton('< До категорії', 'faq_cat_' . $catKey)
                ), 2);

                $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
            }
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'faq_helpful_yes') {
            $this->bot->answerCallbackQuery($callback['id'], 'Раді, що допомогли!');

        } elseif ($data === 'faq_helpful_no') {
            $this->bot->sendMessage($chatId, "Створіть тікет і ми допоможемо особисто: /ticket");
            $this->bot->answerCallbackQuery($callback['id']);

        // Ticket creation
        } elseif ($data === 'create_ticket') {
            $this->handleTicket(array('chat' => array('id' => $chatId), 'from' => $callback['from']), '');
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif (strpos($data, 'ticket_cat_') === 0) {
            $category = substr($data, 11);
            $this->bot->setUserState($chatId, 'creating_ticket', array('category' => $category));
            $this->bot->sendMessage($chatId, "Опишіть вашу проблему детально:");
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'check_status') {
            $this->handleStatus(array('chat' => array('id' => $chatId), 'from' => $callback['from']), '');
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'feedback_start') {
            $this->handleFeedback(array('chat' => array('id' => $chatId), 'from' => $callback['from']));
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif (strpos($data, 'rate_') === 0) {
            $rating = (int)substr($data, 5);
            $this->bot->answerCallbackQuery($callback['id'], "Оцінка: {$rating}/5");
            $this->bot->setUserState($chatId, 'awaiting_feedback_text');
            $this->bot->sendMessage($chatId, "Дякуємо за оцінку *{$rating}/5*! Напишіть коментар (або /skip):");
        }
    }

    public function handleMessage($message) {
        $chatId = $message['chat']['id'];
        $text = $message['text'] ?? '';

        // Try to match FAQ keywords
        $matched = $this->searchFAQ($text);
        if ($matched) {
            $response = "*Можливо, це допоможе:*\n\n*{$matched['q']}*\n\n{$matched['a']}";

            $keyboard = WorldMatesBot::buildInlineKeyboard(array(
                WorldMatesBot::callbackButton('Допомогло', 'faq_helpful_yes'),
                WorldMatesBot::callbackButton('Створити тікет', 'create_ticket')
            ), 2);

            $this->bot->sendMessageWithKeyboard($chatId, $response, $keyboard);
            return;
        }

        // Default response
        $response = "Я не знайшов відповіді на ваше питання.\n\n";
        $response .= "Спробуйте:\n- /help - переглянути FAQ\n- /ticket - створити тікет підтримки";

        $this->bot->sendMessage($chatId, $response);
    }

    private function searchFAQ($query) {
        $query = mb_strtolower($query);
        $bestMatch = null;
        $bestScore = 0;

        foreach (self::FAQ as $cat) {
            foreach ($cat['items'] as $item) {
                $score = 0;
                $keywords = explode(' ', mb_strtolower($item['q']));
                foreach ($keywords as $keyword) {
                    if (mb_strlen($keyword) > 3 && mb_strpos($query, $keyword) !== false) {
                        $score++;
                    }
                }
                if ($score > $bestScore) {
                    $bestScore = $score;
                    $bestMatch = $item;
                }
            }
        }

        return $bestScore >= 2 ? $bestMatch : null;
    }

    public function start() { $this->bot->startPolling(5); }
    public function handleWebhook() { $this->bot->handleWebhook(); }
}
