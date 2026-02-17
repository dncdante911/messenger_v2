package com.worldmates.messenger.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.ui.preferences.BubbleStyle
import com.worldmates.messenger.ui.preferences.UIStyle
import com.worldmates.messenger.ui.preferences.UIStylePreferences

/**
 * Дані одного пакету оформлення "в один клік".
 * Використовує лише підключені до рендерингу налаштування.
 */
data class OneClickInterfacePack(
    val name: String,
    val emoji: String,
    val description: String,
    val themeVariant: ThemeVariant,
    val presetBackgroundId: String,
    val quickReaction: String,
    val bubbleStyle: BubbleStyle,
    val uiStyle: UIStyle,
    val isDarkTheme: Boolean = false
)

val oneClickPacks = listOf(
    OneClickInterfacePack(
        name = "Creator Neon",
        emoji = "⚡",
        description = "Cyberpunk: Dracula + Neon bubbles + Cosmic bg",
        themeVariant = ThemeVariant.DRACULA,
        presetBackgroundId = PresetBackground.COSMIC.id,
        quickReaction = "🔥",
        bubbleStyle = BubbleStyle.NEON,
        uiStyle = UIStyle.WORLDMATES,
        isDarkTheme = true
    ),
    OneClickInterfacePack(
        name = "Business Clean",
        emoji = "💼",
        description = "Minimal + Monochrome + Lavender bg",
        themeVariant = ThemeVariant.MONOCHROME,
        presetBackgroundId = PresetBackground.LAVENDER.id,
        quickReaction = "👍",
        bubbleStyle = BubbleStyle.MINIMAL,
        uiStyle = UIStyle.TELEGRAM
    ),
    OneClickInterfacePack(
        name = "Night Focus",
        emoji = "🌙",
        description = "Dark Ocean theme + Glass bubbles",
        themeVariant = ThemeVariant.OCEAN,
        presetBackgroundId = PresetBackground.MIDNIGHT.id,
        quickReaction = "❤️",
        bubbleStyle = BubbleStyle.GLASS,
        uiStyle = UIStyle.WORLDMATES,
        isDarkTheme = true
    ),
    OneClickInterfacePack(
        name = "Sunset Vibes",
        emoji = "🌅",
        description = "Warm sunset + Gradient bubbles + Peach bg",
        themeVariant = ThemeVariant.SUNSET,
        presetBackgroundId = PresetBackground.PEACH.id,
        quickReaction = "✨",
        bubbleStyle = BubbleStyle.GRADIENT,
        uiStyle = UIStyle.WORLDMATES
    ),
    OneClickInterfacePack(
        name = "Forest Calm",
        emoji = "🌲",
        description = "Nature green theme + Standard bubbles",
        themeVariant = ThemeVariant.FOREST,
        presetBackgroundId = PresetBackground.FOREST.id,
        quickReaction = "🙏",
        bubbleStyle = BubbleStyle.STANDARD,
        uiStyle = UIStyle.WORLDMATES
    ),
    OneClickInterfacePack(
        name = "Rose Gold",
        emoji = "🌹",
        description = "Elegant rose theme + Cotton candy bg",
        themeVariant = ThemeVariant.ROSE_GOLD,
        presetBackgroundId = PresetBackground.COTTON_CANDY.id,
        quickReaction = "❤️",
        bubbleStyle = BubbleStyle.MODERN,
        uiStyle = UIStyle.WORLDMATES
    ),
    OneClickInterfacePack(
        name = "Nord Frost",
        emoji = "❄️",
        description = "Cold Nord theme + Winter bg + Neumorphism",
        themeVariant = ThemeVariant.NORD,
        presetBackgroundId = PresetBackground.WINTER.id,
        quickReaction = "💯",
        bubbleStyle = BubbleStyle.NEUMORPHISM,
        uiStyle = UIStyle.TELEGRAM
    ),
    OneClickInterfacePack(
        name = "Retro Pop",
        emoji = "📼",
        description = "Purple theme + Retro bubbles + Fire bg",
        themeVariant = ThemeVariant.PURPLE,
        presetBackgroundId = PresetBackground.FIRE.id,
        quickReaction = "🎉",
        bubbleStyle = BubbleStyle.RETRO,
        uiStyle = UIStyle.WORLDMATES
    ),
    OneClickInterfacePack(
        name = "Deep Space",
        emoji = "🚀",
        description = "Dracula + Neon City bg + Comic bubbles",
        themeVariant = ThemeVariant.DRACULA,
        presetBackgroundId = PresetBackground.NEON_CITY.id,
        quickReaction = "🔥",
        bubbleStyle = BubbleStyle.COMIC,
        uiStyle = UIStyle.WORLDMATES,
        isDarkTheme = true
    ),
    OneClickInterfacePack(
        name = "Classic Messenger",
        emoji = "💙",
        description = "Classic blue + Ocean bg + Telegram style",
        themeVariant = ThemeVariant.CLASSIC,
        presetBackgroundId = PresetBackground.OCEAN.id,
        quickReaction = "👍",
        bubbleStyle = BubbleStyle.TELEGRAM,
        uiStyle = UIStyle.TELEGRAM
    )
)

@Composable
fun OneClickInterfacePacksSection(themeViewModel: ThemeViewModel) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Готові рішення в один клік",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Повний пакет оформлення: тема + фон + реакція + бульбашки + стиль",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.height(460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(oneClickPacks) { pack ->
                    OneClickPackCard(
                        pack = pack,
                        onClick = {
                            themeViewModel.setThemeVariant(pack.themeVariant)
                            themeViewModel.setPresetBackgroundId(pack.presetBackgroundId)
                            if (pack.isDarkTheme) {
                                themeViewModel.setDarkTheme(true)
                            }
                            UIStylePreferences.setQuickReaction(context, pack.quickReaction)
                            UIStylePreferences.setStyle(context, pack.uiStyle)
                            UIStylePreferences.setBubbleStyle(context, pack.bubbleStyle)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OneClickPackCard(
    pack: OneClickInterfacePack,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(pack.emoji, fontSize = 26.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pack.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = pack.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
        }
    }
}
