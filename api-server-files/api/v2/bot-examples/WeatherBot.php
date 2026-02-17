<?php
/**
 * WorldMates Weather Bot
 *
 * Provides weather forecasts using OpenWeatherMap API.
 * Users can get current weather, forecasts, and set location alerts.
 *
 * Commands:
 *   /start     - Welcome message
 *   /weather   - Get current weather for a city
 *   /forecast  - Get 3-day forecast
 *   /setcity   - Set default city
 *   /alerts    - Configure weather alerts
 *   /help      - Show commands
 */

require_once __DIR__ . '/../bot-sdk/WorldMatesBotSDK.php';

class WeatherBot {
    private $bot;
    private $db;
    private $botId;
    private $apiKey;

    const OWM_CURRENT = 'https://api.openweathermap.org/data/2.5/weather';
    const OWM_FORECAST = 'https://api.openweathermap.org/data/2.5/forecast';

    // Weather condition emoji mapping
    const WEATHER_EMOJI = array(
        'Clear' => 'sun',
        'Clouds' => 'cloud',
        'Rain' => 'rain',
        'Drizzle' => 'drizzle',
        'Thunderstorm' => 'storm',
        'Snow' => 'snow',
        'Mist' => 'fog',
        'Fog' => 'fog',
        'Haze' => 'fog'
    );

    public function __construct($botToken, $dbConnection, $botId, $owmApiKey = '') {
        $this->bot = new WorldMatesBot($botToken);
        $this->db = $dbConnection;
        $this->botId = $botId;
        $this->apiKey = $owmApiKey;
        $this->registerHandlers();
    }

    private function registerHandlers() {
        $self = $this;

        $this->bot->onCommand('start', function($msg) use ($self) { $self->handleStart($msg); });
        $this->bot->onCommand('help', function($msg) use ($self) { $self->handleHelp($msg); });
        $this->bot->onCommand('weather', function($msg, $a) use ($self) { $self->handleWeather($msg, $a); });
        $this->bot->onCommand('forecast', function($msg, $a) use ($self) { $self->handleForecast($msg, $a); });
        $this->bot->onCommand('setcity', function($msg, $a) use ($self) { $self->handleSetCity($msg, $a); });
        $this->bot->onCommand('alerts', function($msg) use ($self) { $self->handleAlerts($msg); });

        $this->bot->onCallbackQuery(function($cb) use ($self) { $self->handleCallback($cb); });
        $this->bot->onMessage(function($msg) use ($self) { $self->handleMessage($msg); });

        $this->bot->onState('waiting_city', function($msg) use ($self) {
            $self->processSetCity($msg);
        });
    }

