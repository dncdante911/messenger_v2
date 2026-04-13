package com.worldmates.messenger.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Варианты тем для WorldMates Messenger.
 *
 * isPremium          = доступно с PRO-подпиской (или 5 пробных дней)
 * isSubscriptionOnly = эксклюзивно для подписчиков (особое оформление)
 */
enum class ThemeVariant(
    val displayName: String,
    val emoji: String,
    val description: String,
    val isPremium: Boolean = false,
    val isSubscriptionOnly: Boolean = false
) {
    // ── 7 базовых тем ────────────────────────────────────────────────────────

    CLASSIC(
        displayName = "Classic Blue",
        emoji = "💙",
        description = "Классическая синяя тема в стиле Messenger"
    ),

    OCEAN(
        displayName = "Deep Ocean",
        emoji = "🌊",
        description = "Глубокие океанские оттенки синего и бирюзового"
    ),

    PURPLE(
        displayName = "Violet Dream",
        emoji = "💜",
        description = "Насыщенные фиолетовые тона с приятным контрастом"
    ),

    MONOCHROME(
        displayName = "Dark Slate",
        emoji = "🖤",
        description = "Тёмный сланцевый — мягкий контраст, лёгкое чтение"
    ),

    NORD(
        displayName = "Nord Frost",
        emoji = "❄️",
        description = "Холодные северные оттенки Nord палитры"
    ),

    DRACULA(
        displayName = "Dracula Night",
        emoji = "🦇",
        description = "Темная тема с яркими акцентами"
    ),

    MATERIAL_YOU(
        displayName = "Material You",
        emoji = "🎨",
        description = "Динамические цвета из обоев (Android 12+)"
    ),

    // ── 5 PRO-тем по мотивам культовых фильмов ───────────────────────────────

    STRANGER_THINGS(
        displayName = "Stranger Things",
        emoji = "🔴",
        description = "80-е, Перевёрнутый мир, красное свечение",
        isPremium = true
    ),

    LORD_OF_THE_RINGS(
        displayName = "The Lord of the Rings",
        emoji = "💍",
        description = "Средиземье: золото, тёмная магия и эпос",
        isPremium = true
    ),

    TERMINATOR(
        displayName = "Terminator",
        emoji = "⚙️",
        description = "Стальной киборг, красный взгляд, тёмное будущее",
        isPremium = true
    ),

    SUPERNATURAL(
        displayName = "Supernatural",
        emoji = "🌙",
        description = "Охотники на монстров: янтарный огонь в кромешной тьме",
        isPremium = true
    ),

    MARVEL(
        displayName = "Marvel",
        emoji = "⭐",
        description = "Красно-золотой героизм вселенной Marvel",
        isPremium = true
    ),

    // ── 5 эксклюзивных тем для подписки ──────────────────────────────────────

    CYBERPUNK(
        displayName = "Cyberpunk 2077",
        emoji = "⚡",
        description = "Неоново-жёлтый киберпанк ночного города",
        isPremium = true,
        isSubscriptionOnly = true
    ),

    INTERSTELLAR(
        displayName = "Interstellar",
        emoji = "🌌",
        description = "Бесконечный космос, золото звёзд и тишина",
        isPremium = true,
        isSubscriptionOnly = true
    ),

    HARRY_POTTER(
        displayName = "Harry Potter",
        emoji = "🪄",
        description = "Хогвартс: бордовое золото магического мира",
        isPremium = true,
        isSubscriptionOnly = true
    ),

    DUNE(
        displayName = "Dune",
        emoji = "🏜️",
        description = "Пустыня Арракис: песок, пряность и мощь",
        isPremium = true,
        isSubscriptionOnly = true
    ),

    DEMON_SLAYER(
        displayName = "Demon Slayer",
        emoji = "🌸",
        description = "Клинок, рассекающий демонов: чёрный и алый",
        isPremium = true,
        isSubscriptionOnly = true
    );

    companion object {
        fun fromOrdinal(ordinal: Int): ThemeVariant {
            return entries.getOrNull(ordinal) ?: CLASSIC
        }

        fun fromName(name: String): ThemeVariant {
            return entries.find { it.name == name } ?: CLASSIC
        }
    }
}

/**
 * Палитра цветов для каждой темы.
 */
data class ThemePalette(
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val secondary: Color,
    val secondaryDark: Color,
    val secondaryLight: Color,
    val messageBubbleOwn: Color,
    val messageBubbleOther: Color,
    val accent: Color,
    val backgroundGradient: Brush
)

/**
 * Получить палитру для конкретного варианта темы.
 */
