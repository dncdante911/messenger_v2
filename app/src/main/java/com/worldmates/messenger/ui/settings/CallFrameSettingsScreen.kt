package com.worldmates.messenger.ui.settings

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.ui.calls.CallFrameStyle

/**
 * 🎨 Екран налаштувань стилю рамок для відеодзвінків
 *
 * Settings → Themes → Call Frame Style
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallFrameSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("call_frame_prefs", Context.MODE_PRIVATE)
    }

    var selectedStyle by remember {
        mutableStateOf(
            CallFrameStyle.valueOf(
                prefs.getString("selected_frame_style", CallFrameStyle.CLASSIC.name)
                    ?: CallFrameStyle.CLASSIC.name
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Стиль рамок відеодзвінків") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF0084FF),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Виберіть стиль рамки для відеодзвінків",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(CallFrameStyle.values()) { style ->
                CallFrameStyleCard(
                    style = style,
                    isSelected = selectedStyle == style,
                    onClick = {
                        selectedStyle = style
                        // Зберегти в SharedPreferences
                        prefs.edit().putString("selected_frame_style", style.name).apply()
                    }
                )
            }
        }
    }
}

/**
 * 🎨 Картка з попереднім переглядом стилю рамки
 */
@Composable
fun CallFrameStyleCard(
    style: CallFrameStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF0084FF).copy(alpha = 0.1f)
                           else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Попередній перегляд рамки
            Box(
                modifier = Modifier
                    .size(80.dp, 100.dp)
            ) {
                when (style) {
                    CallFrameStyle.CLASSIC -> ClassicPreview()
                    CallFrameStyle.NEON -> NeonPreview()
                    CallFrameStyle.GRADIENT -> GradientPreview()
                    CallFrameStyle.MINIMAL -> MinimalPreview()
                    CallFrameStyle.GLASS -> GlassPreview()
                    CallFrameStyle.RAINBOW -> RainbowPreview()
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Назва та опис
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = getStyleEmoji(style),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = getStyleName(style),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = getStyleDescription(style),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Чекбокс
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color(0xFF0084FF),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 🎨 Попередні перегляди рамок
 */
@Composable
fun ClassicPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2a2a2a))
    )
}

@Composable
fun NeonPreview() {
    var animatedAlpha by remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            animatedAlpha = if (animatedAlpha == 1f) 0.5f else 1f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(0xFF00ffff).copy(alpha = animatedAlpha * 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2a2a2a))
    )
}

@Composable
fun GradientPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF667eea),
                        Color(0xFF764ba2),
                        Color(0xFFf093fb)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2a2a2a))
    )
}

@Composable
fun MinimalPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2a2a2a))
    )
}

@Composable
fun GlassPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(1.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF2a2a2a))
    )
}

@Composable
fun RainbowPreview() {
    var offsetX by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(50)
            offsetX = (offsetX + 5f) % 180f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFff0000),
                        Color(0xFFff7f00),
                        Color(0xFFffff00),
                        Color(0xFF00ff00),
                        Color(0xFF0000ff),
                        Color(0xFF4b0082),
                        Color(0xFF9400d3)
                    ),
                    start = Offset(offsetX, 0f),
                    end = Offset(offsetX + 500f, 500f)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2a2a2a))
    )
}

/**
 * 📝 Допоміжні функції для отримання інформації про стилі
 */
fun getStyleEmoji(style: CallFrameStyle): String {
    return when (style) {
        CallFrameStyle.CLASSIC -> "🎨"
        CallFrameStyle.NEON -> "💡"
        CallFrameStyle.GRADIENT -> "🌈"
        CallFrameStyle.MINIMAL -> "⚪"
        CallFrameStyle.GLASS -> "💎"
        CallFrameStyle.RAINBOW -> "🌈"
    }
}

fun getStyleName(style: CallFrameStyle): String {
    return when (style) {
        CallFrameStyle.CLASSIC -> "Класична"
        CallFrameStyle.NEON -> "Неонова"
        CallFrameStyle.GRADIENT -> "Градієнтна"
        CallFrameStyle.MINIMAL -> "Мінімалістична"
        CallFrameStyle.GLASS -> "Скляна"
        CallFrameStyle.RAINBOW -> "Веселкова"
    }
}

fun getStyleDescription(style: CallFrameStyle): String {
    return when (style) {
        CallFrameStyle.CLASSIC -> "Класична рамка з легкою тінню"
        CallFrameStyle.NEON -> "Неонова рамка з пульсуючим світінням"
        CallFrameStyle.GRADIENT -> "Градієнтна фіолетово-рожева рамка"
        CallFrameStyle.MINIMAL -> "Без рамки, чисте відео"
        CallFrameStyle.GLASS -> "Скляний ефект з прозорістю"
        CallFrameStyle.RAINBOW -> "Веселкова анімована рамка"
    }
}

/**
 * 🔧 Функція для отримання збереженого стилю рамки з SharedPreferences
 */
fun getSavedCallFrameStyle(context: Context): CallFrameStyle {
    val prefs = context.getSharedPreferences("call_frame_prefs", Context.MODE_PRIVATE)
    val styleName = prefs.getString("selected_frame_style", CallFrameStyle.CLASSIC.name)
    return try {
        CallFrameStyle.valueOf(styleName ?: CallFrameStyle.CLASSIC.name)
    } catch (e: Exception) {
        CallFrameStyle.CLASSIC
    }
}
