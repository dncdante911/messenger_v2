package com.worldmates.messenger.ui.fonts

/**
 * Decorative text styles that work for ALL scripts: Cyrillic, Ukrainian, Latin, digits.
 *
 * Every style uses Unicode combining marks appended after each character.
 * Combining marks attach visually to the preceding character regardless of script —
 * no Unicode Mathematical block remapping needed (those only cover Latin/digits).
 *
 * Total: 42 styles (including NORMAL).
 */
enum class FontStyle(
    val displayName: String,
    val emoji: String,
    val sampleText: String   // short preview in Cyrillic so user sees real result
) {
    // ─── 1  Normal ───────────────────────────────────────────────────────────
    NORMAL(
        displayName = "Звичайний",
        emoji = "✏️",
        sampleText = "Привіт"
    ),

    // ─── 2–14  Single combining marks ────────────────────────────────────────

    STRIKETHROUGH(
        displayName = "Закреслений",
        emoji = "П̶",
        sampleText = "П̶р̶и̶в̶і̶т̶"
    ),
    UNDERLINE(
        displayName = "Підкреслений",
        emoji = "П̲",
        sampleText = "П̲р̲и̲в̲і̲т̲"
    ),
    DOUBLE_UNDERLINE(
        displayName = "Подвійне підкреслення",
        emoji = "П̳",
        sampleText = "П̳р̳и̳в̳і̳т̳"
    ),
    OVERLINE(
        displayName = "Надрядковий",
        emoji = "П̅",
        sampleText = "П̅р̅и̅в̅і̅т̅"
    ),
    DOUBLE_OVERLINE(
        displayName = "Подвійне надрядкове",
        emoji = "П̿",
        sampleText = "П̿р̿и̿в̿і̿т̿"
    ),
    WAVY(
        displayName = "Хвилястий знизу",
        emoji = "П̰",
        sampleText = "П̰р̰и̰в̰і̰т̰"
    ),
    SLASH(
        displayName = "Косий перекрес",
        emoji = "П̸",
        sampleText = "П̸р̸и̸в̸і̸т̸"
    ),
    TILDE_OVERLAY(
        displayName = "Тільда поверх",
        emoji = "П̴",
        sampleText = "П̴р̴и̴в̴і̴т̴"
    ),
    DOTTED(
        displayName = "Крапка зверху",
        emoji = "П̇",
        sampleText = "П̇р̇и̇в̇і̇т̇"
    ),
    DIAERESIS(
        displayName = "Умлаут (дві крапки)",
        emoji = "П̈",
        sampleText = "П̈р̈ӥв̈їт̈"
    ),
    RING_ABOVE(
        displayName = "Кружечок зверху",
        emoji = "П̊",
        sampleText = "П̊р̊и̊в̊і̊т̊"
    ),
    DOT_BELOW(
        displayName = "Крапка знизу",
        emoji = "Ṗ",
        sampleText = "П̣р̣и̣в̣і̣т̣"
    ),
    RING_BELOW(
        displayName = "Кружечок знизу",
        emoji = "П̥",
        sampleText = "П̥р̥и̥в̥і̥т̥"
    ),

    // ─── 15–20  Accent marks (diacritics) ────────────────────────────────────

    GRAVE(
        displayName = "Гравіс (ліво) над",
        emoji = "П̀",
        sampleText = "П̀р̀ѝв̀і̀т̀"
    ),
    ACUTE(
        displayName = "Акут (право) над",
        emoji = "П́",
        sampleText = "П́р́и́в́і́т́"
    ),
    CIRCUMFLEX(
        displayName = "Циркумфлекс (^) над",
        emoji = "П̂",
        sampleText = "П̂р̂и̂в̂і̂т̂"
    ),
    MACRON(
        displayName = "Макрон (рисочка) над",
        emoji = "П̄",
        sampleText = "П̄р̄ӣв̄і̄т̄"
    ),
    BREVE(
        displayName = "Бреве (дуга) над",
        emoji = "П̆",
        sampleText = "П̆р̆йв̆і̆т̆"
    ),
    CARON(
        displayName = "Карон (v) над",
        emoji = "П̌",
        sampleText = "П̌р̌и̌в̌і̌т̌"
    ),

    // ─── 21–24  Enclosing / special symbols ──────────────────────────────────

    CIRCLE_OVERLAY(
        displayName = "В колі",
        emoji = "О⃝",
        sampleText = "П⃝р⃝и⃝в⃝і⃝т⃝"
    ),
    SQUARE_OVERLAY(
        displayName = "В квадраті",
        emoji = "О⃞",
        sampleText = "П⃞р⃞и⃞в⃞і⃞т⃞"
    ),
    DIAMOND_OVERLAY(
        displayName = "В ромбі",
        emoji = "О⃟",
        sampleText = "П⃟р⃟и⃟в⃟і⃟т⃟"
    ),
    ARROW_ABOVE(
        displayName = "Стрілка зверху",
        emoji = "П⃗",
        sampleText = "П⃗р⃗и⃗в⃗і⃗т⃗"
    ),

    // ─── 25–28  Cyrillic-specific marks ──────────────────────────────────────

    CYRILLIC_TITLO(
        displayName = "Тітло (кирил. скорочення)",
        emoji = "П҃",
        sampleText = "П҃р҃и҃в҃і҃т҃"
    ),
    TRIPLE_DOT_ABOVE(
        displayName = "Три крапки зверху",
        emoji = "П⃛",
        sampleText = "П⃛р⃛и⃛в⃛і⃛т⃛"
    ),
    FOUR_DOTS_ABOVE(
        displayName = "Чотири крапки зверху",
        emoji = "П⃜",
        sampleText = "П⃜р⃜и⃜в⃜і⃜т⃜"
    ),
    TRIPLE_UNDERDOT(
        displayName = "Три крапки знизу",
        emoji = "П⃨",
        sampleText = "П⃨р⃨и⃨в⃨і⃨т⃨"
    ),

    // ─── 29–35  Multi-mark combinations ──────────────────────────────────────

    OVERLINE_UNDERLINE(
        displayName = "Обрамлений (над + під)",
        emoji = "П̲̅",
        sampleText = "П̲̅р̲̅и̲̅в̲̅і̲̅т̲̅"
    ),
    WAVY_STRIKETHROUGH(
        displayName = "Хвилястий закреслений",
        emoji = "П̴̶",
        sampleText = "П̴̶р̴̶и̴̶в̴̶і̴̶т̴̶"
    ),
    DIAERESIS_OVERLINE(
        displayName = "Умлаут з надрядковим",
        emoji = "П̈̅",
        sampleText = "П̈̅р̈̅ӥ̅в̈̅ї̅т̈̅"
    ),
    DOTTED_STRIKETHROUGH(
        displayName = "Крапка + закреслення",
        emoji = "П̶̇",
        sampleText = "П̶̇р̶̇и̶̇в̶̇і̶̇т̶̇"
    ),
    DOUBLE_UNDERLINE_OVERLINE(
        displayName = "Подвійне під + надрядкове",
        emoji = "П̳̅",
        sampleText = "П̳̅р̳̅и̳̅в̳̅і̳̅т̳̅"
    ),
    DIAERESIS_STRIKETHROUGH(
        displayName = "Умлаут + закреслення",
        emoji = "П̶̈",
        sampleText = "П̶̈р̶̈ӥ̶в̶̈ї̶т̶̈"
    ),
    CIRCLE_UNDERLINE(
        displayName = "Коло + підкреслення",
        emoji = "О⃝̲",
        sampleText = "П⃝̲р⃝̲и⃝̲в⃝̲і⃝̲т⃝̲"
    ),

    // ─── 36–42  Triple combinations / extra effects ───────────────────────────

    OVERLINE_STRIKETHROUGH_UNDERLINE(
        displayName = "Повне обрамлення",
        emoji = "П̶̲̅",
        sampleText = "П̶̲̅р̶̲̅и̶̲̅в̶̲̅і̶̲̅т̶̲̅"
    ),
    DOTTED_UNDERLINE(
        displayName = "Крапка + підкреслення",
        emoji = "П̲̇",
        sampleText = "П̲̇р̲̇и̲̇в̲̇і̲̇т̲̇"
    ),
    GRAVE_UNDERLINE(
        displayName = "Гравіс + підкреслення",
        emoji = "П̲̀",
        sampleText = "П̲̀р̲̀ѝ̲в̲̀і̲̀т̲̀"
    ),
    DIAERESIS_UNDERLINE(
        displayName = "Умлаут + підкреслення",
        emoji = "П̲̈",
        sampleText = "П̲̈р̲̈ӥ̲в̲̈ї̲т̲̈"
    ),
    MACRON_UNDERLINE(
        displayName = "Макрон + підкреслення",
        emoji = "П̲̄",
        sampleText = "П̲̄р̲̄ӣ̲в̲̄і̲̄т̲̄"
    ),
    CARON_STRIKETHROUGH(
        displayName = "Карон + закреслення",
        emoji = "П̶̌",
        sampleText = "П̶̌р̶̌и̶̌в̶̌і̶̌т̶̌"
    ),
    ACUTE_OVERLINE(
        displayName = "Акут + надрядкове",
        emoji = "П́̅",
        sampleText = "П́̅р́̅и́̅в́̅і́̅т́̅"
    )
}