fun ThemeVariant.getPalette(): ThemePalette = when (this) {

    // ── Classic Blue ──────────────────────────────────────────────────────────
    ThemeVariant.CLASSIC -> ThemePalette(
        primary        = Color(0xFF1565C0),
        primaryDark    = Color(0xFF003C8F),
        primaryLight   = Color(0xFF5E92F3),
        secondary      = Color(0xFF0288D1),
        secondaryDark  = Color(0xFF005B96),
        secondaryLight = Color(0xFF4FC3F7),
        messageBubbleOwn   = Color(0xFF1976D2),
        messageBubbleOther = Color(0xFFECEFF1),
        accent = Color(0xFF4FC3F7),
        // Плавный диагональный градиент без центрального свечения
        backgroundGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF0D1B3E),
                Color(0xFF1A3A6B),
                Color(0xFF1565C0),
                Color(0xFF1E88E5)
            )
        )
    )

    // ── Deep Ocean ────────────────────────────────────────────────────────────
    ThemeVariant.OCEAN -> ThemePalette(
        primary        = Color(0xFF00695C),
        primaryDark    = Color(0xFF004D40),
        primaryLight   = Color(0xFF4DB6AC),
        secondary      = Color(0xFF0277BD),
        secondaryDark  = Color(0xFF01579B),
        secondaryLight = Color(0xFF4FC3F7),
        messageBubbleOwn   = Color(0xFF00796B),
        messageBubbleOther = Color(0xFFE0F2F1),
        accent = Color(0xFF1DE9B6),
        // Плавный переход от глубины к поверхности — без центрального пятна
        backgroundGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF001A12),
                Color(0xFF003D2E),
                Color(0xFF005B4A),
                Color(0xFF00796B)
            )
        )
    )

    // ── Violet Dream ──────────────────────────────────────────────────────────
    ThemeVariant.PURPLE -> ThemePalette(
        primary        = Color(0xFF7B1FA2),
        primaryDark    = Color(0xFF4A0072),
        primaryLight   = Color(0xFFBA68C8),
        secondary      = Color(0xFF8E24AA),
        secondaryDark  = Color(0xFF5C006D),
        secondaryLight = Color(0xFFCE93D8),
        messageBubbleOwn   = Color(0xFF7B1FA2),
        // Пузырь собеседника — не белый, а мягкий лавандовый для читаемости
        messageBubbleOther = Color(0xFFF3E5F5),
        accent = Color(0xFFE040FB),
        backgroundGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF12002B),
                Color(0xFF2A0050),
                Color(0xFF4A0072),
                Color(0xFF7B1FA2)
            )
        )
    )

    // ── Dark Slate (Monochrome) ───────────────────────────────────────────────
    // Заменен чистый чёрный → тёмно-серый сланец, текст остаётся читаемым
    ThemeVariant.MONOCHROME -> ThemePalette(
        primary        = Color(0xFF455A64),
        primaryDark    = Color(0xFF263238),
        primaryLight   = Color(0xFF78909C),
        secondary      = Color(0xFF546E7A),
        secondaryDark  = Color(0xFF37474F),
        secondaryLight = Color(0xFF90A4AE),
        messageBubbleOwn   = Color(0xFF37474F),
        // Светло-серый вместо чистого белого — нет слепящего контраста
        messageBubbleOther = Color(0xFFECEFF1),
        accent = Color(0xFF78909C),
        backgroundGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF0F1417),
                Color(0xFF1C262B),
                Color(0xFF263238),
                Color(0xFF37474F)
            )
        )
    )

    // ── Nord Frost ────────────────────────────────────────────────────────────
    ThemeVariant.NORD -> ThemePalette(
        primary        = Color(0xFF5E81AC),
        primaryDark    = Color(0xFF4C566A),
        primaryLight   = Color(0xFF81A1C1),
        secondary      = Color(0xFF88C0D0),
        secondaryDark  = Color(0xFF8FBCBB),
        secondaryLight = Color(0xFFD8DEE9),
        messageBubbleOwn   = Color(0xFF5E81AC),
        messageBubbleOther = Color(0xFFECEFF4),
        accent = Color(0xFF88C0D0),
        backgroundGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF2E3440),
                Color(0xFF3B4252),
                Color(0xFF434C5E),
                Color(0xFF4C566A)
            )
        )
    )

    // ── Dracula Night ─────────────────────────────────────────────────────────
    ThemeVariant.DRACULA -> ThemePalette(
        primary        = Color(0xFFBD93F9),
        primaryDark    = Color(0xFF9B6EE8),
        primaryLight   = Color(0xFFD4B5FF),
        secondary      = Color(0xFFFF79C6),
        secondaryDark  = Color(0xFFFF5AC8),
        secondaryLight = Color(0xFFFFB3E5),
        messageBubbleOwn   = Color(0xFFBD93F9),
        messageBubbleOther = Color(0xFF44475A),
        accent = Color(0xFF50FA7B),
        backgroundGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1E1F29),
                Color(0xFF282A36),
                Color(0xFF373844),
                Color(0xFF44475A)
            )
        )
    )

    // ── Material You ──────────────────────────────────────────────────────────
    ThemeVariant.MATERIAL_YOU -> ThemePalette(
        primary        = Color(0xFF6750A4),
        primaryDark    = Color(0xFF4F378B),
        primaryLight   = Color(0xFF9A82DB),
        secondary      = Color(0xFF625B71),
        secondaryDark  = Color(0xFF4A4458),
        secondaryLight = Color(0xFF938F99),
        messageBubbleOwn   = Color(0xFF6750A4),
        messageBubbleOther = Color(0xFFE8DEF8),
        accent = Color(0xFF6750A4),
        backgroundGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1C1B1F),
                Color(0xFF2B2930),
                Color(0xFF3A3740),
                Color(0xFF49454F)
            )
        )
    )

    // ── Stranger Things ───────────────────────────────────────────────────────
    ThemeVariant.STRANGER_THINGS -> ThemePalette(
        primary        = Color(0xFFD32F2F),
        primaryDark    = Color(0xFF7F0000),
        primaryLight   = Color(0xFFEF5350),
        secondary      = Color(0xFFFF6D00),
        secondaryDark  = Color(0xFFBF360C),
        secondaryLight = Color(0xFFFF9100),
        messageBubbleOwn   = Color(0xFF8B0000),
        messageBubbleOther = Color(0xFF1A0A0A),
        accent = Color(0xFFFF1744),
        backgroundGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF050005),
                Color(0xFF0D0008),
                Color(0xFF1A000A),
                Color(0xFF2A0010)
            )
        )
    )

    // ── Lord of the Rings ─────────────────────────────────────────────────────
    ThemeVariant.LORD_OF_THE_RINGS -> ThemePalette(
        primary        = Color(0xFFB8860B),
        primaryDark    = Color(0xFF6B4E0A),
        primaryLight   = Color(0xFFDAA520),
        secondary      = Color(0xFF8B7355),
        secondaryDark  = Color(0xFF5C4A28),
        secondaryLight = Color(0xFFCDAA7D),
        messageBubbleOwn   = Color(0xFF6B4E0A),
        messageBubbleOther = Color(0xFF1C1408),
        accent = Color(0xFFDAA520),
        backgroundGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0C0902),
                Color(0xFF1A1204),
                Color(0xFF2A1E08),
                Color(0xFF3A2B0A)
            )
        )
    )

    // ── Terminator ────────────────────────────────────────────────────────────
    ThemeVariant.TERMINATOR -> ThemePalette(
        primary        = Color(0xFFB71C1C),
        primaryDark    = Color(0xFF7F0000),
        primaryLight   = Color(0xFFEF5350),
        secondary      = Color(0xFF607D8B),
        secondaryDark  = Color(0xFF37474F),
        secondaryLight = Color(0xFF90A4AE),
        messageBubbleOwn   = Color(0xFF8B0000),
        messageBubbleOther = Color(0xFF1A1A1A),
        accent = Color(0xFFFF1744),
        backgroundGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF050505),
                Color(0xFF0D0D0D),
                Color(0xFF181818),
                Color(0xFF1F1208)
            )
        )
    )

    // ── Supernatural ──────────────────────────────────────────────────────────
    ThemeVariant.SUPERNATURAL -> ThemePalette(
        primary        = Color(0xFFC8860A),
        primaryDark    = Color(0xFF7A5100),
        primaryLight   = Color(0xFFE6A820),
        secondary      = Color(0xFF5D4037),
        secondaryDark  = Color(0xFF3E2723),
        secondaryLight = Color(0xFF8D6E63),
        messageBubbleOwn   = Color(0xFF7A5100),
        messageBubbleOther = Color(0xFF1C1208),
        accent = Color(0xFFFFAB00),
        backgroundGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF060402),
                Color(0xFF120C04),
                Color(0xFF1E1406),
                Color(0xFF2A1C08)
            )
        )
    )

    // ── Marvel ────────────────────────────────────────────────────────────────
    ThemeVariant.MARVEL -> ThemePalette(
        primary        = Color(0xFFC62828),
        primaryDark    = Color(0xFF8B0000),
        primaryLight   = Color(0xFFEF5350),
        secondary      = Color(0xFFFFAB00),
        secondaryDark  = Color(0xFFE65100),
        secondaryLight = Color(0xFFFFD54F),
        messageBubbleOwn   = Color(0xFF8B0000),
        messageBubbleOther = Color(0xFF0A0A1A),
        accent = Color(0xFFFFD740),
        backgroundGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF050510),
                Color(0xFF0A0A20),
                Color(0xFF100020),
                Color(0xFF1A0010)
            )
        )
    )

    // ── Cyberpunk 2077 ────────────────────────────────────────────────────────
    ThemeVariant.CYBERPUNK -> ThemePalette(
        primary        = Color(0xFFF5E642),
        primaryDark    = Color(0xFFC0B000),
        primaryLight   = Color(0xFFFFFF6B),
        secondary      = Color(0xFFFF2D78),
        secondaryDark  = Color(0xFFAA0040),
        secondaryLight = Color(0xFFFF80AB),
        messageBubbleOwn   = Color(0xFFB8AD00),
        messageBubbleOther = Color(0xFF0A0010),
        accent = Color(0xFF00E5FF),
        backgroundGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF02000F),
                Color(0xFF08001A),
                Color(0xFF10002A),
                Color(0xFF0A0015)
            )
        )
    )

    // ── Interstellar ──────────────────────────────────────────────────────────
    ThemeVariant.INTERSTELLAR -> ThemePalette(
        primary        = Color(0xFFDAA520),
        primaryDark    = Color(0xFF8B6914),
        primaryLight   = Color(0xFFFFD700),
        secondary      = Color(0xFF4A5568),
        secondaryDark  = Color(0xFF2D3748),
        secondaryLight = Color(0xFF718096),
        messageBubbleOwn   = Color(0xFF6B5010),
        messageBubbleOther = Color(0xFF080810),
        accent = Color(0xFFFFE066),
        backgroundGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF010106),
                Color(0xFF02020C),
                Color(0xFF040412),
                Color(0xFF060618)
            )
        )
    )

    // ── Harry Potter ──────────────────────────────────────────────────────────
    ThemeVariant.HARRY_POTTER -> ThemePalette(
        primary        = Color(0xFFA50000),
        primaryDark    = Color(0xFF5B0000),
        primaryLight   = Color(0xFFD32F2F),
        secondary      = Color(0xFFB8860B),
        secondaryDark  = Color(0xFF7A5B00),
        secondaryLight = Color(0xFFDAA520),
        messageBubbleOwn   = Color(0xFF5B0000),
        messageBubbleOther = Color(0xFF120A00),
        accent = Color(0xFFFFD700),
        backgroundGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF050105),
                Color(0xFF0D050D),
                Color(0xFF180A18),
                Color(0xFF220F22)
            )
        )
    )

    // ── Dune ──────────────────────────────────────────────────────────────────
    ThemeVariant.DUNE -> ThemePalette(
        primary        = Color(0xFFD4A017),
        primaryDark    = Color(0xFF8B6914),
        primaryLight   = Color(0xFFECC44A),
        secondary      = Color(0xFF8B4513),
        secondaryDark  = Color(0xFF5C2E0A),
        secondaryLight = Color(0xFFCD853F),
        messageBubbleOwn   = Color(0xFF7A5800),
        messageBubbleOther = Color(0xFF1A0E00),
        accent = Color(0xFFFFD18C),
        backgroundGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF0F0800),
                Color(0xFF1E1000),
                Color(0xFF2E1A00),
                Color(0xFF3A2200)
            )
        )
    )

    // ── Demon Slayer ──────────────────────────────────────────────────────────
    ThemeVariant.DEMON_SLAYER -> ThemePalette(
        primary        = Color(0xFFD32F2F),
        primaryDark    = Color(0xFF8B0000),
        primaryLight   = Color(0xFFEF9A9A),
        secondary      = Color(0xFF00796B),
        secondaryDark  = Color(0xFF004D40),
        secondaryLight = Color(0xFF4DB6AC),
        messageBubbleOwn   = Color(0xFF8B0000),
        messageBubbleOther = Color(0xFF060A0A),
        accent = Color(0xFFFF8A80),
        backgroundGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF020404),
                Color(0xFF050A08),
                Color(0xFF080E0C),
                Color(0xFF0A1210)
            )
        )
    )
}
