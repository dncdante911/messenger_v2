package com.worldmates.messenger.ui.fonts

/**
 * ════════════════════════════════════════════════════════
 *  WORLDMATES — ALL FONTS ARE HERE
 * ════════════════════════════════════════════════════════
 *
 *  ┌─ ui/fonts/ ────────────────────────────────────────┐
 *  │  AppFonts.kt          ← ВЫ ЗДЕСЬ                   │
 *  │    Compose FontFamily для display-шрифтов           │
 *  │    (Exo2, RussoOne, Righteous, Orbitron)            │
 *  │                                                     │
 *  │  FontStyle.kt                                       │
 *  │    Enum ~50 Unicode-стилей для текста сообщений     │
 *  │    (Bold, Italic, Fraktur, Monospace и др.)         │
 *  │                                                     │
 *  │  FontStyleConverter.kt                              │
 *  │    Конвертер: обычный текст → Unicode-декор         │
 *  │                                                     │
 *  │  FontPickerSheet.kt                                 │
 *  │    UI: боттом-шит выбора стиля шрифта              │
 *  └────────────────────────────────────────────────────┘
 *
 *  ┌─ res/font/ ─────────────────────────────────────────┐
 *  │  exo2.xml, exo2_bold.xml, russo_one.xml,            │
 *  │  righteous.xml, orbitron.xml                        │
 *  │  ↑ Дескрипторы Google Fonts для системы Android    │
 *  │  ↑ Стандартное место Android — НЕ МЕНЯТЬ           │
 *  └─────────────────────────────────────────────────────┘
 *
 *  ┌─ res/values/font_certs.xml ─────────────────────────┐
 *  │  Сертификаты GMS-провайдера шрифтов                 │
 *  └─────────────────────────────────────────────────────┘
 *
 * ─────────────────────────────────────────────────────
 *  Display FontFamilies — загружаются через Google Fonts
 *  (GMS provider). Скачиваются один раз, кешируются.
 *  Graceful fallback → системный sans-serif если GMS
 *  недоступен (эмулятор без Play Services).
 *
 *  Использование:
 *  ```kotlin
 *  import com.worldmates.messenger.ui.fonts.AppFonts
 *
 *  Text(
 *      text = "Название канала",
 *      fontFamily = AppFonts.Exo2,
 *      fontWeight = FontWeight.Bold
 *  )
 *  ```
 */

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.worldmates.messenger.R

object AppFonts {

    /**
     * Exo 2 — геометрический футуристичный, полный диапазон весов 100–900.
     * Идеально: названия каналов, заголовки постов, инлайн-кнопки.
     * → fonts.google.com/specimen/Exo+2
     */
    val Exo2: FontFamily by lazy {
        FontFamily(
            Font(
                resId = R.font.exo2,
                weight = FontWeight.Normal,
                style = FontStyle.Normal,
                loadingStrategy = FontLoadingStrategy.OptionalLocal
            ),
            Font(
                resId = R.font.exo2_bold,
                weight = FontWeight.Bold,
                style = FontStyle.Normal,
                loadingStrategy = FontLoadingStrategy.OptionalLocal
            )
        )
    }

    /**
     * Russo One — широкий жирный геометрический display.
     * Авторитетный. Идеально: большие заголовки, инициалы в аватарах.
     * → fonts.google.com/specimen/Russo+One
     */
    val RussoOne: FontFamily by lazy {
        FontFamily(
            Font(
                resId = R.font.russo_one,
                weight = FontWeight.Normal,
                style = FontStyle.Normal,
                loadingStrategy = FontLoadingStrategy.OptionalLocal
            )
        )
    }

    /**
     * Righteous — скруглённый bold, дружелюбный но сильный.
     * Идеально: теги категорий, бейджи, подписи кнопок.
     * → fonts.google.com/specimen/Righteous
     */
    val Righteous: FontFamily by lazy {
        FontFamily(
            Font(
                resId = R.font.righteous,
                weight = FontWeight.Normal,
                style = FontStyle.Normal,
                loadingStrategy = FontLoadingStrategy.OptionalLocal
            )
        )
    }

    /**
     * Orbitron — sci-fi/tech display с объёмным весом.
     * Идеально: статистика, лайв-индикаторы, акценты бренда.
     * → fonts.google.com/specimen/Orbitron
     */
    val Orbitron: FontFamily by lazy {
        FontFamily(
            Font(
                resId = R.font.orbitron,
                weight = FontWeight.Bold,
                style = FontStyle.Normal,
                loadingStrategy = FontLoadingStrategy.OptionalLocal
            )
        )
    }
}

// ── Composable helpers ───────────────────────────────────────────────────────
// Используй внутри @Composable — FontFamily remember'ится между перерисовками.

@Composable fun rememberExo2(): FontFamily = remember { AppFonts.Exo2 }
@Composable fun rememberRussoOne(): FontFamily = remember { AppFonts.RussoOne }
@Composable fun rememberRighteous(): FontFamily = remember { AppFonts.Righteous }
@Composable fun rememberOrbitron(): FontFamily = remember { AppFonts.Orbitron }
