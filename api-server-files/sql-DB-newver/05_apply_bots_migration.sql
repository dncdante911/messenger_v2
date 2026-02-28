-- ============================================================
-- WorldMates Bot API — Применение миграции + seed данных
-- Запустить ОДИН РАЗ на рабочей базе данных
--
-- Шаги:
--   1. Применить таблицы ботов (если ещё не применены)
--   2. Создать системного пользователя WallyBot в Wo_Users
--   3. Создать запись WallyBot в Wo_Bots
--   4. Установить команды WallyBot
-- ============================================================

-- Включить строгий режим на время миграции
SET NAMES utf8mb4;
SET foreign_key_checks = 0;

-- ============================================================
-- ШАГ 1: Применить таблицы ботов
-- (Безопасно — использует IF NOT EXISTS)
-- ============================================================

SOURCE add_bot_api_tables.sql;

-- ============================================================
-- ШАГ 2: Создать Wo_Users запись для WallyBot
-- type='bot' уже есть в схеме Wo_Users (varchar(11))
-- Это нужно для появления WallyBot в поиске Android
-- ============================================================

INSERT IGNORE INTO `Wo_Users`
  (`username`, `email`, `password`, `first_name`, `last_name`,
   `avatar`, `about`, `type`, `active`, `verified`,
   `message_privacy`, `lastseen`, `registered`, `joined`,
   `follow_privacy`, `friend_privacy`, `post_privacy`,
   `confirm_followers`, `show_activities_privacy`)
VALUES
  ('wallybot',
   'wallybot@bots.internal',
   MD5(UUID()),
   'WallyBot',
   '',
   'upload/photos/d-avatar.jpg',
   'Официальный бот-менеджер WorldMates. Помогает создавать и управлять ботами прямо в чате!',
   'bot',
   '1',
   '0',
   '0',
   UNIX_TIMESTAMP(),
   DATE_FORMAT(NOW(), '%m/%Y'),
   UNIX_TIMESTAMP(),
   '0',
   '0',
   'everyone',
   '0',
   '1');

-- Сохраним user_id WallyBot в переменную для последующего использования
SET @wallybot_user_id = (SELECT `user_id` FROM `Wo_Users` WHERE `username` = 'wallybot' LIMIT 1);

SELECT CONCAT('WallyBot Wo_Users user_id = ', @wallybot_user_id) AS info;

-- ============================================================
-- ШАГ 3: Создать запись WallyBot в Wo_Bots
-- ============================================================

-- Сначала генерируем токен (детерминированный для воспроизводимости)
SET @wallybot_token = CONCAT('wallybot:', SHA2(CONCAT('wallybot', UUID()), 256));

INSERT IGNORE INTO `Wo_Bots`
  (`bot_id`, `owner_id`, `bot_token`, `username`, `display_name`,
   `description`, `about`, `bot_type`, `status`, `is_public`,
   `is_inline`, `can_join_groups`, `supports_commands`, `category`,
   `linked_user_id`, `created_at`, `updated_at`)
VALUES
  ('wallybot',
   1,
   @wallybot_token,
   'wallybot',
   'WallyBot',
   'Официальный бот-менеджер WorldMates. Создавай своих ботов прямо в чате!',
   'Как Telegram BotFather — помогает создавать и управлять ботами. Обучаем через /learn.',
   'system',
   'active',
   1,
   0,
   0,
   1,
   'system',
   @wallybot_user_id,
   NOW(),
   NOW());

SELECT CONCAT('WallyBot token = ', @wallybot_token) AS wallybot_token;

-- ============================================================
-- ШАГ 4: Добавить поле linked_user_id в Wo_Bots (если нет)
-- Связывает Wo_Bots.bot_id ↔ Wo_Users.user_id
-- ============================================================

ALTER TABLE `Wo_Bots`
  ADD COLUMN IF NOT EXISTS `linked_user_id` INT(11) DEFAULT NULL
  COMMENT 'Соответствующий user_id в Wo_Users (для поиска и DM)';

-- ============================================================
-- ШАГ 5: Команды WallyBot
-- ============================================================

INSERT IGNORE INTO `Wo_Bot_Commands`
  (`bot_id`, `command`, `description`, `scope`, `is_hidden`, `sort_order`)
VALUES
  ('wallybot', 'start',       'Главное меню',                      'all', 0, 0),
  ('wallybot', 'help',        'Список команд',                     'all', 0, 1),
  ('wallybot', 'cancel',      'Отменить текущее действие',         'all', 0, 2),
  ('wallybot', 'newbot',      'Создать нового бота',               'all', 0, 3),
  ('wallybot', 'mybots',      'Список моих ботов',                 'all', 0, 4),
  ('wallybot', 'editbot',     'Редактировать бота',                'all', 0, 5),
  ('wallybot', 'deletebot',   'Удалить бота',                      'all', 0, 6),
  ('wallybot', 'token',       'Получить токен бота',               'all', 0, 7),
  ('wallybot', 'setcommands', 'Установить команды бота',           'all', 0, 8),
  ('wallybot', 'setdesc',     'Изменить описание бота',            'all', 0, 9),
  ('wallybot', 'learn',       'Обучить WallyBot новому ответу',    'all', 0, 10),
  ('wallybot', 'forget',      'Удалить ответ из базы знаний',      'all', 0, 11),
  ('wallybot', 'ask',         'Задать вопрос WallyBot',            'all', 0, 12);

-- ============================================================
-- ШАГ 6: Для каждого бота созданного ранее через PHP —
-- создать Wo_Users entries если их нет
-- ============================================================

INSERT IGNORE INTO `Wo_Users`
  (`username`, `email`, `password`, `first_name`, `last_name`,
   `about`, `type`, `active`, `verified`,
   `message_privacy`, `lastseen`, `registered`, `joined`,
   `follow_privacy`, `friend_privacy`, `post_privacy`,
   `confirm_followers`, `show_activities_privacy`)
SELECT
  b.`username`,
  CONCAT(b.`bot_id`, '@bots.internal'),
  MD5(UUID()),
  b.`display_name`,
  '',
  COALESCE(b.`description`, ''),
  'bot',
  '1',
  '0',
  '0',
  UNIX_TIMESTAMP(),
  DATE_FORMAT(NOW(), '%m/%Y'),
  UNIX_TIMESTAMP(),
  '0',
  '0',
  'everyone',
  '0',
  '1'
FROM `Wo_Bots` b
WHERE NOT EXISTS (
  SELECT 1 FROM `Wo_Users` u WHERE u.`username` = b.`username`
);

-- Проставить linked_user_id для всех ботов
UPDATE `Wo_Bots` b
JOIN `Wo_Users` u ON u.`username` = b.`username`
SET b.`linked_user_id` = u.`user_id`
WHERE b.`linked_user_id` IS NULL;

-- ============================================================
-- Финальный отчёт
-- ============================================================

SELECT 'Миграция завершена!' AS status;
SELECT COUNT(*) AS bot_tables FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'Wo_Bot%';
SELECT COUNT(*) AS total_bots FROM `Wo_Bots`;
SELECT `user_id`, `username`, `type`, `active`
  FROM `Wo_Users` WHERE `type` = 'bot';

SET foreign_key_checks = 1;
