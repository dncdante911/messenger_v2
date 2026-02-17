-- ============================================================
-- Group Customization Theme Columns for Wo_GroupChat
-- Додає колонки для кастомних тем групових чатів
-- Ці колонки зберігають візуальні налаштування кожної групи
-- ============================================================

-- Стиль бульбашок повідомлень (STANDARD, COMIC, TELEGRAM, MINIMAL, MODERN, RETRO, GLASS, NEON, GRADIENT, NEUMORPHISM)
ALTER TABLE Wo_GroupChat ADD COLUMN IF NOT EXISTS theme_bubble_style VARCHAR(50) DEFAULT 'STANDARD';

-- Пресет фону (ocean, sunset, lavender, cosmic, forest, peach, aurora, midnight, fire, spring_meadow, cotton_candy, deep_space, spring, neon_city, winter)
ALTER TABLE Wo_GroupChat ADD COLUMN IF NOT EXISTS theme_preset_background VARCHAR(50) DEFAULT 'ocean';

-- Акцентний колір у форматі HEX (#2196F3)
ALTER TABLE Wo_GroupChat ADD COLUMN IF NOT EXISTS theme_accent_color VARCHAR(10) DEFAULT '#2196F3';

-- Чи ввімкнена кастомна тема адміністратором
ALTER TABLE Wo_GroupChat ADD COLUMN IF NOT EXISTS theme_enabled_by_admin TINYINT(1) DEFAULT 1;

-- Часова мітка останнього оновлення теми (UNIX timestamp)
ALTER TABLE Wo_GroupChat ADD COLUMN IF NOT EXISTS theme_updated_at INT DEFAULT 0;

-- ID користувача, який останній раз оновив тему
ALTER TABLE Wo_GroupChat ADD COLUMN IF NOT EXISTS theme_updated_by INT DEFAULT 0;

-- Індекс для швидкого пошуку груп з кастомними темами
CREATE INDEX IF NOT EXISTS idx_group_theme_updated ON Wo_GroupChat (theme_updated_at);
