package com.worldmates.messenger.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.ui.preferences.BubbleStyle

/**
 * 🎨 СТАНДАРТНИЙ СТИЛЬ - заокруглені бульбашки
 */
@Composable
fun StandardBubble(
    isOwn: Boolean,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = if (isOwn) {
            RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 4.dp
            )
        } else {
            RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            )
        },
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isOwn) 3.dp else 1.dp,
            pressedElevation = 1.dp
        )
    ) {
        Box {
            // Subtle inner top highlight for own messages — adds visual depth
            if (isOwn) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                content = content
            )
        }
    }
}

/**
 * 💭 КОМІКС СТИЛЬ - з хвостиком як в коміксах
 */
@Composable
fun ComicBubble(
    isOwn: Boolean,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // Форма з хвостиком
    val bubbleShape = GenericShape { size, _ ->
        val tailWidth = 20f
        val tailHeight = 15f
        val cornerRadius = 40f

        if (isOwn) {
            // Хвостик справа знизу
            moveTo(cornerRadius, 0f)
            lineTo(size.width - cornerRadius, 0f)
            quadraticBezierTo(size.width, 0f, size.width, cornerRadius)
            lineTo(size.width, size.height - cornerRadius - tailHeight)
            quadraticBezierTo(size.width, size.height - tailHeight, size.width - cornerRadius, size.height - tailHeight)
            lineTo(size.width - 40f, size.height - tailHeight)
            lineTo(size.width - 10f, size.height)
            lineTo(size.width - 50f, size.height - tailHeight)
            lineTo(cornerRadius, size.height - tailHeight)
            quadraticBezierTo(0f, size.height - tailHeight, 0f, size.height - cornerRadius - tailHeight)
            lineTo(0f, cornerRadius)
            quadraticBezierTo(0f, 0f, cornerRadius, 0f)
        } else {
            // Хвостик зліва знизу
            moveTo(cornerRadius, 0f)
            lineTo(size.width - cornerRadius, 0f)
            quadraticBezierTo(size.width, 0f, size.width, cornerRadius)
            lineTo(size.width, size.height - cornerRadius - tailHeight)
            quadraticBezierTo(size.width, size.height - tailHeight, size.width - cornerRadius, size.height - tailHeight)
            lineTo(50f, size.height - tailHeight)
            lineTo(10f, size.height)
            lineTo(40f, size.height - tailHeight)
            lineTo(cornerRadius, size.height - tailHeight)
            quadraticBezierTo(0f, size.height - tailHeight, 0f, size.height - cornerRadius - tailHeight)
            lineTo(0f, cornerRadius)
            quadraticBezierTo(0f, 0f, cornerRadius, 0f)
        }
        close()
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isOwn) 5.dp else 2.dp,
                shape = bubbleShape,
                spotColor = bgColor.copy(alpha = if (isOwn) 0.5f else 0.15f)
            )
            .clip(bubbleShape)
            .background(bgColor)
    ) {
        if (isOwn) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                        )
                    )
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            content = content
        )
    }
}

/**
 * 📱 TELEGRAM СТИЛЬ - мінімалістичні кутасті
 */
@Composable
fun TelegramBubble(
    isOwn: Boolean,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val tgShape = RoundedCornerShape(
        topStart = 14.dp,
        topEnd = 14.dp,
        bottomStart = if (isOwn) 14.dp else 2.dp,
        bottomEnd = if (isOwn) 2.dp else 14.dp
    )
    // Flat — no shadow, no border, intentionally "blank slate" like real Telegram
    Box(
        modifier = modifier
            .clip(tgShape)
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(content = content)
    }
}

/**
 * ⚪ МІНІМАЛ СТИЛЬ - прості без тіней
 */
@Composable
fun MinimalBubble(
    isOwn: Boolean,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // Zero shadow, zero border — the purest possible style
    Row(modifier = modifier) {
        if (!isOwn) {
            // Thin accent bar on incoming messages — the only decoration
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            )
        }
        Box(
            modifier = Modifier
                .clip(
                    if (isOwn) RoundedCornerShape(22.dp)
                    else RoundedCornerShape(topStart = 0.dp, topEnd = 22.dp, bottomStart = 0.dp, bottomEnd = 22.dp)
                )
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Column(content = content)
        }
    }
}

/**
 * ✨ МОДЕРН СТИЛЬ - градієнти та glass morphism
 */
