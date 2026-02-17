package com.worldmates.messenger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Формы для современного футуристичного дизайна
 * Увеличенные скругления в стиле iOS/Telegram для более мягкого и современного вида
 */
val Shapes = Shapes(
    // Экстрамалые формы (чипы, бейджи) - увеличены для мягкости
    extraSmall = RoundedCornerShape(6.dp),

    // Малые формы (кнопки, карточки сообщений) - более округлые
    small = RoundedCornerShape(14.dp),

    // Средние формы (диалоги, bottom sheets) - современные скругления
    medium = RoundedCornerShape(18.dp),

    // Большие формы (модальные окна, карточки медиа) - выразительные
    large = RoundedCornerShape(24.dp),

    // Экстрабольшие формы (полноэкранные карточки) - максимально мягкие
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Кастомные формы для специфичных элементов - Современный футуристичный стиль
 */
object WMShapes {
    // Собственные сообщения (скруглены все углы кроме правого нижнего) - увеличены для мягкости
    val ownMessageBubble = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 6.dp  // Небольшой "хвостик" для указания направления
    )

    // Чужие сообщения (скруглены все углы кроме левого нижнего) - увеличены для мягкости
    val otherMessageBubble = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 6.dp,  // Небольшой "хвостик" для указания направления
        bottomEnd = 20.dp
    )

    // Голосовое сообщение - более округлое для выделения
    val voiceMessage = RoundedCornerShape(24.dp)

    // Медиа-сообщения (фото/видео) - современные скругления
    val mediaMessage = RoundedCornerShape(16.dp)

    // Файлы - увеличенные скругления
    val fileAttachment = RoundedCornerShape(16.dp)

    // Аватар пользователя - идеально круглый
    val avatar = RoundedCornerShape(50) // Круглый

    // Аватар группы - более округлый квадрат
    val groupAvatar = RoundedCornerShape(20.dp) // Скруглённый квадрат

    // Поле ввода сообщения - pill-образная форма
    val messageInput = RoundedCornerShape(26.dp)

    // Кнопки - современные скругления
    val button = RoundedCornerShape(14.dp)
    val roundButton = RoundedCornerShape(50) // Круглая кнопка
    val pillButton = RoundedCornerShape(28.dp) // Pill-образная кнопка

    // Карточка чата в списке - минимальное скругление для чистоты списка
    val chatCard = RoundedCornerShape(0.dp) // Без скругления (как в Telegram)

    // Карточка группы - увеличенные скругления
    val groupCard = RoundedCornerShape(20.dp)

    // Диалоговые окна - максимально мягкие
    val dialog = RoundedCornerShape(32.dp)

    // Bottom Sheet - выразительные верхние углы
    val bottomSheet = RoundedCornerShape(
        topStart = 32.dp,
        topEnd = 32.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    // Бейдж непрочитанных - pill-образный
    val unreadBadge = RoundedCornerShape(14.dp)

    // Индикатор "печатает" - мягкие скругления
    val typingIndicator = RoundedCornerShape(10.dp)

    // Превью медиа - современные скругления
    val mediaPreview = RoundedCornerShape(12.dp)

    // Реакции на сообщения - pill-образные
    val reaction = RoundedCornerShape(18.dp)

    // Панель выбора эмодзи - мягкие верхние углы
    val emojiPanel = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    // Карточки настроек - современные скругления
    val settingsCard = RoundedCornerShape(18.dp)

    // Модальные карточки - выразительные скругления
    val modalCard = RoundedCornerShape(24.dp)

    // Поисковая строка - pill-образная
    val searchBar = RoundedCornerShape(28.dp)

    // Чипы и теги - мягкие скругления
    val chip = RoundedCornerShape(12.dp)
}
