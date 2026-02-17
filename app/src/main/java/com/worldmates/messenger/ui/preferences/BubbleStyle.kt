package com.worldmates.messenger.ui.preferences

/**
 * 🎨 Стилі бульбашок повідомлень
 */
enum class BubbleStyle(
    val displayName: String,
    val description: String,
    val icon: String
) {
    STANDARD(
        displayName = "Стандарт",
        description = "Заокруглені бульбашки з м'якими тінями",
        icon = "💬"
    ),
    COMIC(
        displayName = "Комікс",
        description = "Бульбашки з хвостиком як в коміксах",
        icon = "💭"
    ),
    TELEGRAM(
        displayName = "Класичний",
        description = "Мінімалістичні кутасті бульбашки",
        icon = "📱"
    ),
    MINIMAL(
        displayName = "Мінімал",
        description = "Прості бульбашки без тіней",
        icon = "⚪"
    ),
    MODERN(
        displayName = "Модерн",
        description = "Градієнти та glass morphism ефекти",
        icon = "✨"
    ),
    RETRO(
        displayName = "Ретро",
        description = "Яскраві кольори з товстими рамками",
        icon = "🔴"
    ),
    GLASS(
        displayName = "Скляний",
        description = "Glassmorphism з напівпрозорістю",
        icon = "🪟"
    ),
    NEON(
        displayName = "Неон",
        description = "Cyberpunk світіння по контуру",
        icon = "💡"
    ),
    GRADIENT(
        displayName = "Градієнт",
        description = "Яскраві кольорові переходи",
        icon = "🌈"
    ),
    NEUMORPHISM(
        displayName = "Неоморфізм",
        description = "М'який 3D-ефект з тінями",
        icon = "🎭"
    );

    companion object {
        fun fromOrdinal(ordinal: Int): BubbleStyle {
            return values().getOrElse(ordinal) { STANDARD }
        }
    }
}
