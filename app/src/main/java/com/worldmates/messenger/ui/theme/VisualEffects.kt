package com.worldmates.messenger.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Варіанти візуальних ефектів для повідомлень
 */
enum class MessageBubbleStyle(
    val displayName: String,
    val emoji: String,
    val description: String
) {
    MODERN(
        displayName = "Сучасний",
        emoji = "✨",
        description = "Елегантний Material Design"
    ),

    GLASS(
        displayName = "Скляний",
        emoji = "🪟",
        description = "Glassmorphism з розмиттям"
    ),

    GRADIENT(
        displayName = "Градієнт",
        emoji = "🌈",
        description = "Яскраві кольорові переходи"
    ),

    NEON(
        displayName = "Неон",
        emoji = "💡",
        description = "Світіння в стилі cyberpunk"
    ),

    SHADOW(
        displayName = "Тіні",
        emoji = "🌑",
        description = "Глибокі м'які тіні"
    ),

    FLAT(
        displayName = "Плоский",
        emoji = "📱",
        description = "Flat Design без ефектів"
    ),

    ROUNDED(
        displayName = "Округлий",
        emoji = "⚪",
        description = "Bubble-style з великим радіусом"
    ),

    MINIMAL(
        displayName = "Мінімал",
        emoji = "⬜",
        description = "iOS стиль з мінімалізмом"
    ),

    RETRO(
        displayName = "Ретро",
        emoji = "📼",
        description = "Вінтажний стиль 80-х"
    ),

    NEUMORPHISM(
        displayName = "Неоморфізм",
        emoji = "🎭",
        description = "М'який 3D-ефект"
    ),

    COMIC(
        displayName = "Комікс",
        emoji = "💥",
        description = "Стиль коміксів з обводкою"
    ),

    FUTURISTIC(
        displayName = "Футуристичний",
        emoji = "🚀",
        description = "Sci-fi дизайн майбутнього"
    );

    companion object {
        fun fromOrdinal(ordinal: Int): MessageBubbleStyle {
            return values().getOrNull(ordinal) ?: MODERN
        }
    }
}

/**
 * Варіанти анімацій для повідомлень
 */
enum class MessageAnimationStyle(
    val displayName: String,
    val emoji: String
) {
    NONE("Без анімації", "⏸️"),
    FADE("Плавна поява", "🌫️"),
    SLIDE("Ковзання", "➡️"),
    SCALE("Масштабування", "🔍"),
    BOUNCE("Підстрибування", "🎾"),
    WAVE("Хвиля", "🌊");

    companion object {
        fun fromOrdinal(ordinal: Int): MessageAnimationStyle {
            return values().getOrNull(ordinal) ?: FADE
        }
    }
}

/**
 * Варіанти шрифтів
 */
enum class FontVariant(
    val displayName: String,
    val emoji: String,
    val description: String
) {
    DEFAULT(
        displayName = "За замовчуванням",
        emoji = "📝",
        description = "Системний шрифт Android"
    ),
    ROBOTO(
        displayName = "Roboto",
        emoji = "🤖",
        description = "Material Design класика"
    ),
    OPEN_SANS(
        displayName = "Open Sans",
        emoji = "📖",
        description = "Гуманістичний і читабельний"
    ),
    LATO(
        displayName = "Lato",
        emoji = "✍️",
        description = "Елегантний sans-serif"
    ),
    MONTSERRAT(
        displayName = "Montserrat",
        emoji = "🎨",
        description = "Геометричний Urban стиль"
    ),
    POPPINS(
        displayName = "Poppins",
        emoji = "✨",
        description = "Геометричний з округленими краями"
    ),
    COMFORTAA(
        displayName = "Comfortaa",
        emoji = "😊",
        description = "М'який і дружній"
    ),
    PACIFICO(
        displayName = "Pacifico",
        emoji = "🌴",
        description = "Серфінг-стиль рукописний"
    ),
    PLAYFAIR(
        displayName = "Playfair Display",
        emoji = "👑",
        description = "Елегантний класичний serif"
    ),
    RALEWAY(
        displayName = "Raleway",
        emoji = "💎",
        description = "Тонкий і витончений"
    ),
    UBUNTU(
        displayName = "Ubuntu",
        emoji = "🐧",
        description = "Технологічний Linux стиль"
    ),
    FIRA_CODE(
        displayName = "Fira Code",
        emoji = "💻",
        description = "Моноширинний для кодерів"
    ),
    SATISFY(
        displayName = "Satisfy",
        emoji = "🎭",
        description = "Каліграфічний рукописний"
    ),
    SHADOWS_INTO_LIGHT(
        displayName = "Shadows Into Light",
        emoji = "✏️",
        description = "Неформальний рукописний"
    ),
    CREEPSTER(
        displayName = "Creepster",
        emoji = "🎃",
        description = "Готичний Horror стиль"
    ),
    SPECIAL_ELITE(
        displayName = "Special Elite",
        emoji = "⌨️",
        description = "Друкарська машинка ретро"
    ),
    ARCHITECTS_DAUGHTER(
        displayName = "Architects Daughter",
        emoji = "📐",
        description = "Архітектурний ескіз"
    ),
    CAVEAT(
        displayName = "Caveat",
        emoji = "🖊️",
        description = "Швидкий рукописний стиль"
    );

    companion object {
        fun fromOrdinal(ordinal: Int): FontVariant {
            return values().getOrNull(ordinal) ?: DEFAULT
        }
    }
}

/**
 * Модифікатор для застосування glass-ефекту
 */
