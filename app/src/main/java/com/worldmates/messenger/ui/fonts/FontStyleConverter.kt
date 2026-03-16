package com.worldmates.messenger.ui.fonts

/**
 * Converts Latin text to Unicode Mathematical Alphanumeric variants.
 * Non-Latin characters (Cyrillic, digits, punctuation) pass through unchanged.
 *
 * Reference: Unicode block U+1D400–U+1D7FF
 * https://www.unicode.org/charts/PDF/U1D400.pdf
 */
object FontStyleConverter {

    fun convert(text: String, style: FontStyle): String = when (style) {
        FontStyle.NORMAL             -> text
        FontStyle.BOLD               -> toBold(text)
        FontStyle.ITALIC             -> toItalic(text)
        FontStyle.BOLD_ITALIC        -> toBoldItalic(text)
        FontStyle.SCRIPT             -> toScript(text)
        FontStyle.BOLD_SCRIPT        -> toBoldScript(text)
        FontStyle.FRAKTUR            -> toFraktur(text)
        FontStyle.BOLD_FRAKTUR       -> toBoldFraktur(text)
        FontStyle.DOUBLE_STRUCK      -> toDoubleStruck(text)
        FontStyle.MONOSPACE          -> toMonospace(text)
        FontStyle.SANS_SERIF         -> toSansSerif(text)
        FontStyle.SANS_SERIF_BOLD    -> toSansSerifBold(text)
        FontStyle.SANS_SERIF_ITALIC  -> toSansSerifItalic(text)
        FontStyle.SANS_SERIF_BOLD_ITALIC -> toSansSerifBoldItalic(text)
        FontStyle.FULLWIDTH          -> toFullwidth(text)
        FontStyle.SMALL_CAPS         -> toSmallCaps(text)
        FontStyle.CIRCLED            -> toCircled(text)
        FontStyle.STRIKETHROUGH      -> toStrikethrough(text)
    }

    // ==================== HELPERS ====================

    private fun codePointToString(cp: Int) = String(Character.toChars(cp))

    /** Map using Mathematical block offsets. upperBase/lowerBase are for A and a respectively. */
    private fun mathAlpha(
        text: String,
        upperBase: Int,
        lowerBase: Int,
        digitBase: Int = -1,
        exceptions: Map<Char, String> = emptyMap()
    ): String = buildString {
        for (ch in text) {
            val mapped = exceptions[ch]
            when {
                mapped != null      -> append(mapped)
                ch in 'A'..'Z'     -> append(codePointToString(upperBase + (ch - 'A')))
                ch in 'a'..'z'     -> append(codePointToString(lowerBase + (ch - 'a')))
                digitBase >= 0 && ch in '0'..'9' -> append(codePointToString(digitBase + (ch - '0')))
                else               -> append(ch)
            }
        }
    }

    // ==================== BOLD (U+1D400) ====================

    private fun toBold(text: String) = mathAlpha(text,
        upperBase = 0x1D400,
        lowerBase = 0x1D41A,
        digitBase = 0x1D7CE
    )

    // ==================== ITALIC (U+1D434) ====================
    // Missing: U+1D455 (h) → ℎ

    private fun toItalic(text: String) = mathAlpha(text,
        upperBase = 0x1D434,
        lowerBase = 0x1D44E,
        exceptions = mapOf('h' to "\u210E")
    )

    // ==================== BOLD ITALIC (U+1D468) ====================

    private fun toBoldItalic(text: String) = mathAlpha(text,
        upperBase = 0x1D468,
        lowerBase = 0x1D482
    )

    // ==================== SCRIPT (U+1D49C) ====================
    // Missing uppercase: B→ℬ, E→ℰ, F→ℱ, H→ℋ, I→ℐ, L→ℒ, M→ℳ, R→ℛ
    // Missing lowercase: e→ℯ, g→ℊ, o→ℴ

    private fun toScript(text: String) = mathAlpha(text,
        upperBase = 0x1D49C,
        lowerBase = 0x1D4B6,
        exceptions = mapOf(
            'B' to "\u212C", 'E' to "\u2130", 'F' to "\u2131",
            'H' to "\u210B", 'I' to "\u2110", 'L' to "\u2112",
            'M' to "\u2133", 'R' to "\u211B",
            'e' to "\u212F", 'g' to "\u210A", 'o' to "\u2134"
        )
    )

    // ==================== BOLD SCRIPT (U+1D4D0) ====================

    private fun toBoldScript(text: String) = mathAlpha(text,
        upperBase = 0x1D4D0,
        lowerBase = 0x1D4EA
    )

    // ==================== FRAKTUR (U+1D504) ====================
    // Missing: C→ℭ, H→ℌ, I→ℑ, R→ℜ, Z→ℨ

