-- +------------------------------------------------------------------------+
-- | WorldMates Messenger - User Rating System (Karma/Trust Score)
-- +------------------------------------------------------------------------+
-- | Таблиця для зберігання оцінок користувачів (лайки/дизлайки)
-- | Один користувач може поставити одну оцінку іншому користувачу
-- +------------------------------------------------------------------------+

CREATE TABLE IF NOT EXISTS `Wo_UserRatings` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `rater_id` int(11) NOT NULL COMMENT 'ID користувача, який ставить оцінку',
    `rated_user_id` int(11) NOT NULL COMMENT 'ID користувача, якому ставлять оцінку',
    `rating_type` enum('like','dislike') NOT NULL COMMENT 'Тип оцінки: like (👍) або dislike (👎)',
    `comment` text DEFAULT NULL COMMENT 'Необов`язковий коментар до оцінки',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `unique_rating` (`rater_id`, `rated_user_id`) COMMENT 'Один користувач може поставити лише одну оцінку',
    KEY `idx_rated_user` (`rated_user_id`),
    KEY `idx_rating_type` (`rating_type`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `fk_rater` FOREIGN KEY (`rater_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_rated_user` FOREIGN KEY (`rated_user_id`) REFERENCES `Wo_Users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Система оцінки користувачів (карма/довіра)';

-- Додаємо поля до таблиці користувачів для кешування рейтингу
ALTER TABLE `Wo_Users`
    ADD COLUMN IF NOT EXISTS `rating_likes` int(11) NOT NULL DEFAULT 0 COMMENT 'Кількість позитивних оцінок (👍)',
    ADD COLUMN IF NOT EXISTS `rating_dislikes` int(11) NOT NULL DEFAULT 0 COMMENT 'Кількість негативних оцінок (👎)',
    ADD COLUMN IF NOT EXISTS `rating_score` decimal(4,2) NOT NULL DEFAULT 0.00 COMMENT 'Загальний рейтинг (від -100 до +100)',
    ADD COLUMN IF NOT EXISTS `trust_level` enum('untrusted','neutral','trusted','verified') NOT NULL DEFAULT 'neutral' COMMENT 'Рівень довіри',
    ADD INDEX IF NOT EXISTS `idx_rating_score` (`rating_score`),
    ADD INDEX IF NOT EXISTS `idx_trust_level` (`trust_level`);

-- Створюємо тригер для автоматичного оновлення рейтингу при додаванні оцінки
DELIMITER $$

DROP TRIGGER IF EXISTS `update_user_rating_after_insert`$$
CREATE TRIGGER `update_user_rating_after_insert` AFTER INSERT ON `Wo_UserRatings`
FOR EACH ROW
BEGIN
    DECLARE total_likes INT DEFAULT 0;
    DECLARE total_dislikes INT DEFAULT 0;
    DECLARE total_ratings INT DEFAULT 0;
    DECLARE calculated_score DECIMAL(4,2) DEFAULT 0.00;
    DECLARE new_trust_level VARCHAR(20) DEFAULT 'neutral';

    -- Підрахунок кількості оцінок
    SELECT
        SUM(CASE WHEN rating_type = 'like' THEN 1 ELSE 0 END),
        SUM(CASE WHEN rating_type = 'dislike' THEN 1 ELSE 0 END),
        COUNT(*)
    INTO total_likes, total_dislikes, total_ratings
    FROM Wo_UserRatings
    WHERE rated_user_id = NEW.rated_user_id;

    -- Розрахунок рейтингу (від -100 до +100)
    -- Формула: ((likes - dislikes) / total_ratings) * 100
    IF total_ratings > 0 THEN
        SET calculated_score = ((total_likes - total_dislikes) / total_ratings) * 100;
    END IF;

    -- Визначення рівня довіри
    IF total_ratings < 5 THEN
        SET new_trust_level = 'neutral';
    ELSEIF calculated_score >= 70 AND total_ratings >= 10 THEN
        SET new_trust_level = 'verified';
    ELSEIF calculated_score >= 40 THEN
        SET new_trust_level = 'trusted';
    ELSEIF calculated_score <= -40 THEN
        SET new_trust_level = 'untrusted';
    ELSE
        SET new_trust_level = 'neutral';
    END IF;

    -- Оновлення таблиці користувачів
    UPDATE Wo_Users
    SET
        rating_likes = total_likes,
        rating_dislikes = total_dislikes,
        rating_score = calculated_score,
        trust_level = new_trust_level
    WHERE user_id = NEW.rated_user_id;
END$$

-- Тригер для оновлення при зміні оцінки (like ↔ dislike)
DROP TRIGGER IF EXISTS `update_user_rating_after_update`$$
CREATE TRIGGER `update_user_rating_after_update` AFTER UPDATE ON `Wo_UserRatings`
FOR EACH ROW
BEGIN
    DECLARE total_likes INT DEFAULT 0;
    DECLARE total_dislikes INT DEFAULT 0;
    DECLARE total_ratings INT DEFAULT 0;
    DECLARE calculated_score DECIMAL(4,2) DEFAULT 0.00;
    DECLARE new_trust_level VARCHAR(20) DEFAULT 'neutral';

    SELECT
        SUM(CASE WHEN rating_type = 'like' THEN 1 ELSE 0 END),
        SUM(CASE WHEN rating_type = 'dislike' THEN 1 ELSE 0 END),
        COUNT(*)
    INTO total_likes, total_dislikes, total_ratings
    FROM Wo_UserRatings
    WHERE rated_user_id = NEW.rated_user_id;

    IF total_ratings > 0 THEN
        SET calculated_score = ((total_likes - total_dislikes) / total_ratings) * 100;
    END IF;

    IF total_ratings < 5 THEN
        SET new_trust_level = 'neutral';
    ELSEIF calculated_score >= 70 AND total_ratings >= 10 THEN
        SET new_trust_level = 'verified';
    ELSEIF calculated_score >= 40 THEN
        SET new_trust_level = 'trusted';
    ELSEIF calculated_score <= -40 THEN
        SET new_trust_level = 'untrusted';
    ELSE
        SET new_trust_level = 'neutral';
    END IF;

    UPDATE Wo_Users
    SET
        rating_likes = total_likes,
        rating_dislikes = total_dislikes,
        rating_score = calculated_score,
        trust_level = new_trust_level
    WHERE user_id = NEW.rated_user_id;
END$$

-- Тригер для оновлення при видаленні оцінки
DROP TRIGGER IF EXISTS `update_user_rating_after_delete`$$
CREATE TRIGGER `update_user_rating_after_delete` AFTER DELETE ON `Wo_UserRatings`
FOR EACH ROW
BEGIN
    DECLARE total_likes INT DEFAULT 0;
    DECLARE total_dislikes INT DEFAULT 0;
    DECLARE total_ratings INT DEFAULT 0;
    DECLARE calculated_score DECIMAL(4,2) DEFAULT 0.00;
    DECLARE new_trust_level VARCHAR(20) DEFAULT 'neutral';

    SELECT
        SUM(CASE WHEN rating_type = 'like' THEN 1 ELSE 0 END),
        SUM(CASE WHEN rating_type = 'dislike' THEN 1 ELSE 0 END),
        COUNT(*)
    INTO total_likes, total_dislikes, total_ratings
    FROM Wo_UserRatings
    WHERE rated_user_id = OLD.rated_user_id;

    IF total_ratings > 0 THEN
        SET calculated_score = ((total_likes - total_dislikes) / total_ratings) * 100;
    END IF;

    IF total_ratings < 5 THEN
        SET new_trust_level = 'neutral';
    ELSEIF calculated_score >= 70 AND total_ratings >= 10 THEN
        SET new_trust_level = 'verified';
    ELSEIF calculated_score >= 40 THEN
        SET new_trust_level = 'trusted';
    ELSEIF calculated_score <= -40 THEN
        SET new_trust_level = 'untrusted';
    ELSE
        SET new_trust_level = 'neutral';
    END IF;

    UPDATE Wo_Users
    SET
        rating_likes = GREATEST(0, total_likes),
        rating_dislikes = GREATEST(0, total_dislikes),
        rating_score = calculated_score,
        trust_level = new_trust_level
    WHERE user_id = OLD.rated_user_id;
END$$

DELIMITER ;

-- Створюємо допоміжне VIEW для зручної вибірки рейтингів
CREATE OR REPLACE VIEW `Wo_UserRatingSummary` AS
SELECT
    u.user_id,
    u.username,
    u.first_name,
    u.last_name,
    u.avatar,
    u.rating_likes,
    u.rating_dislikes,
    u.rating_score,
    u.trust_level,
    (u.rating_likes + u.rating_dislikes) as total_ratings,
    CASE
        WHEN u.trust_level = 'verified' THEN 'Перевірений ✅'
        WHEN u.trust_level = 'trusted' THEN 'Надійний ⭐'
        WHEN u.trust_level = 'neutral' THEN 'Нейтральний 🔵'
        WHEN u.trust_level = 'untrusted' THEN 'Ненадійний ⚠️'
        ELSE 'Невідомий'
    END as trust_level_label
FROM Wo_Users u
WHERE u.active = '1';

-- Приклади запитів для тестування:

-- Отримати топ користувачів за рейтингом
-- SELECT * FROM Wo_UserRatingSummary ORDER BY rating_score DESC LIMIT 10;

-- Отримати оцінки конкретного користувача
-- SELECT * FROM Wo_UserRatings WHERE rated_user_id = 123 ORDER BY created_at DESC;

-- Перевірити чи користувач A оцінив користувача B
-- SELECT * FROM Wo_UserRatings WHERE rater_id = 1 AND rated_user_id = 123;