fun Modifier.glassEffect(
    blurRadius: Dp = 10.dp,
    alpha: Float = 0.7f,
    backgroundColor: Color = Color.White
): Modifier = this
    .clip(RoundedCornerShape(20.dp))
    .background(backgroundColor.copy(alpha = alpha))
    .blur(blurRadius)

/**
 * Модифікатор для неонового ефекту
 */
fun Modifier.neonEffect(
    glowColor: Color,
    glowRadius: Dp = 8.dp
): Modifier = this
    .shadow(
        elevation = glowRadius,
        shape = RoundedCornerShape(20.dp),
        spotColor = glowColor,
        ambientColor = glowColor
    )

/**
 * Отримати форму bubble згідно стилю
 */
fun MessageBubbleStyle.getShape(): Shape {
    return when (this) {
        MessageBubbleStyle.ROUNDED -> RoundedCornerShape(28.dp)
        MessageBubbleStyle.MODERN -> RoundedCornerShape(20.dp)
        MessageBubbleStyle.MINIMAL -> RoundedCornerShape(18.dp)
        MessageBubbleStyle.FLAT -> RoundedCornerShape(8.dp)
        MessageBubbleStyle.RETRO -> RoundedCornerShape(4.dp)
        MessageBubbleStyle.COMIC -> RoundedCornerShape(16.dp)
        MessageBubbleStyle.FUTURISTIC -> RoundedCornerShape(topStart = 2.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 2.dp)
        MessageBubbleStyle.NEUMORPHISM -> RoundedCornerShape(24.dp)
        else -> RoundedCornerShape(20.dp)
    }
}

/**
 * Отримати модифікатор для стилю bubble
 */
fun MessageBubbleStyle.getModifier(
    isOwn: Boolean,
    primaryColor: Color,
    secondaryColor: Color = Color.LightGray
): Modifier {
    val baseColor = if (isOwn) primaryColor else secondaryColor

    return when (this) {
        MessageBubbleStyle.GLASS -> Modifier.glassEffect(
            alpha = 0.6f,
            backgroundColor = baseColor
        )

        MessageBubbleStyle.GRADIENT -> Modifier
            .clip(getShape())
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (isOwn) {
                        listOf(primaryColor, primaryColor.copy(alpha = 0.7f))
                    } else {
                        listOf(secondaryColor, secondaryColor.copy(alpha = 0.8f))
                    }
                )
            )

        MessageBubbleStyle.NEON -> Modifier
            .clip(getShape())
            .neonEffect(
                glowColor = if (isOwn) primaryColor else Color.Gray,
                glowRadius = 8.dp
            )
            .background(baseColor)

        MessageBubbleStyle.SHADOW -> Modifier
            .shadow(
                elevation = 8.dp,
                shape = getShape(),
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
            .background(baseColor, shape = getShape())

        MessageBubbleStyle.RETRO -> Modifier
            .clip(getShape())
            .background(
                brush = Brush.linearGradient(
                    colors = if (isOwn) {
                        listOf(Color(0xFFFF6B9D), Color(0xFFC239B3))
                    } else {
                        listOf(Color(0xFF00B4DB), Color(0xFF0083B0))
                    }
                )
            )
            .border(2.dp, Color.Black.copy(alpha = 0.3f), getShape())

        MessageBubbleStyle.NEUMORPHISM -> Modifier
            .shadow(
                elevation = 10.dp,
                shape = getShape(),
                spotColor = Color.White.copy(alpha = 0.8f),
                ambientColor = Color.Black.copy(alpha = 0.1f)
            )
            .background(baseColor, shape = getShape())
            .shadow(
                elevation = -2.dp,
                shape = getShape(),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )

        MessageBubbleStyle.COMIC -> Modifier
            .clip(getShape())
            .background(baseColor)
            .border(3.dp, Color.Black, getShape())

        MessageBubbleStyle.FUTURISTIC -> Modifier
            .clip(getShape())
            .background(
                brush = Brush.linearGradient(
                    colors = if (isOwn) {
                        listOf(Color(0xFF00F5FF), Color(0xFF0099FF), Color(0xFF6600FF))
                    } else {
                        listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                    }
                )
            )
            .graphicsLayer {
                shadowElevation = 4.dp.toPx()
                shape = RoundedCornerShape(topStart = 2.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 2.dp)
            }

        MessageBubbleStyle.FLAT, MessageBubbleStyle.MODERN,
        MessageBubbleStyle.ROUNDED, MessageBubbleStyle.MINIMAL -> Modifier
            .clip(getShape())
            .background(baseColor)
    }
}

/**
 * Composable-wrapper для анімованих bubble
 */
@Composable
fun AnimatedMessageBubble(
    style: MessageBubbleStyle,
    animationStyle: MessageAnimationStyle,
    isOwn: Boolean,
    primaryColor: Color,
    secondaryColor: Color = Color.LightGray,
    content: @Composable () -> Unit
) {
    // Анімація появи
    val animationSpec: AnimationSpec<Float> = when (animationStyle) {
        MessageAnimationStyle.BOUNCE -> spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
        MessageAnimationStyle.SCALE, MessageAnimationStyle.FADE -> tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        )
        MessageAnimationStyle.SLIDE -> tween(
            durationMillis = 250,
            easing = FastOutSlowInEasing
        )
        MessageAnimationStyle.WAVE -> tween(
            durationMillis = 400,
            easing = LinearOutSlowInEasing
        )
        else -> snap()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "message_animation")

    Box(
        modifier = style.getModifier(isOwn, primaryColor, secondaryColor)
    ) {
        content()
    }
}

/**
 * Пульсуючий ефект для typing indicator
 */
@Composable
fun PulsingDot(color: Color = Color.Gray, size: Dp = 8.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
    )
}