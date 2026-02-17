package com.worldmates.messenger.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Современные визуальные эффекты для WorldMates Messenger
 * Градиенты, тени и другие футуристичные эффекты
 */

/**
 * Градиенты для фонов и элементов интерфейса
 */
object WMGradients {

    // Основной градиент приложения - электрический синий
    val primaryGradient = Brush.linearGradient(
        colors = listOf(
            GradientStart,      // 0xFF0A84FF - Яркий синий
            GradientMiddle,     // 0xFF00D4FF - Циан
            GradientEnd         // 0xFF5AC8FA - Светло-голубой
        )
    )

    // Вертикальный основной градиент
    val primaryGradientVertical = Brush.verticalGradient(
        colors = listOf(
            GradientStart,
            GradientMiddle,
            GradientEnd
        )
    )

    // Акцентный градиент - фиолетово-голубой
    val accentGradient = Brush.linearGradient(
        colors = listOf(
            AccentGradientStart,  // 0xFF667EEA - Фиолетовый
            AccentGradientEnd     // 0xFF64B5F6 - Голубой
        )
    )

    // Градиент для кнопок - яркий и привлекательный
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(
            WMPrimary,      // 0xFF0A84FF
            WMSecondary     // 0xFF00D4FF
        )
    )

    // Градиент для сообщений - мягкий
    val messageBubbleGradient = Brush.horizontalGradient(
        colors = listOf(
            MessageBubbleOwn,
            MessageBubbleOwn.copy(alpha = 0.9f)
        )
    )

    // Неоновый градиент - футуристичный
    val neonGradient = Brush.linearGradient(
        colors = listOf(
            NeonBlue,       // 0xFF00D4FF
            NeonPurple,     // 0xFFBF00FF
            NeonPink        // 0xFFFF006E
        )
    )

    // Градиент восхода - теплый
    val sunriseGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFF6B35),  // Оранжевый
            Color(0xFFFF8E53),  // Светло-оранжевый
            Color(0xFFFFB347)   // Персиковый
        )
    )

    // Градиент заката - романтичный
    val sunsetGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFE91E63),  // Розовый
            Color(0xFFFF6B9D),  // Светло-розовый
            Color(0xFFFFB74D)   // Золотистый
        )
    )

    // Океанский градиент - прохладный
    val oceanGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF006BA6),  // Темно-синий
            Color(0xFF00A1B8),  // Циан
            Color(0xFF4DC4D4)   // Светло-циан
        )
    )

    // Лесной градиент - природный
    val forestGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1B5E20),  // Темно-зеленый
            Color(0xFF2E7D32),  // Зеленый
            Color(0xFF66BB6A)   // Светло-зеленый
        )
    )

    // Фиолетовый градиент - элегантный
    val purpleGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF4A148C),  // Темно-фиолетовый
            Color(0xFF6A1B9A),  // Фиолетовый
            Color(0xFF9C4DCC)   // Светло-фиолетовый
        )
    )

    // Градиент для темной темы - приглушенный
    val darkGradient = Brush.verticalGradient(
        colors = listOf(
            BackgroundDark,                     // 0xFF0D1117
            BackgroundDark.copy(alpha = 0.95f),
            SurfaceDark                         // 0xFF161B22
        )
    )

    // Градиент для светлой темы - воздушный
    val lightGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFFFFF),  // Белый
            BackgroundLight,    // 0xFFF8F9FA
            Color(0xFFF0F2F5)   // Очень светло-серый
        )
    )

    // Стеклянный эффект (Glassmorphism) - полупрозрачный
    val glassGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.2f),
            Color.White.copy(alpha = 0.1f)
        )
    )

    // Радужный градиент - яркий и игривый
    val rainbowGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFFFF0000),  // Красный
            Color(0xFFFF7F00),  // Оранжевый
            Color(0xFFFFFF00),  // Желтый
            Color(0xFF00FF00),  // Зеленый
            Color(0xFF0000FF),  // Синий
            Color(0xFF4B0082),  // Индиго
            Color(0xFF9400D3)   // Фиолетовый
        )
    )

    // Металлический градиент - премиум вид
    val metallicGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFB4B4B4),  // Серебро светлое
            Color(0xFF8E8E8E),  // Серебро
            Color(0xFFB4B4B4)   // Серебро светлое
        )
    )

    // Золотой градиент - роскошный
    val goldGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFD700),  // Золото
            Color(0xFFFFB700),  // Темное золото
            Color(0xFFFFD700)   // Золото
        )
    )

    // Градиент для карточек - с глубиной
    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            SurfaceLight,
            SurfaceLight.copy(alpha = 0.98f),
            Color(0xFFF8F9FA)
        )
    )

    // Темный градиент для карточек
    val cardGradientDark = Brush.verticalGradient(
        colors = listOf(
            SurfaceDark,
            SurfaceDark.copy(alpha = 0.98f),
            Color(0xFF1C1E21)
        )
    )
}

/**
 * Эффекты тени для современного дизайна
 */
object WMShadows {
    // Небольшая тень для карточек
    val small = 2f

    // Средняя тень для кнопок
    val medium = 4f

    // Большая тень для модальных окон
    val large = 8f

    // Очень большая тень для floating элементов
    val extraLarge = 16f
}

/**
 * Эффекты размытия для glassmorphism
 */
object WMBlur {
    // Легкое размытие
    val light = 10f

    // Среднее размытие
    val medium = 20f

    // Сильное размытие
    val heavy = 30f
}

/**
 * Анимационные параметры для плавных переходов
 */
object WMAnimations {
    // Быстрая анимация (кнопки)
    const val FAST = 150

    // Средняя анимация (переходы)
    const val MEDIUM = 300

    // Медленная анимация (сложные переходы)
    const val SLOW = 500

    // Очень медленная (декоративные эффекты)
    const val VERY_SLOW = 800
}

/**
 * Значения прозрачности для современного дизайна
 */
object WMOpacity {
    // Очень слабая прозрачность (для фонов)
    const val BARELY = 0.05f

    // Слабая прозрачность
    const val LIGHT = 0.1f

    // Средняя прозрачность (для оверлеев)
    const val MEDIUM = 0.3f

    // Сильная прозрачность
    const val HEAVY = 0.5f

    // Очень сильная прозрачность
    const val VERY_HEAVY = 0.7f

    // Почти полная прозрачность
    const val ALMOST_TRANSPARENT = 0.9f
}

/**
 * Эффекты свечения для футуристичного вида
 */
object WMGlow {
    // Мягкое свечение
    val soft = listOf(
        Color.White.copy(alpha = 0.1f),
        Color.Transparent
    )

    // Неоновое свечение - синее
    val neonBlue = listOf(
        NeonBlue.copy(alpha = 0.3f),
        Color.Transparent
    )

    // Неоновое свечение - фиолетовое
    val neonPurple = listOf(
        NeonPurple.copy(alpha = 0.3f),
        Color.Transparent
    )

    // Неоновое свечение - зеленое
    val neonGreen = listOf(
        NeonGreen.copy(alpha = 0.3f),
        Color.Transparent
    )

    // Неоновое свечение - розовое
    val neonPink = listOf(
        NeonPink.copy(alpha = 0.3f),
        Color.Transparent
    )
}