@Composable
fun ModernBubble(
    isOwn: Boolean,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f

    val gradientBrush = if (isOwn) {
        Brush.linearGradient(
            colors = listOf(
                colorScheme.primary,
                colorScheme.tertiary.copy(alpha = 0.88f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                colorScheme.surfaceVariant,
                colorScheme.surface
            )
        )
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isOwn) 4.dp else 2.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = if (isOwn) colorScheme.primary.copy(alpha = 0.4f)
                            else Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(gradientBrush)
            .border(
                width = 0.5.dp,
                color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(content = content)
    }
}

/**
 * 🔴 РЕТРО СТИЛЬ - яскраві кольори з товстими рамками
 */
@Composable
fun RetroBubble(
    isOwn: Boolean,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val retroBg = if (isOwn) {
        if (isDark) Color(0xFF8B6914) else Color(0xFFFFE082)
    } else {
        if (isDark) Color(0xFF4A3680) else Color(0xFFB39DDB)
    }

    val borderColor = if (isOwn) {
        if (isDark) Color(0xFFC47F00) else Color(0xFFFF6F00)
    } else {
        if (isDark) Color(0xFF7E57C2) else Color(0xFF5E35B1)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(retroBg)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Column(content = content)
    }
}

/**
 * 🪟 GLASS СТИЛЬ - glassmorphism з напівпрозорістю
 */
@Composable
fun GlassBubble(
    isOwn: Boolean,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val glassColor = if (isOwn) {
        bgColor.copy(alpha = if (isDark) 0.65f else 0.55f)
    } else {
        if (isDark) Color(0xFF2A2F3A).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(glassColor)
            .border(
                width = 0.5.dp,
                color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(content = content)
    }
}

/**
 * 💡 NEON СТИЛЬ - cyberpunk світіння по контуру
 */
@Composable
fun NeonBubble(
    isOwn: Boolean,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val neonColor = if (isOwn) Color(0xFF00FF88) else Color(0xFF00BFFF)
    val darkBg = Color(0xFF1A1A2E)

    Box(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = neonColor.copy(alpha = 0.6f),
                ambientColor = neonColor.copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(darkBg)
            .border(
                width = 1.5.dp,
                color = neonColor.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(content = content)
    }
}

/**
 * 🌈 GRADIENT СТИЛЬ - яскраві кольорові переходи
 */
@Composable
fun GradientBubble(
    isOwn: Boolean,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    // Radial — radiates from center outward; clearly different from Modern's linear
    val gradientBrush = if (isOwn) {
        Brush.radialGradient(
            colors = listOf(
                colorScheme.tertiary,          // bright centre
                colorScheme.primary.copy(alpha = 0.85f)   // deeper edge
            )
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                colorScheme.surface,
                colorScheme.surfaceVariant.copy(alpha = 0.90f)
            )
        )
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isOwn) 5.dp else 2.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (isOwn) colorScheme.tertiary.copy(alpha = 0.50f)
                            else Color.Black.copy(alpha = 0.07f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(gradientBrush)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Column(content = content)
    }
}

/**
 * 🎭 NEUMORPHISM СТИЛЬ - м'який 3D-ефект
 */
@Composable
fun NeumorphismBubble(
    isOwn: Boolean,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f

    val surfaceColor = if (isOwn) colorScheme.primaryContainer else colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isOwn) 10.dp else 5.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = if (isOwn) colorScheme.primary.copy(alpha = if (isDark) 0.40f else 0.28f)
                            else Color.Black.copy(alpha = if (isDark) 0.30f else 0.12f),
                ambientColor = if (isOwn) colorScheme.primary.copy(alpha = 0.10f)
                               else Color.Black.copy(alpha = if (isDark) 0.18f else 0.06f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(surfaceColor)
    ) {
        // ── Embossed inner light: top highlight (light source from top-left) ──
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    )
                )
        )
        // ── Embossed inner shadow: bottom-right pressed depression ──
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (isDark) Color.Black.copy(alpha = 0.28f)
                            else Color.Black.copy(alpha = 0.10f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            content = content
        )
    }
}

/**
 * 🎨 Фабрика для створення бульбашки відповідного стилю
 */
@Composable
fun StyledBubble(
    bubbleStyle: BubbleStyle,
    isOwn: Boolean,
    bgColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    when (bubbleStyle) {
        BubbleStyle.STANDARD -> StandardBubble(isOwn, bgColor, modifier, content)
        BubbleStyle.COMIC -> ComicBubble(isOwn, bgColor, modifier, content)
        BubbleStyle.TELEGRAM -> TelegramBubble(isOwn, bgColor, modifier, content)
        BubbleStyle.MINIMAL -> MinimalBubble(isOwn, bgColor, modifier, content)
        BubbleStyle.MODERN -> ModernBubble(isOwn, bgColor, modifier, content)
        BubbleStyle.RETRO -> RetroBubble(isOwn, bgColor, modifier, content)
        BubbleStyle.GLASS -> GlassBubble(isOwn, bgColor, modifier, content)
        BubbleStyle.NEON -> NeonBubble(isOwn, bgColor, modifier, content)
        BubbleStyle.GRADIENT -> GradientBubble(isOwn, bgColor, modifier, content)
        BubbleStyle.NEUMORPHISM -> NeumorphismBubble(isOwn, bgColor, modifier, content)
    }
}
