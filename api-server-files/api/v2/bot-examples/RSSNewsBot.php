<?php
/**
 * WorldMates RSS News Bot
 *
 * Monitors RSS feeds and posts news updates to chats/channels.
 * Users can subscribe to various news sources.
 *
 * Commands:
 *   /start    - Welcome message with instructions
 *   /help     - Show all available commands
 *   /add      - Add a new RSS feed subscription
 *   /remove   - Remove a feed subscription
 *   /list     - List active feed subscriptions
 *   /latest   - Get latest news from all subscribed feeds
 *   /sources  - Show available pre-configured news sources
 *   /settings - Configure feed settings
 */

require_once __DIR__ . '/../bot-sdk/WorldMatesBotSDK.php';

class RSSNewsBot {
    private $bot;
    private $db;
    private $botId;

    // Pre-configured popular news sources
    const DEFAULT_SOURCES = array(
        'bbc' => array(
            'name' => 'BBC News',
            'url' => 'http://feeds.bbci.co.uk/news/rss.xml',
            'lang' => 'en',
            'category' => 'general'
        ),
        'reuters' => array(
            'name' => 'Reuters',
            'url' => 'http://feeds.reuters.com/reuters/topNews',
            'lang' => 'en',
            'category' => 'general'
        ),
        'techcrunch' => array(
            'name' => 'TechCrunch',
            'url' => 'https://techcrunch.com/feed/',
            'lang' => 'en',
            'category' => 'tech'
        ),
        'hackernews' => array(
            'name' => 'Hacker News',
            'url' => 'https://hnrss.org/frontpage',
            'lang' => 'en',
            'category' => 'tech'
        ),
        'pravda_ua' => array(
            'name' => 'Ukrainska Pravda',
            'url' => 'https://www.pravda.com.ua/rss/view_news/',
            'lang' => 'uk',
            'category' => 'ukraine'
        ),
        'unian' => array(
            'name' => 'UNIAN',
            'url' => 'https://rss.unian.net/site/news_ukr.rss',
            'lang' => 'uk',
            'category' => 'ukraine'
        ),
        'espresso' => array(
            'name' => 'Espresso TV',
            'url' => 'https://espreso.tv/rss',
            'lang' => 'uk',
            'category' => 'ukraine'
        ),
        'cnn' => array(
            'name' => 'CNN',
            'url' => 'http://rss.cnn.com/rss/edition.rss',
            'lang' => 'en',
            'category' => 'general'
        ),
        'theverge' => array(
            'name' => 'The Verge',
            'url' => 'https://www.theverge.com/rss/index.xml',
            'lang' => 'en',
            'category' => 'tech'
        ),
        'sports_espn' => array(
            'name' => 'ESPN',
            'url' => 'https://www.espn.com/espn/rss/news',
            'lang' => 'en',
            'category' => 'sports'
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

        $this->bot->onCommand('start', function($message) use ($self) {
            $self->handleStart($message);
        });

        $this->bot->onCommand('help', function($message) use ($self) {
            $self->handleHelp($message);
        });

        $this->bot->onCommand('add', function($message, $args) use ($self) {
            $self->handleAdd($message, $args);
        });

        $this->bot->onCommand('remove', function($message, $args) use ($self) {
            $self->handleRemove($message, $args);
        });

        $this->bot->onCommand('list', function($message) use ($self) {
            $self->handleList($message);
        });

        $this->bot->onCommand('latest', function($message, $args) use ($self) {
            $self->handleLatest($message, $args);
        });

        $this->bot->onCommand('sources', function($message) use ($self) {
            $self->handleSources($message);
        });

        $this->bot->onCommand('settings', function($message) use ($self) {
            $self->handleSettings($message);
        });

        $this->bot->onCallbackQuery(function($callback) use ($self) {
            $self->handleCallback($callback);
        });

        $this->bot->onMessage(function($message) use ($self) {
            $self->handleMessage($message);
        });
    }

    // ==================== COMMAND HANDLERS ====================

    public function handleStart($message) {
        $chatId = $message['chat']['id'];
        $name = $message['from']['first_name'] ?? 'User';

        $text = "*Vistayemo, {$name}!* \n\n";
        $text .= "Я RSS News Bot - ваш персональний агрегатор новин.\n\n";
        $text .= "Я можу:\n";
        $text .= "- Відстежувати RSS-стрічки новин\n";
        $text .= "- Надсилати вам нові статті автоматично\n";
        $text .= "- Підтримувати будь-яке RSS/Atom джерело\n\n";
        $text .= "Почніть з /sources щоб побачити доступні джерела, або /add щоб додати власне.\n";
        $text .= "Натисніть /help для повного списку команд.";

        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('Джерела новин', 'sources_list'),
            WorldMatesBot::callbackButton('Мої підписки', 'my_feeds'),
            WorldMatesBot::callbackButton('Останні новини', 'latest_all'),
            WorldMatesBot::callbackButton('Допомога', 'help')
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function handleHelp($message) {
        $chatId = $message['chat']['id'];
        $text = "*Доступні команди:*\n\n";
        $text .= "/add `<url>` - Додати RSS-стрічку\n";
        $text .= "/add `<source_key>` - Додати з каталогу (наприклад: /add bbc)\n";
        $text .= "/remove `<feed_id>` - Видалити підписку\n";
        $text .= "/list - Показати мої підписки\n";
        $text .= "/latest - Останні новини з усіх підписок\n";
        $text .= "/latest `<feed_id>` - Новини з конкретної стрічки\n";
        $text .= "/sources - Каталог готових джерел\n";
        $text .= "/settings - Налаштування (інтервал, формат)\n";
        $text .= "/help - Ця довідка\n";

        $this->bot->sendMessage($chatId, $text);
    }

    public function handleAdd($message, $args) {
        $chatId = $message['chat']['id'];

        if (empty($args)) {
            // Show source selection menu
            $this->handleSources($message);
            return;
        }

        $source = trim($args);

        // Check if it's a pre-configured source key
        if (isset(self::DEFAULT_SOURCES[$source])) {
            $sourceData = self::DEFAULT_SOURCES[$source];
            $feedUrl = $sourceData['url'];
            $feedName = $sourceData['name'];
        } elseif (filter_var($source, FILTER_VALIDATE_URL)) {
            $feedUrl = $source;
            $feedName = $this->getFeedTitle($source);
            if (!$feedName) {
                $this->bot->sendMessage($chatId, "Не вдалося отримати RSS-стрічку за URL. Перевірте, що це валідний RSS/Atom URL.");
                return;
            }
        } else {
            $this->bot->sendMessage($chatId, "Невідоме джерело: `{$source}`. Використайте /sources для списку або надішліть URL RSS-стрічки.");
            return;
        }

        // Check if already subscribed
        $safe_url = mysqli_real_escape_string($this->db, $feedUrl);
        $existing = mysqli_query($this->db, "SELECT id FROM Wo_Bot_RSS_Feeds
            WHERE bot_id = '{$this->botId}' AND chat_id = '{$chatId}' AND feed_url = '{$safe_url}'");

        if (mysqli_num_rows($existing) > 0) {
            $this->bot->sendMessage($chatId, "Ви вже підписані на *{$feedName}*.");
            return;
        }

        // Check feed limit (max 20 feeds per chat)
        $count = mysqli_query($this->db, "SELECT COUNT(*) as cnt FROM Wo_Bot_RSS_Feeds
            WHERE bot_id = '{$this->botId}' AND chat_id = '{$chatId}'");
        $row = mysqli_fetch_assoc($count);
        if ($row['cnt'] >= 20) {
            $this->bot->sendMessage($chatId, "Максимум 20 підписок. Видаліть старі за допомогою /remove.");
            return;
        }

        $safe_name = mysqli_real_escape_string($this->db, $feedName);
        $lang = isset($sourceData['lang']) ? $sourceData['lang'] : 'en';

        mysqli_query($this->db, "INSERT INTO Wo_Bot_RSS_Feeds
            (bot_id, chat_id, feed_url, feed_name, feed_language, is_active, check_interval_minutes, created_at)
            VALUES ('{$this->botId}', '{$chatId}', '{$safe_url}', '{$safe_name}', '{$lang}', 1, 30, NOW())");

        $this->bot->sendMessage($chatId, "Підписка додана: *{$feedName}*\nНовини будуть надходити автоматично кожні 30 хвилин.\n\nНатисніть /latest для перших новин.");
    }

    public function handleRemove($message, $args) {
        $chatId = $message['chat']['id'];

        if (empty($args)) {
            // Show feeds list with remove buttons
            $feeds = $this->getUserFeeds($chatId);
            if (empty($feeds)) {
                $this->bot->sendMessage($chatId, "У вас немає активних підписок.");
                return;
            }

            $text = "*Ваші підписки (натисніть для видалення):*\n";
            $buttons = array();
            foreach ($feeds as $feed) {
                $buttons[] = WorldMatesBot::callbackButton("X " . $feed['feed_name'], 'remove_feed_' . $feed['id']);
            }

            $keyboard = WorldMatesBot::buildInlineKeyboard($buttons, 1);
            $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
            return;
        }

        $feedId = (int)$args;
        mysqli_query($this->db, "DELETE FROM Wo_Bot_RSS_Feeds
            WHERE id = {$feedId} AND bot_id = '{$this->botId}' AND chat_id = '{$chatId}'");

        if (mysqli_affected_rows($this->db) > 0) {
            $this->bot->sendMessage($chatId, "Підписку видалено.");
        } else {
            $this->bot->sendMessage($chatId, "Підписку не знайдено.");
        }
    }

    public function handleList($message) {
        $chatId = $message['chat']['id'];
        $feeds = $this->getUserFeeds($chatId);

        if (empty($feeds)) {
            $this->bot->sendMessage($chatId, "У вас немає активних підписок. Додайте за допомогою /add або /sources.");
            return;
        }

        $text = "*Ваші підписки ({$this->count($feeds)}):*\n\n";
        foreach ($feeds as $idx => $feed) {
            $status = $feed['is_active'] ? 'ON' : 'OFF';
            $lastCheck = $feed['last_check_at'] ? date('d.m H:i', strtotime($feed['last_check_at'])) : 'ще не перевірялось';
            $text .= ($idx + 1) . ". *{$feed['feed_name']}* [{$status}]\n";
            $text .= "   Перевірка: кожні {$feed['check_interval_minutes']} хв | Остання: {$lastCheck}\n";
            $text .= "   Опубліковано: {$feed['items_posted']} новин\n\n";
        }

        $this->bot->sendMessage($chatId, $text);
    }

    public function handleLatest($message, $args = null) {
        $chatId = $message['chat']['id'];

        if ($args) {
            $feedId = (int)$args;
            $feeds = array($this->getFeedById($feedId, $chatId));
        } else {
            $feeds = $this->getUserFeeds($chatId);
        }

        if (empty($feeds) || !$feeds[0]) {
            $this->bot->sendMessage($chatId, "Немає активних підписок або стрічку не знайдено.");
            return;
        }

        foreach ($feeds as $feed) {
            if (!$feed) continue;
            $items = $this->fetchFeedItems($feed['feed_url'], 3);
            if (empty($items)) continue;

            $text = "*{$feed['feed_name']}:*\n\n";
            foreach ($items as $item) {
                $text .= "- [{$item['title']}]({$item['link']})\n";
                if ($item['description']) {
                    $desc = substr(strip_tags($item['description']), 0, 100);
                    $text .= "  _{$desc}..._\n";
                }
                $text .= "\n";
            }

            $this->bot->sendMessage($chatId, $text);
        }
    }

    public function handleSources($message) {
        $chatId = $message['chat']['id'];

        $categories = array(
            'general' => 'Загальні новини',
            'tech' => 'Технології',
            'ukraine' => 'Україна',
            'sports' => 'Спорт'
        );

        $text = "*Каталог джерел новин:*\n\n";
        foreach ($categories as $catKey => $catName) {
            $text .= "*{$catName}:*\n";
            foreach (self::DEFAULT_SOURCES as $key => $source) {
                if ($source['category'] === $catKey) {
                    $text .= "  /add `{$key}` - {$source['name']}\n";
                }
            }
            $text .= "\n";
        }
        $text .= "Або додайте свій RSS: /add `https://example.com/feed.xml`";

        // Build inline keyboard for quick add
        $buttons = array();
        foreach (self::DEFAULT_SOURCES as $key => $source) {
            $buttons[] = WorldMatesBot::callbackButton($source['name'], 'add_source_' . $key);
        }

        $keyboard = WorldMatesBot::buildInlineKeyboard($buttons, 2);
        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function handleSettings($message) {
        $chatId = $message['chat']['id'];

        $text = "*Налаштування RSS Bot:*\n\n";
        $text .= "Оберіть що налаштувати:";

        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('Інтервал перевірки', 'settings_interval'),
            WorldMatesBot::callbackButton('Формат новин', 'settings_format'),
            WorldMatesBot::callbackButton('Показувати зображення', 'settings_images'),
            WorldMatesBot::callbackButton('Макс. новин за раз', 'settings_max_items')
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    // ==================== CALLBACK HANDLERS ====================

    public function handleCallback($callback) {
        $chatId = $callback['from']['id'];
        $data = $callback['data'];

        if (strpos($data, 'add_source_') === 0) {
            $sourceKey = substr($data, 11);
            $this->handleAdd(array('chat' => array('id' => $chatId), 'from' => $callback['from']), $sourceKey);
            $this->bot->answerCallbackQuery($callback['id'], 'Додано!');

        } elseif (strpos($data, 'remove_feed_') === 0) {
            $feedId = (int)substr($data, 12);
            $this->handleRemove(array('chat' => array('id' => $chatId), 'from' => $callback['from']), (string)$feedId);
            $this->bot->answerCallbackQuery($callback['id'], 'Видалено!');

        } elseif ($data === 'sources_list') {
            $this->handleSources(array('chat' => array('id' => $chatId), 'from' => $callback['from']));
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'my_feeds') {
            $this->handleList(array('chat' => array('id' => $chatId), 'from' => $callback['from']));
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'latest_all') {
            $this->handleLatest(array('chat' => array('id' => $chatId), 'from' => $callback['from']));
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif ($data === 'help') {
            $this->handleHelp(array('chat' => array('id' => $chatId), 'from' => $callback['from']));
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif (strpos($data, 'settings_interval') === 0) {
            $keyboard = WorldMatesBot::buildInlineKeyboard(array(
                WorldMatesBot::callbackButton('15 хв', 'set_interval_15'),
                WorldMatesBot::callbackButton('30 хв', 'set_interval_30'),
                WorldMatesBot::callbackButton('1 год', 'set_interval_60'),
                WorldMatesBot::callbackButton('3 год', 'set_interval_180')
            ), 2);
            $this->bot->sendMessageWithKeyboard($chatId, "Оберіть інтервал перевірки новин:", $keyboard);
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif (strpos($data, 'set_interval_') === 0) {
            $interval = (int)substr($data, 13);
            mysqli_query($this->db, "UPDATE Wo_Bot_RSS_Feeds SET check_interval_minutes = {$interval}
                WHERE bot_id = '{$this->botId}' AND chat_id = '{$chatId}'");
            $this->bot->sendMessage($chatId, "Інтервал перевірки оновлено: кожні {$interval} хв.");
            $this->bot->answerCallbackQuery($callback['id'], 'Оновлено!');
        }
    }

    public function handleMessage($message) {
        $chatId = $message['chat']['id'];
        $text = $message['text'] ?? '';

        // Check if user sent a URL (auto-add feed)
        if (filter_var($text, FILTER_VALIDATE_URL)) {
            $this->handleAdd($message, $text);
            return;
        }

        $this->bot->sendMessage($chatId, "Я розумію тільки команди та URL RSS-стрічок.\nНатисніть /help для списку команд.");
    }

    // ==================== RSS FETCHING ====================

    /**
     * Check all active feeds and post new items (called by cron)
     */
    public function checkFeeds() {
        $result = mysqli_query($this->db, "SELECT * FROM Wo_Bot_RSS_Feeds
            WHERE bot_id = '{$this->botId}' AND is_active = 1
            AND (last_check_at IS NULL OR last_check_at < DATE_SUB(NOW(), INTERVAL check_interval_minutes MINUTE))
            LIMIT 50");

        $posted = 0;
        while ($feed = mysqli_fetch_assoc($result)) {
            $items = $this->fetchFeedItems($feed['feed_url'], $feed['max_items_per_check']);

            foreach ($items as $item) {
                $itemHash = md5($item['link'] . $item['title']);

                // Check if already posted
                $existing = mysqli_query($this->db, "SELECT id FROM Wo_Bot_RSS_Items
                    WHERE feed_id = {$feed['id']} AND item_hash = '{$itemHash}'");
                if (mysqli_num_rows($existing) > 0) continue;

                // Post item to chat
                $text = "*{$item['title']}*\n";
                if ($feed['include_description'] && $item['description']) {
                    $desc = substr(strip_tags($item['description']), 0, 200);
                    $text .= "_{$desc}_\n";
                }
                $text .= "\n[Читати далі]({$item['link']})";
                $text .= "\n\n_via {$feed['feed_name']}_";

                $this->bot->sendMessage($feed['chat_id'], $text);

                // Record posted item
                $safe_title = mysqli_real_escape_string($this->db, $item['title']);
                $safe_link = mysqli_real_escape_string($this->db, $item['link']);
                mysqli_query($this->db, "INSERT INTO Wo_Bot_RSS_Items (feed_id, item_hash, title, link)
                    VALUES ({$feed['id']}, '{$itemHash}', '{$safe_title}', '{$safe_link}')");

                $posted++;
            }

            // Update last check time
            mysqli_query($this->db, "UPDATE Wo_Bot_RSS_Feeds SET last_check_at = NOW(),
                items_posted = items_posted + {$posted}, last_item_hash = '{$itemHash}'
                WHERE id = {$feed['id']}");
        }

        return $posted;
    }

    private function fetchFeedItems($url, $limit = 5) {
        $ch = curl_init($url);
        curl_setopt_array($ch, array(
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 15,
            CURLOPT_FOLLOWLOCATION => true,
            CURLOPT_USERAGENT => 'WorldMatesRSSBot/1.0'
        ));
        $xml_string = curl_exec($ch);
        curl_close($ch);

        if (!$xml_string) return array();

        $items = array();
        try {
            $xml = new SimpleXMLElement($xml_string);

            // RSS 2.0
            if (isset($xml->channel->item)) {
                foreach ($xml->channel->item as $item) {
                    $items[] = array(
                        'title' => (string)$item->title,
                        'link' => (string)$item->link,
                        'description' => (string)$item->description,
                        'pubDate' => (string)$item->pubDate
                    );
                    if (count($items) >= $limit) break;
                }
            }
            // Atom
            elseif (isset($xml->entry)) {
                foreach ($xml->entry as $entry) {
                    $link = '';
                    if (isset($entry->link)) {
                        $link = (string)$entry->link['href'];
                    }
                    $items[] = array(
                        'title' => (string)$entry->title,
                        'link' => $link,
                        'description' => (string)($entry->summary ?? $entry->content ?? ''),
                        'pubDate' => (string)($entry->published ?? $entry->updated ?? '')
                    );
                    if (count($items) >= $limit) break;
                }
            }
        } catch (Exception $e) {
            // Invalid XML
        }

        return $items;
    }

    private function getFeedTitle($url) {
        $ch = curl_init($url);
        curl_setopt_array($ch, array(
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 10,
            CURLOPT_FOLLOWLOCATION => true,
            CURLOPT_USERAGENT => 'WorldMatesRSSBot/1.0'
        ));
        $xml_string = curl_exec($ch);
        curl_close($ch);

        if (!$xml_string) return null;

        try {
            $xml = new SimpleXMLElement($xml_string);
            if (isset($xml->channel->title)) {
                return (string)$xml->channel->title;
            }
            if (isset($xml->title)) {
                return (string)$xml->title;
            }
        } catch (Exception $e) {
            return null;
        }
        return null;
    }

    // ==================== HELPERS ====================

    private function getUserFeeds($chatId) {
        $result = mysqli_query($this->db, "SELECT * FROM Wo_Bot_RSS_Feeds
            WHERE bot_id = '{$this->botId}' AND chat_id = '{$chatId}'
            ORDER BY created_at DESC");
        $feeds = array();
        while ($row = mysqli_fetch_assoc($result)) {
            $feeds[] = $row;
        }
        return $feeds;
    }

    private function getFeedById($feedId, $chatId) {
        $result = mysqli_query($this->db, "SELECT * FROM Wo_Bot_RSS_Feeds
            WHERE id = {$feedId} AND bot_id = '{$this->botId}' AND chat_id = '{$chatId}' LIMIT 1");
        return mysqli_fetch_assoc($result);
    }

    private function count($arr) { return count($arr); }

    /**
     * Start bot (for standalone execution)
     */
    public function start() {
        $this->bot->startPolling(5);
    }

    /**
     * Handle incoming webhook
     */
    public function handleWebhook() {
        $this->bot->handleWebhook();
    }
}

// ==================== STANDALONE EXECUTION ====================
// Uncomment below to run as standalone bot:
//
// $config = require(__DIR__ . '/bot_config.php');
// $db = new mysqli($config['db_host'], $config['db_user'], $config['db_pass'], $config['db_name']);
// $rssBot = new RSSNewsBot($config['rss_bot_token'], $db, $config['rss_bot_id']);
// $rssBot->start();
