package com.worldmates.messenger.ui.channels.premium.appearance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.data.model.ChannelPremiumCustomization
import com.worldmates.messenger.ui.channels.premium.components.AvatarFrameStyle
import com.worldmates.messenger.ui.channels.premium.components.ChannelLevelUpDialog
import com.worldmates.messenger.ui.channels.premium.components.GiftPlanOption
import com.worldmates.messenger.ui.channels.premium.components.GiftSubscriptionSheet
import com.worldmates.messenger.ui.channels.premium.components.GlassSurface
import com.worldmates.messenger.ui.channels.premium.components.PremiumBadge
import com.worldmates.messenger.ui.channels.premium.components.PremiumBadgeSize
import com.worldmates.messenger.ui.channels.premium.components.PremiumChannelAvatar
import com.worldmates.messenger.ui.channels.premium.components.PremiumDivider
import com.worldmates.messenger.ui.channels.premium.components.PremiumEmojiStatusPicker
import com.worldmates.messenger.ui.channels.premium.components.PremiumGlassIconButton
import com.worldmates.messenger.ui.channels.premium.components.PremiumLevelBadge
import com.worldmates.messenger.ui.channels.premium.components.PremiumPrimaryButton
import com.worldmates.messenger.ui.channels.premium.components.PremiumReactionsPicker
import com.worldmates.messenger.ui.channels.premium.components.PremiumSecondaryButton
import com.worldmates.messenger.ui.channels.premium.components.PremiumSectionHeader
import com.worldmates.messenger.ui.channels.premium.components.PremiumTrialBanner
import com.worldmates.messenger.ui.channels.premium.design.AccentPreset
import com.worldmates.messenger.ui.channels.premium.design.AvatarFramePreset
import com.worldmates.messenger.ui.channels.premium.design.BannerPreset
import com.worldmates.messenger.ui.channels.premium.design.ChannelAppearance
import com.worldmates.messenger.ui.channels.premium.design.CornerRadiusPreset
import com.worldmates.messenger.ui.channels.premium.design.EmojiPackPreset
import com.worldmates.messenger.ui.channels.premium.design.FontWeightPreset
import com.worldmates.messenger.ui.channels.premium.design.PremiumCustomizationResolver
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.PremiumPresets
import com.worldmates.messenger.ui.channels.premium.design.PremiumTheme
import com.worldmates.messenger.ui.channels.premium.design.current
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.utils.LanguageManager

/**
 * Settings → Themes → Premium channels.
 *
 * App-wide control panel for the new premium-channels design system:
 *   - Master enable / disable toggle.
 *   - Default appearance picker (accent / frame / corners / weight / banner / emoji pack).
 *   - Live preview of how a premium channel will look.
 *   - Component playground (reactions picker, emoji status, level-up celebration, gift sheet).
 *
 * The chosen defaults are applied to any premium channel that hasn't set
 * its own per-channel customization. Per-channel choices made by an owner
 * via [ChannelAppearanceActivity] still win.
 */
