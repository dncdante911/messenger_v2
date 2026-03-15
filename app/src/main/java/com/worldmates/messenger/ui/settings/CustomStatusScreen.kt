package com.worldmates.messenger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession

/**
 * Custom Emoji Status screen (Premium only).
 * Allows users to set a status emoji + short text that appears next to their name.
 * Uses the full Unicode emoji set grouped by category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomStatusScreen(onBackClick: () -> Unit) {
    val isPro = UserSession.isProActive

    var selectedEmoji by remember { mutableStateOf(UserSession.statusEmoji ?: "") }
    var statusText by remember { mutableStateOf(UserSession.statusText ?: "") }
    var selectedCategory by remember { mutableStateOf(EmojiCategory.SMILEYS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.custom_status_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isPro) {
                        TextButton(
                            onClick = {
                                UserSession.statusEmoji = selectedEmoji.ifBlank { null }
                                UserSession.statusText = statusText.trim().ifBlank { null }
                                onBackClick()
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isPro) {
                // Premium gate — show upgrade prompt
                PremiumGateBanner(onBackClick = onBackClick)
                return@Column
            }

            // ── Status Preview ─────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Avatar placeholder
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = UserSession.username?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = UserSession.username ?: "username",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (selectedEmoji.isNotBlank()) {
                                Text(
                                    text = selectedEmoji,
                                    fontSize = 18.sp
                                )
                            }
                        }
                        if (statusText.isNotBlank()) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Status Text Input ───────────────────────────────────────
            OutlinedTextField(
                value = statusText,
                onValueChange = { if (it.length <= 60) statusText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.custom_status_placeholder)) },
                label = { Text(stringResource(R.string.custom_status_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                trailingIcon = {
                    if (statusText.isNotBlank()) {
                        IconButton(onClick = { statusText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                supportingText = { Text("${statusText.length}/60") }
            )

            Spacer(Modifier.height(8.dp))

            // ── Clear status ────────────────────────────────────────────
            if (selectedEmoji.isNotBlank() || statusText.isNotBlank()) {
                TextButton(
                    onClick = {
                        selectedEmoji = ""
                        statusText = ""
                        UserSession.statusEmoji = null
                        UserSession.statusText = null
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(stringResource(R.string.custom_status_clear))
                }
            }

            // ── Quick status presets ────────────────────────────────────
            Text(
                text = stringResource(R.string.custom_status_presets),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(STATUS_PRESETS) { preset ->
                    StatusPresetChip(
                        emoji = preset.first,
                        text = preset.second,
                        selected = selectedEmoji == preset.first && statusText == preset.second,
                        onClick = {
                            selectedEmoji = preset.first
                            statusText = preset.second
                        }
                    )
                }
            }

            // ── Emoji category tabs ─────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.custom_status_pick_emoji),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(EmojiCategory.entries) { category ->
                    EmojiCategoryTab(
                        category = category,
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category }
                    )
                }
            }

            // ── Emoji grid ──────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(selectedCategory.emojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selectedEmoji == emoji)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    Color.Transparent
                            )
                            .clickable { selectedEmoji = emoji },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// ── Components ─────────────────────────────────────────────────────────────

@Composable
private fun PremiumGateBanner(onBackClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = stringResource(R.string.custom_status_premium_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.custom_status_premium_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusPresetChip(
    emoji: String,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 18.sp)
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmojiCategoryTab(
    category: EmojiCategory,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = category.icon,
            fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ── Emoji Data ──────────────────────────────────────────────────────────────

private val STATUS_PRESETS = listOf(
    "😊" to "Доступний",
    "🤫" to "Не турбувати",
    "😴" to "Сплю",
    "💼" to "На роботі",
    "🎮" to "Граю",
    "✈️" to "В дорозі",
    "📚" to "Навчаюсь",
    "🏃" to "На тренуванні",
    "🍕" to "Обідаю",
    "📵" to "Недоступний"
)

enum class EmojiCategory(val icon: String, val emojis: List<String>) {
    SMILEYS("😀", listOf(
        "😀","😁","😂","🤣","😃","😄","😅","😆","😉","😊","😋","😎","😍","🥰","😘",
        "😗","😙","😚","🙂","🤗","🤩","🤔","🤨","😐","😑","😶","🙄","😏","😣","😥",
        "😮","🤐","😯","😪","😫","😴","😌","😛","😜","😝","🤤","😒","😓","😔","😕",
        "🙃","🤑","😲","🙁","😖","😞","😟","😤","😢","😭","😦","😧","😨","😩","🤯",
        "😬","😰","😱","🥵","🥶","😳","🤪","😵","😡","😠","🤬","😷","🤒","🤕","🤢",
        "🤮","🤧","😇","🥳","🥴","🥺","🤠","🥸","🤡","👹","👺","💀","☠️","👻","👽",
        "🤖","😺","😸","😹","😻","😼","😽","🙀","😿","😾"
    )),
    PEOPLE("👋", listOf(
        "👋","🤚","🖐️","✋","🖖","👌","🤌","🤏","✌️","🤞","🤟","🤘","🤙","👈","👉",
        "👆","🖕","👇","☝️","👍","👎","✊","👊","🤛","🤜","👏","🙌","🤲","🤝","🙏",
        "💅","🤳","💪","🦵","🦶","👂","🦻","👃","🫀","🫁","🧠","🦷","🦴","👀","👁️",
        "👅","👄","💋","🫦","👶","🧒","👦","👧","🧑","👱","👨","🧔","👩","🧓","👴","👵",
        "🧏","💆","💇","🚶","🧍","🧎","🏃","💃","🕺","👫","👬","👭","💑","👪","🤰","🤱"
    )),
    NATURE("🐶", listOf(
        "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵",
        "🙈","🙉","🙊","🐒","🐔","🐧","🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐴",
        "🦄","🐝","🪱","🐛","🦋","🐌","🐞","🐜","🪲","🦟","🦗","🕷️","🦂","🐢","🐍",
        "🦎","🦖","🦕","🐙","🦑","🦐","🦞","🦀","🐡","🐠","🐟","🐬","🐳","🐋","🦈",
        "🦭","🐊","🐅","🐆","🦓","🦍","🦧","🦣","🐘","🦛","🦏","🐪","🐫","🦒","🦘",
        "🦬","🐃","🐂","🐄","🐎","🐖","🐏","🐑","🦙","🐐","🦌","🐕","🐩","🦮","🐈"
    )),
    FOOD("🍕", listOf(
        "🍕","🍔","🌮","🌯","🥙","🧆","🥚","🍳","🥘","🍲","🥣","🥗","🍿","🧈","🧇",
        "🥞","🧇","🥓","🥩","🍗","🍖","🌭","🍟","🍱","🍘","🍙","🍚","🍛","🍜","🍝",
        "🍠","🍢","🍣","🍤","🍥","🥮","🍡","🥟","🥠","🥡","🦪","🍦","🍧","🍨","🍩",
        "🍪","🎂","🍰","🧁","🥧","🍫","🍬","🍭","🍮","🍯","🍼","🥛","☕","🫖","🍵",
        "🧃","🥤","🧋","🍶","🍺","🍻","🥂","🍷","🥃","🍸","🍹","🧉","🍾","🧊","🥄"
    )),
    TRAVEL("✈️", listOf(
        "✈️","🚀","🛸","🚁","🛶","⛵","🚤","🛥️","🛳️","⛴️","🚢","🚂","🚃","🚄","🚅",
        "🚆","🚇","🚈","🚉","🚊","🚝","🚞","🚋","🚌","🚍","🚎","🚐","🚑","🚒","🚓",
        "🚔","🚕","🚖","🚗","🚘","🚙","🛻","🚚","🚛","🚜","🏎️","🏍️","🛵","🦽","🦼",
        "🛺","🚲","🛴","🛹","🛼","🚏","🛣️","🛤️","⛽","🚧","⚓","🛟","⛵","🌍","🌎","🌏",
        "🗺️","🧭","🌋","🏔️","⛰️","🗻","🏕️","🏖️","🏜️","🏝️","🏟️","🏛️","🏗️","🧱","🪨"
    )),
    ACTIVITIES("⚽", listOf(
        "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🪀","🏓","🏸","🏒","🥍",
        "🏑","🥅","⛳","🪁","🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛷","⛸️","🥌","🎿",
        "⛷️","🏂","🪂","🏋️","🤼","🤸","⛹️","🤺","🏇","🧘","🏄","🏊","🤽","🚣","🧗",
        "🚵","🚴","🏆","🥇","🥈","🥉","🏅","🎖️","🏵️","🎗️","🎫","🎟️","🎪","🤹","🎭",
        "🩰","🎨","🎬","🎤","🎧","🎼","🎵","🎶","🥁","🪘","🎷","🎺","🪗","🎸","🎹"
    )),
    OBJECTS("💡", listOf(
        "💡","🔦","🕯️","💎","🔑","🗝️","🔐","🔏","🔒","🔓","🔨","🪓","⛏️","⚒️","🛠️",
        "🗡️","⚔️","🛡️","🔧","🔩","⚙️","🗜️","🔗","⛓️","🪝","🧲","🪜","⚗️","🔭","🔬",
        "🩺","📡","💉","🩸","💊","🩹","🩼","🩻","🚪","🪞","🪟","🛋️","🪑","🚽","🚿",
        "🛁","🪠","🧴","🧷","🧹","🧺","🧻","🪣","🧼","🫧","🪥","🧽","🪒","🧻","🪤"
    )),
    SYMBOLS("❤️", listOf(
        "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗",
        "💖","💘","💝","💟","☮️","✝️","☪️","🕉️","☸️","✡️","🔯","🕎","☯️","☦️","🛐",
        "⛎","♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒","♓","🆔","⚜️","🔱",
        "📛","🔰","⭕","✅","☑️","✔️","❌","❎","➕","➖","➗","✖️","♾️","💯","🔅",
        "🔆","🔱","⚜️","🏧","♻️","⚠️","🚫","🔞","💢","💠","🔴","🟠","🟡","🟢","🔵","🟣"
    ))
}
