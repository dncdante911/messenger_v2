package com.worldmates.messenger.ui.theme

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.ui.preferences.BubbleStyle
import com.worldmates.messenger.ui.preferences.UIStyle
import com.worldmates.messenger.ui.preferences.UIStylePreferences
import com.worldmates.messenger.ui.premium.PremiumActivity

/**
 * Дані одного пакету оформлення "в один клік".
 *
 * isPremium = true означає, що пакет використовує PRO-тему;
 * при виборі без підписки відкривається екран PRO.
 */
data class OneClickInterfacePack(
    val nameResId: Int,
    val descResId: Int,
    val emoji: String,
    val themeVariant: ThemeVariant,
    val presetBackgroundId: String,
    val quickReaction: String,
    val bubbleStyle: BubbleStyle,
    val uiStyle: UIStyle,
    val isDarkTheme: Boolean = false,
    val isPremium: Boolean = false
)

val oneClickPacks = listOf(

    // ── Базові набори ─────────────────────────────────────────────────────────

    OneClickInterfacePack(
        nameResId          = R.string.pack_classic_name,
        descResId          = R.string.pack_classic_desc,
        emoji              = "💙",
        themeVariant       = ThemeVariant.CLASSIC,
        presetBackgroundId = PresetBackground.MIDNIGHT.id,
        quickReaction      = "👍",
        bubbleStyle        = BubbleStyle.TELEGRAM,
        uiStyle            = UIStyle.TELEGRAM
    ),

    OneClickInterfacePack(
        nameResId          = R.string.pack_nord_name,
        descResId          = R.string.pack_nord_desc,
        emoji              = "❄️",
        themeVariant       = ThemeVariant.NORD,
        presetBackgroundId = PresetBackground.WINTER.id,
        quickReaction      = "💯",
        bubbleStyle        = BubbleStyle.NEUMORPHISM,
        uiStyle            = UIStyle.TELEGRAM,
        isDarkTheme        = true
    ),

    OneClickInterfacePack(
        nameResId          = R.string.pack_night_focus_name,
        descResId          = R.string.pack_night_focus_desc,
        emoji              = "🌙",
        themeVariant       = ThemeVariant.OCEAN,
        presetBackgroundId = PresetBackground.AURORA.id,
        quickReaction      = "❤️",
        bubbleStyle        = BubbleStyle.GLASS,
        uiStyle            = UIStyle.WORLDMATES,
        isDarkTheme        = true
    ),

    OneClickInterfacePack(
        nameResId          = R.string.pack_neon_creator_name,
        descResId          = R.string.pack_neon_creator_desc,
        emoji              = "⚡",
        themeVariant       = ThemeVariant.DRACULA,
        presetBackgroundId = PresetBackground.COSMIC.id,
        quickReaction      = "🔥",
        bubbleStyle        = BubbleStyle.NEON,
        uiStyle            = UIStyle.WORLDMATES,
        isDarkTheme        = true
    ),

    OneClickInterfacePack(
        nameResId          = R.string.pack_violet_name,
        descResId          = R.string.pack_violet_desc,
        emoji              = "💜",
        themeVariant       = ThemeVariant.PURPLE,
        presetBackgroundId = PresetBackground.LAVENDER.id,
        quickReaction      = "✨",
        bubbleStyle        = BubbleStyle.MODERN,
        uiStyle            = UIStyle.WORLDMATES
    ),

    OneClickInterfacePack(
        nameResId          = R.string.pack_business_name,
        descResId          = R.string.pack_business_desc,
        emoji              = "💼",
        themeVariant       = ThemeVariant.MONOCHROME,
        presetBackgroundId = PresetBackground.MORNING_MIST.id,
        quickReaction      = "👍",
        bubbleStyle        = BubbleStyle.MINIMAL,
        uiStyle            = UIStyle.TELEGRAM
    ),

    OneClickInterfacePack(
        nameResId          = R.string.pack_spring_name,
        descResId          = R.string.pack_spring_desc,
        emoji              = "🌸",
        themeVariant       = ThemeVariant.MATERIAL_YOU,
        presetBackgroundId = PresetBackground.SPRING.id,
        quickReaction      = "😊",
        bubbleStyle        = BubbleStyle.SOFT,
        uiStyle            = UIStyle.WORLDMATES
    ),

    // ── PRO-набори на основі преміум-тем ─────────────────────────────────────

    OneClickInterfacePack(
        nameResId          = R.string.pack_stranger_name,
        descResId          = R.string.pack_stranger_desc,
        emoji              = "🔴",
        themeVariant       = ThemeVariant.STRANGER_THINGS,
        presetBackgroundId = PresetBackground.DEEP_PLUM.id,
        quickReaction      = "😱",
        bubbleStyle        = BubbleStyle.NEON,
        uiStyle            = UIStyle.WORLDMATES,
        isDarkTheme        = true,
        isPremium          = true
    ),

    OneClickInterfacePack(
        nameResId          = R.string.pack_lotr_name,
        descResId          = R.string.pack_lotr_desc,
        emoji              = "💍",
        themeVariant       = ThemeVariant.LORD_OF_THE_RINGS,
        presetBackgroundId = PresetBackground.SAND_DUNES.id,
        quickReaction      = "🧙",
        bubbleStyle        = BubbleStyle.OUTLINED,
        uiStyle            = UIStyle.WORLDMATES,
        isDarkTheme        = true,
        isPremium          = true
    ),

    OneClickInterfacePack(
        nameResId          = R.string.pack_cyberpunk_name,
        descResId          = R.string.pack_cyberpunk_desc,
        emoji              = "⚡",
        themeVariant       = ThemeVariant.CYBERPUNK,
        presetBackgroundId = PresetBackground.DEEP_PLUM.id,
        quickReaction      = "⚡",
        bubbleStyle        = BubbleStyle.NEON,
        uiStyle            = UIStyle.WORLDMATES,
        isDarkTheme        = true,
        isPremium          = true
    )
)

@Composable
fun OneClickInterfacePacksSection(
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canUsePremium = remember { UserSession.isProActive }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.one_click_themes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.one_click_theme_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                oneClickPacks.forEach { pack ->
                    val isLocked = pack.isPremium && !canUsePremium
                    OneClickPackCard(
                        pack = pack,
                        isLocked = isLocked,
                        onClick = {
                            if (isLocked) {
                                context.startActivity(Intent(context, PremiumActivity::class.java))
                            } else {
                                themeViewModel.setThemeVariant(pack.themeVariant)
                                themeViewModel.setPresetBackgroundId(pack.presetBackgroundId)
                                if (pack.isDarkTheme) themeViewModel.setDarkTheme(true)
                                UIStylePreferences.setQuickReaction(context, pack.quickReaction)
                                UIStylePreferences.setStyle(context, pack.uiStyle)
                                UIStylePreferences.setBubbleStyle(context, pack.bubbleStyle)
                            }
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
    isLocked: Boolean,
    onClick: () -> Unit
) {
    val badgeBg = if (pack.themeVariant.isSubscriptionOnly) Color(0xFFFF6D00) else Color(0xFFFFD700)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isLocked) "🔒" else pack.emoji,
                fontSize = 26.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(pack.nameResId),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (pack.isPremium) {
                        val badgeLabel = stringResource(
                            if (pack.themeVariant.isSubscriptionOnly) R.string.theme_badge_exclusive
                            else R.string.theme_badge_pro
                        )
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .background(badgeBg, RoundedCornerShape(5.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(pack.descResId),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
        }
    }
}