    private fun toFraktur(text: String) = mathAlpha(text,
        upperBase = 0x1D504,
        lowerBase = 0x1D51E,
        exceptions = mapOf(
            'C' to "\u212D", 'H' to "\u210C", 'I' to "\u2111",
            'R' to "\u211C", 'Z' to "\u2128"
        )
    )

    // ==================== BOLD FRAKTUR (U+1D56C) ====================

    private fun toBoldFraktur(text: String) = mathAlpha(text,
        upperBase = 0x1D56C,
        lowerBase = 0x1D586
    )

    // ==================== DOUBLE-STRUCK (U+1D538) ====================
    // Missing: C→ℂ, H→ℍ, N→ℕ, P→ℙ, Q→ℚ, R→ℝ, Z→ℤ

    private fun toDoubleStruck(text: String) = mathAlpha(text,
        upperBase = 0x1D538,
        lowerBase = 0x1D552,
        digitBase = 0x1D7D8,
        exceptions = mapOf(
            'C' to "\u2102", 'H' to "\u210D", 'N' to "\u2115",
            'P' to "\u2119", 'Q' to "\u211A", 'R' to "\u211D", 'Z' to "\u2124"
        )
    )

    // ==================== MONOSPACE (U+1D670) ====================

    private fun toMonospace(text: String) = mathAlpha(text,
        upperBase = 0x1D670,
        lowerBase = 0x1D68A,
        digitBase = 0x1D7F6
    )

    // ==================== SANS-SERIF (U+1D5A0) ====================

    private fun toSansSerif(text: String) = mathAlpha(text,
        upperBase = 0x1D5A0,
        lowerBase = 0x1D5BA,
        digitBase = 0x1D7E2
    )

    // ==================== SANS-SERIF BOLD (U+1D5D4) ====================

    private fun toSansSerifBold(text: String) = mathAlpha(text,
        upperBase = 0x1D5D4,
        lowerBase = 0x1D5EE,
        digitBase = 0x1D7EC
    )

    // ==================== SANS-SERIF ITALIC (U+1D608) ====================

    private fun toSansSerifItalic(text: String) = mathAlpha(text,
        upperBase = 0x1D608,
        lowerBase = 0x1D622
    )

    // ==================== SANS-SERIF BOLD ITALIC (U+1D63C) ====================

    private fun toSansSerifBoldItalic(text: String) = mathAlpha(text,
        upperBase = 0x1D63C,
        lowerBase = 0x1D656
    )

    // ==================== FULLWIDTH (U+FF01) ====================

    private fun toFullwidth(text: String) = buildString {
        for (ch in text) {
            append(when (ch) {
                in 'A'..'Z' -> (0xFF21 + (ch - 'A')).toChar()
                in 'a'..'z' -> (0xFF41 + (ch - 'a')).toChar()
                in '0'..'9' -> (0xFF10 + (ch - '0')).toChar()
                ' '         -> '\u3000' // Ideographic space
                else        -> ch
            })
        }
    }

    // ==================== SMALL CAPS ====================
    // Uses actual Unicode small capital letters where available

    private val smallCapsMap = mapOf(
        'a' to 'ᴀ', 'b' to 'ʙ', 'c' to 'ᴄ', 'd' to 'ᴅ', 'e' to 'ᴇ',
        'f' to 'ꜰ', 'g' to 'ɢ', 'h' to 'ʜ', 'i' to 'ɪ', 'j' to 'ᴊ',
        'k' to 'ᴋ', 'l' to 'ʟ', 'm' to 'ᴍ', 'n' to 'ɴ', 'o' to 'ᴏ',
        'p' to 'ᴘ', 'q' to 'ǫ', 'r' to 'ʀ', 's' to 'ꜱ', 't' to 'ᴛ',
        'u' to 'ᴜ', 'v' to 'ᴠ', 'w' to 'ᴡ', 'x' to 'x', 'y' to 'ʏ', 'z' to 'ᴢ'
    )

    private fun toSmallCaps(text: String) = buildString {
        for (ch in text) {
            append(when {
                ch in 'a'..'z' -> smallCapsMap[ch] ?: ch
                ch in 'A'..'Z' -> smallCapsMap[ch.lowercaseChar()] ?: ch
                else           -> ch
            })
        }
    }

    // ==================== CIRCLED ====================
    // Ⓐ=U+24B6 … Ⓩ=U+24CF, ⓐ=U+24D0 … ⓩ=U+24E9

    private fun toCircled(text: String) = buildString {
        for (ch in text) {
            append(when (ch) {
                in 'A'..'Z' -> (0x24B6 + (ch - 'A')).toChar()
                in 'a'..'z' -> (0x24D0 + (ch - 'a')).toChar()
                else        -> ch
            })
        }
    }

    // ==================== STRIKETHROUGH ====================
    // Uses combining strikethrough U+0336 after each character

    private fun toStrikethrough(text: String) = buildString {
        for (ch in text) {
            append(ch)
            if (!ch.isWhitespace()) append('\u0336')
        }
    }
}
