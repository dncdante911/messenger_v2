package com.worldmates.messenger.ui.messages

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.ui.components.formatting.FormattedText
import com.worldmates.messenger.ui.components.formatting.FormattingSettings
import com.worldmates.messenger.ui.preferences.UIStyle
import com.worldmates.messenger.ui.preferences.rememberUIStyle

/**
 * Formatted Message Bubble Component
 *
 * Renders message text with full formatting support:
 * - @mentions (clickable)
 * - #hashtags (clickable)
 * - Markdown (bold, italic, code, etc.)
 * - Spoilers (tap to reveal)
 * - Links (clickable)
 *
 * This is a modular component extracted from MessagesScreen to keep
 * the main file manageable.
 */

// ==================== TEXT RENDERING ====================

/**
 * Renders message text with formatting
 */
@Composable
fun FormattedMessageText(
    text: String,
    textColor: Color,
    settings: FormattingSettings = FormattingSettings(),
    onMentionClick: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    FormattedText(
        text = text,
        color = textColor,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        settings = settings,
        onMentionClick = onMentionClick,
        onHashtagClick = onHashtagClick,
        onLinkClick = onLinkClick,
        modifier = modifier
    )
}

/**
 * Message bubble content with formatted text
 */
@Composable
fun FormattedMessageContent(
    message: Message,
    textColor: Color,
    settings: FormattingSettings = FormattingSettings(),
    onMentionClick: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val text = message.decryptedText ?: message.encryptedText ?: ""

    if (text.isNotBlank()) {
        // Check if it's emoji-only message
        if (isEmojiOnly(text)) {
            // Large emoji without bubble
            Text(
                text = text,
                fontSize = getEmojiSize(text),
                lineHeight = (getEmojiSize(text).value + 4).sp,
                modifier = modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp)
            )
        } else {
            // Regular formatted text
            FormattedMessageText(
                text = text,
                textColor = textColor,
                settings = settings,
                onMentionClick = onMentionClick,
                onHashtagClick = onHashtagClick,
                onLinkClick = onLinkClick,
                modifier = modifier
            )
        }
    }
}

// ==================== REPLY PREVIEW ====================

/**
 * Reply quote block with formatted preview
 */
@Composable
fun ReplyQuoteBlock(
    replyToText: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = textColor.copy(alpha = 0.1f),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Vertical line
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(
                        color = colorScheme.primary,
                        shape = RoundedCornerShape(2.dp)
                    )
            )

            // Quote text
            Column {
                Text(
                    text = "Відповідь",
                    color = colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = replyToText,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ==================== SENDER INFO (for groups) ====================

/**
 * Sender name and avatar for group messages
 */
@Composable
fun MessageSenderInfo(
    senderName: String,
    senderId: Long,
    onSenderClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = senderName,
        color = getSenderColor(senderId),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clickable { onSenderClick(senderId) }
            .padding(bottom = 4.dp)
    )
}

/**
 * Get consistent color for sender name based on their ID
 */
@Composable
private fun getSenderColor(userId: Long): Color {
    val colors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFFE91E63), // Pink
        Color(0xFF00BCD4), // Cyan
        Color(0xFFFF5722), // Deep Orange
        Color(0xFF3F51B5), // Indigo
    )
    return colors[(userId % colors.size).toInt()]
}

// ==================== EMOJI HELPERS ====================

/**
 * Check if text contains only emoji
 */
fun isEmojiOnly(text: String): Boolean {
    if (text.isBlank()) return false

    // Remove whitespace
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false

    // Check each character
    var i = 0
    while (i < trimmed.length) {
        val codePoint = trimmed.codePointAt(i)
        val charCount = Character.charCount(codePoint)

        // Check if it's an emoji
        if (!isEmoji(codePoint)) {
            return false
        }

        i += charCount
    }

    // Limit to max 5 emoji for "emoji only" display
    val emojiCount = countEmojis(trimmed)
    return emojiCount in 1..5
}

