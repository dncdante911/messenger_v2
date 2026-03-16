package com.worldmates.messenger.ui.fonts

/**
 * Decorative text styles for premium users.
 * All styles use Unicode Mathematical Alphanumeric Symbols (U+1D400–U+1D7FF)
 * so styled text is visible to all recipients without any font downloads.
 */
enum class FontStyle(
    val displayName: String,
    val emoji: String,
    val sampleText: String   // "Hello" in each style (precomputed)
) {
    NORMAL(
        displayName = "Звичайний",
        emoji = "✏️",
        sampleText = "Hello"
    ),
    BOLD(
        displayName = "Жирний",
        emoji = "𝐁",
        sampleText = "𝐇𝐞𝐥𝐥𝐨"
    ),
    ITALIC(
        displayName = "Курсив",
        emoji = "𝐼",
        sampleText = "𝐻𝑒𝑙𝑙𝑜"
    ),
    BOLD_ITALIC(
        displayName = "Жирний курсив",
        emoji = "𝑩",
        sampleText = "𝑯𝒆𝒍𝒍𝒐"
    ),
    SCRIPT(
        displayName = "Рукопис",
        emoji = "𝒜",
        sampleText = "ℋℯ𝓁𝓁ℴ"
    ),
    BOLD_SCRIPT(
        displayName = "Жирний рукопис",
        emoji = "𝓑",
        sampleText = "𝓗𝓮𝓵𝓵𝓸"
    ),
    FRAKTUR(
        displayName = "Готика",
        emoji = "𝔄",
        sampleText = "ℌ𝔢𝔩𝔩𝔬"
    ),
    BOLD_FRAKTUR(
        displayName = "Жирна готика",
        emoji = "𝕬",
        sampleText = "𝕳𝖊𝖑𝖑𝖔"
    ),
    DOUBLE_STRUCK(
        displayName = "Подвійний",
        emoji = "𝔸",
        sampleText = "ℍ𝕖𝕝𝕝𝕠"
    ),
    MONOSPACE(
        displayName = "Моноширний",
        emoji = "𝙰",
        sampleText = "𝙷𝚎𝚕𝚕𝚘"
    ),
    SANS_SERIF(
        displayName = "Без засічок",
        emoji = "𝖠",
        sampleText = "𝖧𝖾𝗅𝗅𝗈"
    ),
    SANS_SERIF_BOLD(
        displayName = "Жирний без засічок",
        emoji = "𝗔",
        sampleText = "𝗛𝗲𝗹𝗹𝗼"
    ),
    SANS_SERIF_ITALIC(
        displayName = "Курсив без засічок",
        emoji = "𝘈",
        sampleText = "𝘏𝘦𝘭𝘭𝘰"
    ),
    SANS_SERIF_BOLD_ITALIC(
        displayName = "Жирний курсив без засічок",
        emoji = "𝘼",
        sampleText = "𝙃𝙚𝙡𝙡𝙤"
    ),
    FULLWIDTH(
        displayName = "Широкий",
        emoji = "Ａ",
        sampleText = "Ｈｅｌｌｏ"
    ),
    SMALL_CAPS(
        displayName = "Малі великі",
        emoji = "ꜱ",
        sampleText = "ʜᴇʟʟᴏ"
    ),
    CIRCLED(
        displayName = "В кружечках",
        emoji = "Ⓐ",
        sampleText = "ⒽⓔⓁⓁⓞ"
    ),
    STRIKETHROUGH(
        displayName = "Закреслений",
        emoji = "S̶",
        sampleText = "H̶e̶l̶l̶o̶"
    )
}