class PremiumChannelsThemeActivity : ComponentActivity() {

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, PremiumChannelsThemeActivity::class.java)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.initialize(this)

        val store = PremiumChannelsThemeStore.from(this)

        setContent {
            PremiumTheme {
                PremiumChannelsThemeScreen(
                    initialEnabled = store.enabled,
                    initial = store.read(),
                    onSave = { enabled, c ->
                        store.enabled = enabled
                        store.save(c)
                        PremiumCustomizationResolver.refreshGlobalDefaults(this)
                        finish()
                    },
                    onReset = {
                        store.reset()
                        PremiumCustomizationResolver.refreshGlobalDefaults(this)
                    },
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
private fun PremiumChannelsThemeScreen(
    initialEnabled: Boolean,
    initial: ChannelPremiumCustomization,
    onSave: (Boolean, ChannelPremiumCustomization) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    var enabled by remember { mutableStateOf(initialEnabled) }
    var accent by remember { mutableStateOf(PremiumPresets.accent(initial.accentColorId)) }
    var banner by remember { mutableStateOf(PremiumPresets.banner(initial.bannerPatternId)) }
    var emojiPack by remember { mutableStateOf(PremiumPresets.emojiPack(initial.emojiPackId)) }
    var weight by remember { mutableStateOf(PremiumPresets.fontWeight(initial.fontWeight)) }
    var cornerRadius by remember { mutableStateOf(PremiumPresets.cornerRadius(initial.postCornerRadius)) }
    var avatarFrame by remember { mutableStateOf(PremiumPresets.avatarFramePreset(initial.avatarFrame)) }

    val current = ChannelPremiumCustomization(
        accentColorId = accent.id,
        bannerPatternId = banner.id,
        emojiPackId = emojiPack.id,
        fontWeight = weight.id,
        postCornerRadius = cornerRadius.value.value.toInt(),
        avatarFrame = avatarFrame.id,
        postsBackdropEnabled = initial.postsBackdropEnabled,
    )
    val resolved = com.worldmates.messenger.ui.channels.premium.design.PremiumCustomizationResolver
        .resolve(current)

    PremiumTheme(appearance = resolved) {
        val design = PremiumDesign.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(design.colors.backgroundBrush()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 40.dp),
            ) {
                TopBar(onClose = onClose)

                MasterToggleCard(enabled = enabled, onChange = { enabled = it })

                Spacer(Modifier.height(8.dp))

                PreviewPanel()

                Spacer(Modifier.height(8.dp))

                AccentSection(current = accent, onPick = { accent = it })
                FrameSection(current = avatarFrame, onPick = { avatarFrame = it })
                RadiusSection(current = cornerRadius, onPick = { cornerRadius = it })
                WeightSection(current = weight, onPick = { weight = it })
                BannerSection(current = banner, onPick = { banner = it })
                EmojiPackSection(current = emojiPack, onPick = { emojiPack = it })

                Spacer(Modifier.height(20.dp))

                ComponentPlayground()

                Spacer(Modifier.height(20.dp))

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PremiumPrimaryButton(
                        text = "Save & apply",
                        onClick = { onSave(enabled, current) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PremiumSecondaryButton(
                        text = "Reset to defaults",
                        onClick = {
                            onReset()
                            accent = PremiumPresets.defaultAccent
                            banner = PremiumPresets.defaultBanner
                            emojiPack = PremiumPresets.defaultEmojiPack
                            weight = PremiumPresets.defaultFontWeight
                            cornerRadius = PremiumPresets.defaultCornerRadius
                            avatarFrame = PremiumPresets.defaultAvatarFrame
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(onClose: () -> Unit) {
    val design = PremiumDesign.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PremiumGlassIconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.ArrowBackIosNew,
                contentDescription = "Back",
                tint = design.colors.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "Premium channels",
                style = design.typography.title.copy(color = design.colors.onPrimary),
            )
            Text(
                text = "App-wide design defaults",
                style = design.typography.caption.copy(color = design.colors.onMuted),
            )
        }
    }
}

// ─── Master toggle ────────────────────────────────────────────────────────────

@Composable
private fun MasterToggleCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val design = PremiumDesign.current
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumBadge(size = PremiumBadgeSize.Medium, animated = enabled)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Use new premium design",
                    style = design.typography.bodyStrong.copy(color = design.colors.onPrimary),
                )
                Text(
                    text = if (enabled) "Active for all premium channels" else "Falls back to the old layout",
                    style = design.typography.caption.copy(color = design.colors.onSecondary),
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = design.colors.onAccent,
                    checkedTrackColor = design.colors.accent,
                    uncheckedThumbColor = design.colors.onMuted,
                    uncheckedTrackColor = design.colors.surface,
                ),
            )
        }
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Composable
private fun PreviewPanel() {
    val design = PremiumDesign.current
    val appearance = ChannelAppearance.current
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(appearance.cornerRadius.value),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumChannelAvatar(
                    size = 56.dp,
                    imageUrl = null,
                    initials = "WM",
                    frame = appearance.avatarFrameStyle,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Worldmates HQ",
                            style = design.typography.titleSmall.copy(color = design.colors.onPrimary),
                        )
                        Spacer(Modifier.width(8.dp))
                        PremiumBadge(size = PremiumBadgeSize.Small)
                    }
                    Spacer(Modifier.height(4.dp))
                    PremiumLevelBadge(level = 7, compact = true)
                }
            }
            Spacer(Modifier.height(12.dp))
            PremiumDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "This preview reflects every change you make. Pick an accent, choose a header pattern, swap the emoji pack — everything updates here in real time.",
                style = design.typography.body.copy(color = design.colors.onPrimary),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                appearance.emojiPack.reactions.take(5).forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(design.colors.reactionIdleFill)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = emoji,
                            style = design.typography.body.copy(color = design.colors.onPrimary),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(appearance.cornerRadius.value))
                    .background(design.colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Subscribe",
                    style = design.typography.button.copy(color = design.colors.onAccent),
                )
            }
        }
    }
}

