package com.worldmates.messenger.ui.theme

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * Компонент для отображения кастомного фонового изображения или градиента
 * @param backgroundImageUri URI кастомного изображения (если установлено)
 * @param defaultGradient градиент по умолчанию (если изображение не установлено)
 * @param content контент, который будет отображен поверх фона
 */
@Composable
fun BackgroundContainer(
    backgroundImageUri: String?,
    defaultGradient: Brush,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Фоновый слой
        if (backgroundImageUri != null) {
            // Показываем кастомное изображение с затемнением
            AsyncImage(
                model = Uri.parse(backgroundImageUri),
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Полупрозрачный оверлей для лучшей читабельности
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        } else {
            // Показываем градиент темы
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = defaultGradient)
            )
        }

        // Контент поверх фона
        content()
    }
}

/**
 * Компонент для відображення фонового зображення з налаштувань тем
 * Показує або кастомне зображення, або готовий градієнт, або стандартний фон
 * @param backgroundImageUri URI кастомного зображення користувача
 * @param presetBackgroundId ID готового градієнта (ocean, sunset, forest тощо)
 */
@Composable
fun BackgroundImage(
    backgroundImageUri: String?,
    presetBackgroundId: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            // Пріоритет 1: Кастомне зображення користувача
            backgroundImageUri != null -> {
                AsyncImage(
                    model = Uri.parse(backgroundImageUri),
                    contentDescription = "Custom Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Напівпрозорий оверлей для кращої читабельності
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }
            // Пріоритет 2: Готовий градієнт
            presetBackgroundId != null -> {
                val presetBg = when (presetBackgroundId) {
                    "ocean" -> listOf(Color(0xFF667eea), Color(0xFF764ba2))
                    "sunset" -> listOf(Color(0xFFf83600), Color(0xFFf9d423))
                    "forest" -> listOf(Color(0xFF11998e), Color(0xFF38ef7d))
                    "lavender" -> listOf(Color(0xFFa8edea), Color(0xFFfed6e3))
                    "midnight" -> listOf(Color(0xFF2c3e50), Color(0xFF3498db))
                    "peach" -> listOf(Color(0xFFffecd2), Color(0xFFfcb69f))
                    else -> null
                }

                if (presetBg != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(colors = presetBg)
                            )
                    )
                    // Легкий оверлей
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.1f))
                    )
                } else {
                    // Невідомий ID - показуємо стандартний фон
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
            // Пріоритет 3: Стандартний фон теми
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}
