package com.worldmates.messenger.ui.fonts

/**
 * Decorative text styles for premium users.
 *
 * Styles 1–17  : Unicode Mathematical Alphanumeric blocks (Latin/digits only).
 * Styles 18–31 : Unicode combining marks — work for ALL scripts incl. Cyrillic/Ukrainian.
 * Styles 32–35 : Enclosed Latin uppercase/lowercase (supplementary-plane symbols).
 * Styles 36–50 : Combined effects (base Unicode style + combining mark).
 *
 * All styled text is stored as plain Unicode, visible to all recipients with no font download.
 */
enum class FontStyle(
    val displayName: String,
    val emoji: String,
    val sampleText: String   // precomputed short preview
) {
    // ─── 1 ─────────────────────────────────────────────────────────────────
    NORMAL(
        displayName = "Звичайний",
        emoji = "✏️",
        sampleText = "Hello"
    ),

    // ─── 2–17  Unicode Math blocks (Latin / digits) ─────────────────────────
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

    // ─── 18  First combining-mark style ─────────────────────────────────────
    STRIKETHROUGH(
        displayName = "Закреслений",
        emoji = "S̶",
        sampleText = "H̶e̶l̶l̶o̶"
    ),

    // ─── 19–31  Combining marks — work for ALL scripts incl. Cyrillic ────────
    UNDERLINE(
        displayName = "Підкреслений",
        emoji = "\u041F\u0332",                          // П̲
        sampleText = "\u041F\u0332\u0440\u0332\u0438\u0332\u0432\u0332\u0456\u0332\u0442\u0332"  // П̲р̲и̲в̲і̲т̲
    ),
    DOUBLE_UNDERLINE(
        displayName = "Подвійне підкреслення",
        emoji = "\u041F\u0333",
        sampleText = "\u041F\u0333\u0440\u0333\u0438\u0333\u0432\u0333\u0456\u0333\u0442\u0333"
    ),
    OVERLINE(
        displayName = "Надрядковий",
        emoji = "\u041F\u0305",
        sampleText = "\u041F\u0305\u0440\u0305\u0438\u0305\u0432\u0305\u0456\u0305\u0442\u0305"
    ),
    DOUBLE_OVERLINE(
        displayName = "Подвійне надрядкове",
        emoji = "\u041F\u033F",
        sampleText = "\u041F\u033F\u0440\u033F\u0438\u033F\u0432\u033F\u0456\u033F\u0442\u033F"
    ),
    WAVY(
        displayName = "Хвилястий",
        emoji = "\u041F\u0330",
        sampleText = "\u041F\u0330\u0440\u0330\u0438\u0330\u0432\u0330\u0456\u0330\u0442\u0330"
    ),
    SLASH(
        displayName = "Косий перекрес",
        emoji = "\u041F\u0338",
        sampleText = "\u041F\u0338\u0440\u0338\u0438\u0338\u0432\u0338\u0456\u0338\u0442\u0338"
    ),
    TILDE_OVERLAY(
        displayName = "Тільда поверх",
        emoji = "\u041F\u0334",
        sampleText = "\u041F\u0334\u0440\u0334\u0438\u0334\u0432\u0334\u0456\u0334\u0442\u0334"
    ),
    DOTTED(
        displayName = "Крапки зверху",
        emoji = "\u041F\u0307",
        sampleText = "\u041F\u0307\u0440\u0307\u0438\u0307\u0432\u0307\u0456\u0307\u0442\u0307"
    ),
    DIAERESIS(
        displayName = "Умлаут",
        emoji = "\u041F\u0308",
        sampleText = "\u041F\u0308\u0440\u0308\u0438\u0308\u0432\u0308\u0456\u0308\u0442\u0308"
    ),
    CIRCLE_OVERLAY(
        displayName = "В колі",
        emoji = "\u004F\u20DD",                          // O⃝
        sampleText = "\u041F\u20DD\u0440\u20DD\u0438\u20DD\u0432\u20DD\u0456\u20DD\u0442\u20DD"
    ),
    SQUARE_OVERLAY(
        displayName = "В квадраті",
        emoji = "\u004F\u20DE",
        sampleText = "\u041F\u20DE\u0440\u20DE\u0438\u20DE\u0432\u20DE\u0456\u20DE\u0442\u20DE"
    ),
    DIAMOND_OVERLAY(
        displayName = "В ромбі",
        emoji = "\u004F\u20DF",
        sampleText = "\u041F\u20DF\u0440\u20DF\u0438\u20DF\u0432\u20DF\u0456\u20DF\u0442\u20DF"
    ),

    // ─── 32–35  Enclosed Latin (uppercase/lowercase, pass-through for Cyrillic) ─
    NEGATIVE_CIRCLED(
        displayName = "Темний кружок",
        emoji = "\uD83C\uDD50",                          // 🅐
        sampleText = "\uD83C\uDD57\uD83C\uDD54\uD83C\uDD5B\uD83C\uDD5B\uD83C\uDD5E"  // 🅗🅔🅛🅛🅞
    ),
    PARENTHESIZED_CAPS(
        displayName = "В дужках (великі)",
        emoji = "\uD83C\uDD10",                          // 🄐
        sampleText = "\uD83C\uDD17\uD83C\uDD14\uD83C\uDD1B\uD83C\uDD1B\uD83C\uDD1E"  // 🄗🄔🄛🄛🄞
    ),
    SQUARED_LATIN(
        displayName = "В рамці",
        emoji = "\uD83C\uDD30",                          // 🄰
        sampleText = "\uD83C\uDD37\uD83C\uDD34\uD83C\uDD3B\uD83C\uDD3B\uD83C\uDD3E"  // 🄷🄴🄻🄻🄾
    ),
    PARENTHESIZED(
        displayName = "В дужках (малі)",
        emoji = "\u249C",                                // ⒜
        sampleText = "\u24A3\u24A0\u24A7\u24A7\u24AA"  // ⒣⒠⒧⒧⒪
    ),

    // ─── 36–50  Combined effects ─────────────────────────────────────────────
    BOLD_UNDERLINE(
        displayName = "Жирний підкреслений",
        emoji = "\uD835\uDC01\u0332",                   // 𝐁̲
        sampleText = "\uD835\uDC07\u0332\uD835\uDC1E\u0332\uD835\uDC25\u0332\uD835\uDC25\u0332\uD835\uDC28\u0332"
    ),
    ITALIC_UNDERLINE(
        displayName = "Курсив підкреслений",
        emoji = "\uD835\uDC3C\u0332",                   // 𝐼̲
        sampleText = "\uD835\uDC3B\u0332\uD835\uDC52\u0332\uD835\uDC59\u0332\uD835\uDC59\u0332\uD835\uDC5C\u0332"
    ),
    SCRIPT_UNDERLINE(
        displayName = "Рукопис підкреслений",
        emoji = "\uD835\uDC9C\u0332",                   // 𝒜̲
        sampleText = "\u210B\u0332\u212F\u0332\uD835\uDCC1\u0332\uD835\uDCC1\u0332\u2134\u0332"
    ),
    BOLD_SCRIPT_UNDERLINE(
        displayName = "Жирний рукопис підкреслений",
        emoji = "\uD835\uDCD1\u0332",                   // 𝓑̲
        sampleText = "\uD835\uDCCF\u0332\uD835\uDCEE\u0332\uD835\uDCF5\u0332\uD835\uDCF5\u0332\uD835\uDCF8\u0332"
    ),
    MONOSPACE_UNDERLINE(
        displayName = "Моно підкреслений",
        emoji = "\uD835\uDE70\u0332",                   // 𝙰̲
        sampleText = "\uD835\uDE77\u0332\uD835\uDE8E\u0332\uD835\uDE95\u0332\uD835\uDE95\u0332\uD835\uDE98\u0332"
    ),
    SANS_BOLD_UNDERLINE(
        displayName = "Жирний без засічок підкреслений",
        emoji = "\uD835\uDDD4\u0332",                   // 𝗔̲
        sampleText = "\uD835\uDDDB\u0332\uD835\uDDF2\u0332\uD835\uDDF9\u0332\uD835\uDDF9\u0332\uD835\uDDFC\u0332"
    ),
    BOLD_STRIKETHROUGH(
        displayName = "Жирний закреслений",
        emoji = "\uD835\uDC01\u0336",                   // 𝐁̶
        sampleText = "\uD835\uDC07\u0336\uD835\uDC1E\u0336\uD835\uDC25\u0336\uD835\uDC25\u0336\uD835\uDC28\u0336"
    ),
    ITALIC_STRIKETHROUGH(
        displayName = "Курсив закреслений",
        emoji = "\uD835\uDC3C\u0336",
        sampleText = "\uD835\uDC3B\u0336\uD835\uDC52\u0336\uD835\uDC59\u0336\uD835\uDC59\u0336\uD835\uDC5C\u0336"
    ),
    SCRIPT_STRIKETHROUGH(
        displayName = "Рукопис закреслений",
        emoji = "\uD835\uDC9C\u0336",
        sampleText = "\u210B\u0336\u212F\u0336\uD835\uDCC1\u0336\uD835\uDCC1\u0336\u2134\u0336"
    ),
    MONOSPACE_STRIKETHROUGH(
        displayName = "Моно закреслений",
        emoji = "\uD835\uDE70\u0336",
        sampleText = "\uD835\uDE77\u0336\uD835\uDE8E\u0336\uD835\uDE95\u0336\uD835\uDE95\u0336\uD835\uDE98\u0336"
    ),
    OVERLINE_UNDERLINE(
        displayName = "Обрамлений",
        emoji = "\u041F\u0305\u0332",
        sampleText = "\u041F\u0305\u0332\u0440\u0305\u0332\u0438\u0305\u0332\u0432\u0305\u0332\u0456\u0305\u0332\u0442\u0305\u0332"
    ),
    WAVY_STRIKETHROUGH(
        displayName = "Хвилястий закреслений",
        emoji = "\u041F\u0334\u0336",
        sampleText = "\u041F\u0334\u0336\u0440\u0334\u0336\u0438\u0334\u0336\u0432\u0334\u0336\u0456\u0334\u0336\u0442\u0334\u0336"
    ),
    BOLD_FRAKTUR_UNDERLINE(
        displayName = "Готика підкреслена",
        emoji = "\uD835\uDD6C\u0332",                   // 𝕬̲
        sampleText = "\uD835\uDD73\u0332\uD835\uDD8A\u0332\uD835\uDD91\u0332\uD835\uDD91\u0332\uD835\uDD94\u0332"
    ),
    DOUBLE_STRUCK_UNDERLINE(
        displayName = "Подвійний підкреслений",
        emoji = "\uD835\uDD38\u0332",                   // 𝔸̲
        sampleText = "\u210D\u0332\uD835\uDD56\u0332\uD835\uDD5D\u0332\uD835\uDD5D\u0332\uD835\uDD60\u0332"
    ),
    DIAERESIS_OVERLINE(
        displayName = "Умлаут з надрядковим",
        emoji = "\u041F\u0308\u0305",
        sampleText = "\u041F\u0308\u0305\u0440\u0308\u0305\u0438\u0308\u0305\u0432\u0308\u0305\u0456\u0308\u0305\u0442\u0308\u0305"
    ),

    // ─── 51–55  Extra combined styles ────────────────────────────────────────
    FRAKTUR_UNDERLINE(
        displayName = "Готика підкреслена (звичайна)",
        emoji = "\uD835\uDD24\u0332",                   // 𝔤̲
        sampleText = "\u210C\u0332\uD835\uDD22\u0332\uD835\uDD29\u0332\uD835\uDD29\u0332\uD835\uDD2C\u0332"  // ℌ̲𝔢̲𝔩̲𝔩̲𝔬̲
    ),
    SANS_SERIF_ITALIC_UNDERLINE(
        displayName = "Курсив без засічок підкреслений",
        emoji = "\uD835\uDE08\u0332",                   // 𝘈̲
        sampleText = "\uD835\uDE17\u0332\uD835\uDE2E\u0332\uD835\uDE35\u0332\uD835\uDE35\u0332\uD835\uDE38\u0332"
    ),
    BOLD_WAVY(
        displayName = "Жирний хвилястий",
        emoji = "\uD835\uDC01\u0330",                   // 𝐁̰
        sampleText = "\uD835\uDC07\u0330\uD835\uDC1E\u0330\uD835\uDC25\u0330\uD835\uDC25\u0330\uD835\uDC28\u0330"
    ),
    DOTTED_STRIKETHROUGH(
        displayName = "Крапки закреслені",
        emoji = "\u041F\u0307\u0336",
        sampleText = "\u041F\u0307\u0336\u0440\u0307\u0336\u0438\u0307\u0336\u0432\u0307\u0336\u0456\u0307\u0336\u0442\u0307\u0336"
    ),
    DOUBLE_UNDERLINE_OVERLINE(
        displayName = "Подвійне підкреслення + надрядкове",
        emoji = "\u041F\u0333\u0305",
        sampleText = "\u041F\u0333\u0305\u0440\u0333\u0305\u0438\u0333\u0305\u0432\u0333\u0305\u0456\u0333\u0305\u0442\u0333\u0305"
    )
}