// ─── Component playground ─────────────────────────────────────────────────────

@Composable
private fun ComponentPlayground() {
    val design = PremiumDesign.current
    var showLevelUp by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    var showEmojiStatus by remember { mutableStateOf(false) }
    var showGift by remember { mutableStateOf(false) }
    var pickedReaction by remember { mutableStateOf<String?>(null) }
    var pickedStatus by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        PremiumSectionHeader(
            title = "TRY IT OUT",
            modifier = Modifier.padding(horizontal = 18.dp),
        )

        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlaygroundRow(
                title = "Trial banner",
                subtitle = "Inline preview shown to channel owners with no prior subscription",
            )
            PremiumTrialBanner(
                trialDays = 7,
                onStartTrial = { /* no-op in playground */ },
            )

            Spacer(Modifier.height(6.dp))
            PlaygroundButton("Open reactions picker") { showReactions = true }
            pickedReaction?.let {
                Text(
                    text = "Last reaction: $it",
                    style = design.typography.caption.copy(color = design.colors.onSecondary),
                )
            }

            PlaygroundButton("Open emoji status picker") { showEmojiStatus = true }
            pickedStatus?.let {
                Text(
                    text = "Last status: ${it.ifBlank { "—" }}",
                    style = design.typography.caption.copy(color = design.colors.onSecondary),
                )
            }

            PlaygroundButton("Show level-up celebration") { showLevelUp = true }
            PlaygroundButton("Open gift sheet") { showGift = true }
        }
    }

    if (showReactions) {
        PremiumReactionsPicker(
            onReactionPick = { pickedReaction = it; showReactions = false },
            currentReaction = pickedReaction,
        )
    }
    if (showEmojiStatus) {
        PremiumEmojiStatusPicker(
            current = pickedStatus,
            onPick = { pickedStatus = it.orEmpty(); showEmojiStatus = false },
        )
    }
    if (showLevelUp) {
        ChannelLevelUpDialog(
            visible = true,
            newLevel = 8,
            unlockedSummary = "Custom emoji pack, deeper analytics and a new banner pattern unlocked.",
            onDismiss = { showLevelUp = false },
            onExplore = { showLevelUp = false },
            channelName = "Your channel",
        )
    }
    if (showGift) {
        GiftSubscriptionSheet(
            plans = listOf(
                GiftPlanOption("monthly",   1,  299, "1 month",   "Full price"),
                GiftPlanOption("quarterly", 3,  807, "3 months",  "10% off"),
                GiftPlanOption("annual",   12, 2691, "12 months", "25% off"),
            ),
            onGift = { _, _, _ -> showGift = false },
        )
    }
}

@Composable
private fun PlaygroundRow(title: String, subtitle: String) {
    val design = PremiumDesign.current
    Column {
        Text(
            text = title,
            style = design.typography.bodyStrong.copy(color = design.colors.onPrimary),
        )
        Text(
            text = subtitle,
            style = design.typography.caption.copy(color = design.colors.onSecondary),
        )
    }
}

@Composable
private fun PlaygroundButton(text: String, onClick: () -> Unit) {
    PremiumSecondaryButton(
        text = text,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ─── Picker sections (mirrored from ChannelAppearanceActivity) ────────────────

@Composable
private fun AccentSection(current: AccentPreset, onPick: (AccentPreset) -> Unit) {
    SectionWrap("ACCENT COLOR") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PremiumPresets.accents.forEach { preset ->
                AccentSwatch(preset, preset.id == current.id) { onPick(preset) }
            }
        }
    }
}

@Composable
private fun AccentSwatch(preset: AccentPreset, selected: Boolean, onClick: () -> Unit) {
    val design = PremiumDesign.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(preset.base)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) design.colors.onPrimary else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = preset.onAccentDark,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.displayName,
            style = design.typography.caption.copy(color = design.colors.onSecondary),
        )
    }
}