private fun isEmoji(codePoint: Int): Boolean {
    return (codePoint in 0x1F600..0x1F64F) || // Emoticons
            (codePoint in 0x1F300..0x1F5FF) || // Misc Symbols and Pictographs
            (codePoint in 0x1F680..0x1F6FF) || // Transport and Map
            (codePoint in 0x1F1E0..0x1F1FF) || // Flags
            (codePoint in 0x2600..0x26FF) ||   // Misc symbols
            (codePoint in 0x2700..0x27BF) ||   // Dingbats
            (codePoint in 0xFE00..0xFE0F) ||   // Variation Selectors
            (codePoint in 0x1F900..0x1F9FF) || // Supplemental Symbols and Pictographs
            (codePoint in 0x1FA00..0x1FA6F) || // Chess Symbols
            (codePoint in 0x1FA70..0x1FAFF) || // Symbols and Pictographs Extended-A
            (codePoint in 0x231A..0x231B) ||   // Watch, Hourglass
            (codePoint in 0x23E9..0x23F3) ||   // Various symbols
            (codePoint in 0x23F8..0x23FA) ||   // Various symbols
            (codePoint in 0x25AA..0x25AB) ||   // Squares
            (codePoint in 0x25B6..0x25C0) ||   // Triangles
            (codePoint in 0x25FB..0x25FE) ||   // Squares
            (codePoint in 0x2614..0x2615) ||   // Umbrella, Hot Beverage
            (codePoint in 0x2648..0x2653) ||   // Zodiac
            (codePoint in 0x267F..0x267F) ||   // Wheelchair
            (codePoint in 0x2693..0x2693) ||   // Anchor
            (codePoint in 0x26A1..0x26A1) ||   // High Voltage
            (codePoint in 0x26AA..0x26AB) ||   // Circles
            (codePoint in 0x26BD..0x26BE) ||   // Soccer, Baseball
            (codePoint in 0x26C4..0x26C5) ||   // Snowman, Sun
            (codePoint in 0x26CE..0x26CE) ||   // Ophiuchus
            (codePoint in 0x26D4..0x26D4) ||   // No Entry
            (codePoint in 0x26EA..0x26EA) ||   // Church
            (codePoint in 0x26F2..0x26F3) ||   // Fountain, Golf
            (codePoint in 0x26F5..0x26F5) ||   // Sailboat
            (codePoint in 0x26FA..0x26FA) ||   // Tent
            (codePoint in 0x26FD..0x26FD) ||   // Fuel Pump
            (codePoint in 0x2702..0x2702) ||   // Scissors
            (codePoint in 0x2705..0x2705) ||   // Check Mark
            (codePoint in 0x2708..0x270D) ||   // Various
            (codePoint in 0x270F..0x270F) ||   // Pencil
            (codePoint in 0x2712..0x2712) ||   // Black Nib
            (codePoint in 0x2714..0x2714) ||   // Check Mark
            (codePoint in 0x2716..0x2716) ||   // X Mark
            (codePoint in 0x271D..0x271D) ||   // Latin Cross
            (codePoint in 0x2721..0x2721) ||   // Star of David
            (codePoint in 0x2728..0x2728) ||   // Sparkles
            (codePoint in 0x2733..0x2734) ||   // Eight Spoked Asterisk
            (codePoint in 0x2744..0x2744) ||   // Snowflake
            (codePoint in 0x2747..0x2747) ||   // Sparkle
            (codePoint in 0x274C..0x274C) ||   // Cross Mark
            (codePoint in 0x274E..0x274E) ||   // Cross Mark
            (codePoint in 0x2753..0x2755) ||   // Question Marks
            (codePoint in 0x2757..0x2757) ||   // Exclamation Mark
            (codePoint in 0x2763..0x2764) ||   // Heart Exclamation, Heart
            (codePoint in 0x2795..0x2797) ||   // Plus, Minus, Divide
            (codePoint in 0x27A1..0x27A1) ||   // Right Arrow
            (codePoint in 0x27B0..0x27B0) ||   // Curly Loop
            (codePoint in 0x27BF..0x27BF) ||   // Double Curly Loop
            (codePoint in 0x2934..0x2935) ||   // Arrows
            (codePoint in 0x2B05..0x2B07) ||   // Arrows
            (codePoint in 0x2B1B..0x2B1C) ||   // Squares
            (codePoint in 0x2B50..0x2B50) ||   // Star
            (codePoint in 0x2B55..0x2B55) ||   // Circle
            (codePoint in 0x3030..0x3030) ||   // Wavy Dash
            (codePoint in 0x303D..0x303D) ||   // Part Alternation Mark
            (codePoint in 0x3297..0x3297) ||   // Circled Ideograph Congratulation
            (codePoint in 0x3299..0x3299) ||   // Circled Ideograph Secret
            (codePoint == 0x200D) ||           // Zero Width Joiner (for combined emoji)
            (codePoint == 0x20E3) ||           // Combining Enclosing Keycap
            (codePoint == 0xFE0F)              // Variation Selector-16
}

private fun countEmojis(text: String): Int {
    var count = 0
    var i = 0
    while (i < text.length) {
        val codePoint = text.codePointAt(i)
        val charCount = Character.charCount(codePoint)

        // Skip variation selectors and joiners
        if (codePoint != 0xFE0F && codePoint != 0x200D) {
            if (isEmoji(codePoint)) {
                count++
            }
        }

        i += charCount
    }
    return count
}

/**
 * Get emoji display size based on count
 */
fun getEmojiSize(text: String): androidx.compose.ui.unit.TextUnit {
    val emojiCount = countEmojis(text)
    return when {
        emojiCount == 1 -> 48.sp
        emojiCount == 2 -> 40.sp
        emojiCount == 3 -> 32.sp
        else -> 28.sp
    }
}

// ==================== BUBBLE COLORS ====================

/**
 * Get bubble background color based on UI style
 */
@Composable
fun getBubbleBackgroundColor(isOwn: Boolean): Color {
    val uiStyle = rememberUIStyle()

    return when (uiStyle) {
        UIStyle.WORLDMATES -> {
            if (isOwn) Color(0xFF4A90E2) else Color(0xFFF0F0F0)
        }
        UIStyle.TELEGRAM -> {
            if (isOwn) Color(0xFFDCF8C6) else Color(0xFFFFFFFF)
        }
    }
}

/**
 * Get text color based on UI style
 */
@Composable
fun getBubbleTextColor(isOwn: Boolean): Color {
    val uiStyle = rememberUIStyle()

    return when (uiStyle) {
        UIStyle.WORLDMATES -> {
            if (isOwn) Color.White else Color(0xFF1F1F1F)
        }
        UIStyle.TELEGRAM -> {
            Color(0xFF1F1F1F)
        }
    }
}
