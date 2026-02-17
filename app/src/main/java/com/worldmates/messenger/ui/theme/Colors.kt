package com.worldmates.messenger.ui.theme

import androidx.compose.ui.graphics.Color

// Основные цвета бренда WorldMates - Современный iOS/Telegram стиль
val WMPrimary = Color(0xFF0A84FF)  // Яркий электрический синий
val WMPrimaryDark = Color(0xFF0040DD)  // Глубокий синий
val WMPrimaryLight = Color(0xFF5AC8FA)  // Светло-голубой

val WMSecondary = Color(0xFF00D4FF)  // Яркий циан
val WMSecondaryDark = Color(0xFF00A0C8)  // Темный циан
val WMSecondaryLight = Color(0xFF64D2FF)  // Светлый циан

// Фоновые цвета - Современные градации
val BackgroundLight = Color(0xFFF8F9FA)  // Очень светло-серый
val BackgroundDark = Color(0xFF0D1117)  // Глубокий темный (GitHub dark)

val SurfaceLight = Color(0xFFFFFFFF)  // Чистый белый
val SurfaceDark = Color(0xFF161B22)  // Темная поверхность

// Цвета для сообщений - Современные с градиентом
val MessageBubbleOwn = Color(0xFF0A84FF)  // Синий iOS-стиль
val MessageBubbleOther = Color(0xFFF0F2F5)  // Светло-серый
val MessageBubbleOwnDark = Color(0xFF0A84FF)  // Синий даже в темной теме
val MessageBubbleOtherDark = Color(0xFF262C35)  // Темно-серый

// Текстовые цвета - Контрастные
val TextPrimary = Color(0xFF1C1E21)  // Почти черный
val TextSecondary = Color(0xFF65676B)  // Средне-серый
val TextTertiary = Color(0xFF8A8D91)  // Светло-серый

val TextPrimaryDark = Color(0xFFF0F2F5)  // Светлый текст
val TextSecondaryDark = Color(0xFFB0B3B8)  // Средне-светлый
val TextTertiaryDark = Color(0xFF8A8D91)  // Серый

// Статусные цвета - Яркие и заметные
val Success = Color(0xFF00C851)  // Зеленый успех
val Warning = Color(0xFFFFBB33)  // Янтарный
val Error = Color(0xFFFF4444)  // Красный
val Info = Color(0xFF33B5E5)  // Синий информация

// Цвета для онлайн статуса - Яркие индикаторы
val OnlineGreen = Color(0xFF00C851)  // Яркий зеленый
val AwayYellow = Color(0xFFFFBB33)  // Янтарный
val BusyRed = Color(0xFFFF4444)  // Красный
val OfflineGray = Color(0xFF8A8D91)  // Серый

// Разделители и границы - Тонкие и ненавязчивые
val Divider = Color(0xFFE4E6EB)  // Светлый разделитель
val DividerDark = Color(0xFF2F3336)  // Темный разделитель

// Цвета для различных элементов UI - Современные акценты
val UnreadBadge = Color(0xFFFF4444)  // Красный значок
val TypingIndicator = Color(0xFF0A84FF)  // Синий индикатор
val SearchBarBackground = Color(0xFFF0F2F5)  // Светлый фон поиска
val SearchBarBackgroundDark = Color(0xFF262C35)  // Темный фон поиска

// Прозрачные цвета для оверлеев - Современное размытие
val Overlay = Color(0xCC000000)  // Полупрозрачный черный
val OverlayLight = Color(0x66000000)  // Легкая затемненность

// Цвета для кнопок - Яркие и привлекательные
val ButtonEnabled = Color(0xFF0A84FF)  // Синий
val ButtonDisabled = Color(0xFFE4E6EB)  // Светло-серый
val ButtonEnabledDark = Color(0xFF5AC8FA)  // Светло-синий
val ButtonDisabledDark = Color(0xFF3A3A3C)  // Темно-серый

// Цвета для карточек - С тенью и глубиной
val CardBackground = Color(0xFFFFFFFF)  // Белый
val CardBackgroundDark = Color(0xFF1C1E21)  // Темный
val CardElevation = Color(0x1A000000)  // Тень карточки

// Цвета для индикаторов прочтения - Современные статусы
val MessageRead = Color(0xFF00C851)  // Зеленый - прочитано
val MessageDelivered = Color(0xFF8A8D91)  // Серый - доставлено
val MessageSent = Color(0xFFB0B3B8)  // Светло-серый - отправлено

// Градиенты для эффектов - Футуристичные переходы
val GradientStart = Color(0xFF0A84FF)  // Синий
val GradientMiddle = Color(0xFF00D4FF)  // Циан
val GradientEnd = Color(0xFF5AC8FA)  // Светло-голубой

// Акцентные градиенты
val AccentGradientStart = Color(0xFF667EEA)  // Фиолетовый
val AccentGradientEnd = Color(0xFF64B5F6)  // Голубой

// Цвета для групп - Яркая палитра
val GroupAvatarColors = listOf(
    Color(0xFFFF6B9D),  // Розовый
    Color(0xFFC446FF),  // Пурпурный
    Color(0xFF0084FF),  // Синий
    Color(0xFF00C0FF),  // Циан
    Color(0xFF00E0A6),  // Мятный
    Color(0xFF00C851),  // Зеленый
    Color(0xFFFFC107),  // Желтый
    Color(0xFFFF9500),  // Оранжевый
    Color(0xFFFF6347),  // Красный
    Color(0xFFFF1744),  // Алый
    Color(0xFF9C27B0),  // Фиолетовый
    Color(0xFF3F51B5),  // Индиго
    Color(0xFF00BCD4),  // Бирюзовый
    Color(0xFF4CAF50),  // Зеленый лайм
    Color(0xFFFF5722)   // Глубокий оранжевый
)

// Цвета для типов медиа - Яркие иконки
val MediaImage = Color(0xFF00C851)  // Зеленый
val MediaVideo = Color(0xFF0A84FF)  // Синий
val MediaAudio = Color(0xFFFF9500)  // Оранжевый
val MediaFile = Color(0xFF8E8E93)  // Серый

// Shimmer эффект для загрузки - Современный плавный переход
val ShimmerColorShades = listOf(
    Color(0xFFE4E6EB),  // Светлый
    Color(0xFFF0F2F5),  // Очень светлый
    Color(0xFFE4E6EB)   // Светлый
)

// Дополнительные футуристичные цвета
val NeonBlue = Color(0xFF00D4FF)  // Неоновый синий
val NeonPurple = Color(0xFFBF00FF)  // Неоновый фиолетовый
val NeonGreen = Color(0xFF00FF88)  // Неоновый зеленый
val NeonPink = Color(0xFFFF006E)  // Неоновый розовый