@Composable
private fun FrameSection(current: AvatarFramePreset, onPick: (AvatarFramePreset) -> Unit) {
    SectionWrap("AVATAR FRAME") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PremiumPresets.avatarFrames.forEach { preset ->
                FrameSwatch(preset, preset.id == current.id) { onPick(preset) }
            }
        }
    }
}

@Composable
private fun FrameSwatch(preset: AvatarFramePreset, selected: Boolean, onClick: () -> Unit) {
    val design = PremiumDesign.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(86.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) design.colors.accent else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            PremiumChannelAvatar(
                size = 62.dp,
                imageUrl = null,
                initials = "A",
                frame = preset.style,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.displayName,
            style = design.typography.caption.copy(color = design.colors.onSecondary),
        )
    }
}

@Composable
private fun RadiusSection(current: CornerRadiusPreset, onPick: (CornerRadiusPreset) -> Unit) {
    SectionWrap("POST CORNERS") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PremiumPresets.cornerRadii.forEach { preset ->
                RadiusSwatch(preset, preset.value == current.value) { onPick(preset) }
            }
        }
    }
}

@Composable
private fun RadiusSwatch(preset: CornerRadiusPreset, selected: Boolean, onClick: () -> Unit) {
    val design = PremiumDesign.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 56.dp)
                .clip(RoundedCornerShape(preset.value))
                .background(design.colors.surface)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) design.colors.accent else design.colors.outline,
                    shape = RoundedCornerShape(preset.value),
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.displayName,
            style = design.typography.caption.copy(color = design.colors.onSecondary),
        )
    }
}

@Composable
private fun WeightSection(current: FontWeightPreset, onPick: (FontWeightPreset) -> Unit) {
    SectionWrap("TITLE WEIGHT") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PremiumPresets.fontWeights.forEach { preset ->
                WeightChip(preset, preset.id == current.id) { onPick(preset) }
            }
        }
    }
}

@Composable
private fun WeightChip(preset: FontWeightPreset, selected: Boolean, onClick: () -> Unit) {
    val design = PremiumDesign.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) design.colors.accent else design.colors.surface)
            .border(
                width = 1.dp,
                color = if (selected) design.colors.accent else design.colors.outline,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = preset.displayName,
            style = design.typography.bodyStrong.copy(
                fontWeight = preset.title,
                color = if (selected) design.colors.onAccent else design.colors.onPrimary,
            ),
        )
    }
}

@Composable
private fun BannerSection(current: BannerPreset, onPick: (BannerPreset) -> Unit) {
    SectionWrap("HEADER PATTERN") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PremiumPresets.banners.forEach { preset ->
                BannerSwatch(preset, preset.id == current.id) { onPick(preset) }
            }
        }
    }
}

@Composable
private fun BannerSwatch(preset: BannerPreset, selected: Boolean, onClick: () -> Unit) {
    val design = PremiumDesign.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 58.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(design.colors.backgroundBrush())
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) design.colors.accent else design.colors.outline,
                    shape = RoundedCornerShape(10.dp),
                ),
        ) {
            com.worldmates.messenger.ui.channels.premium.components.PremiumBannerPattern(
                banner = preset,
                strength = 0.35f,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.displayName,
            style = design.typography.caption.copy(color = design.colors.onSecondary),
        )
    }
}

@Composable
private fun EmojiPackSection(current: EmojiPackPreset, onPick: (EmojiPackPreset) -> Unit) {
    SectionWrap("EMOJI PACK") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PremiumPresets.emojiPacks.forEach { preset ->
                EmojiPackRow(preset, preset.id == current.id) { onPick(preset) }
            }
        }
    }
}

@Composable
private fun EmojiPackRow(preset: EmojiPackPreset, selected: Boolean, onClick: () -> Unit) {
    val design = PremiumDesign.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(design.colors.surface)
            .border(
                width = 1.dp,
                color = if (selected) design.colors.accent else design.colors.outline,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = preset.sample,
            style = design.typography.title.copy(color = design.colors.onPrimary),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.displayName,
                style = design.typography.bodyStrong.copy(color = design.colors.onPrimary),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = preset.reactions.take(6).joinToString("  "),
                style = design.typography.caption.copy(color = design.colors.onSecondary),
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = design.colors.accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SectionWrap(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        PremiumSectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        content()
        Spacer(Modifier.height(10.dp))
    }
}