    public function handleStart($message) {
        $chatId = $message['chat']['id'];
        $name = $message['from']['first_name'] ?? 'User';

        $text = "*Vistayemo, {$name}!*\n\n";
        $text .= "Я Weather Bot - ваш персональний метеоролог.\n\n";
        $text .= "Швидкий старт:\n";
        $text .= "- /weather Kyiv - погода в Києві\n";
        $text .= "- /forecast London - прогноз на 3 дні\n";
        $text .= "- /setcity - встановити місто за замовчуванням\n\n";
        $text .= "Або просто напишіть назву міста!";

        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('Kyiv', 'weather_Kyiv'),
            WorldMatesBot::callbackButton('London', 'weather_London'),
            WorldMatesBot::callbackButton('New York', 'weather_New York'),
            WorldMatesBot::callbackButton('Tokyo', 'weather_Tokyo')
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function handleHelp($message) {
        $chatId = $message['chat']['id'];
        $text = "*Команди Weather Bot:*\n\n";
        $text .= "/weather `<city>` - Поточна погода\n";
        $text .= "/forecast `<city>` - Прогноз на 3 дні\n";
        $text .= "/setcity `<city>` - Місто за замовчуванням\n";
        $text .= "/alerts - Сповіщення про погоду\n";
        $text .= "/help - Ця довідка\n\n";
        $text .= "Або просто напишіть назву міста для швидкої перевірки.";

        $this->bot->sendMessage($chatId, $text);
    }

    public function handleWeather($message, $args) {
        $chatId = $message['chat']['id'];
        $city = trim($args);

        if (empty($city)) {
            // Try user's default city
            $city = $this->getUserCity($chatId);
            if (!$city) {
                $this->bot->sendMessage($chatId, "Вкажіть місто: /weather Kyiv\nАбо встановіть за замовчуванням: /setcity");
                return;
            }
        }

        $weather = $this->fetchCurrentWeather($city);
        if (!$weather) {
            $this->bot->sendMessage($chatId, "Місто не знайдено: *{$city}*. Перевірте назву.");
            return;
        }

        $text = $this->formatCurrentWeather($weather);

        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('Прогноз 3 дні', 'forecast_' . $city),
            WorldMatesBot::callbackButton('Оновити', 'weather_' . $city)
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function handleForecast($message, $args) {
        $chatId = $message['chat']['id'];
        $city = trim($args);

        if (empty($city)) {
            $city = $this->getUserCity($chatId);
            if (!$city) {
                $this->bot->sendMessage($chatId, "Вкажіть місто: /forecast Kyiv");
                return;
            }
        }

        $forecast = $this->fetchForecast($city);
        if (!$forecast) {
            $this->bot->sendMessage($chatId, "Місто не знайдено: *{$city}*.");
            return;
        }

        $text = $this->formatForecast($forecast, $city);
        $this->bot->sendMessage($chatId, $text);
    }

    public function handleSetCity($message, $args) {
        $chatId = $message['chat']['id'];

        if (!empty($args)) {
            $this->saveUserCity($chatId, trim($args));
            $this->bot->sendMessage($chatId, "Місто за замовчуванням: *{$args}*\nТепер /weather покаже погоду для цього міста.");
            return;
        }

        $this->bot->setUserState($chatId, 'waiting_city');
        $this->bot->sendMessage($chatId, "Напишіть назву вашого міста:");
    }

    public function processSetCity($message) {
        $chatId = $message['chat']['id'];
        $city = trim($message['text']);

        // Verify city exists
        $weather = $this->fetchCurrentWeather($city);
        if (!$weather) {
            $this->bot->sendMessage($chatId, "Місто *{$city}* не знайдено. Спробуйте ще раз:");
            return;
        }

        $this->saveUserCity($chatId, $city);
        $this->bot->clearUserState($chatId);
        $this->bot->sendMessage($chatId, "Місто встановлено: *{$city}* ({$weather['sys']['country']})\nТепер /weather покаже погоду автоматично.");
    }

    public function handleAlerts($message) {
        $chatId = $message['chat']['id'];

        $text = "*Сповіщення про погоду:*\n\nОберіть тип сповіщення:";
        $keyboard = WorldMatesBot::buildInlineKeyboard(array(
            WorldMatesBot::callbackButton('Щоранку (08:00)', 'alert_morning'),
            WorldMatesBot::callbackButton('Перед дощем', 'alert_rain'),
            WorldMatesBot::callbackButton('Екстремальна погода', 'alert_extreme'),
            WorldMatesBot::callbackButton('Вимкнути всі', 'alert_off')
        ), 2);

        $this->bot->sendMessageWithKeyboard($chatId, $text, $keyboard);
    }

    public function handleCallback($callback) {
        $chatId = $callback['from']['id'];
        $data = $callback['data'];

        if (strpos($data, 'weather_') === 0) {
            $city = substr($data, 8);
            $this->handleWeather(array('chat' => array('id' => $chatId), 'from' => $callback['from']), $city);
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif (strpos($data, 'forecast_') === 0) {
            $city = substr($data, 9);
            $this->handleForecast(array('chat' => array('id' => $chatId), 'from' => $callback['from']), $city);
            $this->bot->answerCallbackQuery($callback['id']);

        } elseif (strpos($data, 'alert_') === 0) {
            $type = substr($data, 6);
            $this->bot->sendMessage($chatId, "Сповіщення типу *{$type}* налаштовано. (Потребує cron job на сервері)");
            $this->bot->answerCallbackQuery($callback['id'], 'Налаштовано!');
        }
    }

    public function handleMessage($message) {
        $chatId = $message['chat']['id'];
        $text = trim($message['text'] ?? '');

        if (!empty($text) && strlen($text) < 100) {
            // Treat any text as a city name
            $this->handleWeather($message, $text);
        }
    }

    // ==================== WEATHER API ====================

    private function fetchCurrentWeather($city) {
        if (empty($this->apiKey)) {
            // Return mock data for demo without API key
            return $this->getMockWeather($city);
        }

        $url = self::OWM_CURRENT . '?' . http_build_query(array(
            'q' => $city,
            'appid' => $this->apiKey,
            'units' => 'metric',
            'lang' => 'uk'
        ));

        return $this->httpGet($url);
    }

    private function fetchForecast($city) {
        if (empty($this->apiKey)) {
            return $this->getMockForecast($city);
        }

        $url = self::OWM_FORECAST . '?' . http_build_query(array(
            'q' => $city,
            'appid' => $this->apiKey,
            'units' => 'metric',
            'lang' => 'uk',
            'cnt' => 24 // 3 days * 8 (3-hour intervals)
        ));

        return $this->httpGet($url);
    }

    private function httpGet($url) {
        $ch = curl_init($url);
        curl_setopt_array($ch, array(
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 10,
            CURLOPT_SSL_VERIFYPEER => true
        ));
        $response = curl_exec($ch);
        curl_close($ch);
        $data = json_decode($response, true);
        return ($data && isset($data['cod']) && $data['cod'] == 200) ? $data : null;
    }

    // ==================== FORMATTING ====================

    private function formatCurrentWeather($data) {
        $city = $data['name'];
        $country = $data['sys']['country'] ?? '';
        $temp = round($data['main']['temp']);
        $feelsLike = round($data['main']['feels_like']);
        $humidity = $data['main']['humidity'];
        $wind = round($data['wind']['speed'], 1);
        $desc = $data['weather'][0]['description'] ?? '';
        $mainWeather = $data['weather'][0]['main'] ?? 'Clear';

        $emoji = $this->getWeatherEmoji($mainWeather);

        $text = "{$emoji} *Погода: {$city}, {$country}*\n\n";
        $text .= "Температура: *{$temp}C* (відчувається {$feelsLike}C)\n";
        $text .= "Опис: {$desc}\n";
        $text .= "Вологість: {$humidity}%\n";
        $text .= "Вітер: {$wind} м/с\n";

        if (isset($data['main']['pressure'])) {
            $text .= "Тиск: {$data['main']['pressure']} гПа\n";
        }

        if (isset($data['visibility'])) {
            $vis = round($data['visibility'] / 1000, 1);
            $text .= "Видимість: {$vis} км\n";
        }

        $text .= "\n_Оновлено: " . date('H:i d.m.Y') . "_";
        return $text;
    }

    private function formatForecast($data, $city) {
        $text = "*Прогноз: {$city}*\n\n";

        $dailyData = array();
        foreach ($data['list'] as $entry) {
            $date = date('d.m', $entry['dt']);
            if (!isset($dailyData[$date])) {
                $dailyData[$date] = array('temps' => array(), 'weather' => '', 'desc' => '');
            }
            $dailyData[$date]['temps'][] = round($entry['main']['temp']);
            if (date('H', $entry['dt']) == '12' || empty($dailyData[$date]['weather'])) {
                $dailyData[$date]['weather'] = $entry['weather'][0]['main'] ?? 'Clear';
                $dailyData[$date]['desc'] = $entry['weather'][0]['description'] ?? '';
            }
        }

        foreach ($dailyData as $date => $day) {
            $min = min($day['temps']);
            $max = max($day['temps']);
            $emoji = $this->getWeatherEmoji($day['weather']);
            $dayName = $this->getDayName($date);
            $text .= "{$emoji} *{$dayName} ({$date})*: {$min}..{$max}C - {$day['desc']}\n";
        }

        $text .= "\n_Оновлено: " . date('H:i d.m.Y') . "_";
        return $text;
    }

    private function getWeatherEmoji($main) {
        $map = array(
            'Clear' => "\xE2\x98\x80\xEF\xB8\x8F",        // sun
            'Clouds' => "\xE2\x98\x81\xEF\xB8\x8F",       // cloud
            'Rain' => "\xF0\x9F\x8C\xA7\xEF\xB8\x8F",     // rain
            'Drizzle' => "\xF0\x9F\x8C\xA6\xEF\xB8\x8F",  // drizzle
            'Thunderstorm' => "\xE2\x9B\x88\xEF\xB8\x8F",  // storm
            'Snow' => "\xE2\x9D\x84\xEF\xB8\x8F",          // snow
            'Mist' => "\xF0\x9F\x8C\xAB\xEF\xB8\x8F",     // fog
            'Fog' => "\xF0\x9F\x8C\xAB\xEF\xB8\x8F",
            'Haze' => "\xF0\x9F\x8C\xAB\xEF\xB8\x8F"
        );
        return isset($map[$main]) ? $map[$main] : "\xF0\x9F\x8C\xA4\xEF\xB8\x8F";
    }

    private function getDayName($date) {
        $days = array('Нд', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб');
        $ts = strtotime(date('Y') . '-' . implode('-', array_reverse(explode('.', $date))));
        return $days[date('w', $ts)];
    }

    // ==================== USER DATA ====================

    private function getUserCity($chatId) {
        $result = mysqli_query($this->db, "SELECT custom_data FROM Wo_Bot_Users
            WHERE bot_id = '{$this->botId}' AND user_id = '{$chatId}' LIMIT 1");
        $row = mysqli_fetch_assoc($result);
        if ($row && $row['custom_data']) {
            $data = json_decode($row['custom_data'], true);
            return $data['city'] ?? null;
        }
        return null;
    }

    private function saveUserCity($chatId, $city) {
        $data = json_encode(array('city' => $city));
        $safe = mysqli_real_escape_string($this->db, $data);
        $existing = mysqli_query($this->db, "SELECT id FROM Wo_Bot_Users
            WHERE bot_id = '{$this->botId}' AND user_id = '{$chatId}'");

        if (mysqli_num_rows($existing) > 0) {
            mysqli_query($this->db, "UPDATE Wo_Bot_Users SET custom_data = '{$safe}'
                WHERE bot_id = '{$this->botId}' AND user_id = '{$chatId}'");
        } else {
            mysqli_query($this->db, "INSERT INTO Wo_Bot_Users (bot_id, user_id, custom_data, first_interaction_at)
                VALUES ('{$this->botId}', '{$chatId}', '{$safe}', NOW())");
        }
    }

    // ==================== MOCK DATA (for demo) ====================

    private function getMockWeather($city) {
        return array(
            'name' => ucfirst($city),
            'sys' => array('country' => 'UA'),
            'main' => array('temp' => rand(-5, 25), 'feels_like' => rand(-8, 22), 'humidity' => rand(40, 90), 'pressure' => rand(1005, 1030)),
            'weather' => array(array('main' => array_rand(array_flip(array('Clear','Clouds','Rain','Snow'))), 'description' => 'хмарно')),
            'wind' => array('speed' => rand(1, 15)),
            'visibility' => rand(3000, 10000),
            'cod' => 200
        );
    }

    private function getMockForecast($city) {
        $list = array();
        for ($i = 0; $i < 24; $i++) {
            $list[] = array(
                'dt' => time() + ($i * 10800),
                'main' => array('temp' => rand(-5, 25)),
                'weather' => array(array('main' => 'Clouds', 'description' => 'хмарно'))
            );
        }
        return array('list' => $list, 'cod' => '200');
    }

    public function start() { $this->bot->startPolling(5); }
    public function handleWebhook() { $this->bot->handleWebhook(); }
}
